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
 * Applicant personal / KYC details captured during PDL signup (mockup 12.1.1 +
 * 12.1.2). PDL-specific home for the extended KYC the shared {@code User} does
 * not hold (Khmer name, ID type/dates, marital status, nationality, place of
 * birth, correspondence + permanent addresses) — resolves GAP-2.
 */
@Data
@Entity
@Table(name = "pdl_personal_info")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PdlPersonalInfo extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	private User user;

	// Names
	private String khmerFamilyName;
	private String khmerFirstName;
	private String latinFamilyName;
	private String latinFirstName;

	private String gender; // M / F
	private String dateOfBirth; // dd/MM/yyyy

	// Identity document
	private String idType; // National ID Card, Passport, ...
	private String idNo;
	private String idIssuedDate; // dd/MM/yyyy
	private String idExpiryDate; // dd/MM/yyyy

	// Place of birth
	private String birthCountry;
	private String birthProvince;
	private String birthDistrict;

	private String nationality;
	private String maritalStatus;
	private String mobilePhone;

	// Correspondence address
	private String corrCountry;
	private String corrProvince;
	private String corrDistrict;
	private String corrCommune;
	private String corrVillage;
	private String corrHouseStreetNo;

	// Permanent address
	private String permCountry;
	private String permProvince;
	private String permDistrict;
	private String permCommune;
	private String permVillage;
	private String permHouseStreetNo;

	// KYC document refs (stored via image/ or file/upload)
	private String nidFrontFileRef;
	private String nidBackFileRef;
	private String profilePhotoFileRef;

	@Column(columnDefinition = "bool default false")
	private boolean verified;
	private String verifiedBy;
	private Instant verifiedDate;

}
