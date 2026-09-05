package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PdlDataRequest;
import com.ezetik.kjeypapa.pdl.model.PdlDataRequestStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlDataRequestTypeEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlDataDecisionPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlDataRequestPayload;
import com.ezetik.kjeypapa.pdl.repository.PdlDataRequestRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Data-rights requests: the customer side of the CBC consent.
 *
 * <p>The consent text tells the customer they may see what we hold, correct it
 * and ask for deletion. Until now there was no way to do any of that, which made
 * the promise decorative. This is the mechanism.
 *
 * <p>Withdrawal is not among them: Sambat ruled (2026-09-05) that the CBC
 * consent cannot be withdrawn, because the enquiry it authorises is made
 * automatically while the loan is assessed. See {@link #OFFERED}.
 *
 * <p>Nothing here acts automatically. Every request is recorded and routed to a
 * person; the value is that the request, its timing and its answer are all on
 * record.
 */
@Slf4j
@Service
public class PdlDataRequestService {

	/**
	 * The rights a customer may actually exercise here.
	 *
	 * <p>{@code WITHDRAW_CONSENT} is deliberately absent. Sambat ruled
	 * (2026-09-05) that the CBC consent is not withdrawable: the enquiry it
	 * authorises is made automatically while the loan is assessed, so by the
	 * time anyone could withdraw it there is nothing left to stop. Offering a
	 * control that cannot do what its name says is worse than not offering it —
	 * the customer would believe something had been undone.
	 *
	 * <p>Enforced here rather than only hidden in the app, because the endpoint
	 * is reachable without it.
	 */
	private static final Set<PdlDataRequestTypeEnum> OFFERED = Set.of(
			PdlDataRequestTypeEnum.ACCESS, PdlDataRequestTypeEnum.CORRECTION,
			PdlDataRequestTypeEnum.DELETION);

	/** Statuses that mean "somebody still owes this customer an answer". */
	private static final List<PdlDataRequestStatusEnum> OPEN = List.of(
			PdlDataRequestStatusEnum.PENDING, PdlDataRequestStatusEnum.IN_PROGRESS);

	@Autowired
	private PdlDataRequestRepository repo;

	@Autowired
	private UserService userService;

	// ----- customer -----

	/**
	 * Raise a request.
	 *
	 * <p>Refuses a second open request of the same type from the same customer.
	 * Not rate limiting: two open deletion requests are one obligation, and
	 * letting them accumulate means a queue where the count of outstanding
	 * requests no longer matches the number of people waiting.
	 */
	@Transactional
	public ResponseEntity<Message<PdlDataRequest>> create(PdlDataRequestPayload payload) {
		PdlDataRequestTypeEnum type = parseType(payload == null ? null : payload.getType());
		if (type == null || !OFFERED.contains(type))
			return resp("INVALID", "Unknown request type. Expected one of: " + OFFERED, null);

		User user = currentUser();
		boolean alreadyOpen = repo.findByUser_IdAndStatusIn(user.getId(), OPEN).stream()
				.anyMatch(r -> r.getType() == type);
		if (alreadyOpen)
			return resp("DUPLICATE",
					"You already have an open request of this type. We will respond to it.", null);

		PdlDataRequest req = new PdlDataRequest();
		req.setUser(user);
		req.setType(type);
		req.setStatus(PdlDataRequestStatusEnum.PENDING);
		req.setDetails(trim(payload.getDetails(), 2000));
		req.setRelatedLoanId(payload.getRelatedLoanId());
		PdlDataRequest saved = repo.save(req);

		// Worth a log line at INFO: these carry a response obligation, and the
		// first question after a missed one is when it arrived.
		log.info("PDL data request {} raised by user {} type {}", saved.getId(), user.getId(), type);
		return resp("SUCCESS", "Request received", saved);
	}

	/** The caller's own requests. Never anybody else's. */
	public ResponseEntity<Message<List<PdlDataRequest>>> mine() {
		return ResponseEntity.ok(new Message<>("SUCCESS", "OK",
				repo.findByUser_IdOrderByIdDesc(currentUser().getId())));
	}

	// ----- operator -----

	/** The queue: one status, or everything. */
	public ResponseEntity<Message<List<PdlDataRequest>>> list(String status) {
		if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
			return ResponseEntity.ok(new Message<>("SUCCESS", "OK", repo.findAllByOrderByIdDesc()));
		PdlDataRequestStatusEnum s = parseStatus(status);
		if (s == null)
			return ResponseEntity.ok(new Message<>("INVALID", "Unknown status: " + status, List.of()));
		return ResponseEntity.ok(new Message<>("SUCCESS", "OK", repo.findByStatusOrderByIdAsc(s)));
	}

	/**
	 * Record what was decided.
	 *
	 * <p>Follows {@code PdlAccountRequestService.decide()}: terminal states are
	 * one-shot, so a COMPLETED request cannot be quietly reopened and answered
	 * differently. IN_PROGRESS is not terminal — picking a request up and then
	 * finishing it is the normal path.
	 *
	 * @param decidedBy from the security context, never the request body
	 */
	@Transactional
	public ResponseEntity<Message<PdlDataRequest>> decide(int id, PdlDataDecisionPayload payload,
			String decidedBy) {
		PdlDataRequest req = repo.findById(id).orElse(null);
		if (req == null)
			return resp("NOT_FOUND", "Data request not found: " + id, null);

		PdlDataRequestStatusEnum target = parseStatus(payload == null ? null : payload.getStatus());
		if (target == null || target == PdlDataRequestStatusEnum.PENDING)
			return resp("INVALID", "Decision must be IN_PROGRESS, COMPLETED or REJECTED", null);

		if (req.getStatus() == PdlDataRequestStatusEnum.COMPLETED
				|| req.getStatus() == PdlDataRequestStatusEnum.REJECTED)
			return resp("INVALID", "Already answered: " + req.getStatus(), req);

		String reason = trim(payload.getReason(), 2000);
		// A refusal with no reason is not an answer, and it is the one case the
		// customer is most entitled to an explanation for.
		if (target == PdlDataRequestStatusEnum.REJECTED && (reason == null || reason.isBlank()))
			return resp("INVALID", "A rejection must say why", null);

		req.setStatus(target);
		req.setDecisionReason(reason);
		req.setDecidedBy(decidedBy);
		req.setDecidedDate(Instant.now());
		log.info("PDL data request {} -> {} by {}", id, target, decidedBy);
		return resp("SUCCESS", "Request updated", repo.save(req));
	}

	// ----- helpers -----

	private User currentUser() {
		String principal = SecurityContextHolder.getContext().getAuthentication().getName();
		User u = userService.findUserByUsername(principal);
		if (u == null)
			throw new IllegalStateException("Authenticated user not found: " + principal);
		return u;
	}

	private static PdlDataRequestTypeEnum parseType(String v) {
		if (v == null)
			return null;
		try {
			return PdlDataRequestTypeEnum.valueOf(v.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static PdlDataRequestStatusEnum parseStatus(String v) {
		if (v == null)
			return null;
		try {
			return PdlDataRequestStatusEnum.valueOf(v.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String trim(String v, int max) {
		if (v == null)
			return null;
		String s = v.trim();
		return s.length() <= max ? s : s.substring(0, max);
	}

	private static <T> ResponseEntity<Message<T>> resp(String type, String message, T data) {
		return new ResponseEntity<>(new Message<>(type, message, data),
				"SUCCESS".equals(type) ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED);
	}
}
