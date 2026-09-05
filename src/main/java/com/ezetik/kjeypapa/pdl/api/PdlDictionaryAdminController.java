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

	@Autowired
	private com.ezetik.kjeypapa.pdl.service.LosApplicationMapper losMapper;

	@Autowired
	private com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository loanRepo;

	@Autowired
	private com.fasterxml.jackson.databind.ObjectMapper om;

	/**
	 * Code-list read for the LPO console — same rows as the public
	 * {@code /pdl/dictionary/list}, but behind ADMIN so it can also serve
	 * EMPLOYER (Sambat's approved-employer client list, which the public
	 * endpoint refuses).
	 */
	@org.springframework.web.bind.annotation.GetMapping("/list")
	public ResponseEntity<Message<java.util.List<com.ezetik.kjeypapa.pdl.model.PdlCodeList>>> list(
			@org.springframework.web.bind.annotation.RequestParam String name) {
		return dictionary.list(name);
	}

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

	/**
	 * The exact JSON {@code POST /new-loan-application} would carry for a loan,
	 * with every {@code Doc_*} base64 replaced by its byte length. Runs the
	 * real mapper without the config gate, so unset Sambat codes show as
	 * blank — which is precisely what there is to compare against their
	 * reference payload. Nothing is sent anywhere.
	 */
	@org.springframework.web.bind.annotation.GetMapping("/los-preview/{loanId}")
	@SuppressWarnings("unchecked")
	public ResponseEntity<Message<java.util.Map<String, Object>>> losPreview(
			@org.springframework.web.bind.annotation.PathVariable int loanId) {
		var loan = loanRepo.findById(loanId).orElse(null);
		if (loan == null)
			return new ResponseEntity<>(new Message<>("NOT_FOUND", "No loan " + loanId, null), HttpStatus.OK);
		// A Map, not a JsonNode: this ObjectMapper is Jackson 2 while the MVC
		// converter is Jackson 3, and a Jackson-2 node serialises there as a bean.
		java.util.Map<String, Object> json = om.convertValue(losMapper.preview(loan), java.util.Map.class);
		java.util.Map<String, Object> req = (java.util.Map<String, Object>) json.get("newAppRequest");
		for (var e : req.entrySet()) {
			String f = e.getKey();
			if (f.startsWith("Doc_") && !f.endsWith("_FileName") && e.getValue() instanceof String v && v.length() > 64)
				e.setValue("<base64, " + (v.length() * 3 / 4) + " bytes>");
		}
		return ResponseEntity.ok(new Message<>("SUCCESS", "LOS payload preview (not sent)", json));
	}
}
