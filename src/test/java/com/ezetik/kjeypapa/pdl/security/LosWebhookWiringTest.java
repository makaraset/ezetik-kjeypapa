package com.ezetik.kjeypapa.pdl.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.ezetik.kjeypapa.pdl.api.LosWebhookController;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.security.util.Message;

/**
 * The interceptor protects nothing unless Spring puts it in front of the
 * handlers. This goes through the real registration, so losing
 * {@link LosWebhookWebConfig} fails here instead of quietly reopening F16.
 */
@SpringJUnitWebConfig(LosWebhookWiringTest.Config.class)
@TestPropertySource(properties = { "pdl.webhook.auth.enabled=true",
		"pdl.webhook.secret=test-only-secret-not-a-real-credential" })
class LosWebhookWiringTest {

	@Configuration
	@EnableWebMvc
	@Import({ LosWebhookWebConfig.class, LosWebhookAuthInterceptor.class, LosWebhookController.class })
	static class Config {
	}

	@Autowired
	private WebApplicationContext context;

	// A bean override, not a @Bean: Spring would autowire the mock's own fields.
	@MockitoBean
	private LosWebhookService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(service.handleApproved(any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Approved processed", null)));
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	@DisplayName("an anonymous approve is refused by the registered interceptor")
	void anonymousRefused() throws Exception {
		mvc.perform(post("/api/v1/pdl/los/approved").contentType(MediaType.APPLICATION_JSON)
				.content("{\"appId\":999999}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("with the secret, the approve is served")
	void withSecretServed() throws Exception {
		mvc.perform(post("/api/v1/pdl/los/approved").header("X-LOS-Signature", "test-only-secret-not-a-real-credential")
				.contentType(MediaType.APPLICATION_JSON).content("{\"appId\":999999}"))
				.andExpect(status().isOk());
		verify(service).handleApproved(any());
	}
}
