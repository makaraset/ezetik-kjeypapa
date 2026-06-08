package com.ezetik.kjeypapa.security.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Token {
	@JsonProperty("access_token")
	private String accessToken;

	@JsonProperty("expires_in")
	private long expiresIn;

	@JsonProperty("refresh_expires_in")
	private long refreshExpiresIn;

	@JsonProperty("refresh_token")
	private String refreshToken;

	@JsonProperty("token_type")
	private String tokenType;

	@JsonProperty("not-before-policy")
	private long notBeforePolicy;

	@JsonProperty("session_state")
	private String sessionState;

	private String organization;

	private String scope;

	private LocalDateTime expriresInDate;
	private LocalDateTime refreshExpiresInDate;

	public boolean isAccessTokenExprires() {
		return expriresInDate.isBefore(LocalDateTime.now());
	}

	public boolean isRefreshExpiresInDate() {
		return refreshExpiresInDate.isBefore(LocalDateTime.now());
	}
}
