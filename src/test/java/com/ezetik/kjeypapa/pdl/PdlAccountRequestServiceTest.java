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
import com.ezetik.kjeypapa.pdl.service.SbfGatewayClient;
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
	@Mock SbfGatewayClient sbfGateway;
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
		when(sbfGateway.findCifByIdNo(anyString())).thenReturn(70); // a real CIF, which must not come back
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
	@DisplayName("a blank employment status is refused before any user is created or SMS sent")
	void employmentValidatedFirst() throws Exception {
		PdlAccountRequestPayload p = signup();
		p.getEmployment().setEmploymentStatus("");

		var body = service.create(p).getBody();

		assertThat(body.getType()).isEqualTo("INVALID");
		assertThat(body.getMessage()).contains("employment status is required");
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
}
