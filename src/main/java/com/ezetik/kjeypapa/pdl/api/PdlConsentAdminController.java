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
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.ArchiveReport;
import com.ezetik.kjeypapa.pdl.service.PdlCbcConsentLedger.LedgerReport;
import com.ezetik.kjeypapa.pdl.service.PdlConsentBackfillService;
import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService;
import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService.RetentionReport;
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

	@Autowired
	private PdlConsentRetentionService retention;

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
	 * Checks every archived consent document against the hash its record keeps.
	 *
	 * <p>Its own endpoint because it reads every stored PDF, which the chain walk
	 * above does not. The two answer different questions and neither implies the
	 * other: the documents live in an ordinary table with no trigger on it, so
	 * one can be replaced while the chain still verifies perfectly.
	 */
	@GetMapping("/verify-archives")
	public ResponseEntity<Message<ArchiveReport>> verifyArchives() {
		ArchiveReport r = ledger.verifyArchives();
		boolean ok = r.findings().isEmpty();
		return ResponseEntity.ok(new Message<>(ok ? "SUCCESS" : "FAILED",
				ok ? "All " + r.checked() + " archived document(s) match their records"
						: r.missing() + " missing, " + r.altered() + " altered",
				r));
	}

	/**
	 * Consent records whose loan file has been over for longer than the
	 * configured retention period.
	 *
	 * <p>A list, not an action. Nothing is deleted here or by the nightly job —
	 * disposal of consent evidence is a deliberate act by somebody who has read
	 * this, and the period itself is still to be confirmed with Sambat. Each
	 * candidate names the date its clock started and where that date came from,
	 * because the schema records no loan-closure timestamp and some of those
	 * dates are approximations.
	 */
	@GetMapping("/retention")
	public ResponseEntity<Message<RetentionReport>> retention() {
		RetentionReport r = retention.report();
		String msg = !r.configured() ? "No retention period configured; nothing expires"
				: r.candidates().size() + " consent(s) past retention, reported only";
		return ResponseEntity.ok(new Message<>("SUCCESS", msg, r));
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
