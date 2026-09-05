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
	Disbursed, Active, Closed, Revoked
}
