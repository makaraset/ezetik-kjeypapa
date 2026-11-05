package com.ezetik.kjeypapa.pdl.service;

import java.util.List;

import lombok.Getter;

/**
 * A LOS submission that failed for a reason the customer or an operator needs
 * to see, as opposed to a transport error.
 *
 * <p>Carrying the detail on an exception is what lets
 * {@link LosProvider#submitApplication} keep its {@code String} return type —
 * the existing callers and tests are untouched.
 */
@Getter
public class LosSubmitException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * The request left us and we never learned what happened to it.
	 *
	 * <p>Kept apart from {@code LOS_UNAVAILABLE} because the two demand
	 * opposite responses. Unavailable means Sambat was never reached, so
	 * resubmitting is free. Unknown means they may well have filed a real credit
	 * application under a real customer's name — resubmitting then files it
	 * twice, and a duplicate credit enquiry is not something an apology fixes.
	 *
	 * <p>A loan carrying this code refuses to submit again until a person has
	 * checked Sambat's console and said which way it went.
	 */
	public static final String OUTCOME_UNKNOWN = "LOS_OUTCOME_UNKNOWN";

	/** Our own code, stored on the loan (e.g. {@code R-MISSINGDATA}). */
	private final String losCode;

	/** SBF field names it reported as missing; empty when not that kind of failure. */
	private final transient List<String> missingFields;

	public LosSubmitException(String losCode, String message) {
		this(losCode, message, List.of());
	}

	public LosSubmitException(String losCode, String message, List<String> missingFields) {
		super(message);
		this.losCode = losCode;
		this.missingFields = missingFields == null ? List.of() : missingFields;
	}
}
