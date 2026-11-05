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

	/** The application, by our own id (see {@link LosNotificationPayload#getMobileAppId}). */
	private Long mobileAppId;

	/** Sambat's LOS AppId. Read only until their callbacks send {@code mobileAppId}. */
	private Long appId;

	private String eventId;

	private String disbursementStatus; // DISBURSED / FAILED / PENDING
	private String disbursementTxnId;
	private Double disbursedAmount;
	private Instant disbursedDate;
	private String failureReason;

	private String signature;
}
