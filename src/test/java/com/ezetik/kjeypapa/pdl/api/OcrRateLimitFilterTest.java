package com.ezetik.kjeypapa.pdl.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/** The anonymous Sambat relays are capped per client, each path on its own budget. */
class OcrRateLimitFilterTest {

	private OcrRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		filter = new OcrRateLimitFilter();
		ReflectionTestUtils.setField(filter, "perMinute", 2);
		ReflectionTestUtils.setField(filter, "livenessPerMinute", 3);
		ReflectionTestUtils.setField(filter, "trustedProxies", "");
	}

	private int call(String path) throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
		req.setRemoteAddr("203.0.113.9");
		MockHttpServletResponse res = new MockHttpServletResponse();
		filter.doFilter(req, res, new MockFilterChain());
		return res.getStatus();
	}

	@Test
	@DisplayName("OCR keeps its budget, then answers 429")
	void ocr() throws Exception {
		assertThat(call(OcrRateLimitFilter.OCR)).isEqualTo(200);
		assertThat(call(OcrRateLimitFilter.OCR)).isEqualTo(200);
		assertThat(call(OcrRateLimitFilter.OCR)).isEqualTo(429);
	}

	@Test
	@DisplayName("both liveness paths are capped, and spending one budget leaves the others intact")
	void separateBudgets() throws Exception {
		for (int i = 0; i < 3; i++)
			assertThat(call(OcrRateLimitFilter.LIVENESS_SUBMIT)).isEqualTo(200);
		assertThat(call(OcrRateLimitFilter.LIVENESS_SUBMIT)).isEqualTo(429);

		assertThat(call(OcrRateLimitFilter.LIVENESS_CHALLENGE)).isEqualTo(200);
		assertThat(call(OcrRateLimitFilter.OCR)).isEqualTo(200);
	}

	@Test
	@DisplayName("every other path passes through uncounted")
	void othersPassThrough() throws Exception {
		for (int i = 0; i < 20; i++)
			assertThat(call("/api/v1/pdl/account-request")).isEqualTo(200);
		assertThat(filter.limitFor("/api/v1/pdl/account-request/status")).isNull();
	}
}
