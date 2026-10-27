package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.payload.LivenessChallengeRequest;
import com.ezetik.kjeypapa.pdl.payload.LivenessChallengeResponse;
import com.ezetik.kjeypapa.pdl.payload.LivenessResultResponse;
import com.ezetik.kjeypapa.pdl.payload.LivenessSubmitRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestCreated;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestPayload;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.pdl.service.PdlIdentityVerificationService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

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

	@Autowired
	private PdlIdentityVerificationService verification;

	/** The same trusted-proxy list the rate limiter and the consent records use. */
	@Value("${pdl.ocr.rate-limit.trusted-proxies:}")
	private String trustedProxies;

	@PostMapping
	public ResponseEntity<Message<PdlAccountRequestCreated>> create(@RequestBody PdlAccountRequestPayload payload) {
		return service.create(payload);
	}

	/** Status probe for the pending screen (PENDING/APPROVED/REJECTED only). */
	/** POST so the phone number never appears in a URL, proxy log or query string. */
	@PostMapping("/status")
	public ResponseEntity<Message<String>> status(@RequestBody java.util.Map<String, String> body) {
		return service.status(body == null ? null : body.get("username"));
	}

	/**
	 * CamDigi step 1: a single-use challenge for the caller's own PENDING
	 * request, after OTP. Checks the request's password itself, since a
	 * pending customer cannot log in. Always HTTP 200; the outcome is in type.
	 */
	@PostMapping("/liveness/challenge")
	public ResponseEntity<Message<LivenessChallengeResponse>> livenessChallenge(
			@RequestBody(required = false) LivenessChallengeRequest body, HttpServletRequest http) {
		return verification.challenge(body == null ? null : body.username(), body == null ? null : body.password(),
				ClientAddress.of(http, trustedProxies));
	}

	/** CamDigi step 2: the capture answering a challenge. Always HTTP 200; the outcome is in type. */
	@PostMapping("/liveness")
	public ResponseEntity<Message<LivenessResultResponse>> livenessSubmit(
			@RequestBody(required = false) LivenessSubmitRequest body, HttpServletRequest http) {
		return verification.submit(body == null ? null : body.challengeId(), body == null ? null : body.liveness(),
				body == null ? null : body.mimeType(), ClientAddress.of(http, trustedProxies));
	}
}
