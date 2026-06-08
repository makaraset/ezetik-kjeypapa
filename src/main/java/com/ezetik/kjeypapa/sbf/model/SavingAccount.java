package com.ezetik.kjeypapa.sbf.model;

import java.math.BigDecimal;

import com.ezetik.kjeypapa.security.model.GenderEnum;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
public class SavingAccount {

	private Long custacctid;
	private String accountNo;
	private Long cifNo;
	private String cifName;
	private int branchId;
	private String names;

	@Enumerated(EnumType.STRING)
	private GenderEnum sex;

	private String ccy;
	private String product;
	private String jointType;
	private BigDecimal intRate;
	private BigDecimal balance;
	private BigDecimal freezedamt;
	private BigDecimal valBalance;
	private String accStatus;
	private String openDate;
}
