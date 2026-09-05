package com.ezetik.kjeypapa.pdl.model;

import java.time.Instant;
import java.util.List;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A PDL document attachment (mirrors {@code NoteAttachment}); reuses the shared
 * {@code image.model.Image} storage.
 */
@Data
@Entity
@Table(name = "pdl_attachment")
@EqualsAndHashCode(callSuper = true)
public class PdlAttachment extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JsonIdentityReference(alwaysAsId = true)
	private PaydayLoan pdl;

	@Enumerated(EnumType.STRING)
	private PdlDocTypeEnum docType;

	@OneToMany
	private List<Image> attachFiles;

	// Pin JSON keys explicitly so the wire contract is stable regardless of the
	// `isXxx` naming (which differs between Jackson 2 and 3). Not exposed to a
	// client today — this reserves a contract for any future consumer.
	// See [[jackson3-boolean-contract]].
	@JsonProperty("isReviewed")
	private boolean isReviewed;
	@JsonProperty("isCorrect")
	private boolean isCorrect;
	private String reason;
	private String reviewedBy;
	private Instant reviewedDate;
	@JsonProperty("isDeleted")
	private boolean isDeleted;
	@JsonProperty("isRework")
	private boolean isRework;

}
