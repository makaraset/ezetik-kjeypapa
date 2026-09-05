package com.ezetik.kjeypapa.pdl.model;

/**
 * Payday Loan application/loan status.
 *
 * PDL has NO "Rework" status — a rework request is treated as a Rejection
 * (BRS 2.5.1). Add a {@code Rework} value only when MSIL/PL are introduced.
 */
public enum PdlStatusEnum {

	Draft, Submitted, Rejected, Approved, Accepted,
	// V21: after Accept, the applicant is handed off to the Bank Mobile App to
	// verify the payroll account + consent to disbursement/auto-debit. This gate
	// sits between Accepted and Disbursed. Reserved — the hand-off + callback are
	// pending the Sambat/Bank contract (Q4); enable via pdl.bank-verification.enabled.
	Pending_Bank_Verification,
	Disbursed, Active, Closed, Revoked;

	/**
	 * Whether the loan file is over.
	 *
	 * <p>Defined once, here, because two things depend on it and they must not
	 * drift: {@code PaydayLoan.stampClosure()} decides when to record an end
	 * date, and consent retention decides what may eventually be disposed of. A
	 * status counted as terminal by one and not the other would either start a
	 * retention clock on a live loan or never start one at all.
	 *
	 * <p>{@code Disbursed} and {@code Active} are deliberately NOT terminal —
	 * they are the middle of a loan's life, not its end.
	 */
	public boolean isTerminal() {
		return this == Closed || this == Rejected || this == Revoked;
	}
}
