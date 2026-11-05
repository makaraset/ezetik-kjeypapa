package com.ezetik.kjeypapa.pdl.payload.los;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/** One row of {@code MonthlyIncomes[]}. */
@Getter
@Setter
public class MonthlyIncomeItem {

	@JsonProperty("IncomeType")
	private String incomeType = "";

	@JsonProperty("IncomeAmount")
	private double incomeAmount = 0;

	@JsonProperty("Currency")
	private String currency = "";
}
