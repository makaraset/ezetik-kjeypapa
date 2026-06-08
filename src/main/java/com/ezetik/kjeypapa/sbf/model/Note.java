package com.ezetik.kjeypapa.sbf.model;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.ezetik.kjeypapa.sbf.payload.NotePayload;
import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "sbf_note")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class Note extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String facilityNo;
	private Long loanFacId;
	private Long saCustacctid;
	private String customerName;

	private Double requestAmount;

	private Instant disbursementDate;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	private NotePeriod notePeriod;

	private Instant repaymentDate;

	private String merchantCode;
	private String merchantName;
	private Long merchantCustAcctId;

	@Enumerated(EnumType.STRING)
	private NoteStatusEnum status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	private User user;

	@Column(columnDefinition = "bool default false")
	private boolean isReviewed;
	private String reviewedBy;
	private Date reviewedDate;
	private boolean isEligible;

	@Column(columnDefinition = "bool default false")
	private boolean isRejected;
	private String rejectedBy;
	private Date rejectedDate;

//	private boolean isConfirmed;
//	private String confirmedBy;
//	private Date confirmedDate;

	private boolean isApproved;
	private String approvedBy;
	private Date approvedDate;
	private String approvedComment;

	// Customer
	@Column(columnDefinition = "bool default false")
	private boolean isAccepted;
	private String acceptedBy;
	private Date acceptedDate;

	// Customer
	@Column(columnDefinition = "bool default false")
	private boolean isGoodReceived;
	private String receivedBy;
	private Date receivedDate;

	private String loanFacRefNo;

	private boolean isDelivryConfirmed;
	private String deliveryCofirmedBy;
	private Date deliveryConfirmedDate;

//	@ColumnDefault("false")
//	private boolean isSignedNoteCorrect;

	public Note(NotePayload note) {
		super();
		this.id = note.getId();
		this.facilityNo = note.getFacilityNo();
		this.requestAmount = note.getRequestAmount();
		this.merchantCode = note.getMerchantCode();
		this.merchantName = note.getMerchantName();
		this.merchantCustAcctId = note.getMerchantCustAcctId();
		this.disbursementDate = note.getDisbursementDate();
		this.repaymentDate = note.getRepaymentDate();
		this.status = note.getStatus();
	}

	Instant stringToInstant(String dateStr) {

		String pattern = "yyyy-MM-dd";
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
		LocalDateTime localDateTime = LocalDateTime.parse(dateStr, dateTimeFormatter);
		ZoneId zoneId = ZoneId.of("Asia/Phnom_Penh");
		ZonedDateTime zonedDateTime = localDateTime.atZone(zoneId);
		Instant instant = zonedDateTime.toInstant();

		return instant;

	}

}
