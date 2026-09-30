package com.ezetik.kjeypapa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.ezetik.kjeypapa.security.config.JwtAuthenticationFilter;
import com.ezetik.kjeypapa.security.service.JwtService;

/**
 * A token the server cannot use means "not authenticated", never "fail the
 * request".
 *
 * <p>This filter runs on every request, whitelisted paths included. When it
 * threw on an expired token it took the entire unauthenticated signup flow with
 * it — the NID scan and the account-request probe both answered 403 to anyone
 * whose phone still held an old token, which reads as a permissions problem and
 * was really a stale value in the keychain. Found on a real device on
 * 2026-09-30 against a token six days expired.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtFilterStaleTokenTest {

	@Mock JwtService jwtService;
	@Mock UserDetailsService userDetailsService;

	private JwtAuthenticationFilter filter;
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain chain;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
		filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
		request = new MockHttpServletRequest("POST", "/api/v1/pdl/ocr-nid");
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	@Test
	@DisplayName("an expired token does not fail the request; it just does not authenticate")
	void expiredTokenPassesThroughUnauthenticated() throws Exception {
		request.addHeader("Authorization", "Bearer expired.token.value");
		when(jwtService.extractUsername(any()))
				.thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

		filter.doFilter(request, response, chain);

		// Reached the endpoint rather than dying in the filter.
		assertThat(chain.getRequest()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(userDetailsService, never()).loadUserByUsername(any());
	}

	@Test
	@DisplayName("a malformed token behaves the same way")
	void malformedTokenPassesThroughUnauthenticated() throws Exception {
		request.addHeader("Authorization", "Bearer not-a-jwt");
		when(jwtService.extractUsername(any())).thenThrow(new IllegalArgumentException("bad"));

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	@DisplayName("no Authorization header at all is still fine")
	void noHeaderPassesThrough() throws Exception {
		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}
