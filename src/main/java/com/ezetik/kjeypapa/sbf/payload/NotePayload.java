package com.ezetik.kjeypapa.sbf.payload;

import java.time.Instant;

import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
public class NotePayload {

	private Integer id;
	private String facilityNo;
	private Double requestAmount;
	private Instant disbursementDate;
	private int notePeriod;
	private Instant repaymentDate;
	private String merchantCode;
	private String merchantName;
	private Long merchantCustAcctId;
	@Enumerated(EnumType.STRING)
	private NoteStatusEnum status;
	private boolean isMe;
	private int user;

}
