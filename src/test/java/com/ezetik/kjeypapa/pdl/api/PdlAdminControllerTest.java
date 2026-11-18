package com.ezetik.kjeypapa.pdl.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.security.util.Message;

/**
 * The decision endpoint's body, read through the real JSON conversion.
 *
 * <p>The body was a free-form map, so Swagger showed {@code additionalProp1..3}
 * for it. A tester sent that placeholder as it stood, and it was read as a
 * rejection: nothing in it said "approve", and a missing value counted as
 * false. These tests hold the named body to what the app sends, and hold a
 * body that does not say which way to decide to deciding nothing.
 */
@SpringJUnitWebConfig(PdlAdminControllerTest.Config.class)
class PdlAdminControllerTest {

	@Configuration
	@EnableWebMvc
	@Import({ PdlAdminController.class, PdlAdminExceptionHandler.class })
	static class Config {
	}

	private static final String DECISION = "/api/v1/pdl/admin/account-requests/4/decision";

	@Autowired
	private WebApplicationContext context;

	// A bean override, not a @Bean: Spring would autowire the mock's own fields.
	@MockitoBean
	private PdlAccountRequestService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		when(service.decide(anyInt(), anyBoolean(), any(), any(), any(), any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "Account decision processed", "DECIDED")));
		when(service.confirmCif(anyInt(), anyLong(), any()))
				.thenReturn(ResponseEntity.ok(new Message<>("SUCCESS", "CIF 70 confirmed for this customer.", "70")));
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("lpo1", null));
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private ResultActions decide(String json) throws Exception {
		return mvc.perform(post(DECISION).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	@DisplayName("the app's approval, with the employer the LPO picked")
	void approvalAsTheAppSendsIt() throws Exception {
		// Exactly the app's body: it always sends "reason", null on an approval.
		decide("{\"approve\":true,\"reason\":null,\"employerCode\":\"E10002\"}").andExpect(status().isOk());

		verify(service).decide(4, true, null, "lpo1", "E10002", null);
	}

	@Test
	@DisplayName("the app's approval without an employer")
	void approvalWithoutAnEmployer() throws Exception {
		// "Approve without employer" in the console leaves the key out.
		decide("{\"approve\":true,\"reason\":null}").andExpect(status().isOk());

		verify(service).decide(4, true, null, "lpo1", null, null);
	}

	@Test
	@DisplayName("an approval that confirms a CIF passes it on")
	void approvalConfirmingACif() throws Exception {
		// What the console sends when the LPO says the CIF Sambat hold for
		// this ID number is this customer.
		decide("{\"approve\":true,\"reason\":null,\"employerCode\":\"E10002\",\"confirmCif\":70}")
				.andExpect(status().isOk());

		verify(service).decide(4, true, null, "lpo1", "E10002", 70L);
	}

	@Test
	@DisplayName("the app's rejection, with its reason")
	void rejectionAsTheAppSendsIt() throws Exception {
		decide("{\"approve\":false,\"reason\":\"ID photo is unreadable\"}").andExpect(status().isOk());

		verify(service).decide(4, false, "ID photo is unreadable", "lpo1", null, null);
	}

	@Test
	@DisplayName("approve written as text is still read")
	void approveAsTextIsStillRead() throws Exception {
		// The map-bodied endpoint took "true" as well as true. Kept, so a
		// caller who quotes the value is not turned away by the named body.
		decide("{\"approve\":\"true\"}").andExpect(status().isOk());

		verify(service).decide(4, true, null, "lpo1", null, null);
	}

	@Test
	@DisplayName("Swagger's sample for the named body is read as written")
	void swaggerSampleIsReadAsWritten() throws Exception {
		// The sample keeps Swagger's usual values, so this is what its form
		// sends unchanged: an approval, with "string" as reason and employer.
		// Its confirmCif of 0 is passed on as it is, and confirms nothing.
		decide("{\"approve\":true,\"reason\":\"string\",\"employerCode\":\"string\",\"confirmCif\":0}")
				.andExpect(status().isOk());

		verify(service).decide(4, true, "string", "lpo1", "string", 0L);
	}

	@Test
	@DisplayName("Swagger's old placeholder body decides nothing")
	void placeholderBodyDecidesNothing() throws Exception {
		// Before the named body this call reached decide(4, false, null, ...).
		decide("{\"additionalProp1\":\"string\",\"additionalProp2\":\"string\",\"additionalProp3\":\"string\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("INVALID"))
				.andExpect(jsonPath("$.message", containsString("'approve' is required")));

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("a body that does not say which way decides nothing")
	void missingOrNullApproveDecidesNothing() throws Exception {
		decide("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("INVALID"));
		decide("{\"approve\":null,\"reason\":\"no\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("INVALID"));

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("a value that is neither true nor false is named, not answered 403")
	void unreadableApproveIsNamed() throws Exception {
		decide("{\"approve\":\"string\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message", containsString("'approve'")))
				.andExpect(jsonPath("$.message", containsString("Received: 'string'")));

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("broken JSON is said to be broken JSON")
	void brokenJsonIsExplained() throws Exception {
		// A trailing comma is the usual slip when a body is edited by hand.
		decide("{\"approve\":true,}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message", containsString("could not be read as JSON")));

		verifyNoInteractions(service);
	}

	// ----- the CIF of a customer who is already approved -----

	private ResultActions confirmCif(String json) throws Exception {
		return mvc.perform(post("/api/v1/pdl/admin/account-requests/4/confirm-cif")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	@DisplayName("a confirmation for an approved customer passes the CIF on, in the LPO's name")
	void confirmingForAnApprovedCustomer() throws Exception {
		confirmCif("{\"cif\":70}").andExpect(status().isOk()).andExpect(jsonPath("$.data").value("70"));

		verify(service).confirmCif(4, 70L, "lpo1");
	}

	@Test
	@DisplayName("a confirmation that names no CIF stores nothing")
	void aConfirmationWithoutACifStoresNothing() throws Exception {
		// Absent, null, and 0: Swagger's sample value for a number.
		for (String body : new String[] { "{}", "{\"cif\":null}", "{\"cif\":0}" })
			confirmCif(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("INVALID"))
					.andExpect(jsonPath("$.message", containsString("'cif' is required")));

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("a CIF that is not a number is named, not answered 403")
	void anUnreadableCifIsNamed() throws Exception {
		confirmCif("{\"cif\":\"seventy\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message", containsString("'cif'")));

		verifyNoInteractions(service);
	}
}
