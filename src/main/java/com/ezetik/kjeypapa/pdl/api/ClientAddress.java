package com.ezetik.kjeypapa.pdl.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a request actually came from.
 *
 * <p>{@code X-Forwarded-For} is a client-supplied header. Honouring it from
 * anyone lets a caller write their own address, which is worthless in a consent
 * record and worse than useless in a rate limiter — the first version of
 * {@link OcrRateLimitFilter} trusted it and a rotating value defeated the cap in
 * twelve requests. So the header counts only when the socket peer is a proxy we
 * put there ourselves, and otherwise the socket address is the answer.
 *
 * <p>Shared rather than duplicated: the rate limiter and the consent record must
 * agree on what a client's address is, and two copies of this logic would drift.
 */
public final class ClientAddress {

	private ClientAddress() {
	}

	/**
	 * @param trustedProxies comma-separated peers whose forwarding header we
	 *                       believe; blank means trust nobody's header
	 */
	public static String of(HttpServletRequest req, String trustedProxies) {
		String peer = req.getRemoteAddr();
		if (trustedProxies == null || trustedProxies.isBlank())
			return peer;
		boolean trusted = java.util.Arrays.stream(trustedProxies.split(",")).map(String::trim)
				.anyMatch(p -> !p.isEmpty() && p.equals(peer));
		String fwd = req.getHeader("X-Forwarded-For");
		if (!trusted || fwd == null || fwd.isBlank())
			return peer;
		// The right-most entry is the one the trusted proxy itself appended;
		// everything to its left was supplied by whoever came before it.
		String[] hops = fwd.split(",");
		return hops[hops.length - 1].trim();
	}
}
