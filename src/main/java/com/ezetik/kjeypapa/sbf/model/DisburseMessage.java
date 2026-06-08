package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class DisburseMessage {

	private String statusCode;
	private String message;
	private Long tranxId;

}
