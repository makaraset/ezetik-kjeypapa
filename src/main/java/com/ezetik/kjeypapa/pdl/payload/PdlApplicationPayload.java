package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App → backend request to create a PDL application. Per the V21 customer
 * journey the request captures only currency + amount + CBC consent (info is
 * confirmed, not re-entered); the mandatory documents are captured once at
 * signup as profile file-refs, not re-uploaded here.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlApplicationPayload {

	private Integer id;

	private Double requestAmount;
	private String currency; // USD / KHR — KHR stays (Sambat 2026-08-13, QC1.5)
	private String loanType; // PAYDAY (default) / MICRO / PERSONAL — V8 screen 11
	// V8 wizard: the customer selects a repayment-amount TIER; the server derives
	// principal/interest/fees/net/dates via PdlPricingService (QC1.3/QC1.6).
	private Double repaymentAmount;
	private Double interestAmount;
	private Double processingFee;
	private Integer loanPeriodDays; // deprecated at request time (term comes from LOS product)
	private Instant disbursementDate;
	private Instant repaymentDate;

	private String cbcConsentRef;
	// Boxed (nullable): V21 removed bank consent from the request (it moves to the
	// bank-app step), so it may be absent — must not fail Jackson-3 primitive parsing.
	private Boolean bankConsent;

	// Optional links to the applicant's profile (captured at signup).
	private Integer employmentInfoId;
	private Integer bankInfoId;

	// PDL is always self-service: the application is created for the authenticated
	// caller. No "create on behalf of" flag (unlike NotePayload.isMe), which also
	// avoids the Jackson-3 isXxx boolean property-naming ambiguity.
}
