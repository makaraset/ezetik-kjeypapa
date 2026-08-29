package com.ezetik.kjeypapa.pdl.service;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;

/**
 * Decides whether the caller may read a stored PDL document.
 *
 * <p>Before 2026-08-28 {@code GET /api/v1/file/show/**} returned any image to
 * any authenticated user; file names are a timestamp plus a 5-digit random, so
 * a customer's NID photo was reachable by anyone with a login who could guess
 * the upload minute. A KYC document is readable by its owner and by an ADMIN,
 * and by nobody else.
 *
 * <p>Only PDL-tagged images are gated here; the TFF note documents keep their
 * existing behaviour.
 */
@Component
public class PdlDocumentAccess {

	@Autowired
	private UserService userService;
	@Autowired
	private PdlPersonalInfoRepository personalRepo;
	@Autowired
	private PdlEmploymentInfoRepository employmentRepo;
	@Autowired
	private PdlBankInfoRepository bankRepo;
	@Autowired
	private PaydayLoanRepository loanRepo;

	public boolean isPdlDocument(Image image) {
		if (image == null || image.getEntityClass() == null)
			return false;
		String tag = image.getEntityClass().toUpperCase();
		return SIGNUP_TAGS.contains(tag) || LOAN_TAGS.contains(tag);
	}

	/** Signup-time uploads: entity_id is the owning USER id. */
	private static final Set<String> SIGNUP_TAGS = Set.of("PDL_ACCOUNT_REQUEST", "PDL_PROFILE_PHOTO",
			"PDL_NID_FRONT", "PDL_NID_BACK", "PDL_DOC");

	/** Per-loan uploads (PdlDocTypeEnum): entity_id is a LOAN id. */
	private static final Set<String> LOAN_TAGS = Set.of("E_CBC_CONSENT", "NID", "EMPLOYMENT_CARD",
			"BANK_STATEMENT", "SIGNED_CONTRACT", "PROFILE_PHOTO");

	/**
	 * True when the current principal owns [image] or is an ADMIN.
	 *
	 * <p>The decision is made by what the tag MEANS, and each branch is
	 * terminal. The first version of this check fell through from the signup
	 * rule to the loan rule; user ids and loan ids share one integer sequence,
	 * so whoever owned loan #13 could read user #13's ID card. Found by the
	 * adversarial review of the change, live, before it shipped.
	 */
	public boolean canRead(Image image) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null)
			return false;
		if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
			return true;
		User me = userService.findUserByUsername(auth.getName());
		if (me == null)
			return false;
		String tag = image.getEntityClass() == null ? "" : image.getEntityClass().toUpperCase();
		String entityId = image.getEntityId() == null ? "" : image.getEntityId().trim();
		String ref = image.getFileName();

		if (SIGNUP_TAGS.contains(tag))
			return String.valueOf(me.getId()).equals(entityId) || referencedByMyProfile(me.getId(), ref);

		if (LOAN_TAGS.contains(tag)) {
			// PROFILE_PHOTO doubles as the generic account avatar, tagged with
			// the username rather than a loan id.
			if ("PROFILE_PHOTO".equals(tag) && me.getUsername() != null && me.getUsername().equals(entityId))
				return true;
			return ownsLoan(me, entityId) || referencedByMyProfile(me.getId(), ref);
		}
		return false;
	}

	private boolean ownsLoan(User me, String entityId) {
		try {
			PaydayLoan loan = loanRepo.findById(Integer.parseInt(entityId)).orElse(null);
			return loan != null && loan.getUser() != null && Objects.equals(loan.getUser().getId(), me.getId());
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Anything referenced from my own profile rows is mine, whatever its tag. */
	private boolean referencedByMyProfile(int id, String ref) {
		if (ref == null)
			return false;
		return personalRepo.findByUser(id).stream().anyMatch(p -> eq(ref, p.getNidFrontFileRef())
				|| eq(ref, p.getNidBackFileRef()) || eq(ref, p.getProfilePhotoFileRef()))
				|| employmentRepo.findByUser(id).stream().anyMatch(e -> eq(ref, e.getEmploymentCardFileRef()))
				|| bankRepo.findByUser(id).stream().anyMatch(b -> eq(ref, b.getBankStatementFileRef()));
	}

	private static boolean eq(String a, String b) {
		return a != null && a.equals(b);
	}
}
