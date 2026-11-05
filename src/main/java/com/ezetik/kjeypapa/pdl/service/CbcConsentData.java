package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

/**
 * Everything the CBC consent form prints, frozen at the moment of consent.
 *
 * <p>The renderer used to take the loan and the live {@link PdlPersonalInfo}
 * and fall back to config and {@code now()} whenever a field was not yet
 * stamped. Two problems came from that. The copy filed with Sambat was rendered
 * <em>before</em> the stamp, so it took those fallbacks and was computed
 * independently of the record kept beside it. And because the identity came
 * from a live row, a customer editing their name afterwards silently changed
 * the document shown for an application already filed.
 *
 * <p>Passing this record instead removes both: there is nothing to fall back
 * to, and every value is fixed when the consent happens.
 *
 * @param loanId            the application number, printed on the form
 * @param consentDate       when the customer consented
 * @param textVersion       which wording they agreed to
 * @param formReference     Sambat's own form reference, printed as the footer
 * @param idNo              national ID, printed
 * @param customerNameKm    name in Khmer, printed
 * @param customerNameLatin name in Latin script, printed
 */
public record CbcConsentData(
		int loanId,
		Instant consentDate,
		String textVersion,
		String formReference,
		String idNo,
		String customerNameKm,
		String customerNameLatin) {

	public CbcConsentData {
		// A form without a date or a wording version cannot be evidence of
		// anything. Failing here is the point: it is what stops the renderer
		// being called before the consent exists.
		if (consentDate == null)
			throw new LosSubmitException("LOS_CONSENT_INCOMPLETE",
					"Cannot render a consent form before the consent is recorded.");
		if (textVersion == null || textVersion.isBlank())
			throw new LosSubmitException("LOS_CONSENT_INCOMPLETE",
					"Cannot render a consent form without a wording version.");
	}

	/**
	 * Reads a consent that has already been stamped on the loan.
	 *
	 * @throws LosSubmitException when the loan carries no consent yet — callers
	 *         that need a document before filing must build the record first
	 *         and render from it, not ask the loan.
	 */
	public static CbcConsentData of(PaydayLoan loan, PdlPersonalInfo pi, String formReference) {
		return new CbcConsentData(
				loan.getId() == null ? 0 : loan.getId(),
				loan.getCbcConsentDate(),
				loan.getCbcConsentTextVersion(),
				formReference,
				pi == null ? "" : s(pi.getIdNo()),
				pi == null ? "" : (s(pi.getKhmerFamilyName()) + " " + s(pi.getKhmerFirstName())).trim(),
				pi == null ? "" : (s(pi.getLatinFamilyName()) + " " + s(pi.getLatinFirstName())).trim());
	}

	private static String s(String v) {
		return v == null ? "" : v;
	}
}
