package com.ezetik.kjeypapa.pdl.payload.los;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of {@code MonthlyExpenses[]}. {@code ExpenseAmount} is a plain
 * number on the wire despite swagger typing it as a {@code $ref} to an empty
 * "Number" schema — the vendor's sample sends a bare {@code 100}.
 */
@Getter
@Setter
public class MonthlyExpenseItem {

	@JsonProperty("ExpenseType")
	private String expenseType = "";

	@JsonProperty("ExpenseAmount")
	private double expenseAmount = 0;

	@JsonProperty("Currency")
	private String currency = "";
}
