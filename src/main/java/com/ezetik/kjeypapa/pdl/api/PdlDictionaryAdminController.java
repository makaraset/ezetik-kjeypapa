package com.ezetik.kjeypapa.pdl.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.service.PdlDictionaryService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Operator control over the Sambat dictionary mirror.
 *
 * <p>Separate from {@link PdlAdminController}, whose base path is
 * {@code /account-requests} — hanging a dictionary route off that class gives
 * it a nonsense URL.
 */
@RestController
@RequestMapping("/api/v1/pdl/admin/dictionary")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "12- PDL Dictionary API", description = "Admin refresh of the Sambat dictionary mirror")
public class PdlDictionaryAdminController {

	@Autowired
	private PdlDictionaryService dictionary;

	@Autowired
	private com.ezetik.kjeypapa.pdl.service.PdlGeoBackfillService backfill;

	/**
	 * Pull Sambat's dictionaries now. The read endpoints never fetch, so this
	 * and the nightly job are the only callers.
	 */
	@PostMapping("/refresh")
	public ResponseEntity<Message<String>> refresh() {
		try {
			return ResponseEntity.ok(new Message<>("SUCCESS", "Dictionary refreshed", dictionary.refresh()));
		} catch (Exception e) {
			return new ResponseEntity<>(new Message<>("FAILED", "Refresh failed: " + e.getMessage(), null),
					HttpStatus.EXPECTATION_FAILED);
		}
	}

	/**
	 * Code the address rows captured before Sambat's geo codes existed, by
	 * exact name match against the mirror. Safe to re-run; only blank codes
	 * are touched.
	 */
	@PostMapping("/backfill-geo-codes")
	public ResponseEntity<Message<String>> backfillGeoCodes() {
		var r = backfill.run();
		return ResponseEntity.ok(new Message<>("SUCCESS", "Backfill complete",
				"rows=" + r.rowsSeen() + " levelsFilled=" + r.levelsFilled() + " levelsUnmatched=" + r.levelsUnmatched()));
	}
}
