package com.ezetik.kjeypapa.pdl.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;

import com.ezetik.kjeypapa.pdl.api.LosWebhookController;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.security.util.Message;

/** Nobody but Sambat may approve, reject or update a loan through the webhooks. */
class LosWebhookAuthInterceptorTest {

	private static final String SECRET = "test-only-secret-not-a-real-credential";
	private static final String TRICUBE = "172.16.88.12";

	private LosWebhookAuthInterceptor auth;
	private HandlerMethod webhook;
	private HandlerMethod elsewhere;

	@BeforeEach
	void setUp() throws Exception {
		auth = new LosWebhookAuthInterceptor();
		set("enabled", true);
		set("secret", SECRET);
		set("secretHeader", "X-LOS-Signature");
		set("allowedIps", "");
		set("trustedProxies", "");
		webhook = new HandlerMethod(new LosWebhookController(),
				LosWebhookController.class.getMethod("approved", LosNotificationPayload.class));
		elsewhere = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
	}

	private void set(String field, Object value) {
		ReflectionTestUtils.setField(auth, field, value);
	}

	private MockHttpServletResponse call(HandlerMethod handler, String secret, String peer, String forwardedFor)
			throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/pdl/los/approved");
		req.setRemoteAddr(peer);
		if (secret != null)
			req.addHeader("X-LOS-Signature", secret);
		if (forwardedFor != null)
			req.addHeader("X-Forwarded-For", forwardedFor);
		MockHttpServletResponse res = new MockHttpServletResponse();
		boolean proceed = auth.preHandle(req, res, handler);
		assertThat(proceed).as("proceeds only when nothing was refused").isEqualTo(res.getStatus() == 200);
		return res;
	}

	@Test
	@DisplayName("no secret: 401, and the body names the header to send")
	void missingSecret() throws Exception {
		MockHttpServletResponse res = call(webhook, null, "203.0.113.9", null);
		assertThat(res.getStatus()).isEqualTo(401);
		assertThat(res.getContentType()).startsWith("application/json");
		assertThat(res.getContentAsString())
				.isEqualTo("{\"type\":\"UNAUTHORIZED\",\"message\":\"Missing or wrong X-LOS-Signature header.\",\"data\":null}");
	}

	@Test
	@DisplayName("a wrong secret is refused, including one that is only a prefix of ours")
	void wrongSecret() throws Exception {
		assertThat(call(webhook, "not-the-secret", "203.0.113.9", null).getStatus()).isEqualTo(401);
		assertThat(call(webhook, SECRET.substring(0, 16), "203.0.113.9", null).getStatus()).isEqualTo(401);
		assertThat(call(webhook, SECRET + "x", "203.0.113.9", null).getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("the right secret passes")
	void rightSecret() throws Exception {
		assertThat(call(webhook, SECRET, "203.0.113.9", null).getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("only the webhook controller is checked")
	void otherHandlersUntouched() throws Exception {
		assertThat(call(elsewhere, null, "203.0.113.9", null).getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("switched off, the webhooks are open (local development)")
	void off() throws Exception {
		set("enabled", false);
		assertThat(call(webhook, null, "203.0.113.9", null).getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("switched on with nothing configured refuses everything; it used to pass everything")
	void onButUnconfigured() throws Exception {
		set("secret", "");
		MockHttpServletResponse res = call(webhook, null, "203.0.113.9", null);
		assertThat(res.getStatus()).isEqualTo(503);
		assertThat(res.getContentAsString()).contains("\"type\":\"SERVICE_UNAVAILABLE\"");
	}

	@Test
	@DisplayName("a short secret is a placeholder, not protection, even when the caller knows it")
	void shortSecret() throws Exception {
		set("secret", "changeme");
		assertThat(call(webhook, "changeme", "203.0.113.9", null).getStatus()).isEqualTo(503);
		set("allowedIps", TRICUBE); // and an allowlist does not make it count
		assertThat(call(webhook, "changeme", TRICUBE, null).getStatus()).isEqualTo(503);
	}

	@Test
	@DisplayName("a forged X-Forwarded-For does not get past the allowlist")
	void forgedForwardedFor() throws Exception {
		set("secret", "");
		set("allowedIps", TRICUBE);
		// The old filter believed the left-most entry, which the caller writes.
		MockHttpServletResponse res = call(webhook, null, "203.0.113.9", TRICUBE);
		assertThat(res.getStatus()).isEqualTo(403);
		assertThat(res.getContentAsString()).contains("\"type\":\"FORBIDDEN\"");

		assertThat(call(webhook, null, TRICUBE, null).getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("behind a trusted proxy, the address the proxy appended is the one checked")
	void behindTrustedProxy() throws Exception {
		set("secret", "");
		set("allowedIps", TRICUBE);
		set("trustedProxies", "10.32.6.80");
		assertThat(call(webhook, null, "10.32.6.80", "198.51.100.7, " + TRICUBE).getStatus()).isEqualTo(200);
		assertThat(call(webhook, null, "10.32.6.80", TRICUBE + ", 198.51.100.7").getStatus()).isEqualTo(403);
	}

	@Test
	@DisplayName("with both checks configured, both must pass")
	void bothChecks() throws Exception {
		set("allowedIps", TRICUBE);
		assertThat(call(webhook, SECRET, TRICUBE, null).getStatus()).isEqualTo(200);
		assertThat(call(webhook, null, TRICUBE, null).getStatus()).isEqualTo(401);
		assertThat(call(webhook, SECRET, "203.0.113.9", null).getStatus()).isEqualTo(403);
	}

	/**
	 * The hole the old servlet filter had: it compared the raw URI with
	 * /api/v1/pdl/los/, while Spring matches the decoded path, so an encoded
	 * spelling reached the handler unchecked. Through real MVC dispatch here.
	 */
	@Test
	@DisplayName("an encoded spelling of the path reaches the handler, and is still checked")
	void encodedPath() throws Exception {
		LosWebhookService service = mock(LosWebhookService.class);
		when(service.handleReject(any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Reject processed", null)));
		LosWebhookController controller = new LosWebhookController();
		ReflectionTestUtils.setField(controller, "service", service);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(auth).build();
		URI encoded = URI.create("/api/v1/pdl/%6cos/reject");

		mvc.perform(post(encoded).contentType(MediaType.APPLICATION_JSON).content("{\"mobileAppId\":999999}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
		verifyNoInteractions(service);

		mvc.perform(post(encoded).header("X-LOS-Signature", SECRET).contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileAppId\":999999}"))
				.andExpect(status().isOk());
		verify(service).handleReject(any());
	}
}
