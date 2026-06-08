package com.ezetik.kjeypapa.sbf.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.ezetik.kjeypapa.sbf.model.Customer;
import com.ezetik.kjeypapa.sbf.payload.NoteAttachmentPayload;
import com.ezetik.kjeypapa.security.util.Message;

public interface MerchantService {

	ResponseEntity<Message<List<Customer>>> findMyCustomer();

	ResponseEntity<Message<Customer>> findMyCustomerByNoteId(int noteId);

	ResponseEntity<Message<Customer>> confirmDelivery(NoteAttachmentPayload pl);

}
