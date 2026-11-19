package com.ezetik.kjeypapa.pdl.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.ezetik.kjeypapa.pdl.service.PdlSubmissionReconcileService;
import com.ezetik.kjeypapa.security.util.Message;

/**
 * The two reconciliation bodies, read through the real JSON conversion.
 *
 * <p>Both were free-form maps, so Swagger showed {@code additionalProp1..3}
 * for them. These tests hold the named bodies to everything the map took: the
 * id as a number or as text, and "not filed" with no body at all. What the
 * service does with a missing id or with 0 is in
 * {@code PdlSubmissionReconcileServiceTest}.
 */
@SpringJUnitWebConfig(PdlSubmissionAdminControllerTest.Config.class)
class PdlSubmissionAdminControllerTest {

	@Configuration
	@EnableWebMvc
	@Import({ PdlSubmissionAdminController.class, PdlAdminExceptionHandler.class })
	static class Config {
	}

	private static final String FILED = "/api/v1/pdl/admin/submissions/21/filed";
	private static final String NOT_FILED = "/api/v1/pdl/admin/submissions/21/not-filed";

	@Autowired
	private WebApplicationContext context;

	// A bean override, not a @Bean: Spring would autowire the mock's own fields.
	@MockitoBean
	private PdlSubmissionReconcileService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(service.markFiled(anyInt(), any(), any(), any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Loan 21 recorded as filed with Sambat", null)));
		when(service.markNotFiled(anyInt(), any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Loan 21 can be submitted again", null)));
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	private ResultActions filed(String json) throws Exception {
		return mvc.perform(post(FILED).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	@DisplayName("filed: the id, the reference and the note reach the service")
	void filedWithEverything() throws Exception {
		filed("{\"losAppId\":257861,\"losApplicationNo\":\"PDL-2026-0042\",\"note\":\"seen in their console\"}")
				.andExpect(status().isOk());

		verify(service).markFiled(21, 257861L, "PDL-2026-0042", "seen in their console");
	}

	@Test
	@DisplayName("filed: the id may still be sent as text")
	void filedWithTheIdAsText() throws Exception {
		// The map took a number or a string, and trimmed it.
		filed("{\"losAppId\":\" 257861 \"}").andExpect(status().isOk());

		verify(service).markFiled(21, 257861L, "", "");
	}

	@Test
	@DisplayName("filed: Swagger's blank sample reaches the service as 0")
	void filedWithTheBlankSample() throws Exception {
		// The service refuses 0, so the sample sent as it stands changes nothing.
		filed("{\"losAppId\": 0, \"losApplicationNo\": \"\", \"note\": \"\"}").andExpect(status().isOk());

		verify(service).markFiled(21, 0L, "", "");
	}

	@Test
	@DisplayName("filed: Swagger's old placeholder body carries no id")
	void filedWithTheOldPlaceholder() throws Exception {
		filed("{\"additionalProp1\":\"string\",\"additionalProp2\":\"string\",\"additionalProp3\":\"string\"}")
				.andExpect(status().isOk());

		verify(service).markFiled(21, null, "", "");
	}

	@Test
	@DisplayName("filed: an id that is not a number is refused with the reason")
	void filedWithAnIdThatIsNotANumber() throws Exception {
		// As a map this threw inside the handler, and the caller got HTTP 403
		// with no content.
		filed("{\"losAppId\":\"string\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message", containsString("losAppId")));

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("not filed: no body at all")
	void notFiledWithNoBody() throws Exception {
		mvc.perform(post(NOT_FILED)).andExpect(status().isOk());

		verify(service).markNotFiled(21, "");
	}

	@Test
	@DisplayName("not filed: an empty body")
	void notFiledWithAnEmptyBody() throws Exception {
		mvc.perform(post(NOT_FILED).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());

		verify(service).markNotFiled(21, "");
	}

	@Test
	@DisplayName("not filed: the note reaches the service")
	void notFiledWithANote() throws Exception {
		mvc.perform(post(NOT_FILED).contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"nothing in their console\"}")).andExpect(status().isOk());

		verify(service).markNotFiled(21, "nothing in their console");
	}
}
