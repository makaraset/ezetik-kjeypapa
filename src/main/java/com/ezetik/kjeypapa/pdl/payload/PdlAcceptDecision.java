package com.ezetik.kjeypapa.pdl.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer's decision on an approved PDL offer (BRS 2.7).
 * {@code "Y"} = accept + signed e-contract; {@code "N"} = reject / expiry.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlAcceptDecision {

	private String decision; // "Y" or "N"
	private String signedContractRef; // required when decision = "Y"
}
