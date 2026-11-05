package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.security.model.User;

import jakarta.transaction.Transactional;

/**
 * V21 acceptance cut-off + reminders. While a PDL offer is {@code Approved} but
 * not yet {@code Accepted}, the applicant is reminded periodically to confirm
 * the e-contract; offers still un-accepted after the daily cut-off are
 * auto-rejected (status → {@code Rejected}) and an "N" decision is relayed to LOS.
 *
 * Enabled by default — disable via {@code pdl.acceptance.scheduler.enabled=false}
 * if Sambat/LOS takes ownership of the cut-off (Q3). Cron/grace are configurable.
 */
@Service
@ConditionalOnProperty(name = "pdl.acceptance.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PdlAcceptanceScheduler {

	@Autowired
	private PaydayLoanRepository repo;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private LosProvider losProvider;

	@Value("${pdl.acceptance.grace-minutes:30}")
	private long graceMinutes;

	/**
	 * Whether accepting is followed by the bank hand-off (V26 page 3). Only then
	 * does an Accepted loan still wait on the customer. Without it, accepting
	 * goes straight to Sambat as /customer-accepted, and the loan waits for
	 * their disbursement. The same switch as the bank-verification webhook's.
	 */
	@Value("${pdl.bank-verification.enabled:false}")
	private boolean bankHandoffEnabled;

	/**
	 * Sambat's wording for an offer that lapsed unconfirmed (Mobile App
	 * Dictionary, 30 Oct 2026). It goes in the push and the record; the app
	 * shows its own copy, in the customer's language.
	 */
	private static final String LAPSED = "Apologies. Your loan request is lapsed due to failure in providing "
			+ "your final decision. Please try it again on the next working day.";

	/** Remind applicants with an Approved (un-accepted) offer to confirm before cut-off. */
	@Scheduled(cron = "${pdl.acceptance.reminder-cron:0 0/30 8-16 * * *}",
			zone = "${pdl.acceptance.timezone:Asia/Phnom_Penh}")
	@Transactional
	public void sendReminders() {
		for (PaydayLoan loan : repo.findByStatus(PdlStatusEnum.Approved)) {
			notify(loan, "Confirm your loan offer",
					"Please review and confirm your loan offer before 5 PM today.", "acceptance_reminder");
		}
	}

	/** Auto-reject offers not confirmed before the daily cut-off. */
	@Scheduled(cron = "${pdl.acceptance.cutoff-cron:0 0 17 * * *}",
			zone = "${pdl.acceptance.timezone:Asia/Phnom_Penh}")
	@Transactional
	public void enforceCutoff() {
		Instant threshold = Instant.now().minus(graceMinutes, ChronoUnit.MINUTES);
		// Approved = never confirmed, so it expires. Accepted expires only while
		// the bank hand-off is on: then it means confirmed but the hand-off never
		// succeeded (QB4.1: Re-Attempt is disabled once the cut-off passes).
		// Without the hand-off, accepting already told Sambat, who disburse it,
		// and their API has no call to tell them we expired it, so it waits for
		// their disbursement webhook. Pending_Bank_Verification is in-flight at
		// the bank and is left alone.
		java.util.List<PaydayLoan> sweep = new java.util.ArrayList<>(repo.findByStatus(PdlStatusEnum.Approved));
		if (bankHandoffEnabled)
			sweep.addAll(repo.findByStatus(PdlStatusEnum.Accepted));
		for (PaydayLoan loan : sweep) {
			// Skip offers approved within the grace window (e.g. just before the cut-off).
			if (loan.getApprovedDate() != null && loan.getApprovedDate().isAfter(threshold))
				continue;

			loan.setStatus(PdlStatusEnum.Rejected);
			// R-CUTOFF is our own marker, never sent to Sambat (their code for a
			// lapse is E, which we do not send yet: backlog F25). The app words
			// the reason from it.
			loan.setLosStatusCode("R-CUTOFF");
			loan.setLosMessage(LAPSED);
			repo.save(loan);

			try {
				losProvider.sendDecision(loan, "N", null);
			} catch (Exception e) {
				e.printStackTrace();
			}
			notify(loan, "Loan offer expired", LAPSED, "loan_expired");
		}
	}

	private void notify(PaydayLoan loan, String title, String body, String type) {
		try {
			User u = loan.getUser();
			if (u != null && u.getFcmToken() != null) {
				notificationService.postToClient(new NotificationMessage(
						u.getFcmToken(), title, body, loan.getId().toString(), u.getUsername(),
						type, loan.getId().toString()));
			}
		} catch (Exception e) {
			// A notification failure must never break the sweep.
			e.printStackTrace();
		}
	}
}
