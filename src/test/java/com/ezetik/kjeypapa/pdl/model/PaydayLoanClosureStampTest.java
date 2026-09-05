package com.ezetik.kjeypapa.pdl.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code closedDate} is where a retention clock starts, so the rule that writes
 * it is worth pinning: a wrong one either destroys evidence early or keeps a
 * loan out of retention forever.
 *
 * <p>In the entity's own package because {@code stampClosure()} is a JPA
 * lifecycle callback, not API. Widening it to public to suit a test would
 * advertise it as something callers may invoke, which is exactly what it is not.
 */
class PaydayLoanClosureStampTest {

	private PaydayLoan loanAt(PdlStatusEnum status) {
		PaydayLoan l = new PaydayLoan();
		l.setStatus(status);
		return l;
	}

	@Test
	@DisplayName("reaching a terminal state stamps the closure date")
	void terminalStatesAreStamped() {
		for (PdlStatusEnum s : new PdlStatusEnum[] { PdlStatusEnum.Closed, PdlStatusEnum.Rejected,
				PdlStatusEnum.Revoked }) {
			PaydayLoan l = loanAt(s);
			l.stampClosure();
			assertThat(l.getClosedDate()).as(s.name()).isNotNull();
		}
	}

	@Test
	@DisplayName("a live loan never carries an end date")
	void liveStatesAreNotStamped() {
		// Disbursed and Active are the middle of a loan's life. Stamping either
		// would start a retention clock against a loan still being repaid.
		for (PdlStatusEnum s : new PdlStatusEnum[] { PdlStatusEnum.Draft, PdlStatusEnum.Submitted,
				PdlStatusEnum.Approved, PdlStatusEnum.Accepted,
				PdlStatusEnum.Pending_Bank_Verification, PdlStatusEnum.Disbursed,
				PdlStatusEnum.Active }) {
			PaydayLoan l = loanAt(s);
			l.stampClosure();
			assertThat(l.getClosedDate()).as(s.name()).isNull();
		}
	}

	@Test
	@DisplayName("the first closure is the one that counts")
	void theDateIsNotOverwritten() {
		// Every later save re-runs this callback. Re-stamping would push the
		// retention clock forward each time anything touched the row.
		Instant closedLongAgo = Instant.now().minus(400, ChronoUnit.DAYS);
		PaydayLoan l = loanAt(PdlStatusEnum.Closed);
		l.setClosedDate(closedLongAgo);

		l.stampClosure();
		l.stampClosure();

		assertThat(l.getClosedDate()).isEqualTo(closedLongAgo);
	}

	@Test
	@DisplayName("a loan that leaves a terminal state loses its end date")
	void reopeningClearsTheDate() {
		// An open file has no end. A stale date left behind would start a
		// retention clock against a loan back in use.
		PaydayLoan l = loanAt(PdlStatusEnum.Rejected);
		l.stampClosure();
		assertThat(l.getClosedDate()).isNotNull();

		l.setStatus(PdlStatusEnum.Approved);
		l.stampClosure();

		assertThat(l.getClosedDate()).isNull();
	}

	@Test
	@DisplayName("terminal is defined in one place")
	void terminalIsDefinedOnce() {
		// The stamp and consent retention both ask the enum. If they asked
		// separately they could disagree, and a status counted as terminal by
		// one and not the other is the worst of both.
		assertThat(PdlStatusEnum.Closed.isTerminal()).isTrue();
		assertThat(PdlStatusEnum.Rejected.isTerminal()).isTrue();
		assertThat(PdlStatusEnum.Revoked.isTerminal()).isTrue();
		assertThat(PdlStatusEnum.Disbursed.isTerminal()).isFalse();
		assertThat(PdlStatusEnum.Active.isTerminal()).isFalse();
	}
}
