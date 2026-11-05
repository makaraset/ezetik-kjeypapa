package com.ezetik.kjeypapa.pdl.service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Whether Sambat's customer record for an ID number is this applicant.
 *
 * <p>Sambat asked for the full identity to be checked (2026-11-05), not the
 * ID number alone. The ID number, date of birth and sex must all agree, and so
 * must the name, in Khmer or in Latin script. One script is enough because
 * romanisation differs between their older records and the card; spacing, case
 * and punctuation never count as a difference.
 *
 * <p>The ID type is not compared: their record gives it as a number
 * ({@code regisType}) whose codes we have not been told, and a Sambat code is
 * never guessed.
 */
public final class SbfCustomerMatch {

	/** Field names a difference is reported by: our own property names. */
	static final String ID_NO = "idNo", DATE_OF_BIRTH = "dateOfBirth", GENDER = "gender", NAME = "name";

	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	/** Date forms seen beside ISO and ours, in case their record uses one. */
	private static final DateTimeFormatter[] OTHER_DATES = {
			DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
			DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT) };

	/** The applicant's side of the comparison. */
	public record Applicant(String idNo, String dateOfBirth, String gender, String khmerFamilyName,
			String khmerFirstName, String latinFamilyName, String latinFirstName) {

		public static Applicant of(PersonalInfoRequest r) {
			return new Applicant(r.getIdNo(), r.getDateOfBirth(), r.getGender(), r.getKhmerFamilyName(),
					r.getKhmerFirstName(), r.getLatinFamilyName(), r.getLatinFirstName());
		}

		public static Applicant of(PdlPersonalInfo p) {
			return new Applicant(p.getIdNo(), p.getDateOfBirth(), p.getGender(), p.getKhmerFamilyName(),
					p.getKhmerFirstName(), p.getLatinFamilyName(), p.getLatinFirstName());
		}
	}

	private SbfCustomerMatch() {
	}

	/**
	 * The fields that differ between the applicant and Sambat's record; empty
	 * when they are the same person. A field that is blank on either side, or
	 * that Sambat give in a form we cannot read, counts as different: only a
	 * full match may carry their CIF.
	 */
	static List<String> differences(Applicant a, JsonNode rec) {
		List<String> out = new ArrayList<>();
		String ours = idNumber(a.idNo());
		if (ours.isEmpty() || !ours.equals(idNumber(text(rec, "idNo"))))
			out.add(ID_NO);
		LocalDate born = PersonalInfoValidator.parse(a.dateOfBirth());
		if (born == null || !born.equals(date(rec.get("dob"))))
			out.add(DATE_OF_BIRTH);
		String sex = PersonalInfoValidator.canonGender(a.gender());
		if (sex == null || !sex.equals(sex(text(rec, "sex"))))
			out.add(GENDER);
		if (!sameName(a.khmerFamilyName(), a.khmerFirstName(), text(rec, "familyNameKh"), text(rec, "firstNameKh"),
				text(rec, "khName"))
				&& !sameName(a.latinFamilyName(), a.latinFirstName(), text(rec, "lname"), text(rec, "fname"),
						text(rec, "name")))
			out.add(NAME);
		return out;
	}

	/** An ID number without spacing or dashes, upper case. */
	static String idNumber(String s) {
		return s == null ? "" : s.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	/**
	 * Their date of birth: an ISO date (their swagger's {@code format: date}),
	 * a date-time, one of our own forms, epoch milliseconds or {@code [y, m, d]}.
	 */
	static LocalDate date(JsonNode n) {
		if (n == null || n.isNull())
			return null;
		try {
			if (n.isArray() && n.size() >= 3)
				return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
			if (n.isNumber())
				return Instant.ofEpochMilli(n.asLong()).atZone(KH).toLocalDate();
		} catch (Exception e) {
			return null;
		}
		String t = n.asText().trim();
		int cut = t.indexOf('T') > 0 ? t.indexOf('T') : t.indexOf(' ');
		if (cut > 0)
			t = t.substring(0, cut);
		LocalDate d = PersonalInfoValidator.parse(t);
		for (int i = 0; d == null && i < OTHER_DATES.length; i++) {
			try {
				d = LocalDate.parse(t, OTHER_DATES[i]);
			} catch (Exception ignore) {
			}
		}
		return d;
	}

	/** M or F, from a letter, a word in English or a word in Khmer. */
	static String sex(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		if (t.equals("ប្រុស"))
			return "M";
		if (t.equals("ស្រី"))
			return "F";
		return PersonalInfoValidator.canonGender(t);
	}

	/**
	 * Our family and first name against theirs, given either as two fields or
	 * as one full name in either order.
	 */
	static boolean sameName(String family, String first, String theirFamily, String theirFirst, String theirFull) {
		String ours = fold(family) + fold(first);
		if (ours.isEmpty())
			return false;
		String full = fold(theirFull);
		return ours.equals(fold(theirFamily) + fold(theirFirst)) || ours.equals(full)
				|| (fold(first) + fold(family)).equals(full);
	}

	/** A name with case, spacing, zero-width marks and punctuation folded away. */
	static String fold(String s) {
		if (s == null)
			return "";
		return Normalizer.normalize(s, Normalizer.Form.NFC)
				.replaceAll("[\\s\\p{Punct}\\u200B-\\u200D\\uFEFF\\u2019]", "")
				.toLowerCase(Locale.ROOT);
	}

	private static String text(JsonNode rec, String field) {
		JsonNode n = rec.get(field);
		return n == null || n.isNull() ? null : n.asText();
	}
}
