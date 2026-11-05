package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.service.LosProvider;
import com.ezetik.kjeypapa.pdl.service.LosWebhookService;
import com.ezetik.kjeypapa.pdl.service.PdlAcceptanceScheduler;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlAcceptanceSchedulerTest {

	@Mock PaydayLoanRepository repo;
	@Mock NotificationService notificationService;
	@Mock LosProvider losProvider;

	@InjectMocks PdlAcceptanceScheduler scheduler;

	@BeforeEach
	void grace() {
		ReflectionTestUtils.setField(scheduler, "graceMinutes", 30L);
	}

	private PaydayLoan approved(Instant approvedDate) {
		PaydayLoan l = new PaydayLoan();
		l.setId(1);
		l.setStatus(PdlStatusEnum.Approved);
		l.setLosApplicationNo("LOS-1");
		l.setApprovedDate(approvedDate);
		return l;
	}

	private PaydayLoan accepted(Instant approvedDate) {
		PaydayLoan l = approved(approvedDate);
		l.setId(9);
		l.setStatus(PdlStatusEnum.Accepted);
		l.setLosApplicationNo("LOS-9");
		return l;
	}

	@Test
	void cutoff_rejectsOffersApprovedBeforeTheGraceWindow() {
		PaydayLoan l = approved(Instant.now().minus(2, ChronoUnit.HOURS));
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of(l));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		scheduler.enforceCutoff();

		assertThat(l.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		// The app words the reason from the code; the text is Sambat's lapse wording.
		assertThat(l.getLosStatusCode()).isEqualTo("R-CUTOFF");
		assertThat(l.getLosMessage()).isEqualTo("Apologies. Your loan request is lapsed due to failure in "
				+ "providing your final decision. Please try it again on the next working day.");
		verify(losProvider).sendDecision(l, "N", null);
	}

	@Test
	void cutoff_skipsOffersApprovedWithinTheGraceWindow() {
		PaydayLoan l = approved(Instant.now()); // just approved — within the 30-min grace
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of(l));

		scheduler.enforceCutoff();

		assertThat(l.getStatus()).isEqualTo(PdlStatusEnum.Approved); // untouched
		verify(losProvider, never()).sendDecision(any(), any(), any());
		verify(repo, never()).save(any());
	}

	@Test
	void reminders_iterateApprovedOffers() {
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of(approved(Instant.now())));
		scheduler.sendReminders();
		verify(repo).findByStatus(PdlStatusEnum.Approved);
	}

	@Test
	void cutoff_withTheBankHandoff_alsoExpiresAcceptedLoansPastGrace() {
		// QB4.1: Re-Attempt is disabled once the cut-off passes — an Accepted
		// loan whose bank hand-off never succeeded expires like an Approved one.
		ReflectionTestUtils.setField(scheduler, "graceMinutes", 0L);
		ReflectionTestUtils.setField(scheduler, "bankHandoffEnabled", true);
		PaydayLoan loan = accepted(Instant.now().minus(2, ChronoUnit.HOURS));
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of());
		when(repo.findByStatus(PdlStatusEnum.Accepted)).thenReturn(List.of(loan));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		scheduler.enforceCutoff();

		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		verify(losProvider).sendDecision(loan, "N", null);
	}

	@Test
	void cutoff_withoutTheBankHandoff_leavesAcceptedLoansToSambat() {
		// Accepting already sent /customer-accepted, so Sambat will disburse, and
		// nothing can tell them we expired it: the customer would be told
		// "expired" about a loan that is then paid out to them.
		ReflectionTestUtils.setField(scheduler, "graceMinutes", 0L);
		ReflectionTestUtils.setField(scheduler, "bankHandoffEnabled", false);
		PaydayLoan offer = approved(Instant.now().minus(2, ChronoUnit.HOURS));
		PaydayLoan loan = accepted(Instant.now().minus(8, ChronoUnit.HOURS));
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of(offer));
		when(repo.findByStatus(PdlStatusEnum.Accepted)).thenReturn(List.of(loan));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		scheduler.enforceCutoff();

		assertThat(offer.getStatus()).isEqualTo(PdlStatusEnum.Rejected); // never accepted: still expires
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Accepted);
		assertThat(loan.getLosStatusCode()).isNull();
		verify(repo, never()).save(same(loan));
		verify(losProvider, never()).sendDecision(same(loan), any(), any());
	}

	@Test
	void theCutoffAndTheWebhookAgreeOnWhetherThereIsABankHandoff() throws Exception {
		// One switch, one default: drifted apart, the cut-off expires accepted
		// loans that the webhook can never move on to the bank.
		Value cutoff = PdlAcceptanceScheduler.class.getDeclaredField("bankHandoffEnabled").getAnnotation(Value.class);
		Value webhook = LosWebhookService.class.getDeclaredField("bankVerificationEnabled").getAnnotation(Value.class);
		assertThat(cutoff.value()).isEqualTo(webhook.value()).endsWith(":false}");
	}

	@Test
	void reminders_stopBeforeTheCutoff() throws Exception {
		// One at the cut-off lands on the expiry itself; one after it tells an
		// offer approved after 17:00 to confirm "before 5 PM today".
		Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
		CronExpression reminder = CronExpression.parse(props.getProperty("pdl.acceptance.reminder-cron"));
		LocalDateTime cutoff = CronExpression.parse(props.getProperty("pdl.acceptance.cutoff-cron"))
				.next(LocalDate.of(2026, 11, 4).atStartOfDay());

		LocalDateTime firstFromTheCutoff = reminder.next(cutoff.minusSeconds(1));

		assertThat(firstFromTheCutoff.toLocalDate()).isAfter(cutoff.toLocalDate());
	}
}
