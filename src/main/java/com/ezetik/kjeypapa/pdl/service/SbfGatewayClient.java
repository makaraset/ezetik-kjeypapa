package com.ezetik.kjeypapa.pdl.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal singleton client for the SBF tricube gateway (UAT contract captured
 * in docs/sbf_tricube_uat_api_docs.json). Reuses the same credential
 * properties as the session-scoped {@link com.ezetik.kjeypapa.security.service.SbfAuthorization}
 * but caches the bearer app-wide, since PDL calls (NID OCR at signup, CIF
 * lookup) run outside an authenticated session.
 */
@Component
public class SbfGatewayClient {

	@Value("${token_endpoint}")
	private String tokenEndpoint;

	@Value("${urlencoded_token}")
	private String urlencodedToken;

	@Value("${authorization}")
	private String basicAuth;

	@Value("${url_api}")
	private String urlApi;

	@Autowired
	private ObjectMapper mapper;

	private String accessToken;
	private Instant tokenExpiry = Instant.EPOCH;

	private final HttpClient http = HttpClient.newHttpClient();

	private synchronized String token() throws Exception {
		if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
			HttpRequest req = HttpRequest.newBuilder().uri(new URI(tokenEndpoint))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Authorization", basicAuth)
					.POST(HttpRequest.BodyPublishers.ofString(urlencodedToken)).build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200)
				throw new IllegalStateException("SBF token endpoint returned " + resp.statusCode());
			JsonNode t = mapper.readTree(resp.body());
			accessToken = t.get("access_token").asText();
			tokenExpiry = Instant.now().plusSeconds(Math.max(60, t.path("expires_in").asLong(300) - 60));
		}
		return accessToken;
	}

	/** {@code POST /ocr-id-card} — SBF/CamDigi NID extraction. */
	public JsonNode ocrIdCard(String imageBase64) throws Exception {
		String body = mapper.writeValueAsString(mapper.createObjectNode().put("idImage", imageBase64));
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(urlApi + "/ocr-id-card"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token())
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /ocr-id-card returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /customer-balance?accountNo=} — one saving/settlement account
	 * with its live core-banking balance. Null when SBF has no such account.
	 */
	public JsonNode savingByAccountNo(String accountNo) throws Exception {
		String url = urlApi + "/customer-balance?accountNo="
				+ URLEncoder.encode(accountNo, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(url))
				.header("Authorization", "Bearer " + token()).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /customer-balance returned " + resp.statusCode());
		if (resp.body() == null || resp.body().isBlank())
			return null;
		JsonNode n = mapper.readTree(resp.body());
		return (n == null || n.isNull() || n.path("accountNo").asText("").isBlank()) ? null : n;
	}

	/**
	 * {@code GET /saving-info-by-cid?cifNo=} — all saving/settlement accounts
	 * for a CIF (TFF's by-cid pattern). Empty array when none exist yet.
	 */
	public JsonNode savingsByCif(int cifNo) throws Exception {
		String url = urlApi + "/saving-info-by-cid?cifNo=" + cifNo;
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(url))
				.header("Authorization", "Bearer " + token()).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /saving-info-by-cid returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /customer-information/by-idno} — resolve the SBF CIF
	 * (custKeyNum) for an ID number. Returns null when the customer is new to
	 * SBF (loan submits then carry {@code custId: 0}).
	 */
	public Integer findCifByIdNo(String idNo) throws Exception {
		String url = urlApi + "/customer-information/by-idno?idNo="
				+ URLEncoder.encode(idNo, StandardCharsets.UTF_8) + "&page=0&size=1";
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(url))
				.header("Authorization", "Bearer " + token()).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /customer-information/by-idno returned " + resp.statusCode());
		JsonNode page = mapper.readTree(resp.body());
		JsonNode content = page.path("content");
		if (content.isArray() && content.size() > 0) {
			JsonNode cif = content.get(0).path("custKeyNum");
			return cif.isNumber() ? cif.asInt() : null;
		}
		return null;
	}
}
