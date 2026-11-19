package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.payload.NidOcrRequest;
import com.ezetik.kjeypapa.pdl.payload.NidOcrResponse;
import com.ezetik.kjeypapa.pdl.service.NidOcrService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Signup screen-5 NID extraction (unauthenticated: the pre-login signup flow,
 * same whitelist family as /pdl/account-request). Body: {"imageBase64": "..."}.
 */
@RestController
@RequestMapping("/api/v1/pdl/ocr-nid")
@Tag(name = "11- PDL NID OCR API", description = "Sign-up NID extraction via SBF/CamDigi + live CIF lookup")
public class PdlOcrController {

	@Autowired
	private NidOcrService service;

	@Operation(summary = "Read an ID card photo", description = """
			For the app's sign-up, before the customer has an account. Sambat's OCR reads the card, and the
			answer fills the next screen. It needs no sign-in.

			The outcome is in the answer's `type`:

			- `SUCCESS`: `data` holds what was read from the card.
			- `INVALID`: `imageBase64` is missing or blank. Sambat were not called.
			- `OCR_FAILED`: the card could not be read, or Sambat could not be reached.
			- `RATE_LIMITED` (HTTP 429): too many calls from one address within a minute.
			""")
	@PostMapping
	public ResponseEntity<Message<NidOcrResponse>> extract(@RequestBody NidOcrRequest body) {
		return service.extract(body.imageBase64());
	}
}
