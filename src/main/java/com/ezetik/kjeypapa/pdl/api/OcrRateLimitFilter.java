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
 * Per-client caps on the anonymous endpoints that relay to a Sambat service.
 *
 * <p>{@code POST /api/v1/pdl/ocr-nid} has to be reachable before an account
 * exists, and each call costs a Sambat OCR round trip. Without a cap it is a
 * free relay to their service and a KYC-extraction oracle. A signup needs one
 * or two calls; this allows a handful per minute per client and then answers
 * 429.
 *
 * <p>The two CamDigi liveness endpoints (2026-10-27) are anonymous for the same
 * reason — a pending customer cannot log in — and each submit is a likely
 * charged verification, so they are capped here too. Every path has its own
 * bucket, so retrying a face check never uses up a customer's OCR allowance.
 *
 * <p>In-memory and per-instance, which is enough for one backend node.
 */
@Component
public class OcrRateLimitFilter extends OncePerRequestFilter {

	static final String OCR = "/api/v1/pdl/ocr-nid";
	static final String LIVENESS_CHALLENGE = "/api/v1/pdl/account-request/liveness/challenge";
	static final String LIVENESS_SUBMIT = "/api/v1/pdl/account-request/liveness";

	@Value("${pdl.ocr.rate-limit.per-minute:6}")
	private int perMinute;

	@Value("${pdl.liveness.rate-limit.per-minute:6}")
	private int livenessPerMinute;

	/**
	 * Peers whose X-Forwarded-For we believe (the reverse proxy in front of
	 * this app). Empty means: trust nobody's header and key on the socket
	 * address. The first version honoured any client-supplied header, which a
	 * rotating value defeated in twelve requests.
	 */
	@Value("${pdl.ocr.rate-limit.trusted-proxies:}")
	private String trustedProxies;

	private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

	/** The per-minute budget for a path, or null when the path is not capped. */
	Integer limitFor(String path) {
		if (OCR.equals(path))
			return perMinute;
		if (LIVENESS_CHALLENGE.equals(path) || LIVENESS_SUBMIT.equals(path))
			return livenessPerMinute;
		return null;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return limitFor(request.getRequestURI()) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String path = req.getRequestURI();
		int limit = limitFor(path);
		String key = path + "|" + ClientAddress.of(req, trustedProxies);
		long minute = System.currentTimeMillis() / 60_000L;
		long[] b = buckets.computeIfAbsent(key, k -> new long[] { minute, 0 });
		boolean over;
		synchronized (b) {
			if (b[0] != minute) {
				b[0] = minute;
				b[1] = 0;
			}
			over = ++b[1] > limit;
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
}
