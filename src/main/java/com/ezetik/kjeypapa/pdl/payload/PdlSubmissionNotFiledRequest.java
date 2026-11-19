package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An operator's report that Sambat's console does not show a submission we
 * never got an answer for.
 *
 * <p>A named body rather than a map, so Swagger shows the field and not
 * {@code additionalProp1..3}. The whole body may be left out.
 */
@Schema(description = "An operator's report that Sambat's console does not show the application.",
		example = "{\"note\": \"\"}")
public record PdlSubmissionNotFiledRequest(

		@Schema(description = "A note for the server log, beside the operator's name: what was checked in "
				+ "Sambat's console. It is not stored with the loan. Optional.")
		String note) {
}
