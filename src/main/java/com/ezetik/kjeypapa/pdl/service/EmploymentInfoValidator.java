package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;

/**
 * Server-side rules for an employment-info write: the counterpart of
 * {@link PersonalInfoValidator}, called from the same two write paths.
 *
 * <p>Until 2026-10-27 the backend stored whatever arrived. The app's Employment
 * Status dropdown displayed "Permanent / Confirmed" without ever saving it, so
 * every account SBF's testers created carried a blank status, which they
 * reported as "work status is required". Sambat's Appendix 2 also marks the
 * job start date (#45) and the four work-address codes (#24-28) Required, and
 * the payday product is for salaried employees only (V8 screen 7).
 */
public final class EmploymentInfoValidator {

	private EmploymentInfoValidator() {
	}

	/** The only employment type the payday product accepts (V8 screen 7). */
	public static final String EMPLOYEE = "Employee";

	/**
	 * The app's Employment Status options, stored as their canonical English
	 * value ({@code lib/constants/pdl_options.dart}, {@code employmentStatuses}).
	 * Our proposal to Sambat, not yet confirmed by them: change both sides
	 * together, which {@code EmploymentInfoValidatorTest} pins.
	 */
	public static final List<String> STATUSES = List.of("Permanent / Confirmed", "Probation",
			"Contract (fixed-term)", "Part-time", "Other");

	/** Trims, and fills what has exactly one right answer. */
	public static void normalise(EmploymentInfoRequest r) {
		if (r == null)
			return;
		String type = PersonalInfoValidator.trimToNull(r.getEmploymentType());
		r.setEmploymentType(type == null ? EMPLOYEE : type);
		r.setEmploymentStatus(PersonalInfoValidator.trimToNull(r.getEmploymentStatus()));
		r.setWorkCountry(PersonalInfoValidator.defaultCountry(r.getWorkCountry()));
	}

	/** Human-readable problems; empty means acceptable. */
	public static List<String> validate(EmploymentInfoRequest r) {
		List<String> e = new ArrayList<>();
		if (r == null) {
			e.add("employment information is required");
			return e;
		}
		// Self-employed filed loan applications carrying status 1 beside
		// employment type E and income type S, which contradict each other.
		if (!EMPLOYEE.equals(r.getEmploymentType()))
			e.add("the payday loan is for salaried employees: employment type must be " + EMPLOYEE);
		if (r.getEmploymentStatus() == null)
			e.add("employment status is required");
		else if (!STATUSES.contains(r.getEmploymentStatus()))
			e.add("employment status must be one of: " + String.join(", ", STATUSES));
		Instant start = r.getEmploymentStartDate();
		if (start == null)
			e.add("employment start date is required");
		else if (start.isAfter(Instant.now()))
			e.add("employment start date cannot be in the future");
		PersonalInfoValidator.missingAddress(e, "work", r.getWorkProvinceCode(), r.getWorkDistrictCode(),
				r.getWorkCommuneCode(), r.getWorkVillageCode());
		return e;
	}
}
