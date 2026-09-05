package com.ezetik.kjeypapa.pdl.api;

import java.util.List;

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

import com.ezetik.kjeypapa.pdl.model.PdlDataRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlDataDecisionPayload;
import com.ezetik.kjeypapa.pdl.service.PdlDataRequestService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The operator queue for data-rights requests.
 *
 * <p>Its own base path rather than a route hung off the account-request or
 * dictionary controllers — an endpoint inherited into the wrong base path gets a
 * nonsense URL and a 403 that reads like a permissions bug. That has now cost
 * this project time twice.
 */
@RestController
@RequestMapping("/api/v1/pdl/admin/data-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "14- PDL Data Rights", description = "Operator queue for customer data requests")
public class PdlDataRequestAdminController {

	@Autowired
	private PdlDataRequestService service;

	/** The queue — one status, or ALL. */
	@GetMapping
	public ResponseEntity<Message<List<PdlDataRequest>>> list(
			@RequestParam(name = "status", required = false) String status) {
		return service.list(status);
	}

	/**
	 * Record a decision.
	 *
	 * <p>{@code decidedBy} is taken from the security context here and never
	 * from the body: an actor a client can set is not an audit trail.
	 */
	@PostMapping("/{id}/decision")
	public ResponseEntity<Message<PdlDataRequest>> decide(@PathVariable("id") int id,
			@RequestBody PdlDataDecisionPayload payload) {
		String actor = SecurityContextHolder.getContext().getAuthentication().getName();
		return service.decide(id, payload, actor);
	}
}
