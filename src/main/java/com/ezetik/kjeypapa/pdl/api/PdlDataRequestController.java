package com.ezetik.kjeypapa.pdl.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.model.PdlDataRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlDataRequestPayload;
import com.ezetik.kjeypapa.pdl.service.PdlDataRequestService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The customer's side of the CBC consent: see what we hold, correct it, ask for
 * deletion, withdraw consent.
 *
 * <p>The consent text grants these rights, citing the NBC Prakas on Credit
 * Reporting. Without somewhere to exercise them the grant is decorative, which
 * is a worse position than not having claimed it.
 */
@RestController
@RequestMapping("/api/v1/pdl")
@Tag(name = "14- PDL Data Rights", description = "Customer access, correction, deletion and consent withdrawal")
public class PdlDataRequestController {

	@Autowired
	private PdlDataRequestService service;

	/** Raise a request. Type, free-text detail, and optionally the loan it concerns. */
	@PostMapping("/data-request")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlDataRequest>> create(@RequestBody PdlDataRequestPayload payload) {
		return service.create(payload);
	}

	/** The caller's own requests and where each has got to. */
	@GetMapping("/data-requests")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<PdlDataRequest>>> mine() {
		return service.mine();
	}
}
