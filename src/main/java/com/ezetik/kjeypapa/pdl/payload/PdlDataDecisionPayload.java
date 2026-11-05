package com.ezetik.kjeypapa.pdl.payload;

import lombok.Data;

/**
 * An operator's decision on a data request.
 *
 * <p>No {@code decidedBy}: it comes from the security context in the
 * controller. A client-supplied actor on an audit field is a client-supplied
 * audit trail.
 */
@Data
public class PdlDataDecisionPayload {

	/** IN_PROGRESS, COMPLETED or REJECTED. */
	private String status;

	/** What was done, or why it was refused. */
	private String reason;
}
