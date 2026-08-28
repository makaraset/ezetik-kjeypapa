package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Applicant employment details captured during PDL signup (mockup 5.3 / 11).
 */
@Data
@Entity
@Table(name = "pdl_employment_info")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PdlEmploymentInfo extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	private User user;

	private String employmentType; // Employee, Self-employed, ...
	private String employerName;
	private String businessActivities;
	private String occupation;
	private Instant employmentStartDate;
	private String employmentStatus; // Confirmed, Probation, ...
	private Double monthlyIncome;
	private String currency;

	// Work address
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

	@Column(columnDefinition = "bool default false")
	private boolean verified;
	private String verifiedBy;
	private Instant verifiedDate;

}
