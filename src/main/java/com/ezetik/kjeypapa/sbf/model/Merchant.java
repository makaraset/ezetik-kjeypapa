package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class Merchant {

	private String merchantCode;
	private String merchantName;
	private String merchantKhName;
	private Long merchantCustAcctId;
	private String merchantSetlement;
	private String phoneNumer;
	private String email;
	private String bankName;
	private String accountNo;
	private String benefitciaryName;

}
