package com.ezetik.kjeypapa.pdl.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;

/**
 * Server-side rules for a KYC personal-info write.
 *
 * <p>Until 2026-08-28 the backend trusted whatever the app sent: no length on
 * the ID number, no parse of the dates, no check on gender. On our own test
 * data 6 of 7 stored ID numbers were not 9 digits. A malformed ID number does
 * not bounce — it goes verbatim into Sambat's CIF lookup, misses, and files the
 * customer as brand new with {@code custId 0}.
 *
 * <p>{@link #normalise} runs first and canonicalises what is merely untidy
 * (date format, gender spelling, stray whitespace); {@link #validate} then
 * rejects what is actually wrong. Both write paths call both.
 */
public final class PersonalInfoValidator {

	private PersonalInfoValidator() {
	}

	static final DateTimeFormatter APP = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	// STRICT, with "uuuu": the default SMART resolver quietly turns 31/02/1990
	// into 28/02/1990, so an impossible date on a KYC record would be stored as
	// a plausible one. (STRICT needs the proleptic year field, hence u not y.)
	private static final DateTimeFormatter[] ACCEPTED = {
			DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
			DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT) };
	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	/** Canonical forms: dd/MM/yyyy dates, M/F gender, trimmed, blank -> null. */
	public static void normalise(PersonalInfoRequest r) {
		if (r == null)
			return;
		r.setDateOfBirth(canonDate(r.getDateOfBirth()));
		r.setIdIssuedDate(canonDate(r.getIdIssuedDate()));
		r.setIdExpiryDate(canonDate(r.getIdExpiryDate()));
		r.setGender(canonGender(r.getGender()));
		r.setIdNo(trimToNull(r.getIdNo()) == null ? null : r.getIdNo().trim().toUpperCase(Locale.ROOT));
		r.setMobilePhone(trimToNull(r.getMobilePhone()) == null ? null
				: r.getMobilePhone().trim().replaceAll("[\\s-]", ""));
		r.setLatinFamilyName(trimToNull(r.getLatinFamilyName()));
		r.setLatinFirstName(trimToNull(r.getLatinFirstName()));
		r.setIdType(canonIdType(r.getIdType()));
	}

	/** Human-readable problems; empty means acceptable. */
	public static List<String> validate(PersonalInfoRequest r) {
		return validate(r, false);
	}

	/**
	 * @param identityLocked the row is verified and the identity fields are
	 *                       unchanged — the LPO already accepted them, so do
	 *                       not re-judge them. Without this, a verified customer
	 *                       whose legacy row predates the rules could neither
	 *                       save an address change (validation fails on the
	 *                       old ID number) nor fix the ID number (locked).
	 */
	public static List<String> validate(PersonalInfoRequest r, boolean identityLocked) {
		List<String> e = new ArrayList<>();
		if (r == null) {
			e.add("personal information is required");
			return e;
		}
		tooLong(e, "Khmer family name", r.getKhmerFamilyName(), 100);
		tooLong(e, "Khmer first name", r.getKhmerFirstName(), 100);
		tooLong(e, "Latin family name", r.getLatinFamilyName(), 100);
		tooLong(e, "Latin first name", r.getLatinFirstName(), 100);
		tooLong(e, "nationality", r.getNationality(), 50);
		tooLong(e, "marital status", r.getMaritalStatus(), 50);
		tooLong(e, "correspondence house/street", r.getCorrHouseStreetNo(), 200);
		tooLong(e, "permanent house/street", r.getPermHouseStreetNo(), 200);
		if (r.getMobilePhone() == null || !r.getMobilePhone().matches("0\\d{8,9}"))
			e.add("mobile phone must be a Cambodian number (0 followed by 8-9 digits)");
		if (identityLocked)
			return e;

		if (r.getLatinFamilyName() == null || r.getLatinFirstName() == null)
			e.add("Latin family and first name are required");

		if (r.getIdNo() == null) {
			e.add("ID number is required");
		} else if (isNationalId(r.getIdType())) {
			if (!r.getIdNo().matches("\\d{9}"))
				e.add("a National ID number must be exactly 9 digits");
		} else if (r.getIdNo().length() > 30) {
			e.add("ID number is too long");
		}

		LocalDate dob = parse(r.getDateOfBirth());
		if (r.getDateOfBirth() == null || dob == null) {
			e.add("date of birth must be a valid date (dd/MM/yyyy)");
		} else {
			int age = Period.between(dob, LocalDate.now(KH)).getYears();
			if (age < 18)
				e.add("applicant must be at least 18");
			else if (age > 100)
				e.add("date of birth is not plausible");
		}

		LocalDate issued = parse(r.getIdIssuedDate());
		LocalDate expiry = parse(r.getIdExpiryDate());
		if (r.getIdIssuedDate() != null && issued == null)
			e.add("ID issued date must be a valid date (dd/MM/yyyy)");
		if (r.getIdExpiryDate() != null && expiry == null)
			e.add("ID expiry date must be a valid date (dd/MM/yyyy)");
		if (issued != null && expiry != null && !issued.isBefore(expiry))
			e.add("ID issued date must be before its expiry date");
		if (expiry != null && expiry.isBefore(LocalDate.now(KH)))
			e.add("the ID document has expired");

		if (r.getGender() == null)
			e.add("gender must be M or F");
		return e;
	}

	private static void tooLong(List<String> e, String what, String v, int max) {
		if (v != null && v.length() > max)
			e.add(what + " is too long (max " + max + ")");
	}

	/** Same value after canonicalisation — so 1990-01-03 and 03/01/1990 agree. */
	public static boolean sameIdentityValue(String stored, String incoming) {
		String a = canonAny(stored), b = canonAny(incoming);
		return a.equalsIgnoreCase(b);
	}

	private static String canonAny(String s) {
		String t = trimToNull(s);
		if (t == null)
			return "";
		LocalDate d = parse(t);
		return d != null ? d.format(APP) : t.replaceAll("\\s+", " ");
	}

	/** Same fold LosApplicationMapper uses: "National ID Card" / "NID" / "National ID". */
	/** Stored as Sambat's idCode since 2026-08-29 ("N"); legacy labels still fold. */
	static boolean isNationalId(String idType) {
		String n = idType == null ? "" : idType.trim().toLowerCase(Locale.ROOT);
		if (n.isEmpty())
			return true; // the only ID we issue accounts on today
		return n.equals("n") || n.equals("nid") || n.replaceAll("\\s*card$", "").equals("national id");
	}

	static LocalDate parse(String s) {
		if (s == null)
			return null;
		for (DateTimeFormatter f : ACCEPTED) {
			try {
				return LocalDate.parse(s.trim(), f);
			} catch (Exception ignore) {
			}
		}
		return null;
	}

	static String canonDate(String s) {
		String t = trimToNull(s);
		if (t == null)
			return null;
		LocalDate d = parse(t);
		return d == null ? t : d.format(APP); // leave garbage for validate() to name
	}

	static String canonGender(String s) {
		String t = trimToNull(s);
		if (t == null)
			return null;
		char c = Character.toUpperCase(t.charAt(0));
		return (c == 'M' || c == 'F') ? String.valueOf(c) : null;
	}

	/** Canonical id type = Sambat's idCode: N national ID, P passport. */
	static String canonIdType(String s) {
		String t = trimToNull(s);
		if (t == null)
			return "N";
		if (isNationalId(t))
			return "N";
		String n = t.toLowerCase(Locale.ROOT);
		if (n.equals("p") || n.equals("passport"))
			return "P";
		return t.toUpperCase(Locale.ROOT); // any other Sambat idCode, verbatim
	}

	static String trimToNull(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
