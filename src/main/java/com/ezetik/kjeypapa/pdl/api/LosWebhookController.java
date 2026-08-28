package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.payload.LosBankVerificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosDisbursementPayload;
import com.ezetik.kjeypapa.pdl.payload.LosLoanUpdatePayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosProductSyncPayload;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Inbound webhooks from Sambat LOS (server-to-server). These are on the security
 * whitelist (no customer JWT); signature verification is TBD per the LOS contract.
 * Maps to BRS 2.2 / 2.4 / 2.5 / 2.6.
 */
@RestController
@RequestMapping("/api/v1/pdl/los")
@Tag(name = "09- LOS Webhook API", description = "Sambat LOS inbound notifications (server-to-server)")
public class LosWebhookController {

	@Autowired
	private LosWebhookService service;

	/** BRS 2.4 — reject notification. */
	@PostMapping("/reject")
	public ResponseEntity<Message<String>> reject(@RequestBody LosNotificationPayload payload) {
		return service.handleReject(payload);
	}

	/** BRS 2.5 — rework status sync (PDL: rework = reject). */
	@PostMapping("/rework")
	public ResponseEntity<Message<String>> rework(@RequestBody LosNotificationPayload payload) {
		return service.handleRework(payload);
	}

	/** BRS 2.6 — approved (+ LOS-generated docs and repayment schedule). */
	@PostMapping("/approved")
	public ResponseEntity<Message<String>> approved(@RequestBody LosNotificationPayload payload) {
		return service.handleApproved(payload);
	}

	/** BRS 2.2 — new/update loan product. */
	@PostMapping("/product-sync")
	public ResponseEntity<Message<String>> productSync(@RequestBody LosProductSyncPayload payload) {
		return service.handleProductSync(payload);
	}

	/** Disbursement status (after accept, LOS runs the Campu Bank direct-debit). */
	@PostMapping("/disbursement")
	public ResponseEntity<Message<String>> disbursement(@RequestBody LosDisbursementPayload payload) {
		return service.handleDisbursement(payload);
	}

	/** Loan / repayment status update (outstanding, DPD, per-installment paid). */
	@PostMapping("/loan-update")
	public ResponseEntity<Message<String>> loanUpdate(@RequestBody LosLoanUpdatePayload payload) {
		return service.handleLoanUpdate(payload);
	}

	// The former POST /account-decision mock was removed on 2026-08-28. It sat on
	// the unauthenticated LOS-webhook whitelist and could approve ANY pending
	// account by username — i.e. mint a customer login with no LPO review —
	// while nothing called it: approval is owned by our own PdlAdminController.

	/**
	 * V21 — result of the Bank-Mobile-App verification / disbursement-consent step
	 * (after Accept). SCAFFOLDING: no-ops unless {@code pdl.bank-verification.enabled=true}.
	 * Contract (deep-link + this callback shape) pending Sambat/Bank (Q4).
	 */
	@PostMapping("/bank-verification")
	public ResponseEntity<Message<String>> bankVerification(@RequestBody LosBankVerificationPayload payload) {
		return service.handleBankVerification(payload);
	}
}
