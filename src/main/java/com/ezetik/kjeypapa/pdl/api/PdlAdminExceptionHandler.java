package com.ezetik.kjeypapa.pdl.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ezetik.kjeypapa.security.util.Message;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells an LPO-console caller what was wrong with the body they sent.
 *
 * <p>The reason is the one given in {@link LosWebhookExceptionHandler}: a body
 * that cannot be read never reaches the controller, and the error dispatch
 * that follows is not on the security whitelist, so the caller gets HTTP 403
 * with no content. Someone trying the decision endpoint from Swagger would be
 * told "forbidden" about a stray comma, or about {@code "approve": "yes"}.
 *
 * <p>Scoped to the two admin controllers that take a named body, so nothing
 * else changes shape.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = { PdlAdminController.class, PdlSubmissionAdminController.class })
public class PdlAdminExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Message<String>> unreadable(HttpMessageNotReadableException e) {
		String detail = LosWebhookExceptionHandler.describe(e);
		log.warn("PDL admin API: unreadable body — {}", detail);
		return new ResponseEntity<>(new Message<>("BAD_REQUEST", detail, null), HttpStatus.BAD_REQUEST);
	}
}
