package com.ezetik.kjeypapa.sbf.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.payload.MerchantResp;
import com.ezetik.kjeypapa.sbf.payload.NoteApprovedResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteDisbursementUpdate;
import com.ezetik.kjeypapa.sbf.payload.NotePayload;
import com.ezetik.kjeypapa.sbf.payload.NoteTransaction;
import com.ezetik.kjeypapa.security.util.Message;

public interface NoteService {

	public ResponseEntity<Message<Note>> noteRequest(NotePayload note);

	public Message<String> checkCreditRule(Note note);

	public Message<Boolean> isHoliday(Long date);

	public ResponseEntity<Message<Note>> reviewRequest(int noteId, boolean isEligible, String reason);

	public Message<Note> rejectRequest(Note note);

	public ResponseEntity<Message<Note>> approveRequest(int noteId, boolean isApproved, String comment);

	public ResponseEntity<Message<NoteApprovedResponse>> acceptNote(int noteId, boolean isAccepted);

	public ResponseEntity<Message<Note>> confirmReceivedGood(int noteId, boolean isGoodReceived);

	public LoanFacility submitNoteToCBS(Note note);

	public ResponseEntity<Message<Note>> updateNoteDisbursement(NoteDisbursementUpdate note);

//	public ResponseEntity<Message<Note>> submitSignedNote(Note note);
//
//	public ResponseEntity<Message<Note>> verifySignedNote(Note note, boolean isCorrect);

	public ResponseEntity<Message<NoteAttachmentResponse>> saveAttachment(NoteAttachmentPayload payload);

	public NoteAttachmentResponse getNoteAttachment(int noteId, DocumentTypeEnum docType);

	public ResponseEntity<Message<List<NoteAttachmentResponse>>> getNoteAttachmentList(boolean isReviewed,
			DocumentTypeEnum docType);

	public ResponseEntity<Message<NoteAttachmentResponse>> reviewNoteAttachment(int noteId, DocumentTypeEnum docType,
			boolean isCorrect, String reason);

	public ResponseEntity<Message<List<MerchantResp>>> getMyMerchants();

	public ResponseEntity<Message<List<NoteTransaction>>> getMyNoteTransactions();

}
