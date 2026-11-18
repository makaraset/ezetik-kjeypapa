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
	 * renaming it: "LosAppId" when we agreed it (2026-11-05), "losAppId" in the
	 * file they sent (2026-11-16). Names are matched by exact case, so every
	 * spelling is listed.
	 */
	@JsonProperty("AppId")
	@JsonAlias({ "LosAppId", "losAppId" })
	private Long losAppId;

	/** The reference we persist as {@code losApplicationNo}. */
	@JsonProperty("AppRefId")
	private Integer appRefId;
}
