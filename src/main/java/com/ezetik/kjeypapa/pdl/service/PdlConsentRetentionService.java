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
 * <p><b>It reports. It never disposes — and as of 2026-09-05 it never will.</b>
 * Sambat ruled that consent records are not to be destroyed at all. No period is
 * configured, none is expected, and with the property unset this class reports
 * that nothing expires and stops.
 *
 * <p>It stays for two reasons. It answers "what would be eligible, if a rule
 * ever existed" without anybody writing a query against consent evidence by
 * hand; and if the ruling changes, the safe half of the work is already done and
 * reviewed. Deleting evidence on a timer is how evidence disappears the week
 * somebody needs it, so disposal would still be a deliberate act by a person who
 * had read the list.
 *
 * <p>When disposal is eventually built it must <b>redact the archived document
 * and append a redaction row to the ledger</b>, never delete a consent row. A
 * deleted row is indistinguishable from a tampered one to
 * {@link PdlCbcConsentLedger#verify()}, so disposal done the obvious way would
 * make the ledger permanently report damage that nobody could tell apart from
 * an attack. The partial unique index on {@code consent_given} already leaves
 * room for that row.
 *
 * <h2>When the clock starts</h2>
 *
 * <p>"The life of the loan file" needs a date the loan file ended.
 * {@code PaydayLoan.closedDate} records it, stamped on every transition into a
 * terminal state. Loans that closed before that column existed fall back to a
 * derived date, and every candidate names which it used — an approximation must
 * never read as a recorded fact when the consequence is destroying evidence.
 */
@Service
public class PdlConsentRetentionService {

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
			notes.add("No consent expires. Sambat ruled on 2026-09-05 that consent records are "
					+ "not to be destroyed, so no retention period is configured and none is "
					+ "expected. This report exists to answer 'what would be eligible' without "
					+ "anyone querying consent evidence by hand.");
			return new RetentionReport(false, null, now, all.size(), all.size(), List.of(), notes);
		}

		notes.add("Candidates only. Nothing here has been or will be deleted by this job.");
		notes.add("loanEndedSource says where each end date came from. 'closedDate' is recorded "
				+ "at the transition; anything marked approximate is a fallback for a loan that "
				+ "closed before that column existed and has not been saved since.");

		List<Candidate> candidates = new ArrayList<>();
		for (PdlCbcConsent c : all) {
			PaydayLoan loan = c.getLoan();
			// Everything that is not terminal — Disbursed and Active included — is a
			// live file whose consent is still in use, and no elapsed period
			// makes it a candidate.
			if (loan == null || loan.getStatus() == null || !loan.getStatus().isTerminal())
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
	 * When the loan file ended.
	 *
	 * <p>{@code closedDate} is the answer and it is recorded, not inferred —
	 * {@code PaydayLoan.stampClosure()} writes it on every transition into a
	 * terminal state. The fallbacks below exist only for rows that reached a
	 * terminal state before that column did and have not been saved since;
	 * migration 004 backfills them, so in practice they should never be reached.
	 * They stay because a retention clock that silently starts from
	 * {@code createdAt} when a date is missing would be far worse than one that
	 * says which date it used.
	 *
	 * <p>Where several dates could serve, the one that starts the clock latest
	 * wins: holding a record slightly too long is recoverable, disposing of it
	 * slightly too early is not.
	 */
	private static Instant endedOn(PaydayLoan loan) {
		if (loan.getClosedDate() != null)
			return loan.getClosedDate();
		if (loan.getStatus() == PdlStatusEnum.Revoked && loan.getRevokedDate() != null)
			return loan.getRevokedDate();
		Instant updated = loan.getUpdatedAt();
		if (loan.getStatus() == PdlStatusEnum.Closed && loan.getLastTransactionDate() != null)
			return latest(loan.getLastTransactionDate(), updated);
		return updated == null ? loan.getCreatedAt() : updated;
	}

	private static String endedSource(PaydayLoan loan) {
		if (loan.getClosedDate() != null)
			return "closedDate";
		if (loan.getStatus() == PdlStatusEnum.Revoked && loan.getRevokedDate() != null)
			return "revokedDate (no closedDate; approximate)";
		if (loan.getStatus() == PdlStatusEnum.Closed && loan.getLastTransactionDate() != null)
			return loan.getUpdatedAt() != null && loan.getUpdatedAt().isAfter(loan.getLastTransactionDate())
					? "updatedAt (no closedDate; approximate)"
					: "lastTransactionDate (no closedDate; approximate)";
		return loan.getUpdatedAt() == null ? "createdAt (no closedDate; approximate)"
				: "updatedAt (no closedDate; approximate)";
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
