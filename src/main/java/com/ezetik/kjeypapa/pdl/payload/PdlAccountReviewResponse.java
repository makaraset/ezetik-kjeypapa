package com.ezetik.kjeypapa.pdl.payload;

import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

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
}
