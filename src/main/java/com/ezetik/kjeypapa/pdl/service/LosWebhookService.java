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
 * NOTE: signature verification is TBD (BRS Appendix); until then these run on a
 * security whitelist. Add the real verification once the LOS contract lands.
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

	/** BRS 2.4 — reject. */
	@Transactional
	public ResponseEntity<Message<String>> handleReject(LosNotificationPayload p) {
		try {
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

			notify(loan, "Loan application rejected", "loan_rejected");
			return ok("Reject processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** BRS 2.5 — rework. PDL has no true rework: rework = reject (R-LPO / R-AO). */
	@Transactional
	public ResponseEntity<Message<String>> handleRework(LosNotificationPayload p) {
		try {
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

			notify(loan, "Loan application needs attention", "loan_rejected");
			return ok("Rework processed");
		} catch (Exception e) {
			return error(e);
		}
	}

	/** BRS 2.6 — approved; LOS pushes the generated docs + repayment schedule. */
	@Transactional
	public ResponseEntity<Message<String>> handleApproved(LosNotificationPayload p) {
		try {
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
			loan.setLoanFormRef(p.getLoanFormRef());
			loan.setLoanContractFileRef(p.getLoanContractFileRef());
			loan.setRepaymentScheduleRef(p.getRepaymentScheduleRef());
			// Active-loan detail (My Loan card).
			loan.setLoanRefNo(p.getLoanRefNo());
			loan.setSettlementAccountNo(p.getSettlementAccountNo());
			loan.setCurrency(p.getCurrency());
			loan.setTenor(p.getTenor());
			loan.setOutstandingAmount(p.getOutstandingAmount());
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
			PaydayLoan loan = findLoan(reference(p), p.getLoanRefNo());
			if (loan == null)
				return notFoundRef(reference(p), p.getLoanRefNo());

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

			PaydayLoan loan = findLoan(reference(p), p.getLoanRefNo());
			if (loan == null)
				return notFoundRef(reference(p), p.getLoanRefNo());

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
			PaydayLoan loan = findLoan(reference(p), p.getLoanRefNo());
			if (loan == null)
				return notFoundRef(reference(p), p.getLoanRefNo());

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
		return p == null ? null : findLoan(reference(p), p.getLoanRefNo());
	}

	/**
	 * Their callbacks carry the AppId in a field named {@code appId}
	 * (confirmed 2026-09-03); {@code losApplicationNo} stays supported for the
	 * sample bank app and anything already sending an AppRefId.
	 */
	private static String reference(LosNotificationPayload p) {
		return ref(p.getAppId(), p.getLosApplicationNo());
	}

	private static String reference(LosDisbursementPayload p) {
		return ref(p.getAppId(), p.getLosApplicationNo());
	}

	private static String reference(LosBankVerificationPayload p) {
		return ref(p.getAppId(), p.getLosApplicationNo());
	}

	private static String reference(LosLoanUpdatePayload p) {
		return ref(p.getAppId(), p.getLosApplicationNo());
	}

	private static String ref(Long appId, String losApplicationNo) {
		return appId != null ? String.valueOf(appId) : losApplicationNo;
	}

	/**
	 * Resolve a loan from whichever identifier Sambat sends.
	 *
	 * <p>Their submit response returns BOTH an {@code AppId} (4-digit, e.g.
	 * 8281) and an {@code AppRefId} (6-digit, e.g. 257861), and Sambat
	 * confirmed (2026-09-03) that <b>AppId is the id they identify an
	 * application by</b> — AppRefId is not generally used. The inbound payload
	 * carries one generic reference field, so a webhook keyed on AppId would
	 * never have matched the AppRefId we store as losApplicationNo, and every
	 * status update would have 404'd silently. Try both columns, then the loan
	 * ref no.
	 */
	private PaydayLoan findLoan(String reference, String loanRefNo) {
		if (reference != null && !reference.isBlank()) {
			PaydayLoan l = repo.findByLosApplicationNo(reference).orElse(null);
			if (l != null)
				return l;
			try {
				l = repo.findByLosAppId(Long.valueOf(reference.trim())).orElse(null);
				if (l != null)
					return l;
			} catch (NumberFormatException ignored) {
				// Not an AppId-shaped value; fall through to the loan ref no.
			}
		}
		if (loanRefNo != null)
			return repo.findByLoanRefNo(loanRefNo).orElse(null);
		return null;
	}

	private ResponseEntity<Message<String>> notFoundRef(String losApplicationNo, String loanRefNo) {
		return new ResponseEntity<>(new Message<>("NOT_FOUND",
				"Loan not found: " + (losApplicationNo != null ? losApplicationNo : loanRefNo), null),
				HttpStatus.OK);
	}

	/** Map the LOS status code to the user-facing message (BRS 2.4/2.5). */
	private String messageForCode(String code, String fallback) {
		if ("R-LPO".equals(code) || "RW-LPO".equals(code))
			return "Insufficient Information or Documents";
		if ("R-AO".equals(code) || "RW-AO".equals(code))
			return "Not eligible for the loan";
		// V21: bank payroll-account verification failure (dormant/closed/not found).
		// Exact code TBD — Sambat to confirm (Q5).
		if ("R-BANK".equals(code) || "INVALID_ACCOUNT".equals(code) || "BANK_VERIFY_FAILED".equals(code))
			return "Bank account could not be verified";
		return fallback;
	}

	private void notify(PaydayLoan loan, String title, String type) {
		try {
			User u = loan.getUser();
			if (u != null && u.getFcmToken() != null) {
				// type + refId drive app-side severity mapping and deep links (G19).
				notificationService.postToClient(new NotificationMessage(u.getFcmToken(), title,
						loan.getId().toString(), loan.getId().toString(), u.getUsername(),
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

	private ResponseEntity<Message<String>> notFound(LosNotificationPayload p) {
		return new ResponseEntity<>(
				new Message<>("NOT_FOUND", "Application not found: " + (p != null ? p.getLosApplicationNo() : null),
						null),
				HttpStatus.OK);
	}
}
