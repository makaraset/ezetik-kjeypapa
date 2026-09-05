package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;

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

/**
 * A customer exercising a right the CBC consent grants them.
 *
 * <p>Modelled on {@link PdlAccountRequest}: a customer-raised request, a human
 * decision, and a one-shot transition recorded with who decided and when. The
 * shape is deliberate — these requests carry a response obligation, so the
 * record has to show not just the outcome but that there was one, and when.
 *
 * <p>Unlike a consent record this is <b>not</b> append-only evidence. It is
 * working state that legitimately changes as somebody handles the request. The
 * consent it concerns stays immutable in {@link PdlCbcConsent} regardless of
 * what is decided here — which is the point of keeping them apart. A withdrawal
 * request never edits the consent it withdraws; the consent remains the true
 * record of what was agreed on the day, and the withdrawal is a separate fact
 * layered on top.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pdl_data_request")
public class PdlDataRequest extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JsonIdentityReference(alwaysAsId = true)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(length = 32, nullable = false)
	private PdlDataRequestTypeEnum type;

	@Enumerated(EnumType.STRING)
	@Column(length = 32, nullable = false)
	private PdlDataRequestStatusEnum status;

	/** What the customer actually said, in their own words. */
	@Column(length = 2000)
	private String details;

	/**
	 * The application this concerns, when the customer named one. Plain id
	 * rather than a relation: a request may outlive the loan it refers to, and a
	 * foreign key would either block that or quietly cascade.
	 */
	private Integer relatedLoanId;

	/** What was done, or why it was refused. A refusal without one is not an answer. */
	@Column(length = 2000)
	private String decisionReason;

	private String decidedBy;

	private Instant decidedDate;
}
