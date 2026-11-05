package com.ezetik.kjeypapa.pdl.payload;

import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.service.SbfCifResolver;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything the LPO needs to review one account request: the request row
 * plus the captured profile sections (whose file refs feed the doc viewers).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlAccountReviewResponse {

	private PdlAccountRequest request;
	private PdlPersonalInfo personalInfo;
	private PdlEmploymentInfo employmentInfo;
	private PdlBankInfo bankInfo;

	/** CamDigi identity verification (2026-10-27): state, attempts, SBF's message. */
	private IdentityVerificationSummary identityVerification;

	/**
	 * The customer at Sambat: their stored CIF, or what Sambat's lookup finds
	 * now (new customer, a mismatch and on which fields, or unreachable).
	 */
	private SbfCifResolver.CifCheck sbfCustomer;
}
