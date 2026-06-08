package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;

import lombok.Data;

@Data
public class CreditFacilityMaster {

	private Long creditFacId;
	private String facRefNo;
	private String relRefNo;
	private Double amtLimit;
	private String applDate;
	private int busTypeId;
	private int creditStatusId;
	private int currId;
	private Long custAcctId;
	private String deleted;
	private Instant createDate;
	private String expiryDate;
	private String grantedBy;
	private String reviewDate;
	private int activeNote;
	private Double outstandingBalance;
	private Double availableCredit;

	public Double getAvailableCredit() {
		return amtLimit - outstandingBalance;
	}

}
