package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.service.ImageService;
import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerification;
import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerificationStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.LivenessChallengeResponse;
import com.ezetik.kjeypapa.pdl.payload.LivenessResultResponse;
import com.ezetik.kjeypapa.pdl.payload.camdigi.CamDigiLivenessRequest;
import com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlIdentityVerificationRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

/** CamDigi verification: who may start one, and what each reply becomes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdlIdentityVerificationServiceTest {

	@Mock UserRepository users;
	@Mock PasswordEncoder passwordEncoder;
	@Mock PdlAccountRequestRepository requests;
	@Mock PdlPersonalInfoRepository personalRepo;
	@Mock PdlIdentityVerificationRepository attempts;
	@Mock ImageService imageService;
	@Mock SbfGatewayClient sbf;
	@Mock LosSubmitConfig submitConfig;
	@InjectMocks PdlIdentityVerificationService service;

	private static final byte[] NID_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4 };
	private static final String CAPTURE = Base64.getEncoder()
			.encodeToString(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9, 9 });
	private static final ObjectMapper JSON = new ObjectMapper();

	private User user;
	private PdlAccountRequest req;
	private PdlPersonalInfo pi;
	private Image nid;
	private final List<PdlIdentityVerification> saved = new ArrayList<>();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "enabled", true);
		ReflectionTestUtils.setField(service, "actionPool", "BLINK, TURN_LEFT,TURN_RIGHT , SMILE");
		ReflectionTestUtils.setField(service, "actionsPerChallenge", 2);
		ReflectionTestUtils.setField(service, "challengeTtlSeconds", 600L);
		ReflectionTestUtils.setField(service, "maxAttemptsPerDay", 5);
		ReflectionTestUtils.setField(service, "maxBytes", 15L * 1024 * 1024);
		ReflectionTestUtils.setField(service, "timeoutSeconds", 45);

		user = new User();
		user.setId(42);
		user.setUsername("010849001");
		user.setPassword("$stored-hash");
		user.setEnabled(true);
		req = new PdlAccountRequest();
		req.setId(7);
		req.setUser(user);
		req.setStatus(PdlAccountStatusEnum.PENDING);
		pi = new PdlPersonalInfo();
		pi.setIdNo("110553867");
		pi.setDateOfBirth("03/01/1990");
		pi.setGender("M");
		pi.setLatinFamilyName("SET");
		pi.setLatinFirstName("MAKARA");
		pi.setKhmerFamilyName("សិត");
		pi.setKhmerFirstName("មករា");
		pi.setIdIssuedDate("17/03/2025");
		pi.setIdExpiryDate("16/03/2035");
		pi.setNidFrontFileRef("pdl-nid.jpg");
		nid = Image.build();
		nid.setData(NID_JPEG);

		when(users.findByUsername("010849001")).thenReturn(user);
		when(passwordEncoder.matches("secret1", "$stored-hash")).thenReturn(true);
		when(passwordEncoder.encode(anyString())).thenReturn("$dummy-hash");
		when(requests.findByUser_Username("010849001")).thenReturn(Optional.of(req));
		when(requests.findById(7)).thenReturn(Optional.of(req));
		when(personalRepo.findByUser(42)).thenReturn(List.of(pi));
		when(imageService.findByFileName("pdl-nid.jpg")).thenReturn(nid);
		when(attempts.findByAccountRequestIdAndStatus(eq(7), any())).thenReturn(List.of());
		when(attempts.countChargeableSince(eq(7), any())).thenReturn(0L);
		when(attempts.claim(any(), any(), any(), any())).thenReturn(1);
		when(attempts.save(any(PdlIdentityVerification.class))).thenAnswer(i -> {
			PdlIdentityVerification v = i.getArgument(0);
			if (v.getId() == null)
				v.setId(100 + saved.size());
			saved.add(v);
			return v;
		});
		when(submitConfig.getDoneBy()).thenReturn("KjeyPapa");
	}

	private Message<LivenessChallengeResponse> challenge() {
		return service.challenge("010849001", "secret1", "203.0.113.9").getBody();
	}

	/** An open challenge, as a previous {@code challenge()} would have stored it. */
	private PdlIdentityVerification open(String challengeId) {
		PdlIdentityVerification v = new PdlIdentityVerification();
		v.setId(55);
		v.setAccountRequestId(7);
		v.setStatus(PdlIdentityVerificationStatusEnum.CHALLENGED);
		v.setChallengeHash(ConsentHash.sha256(challengeId));
		v.setActions("SMILE,BLINK");
		v.setChallengeExpiresAt(Instant.now().plusSeconds(300));
		when(attempts.findByChallengeHash(ConsentHash.sha256(challengeId))).thenReturn(Optional.of(v));
		return v;
	}

	private Message<LivenessResultResponse> submit(String challengeId) {
		return service.submit(challengeId, CAPTURE, "image/jpeg", "203.0.113.9").getBody();
	}

	// --- challenge: who may start one -------------------------------------

	@Test
	@DisplayName("switched off, it says so and touches nothing")
	void offByDefault() {
		ReflectionTestUtils.setField(service, "enabled", false);
		assertThat(challenge().getType()).isEqualTo("NOT_REQUIRED");
		assertThat(submit("anything").getType()).isEqualTo("NOT_REQUIRED");
		verify(users, never()).findByUsername(any());
		verify(attempts, never()).save(any());
	}

	@Test
	@DisplayName("every switch defaults to off in code, not only in the properties file")
	void everySwitchDefaultsOff() throws Exception {
		for (String field : List.of("enabled", "mock", "requiredForApproval")) {
			Value v = PdlIdentityVerificationService.class.getDeclaredField(field).getAnnotation(Value.class);
			assertThat(v.value()).as(field).endsWith(":false}");
		}
	}

	@Test
	@DisplayName("a wrong password and an unknown user get the same answer, after the same BCrypt work")
	void credentialsDoNotRevealAccounts() {
		var wrong = service.challenge("010849001", "guess", "x").getBody();
		when(users.findByUsername("099999999")).thenReturn(null);
		var unknown = service.challenge("099999999", "guess", "x").getBody();

		assertThat(wrong.getType()).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknown.getType()).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
		verify(passwordEncoder).matches("guess", "$stored-hash");
		verify(passwordEncoder).matches("guess", "$dummy-hash");
		verify(attempts, never()).save(any());
	}

	@Test
	@DisplayName("only a PENDING request is verified; an approved or rejected one is not")
	void pendingOnly() {
		req.setStatus(PdlAccountStatusEnum.APPROVED);
		assertThat(challenge().getType()).isEqualTo("NOT_PENDING");
		req.setStatus(PdlAccountStatusEnum.REJECTED);
		assertThat(challenge().getType()).isEqualTo("NOT_PENDING");
	}

	@Test
	@DisplayName("the phone must be verified first")
	void otpFirst() {
		user.setEnabled(false);
		assertThat(challenge().getType()).isEqualTo("OTP_REQUIRED");
	}

	@Test
	@DisplayName("no usable NID image means not eligible — a HEIC the app could not convert included")
	void needsAReadableNidImage() {
		nid.setData(new byte[] { 0, 0, 0, 0x18, 'f', 't', 'y', 'p' }); // HEIC
		assertThat(challenge().getType()).isEqualTo("NOT_ELIGIBLE");
		pi.setNidFrontFileRef(null);
		assertThat(challenge().getType()).isEqualTo("NOT_ELIGIBLE");
	}

	@Test
	@DisplayName("missing identity fields mean not eligible")
	void needsTheIdentity() {
		pi.setKhmerFirstName(" ");
		assertThat(challenge().getType()).isEqualTo("NOT_ELIGIBLE");
	}

	@Test
	@DisplayName("a pass is final")
	void passIsFinal() {
		when(attempts.existsByAccountRequestIdAndStatusAndMock(7, PdlIdentityVerificationStatusEnum.PASSED, false))
				.thenReturn(true);
		assertThat(challenge().getType()).isEqualTo("ALREADY_VERIFIED");
	}

	@Test
	@DisplayName("a live attempt blocks a new one; one left behind by a dead process is closed and does not")
	void inFlight() {
		PdlIdentityVerification live = new PdlIdentityVerification();
		live.setStatus(PdlIdentityVerificationStatusEnum.IN_FLIGHT);
		live.setSubmittedAt(Instant.now().minusSeconds(5));
		when(attempts.findByAccountRequestIdAndStatus(7, PdlIdentityVerificationStatusEnum.IN_FLIGHT))
				.thenReturn(List.of(live));
		assertThat(challenge().getType()).isEqualTo("IN_PROGRESS");

		live.setSubmittedAt(Instant.now().minusSeconds(600)); // far past the 45 s call timeout
		assertThat(challenge().getType()).isEqualTo("CHALLENGE");
		assertThat(live.getStatus()).isEqualTo(PdlIdentityVerificationStatusEnum.ERROR);
		assertThat(live.getErrorKind()).isEqualTo("STALE");
	}

	@Test
	@DisplayName("five chargeable attempts in 24 hours is the cap")
	void cap() {
		when(attempts.countChargeableSince(eq(7), any())).thenReturn(5L);
		assertThat(challenge().getType()).isEqualTo("ATTEMPTS_EXHAUSTED");
	}

	@Test
	@DisplayName("a challenge stores only the hash of its id, and draws distinct actions from the pool")
	void issuesAChallenge() {
		var body = challenge();

		assertThat(body.getType()).isEqualTo("CHALLENGE");
		LivenessChallengeResponse c = body.getData();
		assertThat(c.challengeId()).hasSizeGreaterThan(40);
		assertThat(c.actions()).hasSize(2).doesNotHaveDuplicates()
				.allMatch(a -> List.of("BLINK", "TURN_LEFT", "TURN_RIGHT", "SMILE").contains(a));
		assertThat(c.attemptsLeft()).isEqualTo(5);

		PdlIdentityVerification row = saved.get(saved.size() - 1);
		assertThat(row.getChallengeHash()).isEqualTo(ConsentHash.sha256(c.challengeId()))
				.isNotEqualTo(c.challengeId());
		assertThat(row.getStatus()).isEqualTo(PdlIdentityVerificationStatusEnum.CHALLENGED);
		assertThat(row.getActions()).isEqualTo(String.join(",", c.actions()));
		assertThat(row.getChallengeExpiresAt()).isAfter(Instant.now().plusSeconds(590));
	}

	@Test
	@DisplayName("asking again rotates the open challenge instead of adding one")
	void rotates() {
		PdlIdentityVerification previous = new PdlIdentityVerification();
		previous.setId(60);
		previous.setStatus(PdlIdentityVerificationStatusEnum.CHALLENGED);
		previous.setChallengeHash("old");
		when(attempts.findByAccountRequestIdAndStatus(7, PdlIdentityVerificationStatusEnum.CHALLENGED))
				.thenReturn(List.of(previous));

		var c = challenge().getData();

		assertThat(saved).containsExactly(previous);
		assertThat(previous.getChallengeHash()).isEqualTo(ConsentHash.sha256(c.challengeId()));
	}

	@Test
	@DisplayName("an empty action pool sends no actions rather than inventing names")
	void noActionsConfigured() {
		ReflectionTestUtils.setField(service, "actionPool", "");
		assertThat(challenge().getData().actions()).isEmpty();
	}

	// --- submit: what each reply becomes -----------------------------------

	@Test
	@DisplayName("an unknown, expired or used challenge never reaches CamDigi")
	void deadChallenges() throws Exception {
		assertThat(submit("never-issued").getType()).isEqualTo("EXPIRED");

		PdlIdentityVerification expired = open("expired");
		expired.setChallengeExpiresAt(Instant.now().minusSeconds(1));
		assertThat(submit("expired").getType()).isEqualTo("EXPIRED");

		PdlIdentityVerification used = open("used");
		used.setStatus(PdlIdentityVerificationStatusEnum.FAILED);
		assertThat(submit("used").getType()).isEqualTo("EXPIRED");

		verify(sbf, never()).verifiedInfoIdImageLiveness(any());
	}

	@Test
	@DisplayName("an unreadable capture is refused before the claim, so it costs no attempt")
	void unreadableCapture() throws Exception {
		open("c1");
		var r = service.submit("c1", "not base64 !!!", "image/jpeg", "x").getBody();

		assertThat(r.getType()).isEqualTo("INVALID");
		assertThat(r.getData().attemptsLeft()).isEqualTo(5);
		verify(attempts, never()).claim(any(), any(), any(), any());
		verify(sbf, never()).verifiedInfoIdImageLiveness(any());
	}

	@Test
	@DisplayName("of two submits of one challenge, the one that loses the claim does nothing")
	void lostClaim() throws Exception {
		open("c1");
		when(attempts.claim(any(), any(), any(), any())).thenReturn(0);
		assertThat(submit("c1").getType()).isEqualTo("IN_PROGRESS");
		verify(sbf, never()).verifiedInfoIdImageLiveness(any());
	}

	@Test
	@DisplayName("a blank doneBy is refused locally — never sent as \"\"")
	void blankDoneBy() throws Exception {
		open("c1");
		when(submitConfig.getDoneBy()).thenReturn(" ");
		var r = submit("c1");

		assertThat(r.getType()).isEqualTo("ERROR");
		assertThat(saved.get(saved.size() - 1).getErrorKind()).isEqualTo("NOT_CONFIGURED");
		assertThat(r.getData().attemptsLeft()).isEqualTo(5); // never left us, so not counted
		verify(sbf, never()).verifiedInfoIdImageLiveness(any());
	}

	@Test
	@DisplayName("the mock passes without calling CamDigi, and says it was the mock")
	void mock() throws Exception {
		ReflectionTestUtils.setField(service, "mock", true);
		PdlIdentityVerification row = open("c1");

		assertThat(submit("c1").getType()).isEqualTo("PASSED");
		assertThat(row.isMock()).isTrue();
		verify(sbf, never()).verifiedInfoIdImageLiveness(any());
	}

	@Test
	@DisplayName("CamDigi gets the stored NID image, the confirmed identity, doneBy KjeyPapa and the actions in order")
	void buildsTheRequestFromWhatWasSubmitted() throws Exception {
		open("c1");
		when(sbf.verifiedInfoIdImageLiveness(any())).thenReturn(JSON.readTree("{\"error\":0,\"message\":\"ok\"}"));

		submit("c1");

		ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
		verify(sbf).verifiedInfoIdImageLiveness(sent.capture());
		CamDigiLivenessRequest body = (CamDigiLivenessRequest) sent.getValue();
		assertThat(body.doneBy()).isEqualTo("KjeyPapa");
		assertThat(body.idImage()).isEqualTo(Base64.getEncoder().encodeToString(NID_JPEG));
		assertThat(body.liveness()).isEqualTo(CAPTURE);
		assertThat(body.actions()).containsExactly("SMILE", "BLINK");
		assertThat(body.userInfo().idNumber()).isEqualTo("110553867");
		assertThat(body.userInfo().lastNameEn()).isEqualTo("SET");
		assertThat(body.userInfo().firstNameKh()).isEqualTo("មករា");
		assertThat(body.toString()).doesNotContain(CAPTURE); // never logged whole
	}

	@Test
	@DisplayName("error 0 passes, any other error fails, and a reply without one is unreadable, not a pass")
	void classifies() throws Exception {
		open("a");
		when(sbf.verifiedInfoIdImageLiveness(any())).thenReturn(JSON.readTree("{\"error\":0}"));
		assertThat(submit("a").getType()).isEqualTo("PASSED");

		open("b");
		when(sbf.verifiedInfoIdImageLiveness(any()))
				.thenReturn(JSON.readTree("{\"error\":7,\"message\":\"face does not match\"}"));
		var failed = submit("b");
		assertThat(failed.getType()).isEqualTo("FAILED");
		assertThat(failed.getMessage()).doesNotContain("face does not match"); // SBF's words go to the LPO
		assertThat(saved.get(saved.size() - 1).getSbfMessage()).isEqualTo("face does not match");

		open("c");
		when(sbf.verifiedInfoIdImageLiveness(any())).thenReturn(JSON.readTree("{\"data\":{\"match\":true}}"));
		assertThat(submit("c").getType()).isEqualTo("ERROR");
		assertThat(saved.get(saved.size() - 1).getErrorKind()).isEqualTo("BAD_REPLY");
	}

	@Test
	@DisplayName("every gateway failure is an ERROR; only one that never left us leaves the attempt unspent")
	void gatewayFailures() throws Exception {
		for (SbfCallException.Kind kind : SbfCallException.Kind.values()) {
			open("k-" + kind);
			// doThrow, not when(...).thenThrow: re-stubbing via when() calls the
			// previous stub, which already throws.
			doThrow(new SbfCallException(kind, null, "x")).when(sbf).verifiedInfoIdImageLiveness(any());
			var r = submit("k-" + kind);
			assertThat(r.getType()).as(kind.name()).isEqualTo("ERROR");
			assertThat(saved.get(saved.size() - 1).getErrorKind()).isEqualTo(kind.name());
			int expectedLeft = kind == SbfCallException.Kind.UNREACHABLE ? 5 : 4;
			assertThat(r.getData().attemptsLeft()).as(kind.name()).isEqualTo(expectedLeft);
		}
	}

	@Test
	@DisplayName("a long string in CamDigi's reply (an echoed image) is not kept")
	void excerptIsRedacted() throws Exception {
		open("c1");
		String image = "A".repeat(5000);
		when(sbf.verifiedInfoIdImageLiveness(any()))
				.thenReturn(JSON.readTree("{\"error\":0,\"data\":{\"photo\":\"" + image + "\",\"score\":0.93}}"));
		submit("c1");
		String kept = saved.get(saved.size() - 1).getResponseExcerpt();
		assertThat(kept).doesNotContain(image).contains("<omitted 5000 chars>").contains("0.93");
	}

	// --- the approval gate -------------------------------------------------

	@Test
	@DisplayName("approval waits for a pass only when both switches are on")
	void gate() {
		assertThat(service.blocksApproval(req)).isFalse(); // required-for-approval off
		ReflectionTestUtils.setField(service, "requiredForApproval", true);
		assertThat(service.blocksApproval(req)).isTrue();
		when(attempts.existsByAccountRequestIdAndStatusAndMock(7, PdlIdentityVerificationStatusEnum.PASSED, false))
				.thenReturn(true);
		assertThat(service.blocksApproval(req)).isFalse();
	}

	@Test
	@DisplayName("a mock pass stops counting once the mock is off")
	void mockPassExpiresWithTheMock() {
		ReflectionTestUtils.setField(service, "requiredForApproval", true);
		when(attempts.existsByAccountRequestIdAndStatusAndMock(7, PdlIdentityVerificationStatusEnum.PASSED, true))
				.thenReturn(true);
		ReflectionTestUtils.setField(service, "mock", true);
		assertThat(service.blocksApproval(req)).isFalse();
		ReflectionTestUtils.setField(service, "mock", false);
		assertThat(service.blocksApproval(req)).isTrue();
	}

	// --- helpers -----------------------------------------------------------

	@Test
	@DisplayName("only JPEG and PNG count as a usable image")
	void imageSniffing() {
		assertThat(PdlIdentityVerificationService.isJpegOrPng(NID_JPEG)).isTrue();
		assertThat(PdlIdentityVerificationService
				.isJpegOrPng(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A })).isTrue();
		assertThat(PdlIdentityVerificationService.isJpegOrPng(new byte[] { 0, 0, 0, 0x18 })).isFalse();
		assertThat(PdlIdentityVerificationService.isJpegOrPng(null)).isFalse();
	}

	@Test
	@DisplayName("base64 is decoded strictly: line breaks are fine, garbage is not")
	void strictBase64() {
		assertThat(PdlIdentityVerificationService.decode("/9j/\n4AAQ")).isNotNull();
		assertThat(PdlIdentityVerificationService.decode("not base64 !!!")).isNull();
		assertThat(PdlIdentityVerificationService.decode("")).isNull();
	}
}
