package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound notification from Sambat LOS (BRS 2.4 reject / 2.5 rework / 2.6 approved).
 *
 * Field-level contract is TBD until TurnKey delivers Appendices 3-5; this shape
 * covers the known data. On APPROVED, LOS pushes the generated docs + repayment
 * {@link #schedule}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosNotificationPayload {

	/**
	 * Sambat's LOS AppId — the id they identify an application by, and the
	 * field name they confirmed their callbacks use (2026-09-03). Preferred
	 * over {@code losApplicationNo} (their AppRefId) when both are present.
	 */
	private Long appId;

	private String losApplicationNo;
	private String statusCode; // R-LPO / R-AO / RW-LPO / RW-AO
	private String message; // user-facing reason / comment

	// APPROVED only — refs to LOS-generated documents (stored via image/).
	private String loanFormRef;
	private String loanContractFileRef;
	private String repaymentScheduleRef;
	private List<ScheduleRow> schedule;

	// APPROVED only — active-loan detail for the My Loan card.
	private String loanRefNo;
	private String settlementAccountNo;
	private String currency;
	private Integer tenor;
	private Double outstandingAmount;
	private String loanDocRef;

	// Quote/fee detail (V8 acceptance screen 32; QC1.3). Optional — when absent
	// the values computed at create-time by PdlPricingService stand.
	private Double repaymentAmount;
	private Double interestAmount;
	private Double interestRatePercent;
	private Double processingFee;
	private Double cbcEnquiryFee;
	private Double netDisbursedAmount;
	private Integer loanPeriodDays;

	private String signature; // webhook signature (scheme TBD)

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ScheduleRow {
		private Integer installmentNo;
		private Instant dueDate;
		private Double principalDue;
		private Double interestDue;
		private Double feeDue;
		private Double otherDue;
		private Double totalDue;
	}
}
