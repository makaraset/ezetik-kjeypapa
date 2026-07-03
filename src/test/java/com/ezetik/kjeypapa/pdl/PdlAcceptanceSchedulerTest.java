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
		verify(losProvider).sendDecision("LOS-1", "N", null);
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
}
