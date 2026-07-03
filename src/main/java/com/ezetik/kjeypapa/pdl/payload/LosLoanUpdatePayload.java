package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound loan / repayment status update from Sambat LOS — keeps the customer's
 * "My Loan" + "Payment Record" views current (outstanding, days past due, and
 * per-installment paid amounts). Proposed shape — to be aligned with the LOS
 * repayment appendix when delivered.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosLoanUpdatePayload {

	private String losApplicationNo;
	private String loanRefNo;
	private String eventId;

	private String status; // Active / Closed / Overdue
	private Double outstandingAmount;
	private Double overduePayment;
	private Integer daysPastDue;
	private Double lastPaidAmount;
	private Instant lastTransactionDate;

	private List<PaidRow> schedule;

	private String signature;

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PaidRow {
		private Integer installmentNo;
		private Double principalPaid;
		private Double interestPaid;
		private Double feePaid;
		private Double penaltyPaid;
		private Double totalPaid;
		private Double amountPaid;
		private Instant paidDate;
		private Instant transactionDate;
		private String status; // PAID / OVERDUE / PENDING
	}
}
