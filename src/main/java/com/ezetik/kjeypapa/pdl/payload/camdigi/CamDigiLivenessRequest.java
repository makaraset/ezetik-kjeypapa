package com.ezetik.kjeypapa.pdl.payload.camdigi;

import java.util.List;

/**
 * The body of SBF's {@code POST /verified-info-idimage-liveness}, named exactly
 * as their swagger names it ({@code UserInfoLivenessRequest}, tag 18-CamDigi).
 *
 * <p>The backend builds it from the stored account request: {@code idImage} is
 * the NID front the customer submitted, {@code userInfo} the identity they
 * confirmed. The app supplies only {@code liveness}. So CamDigi checks exactly
 * the identity the LPO will approve, and the endpoint cannot be used to test
 * someone else's.
 */
public record CamDigiLivenessRequest(List<String> actions, String doneBy, String idImage, String liveness,
		VerifiedInfo userInfo) {

	/** Sizes only: the default would print an ID card image and a face capture into a log. */
	@Override
	public String toString() {
		return "CamDigiLivenessRequest[actions=" + actions + ", doneBy=" + doneBy + ", idImage="
				+ (idImage == null ? 0 : idImage.length()) + " chars, liveness="
				+ (liveness == null ? 0 : liveness.length()) + " chars]";
	}
}
