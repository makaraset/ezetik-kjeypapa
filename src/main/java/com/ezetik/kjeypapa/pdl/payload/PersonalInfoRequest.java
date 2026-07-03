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

	private String nidFrontFileRef;
	private String nidBackFileRef;
	private String profilePhotoFileRef;
}
