package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;

import com.ezetik.kjeypapa.security.model.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteMerge {

	private Integer id;
	private String facilityNo;
	private Double requestAmount;
	private Instant disbursementDate;
	private NotePeriod notePeriod;
	private Instant repyamentDate;
	private Merchant merchant;
	private NoteStatusEnum status;
	private User user;
	private boolean isReviewed;
	private String reviewedBy;
	private Instant reviewedDate;
	private boolean isEligible;
	private boolean isRejected;
	private String rejectedBy;
	private Instant rejectedDate;
	private boolean isConfirmed;
	private String confirmedBy;
	private Instant confirmedDate;
	private boolean isApproved;
	private String approvedBy;
	private Instant approvedDate;
	private boolean isDelivryConfirmed;
	private String deliveryCofirmedBy;
	private Instant deliveryConfirmedDate;

	private String loanFacRefNo;

}
