package com.ezetik.kjeypapa.pdl.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.ezetik.kjeypapa.pdl.payload.LosDisbursementPayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;

import tools.jackson.databind.json.JsonMapper;

/**
 * What Sambat are told when a webhook body will not parse.
 *
 * <p>Driven through a real Jackson read rather than a hand-built exception,
 * because the bug this guards was entirely about which exception Jackson
 * actually throws: the handler first imported the Jackson 2 class, which
 * compiles fine against this project and never matches at runtime. A test that
 * constructed the exception itself would have passed while the endpoint stayed
 * broken.
 */
class LosWebhookExceptionHandlerTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** The exception Spring would hand the advice for this body. */
	private static String describeFailureOf(String json, Class<?> type) {
		try {
			MAPPER.readValue(json, type);
		} catch (Exception e) {
			return LosWebhookExceptionHandler
					.describe(new HttpMessageNotReadableException("unreadable", e, null));
		}
		throw new AssertionError("expected " + json + " to fail parsing");
	}

	@Test
	@DisplayName("a bare date names the field and the format expected")
	void bareDateIsExplained() {
		// The real incident: a bare yyyy-MM-dd used to answer HTTP 403 with an
		// empty body, which reads as an authentication problem and is not one.
		String message = describeFailureOf(
				"{\"mobileAppId\":1,\"disbursedDate\":\"2026-08-21\"}", LosDisbursementPayload.class);

		assertThat(message).contains("disbursedDate").contains("ISO-8601")
				.contains("2026-08-21T09:00:00Z").contains("Received: '2026-08-21'");
	}

	@Test
	@DisplayName("a date inside a nested schedule row is named too")
	void nestedDateIsExplained() {
		// The schedule rows are where most timestamps live, so a message that
		// only reached top-level fields would miss the common case.
		String message = describeFailureOf(
				"{\"mobileAppId\":1,\"schedule\":[{\"installmentNo\":1,\"dueDate\":\"2026-10-01\"}]}",
				LosNotificationPayload.class);

		assertThat(message).contains("dueDate").contains("ISO-8601");
	}

	@Test
	@DisplayName("a non-date field is named without the date advice")
	void otherFieldsAreNamedPlainly() {
		String message = describeFailureOf(
				"{\"mobileAppId\":1,\"disbursedAmount\":\"lots\"}", LosDisbursementPayload.class);

		assertThat(message).contains("disbursedAmount").contains("Received: 'lots'")
				.doesNotContain("ISO-8601");
	}

	@Test
	@DisplayName("unparseable JSON still gets an answer rather than an empty body")
	void brokenJsonIsExplained() {
		String message = describeFailureOf("{\"mobileAppId\":", LosDisbursementPayload.class);

		assertThat(message).isEqualTo("The request body could not be read as JSON.");
	}
}
