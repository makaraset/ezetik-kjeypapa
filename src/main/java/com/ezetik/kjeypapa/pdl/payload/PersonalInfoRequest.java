package com.ezetik.kjeypapa.pdl.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App → backend body for capturing the applicant's personal / KYC profile
 * (signup mockup 12.1.1 + 12.1.2). Request DTO ONLY — omits {@code id},
 * {@code user}, and the server-set {@code verified*} fields.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersonalInfoRequest {

	private String khmerFamilyName;
	private String khmerFirstName;
	private String latinFamilyName;
	private String latinFirstName;

	private String gender;
	private String dateOfBirth;

	private String idType;
	private String idNo;
	private String idIssuedDate;
	private String idExpiryDate;

	private String birthCountry;
	private String birthProvince;
	private String birthDistrict;

	private String nationality;
	private String maritalStatus;
	private String mobilePhone;

	private String corrCountry;
	private String corrProvince;
	private String corrDistrict;
	private String corrCommune;
	private String corrVillage;
	private String corrHouseStreetNo;

	private String permCountry;
	private String permProvince;
	private String permDistrict;
	private String permCommune;
	private String permVillage;
	private String permHouseStreetNo;

	/**
	 * Sambat's own geo codes (NCDD: 2/4/6/8 digits), confirmed by them
	 * 2026-08-28. These are what the loan application sends and what any
	 * matching uses; the name columns beside them are kept as the label
	 * captured at the time, for display and for the audit trail — Sambat's
	 * master list changes, and a credit file should still read correctly
	 * years later. Blank on rows captured before codes existed.
	 */
	private String birthProvinceCode;
	private String birthDistrictCode;
	private String corrProvinceCode;
	private String corrDistrictCode;
	private String corrCommuneCode;
	private String corrVillageCode;
	private String permProvinceCode;
	private String permDistrictCode;
	private String permCommuneCode;
	private String permVillageCode;


	private String nidFrontFileRef;
	private String nidBackFileRef;
	private String profilePhotoFileRef;
}
