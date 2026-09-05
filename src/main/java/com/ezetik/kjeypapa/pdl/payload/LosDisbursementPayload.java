package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound disbursement notification from Sambat LOS (after the customer accepts,
 * LOS orchestrates the Campu Bank direct-debit). Proposed shape — to be aligned
 * with the LOS disbursement appendix when delivered.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosDisbursementPayload {

	/**
	 * Sambat's LOS AppId — the id they identify an application by, and the
	 * field name their callbacks use (confirmed 2026-09-03). Preferred over
	 * {@code losApplicationNo} (their AppRefId) when both are present.
	 */
	private Long appId;

	private String losApplicationNo;
	private String loanRefNo;
	private String eventId;

	private String disbursementStatus; // DISBURSED / FAILED / PENDING
	private String disbursementTxnId;
	private Double disbursedAmount;
	private Instant disbursedDate;
	private String failureReason;

	private String signature;
}
