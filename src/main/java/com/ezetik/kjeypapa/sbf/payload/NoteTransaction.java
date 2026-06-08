package com.ezetik.kjeypapa.sbf.payload;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteTransaction {

	private int id;
	private String facilityNo;
	private String loanFacRefNo;
	private Double requestAmount;
	private Instant disbursementDate;
	private String notePeriod;
	private Instant repaymentDate;
	private String merchant;
	private NoteStatusEnum status;
	private int aging;
	private Instant createdAt;
	private Date rejectDate;
	private boolean isAccepted;
	private boolean isUpdateDisRequired;
	private NoteAttachmentResponse purchaseOrder;
	private NoteAttachmentResponse deliveryNote;

	public NoteTransaction(Note n) {
		super();
		this.id = n.getId();
		this.facilityNo = n.getFacilityNo();
		this.loanFacRefNo = n.getLoanFacRefNo();
		this.requestAmount = n.getRequestAmount();
		this.disbursementDate = n.getDisbursementDate();
		this.notePeriod = n.getNotePeriod().getDescription();
		this.repaymentDate = n.getRepaymentDate();
		this.merchant = n.getMerchantName();
		this.status = n.getStatus();
		this.aging = 0;
		this.createdAt = n.getCreatedAt();
		this.rejectDate = n.getRejectedDate();
		this.isAccepted = n.isAccepted();
		this.purchaseOrder = null;
		this.deliveryNote = null;
//		Instant validDate = Instant.now().plus(2, ChronoUnit.DAYS);
		Instant validDate = Instant.now().truncatedTo(ChronoUnit.DAYS);
		if (validDate.isAfter(n.getDisbursementDate())) {
			this.isUpdateDisRequired = true;
		} else {
			this.isUpdateDisRequired = false;
		}
	}

}
