package com.ezetik.kjeypapa.pdl.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService.Candidate;
import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService.RetentionReport;

import lombok.extern.slf4j.Slf4j;

/**
 * Ships dormant, and reports rather than disposes.
 *
 * <p>{@code matchIfMissing = false} is the whole point of the annotation here:
 * unlike every other scheduler in this package, this one does not exist unless
 * somebody deliberately turns it on. A retention job that runs by default is a
 * job that starts counting down against evidence before anyone has agreed how
 * long the count should be.
 *
 * <p>Even switched on it only writes a log line. Disposal is a human act — see
 * {@link PdlConsentRetentionService} for what it must do when it is built.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pdl.consent.retention.enabled", havingValue = "true",
		matchIfMissing = false)
public class PdlConsentRetentionScheduler {

	@Autowired
	private PdlConsentRetentionService retention;

	@Scheduled(cron = "${pdl.consent.retention-cron:0 45 2 * * *}",
			zone = "${pdl.consent.ledger.timezone:Asia/Phnom_Penh}")
	public void reportNightly() {
		RetentionReport r = retention.report();

		if (!r.configured()) {
			// Enabled but with no period set. Worth saying out loud, because the
			// operator who switched this on is expecting it to do something.
			log.info("CONSENT-RETENTION IDLE no period configured; {} consent(s) retained",
					r.consentsChecked());
			return;
		}

		if (r.candidates().isEmpty()) {
			log.info("CONSENT-RETENTION OK years={} consents={} candidates=0",
					r.retentionYears(), r.consentsChecked());
			return;
		}

		log.info("CONSENT-RETENTION CANDIDATES years={} consents={} candidates={} (reported only, "
				+ "nothing deleted)", r.retentionYears(), r.consentsChecked(), r.candidates().size());
		for (Candidate c : r.candidates())
			log.info("CONSENT-RETENTION CANDIDATE consent={} ref={} loan={} status={} ended={} ({}) "
					+ "eligible={}", c.consentId(), c.consentRef(), c.loanId(), c.loanStatus(),
					c.loanEnded(), c.loanEndedSource(), c.eligibleFrom());
	}
}
