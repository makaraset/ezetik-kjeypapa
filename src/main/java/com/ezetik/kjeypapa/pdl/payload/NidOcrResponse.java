package com.ezetik.kjeypapa.pdl.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Extraction result for the signup screen-5 NID photo (SBF /ocr-id-card via
 * CamDigi), plus the SBF CIF resolved live via /customer-information/by-idno
 * (null = new-to-SBF customer; loan submits then carry custId 0).
 * Dates normalised to the app's dd/MM/yyyy form format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NidOcrResponse {
	private String khmerFamilyName;
	private String khmerFirstName;
	private String latinFamilyName;
	private String latinFirstName;
	private String gender; // M / F
	private String dateOfBirth; // dd/MM/yyyy
	private String idNumber;
	private String idIssuedDate; // dd/MM/yyyy
	private String idExpiryDate; // dd/MM/yyyy
	private String address; // raw address line from the card
	/**
	 * The card's address line resolved against Sambat's gazetteer. Codes are
	 * what the loan application and our own records need; the names are the
	 * matched labels, for display and for the audit trail.
	 */
	private String provinceCode;
	private String provinceName;
	private String districtCode;
	private String districtName;
	private String communeCode;
	private String communeName;
	private String villageCode;
	private String villageName;
	private String houseStreetNo;

	private Integer cif; // SBF custKeyNum, null when new
	private boolean mock; // true while ocr.mock.enabled serves sample data
}
