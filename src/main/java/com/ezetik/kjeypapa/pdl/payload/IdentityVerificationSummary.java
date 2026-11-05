package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

/**
 * The CamDigi verification of one account request, as the LPO sees it.
 *
 * @param enabled  whether verification runs at all ({@code pdl.liveness.enabled})
 * @param required whether approval waits for a pass ({@code pdl.liveness.required-for-approval})
 * @param state    PASSED, or else the latest attempt's status, or NOT_DONE
 * @param attempts how many were submitted, ever
 * @param mock     the pass came from the mock, not CamDigi
 */
public record IdentityVerificationSummary(boolean enabled, boolean required, String state, int attempts,
		Instant lastAttemptAt, String actions, Integer sbfError, String sbfMessage, String errorKind,
		boolean mock) {
}
