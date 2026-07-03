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
 * Correlation keys mirror the other LOS callbacks (loan ref no, falling back to
 * the LOS application no). {@code verificationDate} timezone/format TBD (Q5.4).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosBankVerificationPayload {

	private String loanRefNo;
	private String losApplicationNo;
	private String eventId;

	private String verificationStatus; // SUCCESS / FAILED / PENDING
	private String disbursementTxnId;
	private String failureReason; // machine code on FAILED (maps via messageForCode)
	private Instant verificationDate;

	private String signature;
}
