package com.ezetik.kjeypapa.pdl.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
@lombok.extern.slf4j.Slf4j
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

	@Value("${los.url_api:${url_api}}")
	private String losUrlApi;

	/** Seconds allowed for a loan submit — a multi-megabyte document payload. */
	@Value("${los.submit.timeout-seconds:120}")
	private int submitTimeoutSeconds;

	/** Seconds allowed for a dictionary pull — the gazetteer is large. */
	@Value("${pdl.dictionary.timeout-seconds:180}")
	private int dictionaryTimeoutSeconds;

	/**
	 * Seconds allowed for NID OCR. It answers in about 3 s, but had no read
	 * timeout at all, so a stalled SBF held the request thread long after the
	 * app had given up at 30 s. Token (10 s) plus this stays under that.
	 */
	@Value("${pdl.ocr.timeout-seconds:18}")
	private int ocrTimeoutSeconds;

	/** Seconds allowed for the CamDigi check: a face match plus a registry lookup. */
	@Value("${pdl.liveness.timeout-seconds:45}")
	private int livenessTimeoutSeconds;

	/**
	 * Seconds allowed for a customer or account lookup. They had no read
	 * timeout, and the signup lookup runs inside the signup's transaction.
	 */
	@Value("${pdl.sbf.lookup-timeout-seconds:10}")
	private int lookupTimeoutSeconds;

	/**
	 * The token request had no timeout, and {@link #token()} is synchronized:
	 * one hung token call blocked every SBF caller in the application.
	 */
	private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(10);

	// A connect timeout matters: this client runs inside the request thread,
	// and a submit runs inside an open transaction, so a hung SBF would pin a
	// pool connection indefinitely while the app gives up at 30s.
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10)).build();

	private synchronized String token() throws Exception {
		if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
			HttpRequest req = HttpRequest.newBuilder().uri(new URI(tokenEndpoint))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Authorization", basicAuth)
					.timeout(TOKEN_TIMEOUT)
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
				.timeout(Duration.ofSeconds(ocrTimeoutSeconds))
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /ocr-id-card returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code POST /verified-info-idimage-liveness} (tag 18-CamDigi): CamDigi
	 * checks an identity against the national registry, the ID card image, and
	 * a liveness capture of the person.
	 *
	 * <p>Unlike the rest of this class, every failure comes back as an
	 * {@link SbfCallException} saying how far the call got. The caller records
	 * each attempt, and must not count our outage or SBF's as the customer's
	 * failed verification. It never retries: a verification is likely charged
	 * per call.
	 */
	public JsonNode verifiedInfoIdImageLiveness(Object body) throws SbfCallException {
		String json;
		try {
			json = mapper.writeValueAsString(body);
		} catch (Exception e) {
			throw new IllegalStateException("could not serialise the CamDigi request", e);
		}
		String bearer;
		try {
			bearer = token();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SbfCallException(SbfCallException.Kind.UNREACHABLE, null, "interrupted getting a token");
		} catch (Exception e) {
			throw new SbfCallException(SbfCallException.Kind.UNREACHABLE, null, "no token: " + e);
		}
		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlApi + "/verified-info-idimage-liveness"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + bearer)
				.timeout(Duration.ofSeconds(livenessTimeoutSeconds))
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
		HttpResponse<String> resp;
		try {
			resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		} catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
			throw new SbfCallException(SbfCallException.Kind.UNREACHABLE, null, e.toString());
		} catch (java.io.IOException e) {
			throw new SbfCallException(SbfCallException.Kind.NO_REPLY, null, e.toString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SbfCallException(SbfCallException.Kind.NO_REPLY, null, "interrupted");
		}
		int status = resp.statusCode();
		String text = resp.body() == null ? "" : resp.body();
		if (status >= 500)
			throw new SbfCallException(SbfCallException.Kind.SERVER_ERROR, status, head(text));
		if (status != 200)
			throw new SbfCallException(SbfCallException.Kind.REJECTED, status, head(text));
		JsonNode node;
		try {
			node = mapper.readTree(text);
		} catch (Exception e) {
			throw new SbfCallException(SbfCallException.Kind.BAD_REPLY, status, head(text));
		}
		if (node == null || !node.isObject())
			throw new SbfCallException(SbfCallException.Kind.BAD_REPLY, status, head(text));
		return node;
	}

	/** The first 512 characters: enough to say why, too short to carry an image. */
	private static String head(String s) {
		return s.length() <= 512 ? s : s.substring(0, 512);
	}

	/**
	 * {@code POST /customer-accepted} — tell Sambat the customer accepted their
	 * approved offer, which is what lets the application move to disbursement.
	 * Carries both keys while Sambat move to ours: {@code mobileAppId} (our
	 * id, agreed 2026-11-05) and their {@code AppId} (int32 in their swagger).
	 */
	public String customerAccepted(long mobileAppId, long appId, String doneBy, String smsText, String trnCode)
			throws Exception {
		var node = mapper.createObjectNode();
		node.put("mobileAppId", mobileAppId);
		node.put("appId", appId);
		node.put("doneBy", doneBy == null ? "" : doneBy);
		node.put("smsText", smsText == null ? "" : smsText);
		node.put("trnCode", trnCode == null ? "" : trnCode);
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(losUrlApi + "/customer-accepted"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(submitTimeoutSeconds))
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(node))).build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200 && resp.statusCode() != 201) {
			String detail = resp.body() == null ? ""
					: resp.body().substring(0, Math.min(512, resp.body().length()));
			throw new IllegalStateException(
					"SBF /customer-accepted returned " + resp.statusCode() + ": " + detail);
		}
		return resp.body();
	}

	/**
	 * {@code GET /customer-balance?accountNo=} — one saving/settlement account
	 * with its live core-banking balance. Null when SBF has no such account.
	 */
	public JsonNode savingByAccountNo(String accountNo) throws Exception {
		String url = urlApi + "/customer-balance?accountNo="
				+ URLEncoder.encode(accountNo, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(url))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(lookupTimeoutSeconds)).GET().build();
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
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(lookupTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /saving-info-by-cid returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code POST /new-loan-application} — file a loan application with LOS.
	 *
	 * <p>Deviates from this class's usual "throw on non-200 and discard the
	 * body" rule for two reasons specific to this endpoint: a rejection comes
	 * back as HTTP <b>200</b> carrying {@code IsSuccess: "False"} and the
	 * {@code MissingData} field list, so the body is the only thing that says
	 * whether it worked; and when the status really is non-200 the body
	 * usually explains why, so it is worth keeping.
	 */
	/** Sambat's gateway limit for a loan application (2026-09-03): 50 MB total. */
	private static final long LOS_MAX_REQUEST_BYTES = 50L * 1024 * 1024;

	public JsonNode newLoanApplication(Object param) throws Exception {
		String body = mapper.writeValueAsString(param);
		// Refuse locally rather than push an oversized request they will reject
		// opaquely — five phone-camera documents can grow past this once
		// base64-encoded, and the customer deserves a message they can act on.
		long bytes = body.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > LOS_MAX_REQUEST_BYTES)
			throw new LosSubmitException("LOS_REQUEST_TOO_LARGE",
					"The application documents are too large to send (" + (bytes / (1024 * 1024))
							+ " MB, limit 50 MB). Please retake the photos.");
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(losUrlApi + "/new-loan-application"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(submitTimeoutSeconds))
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		// Whether the request reached them decides whether a retry is safe, so the
		// two failures are told apart here rather than collapsed into one.
		HttpResponse<String> resp;
		try {
			resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		} catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
			// Never left our side. Nothing was filed; retrying costs nothing.
			log.warn("LOS submit: could not connect: {}", e.toString());
			throw new LosSubmitException("LOS_UNAVAILABLE", "Could not reach Sambat's loan system.");
		} catch (java.io.IOException e) {
			// The request WAS sent — a read timeout, a reset, a dropped
			// response. Sambat may hold a real application we have no record
			// of. This is the case that must never auto-retry.
			log.error("LOS submit: outcome unknown after sending ({} bytes): {}", bytes, e.toString());
			throw new LosSubmitException(LosSubmitException.OUTCOME_UNKNOWN,
					"We sent your application but did not get a reply from Sambat. We are "
							+ "confirming with them before sending it again, so it cannot be "
							+ "filed twice.");
		}
		if (resp.statusCode() != 200) {
			String detail = resp.body() == null ? ""
					: resp.body().substring(0, Math.min(512, resp.body().length()));
			// A 5xx means they received the request and something broke while
			// handling it. Whether an application was created is unknowable from
			// here, so it is treated like a lost reply rather than a refusal —
			// the alternative is a retry that files a second credit enquiry.
			// A 4xx is a refusal: they looked at the request and declined it,
			// so nothing was created and retrying is safe.
			if (resp.statusCode() >= 500) {
				log.error("LOS submit: {} from Sambat, outcome unknown: {}", resp.statusCode(), detail);
				throw new LosSubmitException(LosSubmitException.OUTCOME_UNKNOWN,
						"Sambat's loan system had a problem while receiving your application. We "
								+ "are confirming with them before sending it again, so it cannot "
								+ "be filed twice.");
			}
			throw new IllegalStateException(
					"SBF /new-loan-application returned " + resp.statusCode() + ": " + detail);
		}
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /bulk-selection-mini} — every "02- Selection dictionary" code
	 * list in one call (addresses and villages excluded; those come from
	 * {@link #allAddress()}).
	 *
	 * <p>One request beats nine: the lists are consistent with each other, and a
	 * partial refresh cannot leave districts pointing at provinces from a
	 * different snapshot. Carries an explicit read timeout because this is by
	 * far the largest response we fetch — the other GETs here have only a
	 * connect timeout, which would not save us from a stalled gazetteer.
	 */
	public JsonNode bulkSelectionMini() throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(urlApi + "/bulk-selection-mini"))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(dictionaryTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /bulk-selection-mini returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /all-address} — the whole gazetteer, denormalised one row per
	 * village, carrying every level's id AND its Khmer name.
	 *
	 * <p>This is the only source of Khmer names: {@code /province},
	 * {@code /district}, {@code /commune} and {@code /village} return an
	 * English {@code description} and nothing else. Verified complete against
	 * those four endpoints (25 / 208 / 1,652 / 16,226), so it replaces all of
	 * them.
	 */
	public JsonNode allAddress() throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(urlApi + "/all-address"))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(dictionaryTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /all-address returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /village} — village codes with their parent commune.
	 *
	 * <p>Needed separately because {@code /all-address} carries province,
	 * district and commune ids but NO village id (village is a bare string
	 * there), while our pickers and LOS's {@code CustP_*CBVillage} both work in
	 * village codes.
	 */
	public JsonNode villages() throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(urlApi + "/village"))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(dictionaryTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /village returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /employer} — Sambat's APPROVED employer list. {@code comId} is
	 * the employer entity code the loan application carries as
	 * {@code CustP_EntityFactoryId} ("G30020" in their reference); the LPO
	 * assigns it during account approval.
	 */
	public JsonNode employers() throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(urlApi + "/employer"))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(dictionaryTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /employer returned " + resp.statusCode());
		return mapper.readTree(resp.body());
	}

	/**
	 * {@code GET /customer-information/by-idno}: Sambat's customer record for
	 * an ID number, whose {@code custKeyNum} is the CIF. One ID number has one
	 * CIF (Sambat, 2026-11-05), so the first record is the only one.
	 *
	 * <p>Null only when Sambat answered and hold no customer with that number.
	 * Anything else throws: a wrongly "new" customer is filed with custId 0,
	 * which Sambat answer with a second CIF or an automatic reject.
	 */
	public JsonNode customerByIdNo(String idNo) throws Exception {
		String url = urlApi + "/customer-information/by-idno?idNo="
				+ URLEncoder.encode(idNo, StandardCharsets.UTF_8) + "&page=0&size=1";
		HttpRequest req = HttpRequest.newBuilder().uri(new URI(url))
				.header("Authorization", "Bearer " + token())
				.timeout(Duration.ofSeconds(lookupTimeoutSeconds)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200)
			throw new IllegalStateException("SBF /customer-information/by-idno returned " + resp.statusCode());
		JsonNode content = resp.body() == null || resp.body().isBlank() ? null
				: mapper.readTree(resp.body()).get("content");
		if (content == null || !content.isArray())
			throw new IllegalStateException("SBF /customer-information/by-idno returned no page of customers");
		return content.isEmpty() ? null : content.get(0);
	}
}
