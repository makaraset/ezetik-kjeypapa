package com.ezetik.kjeypapa.pdl.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * The customer's Sambat CIF: looked up by ID number, checked in full, stored
 * once, on {@code User.registedId}.
 *
 * <p>Sambat's answers of 2026-11-05 decide every branch:
 * <ul>
 * <li>they create a new customer's CIF when they approve the application;</li>
 * <li>custId 0 for a customer they already hold earns a second CIF or an
 * automatic reject, so 0 is sent only to someone they have looked for and not
 * found;</li>
 * <li>one ID number has one CIF, and a CIF never changes, so a stored CIF is
 * final and never looked up again;</li>
 * <li>the match is the full identity, not the ID number alone
 * ({@link SbfCustomerMatch}).</li>
 * </ul>
 */
@Slf4j
@Service
public class SbfCifResolver {

	public enum Outcome {
		/** Already held. A CIF never changes, so Sambat are not asked again. */
		STORED,
		/** Sambat hold this person, and every detail agrees. The CIF is stored. */
		MATCHED,
		/** Sambat hold no one with this ID number: the application goes with custId 0. */
		NEW,
		/** Sambat hold the ID number under other details, or the account holds a non-CIF. */
		MISMATCH,
		/** Sambat could not be asked, or their answer could not be read. */
		UNAVAILABLE
	}

	/**
	 * One check. {@code cif} is set for STORED and MATCHED, and for MISMATCH
	 * the CIF the number belongs to at Sambat, which the LPO may need but is
	 * never stored. {@code differences} names the fields that disagree.
	 */
	public record CifCheck(Outcome outcome, Long cif, List<String> differences) {
	}

	@Autowired
	private SbfGatewayClient sbf;

	@Autowired
	private PdlPersonalInfoRepository personalRepo;

	@Autowired
	private UserRepository userRepository;

	/** Looks the applicant up and compares. Stores nothing; {@code who} is for the log only. */
	public CifCheck check(SbfCustomerMatch.Applicant applicant, String who) {
		if (applicant == null || PersonalInfoValidator.trimToNull(applicant.idNo()) == null)
			return new CifCheck(Outcome.UNAVAILABLE, null, List.of());
		try {
			JsonNode rec = sbf.customerByIdNo(applicant.idNo().trim());
			if (rec == null)
				return new CifCheck(Outcome.NEW, null, List.of());
			JsonNode key = rec.get("custKeyNum");
			if (key == null || !key.canConvertToLong() || key.asLong() <= 0) {
				log.warn("Sambat's customer record for {} has no usable custKeyNum", who);
				return new CifCheck(Outcome.UNAVAILABLE, null, List.of());
			}
			List<String> diff = SbfCustomerMatch.differences(applicant, rec);
			if (!diff.isEmpty()) {
				log.warn("Sambat hold the ID number of {} as CIF {}, with a different {}", who, key.asLong(), diff);
				return new CifCheck(Outcome.MISMATCH, key.asLong(), diff);
			}
			return new CifCheck(Outcome.MATCHED, key.asLong(), List.of());
		} catch (Exception e) {
			log.warn("Sambat customer lookup failed for {}: {}", who, e.toString());
			return new CifCheck(Outcome.UNAVAILABLE, null, List.of());
		}
	}

	/** The user's CIF: the stored one, or looked up now and stored when it matches. */
	public CifCheck ensure(User user) {
		return ensure(user, first(personalRepo.findByUser(user.getId())));
	}

	public CifCheck ensure(User user, PdlPersonalInfo pi) {
		CifCheck held = held(user);
		if (held != null)
			return held;
		CifCheck c = check(pi == null ? null : SbfCustomerMatch.Applicant.of(pi), "user " + user.getId());
		if (c.outcome() == Outcome.MATCHED) {
			user.setRegistedId(String.valueOf(c.cif()));
			userRepository.save(user);
		}
		return c;
	}

	/** What the LPO sees: the stored CIF, or what a lookup finds now. Stores nothing. */
	public CifCheck status(User user, PdlPersonalInfo pi) {
		CifCheck held = held(user);
		if (held != null)
			return held;
		return check(pi == null ? null : SbfCustomerMatch.Applicant.of(pi), "user " + user.getId());
	}

	/** The stored CIF, or null when none is stored. */
	private static CifCheck held(User user) {
		String reg = PersonalInfoValidator.trimToNull(user.getRegistedId());
		if (reg == null)
			return null;
		try {
			return new CifCheck(Outcome.STORED, Long.parseLong(reg), List.of());
		} catch (NumberFormatException e) {
			// A merchant code or a staff username, not a CIF: filing it as 0
			// would guess, so the account waits for a person to look.
			return new CifCheck(Outcome.MISMATCH, null, List.of("registedId"));
		}
	}

	private static PdlPersonalInfo first(List<PdlPersonalInfo> l) {
		return l == null || l.isEmpty() ? null : l.get(0);
	}
}
