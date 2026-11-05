package com.ezetik.kjeypapa.pdl.payload;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pre-login account-request bundle (G7 — V8 screens 4-10): credentials +
 * profile sections + the V8 document set, submitted in ONE unauthenticated
 * call; OTP fires after (screen 9). Documents ride inline as base64 (mirrors
 * the Appendix-2 convention; QB2.4 limits: 20MB/doc, 50MB/request).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlAccountRequestPayload {

	private String userType; // EMPLOYEE
	private String username; // User ID = phone number (V8 screen 6)
	private String password;
	private String email; // optional (QC2.3)

	private PersonalInfoRequest personal;
	private EmploymentInfoRequest employment;
	private BankInfoRequest bank;

	/** Bank account-information disclosure consent (V8 screen 8, QB2.3). */
	private Boolean disclosureConsent;

	private List<DocFile> docs;

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class DocFile {
		/** NID_FRONT / NID_BACK / SELFIE / EMPLOYMENT_CARD / BANK_STATEMENT */
		private String docType;
		private String fileName;
		private String contentType;
		private String base64;
	}
}
