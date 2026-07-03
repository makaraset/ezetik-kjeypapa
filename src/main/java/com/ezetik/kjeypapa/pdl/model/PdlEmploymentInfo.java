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

	private String employmentCardFileRef;

	@Column(columnDefinition = "bool default false")
	private boolean verified;
	private String verifiedBy;
	private Instant verifiedDate;

}
