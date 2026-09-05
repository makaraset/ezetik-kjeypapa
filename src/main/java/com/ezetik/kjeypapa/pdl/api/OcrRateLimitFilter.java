package com.ezetik.kjeypapa.pdl.api;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-client cap on the anonymous NID OCR endpoint.
 *
 * <p>{@code POST /api/v1/pdl/ocr-nid} has to be reachable before an account
 * exists, and each call costs a Sambat OCR round trip. Without a cap it is a
 * free relay to their service and a KYC-extraction oracle. A signup needs one
 * or two calls; this allows a handful per minute per client and then answers
 * 429. In-memory and per-instance, which is enough for one backend node.
 */
@Component
public class OcrRateLimitFilter extends OncePerRequestFilter {

	private static final String PATH = "/api/v1/pdl/ocr-nid";

	@Value("${pdl.ocr.rate-limit.per-minute:6}")
	private int perMinute;

	/**
	 * Peers whose X-Forwarded-For we believe (the reverse proxy in front of
	 * this app). Empty means: trust nobody's header and key on the socket
	 * address. The first version honoured any client-supplied header, which a
	 * rotating value defeated in twelve requests.
	 */
	@Value("${pdl.ocr.rate-limit.trusted-proxies:}")
	private String trustedProxies;

	private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !PATH.equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String client = clientKey(req);
		long minute = System.currentTimeMillis() / 60_000L;
		long[] b = buckets.computeIfAbsent(client, k -> new long[] { minute, 0 });
		boolean over;
		synchronized (b) {
			if (b[0] != minute) {
				b[0] = minute;
				b[1] = 0;
			}
			over = ++b[1] > perMinute;
		}
		if (buckets.size() > 10_000) // bound memory by dropping only STALE windows,
			buckets.entrySet().removeIf(e -> e.getValue()[0] != minute); // never live counters
		if (over) {
			res.setStatus(429);
			res.setContentType("application/json");
			res.getWriter().write("{\"type\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Please wait a minute.\",\"data\":null}");
			return;
		}
		chain.doFilter(req, res);
	}

	private String clientKey(HttpServletRequest req) {
		String peer = req.getRemoteAddr();
		if (trustedProxies == null || trustedProxies.isBlank())
			return peer;
		boolean trusted = java.util.Arrays.stream(trustedProxies.split(",")).map(String::trim)
				.anyMatch(p -> !p.isEmpty() && p.equals(peer));
		String fwd = req.getHeader("X-Forwarded-For");
		if (!trusted || fwd == null || fwd.isBlank())
			return peer;
		// The right-most entry is the one the trusted proxy itself appended.
		String[] hops = fwd.split(",");
		return hops[hops.length - 1].trim();
	}
}
