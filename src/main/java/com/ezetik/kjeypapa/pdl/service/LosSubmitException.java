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
