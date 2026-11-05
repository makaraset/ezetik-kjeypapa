package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of the V21 Bank-Mobile-App verification / disbursement-consent step
 * (after Accept). PROPOSED shape — the strawman sent to Sambat (Q4). Wiring is
 * gated off by {@code pdl.bank-verification.enabled} until the contract lands.
 *
 * The application is named like on every other callback, by {@code mobileAppId}.
 * {@code verificationDate} timezone/format TBD (Q5.4).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosBankVerificationPayload {

	/** The application, by our own id (see {@link LosNotificationPayload#getMobileAppId}). */
	private Long mobileAppId;

	/** Sambat's LOS AppId. Read only until their callbacks send {@code mobileAppId}. */
	private Long appId;

	private String eventId;

	private String verificationStatus; // SUCCESS / FAILED / PENDING
	private String disbursementTxnId;
	private String failureReason; // read nowhere: Sambat have no failure codes (QB4.3)
	private Instant verificationDate;

	private String signature;
}
