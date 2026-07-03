package com.ezetik.kjeypapa.pdl.payload;

import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate of the customer's PDL profile for the "My Profile" screen.
 *
 * NOTE: the full personal/KYC block (Khmer name, ID type/dates, marital status,
 * nationality, correspondence/permanent addresses) is NOT yet modelled — see
 * GAP-2 in plans/PDL_PHASE3_BUILD_SPEC.md. For now this carries the basic user
 * identity plus the employment + bank sub-profiles (which exist today).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlProfileResponse {

	private Integer userId;
	private String username;
	private String cif; // registeredId

	// Personal basics from the shared User (account-level identity).
	private String firstname;
	private String lastname;
	private String gender;
	private String dateOfBirth;
	private String phoneNumber;
	private String email;

	private PdlPersonalInfo personalInfo; // extended KYC (null until captured)
	private PdlEmploymentInfo employmentInfo; // null when not captured yet
	private PdlBankInfo bankInfo; // null when not captured yet
}
