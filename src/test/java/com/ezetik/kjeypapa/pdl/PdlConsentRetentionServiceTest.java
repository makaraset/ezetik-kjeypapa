package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.repository.PdlCbcConsentRepository;
import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService;
import com.ezetik.kjeypapa.pdl.service.PdlConsentRetentionService.RetentionReport;

/**
 * Retention decides when evidence may stop existing, so the interesting tests
 * are all about refusing to name something a candidate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlConsentRetentionServiceTest {

	@Mock PdlCbcConsentRepository consentRepo;
	@InjectMocks PdlConsentRetentionService service;

	private static final Instant LONG_AGO = Instant.now().minus(3650, ChronoUnit.DAYS);

	private PdlCbcConsent consentOn(PdlStatusEnum status, Instant ended) {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(7);
		loan.setStatus(status);
		loan.setUpdatedAt(ended);

		PdlCbcConsent c = new PdlCbcConsent();
		c.setId(1);
		c.setConsentRef("CBC-7-v3-2026-09");
		c.setLoan(loan);
		return c;
	}

	private void years(String v) {
		ReflectionTestUtils.setField(service, "retentionYears", v);
	}

	@Test
	@DisplayName("with no period configured, nothing ever expires")
	void unsetPeriodExpiresNothing() {
		// The default, and deliberately so: the period is unconfirmed, and a
		// blank config must not quietly mean zero.
		years("");
		when(consentRepo.findAllByOrderByIdAsc())
				.thenReturn(List.of(consentOn(PdlStatusEnum.Closed, LONG_AGO)));

		RetentionReport r = service.report();

		assertThat(r.configured()).isFalse();
		assertThat(r.candidates()).isEmpty();
		assertThat(r.stillRetained()).isEqualTo(1);
		assertThat(r.notes()).anyMatch(n -> n.contains("no consent ever expires"));
	}

	@Test
	@DisplayName("a nonsensical period is treated as unset, not as zero")
	void brokenPeriodIsTreatedAsUnset() {
		// '0' or 'six' must not make every finished loan's consent disposable
		// the moment it closes.
		when(consentRepo.findAllByOrderByIdAsc())
				.thenReturn(List.of(consentOn(PdlStatusEnum.Closed, LONG_AGO)));

		for (String bad : List.of("0", "-5", "six", " ")) {
			years(bad);
			assertThat(service.report().configured()).as(bad).isFalse();
		}
	}

	@Test
	@DisplayName("a live loan is never a candidate, however old")
	void liveLoansAreNeverCandidates() {
		// The rule is the life of the loan FILE, not the age of the consent. A
		// disbursed loan from years ago is still in use.
		years("1");
		when(consentRepo.findAllByOrderByIdAsc()).thenReturn(List.of(
				consentOn(PdlStatusEnum.Disbursed, LONG_AGO),
				consentOn(PdlStatusEnum.Active, LONG_AGO),
				consentOn(PdlStatusEnum.Approved, LONG_AGO),
				consentOn(PdlStatusEnum.Submitted, LONG_AGO)));

		RetentionReport r = service.report();

		assertThat(r.candidates()).isEmpty();
		assertThat(r.stillRetained()).isEqualTo(4);
	}

	@Test
	@DisplayName("a finished loan past the period is a candidate, with its reasoning")
	void finishedAndElapsedIsACandidate() {
		years("1");
		when(consentRepo.findAllByOrderByIdAsc())
				.thenReturn(List.of(consentOn(PdlStatusEnum.Closed, LONG_AGO)));

		RetentionReport r = service.report();

		assertThat(r.candidates()).hasSize(1);
		var c = r.candidates().get(0);
		assertThat(c.consentId()).isEqualTo(1);
		assertThat(c.loanStatus()).isEqualTo("Closed");
		assertThat(c.eligibleFrom()).isBefore(Instant.now());
		// The date is derived, and the report has to say so rather than let an
		// approximation read as a recorded fact.
		assertThat(c.loanEndedSource()).contains("approximate");
	}

	@Test
	@DisplayName("a finished loan inside the period is still retained")
	void finishedButRecentIsRetained() {
		years("7");
		when(consentRepo.findAllByOrderByIdAsc()).thenReturn(
				List.of(consentOn(PdlStatusEnum.Closed, Instant.now().minus(30, ChronoUnit.DAYS))));

		RetentionReport r = service.report();

		assertThat(r.candidates()).isEmpty();
		assertThat(r.stillRetained()).isEqualTo(1);
	}

	@Test
	@DisplayName("where two end dates could serve, the later one starts the clock")
	void theLaterEndDateWins() {
		// Holding a record slightly too long is recoverable. Disposing of it
		// slightly too early is not.
		years("1");
		Instant transacted = Instant.now().minus(800, ChronoUnit.DAYS);
		Instant touched = Instant.now().minus(200, ChronoUnit.DAYS);
		PdlCbcConsent c = consentOn(PdlStatusEnum.Closed, touched);
		c.getLoan().setLastTransactionDate(transacted);

		RetentionReport r = service.report();

		// 200 days after the later date is not yet a year, so nothing is due.
		assertThat(r.candidates()).isEmpty();
	}

	@Test
	@DisplayName("a revocation uses the date it was actually revoked")
	void revokedUsesItsOwnDate() {
		years("1");
		PdlCbcConsent c = consentOn(PdlStatusEnum.Revoked, Instant.now());
		// java.sql.Date, and its toInstant() throws — the service must not call it.
		c.getLoan().setRevokedDate(new java.sql.Date(LONG_AGO.toEpochMilli()));
		when(consentRepo.findAllByOrderByIdAsc()).thenReturn(List.of(c));

		RetentionReport r = service.report();

		assertThat(r.candidates()).hasSize(1);
		assertThat(r.candidates().get(0).loanEndedSource()).isEqualTo("revokedDate");
	}

	@Test
	@DisplayName("the report always says it has deleted nothing")
	void theReportIsExplicitlyNonDestructive() {
		// The list is read by someone deciding whether to dispose of evidence.
		// It must not be mistakable for a record of disposal already done.
		years("1");
		when(consentRepo.findAllByOrderByIdAsc())
				.thenReturn(List.of(consentOn(PdlStatusEnum.Closed, LONG_AGO)));

		assertThat(service.report().notes())
				.anyMatch(n -> n.contains("Nothing here has been or will be deleted"));
	}
}
