package com.ezetik.kjeypapa.pdl.payload;

import lombok.Data;

/**
 * What a customer sends to raise a data request.
 *
 * <p>Only what the customer legitimately controls. Status, the decision and who
 * made it are set server-side — accepting them here would let a caller file a
 * request already marked COMPLETED by somebody else.
 */
@Data
public class PdlDataRequestPayload {

	/** ACCESS, CORRECTION, DELETION or WITHDRAW_CONSENT. */
	private String type;

	private String details;

	/** Optional: the application this concerns. */
	private Integer relatedLoanId;
}
