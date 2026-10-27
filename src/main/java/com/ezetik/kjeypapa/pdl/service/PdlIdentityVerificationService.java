package com.ezetik.kjeypapa.pdl.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.service.ImageService;
import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerification;
import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerificationStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.IdentityVerificationSummary;
import com.ezetik.kjeypapa.pdl.payload.LivenessChallengeResponse;
import com.ezetik.kjeypapa.pdl.payload.LivenessResultResponse;
import com.ezetik.kjeypapa.pdl.payload.camdigi.CamDigiLivenessRequest;
import com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlIdentityVerificationRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * CamDigi NID-image + liveness verification of a pending account request
 * (SBF, 2026-10-27).
 *
 * <p><b>It runs after the request exists, against what was submitted.</b> SBF's
 * own journey puts e-KYC after OTP and before the LPO decision. The ID image is
 * the NID front the customer submitted and {@code userInfo} the identity they
 * confirmed, both read from our database; the app sends only the capture. So
 * CamDigi checks exactly what the LPO will approve, and this endpoint cannot be
 * used to test whether a stranger's name and birth date match their ID number.
 *
 * <p><b>Who may call it.</b> A PENDING customer cannot log in, so there is no
 * JWT. {@link #challenge} checks the request's own password instead, and is the
 * single way in, both right after OTP (the app still holds the credentials) and
 * on any later resume (from the login screen). The challenge id it returns is
 * the only credential {@link #submit} needs; we store only its hash.
 *
 * <p><b>Not {@code @Transactional}</b>, deliberately: no database connection is
 * held open across a CamDigi call that may take tens of seconds. Each
 * repository call is its own transaction; {@code claim} makes a submit atomic.
 *
 * <p>Off by default. What {@code liveness} must contain, the action names, the
 * {@code userInfo} formats and what {@code data} means are all undocumented —
 * see the questions to SBF in the PDL plan — so {@link #classify} is a
 * placeholder, and nothing may approve on its say-so until they answer.
 */
@Service
@lombok.extern.slf4j.Slf4j
public class PdlIdentityVerificationService {

	// --- outcome types, carried in Message.type (always HTTP 200: the app's
	// _post throws away the body of any other status) ---
	public static final String NOT_REQUIRED = "NOT_REQUIRED";
	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
	public static final String NOT_PENDING = "NOT_PENDING";
	public static final String OTP_REQUIRED = "OTP_REQUIRED";
	public static final String NOT_ELIGIBLE = "NOT_ELIGIBLE";
	public static final String ALREADY_VERIFIED = "ALREADY_VERIFIED";
	public static final String IN_PROGRESS = "IN_PROGRESS";
	public static final String ATTEMPTS_EXHAUSTED = "ATTEMPTS_EXHAUSTED";
	public static final String CHALLENGE = "CHALLENGE";
	public static final String EXPIRED = "EXPIRED";
	public static final String INVALID = "INVALID";
	public static final String PASSED = "PASSED";
	public static final String FAILED = "FAILED";
	public static final String ERROR = "ERROR";

	private static final Duration ATTEMPT_WINDOW = Duration.ofHours(24);
	private static final int EXCERPT_MAX_CHARS = 4096;

	// Code defaults equal the properties, and every switch is off: the OCR
	// mock's code default of true is the trap not to repeat.
	@Value("${pdl.liveness.enabled:false}")
	private boolean enabled;

	@Value("${pdl.liveness.mock:false}")
	private boolean mock;

	@Value("${pdl.liveness.required-for-approval:false}")
	private boolean requiredForApproval;

	/** Comma-separated action codes to sample from; empty until SBF names them. */
	@Value("${pdl.liveness.actions:}")
	private String actionPool;

	@Value("${pdl.liveness.actions-per-challenge:2}")
	private int actionsPerChallenge;

	@Value("${pdl.liveness.challenge-ttl-seconds:600}")
	private long challengeTtlSeconds;

	@Value("${pdl.liveness.max-attempts-per-day:5}")
	private int maxAttemptsPerDay;

	@Value("${pdl.liveness.max-bytes:15728640}")
	private long maxBytes;

	@Value("${pdl.liveness.timeout-seconds:45}")
	private int timeoutSeconds;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PdlAccountRequestRepository requests;

	@Autowired
	private PdlPersonalInfoRepository personalRepo;

	@Autowired
	private PdlIdentityVerificationRepository attempts;

	@Autowired
	private ImageService imageService;

	@Autowired
	private SbfGatewayClient sbf;

	@Autowired
	private LosSubmitConfig submitConfig;

	Clock clock = Clock.systemUTC();
	private final SecureRandom random = new SecureRandom();
	private volatile String dummyHash;

	/**
	 * Issues a single-use challenge for the caller's own pending request, or
	 * says why not. Every refusal here happens before anything reaches SBF.
	 */
	public ResponseEntity<Message<LivenessChallengeResponse>> challenge(String username, String password,
			String clientIp) {
		if (!enabled)
			return ok(NOT_REQUIRED, "Identity verification is not required.", null);

		User u = isBlank(username) ? null : users.findByUsername(username.trim());
		// Unknown user and wrong password get the same answer, and take the same
		// time: a BCrypt check runs either way, so neither reveals an account.
		String hash = u != null ? u.getPassword() : dummyHash();
		boolean matches = !isBlank(password) && hash != null && passwordEncoder.matches(password, hash);
		if (u == null || !matches)
			return ok(INVALID_CREDENTIALS, "Incorrect phone number or password.", null);

		PdlAccountRequest req = requests.findByUser_Username(u.getUsername()).orElse(null);
		if (req == null || req.getStatus() != PdlAccountStatusEnum.PENDING)
			return ok(NOT_PENDING, "There is no account request waiting for verification.", null);
		if (!u.isEnabled())
			return ok(OTP_REQUIRED, "Please verify your phone number first.", null);

		PdlPersonalInfo pi = personalInfo(req);
		List<String> gaps = CamDigiUserInfoMapper.missing(pi);
		if (!gaps.isEmpty() || nidImage(pi) == null) {
			log.warn("Liveness: request {} not eligible (missing {})", req.getId(),
					gaps.isEmpty() ? "a usable NID front image" : String.join(", ", gaps));
			return ok(NOT_ELIGIBLE, "Your application is missing details needed to verify you. "
					+ "Please call 023 99 77 22.", null);
		}
		if (hasPass(req.getId()))
			return ok(ALREADY_VERIFIED, "Your identity is already verified.", null);

		Instant now = clock.instant();
		if (inFlight(req.getId(), now))
			return ok(IN_PROGRESS, "Your last check is still being processed. Please wait a moment.", null);

		long used = attempts.countChargeableSince(req.getId(), now.minus(ATTEMPT_WINDOW));
		if (used >= maxAttemptsPerDay)
			return ok(ATTEMPTS_EXHAUSTED, "You have used all your attempts for today. Please try again "
					+ "tomorrow, or call 023 99 77 22.", null);

		// One open challenge per request: re-asking rotates it rather than piling up rows.
		List<PdlIdentityVerification> open = attempts.findByAccountRequestIdAndStatus(req.getId(),
				PdlIdentityVerificationStatusEnum.CHALLENGED);
		PdlIdentityVerification row = open.isEmpty() ? new PdlIdentityVerification() : open.get(0);
		for (int i = 1; i < open.size(); i++)
			attempts.delete(open.get(i));

		String challengeId = newChallengeId();
		List<String> actions = sampleActions();
		row.setAccountRequestId(req.getId());
		row.setStatus(PdlIdentityVerificationStatusEnum.CHALLENGED);
		row.setChallengeHash(ConsentHash.sha256(challengeId));
		row.setActions(String.join(",", actions));
		row.setChallengeExpiresAt(now.plusSeconds(challengeTtlSeconds));
		row.setClientIp(clientIp);
		row.setMock(false);
		attempts.save(row);
		log.info("Liveness: challenge issued for request {} ({} actions)", req.getId(), actions.size());

		return ok(CHALLENGE, "Follow the instructions on screen.",
				new LivenessChallengeResponse(challengeId, actions, challengeTtlSeconds,
						(int) Math.max(0, maxAttemptsPerDay - used)));
	}

	/** Verifies one capture against the challenge it answers. */
	public ResponseEntity<Message<LivenessResultResponse>> submit(String challengeId, String liveness,
			String mimeType, String clientIp) {
		if (!enabled)
			return result(NOT_REQUIRED, "Identity verification is not required.", 0);

		Instant now = clock.instant();
		PdlIdentityVerification row = isBlank(challengeId) ? null
				: attempts.findByChallengeHash(ConsentHash.sha256(challengeId.trim())).orElse(null);
		if (row == null || row.getStatus() != PdlIdentityVerificationStatusEnum.CHALLENGED
				|| now.isAfter(row.getChallengeExpiresAt()))
			return result(EXPIRED, "This check has expired. Please start again.", 0);

		PdlAccountRequest req = requests.findById(row.getAccountRequestId()).orElse(null);
		if (req == null || req.getStatus() != PdlAccountStatusEnum.PENDING)
			return result(NOT_PENDING, "There is no account request waiting for verification.", 0);

		long used = attempts.countChargeableSince(req.getId(), now.minus(ATTEMPT_WINDOW));
		if (used >= maxAttemptsPerDay)
			return result(ATTEMPTS_EXHAUSTED, "You have used all your attempts for today. Please try again "
					+ "tomorrow, or call 023 99 77 22.", 0);
		int leftAfterThis = (int) Math.max(0, maxAttemptsPerDay - used - 1);

		// The capture is checked before the claim, so an unreadable upload never
		// spends one of the customer's attempts.
		byte[] capture = decode(liveness);
		if (capture == null || capture.length == 0)
			return result(INVALID, "The capture could not be read. Please try again.", leftAfterThis + 1);
		if (capture.length > maxBytes)
			return result(INVALID, "The capture is too large. Please try again.", leftAfterThis + 1);

		if (attempts.claim(row.getId(), PdlIdentityVerificationStatusEnum.CHALLENGED,
				PdlIdentityVerificationStatusEnum.IN_FLIGHT, now) != 1)
			return result(IN_PROGRESS, "This check is already being processed.", leftAfterThis + 1);
		row.setStatus(PdlIdentityVerificationStatusEnum.IN_FLIGHT);
		row.setSubmittedAt(now);
		row.setLivenessBytes(capture.length);
		row.setClientIp(clientIp);
		log.info("Liveness: request {} submitted {} bytes ({})", req.getId(), capture.length,
				isBlank(mimeType) ? "type not given" : mimeType.trim());

		// Never "" and never a username: a username in doneBy is what produced
		// Sambat's opaque 500s on /new-loan-application in September.
		String doneBy = submitConfig.getDoneBy();
		if (isBlank(doneBy)) {
			log.error("Liveness: los.done-by is not set; refusing to call CamDigi for request {}", req.getId());
			return finish(row, PdlIdentityVerificationStatusEnum.ERROR, "NOT_CONFIGURED", leftAfterThis + 1);
		}

		if (mock) {
			row.setMock(true);
			log.warn("Liveness: MOCK pass for request {} (pdl.liveness.mock=true)", req.getId());
			return finish(row, PdlIdentityVerificationStatusEnum.PASSED, null, leftAfterThis);
		}

		PdlPersonalInfo pi = personalInfo(req);
		byte[] nid = nidImage(pi);
		if (nid == null || !CamDigiUserInfoMapper.missing(pi).isEmpty())
			return finish(row, PdlIdentityVerificationStatusEnum.ERROR, "NOT_ELIGIBLE", leftAfterThis + 1);

		CamDigiLivenessRequest body = new CamDigiLivenessRequest(actionsOf(row), doneBy.trim(),
				Base64.getEncoder().encodeToString(nid), Base64.getEncoder().encodeToString(capture),
				CamDigiUserInfoMapper.from(pi));
		long t0 = System.nanoTime();
		try {
			JsonNode reply = sbf.verifiedInfoIdImageLiveness(body);
			row.setLatencyMs(millisSince(t0));
			row.setHttpStatus(200);
			JsonNode err = reply.path("error");
			row.setSbfError(err.isNumber() ? err.asInt() : null);
			row.setSbfMessage(cap(reply.path("message").asText(""), 500));
			row.setResponseExcerpt(ResponseExcerpt.of(reply, EXCERPT_MAX_CHARS));
			PdlIdentityVerificationStatusEnum outcome = classify(reply);
			return finish(row, outcome, outcome == PdlIdentityVerificationStatusEnum.ERROR ? "BAD_REPLY" : null,
					leftAfterThis);
		} catch (SbfCallException e) {
			row.setLatencyMs(millisSince(t0));
			row.setHttpStatus(e.getHttpStatus());
			row.setSbfMessage(cap(e.getDetail(), 500));
			// An attempt that never left us is not counted against the customer.
			int left = e.getKind() == SbfCallException.Kind.UNREACHABLE ? leftAfterThis + 1 : leftAfterThis;
			return finish(row, PdlIdentityVerificationStatusEnum.ERROR, e.getKind().name(), left);
		} catch (RuntimeException e) {
			row.setLatencyMs(millisSince(t0));
			log.error("Liveness: unexpected failure for request {}: {}", req.getId(), e.toString(), e);
			return finish(row, PdlIdentityVerificationStatusEnum.ERROR, "INTERNAL", leftAfterThis);
		}
	}

	/**
	 * PLACEHOLDER until SBF documents {@code data} and the error codes (plan,
	 * SBF question 4). Fails safe: only an explicit numeric {@code error} of 0
	 * passes; a reply with no {@code error} at all is unreadable, not a pass —
	 * the opposite of what OCR assumes, because here a wrong "yes" verifies a
	 * stranger.
	 */
	static PdlIdentityVerificationStatusEnum classify(JsonNode reply) {
		JsonNode err = reply == null ? null : reply.get("error");
		if (err == null || !err.isNumber())
			return PdlIdentityVerificationStatusEnum.ERROR;
		return err.asInt() == 0 ? PdlIdentityVerificationStatusEnum.PASSED : PdlIdentityVerificationStatusEnum.FAILED;
	}

	/**
	 * True when the LPO may not approve this request yet. A mock pass counts
	 * only while the mock is on, so switching it off can never leave a request
	 * looking verified by CamDigi.
	 */
	public boolean blocksApproval(PdlAccountRequest req) {
		if (!enabled || !requiredForApproval || req == null)
			return false;
		return !hasPass(req.getId());
	}

	/** What the LPO review shows for this request. */
	public IdentityVerificationSummary summary(int accountRequestId) {
		List<PdlIdentityVerification> all = attempts.findByAccountRequestIdOrderByIdDesc(accountRequestId);
		List<PdlIdentityVerification> submitted = all.stream().filter(v -> v.getSubmittedAt() != null).toList();
		PdlIdentityVerification pass = submitted.stream()
				.filter(v -> v.getStatus() == PdlIdentityVerificationStatusEnum.PASSED && (mock || !v.isMock()))
				.findFirst().orElse(null);
		PdlIdentityVerification shown = pass != null ? pass : submitted.isEmpty() ? null : submitted.get(0);
		boolean required = enabled && requiredForApproval;
		if (shown == null)
			return new IdentityVerificationSummary(enabled, required, "NOT_DONE", 0, null, null, null, null, null,
					false);
		return new IdentityVerificationSummary(enabled, required, shown.getStatus().name(), submitted.size(),
				shown.getSubmittedAt(), shown.getActions(), shown.getSbfError(), shown.getSbfMessage(),
				shown.getErrorKind(), shown.isMock());
	}

	// ------------------------------------------------------------------ helpers

	private boolean hasPass(int accountRequestId) {
		return attempts.existsByAccountRequestIdAndStatusAndMock(accountRequestId,
				PdlIdentityVerificationStatusEnum.PASSED, false)
				|| (mock && attempts.existsByAccountRequestIdAndStatusAndMock(accountRequestId,
						PdlIdentityVerificationStatusEnum.PASSED, true));
	}

	/**
	 * True while a submit is genuinely still running. One left IN_FLIGHT past
	 * the call's own timeout belongs to a process that died mid-call; it is
	 * closed as an ERROR so the customer is not stuck behind it.
	 */
	private boolean inFlight(int accountRequestId, Instant now) {
		boolean live = false;
		Instant cutoff = now.minusSeconds(timeoutSeconds + 60L);
		for (PdlIdentityVerification v : attempts.findByAccountRequestIdAndStatus(accountRequestId,
				PdlIdentityVerificationStatusEnum.IN_FLIGHT)) {
			if (v.getSubmittedAt() != null && v.getSubmittedAt().isAfter(cutoff)) {
				live = true;
			} else {
				v.setStatus(PdlIdentityVerificationStatusEnum.ERROR);
				v.setErrorKind("STALE");
				v.setCompletedAt(now);
				attempts.save(v);
			}
		}
		return live;
	}

	private ResponseEntity<Message<LivenessResultResponse>> finish(PdlIdentityVerification row,
			PdlIdentityVerificationStatusEnum outcome, String errorKind, int attemptsLeft) {
		row.setStatus(outcome);
		row.setErrorKind(errorKind);
		row.setCompletedAt(clock.instant());
		attempts.save(row);
		log.info("Liveness: request {} -> {}{} ({}, {} bytes, sbf error {})", row.getAccountRequestId(), outcome,
				errorKind == null ? "" : " " + errorKind,
				row.getLatencyMs() == null ? "no call made" : row.getLatencyMs() + " ms", row.getLivenessBytes(),
				row.getSbfError() == null ? "-" : row.getSbfError());
		return switch (outcome) {
		case PASSED -> result(PASSED, "Your identity has been verified.", attemptsLeft);
		case FAILED -> result(FAILED, "We could not verify you. Please try again in good light, with your "
				+ "face inside the frame.", attemptsLeft);
		default -> result(ERROR, "We could not complete the check right now. Please try again in a few "
				+ "minutes.", attemptsLeft);
		};
	}

	private PdlPersonalInfo personalInfo(PdlAccountRequest req) {
		if (req.getUser() == null)
			return null;
		List<PdlPersonalInfo> rows = personalRepo.findByUser(req.getUser().getId());
		return rows.isEmpty() ? null : rows.get(0);
	}

	/**
	 * The stored NID front, or null. Checked by its leading bytes rather than
	 * trusted: the app labels every document image/jpeg, but stores the
	 * original file unchanged when it cannot decode it (a HEIC from the
	 * gallery), and CamDigi should not be sent something it cannot read.
	 */
	private byte[] nidImage(PdlPersonalInfo pi) {
		if (pi == null || isBlank(pi.getNidFrontFileRef()))
			return null;
		Image image = imageService.findByFileName(pi.getNidFrontFileRef());
		byte[] b = image == null ? null : image.getData();
		return isJpegOrPng(b) ? b : null;
	}

	static boolean isJpegOrPng(byte[] b) {
		if (b == null || b.length < 4)
			return false;
		boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
		boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
		return jpeg || png;
	}

	private String newChallengeId() {
		byte[] b = new byte[32];
		random.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	/** A random selection from the configured pool, without repeats, in random order. */
	List<String> sampleActions() {
		List<String> pool = new ArrayList<>(Arrays.stream(actionPool == null ? new String[0] : actionPool.split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
		Collections.shuffle(pool, random);
		return List.copyOf(pool.subList(0, Math.max(0, Math.min(actionsPerChallenge, pool.size()))));
	}

	private static List<String> actionsOf(PdlIdentityVerification row) {
		String a = row.getActions();
		return isBlank(a) ? List.of() : Arrays.stream(a.split(",")).map(String::trim).toList();
	}

	/**
	 * Strict base64, whitespace tolerated. Not the MIME decoder: that one
	 * silently skips every character outside the alphabet, so garbage would
	 * decode to bytes and never be reported as unreadable.
	 */
	static byte[] decode(String base64) {
		if (isBlank(base64))
			return null;
		try {
			return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String dummyHash() {
		String h = dummyHash;
		if (h == null) {
			h = passwordEncoder.encode("not-a-real-password-" + random.nextLong());
			dummyHash = h;
		}
		return h;
	}

	private static int millisSince(long t0) {
		return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - t0) / 1_000_000L);
	}

	private static String cap(String s, int max) {
		if (s == null)
			return null;
		return s.length() <= max ? s : s.substring(0, max);
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static <T> ResponseEntity<Message<T>> ok(String type, String message, T data) {
		return new ResponseEntity<>(new Message<>(type, message, data), HttpStatus.OK);
	}

	private static ResponseEntity<Message<LivenessResultResponse>> result(String type, String message,
			int attemptsLeft) {
		return ok(type, message, new LivenessResultResponse(type, Math.max(0, attemptsLeft), message));
	}
}
