package com.ezetik.kjeypapa.sbf.model;

import java.util.List;

import lombok.Data;

@Data
public class ConsolidateData {

	private CreditFacilityMaster creditFacilityMaster;
	private CustomerInformation customerInformation;
	private List<SavingAccount> savingAccounts;
	private List<LoanFacilityInfo> loanFacilityInfo;
	private Address currentAddress;

}
