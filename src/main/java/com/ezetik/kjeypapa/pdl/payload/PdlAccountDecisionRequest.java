package com.ezetik.kjeypapa.pdl.payload;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An LPO's decision on one account request.
 *
 * <p>A named body rather than a map, so Swagger shows the fields. As a map it
 * showed {@code additionalProp1..3}, and that placeholder, sent as it stood,
 * was read as a rejection.
 *
 * <p>The example is blank on purpose (Makara, 2026-11-19). Sent as it stands
 * it approves and stores nothing else: no reason, no employer, and a
 * {@code confirmCif} of 0 confirms nothing. Swagger's usual sample would store
 * the word "string" as the reason and as the employer code.
 *
 * <p>No {@code decidedBy}: it comes from the security context in the
 * controller, as for {@link PdlDataDecisionPayload}.
 */
@Schema(description = "An LPO's decision on one account request.",
		example = "{\"approve\": true, \"reason\": \"\", \"employerCode\": \"\", \"confirmCif\": 0}")
public record PdlAccountDecisionRequest(

		// Boolean, not boolean: a missing value has to stay null so it can be
		// refused. As false it would be a rejection.
		@Schema(description = "true approves the request, false rejects it. Required: there is no default.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		Boolean approve,

		@Schema(description = "A note kept with the decision, usually why a request was rejected. "
				+ "LPOs see it; the customer does not. Optional.")
		String reason,

		@Schema(description = "Sambat's code for the customer's employer: the code of a row from "
				+ "GET /api/v1/pdl/admin/dictionary/list?name=EMPLOYER. Stored on approval and sent to Sambat "
				+ "with the loan application; ignored on a rejection. Leave it out to approve without an employer.")
		String employerCode,

		@Schema(description = "The LPO's confirmation that a CIF is this customer's, for a request whose review "
				+ "shows sbfCustomer.outcome MISMATCH: send sbfCustomer.cif. It is checked against Sambat again and "
				+ "stored only if they still hold the customer's ID number under that CIF. A confirmed CIF cannot "
				+ "be changed. Leave it out, or send 0, to confirm nothing; ignored on a rejection.")
		Long confirmCif) {
}
