package com.ezetik.kjeypapa.pdl.model;

/**
 * Where a data request has got to.
 *
 * <p>All four declared up front for the same reason as
 * {@link PdlDataRequestTypeEnum}: {@code ddl-auto} writes the check constraint
 * once and never widens it.
 */
public enum PdlDataRequestStatusEnum {

	/** Received, nobody has picked it up. */
	PENDING,

	/**
	 * Someone is working on it. Distinct from PENDING because these requests
	 * carry a response deadline, and "acknowledged" and "untouched" are not the
	 * same thing to answer for.
	 */
	IN_PROGRESS,

	/** Done, with what was done recorded in the reason. */
	COMPLETED,

	/** Declined, with why recorded — a refusal without a reason is not an answer. */
	REJECTED
}
