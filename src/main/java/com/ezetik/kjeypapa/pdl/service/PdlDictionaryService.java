package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ezetik.kjeypapa.pdl.model.PdlCodeList;
import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.repository.PdlCodeListRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlGeoUnitRepository;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Mirrors Sambat's "02- Selection dictionary" lists so the app can offer THEIR
 * codes and the LOS submit can send them.
 *
 * <p>Three deliberate choices:
 *
 * <ul>
 * <li><b>Persisted, not memory-only.</b> The address pickers run on the
 * pre-login signup screens, so a backend restart while Sambat is unreachable —
 * exactly the situation while their credentials are down — would otherwise
 * leave new applicants with empty province lists and no account to fall back
 * on.
 * <li><b>The read path never calls Sambat.</b> Reads come from the table via an
 * in-memory snapshot; only the scheduled warmer or an admin refresh fetches.
 * The read endpoint is anonymous and unrated, so proxying a cache miss upstream
 * would turn it into an amplification vector against their gateway.
 * <li><b>A failed refresh keeps the last good data.</b> Rows are replaced only
 * once a fetch has fully succeeded and parsed.
 * </ul>
 */
@Service
@Slf4j
public class PdlDictionaryService {

	public static final String PROVINCE = "PROVINCE";
	public static final String DISTRICT = "DISTRICT";
	public static final String COMMUNE = "COMMUNE";
	public static final String VILLAGE = "VILLAGE";

	/** Sambat's list name (in /bulk-selection) -> ours. */
	private static final String[][] CODE_LISTS = {
			{ "occupation", "OCCUPATION" },
			{ "maritalStatus", "MARITAL_STATUS" },
			{ "natoinality", "NATIONALITY" }, // their spelling
			{ "countryCode", "COUNTRY" },
			{ "idType", "ID_TYPE" },
			{ "idIssuer", "ID_ISSUER" },
			{ "businessActivity", "BUSINESS_ACTIVITY" },
	};

	@Autowired
	private PdlGeoUnitRepository geoRepo;
	@Autowired
	private PdlCodeListRepository codeRepo;
	@Autowired
	private SbfGatewayClient sbf;

	/** When the persisted snapshot was last replaced; null until first load. */
	private volatile Instant fetchedAt;

	// ----- read API (never touches Sambat) -----

	public ResponseEntity<Message<List<PdlGeoUnit>>> geo(String level, String parent, String q, int size) {
		String lvl = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
		if (!PROVINCE.equals(lvl) && !DISTRICT.equals(lvl) && !COMMUNE.equals(lvl) && !VILLAGE.equals(lvl))
			return resp("INVALID", "level must be province, district, commune or village", null,
					HttpStatus.EXPECTATION_FAILED);
		// Everything below province is parent-scoped, so no response can ever be
		// the whole 14k-row village table.
		if (!PROVINCE.equals(lvl) && (parent == null || parent.isBlank()))
			return resp("INVALID", "parent is required for " + lvl.toLowerCase(Locale.ROOT), null,
					HttpStatus.EXPECTATION_FAILED);

		List<PdlGeoUnit> rows = PROVINCE.equals(lvl) ? geoRepo.findByLevelOrderByNameEnAsc(lvl)
				: geoRepo.findByLevelAndParentCodeOrderByNameEnAsc(lvl, parent.trim());

		if (q != null && !q.isBlank()) {
			String needle = q.trim().toLowerCase(Locale.ROOT);
			rows = rows.stream()
					.filter(u -> contains(u.getNameEn(), needle) || contains(u.getNameKh(), needle))
					.toList();
		}
		int cap = Math.min(size <= 0 ? 200 : size, 500);
		if (rows.size() > cap)
			rows = rows.subList(0, cap);
		return resp("SUCCESS", "OK", rows, HttpStatus.OK);
	}

	public ResponseEntity<Message<List<PdlCodeList>>> list(String name) {
		String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
		List<PdlCodeList> rows = codeRepo.findByListNameOrderByNameEnAsc(n);
		if (rows.isEmpty())
			return resp("NOT_FOUND", "No such dictionary: " + name, List.of(), HttpStatus.OK);
		return resp("SUCCESS", "OK", rows, HttpStatus.OK);
	}

	/** Lets the app skip a re-download when nothing has changed. */
	public ResponseEntity<Message<String>> version() {
		Instant at = fetchedAt;
		if (at == null && geoRepo.countByLevel(PROVINCE) > 0)
			at = Instant.EPOCH; // loaded by a previous run; unknown timestamp
		return resp("SUCCESS", "OK", at == null ? "" : at.toString(), HttpStatus.OK);
	}

	// ----- refresh (admin / scheduler only) -----

	/**
	 * Pulls every dictionary from Sambat and replaces the mirror.
	 *
	 * @return a short human summary of what landed
	 */
	@Transactional
	public String refresh() throws Exception {
		JsonNode bulk = sbf.bulkSelection();
		JsonNode villages = sbf.villages();

		List<PdlGeoUnit> geo = new ArrayList<>();
		// province {id, description, ctryId} / district {id, description, proId}
		// / commune {id, description, disId} / village {id, description, comId}
		collectGeo(bulk.path("province"), PROVINCE, null, geo);
		collectGeo(bulk.path("district"), DISTRICT, "proId", geo);
		collectGeo(bulk.path("commune"), COMMUNE, "disId", geo);
		collectGeo(villages, VILLAGE, "comId", geo);

		List<PdlCodeList> lists = new ArrayList<>();
		for (String[] pair : CODE_LISTS)
			collectList(bulk.path(pair[0]), pair[1], lists);

		if (geo.isEmpty() && lists.isEmpty())
			throw new IllegalStateException("Sambat returned no dictionary rows — keeping the previous snapshot");

		// Replace only now that everything parsed: a half-applied refresh would
		// leave districts pointing at provinces that no longer exist.
		geoRepo.deleteAllInBatch();
		codeRepo.deleteAllInBatch();
		geoRepo.saveAll(geo);
		codeRepo.saveAll(lists);
		fetchedAt = Instant.now();

		String summary = String.format("geo=%d (prov %d, dist %d, comm %d, vill %d), lists=%d",
				geo.size(), count(geo, PROVINCE), count(geo, DISTRICT), count(geo, COMMUNE),
				count(geo, VILLAGE), lists.size());
		log.info("PDL dictionary refreshed: {}", summary);
		return summary;
	}

	/** Pure; public so the parsing rules can be pinned by tests. */
	public static void collectGeo(JsonNode array, String level, String parentField, List<PdlGeoUnit> out) {
		if (array == null || !array.isArray())
			return;
		for (JsonNode n : array) {
			if (isDeleted(n))
				continue;
			String code = text(n, "id");
			if (code.isEmpty())
				continue;
			out.add(new PdlGeoUnit(level, code, parentField == null ? null : emptyToNull(text(n, parentField)),
					text(n, "description"), firstNonBlank(n, "descriptionKh", "khDescription", "descriptionKH")));
		}
	}

	/** Pure; public so the parsing rules can be pinned by tests. */
	public static void collectList(JsonNode array, String listName, List<PdlCodeList> out) {
		if (array == null || !array.isArray())
			return;
		for (JsonNode n : array) {
			if (isDeleted(n))
				continue;
			// Prefer an explicit code member; fall back to the numeric id, which
			// Sambat confirmed is the code for occupation.
			String code = firstNonBlank(n, "cbcCode", "code", "idCode", "id");
			if (code.isEmpty())
				continue;
			out.add(new PdlCodeList(listName, code,
					firstNonBlank(n, "description", "regDescription", "valueEn"),
					firstNonBlank(n, "descriptionKh", "khDescription", "valueKh")));
		}
	}

	/** Sambat marks retired rows with a "deleted" flag rather than removing them. */
	private static boolean isDeleted(JsonNode n) {
		String d = text(n, "deleted");
		return "true".equalsIgnoreCase(d) || "Y".equalsIgnoreCase(d) || "1".equals(d);
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.path(field);
		return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
	}

	private static String firstNonBlank(JsonNode n, String... fields) {
		for (String f : fields) {
			String v = text(n, f);
			if (!v.isEmpty())
				return v;
		}
		return "";
	}

	private static String emptyToNull(String s) {
		return s == null || s.isEmpty() ? null : s;
	}

	private static boolean contains(String hay, String lowerNeedle) {
		return hay != null && hay.toLowerCase(Locale.ROOT).contains(lowerNeedle);
	}

	private static long count(List<PdlGeoUnit> units, String level) {
		return units.stream().filter(u -> level.equals(u.getLevel())).count();
	}

	private static <T> ResponseEntity<Message<T>> resp(String type, String msg, T data, HttpStatus st) {
		Message<T> m = new Message<>();
		m.setType(type);
		m.setMessage(msg);
		m.setData(data);
		return new ResponseEntity<>(m, st);
	}
}
