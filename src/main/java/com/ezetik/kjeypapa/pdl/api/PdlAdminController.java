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
import com.ezetik.kjeypapa.pdl.payload.PdlCifConfirmRequest;
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

	/** LPO decision. Body: {"approve": true|false, "reason": "...", "employerCode": "...", "confirmCif": n} */
	@Operation(summary = "Approve or reject an account request", description = """
			Decides a PENDING request, once.

			- **Approve** lets the customer sign in, marks their details as verified and sends the approval SMS.
			- **Reject** keeps the account disabled and sends the rejection SMS.

			When the review shows `sbfCustomer.outcome` `MISMATCH`, Sambat hold the customer's ID number under
			other details and the customer's loan applications wait. An approval may then carry `confirmCif`,
			the LPO's confirmation that `sbfCustomer.cif` is this customer.

			The outcome is in the answer's `type`:

			- `SUCCESS`: decided. `data` is `APPROVED` or `REJECTED`.
			- `NOT_FOUND`: no request has this id.
			- `INVALID`: the request was already decided (`data` is its status), or `approve` is missing.
			- `IDENTITY_NOT_VERIFIED`: approval is refused until the customer passes identity verification.
			- `CIF_NOT_CONFIRMED`: `confirmCif` is not the CIF Sambat hold for this ID number now, or Sambat
			  could not be asked. Nothing was decided. The message says which.
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
		return service.decide(id, body.approve(), body.reason(), actor(), body.employerCode(), body.confirmCif());
	}

	/** The LPO confirms the CIF of a customer who is already approved. Body: {"cif": n} */
	@Operation(summary = "Confirm the CIF of an approved customer", description = """
			For an APPROVED request whose review shows `sbfCustomer.outcome` `MISMATCH`: Sambat hold the
			customer's ID number under other details, and the customer's loan applications wait until that is
			settled. The LPO confirms here that `sbfCustomer.cif` is this customer. A PENDING request is settled
			on its approval instead, with `confirmCif` on the decision.

			Sambat are asked again, and the CIF is stored only if they still hold the ID number under it. The
			request then records the CIF, what differed, who confirmed and when. A stored CIF cannot be changed.

			The outcome is in the answer's `type`:

			- `SUCCESS`: the customer has this CIF. `data` is the CIF.
			- `NOT_FOUND`: no request has this id.
			- `INVALID`: the request is not approved, or `cif` is missing.
			- `CIF_NOT_CONFIRMED`: `cif` is not the CIF Sambat hold for this ID number now, the customer already
			  has another, or Sambat could not be asked. Nothing was stored. The message says which.
			- `BAD_REQUEST`: the body could not be read. The message names the field.
			""")
	@PostMapping("/{id}/confirm-cif")
	public ResponseEntity<Message<String>> confirmCif(@PathVariable("id") int id,
			@RequestBody PdlCifConfirmRequest body) {
		if (body.cif() == null || body.cif() <= 0)
			return new ResponseEntity<>(new Message<>("INVALID",
					"'cif' is required: the CIF the review shows for this customer.", null),
					HttpStatus.BAD_REQUEST);
		return service.confirmCif(id, body.cif(), actor());
	}

	/** Who is deciding: never taken from the body, so the record cannot be written by the caller. */
	private static String actor() {
		return SecurityContextHolder.getContext().getAuthentication() != null
				? SecurityContextHolder.getContext().getAuthentication().getName()
				: "ADMIN";
	}
}
