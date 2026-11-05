package com.ezetik.kjeypapa.pdl.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlCbcConsentRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings consents recorded before the ledger existed into the chain.
 *
 * <p>Written in Java rather than as SQL in the migration on purpose: the
 * canonical form and the hashing live in {@link ConsentHash}, and writing them
 * a second time in SQL would create two definitions of the same evidence that
 * can drift. If they ever disagreed by one byte the chain would be worthless
 * from its first record.
 *
 * <p><b>Must run before the first live append.</b> A hash chain can be appended
 * to but not prepended to, so a consent that predates the ledger can only join
 * it while the chain is still empty of live records — hence the guard below.
 */
@Slf4j
@Service
public class PdlConsentBackfillService {

	static final String SOURCE = "MIGRATION_003";

	@Autowired
	private PaydayLoanRepository loanRepo;

	@Autowired
	private PdlCbcConsentRepository consentRepo;

	@Autowired
	private PdlCbcConsentLedger ledger;

	/** @return how many records were appended */
	@Transactional
	public int backfill() {
		if (ledger.hasLiveRecords())
			throw new LosSubmitException("LEDGER_ALREADY_LIVE",
					"Consents are already being recorded live. Earlier consents can no longer "
							+ "join the chain, because a hash chain cannot be prepended to.");

		List<PaydayLoan> legacy = loanRepo.findAll().stream()
				.filter(l -> l.getCbcConsentDate() != null)
				.filter(l -> consentRepo.findByLoan_Id(l.getId()).isEmpty())
				// Chain them in the order the consents actually happened.
				.sorted(Comparator.comparing(PaydayLoan::getCbcConsentDate)
						.thenComparing(PaydayLoan::getId))
				.toList();

		for (PaydayLoan loan : legacy)
			ledger.append(reconstruct(loan));

		log.info("CBC consent backfill: {} record(s) appended", legacy.size());
		return legacy.size();
	}

	/**
	 * What we actually know about a consent taken before the ledger existed.
	 *
	 * <p>The archived document and the capture context are empty, not guessed.
	 * The document filed for these applications is genuinely unrecoverable — it
	 * was rendered with a timestamp taken at render time, so it cannot be
	 * reproduced byte-for-byte even in principle — and {@code captureSource}
	 * says so, rather than the record implying an archive that never existed.
	 *
	 * <p>{@code textHash} is copied, never recomputed: recomputing would paper
	 * over a wording file that has since been edited, which is precisely what
	 * verification exists to catch.
	 */
	private PdlCbcConsent reconstruct(PaydayLoan loan) {
		PdlCbcConsent c = new PdlCbcConsent();
		c.setLoan(loan);
		c.setUser(loan.getUser());
		c.setConsentRef(nz(loan.getCbcConsentRef()));
		c.setConsentGiven(true);
		c.setConsentDate(loan.getCbcConsentDate());
		c.setTextVersion(nz(loan.getCbcConsentTextVersion()));
		c.setTextHash(nz(loan.getCbcConsentTextHash()));
		c.setTextLanguage(nz(loan.getCbcConsentLanguage()));
		c.setChannel(nz(loan.getCbcConsentChannel()));
		c.setFormReference("");
		c.setIdNo("");
		c.setCustomerNameKm("");
		c.setCustomerNameLatin("");
		c.setDocumentRef("");
		c.setDocumentHash("");
		c.setAppVersion("");
		c.setDeviceModel("");
		c.setIpAddress("");
		c.setCaptureSource(SOURCE);
		c.setLosAppId(loan.getLosAppId());
		c.setRecordedBy(SOURCE);
		return c;
	}

	private static String nz(String v) {
		return v == null ? "" : v;
	}
}
