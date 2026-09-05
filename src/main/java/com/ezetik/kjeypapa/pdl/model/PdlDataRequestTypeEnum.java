package com.ezetik.kjeypapa.pdl.model;

/**
 * What a customer is asking us to do with their data.
 *
 * <p>These are the rights the CBC consent text itself grants, under the NBC
 * Prakas on Credit Reporting the wording cites — Article 9 for correction and
 * deletion, Article 25 for the borrower's undertaking. A consent that promises
 * rights the product gives no way to exercise is a promise on paper only.
 *
 * <p><b>Every value is declared now, deliberately.</b> {@code ddl-auto=update}
 * generates a check constraint from this enum on first run and will never widen
 * it afterwards — the {@code pdl_payday_loan_status_check} trap in migration
 * 002. Adding a value later means a hand-written migration.
 */
public enum PdlDataRequestTypeEnum {

	/** "Show me what you hold about me." */
	ACCESS,

	/** "This is wrong, fix it." */
	CORRECTION,

	/** "Delete it." Bounded by what we are required to retain. */
	DELETION,

	/**
	 * "I withdraw my consent."
	 *
	 * <p>A request, not an automatic revocation. A credit-bureau enquiry already
	 * made cannot be un-made, and what withdrawal means for an application in
	 * flight or a disbursed loan is Sambat's to define. Recorded, routed to a
	 * human, and answered — never silently actioned.
	 */
	WITHDRAW_CONSENT
}
