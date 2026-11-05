package com.ezetik.kjeypapa.pdl.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App → backend body for capturing the applicant's bank account
 * (signup mockup 12.1.4 / My Profile edit). Request DTO ONLY — omits {@code id},
 * {@code user}, the server-set {@code verified*} fields, and {@code consentGiven}
 * (direct-debit consent is recorded at loan level via PaydayLoan.bankConsent).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankInfoRequest {

	private String bankName;
	private String accountName;
	private String accountNo;
	private String currency;
	private String bankStatementFileRef;
}
