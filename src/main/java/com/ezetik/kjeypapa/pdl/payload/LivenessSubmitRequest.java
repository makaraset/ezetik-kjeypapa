package com.ezetik.kjeypapa.pdl.payload;

/** The customer's liveness capture, base64, answering one challenge. */
public record LivenessSubmitRequest(String challengeId, String liveness, String mimeType) {

	@Override
	public String toString() {
		return "LivenessSubmitRequest[challengeId=<redacted>, liveness="
				+ (liveness == null ? 0 : liveness.length()) + " chars, mimeType=" + mimeType + "]";
	}
}
