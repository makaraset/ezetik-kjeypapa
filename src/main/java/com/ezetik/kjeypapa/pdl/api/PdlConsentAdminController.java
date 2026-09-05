package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.service.LosSubmitException;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.LedgerReport;
import com.ezetik.kjeypapa.pdl.service.PdlConsentBackfillService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Operator view of the consent ledger.
 *
 * <p>Its own controller rather than a route hung off the dictionary or
 * account-request controllers: those carry their own base paths, and an
 * endpoint inherited into the wrong one gets a nonsense URL and a 403 that
 * looks like a permissions bug.
 */
@RestController
@RequestMapping("/api/v1/pdl/admin/consent-ledger")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "13- PDL Consent Ledger", description = "Append-only CBC consent records")
public class PdlConsentAdminController {

	@Autowired
	private PdlCbcConsentLedger ledger;

	@Autowired
	private PdlConsentBackfillService backfill;

	/**
	 * Walks the chain and reports anything that does not hold up.
	 *
	 * <p>The head hash is worth recording outside this database. The chain shows
	 * that nobody altered a record without also recomputing the chain; only a
	 * copy of the head kept elsewhere closes the case of someone who did both.
	 */
	@GetMapping("/verify")
	public ResponseEntity<Message<LedgerReport>> verify() {
		LedgerReport report = ledger.verify();
		String note = report.incomplete() == 0 ? ""
				: " (" + report.incomplete() + " pre-ledger record(s) incomplete by origin)";
		return ResponseEntity.ok(new Message<>(report.broken() == 0 ? "SUCCESS" : "FAILED",
				report.broken() == 0 ? "Consent ledger intact" + note
						: report.broken() + " record(s) do not verify" + note,
				report));
	}

	/**
	 * Brings consents recorded before the ledger existed into the chain. Safe to
	 * re-run; refuses once live consents exist, because a hash chain cannot be
	 * prepended to.
	 */
	@PostMapping("/backfill")
	public ResponseEntity<Message<String>> backfill() {
		try {
			int n = backfill.backfill();
			return ResponseEntity.ok(
					new Message<>("SUCCESS", "Backfilled " + n + " consent record(s)", String.valueOf(n)));
		} catch (LosSubmitException e) {
			return new ResponseEntity<>(new Message<>("FAILED", e.getMessage(), null),
					HttpStatus.EXPECTATION_FAILED);
		}
	}
}
