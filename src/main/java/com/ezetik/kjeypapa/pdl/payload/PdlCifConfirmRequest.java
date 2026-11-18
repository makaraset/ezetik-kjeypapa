package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An LPO's confirmation of the CIF of a customer who is already approved.
 *
 * <p>The number is a claim to be checked, not a value to be stored: the server
 * takes it only if it is the CIF Sambat hold, now, for the customer's ID
 * number. No {@code confirmedBy}: it comes from the security context.
 */
@Schema(description = "An LPO's confirmation of the CIF of a customer who is already approved.")
public record PdlCifConfirmRequest(

		@Schema(description = "The CIF being confirmed as this customer's: sbfCustomer.cif from the review, when "
				+ "sbfCustomer.outcome is MISMATCH. Required, and greater than 0.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		Long cif) {
}
