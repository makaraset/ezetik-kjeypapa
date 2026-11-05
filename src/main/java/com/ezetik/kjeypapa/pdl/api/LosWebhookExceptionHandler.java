package com.ezetik.kjeypapa.pdl.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ezetik.kjeypapa.security.util.Message;
import tools.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells Sambat what was wrong with a webhook body they sent.
 *
 * <p>Without this, a body Jackson cannot parse produced <b>HTTP 403 with no
 * content</b>. The parse failure never reaches a controller, so the request
 * falls through to the error dispatch, which is not on the security whitelist,
 * and the caller is told "forbidden" about what is really a malformed field.
 *
 * <p>That is an expensive lie to tell an integration partner. A 403 sends their
 * developer to check credentials, IP allowlisting and TLS — none of which is the
 * problem — and the one date they typed as {@code "2026-08-21"} instead of
 * {@code "2026-08-21T09:00:00Z"} is never mentioned. We lost time to exactly
 * this on 2026-08-21 before recognising it.
 *
 * <p>Scoped to the webhook package so nothing else changes shape.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = { LosWebhookController.class })
public class LosWebhookExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Message<String>> unreadable(HttpMessageNotReadableException e) {
		String detail = describe(e);
		log.warn("LOS webhook: unreadable body — {}", detail);
		return new ResponseEntity<>(new Message<>("BAD_REQUEST", detail, null), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Names the offending field and, for a date, says what shape is expected.
	 *
	 * <p>Dates are called out specifically because they are the field type most
	 * likely to be sent wrong: every timestamp we accept is an instant, and a
	 * bare {@code yyyy-MM-dd} is the natural thing for a caller to send.
	 */
	static String describe(HttpMessageNotReadableException e) {
		Throwable cause = e.getCause();
		if (cause instanceof InvalidFormatException ife) {
			String field = ife.getPath().isEmpty() ? "" : ife.getPath().get(ife.getPath().size() - 1).getPropertyName();
			String value = String.valueOf(ife.getValue());
			if (ife.getTargetType() != null && java.time.temporal.Temporal.class.isAssignableFrom(ife.getTargetType()))
				return "Field '" + field + "' is a date-time and must be a full ISO-8601 instant "
						+ "such as 2026-08-21T09:00:00Z. Received: '" + value + "'.";
			return "Field '" + field + "' could not be read. Received: '" + value + "'.";
		}
		return "The request body could not be read as JSON.";
	}
}
