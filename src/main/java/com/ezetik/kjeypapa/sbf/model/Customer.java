package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Customer {

	private int noteId;
	private String customerName;
	private Double noteAmount;
	private Instant disbursementDate;
	private Instant repaymentDate;
	private NoteStatusEnum noteStatus;
	private NoteAttachmentResponse purchaseOrder;
	private NoteAttachmentResponse deliveryNote;

	public Customer(Note note) {
		super();
		this.noteId = note.getId();
		this.customerName = note.getUser().getLastname() + " " + note.getUser().getFirstname();
		this.noteAmount = note.getRequestAmount();
		this.disbursementDate = note.getDisbursementDate();
		this.repaymentDate = note.getRepaymentDate();

		Instant validDate = Instant.now().truncatedTo(ChronoUnit.DAYS);
		if (note.getStatus().equals(NoteStatusEnum.Approved) && validDate.isAfter(note.getDisbursementDate())) {
			this.noteStatus = NoteStatusEnum.Expired;
		} else {
			this.noteStatus = note.getStatus();
		}
	}

}
