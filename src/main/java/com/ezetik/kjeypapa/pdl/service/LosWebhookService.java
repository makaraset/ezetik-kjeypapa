package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlPaymentSchedule;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.LosBankVerificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosDisbursementPayload;
import com.ezetik.kjeypapa.pdl.payload.LosLoanUpdatePayload;
import com.ezetik.kjeypapa.pdl.payload.LosLoanUpdatePayload.PaidRow;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload.ScheduleRow;
import com.ezetik.kjeypapa.pdl.payload.LosProductSyncPayload;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPaymentScheduleRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;

/**
 * Ingests inbound Sambat LOS callbacks (BRS 2.2/2.4/2.5/2.6): transitions the
 * {@link PaydayLoan} status, stores LOS-pushed documents/schedule, then notifies
 * the customer. Endpoints are exposed by {@code LosWebhookController}.
 *
 * Callers are authenticated before they get here, by
 * {@code LosWebhookAuthInterceptor} (shared secret and/or address allowlist).
 */
@Service
public class LosWebhookService {

	@Autowired
	private PaydayLoanRepository repo;

	@Autowired
	private PdlPaymentScheduleRepository scheduleRepo;

	@Autowired
	private LosProvider losProvider;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private PdlAccountRequestRepository accountRequestRepo;

	@Autowired
	private PdlAccountRequestService accountRequestService;

	@Autowired
	private com.ezetik.kjeypapa.sbf.service.SMSService sms;

	/**
	 * V21 bank-app disbursement-consent hand-off. Off by default: the deep-link +
	 * callback contract is still pending Sambat/Bank (Q4). When false, the endpoint
	 * is reachable but no-ops so nothing transitions on a stray/early callback.
	 */
	@Value("${pdl.bank-verification.enabled:false}")
	private boolean bankVerificationEnabled;

	/** The number Sambat's rejection wording asks the customer to call. */
	@Value("${pdl.customer.hotline:023 9977 22 or 093 99 77 22}")
	private String hotline;

	/** BRS 2.4 — reject. */
	@Transactional
	public ResponseEntity<Message<String>> handleReject(LosNotificationPayload p) {
		try {
			if (!identified(p))
				return noIdentifier();
			PaydayLoan loan = find(p);
			if (loan == null)
				return notFound(p);

			// In-order / idempotent: only a Submitted or Approved application can be rejected.
			if (loan.getStatus() != PdlStatusEnum.Submitted && loan.getStatus() != PdlStatusEnum.Approved)
				return ok("Ignored — application is " + loan.getStatus());

			loan.setStatus(PdlStatusEnum.Rejected);
			loan.setLosStatusCode(p.getStatusCode());
			loan.setLosMessage(messageForCode(p.getStatusCode(), p.getMessage()));
			repo.save(loan);

			notify(loan, "Loan application rejected", rejectionNotice(loan), "loan_rejected");
			return ok("Reject processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** BRS 2.5 — rework. PDL has no true rework: rework = reject (R-LPO / R-AO). */
	@Transactional
	public ResponseEntity<Message<String>> handleRework(LosNotificationPayload p) {
		try {
			if (!identified(p))
				return noIdentifier();
			PaydayLoan loan = find(p);
			if (loan == null)
				return notFound(p);

			// PDL: rework == reject. Only act on a Submitted or Approved application.
			if (loan.getStatus() != PdlStatusEnum.Submitted && loan.getStatus() != PdlStatusEnum.Approved)
				return ok("Ignored — application is " + loan.getStatus());

			loan.setStatus(PdlStatusEnum.Rejected);
			loan.setLosStatusCode(p.getStatusCode());
			loan.setLosMessage(messageForCode(p.getStatusCode(), p.getMessage()));
			repo.save(loan);

			// The customer is told it was rejected: Sambat's dictionary has no
			// rework for PDL, only R-LPO / R-AO and their rejection wording.
			notify(loan, "Loan application rejected", rejectionNotice(loan), "loan_rejected");
			return ok("Rework processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** BRS 2.6 — approved; LOS pushes the generated docs + repayment schedule. */
	@Transactional
	public ResponseEntity<Message<String>> handleApproved(LosNotificationPayload p) {
		try {
			if (!identified(p))
				return noIdentifier();
			PaydayLoan loan = find(p);
			if (loan == null)
				return notFound(p);

			// Only a Submitted application is approvable (Approved allowed = re-delivery refresh).
			if (loan.getStatus() != PdlStatusEnum.Submitted && loan.getStatus() != PdlStatusEnum.Approved)
				return ok("Ignored — application is " + loan.getStatus());

			loan.setStatus(PdlStatusEnum.Approved);
			loan.setApprovedDate(Instant.now()); // starts the acceptance cut-off clock (V21)
			loan.setLosStatusCode(p.getStatusCode());
			loan.setLosMessage(p.getMessage());
			// A field the body leaves out keeps what we hold, like the fee set
			// below. Above all the currency: it is the application's own, and
			// the acceptance page shows anything but KHR as dollars, so an
			// approve without it put riel amounts on screen as USD.
			if (sent(p.getLoanFormRef()))
				loan.setLoanFormRef(p.getLoanFormRef());
			if (sent(p.getLoanContractFileRef()))
				loan.setLoanContractFileRef(p.getLoanContractFileRef());
			if (sent(p.getRepaymentScheduleRef()))
				loan.setRepaymentScheduleRef(p.getRepaymentScheduleRef());
			// Active-loan detail (My Loan card).
			if (sent(p.getLoanRefNo()))
				loan.setLoanRefNo(p.getLoanRefNo());
			if (sent(p.getSettlementAccountNo()))
				loan.setSettlementAccountNo(p.getSettlementAccountNo());
			if (sent(p.getCurrency()))
				loan.setCurrency(p.getCurrency());
			if (p.getTenor() != null)
				loan.setTenor(p.getTenor());
			if (p.getOutstandingAmount() != null)
				loan.setOutstandingAmount(p.getOutstandingAmount());
			if (sent(p.getLoanDocRef()))
				loan.setLoanDocRef(p.getLoanDocRef());
			// Quote/fee detail: LOS-pushed values override the create-time quote (QC1.3).
			if (p.getRepaymentAmount() != null)
				loan.setRepaymentAmount(p.getRepaymentAmount());
			if (p.getInterestAmount() != null)
				loan.setInterestAmount(p.getInterestAmount());
			if (p.getInterestRatePercent() != null)
				loan.setInterestRatePercent(p.getInterestRatePercent());
			if (p.getProcessingFee() != null)
				loan.setProcessingFee(p.getProcessingFee());
			if (p.getCbcEnquiryFee() != null)
				loan.setCbcEnquiryFee(p.getCbcEnquiryFee());
			if (p.getNetDisbursedAmount() != null)
				loan.setNetDisbursedAmount(p.getNetDisbursedAmount());
			if (p.getLoanPeriodDays() != null)
				loan.setLoanPeriodDays(p.getLoanPeriodDays());
			loan.setDaysPastDue(0);
			loan.setOverduePayment(0.0);
			repo.save(loan);

			// Store the LOS-pushed repayment schedule (backend stores, does not compute).
			if (p.getSchedule() != null) {
				// Idempotent on re-delivery: replace any rows from a previous approval.
				List<PdlPaymentSchedule> previous = scheduleRepo.findSchedule(loan.getId());
				if (!previous.isEmpty())
					scheduleRepo.deleteAll(previous);
				for (ScheduleRow r : p.getSchedule()) {
					PdlPaymentSchedule s = new PdlPaymentSchedule();
					s.setPdl(loan);
					s.setInstallmentNo(r.getInstallmentNo());
					s.setDueDate(r.getDueDate());
					s.setPrincipalDue(r.getPrincipalDue());
					s.setInterestDue(r.getInterestDue());
					s.setFeeDue(r.getFeeDue());
					s.setOtherDue(r.getOtherDue());
					s.setTotalDue(r.getTotalDue());
					s.setStatus("PENDING");
					scheduleRepo.save(s);
				}
			}

			notify(loan, "Loan approved", "loan_approved");
			return ok("Approved processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** BRS 2.2 — new/update loan product. */
	public ResponseEntity<Message<String>> handleProductSync(LosProductSyncPayload p) {
		try {
			losProvider.onProductSync(p);
			return ok("Product synced");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** Disbursement status (after accept, LOS runs the Campu Bank direct-debit). */
	@Transactional
	public ResponseEntity<Message<String>> handleDisbursement(LosDisbursementPayload p) {
		try {
			if (!identified(p.getMobileAppId(), p.getAppId()))
				return noIdentifier();
			PaydayLoan loan = findLoan(p.getMobileAppId(), p.getAppId());
			if (loan == null)
				return notFound(p.getMobileAppId(), p.getAppId());

			if ("DISBURSED".equalsIgnoreCase(p.getDisbursementStatus())) {
				loan.setDisbursementTxnId(p.getDisbursementTxnId());
				if (p.getDisbursedDate() != null)
					loan.setDisbursementDate(p.getDisbursedDate());
				loan.setStatus(PdlStatusEnum.Disbursed);
				repo.save(loan);
				notify(loan, "Loan disbursed", "loan_disbursed");
			} else if ("FAILED".equalsIgnoreCase(p.getDisbursementStatus())) {
				loan.setLosMessage(p.getFailureReason());
				repo.save(loan);
				notify(loan, "Disbursement failed", "disbursement_failed");
			} else {
				repo.save(loan); // PENDING / other — no state change
			}
			return ok("Disbursement processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/**
	 * V21 Bank-Mobile-App verification / disbursement-consent result (after Accept).
	 * SCAFFOLDING — gated by {@code pdl.bank-verification.enabled} (default off) until
	 * the Sambat/Bank contract (Q4) is confirmed. Conservative transitions:
	 * <ul>
	 *   <li>SUCCESS: advance an {@code Accepted} loan to {@code Pending_Bank_Verification}
	 *       (the disbursement webhook remains authoritative for {@code Disbursed} — Q1.8).</li>
	 *   <li>FAILED: reject with the bank-verification message so the app can offer re-attempt.</li>
	 *   <li>PENDING/other: no state change.</li>
	 * </ul>
	 */
	@Transactional
	public ResponseEntity<Message<String>> handleBankVerification(LosBankVerificationPayload p) {
		try {
			if (!bankVerificationEnabled)
				return ok("Ignored — bank-verification hand-off not enabled (pending Sambat contract, Q4)");

			if (!identified(p.getMobileAppId(), p.getAppId()))
				return noIdentifier();
			PaydayLoan loan = findLoan(p.getMobileAppId(), p.getAppId());
			if (loan == null)
				return notFound(p.getMobileAppId(), p.getAppId());

			String status = p.getVerificationStatus();
			if ("SUCCESS".equalsIgnoreCase(status)) {
				// Only advance from Accepted (or re-delivery of the same state).
				if (loan.getStatus() == PdlStatusEnum.Accepted
						|| loan.getStatus() == PdlStatusEnum.Pending_Bank_Verification) {
					loan.setStatus(PdlStatusEnum.Pending_Bank_Verification);
					loan.setLosMessage(null); // clear any earlier failure message
					if (p.getDisbursementTxnId() != null)
						loan.setDisbursementTxnId(p.getDisbursementTxnId());
					repo.save(loan);
					notify(loan, "Bank verification successful", "bank_verification_ok");
				}
				return ok("Bank verification success processed");
			} else if ("FAILED".equalsIgnoreCase(status)) {
				// Sambat 2026-08-13 (QB4.1-4.3): a hand-off failure does NOT reject —
				// the application stays under Approved/Accepted and the customer
				// Re-Attempts until the daily cut-off sweep expires it. No failure
				// codes exist (QB4.3) — message only.
				if (loan.getStatus() == PdlStatusEnum.Accepted
						|| loan.getStatus() == PdlStatusEnum.Pending_Bank_Verification) {
					loan.setStatus(PdlStatusEnum.Accepted); // re-attemptable
					loan.setLosMessage("Bank verification did not complete — please re-attempt");
					repo.save(loan);
					notify(loan, "Bank verification failed", "bank_verification_failed");
				}
				return ok("Bank verification failure processed");
			}
			return ok("Bank verification pending"); // PENDING / unknown — no transition
		} catch (Exception e) {
			return error(e);
		}
	}

	/** Loan / repayment status update — refreshes outstanding + per-installment paid. */
	@Transactional
	public ResponseEntity<Message<String>> handleLoanUpdate(LosLoanUpdatePayload p) {
		try {
			if (!identified(p.getMobileAppId(), p.getAppId()))
				return noIdentifier();
			PaydayLoan loan = findLoan(p.getMobileAppId(), p.getAppId());
			if (loan == null)
				return notFound(p.getMobileAppId(), p.getAppId());

			if (p.getOutstandingAmount() != null)
				loan.setOutstandingAmount(p.getOutstandingAmount());
			if (p.getOverduePayment() != null)
				loan.setOverduePayment(p.getOverduePayment());
			if (p.getDaysPastDue() != null)
				loan.setDaysPastDue(p.getDaysPastDue());
			if (p.getLastPaidAmount() != null)
				loan.setLastPaidAmount(p.getLastPaidAmount());
			if (p.getLastTransactionDate() != null)
				loan.setLastTransactionDate(p.getLastTransactionDate());
			if ("Active".equalsIgnoreCase(p.getStatus()))
				loan.setStatus(PdlStatusEnum.Active);
			else if ("Closed".equalsIgnoreCase(p.getStatus()))
				loan.setStatus(PdlStatusEnum.Closed);
			repo.save(loan);

			// Update the matching installment rows (by installmentNo) in place.
			if (p.getSchedule() != null && !p.getSchedule().isEmpty()) {
				List<PdlPaymentSchedule> rows = scheduleRepo.findSchedule(loan.getId());
				Map<Integer, PdlPaymentSchedule> byNo = new HashMap<>();
				for (PdlPaymentSchedule r : rows)
					byNo.put(r.getInstallmentNo(), r);

				for (PaidRow pr : p.getSchedule()) {
					PdlPaymentSchedule row = byNo.get(pr.getInstallmentNo());
					if (row == null)
						continue; // unknown installment — skip
					row.setPrincipalPaid(pr.getPrincipalPaid());
					row.setInterestPaid(pr.getInterestPaid());
					row.setFeePaid(pr.getFeePaid());
					row.setPenaltyPaid(pr.getPenaltyPaid());
					row.setOtherPaid(pr.getOtherPaid());
					row.setTotalPaid(pr.getTotalPaid());
					row.setAmountPaid(pr.getAmountPaid());
					if (pr.getPaidDate() != null)
						row.setPaidDate(pr.getPaidDate());
					if (pr.getTransactionDate() != null)
						row.setTransactionDate(pr.getTransactionDate());
					if (pr.getStatus() != null)
						row.setStatus(pr.getStatus());
					scheduleRepo.save(row);
				}
			}

			notify(loan, "Loan updated", "loan_updated");
			return ok("Loan update processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/**
	 * TEST TOOLING ONLY (2026-08-21 product decision: SBF/LOS does NOT handle
	 * account approval). The real channel is the admin API
	 * ({@code /api/v1/pdl/admin/account-requests/{id}/decision}, LPO role);
	 * this webhook remains for automated tests and delegates to the same logic.
	 */
	@Transactional
	public ResponseEntity<Message<String>> handleAccountDecision(String username, String decision, String reason) {
		PdlAccountRequest req = accountRequestRepo.findByUser_Username(username).orElse(null);
		if (req == null)
			return new ResponseEntity<>(new Message<>("NOT_FOUND", "Account request not found: " + username, null),
					HttpStatus.OK);
		return accountRequestService.decide(req.getId(), "A".equalsIgnoreCase(decision), reason, "TEST-WEBHOOK");
	}

	// --- helpers ---

	private PaydayLoan find(LosNotificationPayload p) {
		return p == null ? null : findLoan(p.getMobileAppId(), p.getAppId());
	}

	/**
	 * The loan a callback names. There is one key, agreed with Sambat on
	 * 2026-11-05: {@code mobileAppId}, our own application id, which we send in
	 * {@code /new-loan-application} and they quote on every callback.
	 *
	 * <p>Until their callbacks switch over, a body without it may still name
	 * the application by their LOS AppId in {@code appId}, the key agreed on
	 * 2026-09-03. That fallback goes once they have switched. Their AppRefId
	 * and the loan ref no are not identifiers and are not read.
	 */
	private PaydayLoan findLoan(Long mobileAppId, Long appId) {
		if (mobileAppId != null)
			return mobileAppId > 0 && mobileAppId <= Integer.MAX_VALUE
					? repo.findById(mobileAppId.intValue()).orElse(null)
					: null;
		return appId == null ? null : repo.findByLosAppId(appId).orElse(null);
	}

	private static boolean identified(LosNotificationPayload p) {
		return p != null && identified(p.getMobileAppId(), p.getAppId());
	}

	private static boolean identified(Long mobileAppId, Long appId) {
		return mobileAppId != null || appId != null;
	}

	private static boolean sent(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * A body that names no application is malformed, not a missing loan: a 400
	 * tells their queue to fix the body rather than go looking for the loan.
	 */
	private ResponseEntity<Message<String>> noIdentifier() {
		return new ResponseEntity<>(new Message<>("BAD_REQUEST",
				"No application identifier in the body. Send mobileAppId: the id we sent you in /new-loan-application.",
				null), HttpStatus.BAD_REQUEST);
	}

	/** Says which identifier we looked for, so a miss can be traced on both sides. */
	private ResponseEntity<Message<String>> notFound(Long mobileAppId, Long appId) {
		String tried = mobileAppId != null ? "mobileAppId " + mobileAppId : "appId " + appId;
		return new ResponseEntity<>(new Message<>("NOT_FOUND", "Application not found: " + tried, null),
				HttpStatus.OK);
	}

	private ResponseEntity<Message<String>> notFound(LosNotificationPayload p) {
		return notFound(p.getMobileAppId(), p.getAppId());
	}

	/** Sambat's payday-loan wording for R-LPO (Mobile App Dictionary, 30 Oct 2026). */
	private static final String INSUFFICIENT_INFO =
			"Apologies. Your application has been rejected due to insufficient information or documents.";

	/** Theirs for R-AO, which is also their rejection with no reason given. */
	private static final String REJECTED = "Apologies. Your loan application has been rejected.";

	/**
	 * The customer-facing reason for a rejection code. Sambat send codes only,
	 * with no text, so their two payday-loan codes get their wording here. Any
	 * other code keeps the text sent with it, which may be none: the app then
	 * shows the generic rejection in the customer's language.
	 */
	private String messageForCode(String code, String fallback) {
		if ("R-LPO".equals(code) || "RW-LPO".equals(code))
			return INSUFFICIENT_INFO;
		if ("R-AO".equals(code) || "RW-AO".equals(code))
			return REJECTED;
		return fallback;
	}

	/** The rejection push: the reason, then the hotline, as Sambat's wording has it. */
	private String rejectionNotice(PaydayLoan loan) {
		String reason = sent(loan.getLosMessage()) ? loan.getLosMessage().strip() : REJECTED;
		// Text sent with an unknown code may lack a full stop.
		String stop = reason.matches("(?s).*[.!?។]") ? " " : ". ";
		return reason + stop + "Please call " + hotline + " for more information.";
	}

	private void notify(PaydayLoan loan, String title, String type) {
		notify(loan, title, loan.getId().toString(), type);
	}

	private void notify(PaydayLoan loan, String title, String body, String type) {
		try {
			User u = loan.getUser();
			if (u != null && u.getFcmToken() != null) {
				// type + refId drive app-side severity mapping and deep links (G19).
				notificationService.postToClient(new NotificationMessage(u.getFcmToken(), title,
						body, loan.getId().toString(), u.getUsername(),
						type, loan.getId().toString()));
			}
		} catch (Exception e) {
			// Notification must never fail the webhook; log and move on.
			e.printStackTrace();
		}
	}

	private ResponseEntity<Message<String>> ok(String m) {
		return new ResponseEntity<>(new Message<>("SUCCESS", m, null), HttpStatus.OK);
	}

	private ResponseEntity<Message<String>> error(Exception e) {
		e.printStackTrace();
		return new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
