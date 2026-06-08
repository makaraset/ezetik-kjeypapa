package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;
import java.util.List;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.fasterxml.jackson.annotation.JsonIdentityReference;

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

@Data
@Entity
@Table(name = "ez_note_attachment")
@EqualsAndHashCode(callSuper = true)
public class NoteAttachment extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JsonIdentityReference(alwaysAsId = true)
	private Note note;

	@Enumerated(EnumType.STRING)
	private DocumentTypeEnum docType;

	@OneToMany
	private List<Image> attachFiles;

	private boolean isReviewed;
	private boolean isCorrect;
	private String reason;
	private String reviewedBy;
	private Instant reviewedDate;
	private boolean isDeleted;
	private boolean isRework;

}
