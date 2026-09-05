package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ezetik.kjeypapa.pdl.api.ClientAddress;
import com.ezetik.kjeypapa.pdl.service.ConsentContext;

/**
 * Both of these read client-supplied input on the path that files a real credit
 * application, so the failure that matters is not a wrong value — it is a value
 * that breaks the submission after Sambat has already accepted it.
 */
class ConsentContextTest {

	@Test
	@DisplayName("an over-long header is cut to its column, not left to fail the insert")
	void headersAreCutToColumnWidth() {
		// The insert happens at the very end of submit(). A 10KB X-Device would
		// roll back an application Sambat has already filed.
		ConsentContext c = new ConsentContext("v".repeat(200), "d".repeat(500), "1".repeat(100));

		assertThat(c.appVersion()).hasSize(32);
		assertThat(c.deviceModel()).hasSize(64);
		assertThat(c.ipAddress()).hasSize(45);
	}

	@Test
	@DisplayName("control characters are stripped")
	void controlCharactersAreStripped() {
		// The canonical form escapes newlines, so this cannot forge a field
		// boundary in the hash — but no real device name contains one, and a
		// stored record should stay readable.
		ConsentContext c = new ConsentContext("2.0.1+45\n", "iPhone14,3\r\nprev_hash=x", " 10.0.0.4 ");

		assertThat(c.appVersion()).isEqualTo("2.0.1+45");
		assertThat(c.deviceModel()).isEqualTo("iPhone14,3prev_hash=x");
		assertThat(c.ipAddress()).isEqualTo("10.0.0.4");
	}

	@Test
	@DisplayName("nulls become the empty string, which is what 'not captured' means")
	void nullsBecomeEmpty() {
		ConsentContext c = new ConsentContext(null, null, null);

		assertThat(c.appVersion()).isEmpty();
		assertThat(c.deviceModel()).isEmpty();
		assertThat(c.ipAddress()).isEmpty();
		assertThat(ConsentContext.NONE.ipAddress()).isEmpty();
	}

	@Test
	@DisplayName("a forwarding header from an untrusted peer is ignored")
	void forwardedHeaderIsIgnoredFromAnUntrustedPeer() {
		// Otherwise the address in a consent record is whatever the client
		// decided to write, which is worse than recording nothing.
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRemoteAddr("198.51.100.7");
		req.addHeader("X-Forwarded-For", "203.0.113.1");

		assertThat(ClientAddress.of(req, "")).isEqualTo("198.51.100.7");
		assertThat(ClientAddress.of(req, "10.0.0.1")).isEqualTo("198.51.100.7");
	}

	@Test
	@DisplayName("from a trusted proxy, the hop the proxy itself appended wins")
	void trustedProxyContributesTheRightmostHop() {
		// Everything left of the last entry was supplied by whoever came before
		// the proxy, so a client can prepend anything it likes.
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRemoteAddr("10.0.0.1");
		req.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.9");

		assertThat(ClientAddress.of(req, "10.0.0.1")).isEqualTo("203.0.113.9");
	}
}
