package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;

import lombok.Data;

@Data
public class LoanFacilityInfo {

	private Long loanFacId;
	private String loanFacRefNo;
	private String facRefNo;
	private Double loanPrincipal;
	private Instant entryDate;
	private Instant maturityDate;
	private int dayDue;
	private int loanStatus;
	private int aging;
	private Double outstanding;
}
