package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.ezetik.kjeypapa.pdl.service.PersonalInfoValidator;

/** The rules a KYC write must pass — pinned against what our test data got wrong. */
class PersonalInfoValidatorTest {

	private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private PersonalInfoRequest good() {
		PersonalInfoRequest r = new PersonalInfoRequest();
		r.setLatinFamilyName("SET");
		r.setLatinFirstName("MAKARA");
		r.setGender("M");
		r.setDateOfBirth("03/01/1990");
		r.setIdType("National ID Card");
		r.setIdNo("110553867");
		r.setIdIssuedDate("17/03/2025");
		r.setIdExpiryDate("16/03/2035");
		r.setMobilePhone("010849001");
		return r;
	}

	private java.util.List<String> check(PersonalInfoRequest r) {
		PersonalInfoValidator.normalise(r);
		return PersonalInfoValidator.validate(r);
	}

	@Test
	@DisplayName("the real card passes")
	void realCardPasses() {
		assertThat(check(good())).isEmpty();
	}

	@Test
	@DisplayName("a National ID must be exactly 9 digits — 6 of 7 test rows were not")
	void nationalIdLength() {
		for (String bad : new String[] { "1122334", "12345678", "1105538670", "11055386A" }) {
			PersonalInfoRequest r = good();
			r.setIdNo(bad);
			assertThat(check(r)).anyMatch(m -> m.contains("9 digits"));
		}
		// "NID" and a blank type both mean the national card.
		PersonalInfoRequest nid = good();
		nid.setIdType("NID");
		nid.setIdNo("1234");
		assertThat(check(nid)).anyMatch(m -> m.contains("9 digits"));
	}

	@Test
	@DisplayName("a passport is free-form")
	void passportFreeForm() {
		PersonalInfoRequest r = good();
		r.setIdType("Passport");
		r.setIdNo("N1234567");
		assertThat(check(r)).isEmpty();
	}

	@Test
	@DisplayName("ISO dates are accepted and canonicalised to dd/MM/yyyy")
	void isoDateNormalised() {
		PersonalInfoRequest r = good();
		r.setDateOfBirth("1990-01-03");
		assertThat(check(r)).isEmpty();
		assertThat(r.getDateOfBirth()).isEqualTo("03/01/1990");
	}

	@Test
	@DisplayName("garbage dates are rejected, not stored")
	void garbageDates() {
		PersonalInfoRequest r = good();
		r.setDateOfBirth("31/02/1990");
		assertThat(check(r)).anyMatch(m -> m.contains("date of birth"));
	}

	@Test
	@DisplayName("under 18 and implausible ages are rejected")
	void ageBounds() {
		PersonalInfoRequest young = good();
		young.setDateOfBirth(LocalDate.now().minusYears(17).format(F));
		assertThat(check(young)).anyMatch(m -> m.contains("at least 18"));
		PersonalInfoRequest old = good();
		old.setDateOfBirth("01/01/1900");
		assertThat(check(old)).anyMatch(m -> m.contains("not plausible"));
	}

	@Test
	@DisplayName("issued must precede expiry, and the document must not have expired")
	void documentDates() {
		PersonalInfoRequest same = good();
		same.setIdIssuedDate("16/03/2035");
		assertThat(check(same)).anyMatch(m -> m.contains("before its expiry"));
		PersonalInfoRequest expired = good();
		expired.setIdIssuedDate("01/01/2010");
		expired.setIdExpiryDate("01/01/2020");
		assertThat(check(expired)).anyMatch(m -> m.contains("expired"));
	}

	@Test
	@DisplayName("gender folds Male/Female to M/F and rejects anything else")
	void gender() {
		PersonalInfoRequest r = good();
		r.setGender("female");
		assertThat(check(r)).isEmpty();
		assertThat(r.getGender()).isEqualTo("F");
		r.setGender("X");
		assertThat(check(r)).anyMatch(m -> m.contains("gender"));
	}

	@Test
	@DisplayName("phone must be a Cambodian number; spaces and dashes are tolerated")
	void phone() {
		PersonalInfoRequest r = good();
		r.setMobilePhone("010 849-001");
		assertThat(check(r)).isEmpty();
		assertThat(r.getMobilePhone()).isEqualTo("010849001");
		r.setMobilePhone("+85510849001");
		assertThat(check(r)).anyMatch(m -> m.contains("mobile phone"));
	}

	@Test
	@DisplayName("optional document dates may be blank; blanks become null")
	void blanksBecomeNull() {
		PersonalInfoRequest r = good();
		r.setIdIssuedDate("  ");
		r.setIdExpiryDate("");
		assertThat(check(r)).isEmpty();
		assertThat(r.getIdIssuedDate()).isNull();
		assertThat(r.getIdExpiryDate()).isNull();
	}

	@Test
	@DisplayName("a null request is one clear error, not an NPE")
	void nullRequest() {
		assertThat(PersonalInfoValidator.validate(null)).hasSize(1);
	}
}
