package com.ezetik.kjeypapa.pdl.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates inbound Sambat LOS webhooks ({@code /api/v1/pdl/los/**}), which
 * run outside the customer JWT chain. SCAFFOLDING — the enforcement point is in
 * place but OFF by default ({@code pdl.webhook.auth.enabled=false}) so the
 * sandbox keeps working until Sambat provisions the credentials (Q5.2).
 *
 * When enabled it applies two method-agnostic checks (either can be left empty
 * to skip): a source-IP allowlist and a shared-secret header. If Sambat instead
 * mandates an HMAC body-signature or mTLS, swap the {@link #authorized} body for
 * that — the wiring, path scoping and flag stay the same.
 */
@Component
public class LosWebhookAuthFilter extends OncePerRequestFilter {

	private static final String LOS_PATH_PREFIX = "/api/v1/pdl/los/";

	@Value("${pdl.webhook.auth.enabled:false}")
	private boolean enabled;

	/** Shared secret expected in {@code secretHeader}. Empty = skip the header check. */
	@Value("${pdl.webhook.secret:}")
	private String secret;

	@Value("${pdl.webhook.secret-header:X-LOS-Signature}")
	private String secretHeader;

	/** Comma-separated allowlist of source IPs. Empty = skip the IP check. */
	@Value("${pdl.webhook.allowed-ips:}")
	private String allowedIps;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// Only guard the LOS webhook surface; everything else is untouched.
		return !request.getRequestURI().startsWith(LOS_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!enabled || authorized(request)) {
			chain.doFilter(request, response);
			return;
		}
		response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized LOS webhook");
	}

	private boolean authorized(HttpServletRequest request) {
		// IP allowlist (if configured).
		Set<String> ips = split(allowedIps);
		if (!ips.isEmpty() && !ips.contains(clientIp(request)))
			return false;
		// Shared-secret header (if configured). Constant-time compare.
		if (secret != null && !secret.isBlank()) {
			String provided = request.getHeader(secretHeader);
			if (provided == null || !constantTimeEquals(secret, provided))
				return false;
		}
		return true;
	}

	/** Prefer the first X-Forwarded-For hop when behind a proxy/LB, else the socket IP. */
	private String clientIp(HttpServletRequest request) {
		String fwd = request.getHeader("X-Forwarded-For");
		if (fwd != null && !fwd.isBlank())
			return fwd.split(",")[0].trim();
		return request.getRemoteAddr();
	}

	private Set<String> split(String csv) {
		Set<String> out = new HashSet<>();
		if (csv != null)
			for (String s : Arrays.asList(csv.split(",")))
				if (!s.isBlank())
					out.add(s.trim());
		return out;
	}

	private boolean constantTimeEquals(String a, String b) {
		byte[] x = a.getBytes();
		byte[] y = b.getBytes();
		if (x.length != y.length)
			return false;
		int r = 0;
		for (int i = 0; i < x.length; i++)
			r |= x[i] ^ y[i];
		return r == 0;
	}
}
