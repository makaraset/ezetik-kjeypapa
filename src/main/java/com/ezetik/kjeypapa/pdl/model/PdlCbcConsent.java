package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * One CBC consent, as evidence.
 *
 * <p>Consent used to live as six columns on {@link PaydayLoan} — the most
 * frequently rewritten row in the schema, since every inbound Sambat webhook
 * saves it. Evidence does not belong on a row that changes all day, so it lives
 * here instead: <b>append-only</b>, never updated, never deleted, and chained
 * so that altering one row is detectable.
 *
 * <p>Two things enforce that. Every field is {@code updatable = false} and the
 * class is {@link Immutable}, so Hibernate will not emit an {@code UPDATE} even
 * if someone calls {@code save} on a managed instance. A database trigger
 * (migration 003) then rejects {@code UPDATE}, {@code DELETE} and
 * {@code TRUNCATE} outright, because the application connects as a superuser
 * and privilege-based protection would be silently bypassed.
 *
 * <p>Every text column is non-null and defaults to the empty string. One
 * encoding for "not captured" is what lets fields be populated by later work
 * without changing the canonical form, and so without invalidating hashes
 * written before that work landed.
 */
@Data
@Entity
@Immutable
@Table(name = "pdl_cbc_consent")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PdlCbcConsent extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	@JoinColumn(name = "loan_id", updatable = false)
	private PaydayLoan loan;

	/** Denormalised so the record reads without joining the loan. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIdentityReference(alwaysAsId = true)
	@JoinColumn(name = "user_id", updatable = false)
	private User user;

	/** Which canonical form {@code rowHash} was computed under. */
	@Column(length = 8, nullable = false, updatable = false)
	private String canonVersion;

	@Column(length = 64, nullable = false, updatable = false)
	private String consentRef;

	/** Stored, not inferred from the presence of a row. */
	@Column(nullable = false, updatable = false)
	private boolean consentGiven;

	/** Truncated to microseconds, the precision Postgres keeps. */
	@Column(nullable = false, updatable = false)
	private Instant consentDate;

	@Column(length = 32, nullable = false, updatable = false)
	private String textVersion;

	/** SHA-256 of the wording agreed to — the version names it, this proves it. */
	@Column(length = 64, nullable = false, updatable = false)
	private String textHash;

	@Column(length = 8, nullable = false, updatable = false)
	private String textLanguage;

	@Column(length = 32, nullable = false, updatable = false)
	private String channel;

	// ----- frozen copies of what the form printed -----

	@Column(length = 128, nullable = false, updatable = false)
	private String formReference;

	@Column(length = 30, nullable = false, updatable = false)
	private String idNo;

	@Column(length = 255, nullable = false, updatable = false)
	private String customerNameKm;

	@Column(length = 255, nullable = false, updatable = false)
	private String customerNameLatin;

	// ----- the archived document (populated by the archiving step) -----

	@Column(length = 128, nullable = false, updatable = false)
	private String documentRef;

	@Column(length = 64, nullable = false, updatable = false)
	private String documentHash;

	// ----- how the consent was captured (populated by the context step) -----

	@Column(length = 32, nullable = false, updatable = false)
	private String appVersion;

	@Column(length = 64, nullable = false, updatable = false)
	private String deviceModel;

	@Column(length = 45, nullable = false, updatable = false)
	private String ipAddress;

	/** {@code SUBMIT} for a consent captured as it happened, or the backfill that reconstructed it. */
	@Column(length = 32, nullable = false, updatable = false)
	private String captureSource;

	/**
	 * Who recorded this consent.
	 *
	 * <p>Deliberately not {@code createdBy} from {@link UserDateAudit}, even
	 * though the two normally agree. {@code createdBy} belongs to Spring's
	 * auditing listener, which writes it on flush — <i>after</i> the row hash has
	 * been computed. Hashing it produced a ledger where nothing verified, because
	 * every row on disk differed from the row that was hashed. This column is
	 * written by the ledger and by nothing else, so what is hashed is what lands.
	 */
	@Column(length = 64, nullable = false, updatable = false)
	private String recordedBy;

	/** Sambat's application id, when the filing returned one. */
	@Column(updatable = false)
	private Long losAppId;

	// ----- the chain -----

	@Column(length = 64, nullable = false, updatable = false)
	private String prevHash;

	@Column(length = 64, nullable = false, updatable = false)
	private String rowHash;
}
