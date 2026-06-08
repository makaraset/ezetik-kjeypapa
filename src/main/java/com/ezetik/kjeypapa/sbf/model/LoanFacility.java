package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class LoanFacility {
	private Long loanFacId;
	private String loanFacRefNo;
	private Long creditFacId;
	private int creditTypeId;
	private String isRollOver;
	private Double loanPrincipal;
	private int currId;
	private String entryDate;
	private String reviewDate;
	private String expiryDate;
	private String remarks;
	private String deleted;
	private int creditStatusId;
	private int busTypeId;
	private String dealerId;
	private int empRepId;
	private String catId;
	private String externalId;
	private String referee;
	private String applicationNo;
	private String cbcEnquiryReference;
	private int specialNoteId;
}
