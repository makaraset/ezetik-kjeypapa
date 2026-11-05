package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;
import com.ezetik.kjeypapa.pdl.repository.PdlGeoUnitRepository;
import com.ezetik.kjeypapa.pdl.service.KhAddressResolver;
import com.ezetik.kjeypapa.pdl.service.PdlDictionaryService;

/**
 * The card prints Khmer, so Khmer is the match key. These rows are Sambat's
 * real values, including their romanisation "Saensokh" — which is precisely
 * why this resolution moved off the app, whose gazetteer says "Sen Sok".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KhAddressResolverTest {

	@Mock
	PdlGeoUnitRepository repo;

	@InjectMocks
	KhAddressResolver resolver;

	private static final String CARD = "ផ្ទះ540  ផ្លវលំ  ភូមិព្រៃខ្លា សង្កាត់ក្រាំងធ្នង់ ខណ្ឌសែនសុខ ភ្នំពេញ";

	private void gazetteer() {
		when(repo.findByLevelOrderByNameEnAsc(PdlDictionaryService.PROVINCE)).thenReturn(List.of(
				new PdlGeoUnit(PdlDictionaryService.PROVINCE, "12", null, "Phnom Penh", "ភ្នំពេញ"),
				new PdlGeoUnit(PdlDictionaryService.PROVINCE, "20", null, "Svay Rieng", "ស្វាយរៀង")));
		when(repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.DISTRICT, "12"))
				.thenReturn(List.of(
						new PdlGeoUnit(PdlDictionaryService.DISTRICT, "1208", "12", "Saensokh", "សែនសុខ"),
						new PdlGeoUnit(PdlDictionaryService.DISTRICT, "1201", "12", "Chamkar Mon", "ចំការមន")));
		when(repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.COMMUNE, "1208"))
				.thenReturn(List.of(
						new PdlGeoUnit(PdlDictionaryService.COMMUNE, "120807", "1208", "Krang Thnong",
								"ក្រាំងធ្នង់"),
						new PdlGeoUnit(PdlDictionaryService.COMMUNE, "120801", "1208", "Tuek Thla", "ទឹកថ្លា")));
		when(repo.findByLevelAndParentCodeOrderByNameEnAsc(PdlDictionaryService.VILLAGE, "120807"))
				.thenReturn(List.of(
						new PdlGeoUnit(PdlDictionaryService.VILLAGE, "12080705", "120807", "Prey Khla",
								"ព្រៃខ្លា"),
						new PdlGeoUnit(PdlDictionaryService.VILLAGE, "12080701", "120807", "Prey Mul",
								"ព្រៃមូល")));
	}

	@Test
	@DisplayName("a real card address resolves to all four NCDD codes")
	void resolvesRealCard() {
		gazetteer();
		KhAddressResolver.Resolved r = resolver.resolve(CARD);
		assertThat(r.getProvinceCode()).isEqualTo("12");
		assertThat(r.getDistrictCode()).isEqualTo("1208");
		assertThat(r.getCommuneCode()).isEqualTo("120807");
		assertThat(r.getVillageCode()).isEqualTo("12080705");
	}

	@Test
	@DisplayName("names come back as Sambat spells them, not as we do")
	void namesAreSambats() {
		gazetteer();
		KhAddressResolver.Resolved r = resolver.resolve(CARD);
		// Our own gazetteer calls this "Sen Sok"; storing that could never be
		// coded by Sambat.
		assertThat(r.getDistrictName()).isEqualTo("Saensokh");
	}

	@Test
	@DisplayName("the lead-in becomes house/street, with the unit word stripped")
	void leadIn() {
		gazetteer();
		assertThat(resolver.resolve(CARD).getHouseStreetNo()).isEqualTo("ផ្ទះ540 ផ្លវលំ");
	}

	@Test
	@DisplayName("an unmatched level stops the cascade instead of guessing")
	void cascadeStops() {
		gazetteer();
		// Commune garbled: commune AND village must stay blank. A wrong commune
		// on a credit file costs far more than an empty one.
		KhAddressResolver.Resolved r = resolver
				.resolve("ផ្ទះ540 ភូមិព្រៃខ្លា សង្កាត់XXXX ខណ្ឌសែនសុខ ភ្នំពេញ");
		assertThat(r.getProvinceCode()).isEqualTo("12");
		assertThat(r.getDistrictCode()).isEqualTo("1208");
		assertThat(r.getCommuneCode()).isEmpty();
		assertThat(r.getVillageCode()).isEmpty();
	}

	@Test
	@DisplayName("an unknown province yields no codes at all, text preserved")
	void unknownProvince() {
		gazetteer();
		KhAddressResolver.Resolved r = resolver.resolve("ផ្ទះ1 ផ្លូវ2 XXXX YYYY");
		assertThat(r.getProvinceCode()).isEmpty();
		assertThat(r.getHouseStreetNo()).isEqualTo("ផ្ទះ1 ផ្លូវ2 XXXX YYYY");
	}

	@Test
	@DisplayName("English is accepted as a fallback when the card is romanised")
	void englishFallback() {
		gazetteer();
		KhAddressResolver.Resolved r = resolver
				.resolve("House 540, Prey Khla, Krang Thnong, Saensokh, Phnom Penh");
		assertThat(r.getProvinceCode()).isEqualTo("12");
		assertThat(r.getVillageCode()).isEqualTo("12080705");
	}

	@Test
	@DisplayName("a repeated village name resolves under its own commune")
	void hierarchyDecides() {
		gazetteer();
		// "Prey Khla" is not unique nationally; only the commune already agreed
		// is searched, so it cannot bind to another province's village.
		assertThat(resolver.resolve(CARD).getVillageCode()).isEqualTo("12080705");
	}

	@Test
	@DisplayName("empty and null input are handled")
	void emptyInput() {
		when(repo.findByLevelOrderByNameEnAsc(eq(PdlDictionaryService.PROVINCE))).thenReturn(List.of());
		assertThat(resolver.resolve(null).isEmpty()).isTrue();
		assertThat(resolver.resolve("   ").isEmpty()).isTrue();
	}
}
