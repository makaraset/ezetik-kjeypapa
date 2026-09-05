package com.ezetik.kjeypapa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4.0 auto-configures a Jackson 3 ObjectMapper
 * ({@code tools.jackson.databind.ObjectMapper}) for the web layer. The SBF
 * integration services still use Jackson 2 ({@code com.fasterxml.jackson}) to
 * (de)serialize upstream payloads, so we expose a Jackson 2 ObjectMapper bean
 * for them to inject. Modules on the classpath (e.g. JSR-310) are auto-registered.
 *
 * FAIL_ON_UNKNOWN_PROPERTIES is disabled to match Spring Boot's default
 * (Boot's auto-configured mapper is lenient): the SBF API returns extra fields
 * (e.g. "done_by") that our models don't declare and must be ignored, not fail.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper()
				.findAndRegisterModules()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
}
