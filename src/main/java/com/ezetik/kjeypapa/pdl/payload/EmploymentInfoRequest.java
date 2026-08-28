package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App → backend body for capturing the applicant's employment profile
 * (signup mockup 12.1.3 / My Profile edit). Request DTO ONLY — it deliberately
 * omits {@code id}, {@code user}, and the server-set {@code verified*} fields so
 * the client cannot self-verify or hijack another user's row.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmploymentInfoRequest {

	private String employmentType;
	private String employerName;
	private String businessActivities;
	private String occupation;
	private Instant employmentStartDate;
	private String employmentStatus;
	private Double monthlyIncome;
	private String currency;

	private String workCountry;
	private String workProvince;
	private String workDistrict;
	private String workCommune;
	private String workVillage;

	/**
	 * Sambat's own geo codes (NCDD: 2/4/6/8 digits), confirmed by them
	 * 2026-08-28. These are what the loan application sends and what any
	 * matching uses; the name columns beside them are kept as the label
	 * captured at the time, for display and for the audit trail — Sambat's
	 * master list changes, and a credit file should still read correctly
	 * years later. Blank on rows captured before codes existed.
	 */
	private String workProvinceCode;
	private String workDistrictCode;
	private String workCommuneCode;
	private String workVillageCode;


	private String employmentCardFileRef;
}
