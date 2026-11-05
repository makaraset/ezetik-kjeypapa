package com.ezetik.kjeypapa.pdl.payload;

/**
 * Asks for a CamDigi challenge. The credentials are the account request's own:
 * a PENDING customer cannot log in, so the password is checked here instead.
 */
public record LivenessChallengeRequest(String username, String password) {

	@Override
	public String toString() {
		return "LivenessChallengeRequest[username=" + username + ", password=<redacted>]";
	}
}
