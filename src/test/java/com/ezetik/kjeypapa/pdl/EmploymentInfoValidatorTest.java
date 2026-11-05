package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;
import com.ezetik.kjeypapa.pdl.service.EmploymentInfoValidator;

/** The employment write rules, pinned against what SBF's testers hit (2026-10-27). */
class EmploymentInfoValidatorTest {

	private EmploymentInfoRequest good() {
		EmploymentInfoRequest r = new EmploymentInfoRequest();
		r.setEmploymentType("Employee");
		r.setEmployerName("CP CAMBODIA");
		r.setEmploymentStatus("Permanent / Confirmed");
		r.setEmploymentStartDate(Instant.parse("2023-04-30T17:00:00Z")); // 1 May 2023, Phnom Penh
		r.setMonthlyIncome(500.0);
		r.setCurrency("USD");
		r.setWorkProvinceCode("12");
		r.setWorkDistrictCode("1214");
		r.setWorkCommuneCode("121402");
		r.setWorkVillageCode("12140204");
		return r;
	}

	private List<String> check(EmploymentInfoRequest r) {
		EmploymentInfoValidator.normalise(r);
		return EmploymentInfoValidator.validate(r);
	}

	@Test
	@DisplayName("a complete salaried record passes, and gains its country")
	void completeRecordPasses() {
		EmploymentInfoRequest r = good();
		assertThat(check(r)).isEmpty();
		assertThat(r.getWorkCountry()).isEqualTo("Cambodia");
	}

	@Test
	@DisplayName("a blank status is refused — the dropdown showed a default it never saved")
	void statusRequired() {
		for (String blank : new String[] { null, "", "   " }) {
			EmploymentInfoRequest r = good();
			r.setEmploymentStatus(blank);
			assertThat(check(r)).containsExactly("employment status is required");
		}
	}

	@Test
	@DisplayName("a status outside the offered options is refused")
	void statusFromTheList() {
		EmploymentInfoRequest r = good();
		r.setEmploymentStatus("Confirmed");
		assertThat(check(r)).singleElement().asString().startsWith("employment status must be one of");
	}

	@Test
	@DisplayName("the status list is the app's list (lib/constants/pdl_options.dart) — change both together")
	void statusListMatchesTheApp() {
		assertThat(EmploymentInfoValidator.STATUSES).containsExactly("Permanent / Confirmed", "Probation",
				"Contract (fixed-term)", "Part-time", "Other");
	}

	@Test
	@DisplayName("the start date is required (Appendix 2 #45) and cannot be in the future")
	void startDate() {
		EmploymentInfoRequest none = good();
		none.setEmploymentStartDate(null);
		assertThat(check(none)).containsExactly("employment start date is required");

		EmploymentInfoRequest future = good();
		future.setEmploymentStartDate(Instant.now().plus(Duration.ofDays(30)));
		assertThat(check(future)).containsExactly("employment start date cannot be in the future");
	}

	@Test
	@DisplayName("every work-address level is required (Appendix 2 #24-28)")
	void workAddressRequired() {
		EmploymentInfoRequest r = good();
		r.setWorkVillageCode(null);
		assertThat(check(r)).containsExactly("work address is incomplete (village)");
	}

	@Test
	@DisplayName("payday is for salaried employees: Self-employed is refused, a blank type is Employee")
	void employeeOnly() {
		EmploymentInfoRequest self = good();
		self.setEmploymentType("Self-employed");
		assertThat(check(self)).singleElement().asString().contains("employment type must be Employee");

		EmploymentInfoRequest blank = good();
		blank.setEmploymentType(" ");
		assertThat(check(blank)).isEmpty();
		assertThat(blank.getEmploymentType()).isEqualTo("Employee");
	}

	@Test
	@DisplayName("every problem is reported at once, not one per attempt")
	void reportsEverything() {
		EmploymentInfoRequest r = good();
		r.setEmploymentStatus(null);
		r.setEmploymentStartDate(null);
		r.setWorkProvinceCode(null);
		assertThat(check(r)).hasSize(3);
	}

	@Test
	@DisplayName("a null request is one clear error, not an NPE")
	void nullRequest() {
		assertThat(EmploymentInfoValidator.validate(null)).hasSize(1);
	}
}
