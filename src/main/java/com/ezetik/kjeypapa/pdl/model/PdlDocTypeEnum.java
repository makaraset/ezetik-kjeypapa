package com.ezetik.kjeypapa.pdl.model;

/**
 * Document types for a Payday Loan.
 *
 * The first five are the mandatory application attachments (BRS 2.3);
 * SIGNED_CONTRACT is the customer-signed e-contract sent on acceptance (BRS 2.7).
 */
public enum PdlDocTypeEnum {

	E_CBC_CONSENT, PROFILE_PHOTO, NID, EMPLOYMENT_CARD, BANK_STATEMENT, SIGNED_CONTRACT
}
