package com.ezetik.kjeypapa.pdl.service;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.repository.PdlGeoUnitRepository;

import lombok.Getter;
import lombok.Setter;

/**
 * Resolves the single free-form address line on a Cambodian ID card into
 * Sambat's four geo CODES.
 *
 * <p>This lives on the server, not in the app, because it has to match against
 * Sambat's own gazetteer — and their romanisation differs from the NCDD dataset
 * the app ships. Measured against their live data: our
 * {@code Sen Sok} / {@code Chamkarmon} do not exist in their list, which spells
 * it {@code Saensokh}, and 14 districts differ overall. An app-side match
 * therefore produces names Sambat cannot code, which is exactly the failure we
 * are removing.
 *
 * <p>Khmer is the better match key here anyway: the card prints Khmer, SBF's
 * OCR returns Khmer, and their {@code /all-address} carries Khmer at every
 * level. English is tried as a fallback.
 *
 * <p>Matching descends the hierarchy — province, then only that province's
 * districts, and so on — so a name that repeats nationally can only resolve
 * under the parent already agreed. A level that does not match STOPS the
 * cascade: the levels below stay blank rather than being guessed, because a
 * wrong commune on a credit file costs far more than an empty one.
 */
@Component
public class KhAddressResolver {

	/** Unit words the card prints before each name; not part of the name. */
	private static final String[] UNIT_WORDS = { "រាជធានី", "ខេត្ត", "ក្រុង", "ស្រុក", "ខណ្ឌ", "សង្កាត់", "ឃុំ",
			"ភូមិ" };

	@Autowired
	private PdlGeoUnitRepository repo;

	/** One resolved address: codes plus the names they came from. */
	@Getter
	@Setter
	public static class Resolved {
		private String provinceCode = "";
		private String provinceName = "";
		private String districtCode = "";
		private String districtName = "";
		private String communeCode = "";
		private String communeName = "";
		private String villageCode = "";
		private String villageName = "";
		private String houseStreetNo = "";

		public boolean isEmpty() {
			return provinceCode.isEmpty() && houseStreetNo.isEmpty();
		}
	}

	public Resolved resolve(String raw) {
		Resolved out = new Resolved();
		String text = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
		if (text.isEmpty())
			return out;

		Hit province = match(text, repo.findByLevelOrderByNameEnAsc(PdlDictionaryService.PROVINCE));
		if (province == null) {
			out.houseStreetNo = text;
			return out;
		}
		out.provinceCode = province.unit.getCode();
		out.provinceName = province.unit.getNameEn();

		Hit district = match(text, repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.DISTRICT,
				province.unit.getCode()));
		if (district == null) {
			out.houseStreetNo = leadIn(text, province, null, null, null);
			return out;
		}
		out.districtCode = district.unit.getCode();
		out.districtName = district.unit.getNameEn();

		Hit commune = match(text, repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.COMMUNE,
				district.unit.getCode()));
		if (commune == null) {
			out.houseStreetNo = leadIn(text, province, district, null, null);
			return out;
		}
		out.communeCode = commune.unit.getCode();
		out.communeName = commune.unit.getNameEn();

		Hit village = match(text, repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.VILLAGE,
				commune.unit.getCode()));
		if (village != null) {
			out.villageCode = village.unit.getCode();
			out.villageName = village.unit.getNameEn();
		}
		out.houseStreetNo = leadIn(text, province, district, commune, village);
		return out;
	}

	/**
	 * Longest match wins, so a short name that happens to be a substring of the
	 * right one cannot beat it. Khmer is checked before English.
	 */
	private static Hit match(String text, List<PdlGeoUnit> pool) {
		String lower = text.toLowerCase(Locale.ROOT);
		Hit best = null;
		for (PdlGeoUnit u : pool) {
			best = better(best, find(text, u.getNameKh(), u, false));
			best = better(best, find(lower, u.getNameEn(), u, true));
		}
		return best;
	}

	private static Hit find(String haystack, String name, PdlGeoUnit unit, boolean lower) {
		if (name == null || name.isBlank())
			return null;
		String needle = lower ? name.toLowerCase(Locale.ROOT) : name;
		int at = haystack.indexOf(needle);
		return at < 0 ? null : new Hit(unit, at, name.length());
	}

	private static Hit better(Hit a, Hit b) {
		if (b == null)
			return a;
		return (a == null || b.length > a.length) ? b : a;
	}

	/** Whatever preceded the first matched unit, minus a trailing unit word. */
	private static String leadIn(String text, Hit... hits) {
		int cut = text.length();
		for (Hit h : hits)
			if (h != null && h.start < cut)
				cut = h.start;
		String head = text.substring(0, cut).trim();
		for (String w : UNIT_WORDS) {
			if (head.endsWith(w)) {
				head = head.substring(0, head.length() - w.length()).trim();
				break;
			}
		}
		return head;
	}

	private record Hit(PdlGeoUnit unit, int start, int length) {
	}
}
