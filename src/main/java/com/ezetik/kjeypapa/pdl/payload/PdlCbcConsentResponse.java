package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The CBC-consent record generated at submit (Sambat 2026-08-13 QC4.2) —
 * backs the app's "CBC Consent [View]" on loan cards. The legal text is the
 * interim V8 English copy until Sambat delivers the final EN+KM (QC4.3);
 * the app renders the Khmer translation from its own i18n by text version.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlCbcConsentResponse {

	private Integer loanId;
	private String loanRefNo;
	private String losApplicationNo;
	private String customerName;
	private String consentRef;
	private Instant consentDate;
	private String textVersion;
	private String text;
}
