package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An operator's report that Sambat's console shows a submission we never got
 * an answer for.
 *
 * <p>A named body rather than a map, so Swagger shows the fields and not
 * {@code additionalProp1..3}. The example is blank on purpose: sent as it
 * stands it is refused, because 0 is not an application id.
 */
@Schema(description = "An operator's report that Sambat's console shows the application.",
		example = "{\"losAppId\": 0, \"losApplicationNo\": \"\", \"note\": \"\"}")
public record PdlSubmissionFiledRequest(

		@Schema(description = "Sambat's LOS AppId for the application, read from their console. "
				+ "Required, and greater than 0.", requiredMode = Schema.RequiredMode.REQUIRED)
		Long losAppId,

		@Schema(description = "Sambat's application reference (AppRefId), if their console shows one. "
				+ "Stored when given. Optional.")
		String losApplicationNo,

		@Schema(description = "A note for the server log, beside the operator's name: what was seen in "
				+ "Sambat's console. It is not stored with the loan. Optional.")
		String note) {
}
