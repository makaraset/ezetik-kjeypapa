package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.service.LosProvider;
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

	@Test
	void cutoff_rejectsOffersApprovedBeforeTheGraceWindow() {
		PaydayLoan l = approved(Instant.now().minus(2, ChronoUnit.HOURS));
		when(repo.findByStatus(PdlStatusEnum.Approved)).thenReturn(List.of(l));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		scheduler.enforceCutoff();

		assertThat(l.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
		assertThat(l.getLosMessage()).contains("cut-off");
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

	@org.junit.jupiter.api.Test
	void cutoff_alsoExpiresAcceptedLoansPastGrace() {
		// QB4.1: Re-Attempt is disabled once the cut-off passes — an Accepted
		// loan whose bank hand-off never succeeded expires like an Approved one.
		org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "graceMinutes", 0L);
		com.ezetik.kjeypapa.pdl.model.PaydayLoan loan = new com.ezetik.kjeypapa.pdl.model.PaydayLoan();
		loan.setId(9);
		loan.setStatus(com.ezetik.kjeypapa.pdl.model.PdlStatusEnum.Accepted);
		loan.setLosApplicationNo("LOS-9");
		loan.setApprovedDate(java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS));
		org.mockito.Mockito.when(repo.findByStatus(com.ezetik.kjeypapa.pdl.model.PdlStatusEnum.Approved))
				.thenReturn(java.util.List.of());
		org.mockito.Mockito.when(repo.findByStatus(com.ezetik.kjeypapa.pdl.model.PdlStatusEnum.Accepted))
				.thenReturn(java.util.List.of(loan));
		org.mockito.Mockito.when(repo.save(org.mockito.ArgumentMatchers.any()))
				.thenAnswer(inv -> inv.getArgument(0));

		scheduler.enforceCutoff();

		org.assertj.core.api.Assertions.assertThat(loan.getStatus())
				.isEqualTo(com.ezetik.kjeypapa.pdl.model.PdlStatusEnum.Rejected);
		org.mockito.Mockito.verify(losProvider).sendDecision(loan, "N", null);
	}
}
