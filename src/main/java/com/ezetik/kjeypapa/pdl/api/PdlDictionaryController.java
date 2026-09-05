package com.ezetik.kjeypapa.pdl.api;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ezetik.kjeypapa.pdl.service.PaydayLoanServiceImpl;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.pdl.model.PdlCodeList;
import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.service.PdlDictionaryService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Sambat's selection dictionaries, served to the app.
 *
 * <p>Explicitly whitelisted in the security config, because the address pickers run on the pre-login signup screens
 * (V8 5-6) — there is no JWT yet at that point. Nothing here is
 * customer-specific: it is Sambat's public master data.
 *
 * <p>Reads are served from our mirror and never proxy to Sambat — see
 * {@link PdlDictionaryService}.
 */
@RestController
@RequestMapping("/api/v1/pdl/dictionary")
@Tag(name = "12- PDL Dictionary API", description = "Sambat selection dictionaries (geo + code lists)")
public class PdlDictionaryController {

	@Autowired
	private PdlDictionaryService service;

	@Autowired
	private com.ezetik.kjeypapa.pdl.service.CbcConsentFormRenderer consentForm;

	@org.springframework.beans.factory.annotation.Value("${pdl.cbc.text-version:v1}")
	private String currentTextVersion;

	/**
	 * @param level  province | district | commune | village
	 * @param parent parent code — required for every level below province, so a
	 *               response is always a single parent's children
	 * @param q      optional contains-filter over the English and Khmer names
	 */
	@GetMapping("/geo")
	public ResponseEntity<Message<List<PdlGeoUnit>>> geo(@RequestParam String level,
			@RequestParam(required = false) String parent,
			@RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "200") int size) {
		return service.geo(level, parent, q, size);
	}

	/** @param name OCCUPATION | MARITAL_STATUS | NATIONALITY | COUNTRY | ID_TYPE | ID_ISSUER | BUSINESS_ACTIVITY */
	@GetMapping("/list")
	public ResponseEntity<Message<List<PdlCodeList>>> list(@RequestParam String name) {
		// EMPLOYER is Sambat's approved-employer CLIENT list, not public master
		// data like the rest — this endpoint is unauthenticated for the signup
		// pickers, so that one list is served by the admin controller only.
		if ("EMPLOYER".equalsIgnoreCase(name == null ? "" : name.trim()))
			return ResponseEntity.ok(new Message<>("SUCCESS", "OK", List.of()));
		return service.list(name);
	}

	/**
	 * The CBC consent wording, with the hash the consent record will carry.
	 *
	 * <p>The app must render THIS rather than a copy bundled in its own assets.
	 * A bundled copy is a second source of the same legal text: the two were
	 * byte-identical but nothing enforced it, so an app release could change
	 * what the customer agrees to without changing what the record proves — and
	 * the record would still verify. Serving it means the text read and the text
	 * hashed are one string.
	 *
	 * @param version the wording version; omit for the current one
	 */
	@GetMapping("/consent-text")
	public ResponseEntity<Message<Map<String, String>>> consentText(
			@RequestParam(required = false) String version) {
		String text = consentForm.consentTextKm(version);
		return ResponseEntity.ok(new Message<>("SUCCESS", "OK", Map.of(
				"version", version == null || version.isBlank() ? currentTextVersion : version,
				"text", text,
				"hash", PaydayLoanServiceImpl.sha256(text))));
	}

	/** Snapshot stamp, so the app can skip re-downloading unchanged lists. */
	@GetMapping("/version")
	public ResponseEntity<Message<String>> version() {
		return service.version();
	}
}
