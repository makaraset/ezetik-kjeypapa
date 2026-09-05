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
	 * <p><b>Not offered to customers</b> — see
	 * {@code PdlDataRequestService.OFFERED}. Sambat ruled (2026-09-05) that the
	 * CBC consent cannot be withdrawn, because the enquiry it authorises is made
	 * automatically as part of assessing the loan: by the time anyone could
	 * withdraw, the thing consented to has already happened, and there is
	 * nothing left to stop.
	 *
	 * <p>The value stays declared. {@code ddl-auto} wrote it into the column's
	 * check constraint on first run and will not widen that constraint later, so
	 * removing it here would only guarantee a migration if the ruling ever
	 * changes. Existing records also keep their meaning.
	 */
	WITHDRAW_CONSENT
}
