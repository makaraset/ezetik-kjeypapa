package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The customer's Kjey PAPA settlement account + live balance (V8 screen 26,
 * G20). Balance source = SBF core banking (Sambat QC3.1); until the balance
 * API is provided this serves a MOCK balance ({@code mock=true}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlSettlementAccountResponse {

	private String accountNo;
	private String accountName;
	private String currency;
	private Double balance;
	private Instant asOf;
	private boolean mock;
}
