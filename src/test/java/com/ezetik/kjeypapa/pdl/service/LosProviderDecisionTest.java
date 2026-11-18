package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The customer's decision on an approved offer, relayed to Sambat.
 *
 * <p>Sambat are replacing {@code /customer-accepted} (their file of
 * 2026-11-16): the application is named by {@code mobileAppId} alone, the
 * decision travels as {@code customerDecision}, and {@code termChange} is
 * required. They have not said when, so the relay is held to working against
 * either version.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LosProviderDecisionTest {

	@Mock LosApplicationMapper mapper;
	@Mock SbfGatewayClient sbf;
	@Mock SbfCifResolver cifResolver;
	@Mock LosSubmitConfig submitConfig;
	@InjectMocks LosProviderImpl provider;

	private PaydayLoan loan;

	@BeforeEach
	void setUp() throws Exception {
		ReflectionTestUtils.setField(provider, "om", new ObjectMapper());
		ReflectionTestUtils.setField(provider, "mockEnabled", false);
		ReflectionTestUtils.setField(provider, "hotline", "023 9977 22");
		ReflectionTestUtils.setField(provider, "acceptSmsText", "");
		ReflectionTestUtils.setField(provider, "acceptTrnCode", "");
		when(submitConfig.getDoneBy()).thenReturn("KjeyPapa");
		loan = new PaydayLoan();
		loan.setId(900001);
		loan.setLosAppId(8558L);
		sambatReply("{\"errorCode\":\"\",\"message\":\"OK\",\"statusCode\":\"200\"}");
	}

	private void sambatReply(String body) throws Exception {
		when(sbf.customerAccepted(anyLong(), any(), any(), any(), any(), any(), any(), any())).thenReturn(body);
	}

	private void assertRefused() {
		assertThatThrownBy(() -> provider.sendDecision(loan, "Y", "contract.pdf"))
				.isInstanceOf(LosSubmitException.class).hasMessageContaining("023 9977 22")
				.extracting("losCode").isEqualTo("LOS_ACCEPT_REFUSED");
	}

	@Test
	@DisplayName("an acceptance goes as Y, a final approval, under both ids")
	void acceptanceIsRelayed() throws Exception {
		provider.sendDecision(loan, "Y", "contract.pdf");

		// termChange N: a payday loan has no term change, so every approval is final.
		verify(sbf).customerAccepted(900001L, 8558L, "KjeyPapa", "Y", "N", "", "", "");
	}

	@Test
	@DisplayName("an acceptance without their LOS id is still relayed, by our id")
	void acceptanceWithoutTheirId() throws Exception {
		// Their new version names the application by mobileAppId alone, and
		// renamed the id in its reply. A reply read under an unknown spelling
		// used to leave the loan impossible to accept.
		loan.setLosAppId(null);

		provider.sendDecision(loan, "Y", "contract.pdf");

		verify(sbf).customerAccepted(900001L, null, "KjeyPapa", "Y", "N", "", "", "");
	}

	@Test
	@DisplayName("a decline is not relayed until Sambat confirm their new version is live")
	void declineIsNotRelayed() {
		// A server that does not read customerDecision would take any call
		// to /customer-accepted for an acceptance.
		provider.sendDecision(loan, "N", null);

		verifyNoInteractions(sbf);
	}

	@Test
	@DisplayName("a reply that says no is a refusal, although it came with HTTP 200")
	void aReplyThatSaysNoIsARefusal() throws Exception {
		sambatReply("{\"errorCode\":\"E400\",\"message\":\"Signed documents are required\",\"statusCode\":\"400\"}");
		assertRefused();

		sambatReply("{\"IsSuccess\":\"False\",\"ErrorMessage\":\"Application not found\"}");
		assertRefused();

		sambatReply("{\"success\":false}");
		assertRefused();

		sambatReply("{\"statusCode\":\"FAILED\"}");
		assertRefused();
	}

	@Test
	@DisplayName("a reply that does not say no is taken as before")
	void anythingElseIsTaken() throws Exception {
		// Their swagger gives the reply's fields and no example, and the new
		// version's reply is unknown: only a plain "no" may stop an acceptance.
		for (String reply : new String[] { "{\"errorCode\":\"\",\"message\":\"OK\",\"statusCode\":\"200\"}",
				"{\"statusCode\":\"201\"}", "{\"IsSuccess\":\"Success\"}", "{\"success\":true}", "{}", "", "OK",
				"[]", "{\"errorCode\":\"S000\",\"message\":\"done\"}" }) {
			sambatReply(reply);
			assertThatCode(() -> provider.sendDecision(loan, "Y", "contract.pdf")).as(reply)
					.doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("a relay that never reached Sambat can be tried again")
	void transportFailureCanBeRetried() throws Exception {
		when(sbf.customerAccepted(anyLong(), any(), any(), any(), any(), any(), any(), any()))
				.thenThrow(new IOException("connection refused"));

		assertThatThrownBy(() -> provider.sendDecision(loan, "Y", "contract.pdf"))
				.isInstanceOf(LosSubmitException.class).hasMessageContaining("try again")
				.extracting("losCode").isEqualTo("LOS_ACCEPT_FAILED");
	}

	@Test
	@DisplayName("the mock relays nothing")
	void mockRelaysNothing() throws Exception {
		ReflectionTestUtils.setField(provider, "mockEnabled", true);

		provider.sendDecision(loan, "Y", "contract.pdf");

		verify(sbf, never()).customerAccepted(anyLong(), any(), any(), any(), any(), any(), any(), any());
		assertThat(loan.getLosAppId()).isEqualTo(8558L);
	}
}
