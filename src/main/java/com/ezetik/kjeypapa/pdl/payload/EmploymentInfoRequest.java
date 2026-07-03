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

	private String employmentCardFileRef;
}
