package com.ezetik.kjeypapa.pdl.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Loan-product config LOS pushes to the app side (BRS 2.2). Applicable to salary
 * loan products only (PDL / MSIL / PL). Tenor/rate tables TBD per Appendix 1.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LosProductSyncPayload {

	private String productCode; // PDL / MSIL / PL
	private String name;
	private Double minAmount;
	private Double maxAmount;
	// Boxed: LOS is the external source, so a missing field must not break parsing.
	private Boolean active;
}
