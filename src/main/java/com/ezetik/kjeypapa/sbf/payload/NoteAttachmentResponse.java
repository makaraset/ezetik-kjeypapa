package com.ezetik.kjeypapa.sbf.payload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.NoteAttachment;

import lombok.Data;

@Data
public class NoteAttachmentResponse {

	private int id;
	private int noteId;
	private DocumentTypeEnum docType;
	private boolean isReviewed;
	private boolean isCorrect;
	private String reason;
	private String reviewedBy;
	private Instant reviewedDate;
	private boolean isDeleted;
	private String createdBy;
	private Instant createdAt;
	private List<ImageResponse> attachFiles;

	public NoteAttachmentResponse(NoteAttachment ns) {
		super();
		this.id = ns.getId();
		this.noteId = ns.getNote().getId();
		this.docType = ns.getDocType();
		this.isReviewed = ns.isReviewed();
		this.isCorrect = ns.isCorrect();
		this.reason = ns.getReason();
		this.reviewedBy = ns.getReviewedBy();
		this.reviewedDate = ns.getReviewedDate();
		this.isDeleted = ns.isDeleted();
		this.createdBy = ns.getCreatedBy();
		this.createdAt = ns.getCreatedAt();
		this.attachFiles = new ArrayList<>();
		for (Image img : ns.getAttachFiles()) {
			this.attachFiles.add(new ImageResponse(img));
		}

	}

}
