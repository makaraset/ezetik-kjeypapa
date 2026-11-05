package com.ezetik.kjeypapa.pdl.model;

/** Where one CamDigi verification attempt stands. */
public enum PdlIdentityVerificationStatusEnum {
	/** A challenge was issued; nothing has been submitted against it yet. */
	CHALLENGED,
	/** Claimed by a submit and sent to CamDigi; no outcome recorded yet. */
	IN_FLIGHT,
	/** CamDigi verified the identity. Final: no further attempt is issued. */
	PASSED,
	/** CamDigi answered, and did not verify. The customer may retake it. */
	FAILED,
	/** No usable answer: our outage, SBF's, or a reply we could not read. Not the customer's doing. */
	ERROR
}
