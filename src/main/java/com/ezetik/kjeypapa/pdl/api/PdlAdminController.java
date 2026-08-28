package com.ezetik.kjeypapa.pdl.api;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountReviewResponse;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * LPO / admin console API for PDL account requests: list → review → decide.
 * OUR OWN approval process (2026-08-21 product decision — SBF/LOS does not
 * handle account approval). Consumed by the in-app admin section today and by
 * any future web console (same endpoints).
 */
@RestController
@RequestMapping("/api/v1/pdl/admin/account-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "11- PDL Admin API", description = "LPO account-request review + approval")
public class PdlAdminController {


	@Autowired
	private PdlAccountRequestService service;

	/** List account requests — PENDING (default), any status, or ALL. */
	@GetMapping
	public ResponseEntity<Message<List<PdlAccountRequest>>> list(
			@RequestParam(name = "status", required = false) String status) {
		return service.list(status);
	}

	/** Full review bundle: the request + captured profile sections (doc refs). */
	@GetMapping("/{id}")
	public ResponseEntity<Message<PdlAccountReviewResponse>> review(@PathVariable("id") int id) {
		return service.review(id);
	}

	/** LPO decision. Body: {"approve": true|false, "reason": "..."} */
	@PostMapping("/{id}/decision")
	public ResponseEntity<Message<String>> decide(@PathVariable("id") int id,
			@RequestBody Map<String, Object> body) {
		boolean approve = Boolean.TRUE.equals(body.get("approve"))
				|| "true".equalsIgnoreCase(String.valueOf(body.get("approve")));
		String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
		String decidedBy = SecurityContextHolder.getContext().getAuthentication() != null
				? SecurityContextHolder.getContext().getAuthentication().getName()
				: "ADMIN";
		return service.decide(id, approve, reason, decidedBy);
	}
}
