package com.ezetik.kjeypapa.pdl.model;

import java.sql.Date;
import java.time.Instant;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 * A Payday Loan (PDL) application / loan.
 *
 * Unlike the SBF {@code Note}, PDL has NO merchant, facility, or purchase-order.
 * Decisioning, the loan contract and the repayment schedule are owned by Sambat
 * LOS (TurnKey); this entity stores the LOS-pushed data and the customer-side
 * lifecycle. See {@code docs/ARCHITECTURE.md} and {@code plans/PDL_IMPLEMENTATION.md}.
 */
@Data
@Entity
@Table(name = "pdl_payday_loan")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PaydayLoan extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	private User user;

	private String customerName;

	// --- Loan details (mockup 17.6) ---
	private Double requestAmount;
	private Double repaymentAmount;
	private Double interestAmount;
	private Double processingFee;
	private Integer loanPeriodDays;

	// --- V26/V8 quote fields (Sambat 2026-08-13 QC1.3; computed server-side at
	// create, overridable by LOS-pushed values on approval) ---
	@Enumerated(EnumType.STRING)
	private PdlLoanTypeEnum loanType; // PAYDAY-only this release (QC1.1)
	private Double interestRatePercent; // monthly, pro-rated by period
	private Double cbcEnquiryFee;
	private Double netDisbursedAmount; // credited = principal − processing − CBC
	private Instant disbursementDate;
	private Instant repaymentDate;
	private Instant applicationDate;
	private Instant approvedDate; // when LOS approved — drives the acceptance cut-off (V21)

	@Enumerated(EnumType.STRING)
	private PdlStatusEnum status;

	// --- Applicant profile (no merchant / facility / PO) ---
	@ManyToOne(fetch = FetchType.LAZY)
	private PdlEmploymentInfo employmentInfo;

	@ManyToOne(fetch = FetchType.LAZY)
	private PdlBankInfo bankInfo;

	private String cbcConsentRef;
	// Generated consent record (QC4.2): stamped at submit; served by
	// GET /pdl/{id}/cbc-consent for the app's "CBC Consent [View]".
	private Instant cbcConsentDate;
	private String cbcConsentTextVersion;

	/**
	 * SHA-256 of the exact consent wording the customer accepted. The version
	 * label says WHICH text; the hash PROVES it, and keeps proving it if a
	 * label is ever reused or the stored wording edited.
	 */
	@Column(length = 64)
	private String cbcConsentTextHash;

	/** Language the consent was displayed and agreed in ("km" / "en"). */
	@Column(length = 8)
	private String cbcConsentLanguage;

	/** How consent was captured — "MOBILE_APP" for the in-app tick box. */
	@Column(length = 32)
	private String cbcConsentChannel;

	@Column(columnDefinition = "bool default false")
	private boolean bankConsent;

	// --- Sambat LOS sync (contract TBD per BRS Appendices 1-6) ---
	private String losApplicationNo;

	/**
	 * LOS's own {@code AppId} from the submit response, kept beside
	 * {@code losApplicationNo} (their {@code AppRefId}) because it is not yet
	 * settled which of the two their callbacks will quote.
	 */
	private Long losAppId;
	private String losStatusCode; // R-LPO / R-AO / ...
	private String losMessage;
	private String loanFormRef; // LOS-pushed generated documents (stored via image/)
	private String loanContractFileRef;
	private String repaymentScheduleRef;
	private String disbursementTxnId;

	// --- LOS-pushed active-loan detail (My Loan card; nullable until pushed) ---
	private String loanRefNo;
	private String settlementAccountNo;
	private String currency;
	private Integer tenor; // number of installments
	private Double outstandingAmount;
	private Double overduePayment;
	private Integer daysPastDue;
	private Double lastPaidAmount;
	private Instant lastTransactionDate;
	private String loanDocRef;

	// --- Customer decision / audit ---
	// Pin the wire key to "accepted"/"revoked" — the Flutter model's primary key
	// (= the old Jackson-2 behavior). Jackson 3 otherwise keeps the full "isAccepted"
	// name, which only hits the app's fallback key; pinning removes the version
	// ambiguity. See [[jackson3-boolean-contract]].
	@Column(columnDefinition = "bool default false")
	@JsonProperty("accepted")
	private boolean isAccepted;
	private String acceptedBy;
	private Date acceptedDate;
	private String signedContractRef;

	@Column(columnDefinition = "bool default false")
	@JsonProperty("revoked")
	private boolean isRevoked;
	private String revokedBy;
	private Date revokedDate;
	private String revokeReason;

}
