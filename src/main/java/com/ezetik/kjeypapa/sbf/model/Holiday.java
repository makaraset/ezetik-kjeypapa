package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class Holiday {

	private Long holidayDate;
	private String description;
	private String months;
	private String years;
	private String status;

}
