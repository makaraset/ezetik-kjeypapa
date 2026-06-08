package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class DisburseModel {

	private String loanRefNo;
	private Long custacctid;
	private String disburseDate;
	private String maturityDate;
	private Double disburseAmount;
//	private Double monthlyFeeAmt;
	private Double monthlyFeeRate;
	private Double interestRate;
	private Integer noOfInstallment;
	private String repaymentMethod;
	private String disburseTo;
	private String disburseBy;
	private String authBy;
	private String doneBy;

}