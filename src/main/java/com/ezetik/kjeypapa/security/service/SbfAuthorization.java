package com.ezetik.kjeypapa.security.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import com.ezetik.kjeypapa.security.model.Token;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Service
@SessionScope
@Data
public class SbfAuthorization {

	@Value("${token_endpoint}")
	private String token_endpoint;

	@Value("${urlencoded_token}")
	private String urlencoded_token;

	@Value("${urlencoded_refesh_token}")
	private String urlencoded_refesh_token;

	@Value("${authorization}")
	private String auth;

	@Autowired
	private ObjectMapper mapper;
	private Token token;

	public String getAccessToken() throws Exception {
		if (token == null) {
			fetchToken();
		} else {
			if (token.isAccessTokenExprires()) {
				if (token.isRefreshExpiresInDate())
					fetchToken();
				else
					refreshToken();
			}
		}

		return token.getAccessToken();
	}

	public void fetchToken() throws Exception {
		HttpRequest request = HttpRequest.newBuilder().uri(new URI(token_endpoint))
				.header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", auth)
				.POST(HttpRequest.BodyPublishers.ofString(urlencoded_token)).build();

		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 200) {
			token = mapper.readValue(response.body(), Token.class);
			token.setExpriresInDate(LocalDateTime.now().plusSeconds(token.getExpiresIn() - 10));
			token.setRefreshExpiresInDate(LocalDateTime.now().plusSeconds(token.getRefreshExpiresIn() - 10));
		} else {
			throw new Exception();
		}
	}

	public void refreshToken() throws Exception {
		HttpRequest request = HttpRequest.newBuilder().uri(new URI(token_endpoint))
				.header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", auth)
				.POST(HttpRequest.BodyPublishers.ofString(urlencoded_refesh_token + token.getRefreshToken())).build();
		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 200) {
			token = mapper.readValue(response.body(), Token.class);
			token.setExpriresInDate(LocalDateTime.now().plusSeconds(token.getExpiresIn() - 10));
			token.setRefreshExpiresInDate(LocalDateTime.now().plusSeconds(token.getRefreshExpiresIn() - 10));
		} else {
			throw new Exception();
		}
	}
}
