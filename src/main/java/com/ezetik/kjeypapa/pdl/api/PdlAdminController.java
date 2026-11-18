package com.ezetik.kjeypapa.pdl.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import com.ezetik.kjeypapa.pdl.payload.PdlAccountDecisionRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountReviewResponse;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.Operation;
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

	/** LPO decision. Body: {"approve": true|false, "reason": "...", "employerCode": "..."} */
	@Operation(summary = "Approve or reject an account request", description = """
			Decides a PENDING request, once.

			- **Approve** lets the customer sign in, marks their details as verified and sends the approval SMS.
			- **Reject** keeps the account disabled and sends the rejection SMS.

			The outcome is in the answer's `type`:

			- `SUCCESS`: decided. `data` is `APPROVED` or `REJECTED`.
			- `NOT_FOUND`: no request has this id.
			- `INVALID`: the request was already decided (`data` is its status), or `approve` is missing.
			- `IDENTITY_NOT_VERIFIED`: approval is refused until the customer passes identity verification.
			- `BAD_REQUEST`: the body could not be read. The message names the field.
			""")
	@PostMapping("/{id}/decision")
	public ResponseEntity<Message<String>> decide(@PathVariable("id") int id,
			@RequestBody PdlAccountDecisionRequest body) {
		// Never defaulted. A body without "approve" used to count as a
		// rejection: it disabled the customer and sent them the SMS.
		if (body.approve() == null)
			return new ResponseEntity<>(new Message<>("INVALID",
					"'approve' is required: true to approve the request, false to reject it.", null),
					HttpStatus.BAD_REQUEST);
		String decidedBy = SecurityContextHolder.getContext().getAuthentication() != null
				? SecurityContextHolder.getContext().getAuthentication().getName()
				: "ADMIN";
		return service.decide(id, body.approve(), body.reason(), decidedBy, body.employerCode());
	}
}
