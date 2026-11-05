package com.ezetik.kjeypapa.pdl.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.camdigi.VerifiedInfo;

/**
 * CamDigi's {@code userInfo}, built from the identity the customer confirmed.
 *
 * <p>This is the <b>only</b> place SBF's date and gender formats live. Their
 * swagger types every field as a bare string, and {@link NidOcrService} throws
 * away the raw format their OCR returns, so nothing in our repository records
 * it. Until the Stage 0 probe (or SBF) settles it, the two constants below
 * send our own stored form. Change them here and nowhere else.
 */
public final class CamDigiUserInfoMapper {

	private CamDigiUserInfoMapper() {
	}

	/** PLACEHOLDER until SBF confirms: our stored dd/MM/yyyy. */
	static final DateTimeFormatter DATE_OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** PLACEHOLDER until SBF confirms: our stored M / F. */
	static String gender(String stored) {
		return trim(stored);
	}

	public static VerifiedInfo from(PdlPersonalInfo p) {
		return new VerifiedInfo(date(p.getDateOfBirth()), date(p.getIdExpiryDate()), trim(p.getLatinFirstName()),
				trim(p.getKhmerFirstName()), gender(p.getGender()), trim(p.getIdNo()), date(p.getIdIssuedDate()),
				trim(p.getLatinFamilyName()), trim(p.getKhmerFamilyName()));
	}

	/**
	 * The identity fields this row lacks for a CamDigi check; empty means it
	 * is complete. Issue and expiry dates are optional on our side, so they
	 * are sent blank rather than refused.
	 */
	public static List<String> missing(PdlPersonalInfo p) {
		List<String> m = new ArrayList<>();
		if (p == null) {
			m.add("personal information");
			return m;
		}
		if (trim(p.getIdNo()).isEmpty())
			m.add("ID number");
		if (trim(p.getDateOfBirth()).isEmpty())
			m.add("date of birth");
		if (trim(p.getGender()).isEmpty())
			m.add("gender");
		if (trim(p.getLatinFamilyName()).isEmpty() || trim(p.getLatinFirstName()).isEmpty())
			m.add("Latin name");
		if (trim(p.getKhmerFamilyName()).isEmpty() || trim(p.getKhmerFirstName()).isEmpty())
			m.add("Khmer name");
		return m;
	}

	/** Re-formats a stored date; one that does not parse is sent as stored. */
	static String date(String stored) {
		String t = trim(stored);
		if (t.isEmpty())
			return "";
		LocalDate d = PersonalInfoValidator.parse(t);
		return d == null ? t : d.format(DATE_OUT);
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
