package com.ezetik.kjeypapa.pdl.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.payload.NidOcrResponse;
import com.ezetik.kjeypapa.pdl.service.NidOcrService;
import com.ezetik.kjeypapa.security.util.Message;

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

	@PostMapping
	public ResponseEntity<Message<NidOcrResponse>> extract(@RequestBody Map<String, String> body) {
		return service.extract(body.get("imageBase64"));
	}
}
