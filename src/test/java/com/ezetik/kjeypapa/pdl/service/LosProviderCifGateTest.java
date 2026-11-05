package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationParam;
import com.ezetik.kjeypapa.pdl.service.SbfCifResolver.CifCheck;
import com.ezetik.kjeypapa.pdl.service.SbfCifResolver.Outcome;
import com.ezetik.kjeypapa.security.model.User;

/**
 * Nothing is filed until the CIF is settled. custId 0 for a customer Sambat
 * already hold earns a second CIF or an automatic reject (Sambat, 2026-11-05).
 */
@ExtendWith(MockitoExtension.class)
class LosProviderCifGateTest {

	@Mock LosApplicationMapper mapper;
	@Mock SbfGatewayClient sbf;
	@Mock SbfCifResolver cifResolver;
	@InjectMocks LosProviderImpl provider;

	private final byte[] pdf = { 1 };
	private PaydayLoan loan;

	@BeforeEach
	void setUp() {
		User u = new User();
		u.setId(42);
		loan = new PaydayLoan();
		loan.setId(7);
		loan.setUser(u);
	}

	private void sambat(Outcome outcome) {
		when(cifResolver.ensure(loan.getUser())).thenReturn(new CifCheck(outcome, null, List.of()));
	}

	@Test
	@DisplayName("while Sambat cannot be asked, the application waits rather than going as new")
	void outageWaits() throws Exception {
		sambat(Outcome.UNAVAILABLE);

		assertThatThrownBy(() -> provider.submitApplication(loan, pdf)).isInstanceOf(LosSubmitException.class)
				.extracting("losCode").isEqualTo("SBF_CUSTOMER_UNAVAILABLE");
		verify(mapper, never()).toParam(any(), any());
		verify(sbf, never()).newLoanApplication(any());
	}

	@Test
	@DisplayName("an ID number Sambat hold under other details is not filed")
	void mismatchWaits() throws Exception {
		sambat(Outcome.MISMATCH);

		assertThatThrownBy(() -> provider.submitApplication(loan, pdf)).isInstanceOf(LosSubmitException.class)
				.extracting("losCode").isEqualTo("SBF_CUSTOMER_MISMATCH");
		verify(sbf, never()).newLoanApplication(any());
	}

	@Test
	@DisplayName("a confirmed new customer and a known CIF both go on to be filed")
	void settledGoesOn() throws Exception {
		when(mapper.toParam(loan, pdf)).thenReturn(new NewApplicationParam());
		when(sbf.newLoanApplication(any())).thenThrow(new IOException("stop here"));

		for (Outcome settled : List.of(Outcome.NEW, Outcome.STORED, Outcome.MATCHED)) {
			sambat(settled);
			assertThatThrownBy(() -> provider.submitApplication(loan, pdf)).isInstanceOf(LosSubmitException.class)
					.extracting("losCode").isEqualTo("LOS_UNAVAILABLE");
		}
		verify(mapper, org.mockito.Mockito.times(3)).toParam(loan, pdf);
	}
}
