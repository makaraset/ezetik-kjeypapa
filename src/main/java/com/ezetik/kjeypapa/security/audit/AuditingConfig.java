package com.ezetik.kjeypapa.security.audit;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing
public class AuditingConfig {

	@Bean
	AuditorAware<String> auditorProvider() {
		return new SpringSecurityAuditAwareImpl();
	}
}

class SpringSecurityAuditAwareImpl implements AuditorAware<String> {

	@Override
	public Optional<String> getCurrentAuditor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			// Server-to-server / no-auth writes (e.g. Sambat LOS webhooks) have no
			// user principal. createdBy is NOT NULL, so fall back to a system marker
			// instead of leaving it null (which would violate the constraint).
			return Optional.of("SYSTEM");
		}

		String userPrincipal = authentication.getName();

		return Optional.ofNullable(userPrincipal);
	}
}
