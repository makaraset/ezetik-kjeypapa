package com.ezetik.kjeypapa.pdl.payload.los;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/** One entry of {@code LosPostResponse.Result}. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LosPostResult {

	/** LOS application id. int64 in swagger, so keep it wide. */
	@JsonProperty("AppId")
	private Long appId;

	/** The reference we persist as {@code losApplicationNo}. */
	@JsonProperty("AppRefId")
	private Integer appRefId;
}
