package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.LosBankVerificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosLoanUpdatePayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload;
import com.ezetik.kjeypapa.pdl.payload.LosNotificationPayload.ScheduleRow;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPaymentScheduleRepository;
import com.ezetik.kjeypapa.pdl.service.LosProvider;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.util.Message;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LosWebhookServiceTest {

	@Mock PaydayLoanRepository repo;
	@Mock PdlPaymentScheduleRepository scheduleRepo;
	@Mock LosProvider losProvider;
	@Mock NotificationService notificationService;

	@InjectMocks LosWebhookService service;

	/** A loan Sambat know by its mobileAppId, which is our own id. */
	private PaydayLoan loanFor(int id) {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(id);
		loan.setStatus(PdlStatusEnum.Submitted); // valid prior state for reject/rework/approved
		// user left null on purpose — notify() must guard it (no NPE)
		when(repo.findById(id)).thenReturn(Optional.of(loan));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		return loan;
	}

	private LosNotificationPayload payload(int mobileAppId, String code, String msg) {
		LosNotificationPayload p = new LosNotificationPayload();
		p.setMobileAppId((long) mobileAppId);
		p.setStatusCode(code);
		p.setMessage(msg);
		return p;
	}

	private PaydayLoan withDevice(PaydayLoan loan) {
		User u = new User();
		u.setUsername("010849009");
		u.setFcmToken("device-token");
		loan.setUser(u);
		ReflectionTestUtils.setField(service, "hotline", "023 9977 22 or 093 99 77 22");
		return loan;
	}

	private NotificationMessage pushed() throws Exception {
		ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
		verify(notificationService).postToClient(sent.capture());
		return sent.getValue();
	}

	@Test
	void handleReject_givesRAoSambatsPaydayWording() {
		PaydayLoan loan = loanFor(1);
		service.handleReject(payload(1, "R-AO", "raw"));

		// Their dictionary's PDL wording. "Not eligible for the loan" is
		// their R-AO text for Micro and Personal loans only.
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(loan.getLosStatusCode()).isEqualTo("R-AO");
		assertThat(loan.getLosMessage()).isEqualTo("Apologies. Your loan application has been rejected.");
	}

	@Test
	void handleRework_mapsRwLpoToUserMessageAndRejects() {
		PaydayLoan loan = loanFor(2);
		service.handleRework(payload(2, "RW-LPO", "raw"));

		// PDL has no true rework: rework == reject
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(loan.getLosMessage()).isEqualTo(
				"Apologies. Your application has been rejected due to insufficient information or documents.");
	}

	@Test
	void handleReject_anUnknownCodeKeepsTheTextSentWithIt() throws Exception {
		PaydayLoan loan = withDevice(loanFor(4));
		service.handleReject(payload(4, "R-NEW", "Their words"));

		assertThat(loan.getLosStatusCode()).isEqualTo("R-NEW");
		assertThat(loan.getLosMessage()).isEqualTo("Their words");
		// The push still reads as sentences when their text has no full stop.
		assertThat(pushed().getBody())
				.isEqualTo("Their words. Please call 023 9977 22 or 093 99 77 22 for more information.");
	}

	@Test
	void rejectionPush_carriesSambatsWordingAndTheHotline() throws Exception {
		// A PDL rework arrives as R-LPO and is a rejection, so it says so.
		withDevice(loanFor(5));
		service.handleRework(payload(5, "R-LPO", null));

		NotificationMessage push = pushed();
		assertThat(push.getTitle()).isEqualTo("Loan application rejected");
		assertThat(push.getBody()).isEqualTo("Apologies. Your application has been rejected due to insufficient "
				+ "information or documents. Please call 023 9977 22 or 093 99 77 22 for more information.");
		assertThat(push.getType()).isEqualTo("loan_rejected");
	}

	@Test
	void rejectionPush_withAnUnknownCodeAndNoText_isTheGenericRejection() throws Exception {
		PaydayLoan loan = withDevice(loanFor(6));
		service.handleReject(payload(6, "R-NEW", null));

		// Nothing stored, so the app words it in the customer's language.
		assertThat(loan.getLosMessage()).isNull();
		assertThat(pushed().getBody()).isEqualTo("Apologies. Your loan application has been rejected. "
				+ "Please call 023 9977 22 or 093 99 77 22 for more information.");
	}

	@Test
	void handleApproved_setsApprovedDetailAndStoresSchedule() {
		PaydayLoan loan = loanFor(3);
		LosNotificationPayload p = payload(3, null, "Approved");
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
		assertThat(loan.getCurrency()).isEqualTo("USD");
		assertThat(loan.getDaysPastDue()).isZero();
		verify(scheduleRepo, times(2)).save(any()); // one per installment
	}

	@Test
	void handleApproved_keepsWhatTheBodyLeavesOut() {
		// An approve without currency used to erase the application's own, and
		// the acceptance page shows anything but KHR as dollars: a riel loan
		// would have been offered to the customer in USD.
		PaydayLoan loan = loanFor(4);
		loan.setCurrency("KHR");
		loan.setLoanRefNo("PDL-2026-004");

		service.handleApproved(payload(4, null, "Approved"));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Approved);
		assertThat(loan.getCurrency()).isEqualTo("KHR");
		assertThat(loan.getLoanRefNo()).isEqualTo("PDL-2026-004");

		LosNotificationPayload blank = payload(4, null, "Approved");
		blank.setCurrency(" ");
		service.handleApproved(blank); // a re-delivery, with a blank currency
		assertThat(loan.getCurrency()).isEqualTo("KHR");
	}

	@Test
	void notFound_namesTheIdentifierItLookedFor() {
		LosNotificationPayload p = new LosNotificationPayload();
		p.setMobileAppId(999999L);

		ResponseEntity<Message<String>> r = service.handleApproved(p);

		assertThat(r.getStatusCode().value()).isEqualTo(200);
		assertThat(r.getBody().getType()).isEqualTo("NOT_FOUND");
		assertThat(r.getBody().getMessage()).isEqualTo("Application not found: mobileAppId 999999");

		// Until Sambat switch, a body naming only their AppId says so.
		LosLoanUpdatePayload u = new LosLoanUpdatePayload();
		u.setAppId(8999L);
		assertThat(service.handleLoanUpdate(u).getBody().getMessage())
				.isEqualTo("Application not found: appId 8999");
	}

	@Test
	void aBodyNamingNoApplicationIsABadRequest() {
		ResponseEntity<Message<String>> r = service.handleReject(new LosNotificationPayload());

		assertThat(r.getStatusCode().value()).isEqualTo(400);
		assertThat(r.getBody().getType()).isEqualTo("BAD_REQUEST");
		assertThat(r.getBody().getMessage()).contains("mobileAppId");

		assertThat(service.handleLoanUpdate(new LosLoanUpdatePayload()).getStatusCode().value()).isEqualTo(400);
		assertThat(service.handleReject(null).getStatusCode().value()).isEqualTo(400);
		verifyNoInteractions(repo);
	}

	@Test
	void handle_mobileAppIdIsTheOneKey() {
		// Agreed with Sambat 2026-11-05. With mobileAppId present their AppId is
		// never consulted, even when it would match a different loan.
		PaydayLoan ours = loanFor(7);
		LosNotificationPayload p = payload(7, "R-AO", null);
		p.setAppId(8281L);

		service.handleReject(p);

		assertThat(ours.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		verify(repo, never()).findByLosAppId(any());
	}

	@Test
	void handle_anUnknownMobileAppIdDoesNotFallBackToTheirAppId() {
		LosNotificationPayload p = payload(404, "R-AO", null);
		p.setAppId(8281L);

		assertThat(service.handleReject(p).getBody().getMessage())
				.isEqualTo("Application not found: mobileAppId 404");
		verify(repo, never()).findByLosAppId(any());
	}

	@Test
	void handle_stillFindsALoanByTheirAppIdUntilTheySwitch() {
		// Their callbacks keyed on their LOS AppId (2026-09-03) until they move
		// to mobileAppId; a body naming only that still lands meanwhile.
		PaydayLoan loan = new PaydayLoan();
		loan.setId(1);
		loan.setLosAppId(8281L);
		loan.setStatus(PdlStatusEnum.Submitted);
		when(repo.findByLosAppId(8281L)).thenReturn(Optional.of(loan));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		LosNotificationPayload p = new LosNotificationPayload();
		p.setAppId(8281L);
		p.setStatusCode("R-AO");

		ResponseEntity<Message<String>> r = service.handleReject(p);

		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
	}

	private LosBankVerificationPayload bankPayload(int mobileAppId, String status, String failureCode) {
		LosBankVerificationPayload p = new LosBankVerificationPayload();
		p.setMobileAppId((long) mobileAppId);
		p.setVerificationStatus(status);
		p.setFailureReason(failureCode);
		return p;
	}

	@Test
	void handleBankVerification_noopsWhenFeatureDisabled() {
		PaydayLoan loan = loanFor(11);
		loan.setStatus(PdlStatusEnum.Accepted);

		ResponseEntity<Message<String>> r = service.handleBankVerification(bankPayload(11, "SUCCESS", null));

		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Accepted); // flag off — no transition
	}

	@Test
	void handleBankVerification_successAdvancesAcceptedToPending() {
		ReflectionTestUtils.setField(service, "bankVerificationEnabled", true);
		PaydayLoan loan = loanFor(12);
		loan.setStatus(PdlStatusEnum.Accepted);

		service.handleBankVerification(bankPayload(12, "SUCCESS", null));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Pending_Bank_Verification);
	}

	@Test
	void handleBankVerification_failedStaysAcceptedForReattempt() {
		// Sambat QB4.1-4.3: hand-off failure does NOT reject — the customer
		// re-attempts until the daily cut-off sweep expires the offer.
		ReflectionTestUtils.setField(service, "bankVerificationEnabled", true);
		PaydayLoan loan = loanFor(13);
		loan.setStatus(PdlStatusEnum.Pending_Bank_Verification);

		service.handleBankVerification(bankPayload(13, "FAILED", null));

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Accepted);
		assertThat(loan.getLosMessage()).contains("re-attempt");
	}
}
