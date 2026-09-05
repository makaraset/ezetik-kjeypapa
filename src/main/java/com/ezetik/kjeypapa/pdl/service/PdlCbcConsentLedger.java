package com.ezetik.kjeypapa.pdl.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.repository.PdlCbcConsentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * The only thing that writes consent records, and the only thing that can say
 * whether they have been altered.
 *
 * <p>Records are chained: each carries the hash of its predecessor, so editing
 * one row breaks every link after it. That does not make tampering impossible —
 * the application connects to Postgres as a superuser, and a superuser can
 * disable the trigger that blocks updates and recompute the chain. It makes
 * tampering <em>detectable</em>, and the nightly job logs the head hash, so the
 * tip of the chain also exists somewhere the database cannot rewrite.
 */
@Service
public class PdlCbcConsentLedger {

	/** A fixed lock id, not {@code hashtext()}, whose value is not guaranteed stable across major versions. */
	private static final long APPEND_LOCK = 87310042L;

	/** {@code captureSource} of a consent captured as it happened, rather than reconstructed. */
	public static final String LIVE = "SUBMIT";

	@Autowired
	private PdlCbcConsentRepository repo;

	@PersistenceContext
	private EntityManager em;

	/**
	 * Appends a record, chaining it to the current tail.
	 *
	 * <p>Runs in the caller's transaction: a consent must commit with the
	 * application it belongs to, or roll back with it.
	 */
	@Transactional
	public PdlCbcConsent append(PdlCbcConsent record) {
		requireEvidence(record);

		// Serialise appends. Without this two submits can read the same tail and
		// chain themselves to it, forking the chain — and a fork is
		// indistinguishable from a deletion when the chain is later walked.
		em.createNativeQuery("select pg_advisory_xact_lock(:k)")
				.setParameter("k", APPEND_LOCK).getSingleResult();

		record.setCanonVersion(ConsentHash.CANON_VERSION);
		record.setConsentDate(ConsentHash.stamp(record.getConsentDate()));
		record.setPrevHash(repo.lockTail().map(PdlCbcConsent::getRowHash).orElse(ConsentHash.GENESIS));
		// Set before hashing, and on a column the auditing listener does not own —
		// see PdlCbcConsent.recordedBy.
		if (blank(record.getRecordedBy()))
			record.setRecordedBy(currentActor());
		record.setRowHash(ConsentHash.rowHash(record));
		return repo.save(record);
	}

	/** Whether any consent has been captured live yet — see {@code PdlConsentBackfillService}. */
	public boolean hasLiveRecords() {
		return repo.existsByCaptureSource(LIVE);
	}

	/**
	 * A live consent must arrive with the three facts that make it evidence: the
	 * hash of the wording agreed to, the language it was read in, and the channel
	 * it was given through.
	 *
	 * <p>The database cannot enforce this, because it must keep accepting the
	 * pre-ledger records that have none of them. So the rule lives here, where
	 * the difference between "recorded before we captured this" and "captured
	 * badly today" is actually known.
	 */
	private static void requireEvidence(PdlCbcConsent r) {
		if (!LIVE.equals(r.getCaptureSource()))
			return;
		if (blank(r.getTextHash()) || blank(r.getTextLanguage()) || blank(r.getChannel()))
			throw new LosSubmitException("CONSENT_INCOMPLETE",
					"A consent cannot be recorded without the wording hash, its language "
							+ "and the channel it was given through.");
	}

	// ----- verification -----

	/** One thing wrong with one record. {@code layer} is {@code CHAIN} or {@code EVIDENCE}. */
	public record Finding(Integer id, String consentRef, String layer, String problem) {
	}

	/**
	 * The result of walking the chain.
	 *
	 * <p>{@code broken} and {@code incomplete} are counted apart because they
	 * mean opposite things. A broken record is a sign that something was altered
	 * and needs investigating. An incomplete one is an honest record of a consent
	 * taken before we captured that fact; it is expected, it cannot be fixed
	 * without inventing data, and folding it into a failure count would leave the
	 * ledger permanently reporting a problem nobody can ever clear.
	 */
	public record LedgerReport(int checked, int broken, int incomplete, String headId,
			String headHash, List<Finding> findings) {
	}

	/**
	 * Walks the chain and reports every record that does not hold up.
	 *
	 * <p>A single edit breaks two things — the edited row's own hash, and the
	 * link from the row after it — so the two are reported differently. Without
	 * that distinction every record after a tampered one looks equally guilty
	 * and the earliest damage is hard to find.
	 */
	public LedgerReport verify() {
		return walk(repo.findAllByOrderByIdAsc());
	}

	/**
	 * The walk itself, over records already in id order.
	 *
	 * <p>Separate from {@link #verify()} and free of the repository so it can be
	 * tested against chains a database will not hold — a record removed from the
	 * middle, a row altered and its hash recomputed to match — which are exactly
	 * the cases it exists to catch.
	 */
	public static LedgerReport walk(List<PdlCbcConsent> all) {
		List<Finding> chain = new ArrayList<>();
		List<Finding> evidence = new ArrayList<>();
		String expectedPrev = ConsentHash.GENESIS;
		boolean upstreamBroken = false;

		for (PdlCbcConsent c : all) {
			String recomputed = ConsentHash.rowHash(c);
			if (!recomputed.equals(c.getRowHash()))
				chain.add(new Finding(c.getId(), c.getConsentRef(), "CHAIN",
						"row hash does not match its contents — this record was altered"));
			else if (!expectedPrev.equals(c.getPrevHash()))
				chain.add(new Finding(c.getId(), c.getConsentRef(), "CHAIN",
						upstreamBroken ? "link broken upstream — an earlier record was altered or removed"
								: "does not follow the previous record — a record was removed"));
			if (!recomputed.equals(c.getRowHash()) || !expectedPrev.equals(c.getPrevHash()))
				upstreamBroken = true;
			expectedPrev = c.getRowHash();

			String missing = missingEvidence(c);
			if (!missing.isEmpty())
				evidence.add(new Finding(c.getId(), c.getConsentRef(), "EVIDENCE",
						"recorded by " + c.getCaptureSource() + " without " + missing
								+ " — never captured, not lost"));
		}

		PdlCbcConsent head = all.isEmpty() ? null : all.get(all.size() - 1);
		chain.addAll(evidence);
		return new LedgerReport(all.size(), chain.size() - evidence.size(), evidence.size(),
				head == null ? "" : String.valueOf(head.getId()),
				head == null ? "" : head.getRowHash(), chain);
	}

	/** Names what a record cannot prove, in the order an auditor would ask. */
	private static String missingEvidence(PdlCbcConsent c) {
		List<String> gaps = new ArrayList<>();
		if (blank(c.getTextHash()))
			gaps.add("the wording hash");
		if (blank(c.getTextLanguage()))
			gaps.add("the language");
		if (blank(c.getChannel()))
			gaps.add("the channel");
		return String.join(", ", gaps);
	}

	private static boolean blank(String v) {
		return v == null || v.isBlank();
	}

	private static String currentActor() {
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
				.getAuthentication();
		return auth == null || !auth.isAuthenticated() ? "SYSTEM" : auth.getName();
	}
}
