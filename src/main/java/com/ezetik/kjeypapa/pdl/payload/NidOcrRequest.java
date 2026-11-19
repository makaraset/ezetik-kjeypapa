package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The ID card photo the sign-up flow wants read.
 *
 * <p>A named body rather than a map, so Swagger shows the field and not
 * {@code additionalProp1..3}. It reads exactly what the app sends:
 * {@code {"imageBase64": "..."}}. The example is blank on purpose: sent as it
 * stands it is refused here and never reaches Sambat's OCR.
 */
@Schema(description = "The ID card photo to read.", example = "{\"imageBase64\": \"\"}")
public record NidOcrRequest(

		@Schema(description = "The photo of the front of the ID card: the image file's bytes in base64, "
				+ "with no data: prefix. Required.", requiredMode = Schema.RequiredMode.REQUIRED)
		String imageBase64) {
}
