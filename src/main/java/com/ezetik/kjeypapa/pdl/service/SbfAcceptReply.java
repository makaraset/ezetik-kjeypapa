package com.ezetik.kjeypapa.pdl.service;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What Sambat's reply to {@code /customer-accepted} says.
 *
 * <p>Their swagger gives the reply as {@code {errorCode, message, statusCode}},
 * all strings, with no example, and the reply of the version they are moving
 * to is unknown. Their other call answers a rejection with HTTP 200 and
 * {@code IsSuccess: "False"}, so the status line alone cannot be trusted here
 * either.
 *
 * <p>Only a reply that plainly says no counts as a refusal: a failure status,
 * or a success flag that is false. Anything else is taken as it was before
 * the reply was read at all. Guessing the other way would stop real
 * acceptances on a reply shape nobody has seen.
 */
final class SbfAcceptReply {

	private SbfAcceptReply() {
	}

	/** Why the reply is a refusal, in a form fit for the log, or null when it is not one. */
	static String refusal(String body, ObjectMapper om) {
		if (body == null || body.isBlank())
			return null;
		JsonNode n;
		try {
			n = om.readTree(body);
		} catch (Exception e) {
			return null; // not JSON: nothing in it says no
		}
		if (n == null || !n.isObject())
			return null;

		JsonNode flag = first(n, "IsSuccess", "isSuccess", "success", "Success");
		if (flag != null && saysNo(flag))
			return "success flag " + flag + said(n);

		JsonNode status = first(n, "statusCode", "StatusCode", "status", "Status");
		if (status != null && isFailureStatus(status))
			return "status " + status + said(n);
		return null;
	}

	/** false, or a word for it. "Success" and true are a yes. */
	private static boolean saysNo(JsonNode flag) {
		if (flag.isBoolean())
			return !flag.asBoolean();
		String t = flag.asText("").trim().toLowerCase(Locale.ROOT);
		return t.equals("false") || t.startsWith("fail");
	}

	/** An HTTP-style code from 400 up, or a word that starts "fail" or "err". */
	private static boolean isFailureStatus(JsonNode status) {
		String t = status.asText("").trim().toLowerCase(Locale.ROOT);
		if (t.matches("\\d{3}"))
			return Integer.parseInt(t) >= 400;
		return t.startsWith("fail") || t.startsWith("err");
	}

	/** Their own words about it, for the log. */
	private static String said(JsonNode n) {
		JsonNode m = first(n, "message", "Message", "ErrorMessage", "errorMessage");
		String text = m == null || m.isNull() ? "" : m.asText("").trim();
		return text.isEmpty() ? "" : ": " + (text.length() <= 200 ? text : text.substring(0, 200));
	}

	private static JsonNode first(JsonNode n, String... names) {
		for (String name : names) {
			JsonNode v = n.get(name);
			if (v != null && !v.isNull())
				return v;
		}
		return null;
	}
}
