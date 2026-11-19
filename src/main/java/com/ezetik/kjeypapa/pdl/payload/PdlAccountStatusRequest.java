package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whose account request the pending screen is asking about.
 *
 * <p>A named body rather than a map, so Swagger shows the field and not
 * {@code additionalProp1..3}. It reads exactly what the app sends:
 * {@code {"username": "..."}}.
 */
@Schema(description = "Whose account request to look up.", example = "{\"username\": \"\"}")
public record PdlAccountStatusRequest(

		@Schema(description = "The login id the account was requested with: the customer's phone number.")
		String username) {
}
