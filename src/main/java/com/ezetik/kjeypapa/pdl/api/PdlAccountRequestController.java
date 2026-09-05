package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestPayload;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Pre-login PDL account requests (G7). UNAUTHENTICATED by design (whitelisted):
 * the caller has no account yet. The OTP + LPO decision gate everything that
 * follows; abuse surface is bounded to request creation + a status probe.
 */
@RestController
@RequestMapping("/api/v1/pdl/account-request")
@Tag(name = "10- PDL Account Request API", description = "Pre-login PDL sign-up (V8 screens 4-10)")
public class PdlAccountRequestController {

	@Autowired
	private PdlAccountRequestService service;

	@PostMapping
	public ResponseEntity<Message<PdlAccountRequest>> create(@RequestBody PdlAccountRequestPayload payload) {
		return service.create(payload);
	}

	/** Status probe for the pending screen (PENDING/APPROVED/REJECTED only). */
	/** POST so the phone number never appears in a URL, proxy log or query string. */
	@PostMapping("/status")
	public ResponseEntity<Message<String>> status(@RequestBody java.util.Map<String, String> body) {
		return service.status(body == null ? null : body.get("username"));
	}
}
