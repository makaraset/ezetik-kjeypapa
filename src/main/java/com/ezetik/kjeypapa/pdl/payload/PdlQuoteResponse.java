package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A loan quote for the V8 request wizard (screen 15): the customer picks a
 * repayment-amount tier and the server derives everything else. Formula per
 * Sambat's 2026-08-13 answers (QC1.3):
 *
 * <pre>
 *   interest    = principal × monthlyRate × (periodDays / 30)   // pro-rated
 *   principal   = repayment / (1 + monthlyRate × periodDays/30) // = disbursed
 *   net         = principal − processingFee − cbcEnquiryFee     // credited
 * </pre>
 *
 * Worked check (confirmed figures): repayment $50, 15 days →
 * principal $49.63, interest $0.37, net $45.63.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlQuoteResponse {

	private String loanType;
	private String currency;

	private Double repaymentAmount; // the selected tier (total repaid)
	private Double loanAmount; // principal = disbursed amount
	private Double interestAmount;
	private Double interestRatePercent; // monthly, e.g. 1.5
	private Double processingFee;
	private Double cbcEnquiryFee;
	private Double netDisbursedAmount; // credited to the payroll account

	private Integer loanPeriodDays;
	private Instant disbursementDate; // fixed offset from application date (QC1.4)
	private Instant repaymentDate;

	/** The selectable repayment-amount tiers for this loanType+currency. */
	private List<Double> tiers;
}
