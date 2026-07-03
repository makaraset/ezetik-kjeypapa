package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.LosBankVerificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload.ScheduleRow;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPaymentScheduleRepository;
import com.ezetik.kjeypapa.pdl.service.LosProvider;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.security.util.Message;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LosWebhookServiceTest {

	@Mock PaydayLoanRepository repo;
	@Mock PdlPaymentScheduleRepository scheduleRepo;
	@Mock LosProvider losProvider;
	@Mock NotificationService notificationService;

	@InjectMocks LosWebhookService service;

	private PaydayLoan loanFor(String losNo) {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(1);
		loan.setLosApplicationNo(losNo);
		loan.setStatus(PdlStatusEnum.Submitted); // valid prior state for reject/rework/approved
		// user left null on purpose — notify() must guard it (no NPE)
		when(repo.findByLosApplicationNo(losNo)).thenReturn(Optional.of(loan));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		return loan;
	}

	private LosNotificationPayload payload(String losNo, String code, String msg) {
		LosNotificationPayload p = new LosNotificationPayload();
		p.setLosApplicationNo(losNo);
		p.setStatusCode(code);
		p.setMessage(msg);
		return p;
	}

	@Test
	void handleReject_mapsRAoToUserMessage() {
		PaydayLoan loan = loanFor("LOS-1");
		service.handleReject(payload("LOS-1", "R-AO", "raw"));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(loan.getLosStatusCode()).isEqualTo("R-AO");
		assertThat(loan.getLosMessage()).isEqualTo("Not eligible for the loan");
	}

	@Test
	void handleRework_mapsRwLpoToUserMessageAndRejects() {
		PaydayLoan loan = loanFor("LOS-2");
		service.handleRework(payload("LOS-2", "RW-LPO", "raw"));

		// PDL has no true rework: rework == reject
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(loan.getLosMessage()).isEqualTo("Insufficient Information or Documents");
	}

	@Test
	void handleApproved_setsApprovedDetailAndStoresSchedule() {
		PaydayLoan loan = loanFor("LOS-3");
		LosNotificationPayload p = payload("LOS-3", null, "Approved");
		p.setLoanRefNo("PDL-2026-003");
		p.setTenor(3);
		p.setCurrency("USD");
		p.setOutstandingAmount(1080.0);
		p.setSchedule(List.of(
				new ScheduleRow(1, null, 333.33, 23.33, 3.34, 0.0, 360.0),
				new ScheduleRow(2, null, 333.33, 23.33, 3.34, 0.0, 360.0)));

		ResponseEntity<Message<String>> r = service.handleApproved(p);

		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Approved);
		assertThat(loan.getLoanRefNo()).isEqualTo("PDL-2026-003");
		assertThat(loan.getTenor()).isEqualTo(3);
		assertThat(loan.getDaysPastDue()).isZero();
		verify(scheduleRepo, times(2)).save(any()); // one per installment
	}

	@Test
	void handle_returnsNotFoundForUnknownLosApplicationNo() {
		when(repo.findByLosApplicationNo("NOPE")).thenReturn(Optional.empty());

		ResponseEntity<Message<String>> r = service.handleReject(payload("NOPE", "R-AO", "x"));

		assertThat(r.getBody().getType()).isEqualTo("NOT_FOUND");
	}

	private LosBankVerificationPayload bankPayload(String losNo, String status, String failureCode) {
		LosBankVerificationPayload p = new LosBankVerificationPayload();
		p.setLosApplicationNo(losNo);
		p.setVerificationStatus(status);
		p.setFailureReason(failureCode);
		return p;
	}

	@Test
	void handleBankVerification_noopsWhenFeatureDisabled() {
		PaydayLoan loan = loanFor("LOS-BV1");
		loan.setStatus(PdlStatusEnum.Accepted);

		ResponseEntity<Message<String>> r = service.handleBankVerification(bankPayload("LOS-BV1", "SUCCESS", null));

		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Accepted); // flag off — no transition
	}

	@Test
	void handleBankVerification_successAdvancesAcceptedToPending() {
		ReflectionTestUtils.setField(service, "bankVerificationEnabled", true);
		PaydayLoan loan = loanFor("LOS-BV2");
		loan.setStatus(PdlStatusEnum.Accepted);

		service.handleBankVerification(bankPayload("LOS-BV2", "SUCCESS", null));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Pending_Bank_Verification);
	}

	@Test
	void handleBankVerification_failedRejectsWithBankMessage() {
		ReflectionTestUtils.setField(service, "bankVerificationEnabled", true);
		PaydayLoan loan = loanFor("LOS-BV3");
		loan.setStatus(PdlStatusEnum.Accepted);

		service.handleBankVerification(bankPayload("LOS-BV3", "FAILED", "R-BANK"));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(loan.getLosStatusCode()).isEqualTo("R-BANK");
		assertThat(loan.getLosMessage()).isEqualTo("Bank account could not be verified");
	}
}
