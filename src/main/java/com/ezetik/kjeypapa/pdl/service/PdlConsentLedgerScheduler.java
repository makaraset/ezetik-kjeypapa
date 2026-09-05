package com.ezetik.kjeypapa.pdl.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.ArchiveReport;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.Finding;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.LedgerReport;

import lombok.extern.slf4j.Slf4j;

/**
 * Walks the consent ledger nightly and writes its head hash to the log.
 *
 * <p>The logging is the point, not the walking. Everything else in this design
 * is enforced inside the database, and a superuser can undo all of it — disable
 * the trigger, alter a record, recompute the chain from that record onward, and
 * the ledger verifies again with no trace. What that attack cannot reach is a
 * head hash already written somewhere else. So this job emits the hash every
 * night to the application log, which ships off the database host: a chain whose
 * head disagrees with last night's line, over records that were all supposedly
 * final, is evidence no in-database edit can erase.
 *
 * <p>For that reason the log line is deliberately greppable and stable in shape.
 * Anything that ingests these logs can alert on {@code CONSENT-LEDGER FAILED}
 * and archive the head hash from {@code CONSENT-LEDGER OK}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pdl.consent.ledger.scheduler.enabled", havingValue = "true",
		matchIfMissing = true)
public class PdlConsentLedgerScheduler {

	@Autowired
	private PdlCbcConsentLedger ledger;

	@Scheduled(cron = "${pdl.consent.ledger.verify-cron:0 15 2 * * *}",
			zone = "${pdl.consent.ledger.timezone:Asia/Phnom_Penh}")
	public void verifyNightly() {
		LedgerReport r = ledger.verify();

		if (r.checked() == 0) {
			log.info("CONSENT-LEDGER EMPTY records=0");
			return;
		}

		// The chain protects the records; it says nothing about the documents
		// they point at, which live in an ordinary table with no trigger on it.
		// A document can be swapped without touching a consent row at all.
		ArchiveReport a = ledger.verifyArchives();
		for (Finding f : a.findings())
			log.error("CONSENT-ARCHIVE FAILED id={} ref={} {}", f.id(), f.consentRef(), f.problem());

		if (r.broken() == 0 && a.findings().isEmpty()) {
			// One line, always the same shape, carrying the value worth keeping.
			log.info("CONSENT-LEDGER OK records={} head_id={} head_hash={} incomplete={} documents={}",
					r.checked(), r.headId(), r.headHash(), r.incomplete(), a.checked());
			return;
		}

		if (r.broken() == 0) {
			log.error("CONSENT-ARCHIVE FAILED documents={} missing={} altered={} (chain itself intact,"
					+ " head_id={} head_hash={})", a.checked(), a.missing(), a.altered(), r.headId(),
					r.headHash());
			return;
		}

		// Loud, and specific enough to act on without opening the database.
		log.error("CONSENT-LEDGER FAILED records={} broken={} head_id={} head_hash={}",
				r.checked(), r.broken(), r.headId(), r.headHash());
		for (Finding f : r.findings())
			if ("CHAIN".equals(f.layer()))
				log.error("CONSENT-LEDGER FAILED id={} ref={} {}", f.id(), f.consentRef(), f.problem());
	}
}
