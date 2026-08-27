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

	private long appId;
	private long custId;
	private String doneBy = "";
	private NewApplicationRequest newAppRequest = new NewApplicationRequest();
}
