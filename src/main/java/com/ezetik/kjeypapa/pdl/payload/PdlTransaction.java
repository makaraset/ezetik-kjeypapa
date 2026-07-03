package com.ezetik.kjeypapa.pdl.payload;

import java.time.Instant;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A PDL application as listed in the app's "My Application" tab.
 * Built via the JPQL constructor expression (mirror {@code NoteTransaction}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PdlTransaction {

	private Integer id;
	private String losApplicationNo;
	private Double requestAmount;
	private Instant applicationDate;
	private PdlStatusEnum status;
	private String losStatusCode; // R-LPO / R-AO / ...
	private String message; // user-facing message from LOS
	private Instant createdAt;

	public PdlTransaction(PaydayLoan p) {
		this.id = p.getId();
		this.losApplicationNo = p.getLosApplicationNo();
		this.requestAmount = p.getRequestAmount();
		this.applicationDate = p.getApplicationDate();
		this.status = p.getStatus();
		this.losStatusCode = p.getLosStatusCode();
		this.message = p.getLosMessage();
		this.createdAt = p.getCreatedAt();
	}
}
