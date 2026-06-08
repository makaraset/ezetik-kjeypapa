package com.ezetik.kjeypapa.sbf.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.model.NoteConsole;
import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;
import com.ezetik.kjeypapa.sbf.model.TaskTypeEnum;
import com.ezetik.kjeypapa.sbf.payload.MerchantResp;
import com.ezetik.kjeypapa.sbf.payload.NoteApprovedResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentResponse;
import com.ezetik.kjeypapa.sbf.payload.NoteDisbursementUpdate;
import com.ezetik.kjeypapa.sbf.payload.NotePayload;
import com.ezetik.kjeypapa.sbf.payload.NoteTransaction;
import com.ezetik.kjeypapa.sbf.repository.NoteRepository;
import com.ezetik.kjeypapa.sbf.service.NoteService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/note")
@Tag(name = "07- Note API", description = "Note controller")
public class NoteController {

	@Autowired
	private NoteService service;

	@Autowired
	private NoteRepository repo;

	@Autowired
	private UserService userService;

	private FileNameHelper fileHelper = new FileNameHelper();

	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<Note>> noteRequest(@RequestBody NotePayload note) {

		ResponseEntity<Message<Note>> resp = service.noteRequest(note);
		Note noteReps = resp.getBody().getData();

		Message<String> creditRule = service.checkCreditRule(noteReps);

		if (!creditRule.getType().equals("SUCCESS")) {

			service.rejectRequest(noteReps);

			resp.getBody().setType(creditRule.getType());
			resp.getBody().setMessage(creditRule.getMessage() + ", Note is rejected.");

			System.out.println("Rejected note: " + creditRule.getMessage());

		}

		return resp;
	}

	@PostMapping("/update-disbursement")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<Note>> updateDisbursement(@RequestBody NoteDisbursementUpdate note) {

		return service.updateNoteDisbursement(note);
	}

	@PostMapping("/{id}/review")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Message<Note>> noteReview(@PathVariable("id") int noteId, boolean isEligible,
			@RequestParam(required = false) String reason) {

		return service.reviewRequest(noteId, isEligible, reason);
	}

	@PostMapping("/{id}/accept")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<NoteApprovedResponse>> acceptNote(@PathVariable("id") int noteId,
			boolean isAccepted) {

		return service.acceptNote(noteId, isAccepted);
	}

	@PostMapping("/{id}/received")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<Note>> receivedGoods(@PathVariable("id") int noteId, boolean isAccepted) {

		return service.confirmReceivedGood(noteId, isAccepted);
	}

//	@PostMapping("/{id}/confirm")
//	@PreAuthorize("hasRole('USER')")
//	public ResponseEntity<Message<Note>> noteConfirm(@PathVariable("id") int noteId, boolean isConfirm) {
//
//		return service.confirmRequest(noteId, isConfirm);
//	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasRole('APPROVER')")
	public ResponseEntity<Message<Note>> approveRequest(@PathVariable("id") int noteId, boolean isApproved,
			@RequestParam(required = false) String comment) {

		return service.approveRequest(noteId, isApproved, comment);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Message<NoteConsole>> findById(@PathVariable("id") int id) {
		ResponseEntity<Message<NoteConsole>> resp = null;

		try {
			Note note = repo.findById(id).orElse(null);
			NoteAttachmentResponse attachment = service.getNoteAttachment(id, DocumentTypeEnum.PURCHASE_ORDER);

			NoteConsole console = new NoteConsole(note, attachment, null);

			if (note != null) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully.", console), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found.", null), HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@GetMapping()
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Message<List<Note>>> findAll() {
		ResponseEntity<Message<List<Note>>> resp = null;

		try {
			List<Note> notes = repo.findAll();
			if (!notes.isEmpty()) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully.", notes), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found.", null), HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@GetMapping("/holidayCheck")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<Boolean>> holidayCheck(Long date) {

		var mes = service.isHoliday(date);

		return new ResponseEntity<>(mes, HttpStatus.OK);
	}

	@GetMapping("/my-note")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<Note>>> findMyNote() {
		ResponseEntity<Message<List<Note>>> resp = null;
		int userId = getCurrentUser().getId();
		try {
			List<Note> notes = repo.findByUserId(userId);
			if (!notes.isEmpty()) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully.", notes), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found.", null), HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@GetMapping("/tasks")
	@PreAuthorize("hasAnyRole('USER','APPROVER')")
	public ResponseEntity<Message<List<Note>>> findMyTask(TaskTypeEnum task) {
		ResponseEntity<Message<List<Note>>> resp = null;

		try {

			User user = getCurrentUser();

			Collection<GrantedAuthority> authorities = user.getAuthorities();
			boolean isApprover = false;
			boolean isUser = false;

			for (GrantedAuthority g : authorities) {
				if (g.getAuthority().equals("ROLE_APPROVER"))
					isApprover = true;

				if (g.getAuthority().equals("ROLE_USER"))
					isUser = true;

			}

			if (isUser) {
				if (task.equals(TaskTypeEnum.TO_REVIEW)) {
					resp = new ResponseEntity<>(
							new Message<>("SUCCESS", "Note to reveiw", repo.findByStatus(NoteStatusEnum.Draft)),
							HttpStatus.OK);
				} else if (task.equals(TaskTypeEnum.ALL)) {
					resp = new ResponseEntity<>(new Message<>("SUCCESS", "All notes", repo.findAll()), HttpStatus.OK);
				}
			}

			if (isApprover) {
				if (task.equals(TaskTypeEnum.TO_APPROVE)) {
					resp = new ResponseEntity<>(
							new Message<>("SUCCESS", "Note to approve", repo.findByStatus(NoteStatusEnum.Reviewed)),
							HttpStatus.OK);
				} else if (task.equals(TaskTypeEnum.ALL)) {
					resp = new ResponseEntity<>(new Message<>("SUCCESS", "All notes", repo.findAll()), HttpStatus.OK);
				}
			}

			if (!isUser && !isApprover) {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Invalid user", null), HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@GetMapping("/tasks/attachment")
	@PreAuthorize("hasAnyRole('USER','APPROVER')")
	public ResponseEntity<Message<List<NoteAttachmentResponse>>> findAttachment(boolean isReviewed,
			DocumentTypeEnum docType) {

		return service.getNoteAttachmentList(isReviewed, docType);
	}

	@GetMapping("/my-transaction")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<NoteTransaction>>> findMyNoteTransaction() {

		return service.getMyNoteTransactions();
	}

	@PostMapping(path = "/attachment", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<NoteAttachmentResponse>> noteAttachment(
			@RequestPart("entityClass") String entityClass, @RequestPart(name = "noteId") String noteId,
			@RequestPart(name = "files", required = true) MultipartFile[] files) {

		ResponseEntity<Message<NoteAttachmentResponse>> resp = null;
		Note note = repo.findById(Integer.valueOf(noteId)).orElse(null);

		if (note == null) {
			return new ResponseEntity<>(new Message<>("NOTE_NOT_FOUND", "Note request not found", null),
					HttpStatus.NOT_ACCEPTABLE);
		}

		List<Image> images = new ArrayList<>();

		for (MultipartFile file : files) {
			Image image = Image.buildImage(file, fileHelper);
			image.setEntityClass(entityClass);
			image.setEntityId(noteId);
			images.add(image);
		}

		NoteAttachmentPayload nap = new NoteAttachmentPayload();
		nap.setNoteId(Integer.valueOf(noteId));
		nap.setDocType(DocumentTypeEnum.valueOf(entityClass));
		nap.setAttachFiles(images);
		resp = service.saveAttachment(nap);

		return resp;
	}

	@GetMapping("/{id}/attachment")
	@PreAuthorize("hasAnyRole('CUSTOMER','USER','MERCHANT')")
	public ResponseEntity<Message<NoteAttachmentResponse>> findAttachmentByNoteId(@PathVariable("id") int id,
			DocumentTypeEnum docType) {

		ResponseEntity<Message<NoteAttachmentResponse>> resp = null;

		try {
			NoteAttachmentResponse nar = service.getNoteAttachment(id, docType);
			if (nar != null) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully.", nar), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found.", null), HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@GetMapping("/my-merchants")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<MerchantResp>>> findMyMerchants() {

		return service.getMyMerchants();
	}

	@PostMapping("/attatchment/{noteId}/review")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Message<NoteAttachmentResponse>> noteAttachmentReview(@PathVariable("noteId") int noteId,
			boolean isCorrect, DocumentTypeEnum docType, @RequestParam(required = false) String reason) {

		return service.reviewNoteAttachment(noteId, docType, isCorrect, reason);
	}

	User getCurrentUser() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		String userPrincipal = authentication.getName();
		return userService.findUserByUsername(userPrincipal);

	}

}
