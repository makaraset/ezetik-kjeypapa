package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PdlCbcConsentRepository;

/**
 * Which consent records have outlived the loan file they belong to.
 *
 * <p><b>It reports. It never disposes.</b> Deleting evidence on a timer is how
 * evidence disappears the week somebody needs it, and the retention period is
 * not settled — the product decision was "same life as the loan file, confirm
 * with Sambat", and that confirmation has not arrived. So this computes a list
 * and stops. Disposal stays a deliberate act by a person who has read the list.
 *
 * <p>When disposal is eventually built it must <b>redact the archived document
 * and append a redaction row to the ledger</b>, never delete a consent row. A
 * deleted row is indistinguishable from a tampered one to
 * {@link PdlCbcConsentLedger#verify()}, so disposal done the obvious way would
 * make the ledger permanently report damage that nobody could tell apart from
 * an attack. The partial unique index on {@code consent_given} already leaves
 * room for that row.
 *
 * <h2>The clock has no exact start</h2>
 *
 * <p>"The life of the loan file" needs a date the loan file ended, and the
 * schema does not record one. There is no {@code closedDate}. What exists is a
 * revocation date, a last-transaction date, and the audit {@code updatedAt},
 * which is a proxy that drifts if anything touches the row. Every candidate
 * therefore names the date used and where it came from, so nobody reads an
 * approximation as a fact. Recorded as a finding; the right fix is a real
 * closure timestamp, stamped when the rule is confirmed.
 */
@Service
public class PdlConsentRetentionService {

	/**
	 * States in which the loan file is over. Everything else — including
	 * Disbursed and Active — is a live file whose consent is still in use, and
	 * no elapsed period makes it a candidate.
	 */
	private static final Set<PdlStatusEnum> FINISHED = Set.of(
			PdlStatusEnum.Closed, PdlStatusEnum.Rejected, PdlStatusEnum.Revoked);

	@Autowired
	private PdlCbcConsentRepository consentRepo;

	/** Blank means nothing ever expires, which is the deliberate default. */
	@Value("${pdl.consent.retention-years:}")
	private String retentionYears;

	/** One consent that could be disposed of, and the reasoning behind saying so. */
	public record Candidate(Integer consentId, String consentRef, Integer loanId, String loanStatus,
			Instant loanEnded, String loanEndedSource, Instant eligibleFrom) {
	}

	public record RetentionReport(boolean configured, Integer retentionYears, Instant asOf,
			int consentsChecked, int stillRetained, List<Candidate> candidates, List<String> notes) {
	}

	public RetentionReport report() {
		Instant now = Instant.now();
		List<PdlCbcConsent> all = consentRepo.findAllByOrderByIdAsc();
		List<String> notes = new ArrayList<>();
		Integer years = years();

		if (years == null) {
			notes.add("pdl.consent.retention-years is not set, so no consent ever expires. "
					+ "This is the default and it is deliberate: the period is 'same life as the "
					+ "loan file', pending confirmation from Sambat.");
			return new RetentionReport(false, null, now, all.size(), all.size(), List.of(), notes);
		}

		notes.add("Candidates only. Nothing here has been or will be deleted by this job.");
		notes.add("The loan-file end date is derived, not recorded — see loanEndedSource on each "
				+ "candidate. A row showing 'updatedAt' is an approximation.");

		List<Candidate> candidates = new ArrayList<>();
		for (PdlCbcConsent c : all) {
			PaydayLoan loan = c.getLoan();
			if (loan == null || loan.getStatus() == null || !FINISHED.contains(loan.getStatus()))
				continue;

			Instant ended = endedOn(loan);
			if (ended == null)
				continue;

			Instant eligible = ended.atZone(ZoneOffset.UTC).plusYears(years).toInstant();
			if (eligible.isAfter(now))
				continue;

			candidates.add(new Candidate(c.getId(), c.getConsentRef(), loan.getId(),
					loan.getStatus().name(), ended, endedSource(loan), eligible));
		}

		return new RetentionReport(true, years, now, all.size(), all.size() - candidates.size(),
				candidates, notes);
	}

	/**
	 * The best available end date for a finished loan.
	 *
	 * <p>Deliberately conservative: where several dates could serve, the one
	 * that starts the clock latest wins, because holding a record slightly too
	 * long is recoverable and disposing of it slightly too early is not.
	 */
	private static Instant endedOn(PaydayLoan loan) {
		if (loan.getStatus() == PdlStatusEnum.Revoked && loan.getRevokedDate() != null)
			// Via epoch millis, NOT toInstant(): revokedDate is a java.sql.Date,
			// whose toInstant() throws UnsupportedOperationException. It is also
			// date-only, which costs nothing against a clock measured in years.
			return Instant.ofEpochMilli(loan.getRevokedDate().getTime());
		Instant updated = loan.getUpdatedAt();
		if (loan.getStatus() == PdlStatusEnum.Closed && loan.getLastTransactionDate() != null)
			return latest(loan.getLastTransactionDate(), updated);
		return updated == null ? loan.getCreatedAt() : updated;
	}

	private static String endedSource(PaydayLoan loan) {
		if (loan.getStatus() == PdlStatusEnum.Revoked && loan.getRevokedDate() != null)
			return "revokedDate";
		if (loan.getStatus() == PdlStatusEnum.Closed && loan.getLastTransactionDate() != null)
			return loan.getUpdatedAt() != null && loan.getUpdatedAt().isAfter(loan.getLastTransactionDate())
					? "updatedAt (later than lastTransactionDate; approximate)"
					: "lastTransactionDate";
		return loan.getUpdatedAt() == null ? "createdAt (approximate)" : "updatedAt (approximate)";
	}

	private static Instant latest(Instant a, Instant b) {
		if (a == null)
			return b;
		if (b == null)
			return a;
		return a.isAfter(b) ? a : b;
	}

	/** @return the configured period, or null when unset or unusable */
	private Integer years() {
		if (retentionYears == null || retentionYears.isBlank())
			return null;
		try {
			int n = Integer.parseInt(retentionYears.trim());
			// A zero or negative period would make every finished loan's consent
			// a candidate the moment it closed, which is certainly a typo.
			return n > 0 ? n : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
