package com.ezetik.kjeypapa.pdl.payload.los;

import lombok.Getter;
import lombok.Setter;

/**
 * Envelope for {@code POST /new-loan-application}. Note the casing split: this
 * wrapper is camelCase while {@link NewApplicationRequest} inside it is
 * PascalCase.
 */
@Getter
@Setter
public class NewApplicationParam {

	/**
	 * Our own id for the application, the one key both sides use (agreed
	 * 2026-11-05): Sambat quote it back on every callback.
	 */
	private long mobileAppId;

	/** Sambat's LOS AppId: 0 creates an application, an existing one updates it. */
	private long appId;
	private long custId;
	private String doneBy = "";
	private NewApplicationRequest newAppRequest = new NewApplicationRequest();
}
