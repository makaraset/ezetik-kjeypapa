package com.ezetik.kjeypapa.pdl.payload.los;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of {@code LoanUtilizationProject[]}.
 *
 * <p>{@code UltilizationCategory} is misspelled in SBF's schema. That spelling
 * IS the contract — correcting it silently drops the field on the wire.
 */
@Getter
@Setter
public class LoanUtilizationProjectItem {

	@JsonProperty("UltilizationCategory")
	private String ultilizationCategory = "";

	@JsonProperty("TotalUnit")
	private int totalUnit = 0;

	@JsonProperty("UnitPrice")
	private double unitPrice = 0;

	@JsonProperty("SambatLoan")
	private double sambatLoan = 0;
}
