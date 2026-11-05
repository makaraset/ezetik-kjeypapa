package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;

import com.ezetik.kjeypapa.security.audit.DateAudit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * One CamDigi identity-verification attempt for an account request: the
 * challenge issued, and what came of it. Table created by migration 008.
 *
 * <p>Never holds the capture or the ID image. Both are biometric data and are
 * passed through to CamDigi, not stored, until SBF says otherwise.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "pdl_identity_verification")
public class PdlIdentityVerification extends DateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** A plain column, not an association: nothing to lazy-load outside a transaction. */
	@Column(nullable = false)
	private Integer accountRequestId;

	// A converter, not @Enumerated: Hibernate generates a CHECK constraint for
	// an @Enumerated column it creates, and a later outcome would violate it —
	// the trap migration 002 had to undo. These outcomes are expected to grow.
	@Convert(converter = StatusConverter.class)
	@Column(nullable = false, length = 20)
	private PdlIdentityVerificationStatusEnum status;

	/** sha256 of the challenge id. The id itself is only ever held by the app. */
	@Column(nullable = false, length = 64, unique = true)
	private String challengeHash;

	/** The challenge actions, comma-separated, in the order they were asked for. */
	@Column(nullable = false)
	private String actions = "";

	@Column(nullable = false)
	private Instant challengeExpiresAt;

	private Instant submittedAt;
	private Instant completedAt;

	private Integer httpStatus;

	/** CamDigi's {@code error} field: 0 means they verified. */
	private Integer sbfError;

	@Column(length = 500)
	private String sbfMessage;

	/** {@link com.ezetik.kjeypapa.pdl.service.SbfCallException.Kind}, or a local reason. */
	@Column(length = 30)
	private String errorKind;

	/** CamDigi's reply with every long string elided, since it may echo images. */
	@Column(columnDefinition = "text")
	private String responseExcerpt;

	private Integer latencyMs;
	private Integer livenessBytes;

	@Column(length = 64)
	private String clientIp;

	/** Passed by the mock, not by CamDigi. Never counts once the mock is off. */
	@Column(nullable = false)
	private boolean mock;

	/** Stores the enum by name, with no generated CHECK constraint. */
	@Converter
	public static class StatusConverter implements AttributeConverter<PdlIdentityVerificationStatusEnum, String> {

		@Override
		public String convertToDatabaseColumn(PdlIdentityVerificationStatusEnum s) {
			return s == null ? null : s.name();
		}

		@Override
		public PdlIdentityVerificationStatusEnum convertToEntityAttribute(String s) {
			return s == null ? null : PdlIdentityVerificationStatusEnum.valueOf(s);
		}
	}
}
