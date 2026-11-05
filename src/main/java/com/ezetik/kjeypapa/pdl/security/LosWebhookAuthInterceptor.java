package com.ezetik.kjeypapa.pdl.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ezetik.kjeypapa.pdl.api.ClientAddress;
import com.ezetik.kjeypapa.pdl.api.LosWebhookController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates the inbound Sambat LOS webhooks. They sit on the security
 * whitelist because Sambat call them server to server, with no customer JWT, so
 * this is the only thing between the internet and "approve this loan".
 *
 * <p><b>Decided by the handler, not the URL.</b> The servlet filter this
 * replaces skipped any request whose raw URI did not start with
 * {@code /api/v1/pdl/los/}. Spring MVC and Spring Security both match on the
 * decoded path, so {@code /api/v1/pdl/%6cos/approved} still reached the approve
 * handler — through SBF's load balancer, verified on UAT on 2026-10-28 — while
 * the filter let it through unchecked. An interceptor runs after handler
 * mapping, so it guards exactly the requests that will execute a
 * {@link LosWebhookController} method, however the path is spelled.
 *
 * <p>Two checks, either or both:
 * <ul>
 * <li>a shared secret in the {@code pdl.webhook.secret-header} header, compared
 * in constant time;</li>
 * <li>a source-address allowlist, resolved through {@link ClientAddress}. The
 * old filter believed the left-most {@code X-Forwarded-For} entry, which is the
 * one the caller writes, so a forged header passed it.</li>
 * </ul>
 *
 * <p><b>Switched on but not configured refuses everything.</b> The old filter
 * passed every request when neither check was configured, so a deploy that lost
 * its secret looked protected and was open. A secret shorter than
 * {@value #MIN_SECRET_LENGTH} characters counts as not configured: a placeholder
 * must not quietly pass for protection.
 */
@Slf4j
@Component
public class LosWebhookAuthInterceptor implements HandlerInterceptor {

	static final int MIN_SECRET_LENGTH = 32;

	@Value("${pdl.webhook.auth.enabled:false}")
	private boolean enabled;

	@Value("${pdl.webhook.secret:}")
	private String secret;

	@Value("${pdl.webhook.secret-header:X-LOS-Signature}")
	private String secretHeader;

	/** Comma-separated source addresses. Empty = no address check. */
	@Value("${pdl.webhook.allowed-ips:}")
	private String allowedIps;

	/** The same proxies the rate limiter and the consent record believe. */
	@Value("${pdl.ocr.rate-limit.trusted-proxies:}")
	private String trustedProxies;

	@PostConstruct
	void reportAtStartup() {
		if (!enabled)
			log.warn("LOS webhooks: authentication is OFF. Anyone who can reach /api/v1/pdl/los/** "
					+ "can approve, reject or update a loan.");
		else if (!configured())
			log.error("LOS webhooks: authentication is ON but no secret of at least {} characters and no "
					+ "address allowlist is set. Every webhook call will be refused.", MIN_SECRET_LENGTH);
		else
			log.info("LOS webhooks: authentication ON (secret header: {}; address allowlist: {})",
					hasSecret() ? secretHeader : "not used",
					allowlist().isEmpty() ? "not used" : allowlist().size() + " address(es)");
	}

	@Override
	public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
		if (!enabled || !isWebhook(handler))
			return true;

		if (!configured())
			return refuse(req, res, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
					"Webhook authentication is switched on but not configured on our side. Nothing was processed.",
					"authentication not configured");

		Set<String> ips = allowlist();
		String client = ClientAddress.of(req, trustedProxies);
		if (!ips.isEmpty() && !ips.contains(client))
			return refuse(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN",
					"This address may not call the LOS webhooks.", "address not on the allowlist");

		if (hasSecret() && !secretMatches(req.getHeader(secretHeader)))
			return refuse(req, res, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
					"Missing or wrong " + secretHeader + " header.", "secret missing or wrong");

		return true;
	}

	private static boolean isWebhook(Object handler) {
		return handler instanceof HandlerMethod hm && LosWebhookController.class.isAssignableFrom(hm.getBeanType());
	}

	private boolean configured() {
		if (hasSecret() && secret.strip().length() < MIN_SECRET_LENGTH)
			return false;
		return hasSecret() || !allowlist().isEmpty();
	}

	private boolean hasSecret() {
		return secret != null && !secret.isBlank();
	}

	/** Constant time in the length of our secret, whatever the caller sends. */
	private boolean secretMatches(String provided) {
		if (provided == null)
			return false;
		return MessageDigest.isEqual(secret.strip().getBytes(StandardCharsets.UTF_8),
				provided.getBytes(StandardCharsets.UTF_8));
	}

	private Set<String> allowlist() {
		if (allowedIps == null || allowedIps.isBlank())
			return Set.of();
		return Arrays.stream(allowedIps.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	/**
	 * Answers in the same envelope as every other webhook reply, so Sambat's
	 * developer reads why — unlike the old filter's sendError, which went to the
	 * error dispatch and could come back as an empty 403 (see F12).
	 */
	private boolean refuse(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String type,
			String message, String reason) throws IOException {
		log.warn("LOS webhook refused: {} {} from {}: {}", req.getMethod(), req.getRequestURI(),
				ClientAddress.of(req, trustedProxies), reason);
		res.setStatus(status.value());
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.getWriter().write("{\"type\":\"" + type + "\",\"message\":\"" + json(message) + "\",\"data\":null}");
		return false;
	}

	private static String json(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
