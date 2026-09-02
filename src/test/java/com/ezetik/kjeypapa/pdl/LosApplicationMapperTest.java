package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlCodeList;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationRequest;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlCodeListRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.service.LosApplicationMapper;
import com.ezetik.kjeypapa.pdl.service.LosDocumentAssembler;
import com.ezetik.kjeypapa.pdl.service.LosSubmitConfig;
import com.ezetik.kjeypapa.security.model.User;

/**
 * Pins the fields Sambat answered on 2026-08-28: marital status takes their
 * {@code cbcCode}, nationality takes {@code KHM}, and the {@code LocationId}
 * fields are Google Maps coordinates we do not collect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LosApplicationMapperTest {

	@Mock PdlPersonalInfoRepository personalRepo;
	@Mock PdlEmploymentInfoRepository employmentRepo;
	@Mock PdlBankInfoRepository bankRepo;
	@Mock PdlCodeListRepository codeRepo;
	@Mock LosDocumentAssembler docs;
	@Mock LosSubmitConfig config;

	@InjectMocks LosApplicationMapper mapper;

	private PaydayLoan loan;
	private PdlPersonalInfo pi;

	@BeforeEach
	void setUp() {
		User u = new User();
		u.setId(1);
		u.setUsername("010849001");
		loan = new PaydayLoan();
		loan.setId(1);
		loan.setUser(u);

		pi = new PdlPersonalInfo();
		pi.setMaritalStatus("Married");
		pi.setCorrProvinceCode("12");
		pi.setCorrDistrictCode("1208");
		pi.setCorrCommuneCode("120807");
		pi.setCorrVillageCode("12080705");
		pi.setPermProvinceCode("12");
		pi.setPermDistrictCode("1208");
		pi.setBirthProvinceCode("12");
		pi.setBirthDistrictCode("1208");

		when(personalRepo.findByUser(1)).thenReturn(List.of(pi));
		when(employmentRepo.findByUser(1)).thenReturn(List.of());
		when(bankRepo.findByUser(1)).thenReturn(List.of());
		// nullable(): this fixture has no file refs, and anyString() does not
		// match null.
		when(docs.build(nullable(String.class), any(), anyInt()))
				.thenReturn(LosDocumentAssembler.Doc.EMPTY);
		when(codeRepo.findFirstByListNameAndNameEnIgnoreCase(eq("MARITAL_STATUS"), eq("Married")))
				.thenReturn(new PdlCodeList("MARITAL_STATUS", "M", "Married", ""));

		when(codeRepo.findByListNameOrderByNameEnAsc("ID_TYPE")).thenReturn(List.of(
				new PdlCodeList("ID_TYPE", "N", "National ID", ""),
				new PdlCodeList("ID_TYPE", "P", "Passport", "")));

		// The config gate is exercised by its own tests; here it is simply
		// satisfied so the mapping itself is what is under test.
		when(config.getHidCurrentUserId()).thenReturn("541");
		when(config.getLoanTerm()).thenReturn("1");
	}

	private NewApplicationRequest map() {
		return mapper.toParam(loan).getNewAppRequest();
	}

	@Test
	@DisplayName("the four geo levels carry Sambat's NCDD codes, not names")
	void geoCodes() {
		NewApplicationRequest r = map();
		assertThat(r.getCustP_CAddCBProvinceCity()).isEqualTo("12");
		assertThat(r.getCustP_CAddCBDistrict()).isEqualTo("1208");
		assertThat(r.getCustP_CAddCBCommune()).isEqualTo("120807");
		assertThat(r.getCustP_CAddCBVillage()).isEqualTo("12080705");
		assertThat(r.getCustP_POBCBProvinceCity()).isEqualTo("12");
	}

	@Test
	@DisplayName("single-digit provinces are zero-padded to the NCDD width Sambat expects")
	void geoCodesZeroPadded() {
		// Sambat's dictionary returns Kampot as 7 and its districts as 701/702;
		// their loan payload sends 07 / 0702 / 070204. A Kampot address must go
		// out padded or it files against the wrong locality.
		pi.setCorrProvinceCode("7"); // Kampot
		pi.setCorrDistrictCode("702"); // Banteay Meas -> 0702
		pi.setCorrCommuneCode("70204"); // -> 070204
		pi.setCorrVillageCode("7020401"); // -> 07020401
		NewApplicationRequest r = map();
		assertThat(r.getCustP_CAddCBProvinceCity()).isEqualTo("07");
		assertThat(r.getCustP_CAddCBDistrict()).isEqualTo("0702");
		assertThat(r.getCustP_CAddCBCommune()).isEqualTo("070204");
		assertThat(r.getCustP_CAddCBVillage()).isEqualTo("07020401");
	}

	@Test
	@DisplayName("monthly income row is typed S (salary), matching Sambat's reference")
	void incomeTypeSalary() {
		var ei = new com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo();
		ei.setMonthlyIncome(500.0);
		ei.setCurrency("USD");
		when(employmentRepo.findByUser(1)).thenReturn(java.util.List.of(ei));
		var incomes = map().getMonthlyIncomes();
		assertThat(incomes).hasSize(1);
		assertThat(incomes.get(0).getIncomeType()).isEqualTo("S");
		assertThat(incomes.get(0).getIncomeAmount()).isEqualTo(500.0);
	}

	@Test
	@DisplayName("nationality and every country slot are KHM")
	void nationality() {
		NewApplicationRequest r = map();
		assertThat(r.getCustP_Nationality()).isEqualTo("KHM");
		assertThat(r.getCustP_CAddCBCountry()).isEqualTo("KHM");
		assertThat(r.getCustP_PRAddCBCountry()).isEqualTo("KHM");
		assertThat(r.getCustP_POBCBCountry()).isEqualTo("KHM");
	}

	@Test
	@DisplayName("marital status is sent as their cbcCode, not our label")
	void maritalCbcCode() {
		assertThat(map().getCustP_CBMaritalStatus()).isEqualTo("M");
	}

	@Test
	@DisplayName("a label with no counterpart in their list sends blank, not a guess")
	void maritalUnmatched() {
		// Our option list offers "Other"; theirs has no such status. Mapping it
		// onto their "Unknown" would assert something about the customer that
		// they never told us.
		pi.setMaritalStatus("Other");
		when(codeRepo.findFirstByListNameAndNameEnIgnoreCase(eq("MARITAL_STATUS"), eq("Other")))
				.thenReturn(null);
		assertThat(map().getCustP_CBMaritalStatus()).isEmpty();
		assertThat(mapper.maritalCode(null)).isEmpty();
		assertThat(mapper.maritalCode("  ")).isEmpty();
	}

	@Test
	@DisplayName("LocationId stays empty — it is a map pin we never collect")
	void locationIdsEmpty() {
		NewApplicationRequest r = map();
		assertThat(r.getCustP_CAddLocationId()).isEmpty();
		assertThat(r.getCustP_PRAddLocationId()).isEmpty();
		assertThat(r.getCustP_EmpAddLocationId()).isEmpty();
	}

	@Test
	@DisplayName("a level we hold no code for is blank, so their MissingData names it")
	void missingCodeIsBlank() {
		pi.setCorrVillageCode(null);
		assertThat(map().getCustP_CAddCBVillage()).isEmpty();
	}

	@Test
	@DisplayName("permanent-address coincide reflects the stored names")
	void coincide() {
		pi.setCorrProvince("Phnom Penh");
		pi.setPermProvince("Phnom Penh");
		pi.setCorrDistrict("Saensokh");
		pi.setPermDistrict("Saensokh");
		assertThat(map().isCustP_PRAddCBCoincide()).isTrue();
	}

	@Test
	@DisplayName("ID type resolves our three stored spellings to their idCode")
	void idType() {
		// "National ID Card" and "NID" are both what Sambat calls "National ID";
		// the same document, so folding them is not a guess.
		pi.setIdType("National ID Card");
		assertThat(map().getCustP_CBIdType()).isEqualTo("N");
		pi.setIdType("NID");
		assertThat(map().getCustP_CBIdType()).isEqualTo("N");
		pi.setIdType("Passport");
		assertThat(map().getCustP_CBIdType()).isEqualTo("P");
	}

	@Test
	@DisplayName("an ID type they do not list sends blank")
	void idTypeUnknown() {
		pi.setIdType("Monk Card");
		assertThat(map().getCustP_CBIdType()).isEmpty();
		assertThat(mapper.idTypeCode(null)).isEmpty();
	}

	@Test
	@DisplayName("screen 7's employment type maps to their status id: 1 self / 2 employee / 3 other")
	void employmentStatus() {
		var ei = new com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo();
		ei.setEmploymentType("Employee");
		when(employmentRepo.findByUser(1)).thenReturn(java.util.List.of(ei));
		assertThat(map().getCustP_CBEmploymentStatus()).isEqualTo("2");
		assertThat(LosApplicationMapper.employmentStatusCode("Self-employed")).isEqualTo("1");
		assertThat(LosApplicationMapper.employmentStatusCode("self employed")).isEqualTo("1");
		assertThat(LosApplicationMapper.employmentStatusCode("Freelancer")).isEqualTo("3");
		// Blank stays blank: an absent answer is not "other".
		assertThat(LosApplicationMapper.employmentStatusCode(null)).isEmpty();
		assertThat(LosApplicationMapper.employmentStatusCode(" ")).isEmpty();
	}

	@Test
	@DisplayName("EntityFactoryId carries the LPO-assigned employer code, blank until assigned")
	void entityFactoryId() {
		var ei = new com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo();
		when(employmentRepo.findByUser(1)).thenReturn(java.util.List.of(ei));
		assertThat(map().getCustP_EntityFactoryId()).isEmpty();
		ei.setEmployerCode("G30020");
		assertThat(map().getCustP_EntityFactoryId()).isEqualTo("G30020");
	}

	@Test
	@DisplayName("unknown place-of-birth levels are zero-filled to NCDD width, never blank")
	void pobZeroFill() {
		// Sambat (2026-08-31): unknown POB is zero-filled. Commune and village
		// are never captured, so they are always zeros; blank province/district
		// zero-fill too, while a held code still goes out as itself.
		NewApplicationRequest r = map();
		assertThat(r.getCustP_POBCBProvinceCity()).isEqualTo("12");
		assertThat(r.getCustP_POBCBCommune()).isEqualTo("000000");
		assertThat(r.getCustP_POBCBVillage()).isEqualTo("00000000");
		pi.setBirthProvinceCode(null);
		pi.setBirthDistrictCode("");
		r = map();
		assertThat(r.getCustP_POBCBProvinceCity()).isEqualTo("00");
		assertThat(r.getCustP_POBCBDistrict()).isEqualTo("0000");
	}

	@Test
	@DisplayName("payment channel name comes from config — sheet row 12 for now")
	void paymentChannelName() {
		when(config.getPaymentChannelName()).thenReturn("12");
		assertThat(map().getPC_PaymentChannelName()).isEqualTo("12");
	}
}
