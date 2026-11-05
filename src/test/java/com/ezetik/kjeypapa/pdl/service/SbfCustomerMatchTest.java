package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Sambat's "check full" (2026-11-05): when their record for an ID number is this applicant. */
class SbfCustomerMatchTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final SbfCustomerMatch.Applicant DARA = new SbfCustomerMatch.Applicant("012345678",
			"03/01/1990", "M", "សុខ", "តារា", "SOK", "DARA");

	/** Their record for the same person, as their swagger describes it. */
	private static ObjectNode record() {
		ObjectNode r = JSON.createObjectNode();
		r.put("custKeyNum", 70);
		r.put("idNo", "012345678");
		r.put("dob", "1990-01-03");
		r.put("sex", "M");
		r.put("familyNameKh", "សុខ");
		r.put("firstNameKh", "តារា");
		r.put("lname", "SOK");
		r.put("fname", "DARA");
		return r;
	}

	@Test
	@DisplayName("the same person differs in nothing")
	void samePerson() {
		assertThat(SbfCustomerMatch.differences(DARA, record())).isEmpty();
	}

	@Test
	@DisplayName("each field that disagrees is named")
	void eachDifferenceIsNamed() {
		assertThat(SbfCustomerMatch.differences(DARA, record().put("idNo", "012345679")))
				.containsExactly(SbfCustomerMatch.ID_NO);
		assertThat(SbfCustomerMatch.differences(DARA, record().put("dob", "1990-03-01")))
				.containsExactly(SbfCustomerMatch.DATE_OF_BIRTH);
		assertThat(SbfCustomerMatch.differences(DARA, record().put("sex", "F")))
				.containsExactly(SbfCustomerMatch.GENDER);
		assertThat(SbfCustomerMatch.differences(DARA,
				record().put("familyNameKh", "ចាន់").put("lname", "CHAN")))
				.containsExactly(SbfCustomerMatch.NAME);
	}

	@Test
	@DisplayName("the name must agree in one script; romanisation may differ")
	void oneScriptIsEnough() {
		assertThat(SbfCustomerMatch.differences(DARA, record().put("lname", "SOKH"))).isEmpty();
		assertThat(SbfCustomerMatch.differences(DARA, record().putNull("familyNameKh").putNull("firstNameKh")))
				.isEmpty();
	}

	@Test
	@DisplayName("case, spacing, zero-width marks and a full name in either order are not differences")
	void formsOfTheSameName() {
		assertThat(SbfCustomerMatch.differences(DARA, record().put("lname", "Sok ").put("fname", " dara")))
				.isEmpty();
		ObjectNode fullOnly = record();
		fullOnly.remove(java.util.List.of("familyNameKh", "firstNameKh", "lname", "fname"));
		assertThat(SbfCustomerMatch.differences(DARA, fullOnly.deepCopy().put("name", "SOK DARA"))).isEmpty();
		assertThat(SbfCustomerMatch.differences(DARA, fullOnly.deepCopy().put("name", "DARA SOK"))).isEmpty();
		assertThat(SbfCustomerMatch.differences(DARA, fullOnly.deepCopy().put("khName", "សុខ​តារា")))
				.isEmpty();
	}

	@Test
	@DisplayName("their date of birth reads as ISO, a date-time, our form, epoch millis or [y, m, d]")
	void datesTheyMightSend() {
		LocalDate born = LocalDate.of(1990, 1, 3);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree("1990-01-03"))).isEqualTo(born);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree("1990-01-03T00:00:00.000+0000"))).isEqualTo(born);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree("1990-01-03 00:00:00"))).isEqualTo(born);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree("03/01/1990"))).isEqualTo(born);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree("1990/01/03"))).isEqualTo(born);
		long midnight = born.atStartOfDay(ZoneId.of("Asia/Phnom_Penh")).toInstant().toEpochMilli();
		assertThat(SbfCustomerMatch.date(JSON.valueToTree(midnight))).isEqualTo(born);
		long utcMidnight = born.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
		assertThat(SbfCustomerMatch.date(JSON.valueToTree(utcMidnight))).isEqualTo(born);
		assertThat(SbfCustomerMatch.date(JSON.valueToTree(new int[] { 1990, 1, 3 }))).isEqualTo(born);
	}

	@Test
	@DisplayName("sex reads from a letter, an English word or a Khmer word")
	void sexes() {
		assertThat(SbfCustomerMatch.sex("Male")).isEqualTo("M");
		assertThat(SbfCustomerMatch.sex("f")).isEqualTo("F");
		assertThat(SbfCustomerMatch.sex("ប្រុស")).isEqualTo("M");
		assertThat(SbfCustomerMatch.sex("ស្រី")).isEqualTo("F");
		assertThat(SbfCustomerMatch.sex("1")).isNull();
	}

	@Test
	@DisplayName("a blank or unreadable field is a difference: only a full match may carry their CIF")
	void blankIsNotAMatch() {
		ObjectNode noDob = record();
		noDob.remove("dob");
		assertThat(SbfCustomerMatch.differences(DARA, noDob)).containsExactly(SbfCustomerMatch.DATE_OF_BIRTH);
		assertThat(SbfCustomerMatch.differences(DARA, record().put("dob", "unknown")))
				.containsExactly(SbfCustomerMatch.DATE_OF_BIRTH);
		assertThat(SbfCustomerMatch.differences(DARA, record().put("sex", "")))
				.containsExactly(SbfCustomerMatch.GENDER);

		SbfCustomerMatch.Applicant noBirthDate = new SbfCustomerMatch.Applicant("012345678", null, "M", "សុខ",
				"តារា", "SOK", "DARA");
		assertThat(SbfCustomerMatch.differences(noBirthDate, record()))
				.containsExactly(SbfCustomerMatch.DATE_OF_BIRTH);
	}

	@Test
	@DisplayName("ID numbers compare without spacing or dashes")
	void idNumbers() {
		assertThat(SbfCustomerMatch.differences(DARA, record().put("idNo", "0123 456-78"))).isEmpty();
	}
}
