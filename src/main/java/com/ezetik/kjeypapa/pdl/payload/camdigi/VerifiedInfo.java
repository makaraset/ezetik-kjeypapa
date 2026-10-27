package com.ezetik.kjeypapa.pdl.payload.camdigi;

/**
 * SBF's {@code VerifiedInfo}: the identity CamDigi checks against the national
 * registry. Its fields are exactly their OCR response's, minus the MRZ lines
 * and the address — it is meant to carry what their OCR read off the card.
 */
public record VerifiedInfo(String dob, String expiredDate, String firstNameEn, String firstNameKh, String gender,
		String idNumber, String issuedDate, String lastNameEn, String lastNameKh) {
}
