package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlDocTypeEnum;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlAcceptDecision;
import com.ezetik.kjeypapa.pdl.payload.PdlApplicationPayload;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlAttachmentRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPaymentScheduleRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.service.LosProvider;
import com.ezetik.kjeypapa.pdl.service.PaydayLoanServiceImpl;
import com.ezetik.kjeypapa.pdl.service.PdlPricingService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaydayLoanServiceImplTest {

	@Mock PaydayLoanRepository repo;
	@Mock PdlAttachmentRepository attachmentRepo;
	@Mock PdlPaymentScheduleRepository scheduleRepo;
	@Mock PdlEmploymentInfoRepository employmentRepo;
	@Mock PdlBankInfoRepository bankRepo;
	@Mock PdlPersonalInfoRepository personalRepo;
	@Mock ImageRepository imageRepo;
	@Mock UserService userService;
	@Mock LosProvider losProvider;
	@Mock PdlPricingService pricingService;

	@InjectMocks PaydayLoanServiceImpl service;

	/** A current user whose id is OUTSIDE Integer's cache (>127) — exposes == bugs. */
	private static final int CURRENT_ID = 200;

	@BeforeEach
	void authAsCustomer() {
		SecurityContext ctx = SecurityContextHolder.createEmptyContext();
		ctx.setAuthentication(new UsernamePasswordAuthenticationToken("012551101", null));
		SecurityContextHolder.setContext(ctx);
		User current = user(CURRENT_ID); // build the mock BEFORE the outer when()
		when(userService.findUserByUsername("012551101")).thenReturn(current);
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		// Legacy free-amount creates pass the product cap unless a test overrides.
		lenient().when(pricingService.withinProductCap(any(), any(), org.mockito.ArgumentMatchers.anyDouble()))
				.thenReturn(true);
	}

	private User user(int id) {
		User u = org.mockito.Mockito.mock(User.class);
		lenient().when(u.getId()).thenReturn(id);
		lenient().when(u.getUsername()).thenReturn("012551101");
		return u;
	}

	private PdlApplicationPayload payload() {
		PdlApplicationPayload p = new PdlApplicationPayload();
		p.setRequestAmount(500.0);
		p.setLoanPeriodDays(30);
		return p;
	}

	@Test
	void createApplication_setsDraftStatusAndUser() {
		ResponseEntity<Message<PaydayLoan>> r = service.createApplication(payload());
		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(r.getBody().getData().getStatus()).isEqualTo(PdlStatusEnum.Draft);
		assertThat(r.getBody().getData().getUser().getId()).isEqualTo(CURRENT_ID);
	}

	@Test
	void createApplication_linksEmploymentOwnedByCaller_evenWhenIdOver127() {
		PdlEmploymentInfo emp = new PdlEmploymentInfo();
		emp.setId(5);
		emp.setUser(user(CURRENT_ID)); // a DIFFERENT User object with the SAME id
		when(employmentRepo.findById(5)).thenReturn(Optional.of(emp));

		PdlApplicationPayload p = payload();
		p.setEmploymentInfoId(5);
		PaydayLoan loan = service.createApplication(p).getBody().getData();

		// With '==' on boxed Integer(200) this would be null (reference mismatch).
		assertThat(loan.getEmploymentInfo()).isSameAs(emp);
	}

	@Test
	void createApplication_doesNotLinkEmploymentOwnedByAnotherUser() {
		PdlEmploymentInfo emp = new PdlEmploymentInfo();
		emp.setId(7);
		emp.setUser(user(999)); // belongs to someone else
		when(employmentRepo.findById(7)).thenReturn(Optional.of(emp));

		PdlApplicationPayload p = payload();
		p.setEmploymentInfoId(7);
		PaydayLoan loan = service.createApplication(p).getBody().getData();

		assertThat(loan.getEmploymentInfo()).isNull(); // IDOR prevented
	}

	@Test
	void submit_blocksWhenAMandatoryDocumentIsMissing() {
		loanOwnedBy(CURRENT_ID, PdlStatusEnum.Draft);
		// No profile doc refs → personalRepo/bankRepo.findByUser return empty → gate blocks.
		ResponseEntity<Message<PaydayLoan>> r = service.submit(1);

		assertThat(r.getBody().getType()).isEqualTo("MISSING_DOCUMENT");
		verify(losProvider, never()).submitApplication(any());
	}

	@Test
	void submit_sendsToLosAndMarksSubmittedWhenProfileDocsPresent() {
		PaydayLoan loan = loanOwnedBy(CURRENT_ID, PdlStatusEnum.Draft);
		// V21: submit validates the 3 signup doc refs on the profile, not per-loan uploads.
		PdlPersonalInfo pi = new PdlPersonalInfo();
		pi.setNidFrontFileRef("nid.png");
		pi.setNidBackFileRef("nid-back.png");
		pi.setProfilePhotoFileRef("selfie.png");
		when(personalRepo.findByUser(CURRENT_ID)).thenReturn(List.of(pi));
		PdlEmploymentInfo ei = new PdlEmploymentInfo();
		ei.setEmploymentCardFileRef("empcard.png");
		when(employmentRepo.findByUser(CURRENT_ID)).thenReturn(List.of(ei));
		PdlBankInfo bi = new PdlBankInfo();
		bi.setBankStatementFileRef("stmt.png");
		when(bankRepo.findByUser(CURRENT_ID)).thenReturn(List.of(bi));
		when(losProvider.submitApplication(loan)).thenReturn("LOS-MOCK-1");

		PaydayLoan out = service.submit(1).getBody().getData();

		assertThat(out.getStatus()).isEqualTo(PdlStatusEnum.Submitted);
		assertThat(out.getLosApplicationNo()).isEqualTo("LOS-MOCK-1");
		verify(losProvider).submitApplication(loan);
	}

	@Test
	void submit_blocksWhenBankStatementRefMissing() {
		loanOwnedBy(CURRENT_ID, PdlStatusEnum.Draft);
		PdlPersonalInfo pi = new PdlPersonalInfo();
		pi.setNidFrontFileRef("nid.png");
		pi.setNidBackFileRef("nid-back.png");
		pi.setProfilePhotoFileRef("selfie.png");
		when(personalRepo.findByUser(CURRENT_ID)).thenReturn(List.of(pi));
		PdlEmploymentInfo ei2 = new PdlEmploymentInfo();
		ei2.setEmploymentCardFileRef("empcard.png");
		when(employmentRepo.findByUser(CURRENT_ID)).thenReturn(List.of(ei2));
		// bankRepo.findByUser → empty (no bank statement) → blocked.
		assertThat(service.submit(1).getBody().getType()).isEqualTo("MISSING_DOCUMENT");
		verify(losProvider, never()).submitApplication(any());
	}

	@Test
	void accept_yTransitionsApprovedToAccepted() {
		PaydayLoan loan = loanOwnedBy(CURRENT_ID, PdlStatusEnum.Approved);
		loan.setLosApplicationNo("LOS-MOCK-1");

		PaydayLoan out = service.accept(1, new PdlAcceptDecision("Y", "SIGNED-1")).getBody().getData();

		assertThat(out.getStatus()).isEqualTo(PdlStatusEnum.Accepted);
		assertThat(out.isAccepted()).isTrue();
		assertThat(out.getSignedContractRef()).isEqualTo("SIGNED-1");
		verify(losProvider).sendDecision("LOS-MOCK-1", "Y", "SIGNED-1");
	}

	@Test
	void accept_nRejectsTheLoan() {
		loanOwnedBy(CURRENT_ID, PdlStatusEnum.Approved);

		PaydayLoan out = service.accept(1, new PdlAcceptDecision("N", null)).getBody().getData();

		assertThat(out.getStatus()).isEqualTo(PdlStatusEnum.Rejected);
	}

	@Test
	void accept_isInvalidWhenNotApproved() {
		loanOwnedBy(CURRENT_ID, PdlStatusEnum.Submitted);

		ResponseEntity<Message<PaydayLoan>> r = service.accept(1, new PdlAcceptDecision("Y", "X"));

		assertThat(r.getBody().getType()).isEqualTo("INVALID");
	}

	@Test
	void getLoanById_returnsNotFoundWhenNotOwned_evenWhenIdOver127() {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(1);
		loan.setUser(user(999)); // owned by someone else
		when(repo.findById(1)).thenReturn(Optional.of(loan));

		ResponseEntity<Message<PaydayLoan>> r = service.getLoanById(1);

		assertThat(r.getBody().getType()).isEqualTo("NOT_FOUND");
	}

	@Test
	void getLoanById_returnsLoanWhenOwned_evenWhenIdOver127() {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(1);
		loan.setUser(user(CURRENT_ID)); // same id, different object — '!=' would falsely reject
		when(repo.findById(1)).thenReturn(Optional.of(loan));

		ResponseEntity<Message<PaydayLoan>> r = service.getLoanById(1);

		assertThat(r.getBody().getType()).isEqualTo("SUCCESS");
		assertThat(r.getBody().getData()).isSameAs(loan);
	}

	@Test
	void saveBankInfo_upsertsExistingRowAndDoesNotSelfVerify() {
		PdlBankInfo existing = new PdlBankInfo();
		existing.setId(3);
		existing.setVerified(true); // pretend already verified server-side
		when(bankRepo.findByUser(CURRENT_ID)).thenReturn(List.of(existing));
		when(bankRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var req = new com.ezetik.kjeypapa.pdl.payload.BankInfoRequest();
		req.setBankName("Campu Bank");
		req.setAccountNo("001");
		PdlBankInfo out = service.saveBankInfo(req).getBody().getData();

		assertThat(out.getId()).isEqualTo(3); // upsert (same row), not a new insert
		assertThat(out.getBankName()).isEqualTo("Campu Bank");
		// verified is server-controlled — the request can't unset/keep-control of it
		assertThat(out.isVerified()).isTrue();
	}

	// ---- IDOR ownership regression tests (every per-id mutator must be scoped) ----

	private PaydayLoan loanOwnedBy(int ownerId, PdlStatusEnum status) {
		PaydayLoan loan = new PaydayLoan();
		loan.setId(1);
		loan.setUser(user(ownerId));
		loan.setStatus(status);
		when(repo.findById(1)).thenReturn(Optional.of(loan));
		return loan;
	}

	@Test
	void submit_returnsNotFoundForAnotherUsersLoan() {
		loanOwnedBy(999, PdlStatusEnum.Draft);
		assertThat(service.submit(1).getBody().getType()).isEqualTo("NOT_FOUND");
		verify(losProvider, never()).submitApplication(any());
	}

	@Test
	void accept_returnsNotFoundForAnotherUsersLoan() {
		loanOwnedBy(999, PdlStatusEnum.Approved);
		assertThat(service.accept(1, new PdlAcceptDecision("Y", "X")).getBody().getType()).isEqualTo("NOT_FOUND");
		verify(losProvider, never()).sendDecision(any(), any(), any());
	}

	@Test
	void accept_invalidDecisionDoesNotMutateOrNotifyLos() {
		PaydayLoan loan = loanOwnedBy(CURRENT_ID, PdlStatusEnum.Approved);
		ResponseEntity<Message<PaydayLoan>> r = service.accept(1, new PdlAcceptDecision(null, null));
		assertThat(r.getBody().getType()).isEqualTo("INVALID");
		assertThat(loan.getStatus()).isEqualTo(PdlStatusEnum.Approved); // unchanged
		verify(losProvider, never()).sendDecision(any(), any(), any());
	}

	@Test
	void revoke_returnsNotFoundForAnotherUsersLoan() {
		loanOwnedBy(999, PdlStatusEnum.Draft);
		assertThat(service.revoke(1, "x").getBody().getType()).isEqualTo("NOT_FOUND");
	}

	@Test
	void revoke_isInvalidOnceDisbursed() {
		loanOwnedBy(CURRENT_ID, PdlStatusEnum.Disbursed);
		assertThat(service.revoke(1, "x").getBody().getType()).isEqualTo("INVALID");
	}

	@Test
	void getPaymentSchedule_returnsNotFoundForAnotherUsersLoan() {
		loanOwnedBy(999, PdlStatusEnum.Active);
		assertThat(service.getPaymentSchedule(1).getBody().getType()).isEqualTo("NOT_FOUND");
	}
}
