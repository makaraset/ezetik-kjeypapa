package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A PDL application as listed in the app's "My Application" tab.
 * Built via the JPQL constructor expression (mirror {@code NoteTransaction}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlTransaction {

	private Integer id;
	private String losApplicationNo;

	/**
	 * Sambat's LOS App ID — the number they identify an application by
	 * (2026-09-04), and so the one the application cards show a customer.
	 */
	private Long losAppId;
	private Double requestAmount;
	private Instant applicationDate;
	private PdlStatusEnum status;
	private String losStatusCode; // R-LPO / R-AO / ...
	private String message; // user-facing message from LOS
	private Instant createdAt;

	// V8 enriched card fields (screens 29/30/34 — G16).
	private String loanType;
	private String loanRefNo;
	private String currency;
	private Instant disbursementDate;
	private Instant repaymentDate;
	private Integer loanPeriodDays;
	private Double repaymentAmount;
	private Double interestAmount;
	private Double netDisbursedAmount;

	public PdlTransaction(PaydayLoan p) {
		this.id = p.getId();
		this.losApplicationNo = p.getLosApplicationNo();
		this.losAppId = p.getLosAppId();
		this.requestAmount = p.getRequestAmount();
		this.applicationDate = p.getApplicationDate();
		this.status = p.getStatus();
		this.losStatusCode = p.getLosStatusCode();
		this.message = p.getLosMessage();
		this.createdAt = p.getCreatedAt();
		this.loanType = p.getLoanType() != null ? p.getLoanType().name() : null;
		this.loanRefNo = p.getLoanRefNo();
		this.currency = p.getCurrency();
		this.disbursementDate = p.getDisbursementDate();
		this.repaymentDate = p.getRepaymentDate();
		this.loanPeriodDays = p.getLoanPeriodDays();
		this.repaymentAmount = p.getRepaymentAmount();
		this.interestAmount = p.getInterestAmount();
		this.netDisbursedAmount = p.getNetDisbursedAmount();
	}
}
