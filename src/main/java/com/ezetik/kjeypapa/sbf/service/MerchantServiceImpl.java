package com.ezetik.kjeypapa.sbf.service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.sbf.model.Customer;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.sbf.repository.NoteAttachmentRepository;
import com.ezetik.kjeypapa.sbf.repository.NoteRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

@Service
public class MerchantServiceImpl implements MerchantService {

	@Autowired
	NoteRepository repo;

	@Autowired
	NoteAttachmentRepository attRepo;

	@Autowired
	private UserService userService;

	@Autowired
	private NoteRepository noteRepo;

	@Autowired
	NoteService noteService;

	@Override
	public ResponseEntity<Message<List<Customer>>> findMyCustomer() {
		ResponseEntity<Message<List<Customer>>> resp = null;

		try {
			List<Customer> custs = new ArrayList<>();
			String merchanCode = getCurrentUser().getRegistedId();
			custs = repo.findByMerchantCode(merchanCode);

			if (custs.isEmpty()) {

				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null), HttpStatus.OK);
			}

			for (Customer c : custs) {
				c.setPurchaseOrder(attRepo.findByNoteId(c.getNoteId(), DocumentTypeEnum.PURCHASE_ORDER).orElse(null));

				c.setDeliveryNote(attRepo.findByNoteId(c.getNoteId(), DocumentTypeEnum.DELIVERY_NOTE).orElse(null));
			}

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully", custs), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	User getCurrentUser() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		String userPrincipal = authentication.getName();
		return userService.findUserByUsername(userPrincipal);

	}

	@Override
	public ResponseEntity<Message<Customer>> confirmDelivery(NoteAttachmentPayload pl) {
		ResponseEntity<Message<Customer>> resp = null;

		try {

			Note note = noteRepo.findById(pl.getNoteId()).orElse(null);

			if (note == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Note not found", null),
						HttpStatus.EXPECTATION_FAILED);

			if (note.getStatus().equals(NoteStatusEnum.Pending_Delivery)) {

				if (!noteService.saveAttachment(pl).getStatusCode().equals(HttpStatus.OK))
					return new ResponseEntity<>(new Message<>("FAILED", "Failed to save attachment", null),
							HttpStatus.EXPECTATION_FAILED);

				note.setDelivryConfirmed(true);
				note.setDeliveryCofirmedBy(SecurityContextHolder.getContext().getAuthentication().getName());
				note.setDeliveryConfirmedDate(new Date(System.currentTimeMillis()));
				note.setStatus(NoteStatusEnum.Pending_Confirmation);

				noteRepo.save(note);

				resp = findMyCustomerByNoteId(pl.getNoteId());

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
	public ResponseEntity<Message<Customer>> findMyCustomerByNoteId(int noteId) {
		ResponseEntity<Message<Customer>> resp = null;

		try {
			Customer cust = repo.findCustByNoteId(noteId).orElse(null);

			if (cust.equals(null)) {

				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null), HttpStatus.OK);
			}

			cust.setPurchaseOrder(attRepo.findByNoteId(noteId, DocumentTypeEnum.PURCHASE_ORDER).orElse(null));

			cust.setDeliveryNote(attRepo.findByNoteId(noteId, DocumentTypeEnum.DELIVERY_NOTE).orElse(null));

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data successfully", cust), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

}
