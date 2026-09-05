package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlCbcConsentRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolving a submission whose outcome we never learned.
 *
 * <p>When {@code POST /new-loan-application} is sent and no reply arrives, the
 * application may or may not exist at Sambat. Guessing either way is
 * unacceptable: assume it failed and a retry files a second credit enquiry
 * against a real person; assume it succeeded and the customer waits forever for
 * a decision on an application nobody has.
 *
 * <p>Sambat expose no lookup that would answer it — there is no "does an
 * application exist for this customer" endpoint — so this is deliberately a
 * human decision. An operator opens their console, sees whether the application
 * is there, and says so. The code's job is to make sure nothing happens
 * automatically in the meantime, and that whichever answer comes back leaves the
 * records consistent.
 *
 * <p>Sambat confirmed (2026-09-05) that protecting against the double request is
 * ours to do.
 */
@Slf4j
@Service
public class PdlSubmissionReconcileService {

	/** How the consent is marked when it had to be reconstructed after the fact. */
	static final String RECONCILED = "RECONCILED";

	@Autowired
	private PaydayLoanRepository repo;

	@Autowired
	private PdlPersonalInfoRepository personalRepo;

	@Autowired
	private PdlCbcConsentRepository consentRepo;

	@Autowired
	private PdlCbcConsentLedger ledger;

	@Autowired
	private CbcConsentFormRenderer consentForm;

	@Value("${pdl.cbc.text-version:v1}")
	private String cbcTextVersion;

	/** One loan waiting on a human answer. */
	public record Unresolved(Integer loanId, Integer userId, String username, Instant attemptedAt,
			String message) {
	}

	/** Everything a submission never answered for. */
	public List<Unresolved> unresolved() {
		return repo.findAll().stream()
				.filter(l -> LosSubmitException.OUTCOME_UNKNOWN.equals(l.getLosStatusCode()))
				.map(l -> new Unresolved(l.getId(),
						l.getUser() == null ? null : l.getUser().getId(),
						l.getUser() == null ? "" : l.getUser().getUsername(),
						l.getUpdatedAt(), l.getLosMessage()))
				.toList();
	}

	/**
	 * The operator found nothing at Sambat: the application was never filed.
	 *
	 * <p>Clears the marker so the customer can submit normally. Nothing else is
	 * touched, because on this path nothing else happened.
	 */
	@Transactional
	public ResponseEntity<Message<String>> markNotFiled(int loanId, String note) {
		PaydayLoan loan = loanAwaiting(loanId);
		if (loan == null)
			return resp("NOT_FOUND", "No loan " + loanId + " is awaiting reconciliation", null);

		loan.setLosStatusCode(null);
		loan.setLosMessage(null);
		repo.save(loan);
		log.info("PDL submission {} reconciled as NOT filed by {} ({})", loanId, actor(), note);
		return resp("SUCCESS", "Loan " + loanId + " can be submitted again", null);
	}

	/**
	 * The operator found the application at Sambat: it was filed after all.
	 *
	 * <p>Brings our records up to what actually happened. The consent is the
	 * part that matters — the customer did consent and a document was filed, but
	 * the transaction that would have recorded it rolled back when the call
	 * failed, so without this there is a real credit enquiry with no consent
	 * record behind it.
	 *
	 * <p>The reconstructed record is honestly marked. {@code captureSource} says
	 * {@code RECONCILED}, and the archived document is left empty rather than
	 * re-rendered: the filed PDF was built from a timestamp taken at submit time
	 * that did not survive the rollback, so a re-render would be a different
	 * document presented as the one Sambat holds.
	 */
	@Transactional
	public ResponseEntity<Message<String>> markFiled(int loanId, Long losAppId,
			String losApplicationNo, String note) {
		PaydayLoan loan = loanAwaiting(loanId);
		if (loan == null)
			return resp("NOT_FOUND", "No loan " + loanId + " is awaiting reconciliation", null);
		if (losAppId == null && (losApplicationNo == null || losApplicationNo.isBlank()))
			return resp("INVALID",
					"Give Sambat's application id from their console — it is what every later "
							+ "callback quotes, and without it their decision cannot be matched "
							+ "to this loan.",
					null);

		loan.setLosAppId(losAppId);
		if (losApplicationNo != null && !losApplicationNo.isBlank())
			loan.setLosApplicationNo(losApplicationNo.trim());
		loan.setStatus(PdlStatusEnum.Submitted);
		loan.setLosStatusCode(null);
		loan.setLosMessage(null);

		if (consentRepo.findByLoan_Id(loanId).isEmpty())
			recordConsent(loan);

		repo.save(loan);
		log.info("PDL submission {} reconciled as FILED (appId {}) by {} ({})", loanId, losAppId,
				actor(), note);
		return resp("SUCCESS", "Loan " + loanId + " recorded as filed with Sambat", null);
	}

	/**
	 * What we can honestly say about a consent whose recording was lost.
	 *
	 * <p>Everything here is either known or empty. The wording version is the
	 * one configured at the time and its hash is computed from that file, so the
	 * text is still provable; the date is the loan's own consent date when it
	 * has one, and the reconciliation time otherwise.
	 */
	private void recordConsent(PaydayLoan loan) {
		PdlPersonalInfo pi = personalRepo.findByUser(loan.getUser().getId()).stream().findFirst()
				.orElse(null);
		String version = loan.getCbcConsentTextVersion() == null ? cbcTextVersion
				: loan.getCbcConsentTextVersion();

		PdlCbcConsent c = new PdlCbcConsent();
		c.setLoan(loan);
		c.setUser(loan.getUser());
		c.setConsentRef("CBC-" + loan.getId() + "-" + version);
		c.setConsentGiven(true);
		c.setConsentDate(loan.getCbcConsentDate() == null ? Instant.now() : loan.getCbcConsentDate());
		c.setTextVersion(version);
		c.setTextHash(ConsentHash.sha256(consentForm.consentTextKm(version)));
		c.setTextLanguage("km");
		c.setChannel("MOBILE_APP");
		c.setFormReference(consentForm.formReference());
		c.setIdNo(pi == null || pi.getIdNo() == null ? "" : pi.getIdNo());
		c.setCustomerNameKm("");
		c.setCustomerNameLatin("");
		// Not re-rendered: the filed document cannot be reproduced byte for byte,
		// and an approximation of evidence is not evidence.
		c.setDocumentRef("");
		c.setDocumentHash("");
		c.setAppVersion("");
		c.setDeviceModel("");
		c.setIpAddress("");
		c.setCaptureSource(RECONCILED);
		c.setLosAppId(loan.getLosAppId());
		c.setRecordedBy(actor());

		// Keep the loan's own columns consistent with the record.
		loan.setCbcConsentDate(c.getConsentDate());
		loan.setCbcConsentTextVersion(version);
		loan.setCbcConsentRef(c.getConsentRef());
		loan.setCbcConsentTextHash(c.getTextHash());
		loan.setCbcConsentLanguage("km");
		loan.setCbcConsentChannel("MOBILE_APP");

		ledger.append(c);
	}

	private PaydayLoan loanAwaiting(int loanId) {
		PaydayLoan loan = repo.findById(loanId).orElse(null);
		return loan != null && LosSubmitException.OUTCOME_UNKNOWN.equals(loan.getLosStatusCode())
				? loan
				: null;
	}

	private static String actor() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null ? "SYSTEM" : auth.getName();
	}

	private static ResponseEntity<Message<String>> resp(String type, String message, String data) {
		return new ResponseEntity<>(new Message<>(type, message, data),
				"SUCCESS".equals(type) ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED);
	}
}
