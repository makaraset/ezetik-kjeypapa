package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.model.PdlCodeList;
import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.service.PdlDictionaryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins how Sambat's dictionary payloads are read into our mirror.
 *
 * <p>Their credentials are down, so this runs against fixtures shaped from the
 * saved swagger definitions rather than a live pull. It covers the parts that
 * are ours to get right — code selection, parent linking, retired rows — and
 * deliberately not the values, which only a live call can confirm.
 */
class PdlDictionaryParsingTest {

	private final ObjectMapper om = new ObjectMapper();

	private JsonNode fixture(String name) throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/dict/" + name)) {
			assertThat(in).as("fixture " + name).isNotNull();
			return om.readTree(in);
		}
	}

	private List<PdlGeoUnit> geo() throws Exception {
		JsonNode bulk = fixture("bulk_selection.json");
		List<PdlGeoUnit> out = new ArrayList<>();
		PdlDictionaryService.collectGeo(bulk.path("province"), PdlDictionaryService.PROVINCE, null, out);
		PdlDictionaryService.collectGeo(bulk.path("district"), PdlDictionaryService.DISTRICT, "proId", out);
		PdlDictionaryService.collectGeo(bulk.path("commune"), PdlDictionaryService.COMMUNE, "disId", out);
		PdlDictionaryService.collectGeo(fixture("village.json"), PdlDictionaryService.VILLAGE, "comId", out);
		return out;
	}

	private PdlGeoUnit find(List<PdlGeoUnit> all, String level, String code) {
		return all.stream().filter(u -> level.equals(u.getLevel()) && code.equals(u.getCode())).findFirst()
				.orElse(null);
	}

	@Test
	@DisplayName("geo ids become the codes we store, at all four levels")
	void geoCodes() throws Exception {
		List<PdlGeoUnit> all = geo();
		// The NCDD shape Sambat confirmed: 2 / 4 / 6 / 8 digits.
		assertThat(find(all, PdlDictionaryService.PROVINCE, "12").getNameEn()).isEqualTo("Phnom Penh");
		assertThat(find(all, PdlDictionaryService.DISTRICT, "1214").getNameEn()).isEqualTo("Sen Sok");
		assertThat(find(all, PdlDictionaryService.COMMUNE, "121402").getNameEn()).isEqualTo("Krang Thnong");
		assertThat(find(all, PdlDictionaryService.VILLAGE, "12140204").getNameEn()).isEqualTo("Prey Khla");
	}

	@Test
	@DisplayName("each level links to its parent, so the cascade can never cross branches")
	void parentLinking() throws Exception {
		List<PdlGeoUnit> all = geo();
		assertThat(find(all, PdlDictionaryService.PROVINCE, "12").getParentCode()).isNull();
		assertThat(find(all, PdlDictionaryService.DISTRICT, "1214").getParentCode()).isEqualTo("12");
		assertThat(find(all, PdlDictionaryService.COMMUNE, "121402").getParentCode()).isEqualTo("1214");
		// Villages come from /village, which is the ONLY source of a village id:
		// /all-address carries province/district/commune ids but village as a
		// bare string.
		assertThat(find(all, PdlDictionaryService.VILLAGE, "12140204").getParentCode()).isEqualTo("121402");
	}

	@Test
	@DisplayName("retired rows are dropped, not offered to customers")
	void deletedRowsSkipped() throws Exception {
		List<PdlGeoUnit> all = geo();
		assertThat(find(all, PdlDictionaryService.PROVINCE, "99")).isNull();
		assertThat(find(all, PdlDictionaryService.VILLAGE, "12140205")).isNull();
	}

	@Test
	@DisplayName("code lists prefer an explicit code member over the numeric id")
	void codeSelection() throws Exception {
		JsonNode bulk = fixture("bulk_selection.json");
		List<PdlCodeList> out = new ArrayList<>();
		PdlDictionaryService.collectList(bulk.path("maritalStatus"), "MARITAL_STATUS", out);
		PdlDictionaryService.collectList(bulk.path("idType"), "ID_TYPE", out);
		PdlDictionaryService.collectList(bulk.path("countryCode"), "COUNTRY", out);
		PdlDictionaryService.collectList(bulk.path("occupation"), "OCCUPATION", out);

		// cbcCode wins for marital status, idCode for id type, code for country.
		assertThat(pick(out, "MARITAL_STATUS").getCode()).isEqualTo("M");
		assertThat(pick(out, "ID_TYPE").getCode()).isEqualTo("N");
		assertThat(pick(out, "COUNTRY").getCode()).isEqualTo("KHM");
		// Occupation has no code member — Sambat confirmed the id IS the code.
		assertThat(pick(out, "OCCUPATION").getCode()).isEqualTo("7");
		assertThat(pick(out, "OCCUPATION").getNameKh()).isEqualTo("បុគ្គលិកព័ត៌មានវិទ្យា");
	}

	@Test
	@DisplayName("their misspelled list name is read verbatim")
	void nationalityMisspelling() throws Exception {
		JsonNode bulk = fixture("bulk_selection.json");
		List<PdlCodeList> out = new ArrayList<>();
		// "natoinality" is Sambat's spelling; reading "nationality" finds nothing.
		PdlDictionaryService.collectList(bulk.path("natoinality"), "NATIONALITY", out);
		assertThat(out).hasSize(1);
		assertThat(out.get(0).getCode()).isEqualTo("KHM");

		List<PdlCodeList> corrected = new ArrayList<>();
		PdlDictionaryService.collectList(bulk.path("nationality"), "NATIONALITY", corrected);
		assertThat(corrected).isEmpty();
	}

	@Test
	@DisplayName("a malformed row is skipped rather than stored code-less")
	void malformedRowsSkipped() throws Exception {
		JsonNode bad = om.readTree("[{\"description\":\"No id here\"},{\"id\":5,\"description\":\"Fine\"}]");
		List<PdlGeoUnit> out = new ArrayList<>();
		PdlDictionaryService.collectGeo(bad, PdlDictionaryService.PROVINCE, null, out);
		assertThat(out).hasSize(1);
		assertThat(out.get(0).getCode()).isEqualTo("5");
	}

	private PdlCodeList pick(List<PdlCodeList> all, String list) {
		return all.stream().filter(c -> list.equals(c.getListName())).findFirst().orElseThrow();
	}
}
