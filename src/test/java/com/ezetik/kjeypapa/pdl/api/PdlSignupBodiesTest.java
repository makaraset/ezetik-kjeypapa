package com.ezetik.kjeypapa.pdl.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.ezetik.kjeypapa.pdl.payload.NidOcrResponse;
import com.ezetik.kjeypapa.pdl.service.NidOcrService;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.pdl.service.PdlIdentityVerificationService;
import com.ezetik.kjeypapa.security.util.Message;

/**
 * The two sign-up bodies the released app sends, read through the real JSON
 * conversion.
 *
 * <p>Both were free-form maps, so Swagger showed {@code additionalProp1..3}
 * for them. Builds already on customers' phones send these exact bodies, so
 * the named ones must read them the same way.
 */
@SpringJUnitWebConfig(PdlSignupBodiesTest.Config.class)
class PdlSignupBodiesTest {

	@Configuration
	@EnableWebMvc
	@Import({ PdlAccountRequestController.class, PdlOcrController.class })
	static class Config {
	}

	private static final String STATUS = "/api/v1/pdl/account-request/status";
	private static final String OCR = "/api/v1/pdl/ocr-nid";

	@Autowired
	private WebApplicationContext context;

	// Bean overrides, not @Beans: Spring would autowire the mocks' own fields.
	@MockitoBean
	private PdlAccountRequestService accounts;

	@MockitoBean
	private PdlIdentityVerificationService verification;

	@MockitoBean
	private NidOcrService ocr;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(accounts.status(any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Status", "PENDING")));
		when(ocr.extract(any())).thenReturn(new ResponseEntity<>(
				new Message<NidOcrResponse>("INVALID", "Missing idImage", null), HttpStatus.EXPECTATION_FAILED));
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	@DisplayName("status: the app's body")
	void statusAsTheAppSendsIt() throws Exception {
		// lib/repository/pdl_service.dart, getAccountRequestStatus.
		mvc.perform(post(STATUS).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"012345678\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("SUCCESS"))
				.andExpect(jsonPath("$.data").value("PENDING"));

		verify(accounts).status("012345678");
	}

	@Test
	@DisplayName("status: a body with no username asks about nobody")
	void statusWithNoUsername() throws Exception {
		mvc.perform(post(STATUS).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

		verify(accounts).status(null);
	}

	@Test
	@DisplayName("status: Swagger's blank sample asks about nobody")
	void statusWithTheBlankSample() throws Exception {
		mvc.perform(post(STATUS).contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"\"}"))
				.andExpect(status().isOk());

		verify(accounts).status("");
	}

	@Test
	@DisplayName("ID card: the app's body")
	void ocrAsTheAppSendsIt() throws Exception {
		// lib/repository/pdl_service.dart, ocrNid: the photo's bytes in base64.
		mvc.perform(post(OCR).contentType(MediaType.APPLICATION_JSON).content("{\"imageBase64\":\"/9j/4AAQSkZJRg==\"}"));

		verify(ocr).extract("/9j/4AAQSkZJRg==");
	}

	@Test
	@DisplayName("ID card: Swagger's blank sample reaches the service blank")
	void ocrWithTheBlankSample() throws Exception {
		// The service refuses a blank image before it calls Sambat.
		mvc.perform(post(OCR).contentType(MediaType.APPLICATION_JSON).content("{\"imageBase64\": \"\"}"))
				.andExpect(status().isExpectationFailed())
				.andExpect(jsonPath("$.type").value("INVALID"));

		verify(ocr).extract("");
	}

	@Test
	@DisplayName("ID card: a body with no image reaches the service as none")
	void ocrWithNoImage() throws Exception {
		mvc.perform(post(OCR).contentType(MediaType.APPLICATION_JSON).content("{}"));

		verify(ocr).extract(null);
	}
}
