package com.ezetik.kjeypapa.pdl.service;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlGeoUnitRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;

/**
 * Gives rows captured before geo codes existed their Sambat codes, by exact
 * name match (English or Khmer) walked down the hierarchy.
 *
 * <p>Exact, never fuzzy: our old gazetteer romanised 14 districts differently
 * from Sambat ("Sen Sok" vs their "Saensokh"), and guessing across that gap
 * would file a credit application against the wrong locality. A level that
 * does not match is left blank for re-capture, and nothing below it is set.
 */
@Service
public class PdlGeoBackfillService {

	@Autowired
	private PdlGeoUnitRepository geo;
	@Autowired
	private PdlPersonalInfoRepository personalRepo;
	@Autowired
	private PdlEmploymentInfoRepository employmentRepo;

	public record Result(int rowsSeen, int levelsFilled, int levelsUnmatched) {
	}

	@Transactional
	public Result run() {
		int seen = 0, filled = 0, missed = 0;
		for (PdlPersonalInfo p : personalRepo.findAll()) {
			seen++;
			int[] c = chain(p.getCorrProvince(), p.getCorrDistrict(), p.getCorrCommune(), p.getCorrVillage(),
					p.getCorrProvinceCode(), p.getCorrDistrictCode(), p.getCorrCommuneCode(), p.getCorrVillageCode(),
					codes -> { p.setCorrProvinceCode(codes[0]); p.setCorrDistrictCode(codes[1]);
							p.setCorrCommuneCode(codes[2]); p.setCorrVillageCode(codes[3]); });
			filled += c[0]; missed += c[1];
			c = chain(p.getPermProvince(), p.getPermDistrict(), p.getPermCommune(), p.getPermVillage(),
					p.getPermProvinceCode(), p.getPermDistrictCode(), p.getPermCommuneCode(), p.getPermVillageCode(),
					codes -> { p.setPermProvinceCode(codes[0]); p.setPermDistrictCode(codes[1]);
							p.setPermCommuneCode(codes[2]); p.setPermVillageCode(codes[3]); });
			filled += c[0]; missed += c[1];
			c = chain(p.getBirthProvince(), p.getBirthDistrict(), null, null,
					p.getBirthProvinceCode(), p.getBirthDistrictCode(), null, null,
					codes -> { p.setBirthProvinceCode(codes[0]); p.setBirthDistrictCode(codes[1]); });
			filled += c[0]; missed += c[1];
			personalRepo.save(p);
		}
		for (PdlEmploymentInfo e : employmentRepo.findAll()) {
			seen++;
			int[] c = chain(e.getWorkProvince(), e.getWorkDistrict(), e.getWorkCommune(), e.getWorkVillage(),
					e.getWorkProvinceCode(), e.getWorkDistrictCode(), e.getWorkCommuneCode(), e.getWorkVillageCode(),
					codes -> { e.setWorkProvinceCode(codes[0]); e.setWorkDistrictCode(codes[1]);
							e.setWorkCommuneCode(codes[2]); e.setWorkVillageCode(codes[3]); });
			filled += c[0]; missed += c[1];
			employmentRepo.save(e);
		}
		return new Result(seen, filled, missed);
	}

	private interface Apply { void codes(String[] codes); }

	/** Resolves the four names top-down; returns {filled, unmatched}. */
	private int[] chain(String prov, String dist, String comm, String vill,
			String provCode, String distCode, String commCode, String villCode, Apply apply) {
		String[] out = { nz(provCode), nz(distCode), nz(commCode), nz(villCode) };
		int filled = 0, missed = 0;
		String[] names = { prov, dist, comm, vill };
		String[] levels = { PdlDictionaryService.PROVINCE, PdlDictionaryService.DISTRICT,
				PdlDictionaryService.COMMUNE, PdlDictionaryService.VILLAGE };
		String parent = null;
		for (int i = 0; i < 4; i++) {
			if (blank(names[i]))
				break; // nothing captured at this level
			if (!out[i].isEmpty()) { // already coded — just continue the chain
				parent = out[i];
				continue;
			}
			List<PdlGeoUnit> pool = i == 0 ? geo.findByLevelOrderByNameEnAsc(levels[i])
					: (parent == null ? List.of() : geo.findByLevelAndParentCodeOrderByNameEnAsc(levels[i], parent));
			final String name = names[i];
			PdlGeoUnit hit = pool.stream().filter(u -> eq(u.getNameEn(), name) || eq(u.getNameKh(), name))
					.findFirst().orElse(null);
			if (hit == null) {
				missed++;
				break; // stop the cascade — never code a child under a guessed parent
			}
			out[i] = hit.getCode();
			parent = hit.getCode();
			filled++;
		}
		apply.codes(out);
		return new int[] { filled, missed };
	}

	private static boolean eq(String a, String b) {
		return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
	}

	private static boolean blank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}
}
