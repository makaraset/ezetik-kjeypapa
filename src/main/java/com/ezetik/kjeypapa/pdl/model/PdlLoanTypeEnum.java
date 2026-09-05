package com.ezetik.kjeypapa.pdl.model;

/**
 * Loan product selected on the V8 request wizard (screen 11).
 *
 * Per Sambat's 2026-08-13 answers (QC1.1) this release is PAYDAY-only; MICRO
 * and PERSONAL are shown disabled ("coming soon") in the app but reserved here
 * so the enum/wire contract doesn't change when they launch.
 */
public enum PdlLoanTypeEnum {

	PAYDAY, MICRO, PERSONAL
}
