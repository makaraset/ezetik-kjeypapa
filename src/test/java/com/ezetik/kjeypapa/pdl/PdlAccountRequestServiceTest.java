package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestCreated;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestPayload;
import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.service.PdlAccountRequestService;
import com.ezetik.kjeypapa.pdl.service.PdlIdentityVerificationService;
import com.ezetik.kjeypapa.pdl.service.SbfCifResolver;
import com.ezetik.kjeypapa.sbf.service.SMSService;
import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.RoleRepository;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.service.OneTimePasswordService;

/** Signup's response, its validation order, and the LPO approval gate. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlAccountRequestServiceTest {

	@Mock UserRepository userRepository;
	@Mock RoleRepository roleRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock PdlAccountRequestRepository requestRepo;
	@Mock PdlPersonalInfoRepository personalRepo;
	@Mock SbfCifResolver cifResolver;
	@Mock PdlEmploymentInfoRepository employmentRepo;
	@Mock PdlBankInfoRepository bankRepo;
	@Mock ImageRepository imageRepo;
	@Mock OneTimePasswordService otpService;
	@Mock SMSService sms;
	@Mock PdlIdentityVerificationService verification;
	@InjectMocks PdlAccountRequestService service;

	private PdlAccountRequest pending;
	private User applicant;

	@BeforeEach
	void setUp() throws Exception {
		applicant = new User();
		applicant.setId(42);
		applicant.setPhoneNumber("010849001");
		applicant.setEnabled(false);
		pending = new PdlAccountRequest();
		pending.setId(7);
		pending.setUser(applicant);
		pending.setStatus(PdlAccountStatusEnum.PENDING);
		when(requestRepo.findById(7)).thenReturn(Optional.of(pending));

		when(userRepository.findByUsername(anyString())).thenReturn(null);
		when(userRepository.save(any(User.class))).thenAnswer(i -> {
			User u = i.getArgument(0);
			u.setId(42);
			return u;
		});
		when(roleRepository.findById(2L)).thenReturn(Optional.of(new Role()));
		when(passwordEncoder.encode(anyString())).thenReturn("$hash");
		when(personalRepo.findByIdNo(anyString())).thenReturn(List.of());
		// A real CIF, which must not come back.
		when(cifResolver.check(any(), anyString())).thenReturn(
				new SbfCifResolver.CifCheck(SbfCifResolver.Outcome.MATCHED, 70L, List.of()));
		when(requestRepo.save(any(PdlAccountRequest.class))).thenAnswer(i -> {
			PdlAccountRequest r = i.getArgument(0);
			if (r.getId() == null)
				r.setId(7);
			return r;
		});
	}

	private PdlAccountRequestPayload signup() {
		PersonalInfoRequest p = new PersonalInfoRequest();
		p.setLatinFamilyName("SET");
		p.setLatinFirstName("MAKARA");
		p.setGender("M");
		p.setDateOfBirth("03/01/1990");
		p.setIdNo("110553867");
		p.setMobilePhone("010849001");
		p.setCorrProvinceCode("12");
		p.setCorrDistrictCode("1214");
		p.setCorrCommuneCode("121402");
		p.setCorrVillageCode("12140204");
		p.setPermProvinceCode("12");
		p.setPermDistrictCode("1214");
		p.setPermCommuneCode("121402");
		p.setPermVillageCode("12140204");
		EmploymentInfoRequest e = new EmploymentInfoRequest();
		e.setEmploymentType("Employee");
		e.setEmploymentStatus("Probation");
		e.setEmploymentStartDate(Instant.parse("2023-04-30T17:00:00Z"));
		e.setWorkProvinceCode("12");
		e.setWorkDistrictCode("1214");
		e.setWorkCommuneCode("121402");
		e.setWorkVillageCode("12140204");
		PdlAccountRequestPayload payload = new PdlAccountRequestPayload();
		payload.setUsername("010849001");
		payload.setPassword("secret1");
		payload.setPersonal(p);
		payload.setEmployment(e);
		return payload;
	}

	@Test
	@DisplayName("signup answers with the request id and status only — the CIF it looked up never leaves")
	void createDoesNotLeakTheCif() {
		var body = service.create(signup()).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(body.getData()).isEqualTo(new PdlAccountRequestCreated(7, "PENDING"));
		assertThat(Arrays.stream(PdlAccountRequestCreated.class.getRecordComponents()).map(RecordComponent::getName))
				.containsExactly("requestId", "status");
	}

	@Test
	@DisplayName("a blank employment status is refused before anything is written — even a rejected row's ID release")
	void employmentValidatedFirst() throws Exception {
		// A rejected earlier request holds this ID number. A valid retry would
		// release it; an invalid one must leave it exactly as it is, because
		// create() is transactional and a refusal returns rather than throws.
		User earlier = new User();
		earlier.setUsername("010849777");
		PdlPersonalInfo dead = new PdlPersonalInfo();
		dead.setUser(earlier);
		dead.setIdNo("110553867");
		PdlAccountRequest rejected = new PdlAccountRequest();
		rejected.setStatus(PdlAccountStatusEnum.REJECTED);
		when(personalRepo.findByIdNo("110553867")).thenReturn(List.of(dead));
		when(requestRepo.findByUser_Username("010849777")).thenReturn(Optional.of(rejected));

		PdlAccountRequestPayload p = signup();
		p.getEmployment().setEmploymentStatus("");

		var body = service.create(p).getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(body.getMessage()).contains("employment status is required");
		assertThat(dead.getIdNo()).isEqualTo("110553867");
		verify(personalRepo, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(sms, never()).sendSms(anyString(), anyString());
	}

	@Test
	@DisplayName("an unverified customer cannot be approved, and the refusal changes nothing")
	void approvalWaitsForVerification() {
		when(verification.blocksApproval(pending)).thenReturn(true);

		var body = service.decide(7, true, null, "lpo1", "E10002").getBody();

		assertThat(body.getType()).isEqualTo("IDENTITY_NOT_VERIFIED");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.PENDING);
		assertThat(applicant.isEnabled()).isFalse();
		verify(requestRepo, never()).save(any());
		verify(sms, never()).sendSms(anyString(), anyString());
	}

	@Test
	@DisplayName("an LPO can always reject")
	void rejectIsNeverGated() {
		when(verification.blocksApproval(pending)).thenReturn(true);

		var body = service.decide(7, false, "no", "lpo1", null).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.REJECTED);
		verify(verification, never()).blocksApproval(any());
	}

	@Test
	@DisplayName("with verification not required, approval proceeds as before")
	void approvesWhenNotGated() {
		when(verification.blocksApproval(pending)).thenReturn(false);

		var body = service.decide(7, true, null, "lpo1", null).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.APPROVED);
		assertThat(applicant.isEnabled()).isTrue();
	}

	// ----- the LPO confirms the CIF Sambat hold under other details -----

	/** What Sambat's lookup finds for the applicant when the LPO decides. */
	private void sambatHold(SbfCifResolver.Outcome outcome, Long cif, String... differences) {
		when(cifResolver.status(any(), any()))
				.thenReturn(new SbfCifResolver.CifCheck(outcome, cif, List.of(differences)));
	}

	/** A refusal leaves the request, the account and the customer's phone alone. */
	private void assertNothingDecided() {
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.PENDING);
		assertThat(pending.getDecidedBy()).isNull();
		assertThat(pending.getCifConfirmed()).isNull();
		assertThat(applicant.isEnabled()).isFalse();
		verify(requestRepo, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(sms, never()).sendSms(anyString(), anyString());
	}

	@Test
	@DisplayName("an LPO confirms the CIF Sambat hold under other details, and it is on record")
	void lpoConfirmsAMismatchedCif() {
		PdlPersonalInfo details = new PdlPersonalInfo();
		when(personalRepo.findByUser(42)).thenReturn(List.of(details));
		sambatHold(SbfCifResolver.Outcome.MISMATCH, 70L, "dateOfBirth", "name");

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.APPROVED);
		assertThat(applicant.isEnabled()).isTrue();
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		assertThat(pending.getCifConfirmed()).isEqualTo(70L);
		assertThat(pending.getCifConfirmedDifferences()).isEqualTo("dateOfBirth, name");
		assertThat(pending.getCifConfirmedBy()).isEqualTo("lpo1");
		assertThat(pending.getCifConfirmedDate()).isNotNull().isEqualTo(pending.getDecidedDate());
		// Sambat are asked about this applicant's own details, and the CIF is saved.
		verify(cifResolver).status(applicant, details);
		verify(userRepository).save(applicant);
	}

	@Test
	@DisplayName("a CIF that is not the one Sambat hold for the ID number is refused")
	void aCifSambatDoNotHoldForThisIdIsRefused() {
		// A mistyped number, or a review opened before Sambat's record changed.
		sambatHold(SbfCifResolver.Outcome.MISMATCH, 70L, "name");

		var body = service.decide(7, true, null, "lpo1", "E10002", 71L).getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("CIF 70").contains("not 71");
		assertThat(applicant.getRegistedId()).isNull();
		assertNothingDecided();
	}

	@Test
	@DisplayName("there is no CIF to confirm for someone Sambat do not hold")
	void aNewCustomerHasNoCifToConfirm() {
		sambatHold(SbfCifResolver.Outcome.NEW, null);

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("no customer with this ID number");
		assertThat(applicant.getRegistedId()).isNull();
		assertNothingDecided();
	}

	@Test
	@DisplayName("a confirmation is not taken while Sambat cannot be asked")
	void aConfirmationWaitsForSambat() {
		sambatHold(SbfCifResolver.Outcome.UNAVAILABLE, null);

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("could not be reached");
		assertThat(applicant.getRegistedId()).isNull();
		assertNothingDecided();
	}

	@Test
	@DisplayName("a stored CIF is never changed by a confirmation")
	void aStoredCifIsNotChanged() {
		applicant.setRegistedId("70");
		sambatHold(SbfCifResolver.Outcome.STORED, 70L);

		var body = service.decide(7, true, null, "lpo1", "E10002", 71L).getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("already has CIF 70");
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		assertNothingDecided();
	}

	@Test
	@DisplayName("confirming the CIF already stored approves, and records no confirmation")
	void confirmingTheStoredCifChangesNothing() {
		applicant.setRegistedId("70");
		sambatHold(SbfCifResolver.Outcome.STORED, 70L);

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.APPROVED);
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		assertThat(pending.getCifConfirmed()).isNull();
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("a full match found at approval is stored, and is no one's judgement")
	void aFullMatchIsStoredWithoutAConfirmation() {
		// Sambat corrected their record between the review and the decision.
		sambatHold(SbfCifResolver.Outcome.MATCHED, 70L);

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		assertThat(pending.getCifConfirmed()).isNull();
		assertThat(pending.getCifConfirmedBy()).isNull();
		verify(userRepository).save(applicant);
	}

	@Test
	@DisplayName("an account whose registered ID is not a CIF cannot have one confirmed")
	void aNonCifRegisteredIdIsRefused() {
		// A merchant code or a staff username sits where the CIF would go.
		applicant.setRegistedId("MER001");
		sambatHold(SbfCifResolver.Outcome.MISMATCH, null, "registedId");

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("not a CIF");
		assertThat(applicant.getRegistedId()).isEqualTo("MER001");
		assertNothingDecided();
	}

	@Test
	@DisplayName("a rejection confirms nothing, and Sambat are not asked")
	void aRejectionConfirmsNothing() {
		var body = service.decide(7, false, "not the same person", "lpo1", null, 70L).getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.REJECTED);
		assertThat(applicant.getRegistedId()).isNull();
		assertThat(pending.getCifConfirmed()).isNull();
		verify(cifResolver, never()).status(any(), any());
	}

	@Test
	@DisplayName("an approval without a confirmation does not ask Sambat, as before")
	void anApprovalWithoutAConfirmationDoesNotAskSambat() {
		// Absent, and 0: Swagger's sample value for a number.
		assertThat(service.decide(7, true, null, "lpo1", "E10002", null).getBody().getType()).isEqualTo("SUCCESS");
		pending.setStatus(PdlAccountStatusEnum.PENDING);
		assertThat(service.decide(7, true, null, "lpo1", "E10002", 0L).getBody().getType()).isEqualTo("SUCCESS");

		assertThat(applicant.getRegistedId()).isNull();
		assertThat(pending.getCifConfirmed()).isNull();
		verify(cifResolver, never()).status(any(), any());
	}

	@Test
	@DisplayName("identity verification is settled before Sambat are asked about a CIF")
	void theIdentityGateComesBeforeTheCif() {
		when(verification.blocksApproval(pending)).thenReturn(true);

		var body = service.decide(7, true, null, "lpo1", "E10002", 70L).getBody();

		assertThat(body.getType()).isEqualTo("IDENTITY_NOT_VERIFIED");
		verify(cifResolver, never()).status(any(), any());
		assertNothingDecided();
	}

	// ----- the CIF of a customer who is already approved -----

	/** The request as it stands after an approval that confirmed no CIF. */
	private void alreadyApproved() {
		pending.setStatus(PdlAccountStatusEnum.APPROVED);
		pending.setDecidedBy("lpo1");
		applicant.setEnabled(true);
	}

	@Test
	@DisplayName("an LPO confirms the CIF of a customer approved earlier, and it is on record")
	void lpoConfirmsForAnApprovedCustomer() {
		alreadyApproved();
		sambatHold(SbfCifResolver.Outcome.MISMATCH, 70L, "name");

		var body = service.confirmCif(7, 70L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(body.getData()).isEqualTo("70");
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		assertThat(pending.getCifConfirmed()).isEqualTo(70L);
		assertThat(pending.getCifConfirmedDifferences()).isEqualTo("name");
		assertThat(pending.getCifConfirmedBy()).isEqualTo("lpo2");
		assertThat(pending.getCifConfirmedDate()).isNotNull();
		// The decision itself is not rewritten, and the customer is not texted.
		assertThat(pending.getStatus()).isEqualTo(PdlAccountStatusEnum.APPROVED);
		assertThat(pending.getDecidedBy()).isEqualTo("lpo1");
		verify(requestRepo).save(pending);
		verify(userRepository).save(applicant);
		verify(sms, never()).sendSms(anyString(), anyString());
	}

	@Test
	@DisplayName("a pending request is settled on its approval, not here")
	void aPendingRequestIsNotConfirmedHere() {
		var body = service.confirmCif(7, 70L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(body.getMessage()).contains("when you approve it");
		assertThat(applicant.getRegistedId()).isNull();
		verify(cifResolver, never()).status(any(), any());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("a rejected request has no customer to confirm a CIF for")
	void aRejectedRequestIsNotConfirmed() {
		pending.setStatus(PdlAccountStatusEnum.REJECTED);

		var body = service.confirmCif(7, 70L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(applicant.getRegistedId()).isNull();
		verify(cifResolver, never()).status(any(), any());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("a request that does not exist is said not to exist")
	void anUnknownRequestIsNotFound() {
		when(requestRepo.findById(99)).thenReturn(Optional.empty());

		assertThat(service.confirmCif(99, 70L, "lpo2").getBody().getType()).isEqualTo("NOT_FOUND");
		verify(cifResolver, never()).status(any(), any());
	}

	@Test
	@DisplayName("for an approved customer too, only the CIF Sambat hold for the ID number is taken")
	void anApprovedCustomerGetsOnlySambatsCif() {
		alreadyApproved();
		sambatHold(SbfCifResolver.Outcome.MISMATCH, 70L, "name");

		var body = service.confirmCif(7, 71L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("CIF 70").contains("not 71");
		assertThat(applicant.getRegistedId()).isNull();
		assertThat(pending.getCifConfirmed()).isNull();
		verify(requestRepo, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("for an approved customer too, nothing is stored while Sambat cannot be asked")
	void anApprovedCustomerWaitsForSambat() {
		alreadyApproved();
		sambatHold(SbfCifResolver.Outcome.UNAVAILABLE, null);

		var body = service.confirmCif(7, 70L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(applicant.getRegistedId()).isNull();
		verify(requestRepo, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("confirming the CIF an approved customer already has writes nothing")
	void confirmingAnApprovedCustomersStoredCif() {
		alreadyApproved();
		applicant.setRegistedId("70");
		sambatHold(SbfCifResolver.Outcome.STORED, 70L);

		var body = service.confirmCif(7, 70L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("SUCCESS");
		assertThat(body.getMessage()).contains("already stored");
		assertThat(pending.getCifConfirmed()).isNull();
		verify(requestRepo, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("an approved customer's stored CIF is never replaced")
	void anApprovedCustomersStoredCifIsNotReplaced() {
		alreadyApproved();
		applicant.setRegistedId("70");
		sambatHold(SbfCifResolver.Outcome.STORED, 70L);

		var body = service.confirmCif(7, 71L, "lpo2").getBody();

		assertThat(body.getType()).isEqualTo("CIF_NOT_CONFIRMED");
		assertThat(body.getMessage()).contains("already has CIF 70");
		assertThat(applicant.getRegistedId()).isEqualTo("70");
		verify(userRepository, never()).save(any());
	}
}
