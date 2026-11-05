package com.ezetik.kjeypapa.pdl.payload.los;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/** One entry of {@code LosPostResponse.Result}. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LosPostResult {

	/**
	 * Sambat's LOS application id. int64 in swagger, so keep it wide. They are
	 * renaming it LosAppId (2026-11-05); either name is read.
	 */
	@JsonProperty("AppId")
	@JsonAlias("LosAppId")
	private Long losAppId;

	/** The reference we persist as {@code losApplicationNo}. */
	@JsonProperty("AppRefId")
	private Integer appRefId;
}
