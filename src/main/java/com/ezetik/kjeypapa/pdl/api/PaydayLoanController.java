package com.ezetik.kjeypapa.pdl.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlAttachment;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlDocTypeEnum;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPaymentSchedule;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.BankInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.ezetik.kjeypapa.pdl.model.PdlLoanTypeEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlAcceptDecision;
import com.ezetik.kjeypapa.pdl.payload.PdlApplicationPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlCbcConsentResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlSettlementAccountResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlProfileResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlQuoteResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlTransaction;
import com.ezetik.kjeypapa.pdl.service.PaydayLoanService;
import com.ezetik.kjeypapa.pdl.service.PdlPricingService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Customer-facing Payday Loan API. Mirrors {@code NoteController}; direct-to-customer
 * (no merchant/facility). Decisioning + contract + schedule come from Sambat LOS
 * (see {@code LosWebhookController}).
 */
@RestController
@RequestMapping("/api/v1/pdl")
@Tag(name = "08- Payday Loan API", description = "Payday Loan (PDL) customer controller")
public class PaydayLoanController {

	@Autowired
	private PaydayLoanService service;

	@Autowired
	private PdlPricingService pricingService;

	private FileNameHelper fileHelper = new FileNameHelper();

	/**
	 * Quote for a repayment-amount tier (V8 wizard screen 15). Server-owned
	 * pricing (Sambat 2026-08-13 QC1.3/QC1.6); returns the tier list too.
	 */
	@GetMapping("/quote")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlQuoteResponse>> quote(
			@RequestParam(name = "repaymentAmount") double repaymentAmount,
			@RequestParam(name = "currency", required = false, defaultValue = "USD") String currency,
			@RequestParam(name = "loanType", required = false, defaultValue = "PAYDAY") String loanType) {
		try {
			PdlQuoteResponse q = pricingService.quote(PdlLoanTypeEnum.valueOf(loanType.trim().toUpperCase()),
					currency, repaymentAmount);
			return new ResponseEntity<>(new Message<>("SUCCESS", "Quote computed", q), HttpStatus.OK);
		} catch (IllegalArgumentException bad) {
			return new ResponseEntity<>(new Message<>("INVALID", bad.getMessage(), null),
					HttpStatus.EXPECTATION_FAILED);
		}
	}

	/** The selectable repayment-amount tiers (per currency). */
	@GetMapping("/quote/tiers")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<Double>>> quoteTiers(
			@RequestParam(name = "currency", required = false, defaultValue = "USD") String currency) {
		return new ResponseEntity<>(new Message<>("SUCCESS", "Tier list", pricingService.tiers(currency)),
				HttpStatus.OK);
	}

	/** Settlement account + balance (V8 screen 26, G20 — mock until QC3.1 API). */
	@GetMapping("/settlement-account")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlSettlementAccountResponse>> settlementAccount() {
		return service.getSettlementAccount();
	}

	/** The generated CBC-consent record for a submitted application (QC4.2). */
	/**
	 * The documents filed with Sambat for this application. Sambat asked
	 * (2026-09-04) that the customer be able to see everything we post on
	 * their behalf; this lists the slots, and {@code /los-document/{slot}}
	 * serves each one.
	 */
	@GetMapping("/{id}/los-documents")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<java.util.List<String>>> losDocuments(@PathVariable("id") int id) {
		return ResponseEntity.ok(new Message<>("SUCCESS", "OK",
				com.ezetik.kjeypapa.pdl.service.PaydayLoanServiceImpl.LOS_DOCUMENT_SLOTS));
	}

	/** One filed document, as an image the app can display. */
	@GetMapping("/{id}/los-document/{slot}")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<byte[]> losDocument(@PathVariable("id") int id, @PathVariable("slot") String slot) {
		return service.getLosDocument(id, slot);
	}

	@GetMapping("/{id}/cbc-consent")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlCbcConsentResponse>> cbcConsent(@PathVariable("id") int id) {
		return service.getCbcConsent(id);
	}

	/** Create a Draft application (loan details). Upload the 5 docs, then submit. */
	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PaydayLoan>> create(@RequestBody PdlApplicationPayload payload) {
		return service.createApplication(payload);
	}

	/** Validate the 5 mandatory docs are present, then send to LOS (Draft → Submitted). */
	@PostMapping("/{id}/submit")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PaydayLoan>> submit(@PathVariable("id") int id) {
		return service.submit(id);
	}

	/** Upload one of the mandatory documents (multipart; mirrors the Note pattern). */
	@PostMapping(path = "/document", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlAttachment>> uploadDocument(@RequestPart(name = "pdlId") String pdlId,
			@RequestPart(name = "docType") String docType,
			@RequestPart(name = "files", required = true) MultipartFile[] files) {

		List<Image> images = new ArrayList<>();
		for (MultipartFile file : files) {
			Image image = Image.buildImage(file, fileHelper);
			// Unguessable reference (see PdlAccountRequestService.storeDoc).
			String orig = image.getFileName() == null ? "" : image.getFileName();
			int dot = orig.lastIndexOf('.');
			image.setFileName("pdl-" + java.util.UUID.randomUUID()
					+ ((dot > -1 && dot < orig.length() - 1) ? orig.substring(dot).toLowerCase() : ".jpg"));
			image.setEntityClass(docType);
			image.setEntityId(pdlId);
			images.add(image);
		}

		return service.uploadDocument(Integer.valueOf(pdlId), PdlDocTypeEnum.valueOf(docType), images);
	}

	/** Customer accept ("Y" + signed contract) / reject ("N") of an approved offer. */
	@PostMapping("/{id}/accept")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PaydayLoan>> accept(@PathVariable("id") int id,
			@RequestBody PdlAcceptDecision decision) {
		return service.accept(id, decision);
	}

	/** Customer revokes a not-yet-accepted application. */
	@PostMapping("/{id}/revoke")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PaydayLoan>> revoke(@PathVariable("id") int id,
			@RequestParam(required = false) String reason) {
		return service.revoke(id, reason);
	}

	@GetMapping("/my-applications")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<PaydayLoan>>> myApplications() {
		return service.getMyApplications();
	}

	@GetMapping("/my-transactions")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<PdlTransaction>>> myTransactions() {
		return service.getMyTransactions();
	}

	@GetMapping("/{id}/payment-schedule")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<List<PdlPaymentSchedule>>> paymentSchedule(@PathVariable("id") int id) {
		return service.getPaymentSchedule(id);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PaydayLoan>> findById(@PathVariable("id") int id) {
		return service.getLoanById(id);
	}

	// ----- Profile capture (signup / My Profile) -----

	@PostMapping("/personal-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlPersonalInfo>> savePersonalInfo(@RequestBody PersonalInfoRequest req) {
		return service.savePersonalInfo(req);
	}

	@GetMapping("/personal-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlPersonalInfo>> getPersonalInfo() {
		return service.getPersonalInfo();
	}

	@PostMapping("/employment-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlEmploymentInfo>> saveEmploymentInfo(@RequestBody EmploymentInfoRequest req) {
		return service.saveEmploymentInfo(req);
	}

	@GetMapping("/employment-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlEmploymentInfo>> getEmploymentInfo() {
		return service.getEmploymentInfo();
	}

	@PostMapping("/bank-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlBankInfo>> saveBankInfo(@RequestBody BankInfoRequest req) {
		return service.saveBankInfo(req);
	}

	@GetMapping("/bank-info")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlBankInfo>> getBankInfo() {
		return service.getBankInfo();
	}

	@GetMapping("/profile")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<PdlProfileResponse>> getProfile() {
		return service.getProfile();
	}
}
