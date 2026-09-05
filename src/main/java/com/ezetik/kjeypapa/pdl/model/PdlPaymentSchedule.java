package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

import jakarta.persistence.Entity;
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

/**
 * One installment row of a PDL repayment schedule.
 *
 * Populated from the schedule that Sambat LOS generates and pushes on approval
 * (BRS 2.6) — the backend stores it; it does NOT compute interest/installments.
 */
@Data
@Entity
@Table(name = "pdl_payment_schedule")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PdlPaymentSchedule extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JsonIdentityReference(alwaysAsId = true)
	private PaydayLoan pdl;

	private Integer installmentNo;
	private Instant dueDate;
	private Double principalDue;
	private Double interestDue;
	private Double feeDue;
	private Double otherDue;
	private Double totalDue;
	// Paid breakdown (LOS-pushed on repayment; nullable until paid)
	private Double principalPaid;
	private Double interestPaid;
	private Double feePaid;
	private Double penaltyPaid;
	private Double otherPaid; // V8 closed-loan settlement card (G17)
	private Double totalPaid;
	private Double amountPaid;
	private Instant paidDate;
	private Instant transactionDate;
	private String status; // PENDING / PAID / OVERDUE

}
