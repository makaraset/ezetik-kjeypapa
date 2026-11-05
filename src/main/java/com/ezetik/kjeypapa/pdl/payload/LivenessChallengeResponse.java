package com.ezetik.kjeypapa.pdl.payload;

import java.util.List;

/**
 * A single-use challenge. {@code challengeId} is the only credential the submit
 * needs, so it is never stored by us (only its hash) and never logged.
 */
public record LivenessChallengeResponse(String challengeId, List<String> actions, long expiresInSeconds,
		int attemptsLeft) {

	@Override
	public String toString() {
		return "LivenessChallengeResponse[challengeId=<redacted>, actions=" + actions + ", expiresInSeconds="
				+ expiresInSeconds + ", attemptsLeft=" + attemptsLeft + "]";
	}
}
