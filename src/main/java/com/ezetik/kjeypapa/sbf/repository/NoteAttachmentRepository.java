package com.ezetik.kjeypapa.sbf.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.NoteAttachment;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public interface NoteAttachmentRepository extends JpaRepository<NoteAttachment, Integer> {

	@Query(value = "select new com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse(ns) from NoteAttachment ns where note.id=:noteId and ns.isDeleted=false and ns.docType=:docType")
	Optional<NoteAttachmentResponse> findByNoteId(int noteId, DocumentTypeEnum docType);

	@Query(value = "select new com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse(ns) from NoteAttachment ns where note.isReviewed=:isReviewed and ns.isDeleted=false and ns.docType=:docType")
	List<NoteAttachmentResponse> findNoteAttachment(boolean isReviewed, DocumentTypeEnum docType);

	@Modifying
	@Query("update NoteAttachment n set n.isReviewed=true, n.isCorrect=:isCorrect, n.reason=:reason, n.reviewedBy=:reviewedBy, n.reviewedDate =:reviewedDate, n.isRework=:isRework where note.id=:noteId and n.docType=:docType")
	int reviewNoteAttachment(int noteId, DocumentTypeEnum docType, boolean isCorrect, String reason, String reviewedBy,
			Instant reviewedDate, boolean isRework);

	@Query(value = "select new com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse(sn) from NoteAttachment sn where sn.id=:id")
	Optional<NoteAttachmentResponse> findById(int id);

	@Query(value = "select count(n) from NoteAttachment n where note.id=:noteId and n.docType=:docType")
	int countByNoteId(int noteId, DocumentTypeEnum docType);

	@Modifying
	@Query("update NoteAttachment n set n.isDeleted=true, n.updatedBy=:updatedBy, n.updatedAt=:updatedAt where note.id=:noteId and n.docType=:docType")
	int deleteAttachment(int noteId, DocumentTypeEnum docType, String updatedBy, Instant updatedAt);
}
