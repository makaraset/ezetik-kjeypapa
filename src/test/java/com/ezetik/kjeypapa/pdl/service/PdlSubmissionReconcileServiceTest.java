package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;

@ExtendWith(MockitoExtension.class)
class PdlSubmissionReconcileServiceTest {

	@Mock PaydayLoanRepository repo;

	@InjectMocks PdlSubmissionReconcileService service;

	@Test
	void markFiled_needsTheirLosAppId_notJustTheAppRefId() {
		// Callbacks find a loan by our own id (mobileAppId, 2026-11-05), but
		// /customer-accepted still takes their LOS AppId. Recorded without it,
		// the loan could never relay the customer's acceptance.
		PaydayLoan loan = new PaydayLoan();
		loan.setId(21);
		loan.setStatus(PdlStatusEnum.Draft);
		loan.setLosStatusCode(LosSubmitException.OUTCOME_UNKNOWN);
		when(repo.findById(21)).thenReturn(Optional.of(loan));

		var r = service.markFiled(21, null, "257861", "found in their console");

		assertThat(r.getBody().getType()).isEqualTo("INVALID");
		assertThat(r.getBody().getMessage()).contains("LOS AppId");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Draft);
		assertThat(loan.getLosApplicationNo()).isNull();
		verify(repo, never()).save(any());
	}
}
