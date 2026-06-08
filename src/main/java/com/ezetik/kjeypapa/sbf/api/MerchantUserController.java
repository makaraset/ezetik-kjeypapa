package com.ezetik.kjeypapa.sbf.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.sbf.model.Customer;
import com.ezetik.kjeypapa.sbf.model.DocumentTypeEnum;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.sbf.service.MerchantService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/merchant-user")
@Tag(name = "08- Merchant User API", description = "Merchant User controller")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantUserController {

	@Autowired
	MerchantService service;

	private FileNameHelper fileHelper = new FileNameHelper();

	@GetMapping("/my-customer")
	public ResponseEntity<Message<List<Customer>>> findMyCustomer() {

		return service.findMyCustomer();
	}

	@GetMapping("/my-customer/{noteId}")
	public ResponseEntity<Message<Customer>> findMyCustomerByNoteId(@PathVariable("noteId") int noteId) {

		return service.findMyCustomerByNoteId(noteId);
	}

	@PostMapping(path = "/confirm-delivery", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	public ResponseEntity<Message<Customer>> noteAttachment(@RequestPart(name = "noteId") String noteId,
			@RequestPart(name = "files", required = true) MultipartFile[] files) {

		ResponseEntity<Message<Customer>> resp = null;

		List<Image> images = new ArrayList<>();

		for (MultipartFile file : files) {
			Image image = Image.buildImage(file, fileHelper);
			image.setEntityClass(DocumentTypeEnum.DELIVERY_NOTE.toString());
			image.setEntityId(noteId);
			images.add(image);
		}

		NoteAttachmentPayload nap = new NoteAttachmentPayload();
		nap.setNoteId(Integer.valueOf(noteId));
		nap.setDocType(DocumentTypeEnum.DELIVERY_NOTE);
		nap.setAttachFiles(images);
		resp = service.confirmDelivery(nap);

		return resp;
	}

}
