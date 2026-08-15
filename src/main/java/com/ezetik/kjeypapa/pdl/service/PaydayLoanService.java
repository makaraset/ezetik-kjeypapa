package com.ezetik.kjeypapa.pdl.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

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
import com.ezetik.kjeypapa.pdl.payload.PdlAcceptDecision;
import com.ezetik.kjeypapa.pdl.payload.PdlApplicationPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlCbcConsentResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlProfileResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlTransaction;
import com.ezetik.kjeypapa.security.util.Message;

public interface PaydayLoanService {

	ResponseEntity<Message<PaydayLoan>> createApplication(PdlApplicationPayload payload);

	ResponseEntity<Message<PaydayLoan>> submit(int id);

	ResponseEntity<Message<PdlAttachment>> uploadDocument(int pdlId, PdlDocTypeEnum docType, List<Image> images);

	ResponseEntity<Message<PaydayLoan>> accept(int id, PdlAcceptDecision decision);

	ResponseEntity<Message<PaydayLoan>> revoke(int id, String reason);

	ResponseEntity<Message<List<PdlTransaction>>> getMyTransactions();

	ResponseEntity<Message<List<PaydayLoan>>> getMyApplications();

	ResponseEntity<Message<List<PdlPaymentSchedule>>> getPaymentSchedule(int id);

	ResponseEntity<Message<PaydayLoan>> getLoanById(int id);

	ResponseEntity<Message<PdlCbcConsentResponse>> getCbcConsent(int id);

	// --- profile capture (signup / My Profile) ---
	ResponseEntity<Message<PdlPersonalInfo>> savePersonalInfo(PersonalInfoRequest req);

	ResponseEntity<Message<PdlPersonalInfo>> getPersonalInfo();

	ResponseEntity<Message<PdlEmploymentInfo>> saveEmploymentInfo(EmploymentInfoRequest req);

	ResponseEntity<Message<PdlEmploymentInfo>> getEmploymentInfo();

	ResponseEntity<Message<PdlBankInfo>> saveBankInfo(BankInfoRequest req);

	ResponseEntity<Message<PdlBankInfo>> getBankInfo();

	ResponseEntity<Message<PdlProfileResponse>> getProfile();
}
