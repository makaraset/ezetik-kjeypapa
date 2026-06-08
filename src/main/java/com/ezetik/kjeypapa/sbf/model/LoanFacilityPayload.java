package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class LoanFacilityPayload {

	private int creditTypeId;
	private String remarks;
	private int busTypeId;
	private int empRepId;
	private String catId;
}
