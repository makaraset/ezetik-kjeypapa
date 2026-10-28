package com.ezetik.kjeypapa.pdl.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts {@link LosWebhookAuthInterceptor} in front of every handler. */
@Configuration
public class LosWebhookWebConfig implements WebMvcConfigurer {

	private final LosWebhookAuthInterceptor auth;

	public LosWebhookWebConfig(LosWebhookAuthInterceptor auth) {
		this.auth = auth;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// No path patterns, on purpose: the interceptor picks out the webhook
		// handlers itself, so no spelling of the URL can route around it.
		registry.addInterceptor(auth);
	}
}
