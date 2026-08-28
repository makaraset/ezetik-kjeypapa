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

	/** Entity tags that mark a PDL/KYC document (see PdlDocTypeEnum + signup). */
	private static final Set<String> PDL_TAGS = Set.of("PDL_ACCOUNT_REQUEST", "PDL_PROFILE_PHOTO",
			"PDL_NID_FRONT", "PDL_NID_BACK", "PDL_DOC", "E_CBC_CONSENT", "PROFILE_PHOTO", "NID",
			"EMPLOYMENT_CARD", "BANK_STATEMENT", "SIGNED_CONTRACT");

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
		return image != null && image.getEntityClass() != null
				&& PDL_TAGS.contains(image.getEntityClass().toUpperCase());
	}

	/** True when the current principal owns [image] or is an ADMIN. */
	public boolean canRead(Image image) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null)
			return false;
		if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
			return true;
		User me = userService.findUserByUsername(auth.getName());
		if (me == null)
			return false;
		String uid = String.valueOf(me.getId());
		String tag = image.getEntityClass() == null ? "" : image.getEntityClass().toUpperCase();
		String ref = image.getFileName();

		// Signup uploads are tagged with the owning user id directly.
		if (tag.startsWith("PDL_") && uid.equals(image.getEntityId()))
			return true;

		// Per-loan uploads are tagged with the loan id; the loan must be mine.
		if (image.getEntityId() != null) {
			try {
				PaydayLoan loan = loanRepo.findById(Integer.parseInt(image.getEntityId())).orElse(null);
				if (loan != null && loan.getUser() != null && Objects.equals(loan.getUser().getId(), me.getId()))
					return true;
			} catch (NumberFormatException ignore) {
			}
		}

		// Anything referenced from my own profile rows is mine.
		int id = me.getId();
		return personalRepo.findByUser(id).stream().anyMatch(p -> eq(ref, p.getNidFrontFileRef())
				|| eq(ref, p.getNidBackFileRef()) || eq(ref, p.getProfilePhotoFileRef()))
				|| employmentRepo.findByUser(id).stream().anyMatch(e -> eq(ref, e.getEmploymentCardFileRef()))
				|| bankRepo.findByUser(id).stream().anyMatch(b -> eq(ref, b.getBankStatementFileRef()));
	}

	private static boolean eq(String a, String b) {
		return a != null && a.equals(b);
	}
}
