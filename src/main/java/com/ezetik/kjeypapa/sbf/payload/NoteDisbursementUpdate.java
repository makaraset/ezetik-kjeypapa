package com.ezetik.kjeypapa.sbf.payload;

import java.time.Instant;

import lombok.Data;

@Data
public class NoteDisbursementUpdate {

	int noteId;
	private Instant disbursementDate;
	private Instant repyamentDate;

}
