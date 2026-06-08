package com.ezetik.kjeypapa.sbf.service;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.notification.config.NotificationService;
import com.ezetik.kjeypapa.notification.controller.NotificationMessage;
import com.ezetik.kjeypapa.sbf.model.ConsolidateData;
import com.ezetik.kjeypapa.sbf.model.CreditFacilityMaster;
import com.ezetik.kjeypapa.sbf.model.DisburseModel;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.Holiday;
import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.model.LoanFacilityInfo;
import com.ezetik.kjeypapa.sbf.model.Merchant;
import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.model.NoteAttachment;
import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;
import com.ezetik.kjeypapa.sbf.payload.MerchantResp;
import com.ezetik.kjeypapa.sbf.payload.NoteApprovedResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteDisbursementUpdate;
import com.ezetik.kjeypapa.sbf.payload.NotePayload;
import com.ezetik.kjeypapa.sbf.payload.NoteTransaction;
import com.ezetik.kjeypapa.sbf.repository.NoteAttachmentRepository;
import com.ezetik.kjeypapa.sbf.repository.NotePeriodRepository;
import com.ezetik.kjeypapa.sbf.repository.NoteRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;

@Service
public class NoteServiceImpl implements NoteService {

	@Autowired
	private SBFApiService apiService;

	@Autowired
	private NoteRepository noteRepo;

	@Autowired
	private NotePeriodRepository notePeriod;

	@Autowired
	private UserService userService;

	@Autowired
	private ImageRepository imageRepo;

	@Autowired
	private NoteAttachmentRepository noteAttachmentRepo;

	@Autowired
	private SBFApiService sbfService;

	@Autowired
	private NotificationService notificationService;

	@Override
	@Transactional
	public ResponseEntity<Message<Note>> noteRequest(NotePayload note) {

		ResponseEntity<Message<Note>> resp = null;

		try {
			Note noteReq = new Note(note);

			if (note.isMe()) {
				noteReq.setUser(this.getCurrentUser());
			} else {
				return new ResponseEntity<>(new Message<>("BAD_REQUEST", "isMe cannot be false", null),
						HttpStatus.BAD_REQUEST);
			}

			Long cif = Long.valueOf(
					userService.findUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
							.getRegistedId());

			ConsolidateData data = sbfService.getFacilityByCIF(cif);

			if (!note.getFacilityNo().equals(data.getCreditFacilityMaster().getFacRefNo())) {
				return new ResponseEntity<>(new Message<>("BAD_REQUEST", "Facility No not matched.", null),
						HttpStatus.BAD_REQUEST);
			}

			noteReq.setNotePeriod(notePeriod.findById(note.getNotePeriod()).orElse(null));
			noteReq.setStatus(NoteStatusEnum.Draft);

			noteReq.setCustomerName(data.getCustomerInformation().getName());
			noteReq.setSaCustacctid(data.getSavingAccounts().get(0).getCustacctid());

			Note nr = noteRepo.save(noteReq);
			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Data is saved", nr), HttpStatus.OK);

		} catch (Exception e) {

			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);

			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public Message<String> checkCreditRule(Note note) {

		Message<String> resp = null;
		try {

			// =======Check Validity Period=======//
			ConsolidateData account = apiService.getFacilityByCIF(Long.valueOf(this.getCurrentUser().getRegistedId()));
			CreditFacilityMaster fac = account.getCreditFacilityMaster();

			SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
			java.util.Date disDate = formatter.parse(fac.getExpiryDate());

			if (note.getRepaymentDate().isAfter(disDate.toInstant())) {
				return new Message<>("INVALID", "Repayment date cannot after facility expiry date", null);
			}

			if (fac.getAmtLimit().compareTo(note.getRequestAmount()) < 0) {
				return new Message<>("INVALID", "Request amount cannot over the amount limit", null);
			}

			for (LoanFacilityInfo lf : account.getLoanFacilityInfo()) {
				if (lf.getAging() > 0) {
					return new Message<>("INVALID", "Please repayment your note before request new.", null);
				}
			}

//			Instant next4Day = Instant.now().plus(3, ChronoUnit.DAYS);
//
//			if (note.getDisbursementDate().isBefore(next4Day)) {
//				return new Message<>("INVALID", "Disbursement date must be at least next 4 days", null);
//			}

			resp = new Message<>("SUCCESS", "Passed", "Credit rule is passed");

		} catch (Exception e) {
			resp = new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null);
		}
		return resp;
	}

	@Override
	public ResponseEntity<Message<Note>> reviewRequest(int noteId, boolean isEligible, String reason) {

		ResponseEntity<Message<Note>> resp = null;

		try {

			Note note = noteRepo.findById(noteId).orElse(null);

			if (note == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.isReviewed())
				return new ResponseEntity<>(new Message<>("INVALID", "Note is already reviewed", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.isRejected())
				return new ResponseEntity<>(new Message<>("INVALID", "Note is rejected", null),
						HttpStatus.EXPECTATION_FAILED);

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			note.setEligible(isEligible);

			if (isEligible) {
				note.setReviewed(true);
				note.setReviewedBy(authentication.getName());
				note.setReviewedDate(new Date(System.currentTimeMillis()));
				note.setStatus(NoteStatusEnum.Reviewed);

				note = noteRepo.save(note);
				reviewNoteAttachment(noteId, DocumentTypeEnum.PURCHASE_ORDER, isEligible, "Eligible");

				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Note is reviewed", note), HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(rejectRequest(note), HttpStatus.OK);
				reviewNoteAttachment(noteId, DocumentTypeEnum.PURCHASE_ORDER, isEligible, reason);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@Override
	public Message<Note> rejectRequest(Note note) {

		Message<Note> resp = null;
		try {
			if (note.getStatus().equals(NoteStatusEnum.Draft)) {

				note.setRejected(true);
				note.setRejectedBy(SecurityContextHolder.getContext().getAuthentication().getName());
				note.setRejectedDate(new Date(System.currentTimeMillis()));
				note.setStatus(NoteStatusEnum.Rejected);

				resp = new Message<>("SUCCESS", "Noted is rejected", noteRepo.save(note));

				// Send notification
				NotificationMessage message = new NotificationMessage(note.getUser().getFcmToken(), "Request rejected",
						note.getId().toString(), note.getId().toString(), note.getUser().getUsername());
				notificationService.postToClient(message);

			} else {
				resp = new Message<>("INVALID", "Invalid status", note);
			}
		} catch (Exception e) {
			resp = new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null);
			e.printStackTrace();
		}
		return resp;
	}

	@Override
	public ResponseEntity<Message<Note>> approveRequest(int noteId, boolean isApproved, String comment) {
		ResponseEntity<Message<Note>> resp = null;

		try {

			Note note = noteRepo.findById(noteId).orElse(null);

			if (note == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.getStatus().equals(NoteStatusEnum.Reviewed)) {
				if (isApproved) {

					note.setApproved(isApproved);
					note.setApprovedBy(SecurityContextHolder.getContext().getAuthentication().getName());
					note.setApprovedDate(new Date(System.currentTimeMillis()));
					note.setStatus(NoteStatusEnum.Approved);
					note.setApprovedComment(comment);

					resp = new ResponseEntity<>(new Message<>("SUCCESS", "Note is approved", noteRepo.save(note)),
							HttpStatus.OK);

					// Send notification
					NotificationMessage message = new NotificationMessage(note.getUser().getFcmToken(),
							"Request approved", note.getId().toString(), note.getId().toString(),
							note.getUser().getUsername());
					notificationService.postToClient(message);

				} else {
					Message<Note> rjReps = rejectRequest(note);
					resp = new ResponseEntity<>(new Message<>(rjReps.getType(), rjReps.getMessage(), note),
							HttpStatus.OK);
				}
			} else {
				resp = new ResponseEntity<>(new Message<>("INVALID", "Invalid status", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();

		}
		return resp;
	}

	@Override
	public ResponseEntity<Message<NoteApprovedResponse>> acceptNote(int noteId, boolean isAccepted) {
		ResponseEntity<Message<NoteApprovedResponse>> resp = null;

		try {

			Note note = noteRepo.findById(noteId).orElse(null);
			NoteApprovedResponse nar = new NoteApprovedResponse();

			if (note == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.getStatus().equals(NoteStatusEnum.Approved)) {
				if (isAccepted) {

					note.setStatus(NoteStatusEnum.Pending_Delivery);

					note.setAccepted(isAccepted);
					note.setAcceptedBy(SecurityContextHolder.getContext().getAuthentication().getName());
					note.setAcceptedDate(new Date(System.currentTimeMillis()));

					nar.setLoanFacility(submitNoteToCBS(note));

					note.setLoanFacRefNo(nar.getLoanFacility().getLoanFacRefNo());
					note.setLoanFacId(nar.getLoanFacility().getLoanFacId());
					nar.setNote(noteRepo.save(note));

					resp = new ResponseEntity<>(new Message<>("SUCCESS", "Note is accepted", nar), HttpStatus.OK);

					// Notify to merchant
					User merchantUser = userService.findUserByRegisteredId(note.getMerchantCode());
					if (merchantUser != null) {
						NotificationMessage message2 = new NotificationMessage(merchantUser.getFcmToken(),
								"Request approved", note.getId().toString(), note.getId().toString(),
								merchantUser.getUsername());
						notificationService.postToClient(message2);
					}

				} else {
					Message<Note> rjReps = rejectRequest(note);
					resp = new ResponseEntity<>(
							new Message<>(rjReps.getType(), rjReps.getMessage(), new NoteApprovedResponse(note, null)),
							HttpStatus.OK);
				}
			} else {
				resp = new ResponseEntity<>(new Message<>("INVALID_STATUS", "Note must got approved first", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();

		}
		return resp;
	}

	@Override
	public ResponseEntity<Message<Note>> confirmReceivedGood(int noteId, boolean isGoodReceived) {
		ResponseEntity<Message<Note>> resp = null;

		try {

			Note note = noteRepo.findById(noteId).orElse(null);

			if (note == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.getStatus().equals(NoteStatusEnum.Pending_Confirmation) && note.isAccepted()) {
				if (isGoodReceived) {

					note.setGoodReceived(isGoodReceived);
					note.setReceivedBy(SecurityContextHolder.getContext().getAuthentication().getName());
					note.setReceivedDate(new Date(System.currentTimeMillis()));

					System.out.println(note.toString());
					// Disbursement here

					DisburseModel dis = new DisburseModel();
					dis.setLoanRefNo(note.getLoanFacRefNo());
					dis.setCustacctid(note.getSaCustacctid());
					dis.setDisburseDate(note.getDisbursementDate().toString().substring(0, 10));
					dis.setMaturityDate(note.getRepaymentDate().toString().substring(0, 10));
					dis.setDisburseAmount(note.getRequestAmount());
					dis.setInterestRate(0.00);
					dis.setMonthlyFeeRate(note.getNotePeriod().getRate());
					dis.setRepaymentMethod("D");
					dis.setNoOfInstallment(1);
					dis.setDisburseTo(note.getMerchantCustAcctId().toString());
					dis.setDisburseBy("ACTR");
					dis.setAuthBy("Kjey_PAPA");
					dis.setDoneBy("Kjey_PAPA");

					System.out.println(dis);

					var message = apiService.autoDisburse(dis);

					System.out.println(message);

					if (message.getStatusCode() != null) {

						if (message.getStatusCode().equals("200")) {
							note.setStatus(NoteStatusEnum.Disbursed);
							resp = new ResponseEntity<>(new Message<>("SUCCESS",
									message.getMessage() + ", trn: " + message.getTranxId(), noteRepo.save(note)),
									HttpStatus.OK);

						} else {
							resp = new ResponseEntity<>(new Message<>("ERROR",
									message.getMessage() + ", error: " + message.getStatusCode(), null),
									HttpStatus.INTERNAL_SERVER_ERROR);
						}
					} else {
						resp = new ResponseEntity<>(new Message<>("ERROR",
								message.getMessage() + ", error: " + message.getStatusCode(), null),
								HttpStatus.INTERNAL_SERVER_ERROR);
					}

				} else {
					Message<Note> rjReps = rejectRequest(note);
					resp = new ResponseEntity<>(new Message<>(rjReps.getType(), rjReps.getMessage(), note),
							HttpStatus.OK);
				}
			} else {
				resp = new ResponseEntity<>(new Message<>("INVALID", "Invalid status", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();

		}
		return resp;
	}

	@Override
	public LoanFacility submitNoteToCBS(Note note) {

		LoanFacility lf = new LoanFacility();

		ConsolidateData data = sbfService.getFacilityByCIF(Long.valueOf(note.getUser().getRegistedId()));

		String PATTERN_FORMAT = "dd-MM-yyyy";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT).withZone(ZoneId.systemDefault());

		lf.setCreditFacId(data.getCreditFacilityMaster().getCreditFacId());
		lf.setCreditTypeId(845);
		lf.setIsRollOver("N");
		lf.setLoanPrincipal(note.getRequestAmount());
		lf.setCurrId(2);
		lf.setEntryDate(formatter.format(note.getDisbursementDate()));
		lf.setReviewDate(formatter.format(note.getDisbursementDate()));
		lf.setExpiryDate(formatter.format(note.getRepaymentDate()));
		lf.setDeleted("N");
		lf.setCreditStatusId(4);
		lf.setBusTypeId(1);
		lf.setEmpRepId(273);
		lf.setCatId("CFL");
		lf.setSpecialNoteId(1);
		lf.setExternalId("N" + note.getId().toString());
		lf.setRemarks(note.getMerchantCode());
		lf.setDealerId(note.getMerchantCode());

		return sbfService.createLoanFac(lf);
	}

	User getCurrentUser() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		String userPrincipal = authentication.getName();
		return userService.findUserByUsername(userPrincipal);

	}

	@Override
	@Transactional
	public ResponseEntity<Message<NoteAttachmentResponse>> saveAttachment(NoteAttachmentPayload pl) {

		ResponseEntity<Message<NoteAttachmentResponse>> resp = null;

		try {
			NoteAttachment ns = new NoteAttachment();

			NoteAttachmentResponse att = getNoteAttachment(pl.getNoteId(), pl.getDocType());
			if (att != null) {
				if (att.isCorrect() && att.isReviewed()) {
					return new ResponseEntity<>(
							new Message<>("INVALID_REQUEST", "Attachment has been reviewed and confirmed", att),
							HttpStatus.EXPECTATION_FAILED);
				} else {
					ns.setId(att.getId());
				}
			} else {
				ns.setId(pl.getId());

			}

			ns.setNote(noteRepo.findById(pl.getNoteId()).orElse(null));
			ns.setAttachFiles(imageRepo.saveAll(pl.getAttachFiles()));
			ns.setDocType(pl.getDocType());
			ns.setReviewed(false);
			ns.setDeleted(false);

			ns = noteAttachmentRepo.save(ns);

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Data is saved", new NoteAttachmentResponse(ns)),
					HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@Override
	public NoteAttachmentResponse getNoteAttachment(int noteId, DocumentTypeEnum docType) {

		NoteAttachmentResponse resp = noteAttachmentRepo.findByNoteId(noteId, docType).orElse(null);

		return resp;
	}

	@Override
	public ResponseEntity<Message<NoteAttachmentResponse>> reviewNoteAttachment(int noteId, DocumentTypeEnum docType,
			boolean isCorrect, String reason) {
		ResponseEntity<Message<NoteAttachmentResponse>> resp = null;
		try {
			int exist = noteAttachmentRepo.countByNoteId(noteId, docType);
			if (exist > 0) {

				boolean isRework = false;
				if (!isCorrect)
					isRework = true;

				int updated = noteAttachmentRepo.reviewNoteAttachment(noteId, docType, isCorrect, reason,
						SecurityContextHolder.getContext().getAuthentication().getName(), Instant.now(), isRework);

				if (updated > 0) {

					resp = new ResponseEntity<>(new Message<>("SUCCESS", "Singed note is reviewed",
							noteAttachmentRepo.findByNoteId(noteId, docType).orElse(null)), HttpStatus.OK);

				} else {
					resp = new ResponseEntity<>(new Message<>("FAILED", "Failed with unknown reason", null),
							HttpStatus.NOT_FOUND);
				}
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Attachment not found", null),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return resp;
	}

	@Override
	public ResponseEntity<Message<List<MerchantResp>>> getMyMerchants() {

		ResponseEntity<Message<List<MerchantResp>>> resp = null;

		try {

			var sbfMerchant = apiService.getMyMerchant(Long.valueOf(getCurrentUser().getRegistedId()));
			List<Merchant> merchantList = new ArrayList<>();
			if (sbfMerchant.getStatusCode().equals(HttpStatus.OK)) {
				merchantList = sbfMerchant.getBody().getData();
				if (!merchantList.isEmpty()) {
					resp = new ResponseEntity<>(
							new Message<>("SUCCESS", "Get data success", prepareMerchantsSbf(merchantList)),
							HttpStatus.OK);
				} else {
					resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "You don't have any active note", null),
							HttpStatus.OK);
				}
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	List<MerchantResp> prepareMerchants(List<Merchant> merchants) {

		List<MerchantResp> merchantResp = new ArrayList<>();
		MerchantResp mr = null;
		for (Merchant m : merchants) {
			mr = new MerchantResp(m);
			mr.setActiveNote(noteRepo.countNoteByMerchantCode(m.getMerchantCode(), NoteStatusEnum.Disbursed));
			merchantResp.add(mr);
		}
		return merchantResp;
	}

	List<MerchantResp> prepareMerchantsSbf(List<Merchant> merchants) {

		List<MerchantResp> mlist = new ArrayList<>();
		if (!merchants.isEmpty()) {

			Long cif = Long.valueOf(
					userService.findUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
							.getRegistedId());

			ConsolidateData data = sbfService.getFacilityByCIF(cif);
			List<Integer> activeStatus = Arrays.asList(1, 3, 6, 7, 10);

			List<Integer> ids = new ArrayList<>();

			MerchantResp mresp = null;

			List<LoanFacilityInfo> activeLoan = data.getLoanFacilityInfo().stream()
					.filter(loan -> activeStatus.contains(loan.getLoanStatus())).collect(Collectors.toList());

			List<Note> notes = new ArrayList<>();
			List<NoteStatusEnum> statuses = new ArrayList<>();
//			statuses.add(NoteStatusEnum.Approved);
			statuses.add(NoteStatusEnum.Disbursed);

			MerchantResp mr = null;

			for (Merchant m : merchants) {
				mr = new MerchantResp(m);
				notes = noteRepo.findByMerchantCodeAndStatus(m.getMerchantCode(), statuses);
				int ac = 0;
				Double out = 0.0;
				for (LoanFacilityInfo loan : activeLoan) {
					for (Note n : notes) {
						if (loan.getLoanFacRefNo().equals(n.getLoanFacRefNo())) {
							ac++;
							out += loan.getOutstanding();
						}
					}
				}
				mr.setActiveNote(ac);
				mr.setOutstanding(out);
				mlist.add(mr);
			}

		}

		return mlist;
	}

	@Override
	public ResponseEntity<Message<List<NoteTransaction>>> getMyNoteTransactions() {

		ResponseEntity<Message<List<NoteTransaction>>> resp = null;

		try {

			List<NoteTransaction> notes = noteRepo.findTransactionByUserId(getCurrentUser().getId());

			if (notes.isEmpty())
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "You don't have any active note", null),
						HttpStatus.OK);

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", prepareNoteData(notes)),
					HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	List<NoteTransaction> prepareNoteData(List<NoteTransaction> notes) {

		if (!notes.isEmpty()) {

			Long cif = Long.valueOf(
					userService.findUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
							.getRegistedId());

			ConsolidateData data = sbfService.getFacilityByCIF(cif);

			for (NoteTransaction nt : notes) {

				for (LoanFacilityInfo lf : data.getLoanFacilityInfo()) {

					if (lf.getLoanFacRefNo().equals(nt.getLoanFacRefNo())) {
						nt.setAging(lf.getAging());
						break;
					}
				}

				nt.setPurchaseOrder(
						noteAttachmentRepo.findByNoteId(nt.getId(), DocumentTypeEnum.PURCHASE_ORDER).orElse(null));

				nt.setDeliveryNote(
						noteAttachmentRepo.findByNoteId(nt.getId(), DocumentTypeEnum.DELIVERY_NOTE).orElse(null));
			}
		}

		return notes;
	}

	@Override
	public ResponseEntity<Message<Note>> updateNoteDisbursement(NoteDisbursementUpdate nd) {

		ResponseEntity<Message<Note>> resp = null;

		try {

			Note note = noteRepo.findById(nd.getNoteId()).orElse(null);
			if (note.equals(null))
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			note.setDisbursementDate(nd.getDisbursementDate());
			note.setRepaymentDate(nd.getRepyamentDate());

			note = noteRepo.save(note);

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Note is saved", note), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<List<NoteAttachmentResponse>>> getNoteAttachmentList(boolean isReviewed,
			DocumentTypeEnum docType) {

		ResponseEntity<Message<List<NoteAttachmentResponse>>> resp = null;

		try {
			List<NoteAttachmentResponse> data = noteAttachmentRepo.findNoteAttachment(isReviewed, docType);
			if (!data.isEmpty()) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get Data Success", data), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Data not found", null), HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", "ERROR: " + e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@Override
	public Message<Boolean> isHoliday(Long date) {
		List<Holiday> days = apiService.getHolidays().getBody();
		// System.out.println(days);

		Holiday holiday = days.stream().filter(s -> {
			return s.getHolidayDate().equals(date);
		}).findAny().orElse(null);

		if (holiday != null) {
			return new Message<>("Holiday", date + " is non-working day", true);
		} else {
			return new Message<>("Working Day", date + " is working day", false);
		}
	}

}
