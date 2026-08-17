package com.ezetik.kjeypapa.pdl.model;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A pre-login PDL account request (G7 — V8 screens 4-10). The User is created
 * disabled-until-OTP; login stays blocked until the LPO decision arrives via
 * {@code POST /pdl/los/account-decision} flips this to APPROVED (QC2.1).
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pdl_account_request")
public class PdlAccountRequest extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@OneToOne(fetch = FetchType.EAGER)
	@JsonIdentityReference(alwaysAsId = false)
	private User user;

	private String userType; // EMPLOYEE (V26's "Select User Type 'Employee'")

	@Enumerated(EnumType.STRING)
	private PdlAccountStatusEnum status;

	private String decisionReason;
}
