package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * What {@code POST /customer-accepted} carries, read back from a real local
 * HTTP server.
 *
 * <p>Sambat are replacing this body (their file of 2026-11-16) and have not
 * said when. Until they do, one request has to be right for the server they
 * run today and the one they will run, so both versions' fields are pinned
 * here together.
 */
class SbfGatewayClientAcceptTest {

	private final ObjectMapper om = new ObjectMapper();
	private final AtomicReference<String> received = new AtomicReference<>();

	private HttpServer server;
	private SbfGatewayClient client;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/oauth/token",
				ex -> respond(ex, 200, "{\"access_token\":\"t\",\"expires_in\":300}"));
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();

		client = new SbfGatewayClient();
		ReflectionTestUtils.setField(client, "tokenEndpoint", base + "/oauth/token");
		ReflectionTestUtils.setField(client, "urlencodedToken", "grant_type=client_credentials");
		ReflectionTestUtils.setField(client, "basicAuth", "Basic eA==");
		ReflectionTestUtils.setField(client, "losUrlApi", base + "/los");
		ReflectionTestUtils.setField(client, "mapper", om);
		ReflectionTestUtils.setField(client, "submitTimeoutSeconds", 5);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void sambatAnswer(int status, String body) {
		server.createContext("/los/customer-accepted", ex -> {
			received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(ex, status, body);
		});
	}

	private JsonNode sent() throws Exception {
		return om.readTree(received.get());
	}

	@Test
	@DisplayName("an acceptance carries today's fields and Sambat's new ones together")
	void carriesBothVersions() throws Exception {
		sambatAnswer(200, "{\"statusCode\":\"200\"}");

		String reply = client.customerAccepted(900001L, 8558L, "KjeyPapa", "Y", "N", "", "", "");

		assertThat(reply).isEqualTo("{\"statusCode\":\"200\"}");
		JsonNode body = sent();
		// Both keys: ours, and their LOS id for the server they run today.
		assertThat(body.get("mobileAppId").asLong()).isEqualTo(900001L);
		assertThat(body.get("appId").asLong()).isEqualTo(8558L);
		assertThat(body.get("doneBy").asText()).isEqualTo("KjeyPapa");
		// Their new body: both are required there.
		assertThat(body.get("customerDecision").asText()).isEqualTo("Y");
		assertThat(body.get("termChange").asText()).isEqualTo("N");
		assertThat(body.get("customerComment").asText()).isEmpty();
		// Today's optional fields, empty as before.
		assertThat(body.get("smsText").asText()).isEmpty();
		assertThat(body.get("trnCode").asText()).isEmpty();
	}

	@Test
	@DisplayName("no signed document is sent while we have none")
	void sendsNoSignedDocuments() throws Exception {
		sambatAnswer(200, "{}");

		client.customerAccepted(900001L, 8558L, "KjeyPapa", "Y", "N", "", "", "");

		// A blank or made-up document under these names would claim a signature.
		sent().fieldNames().forEachRemaining(name -> assertThat(name).doesNotStartWithIgnoringCase("doc_"));
	}

	@Test
	@DisplayName("without their LOS id the key is left out, not sent as 0")
	void leavesOutAnUnknownLosId() throws Exception {
		sambatAnswer(200, "{}");

		client.customerAccepted(900001L, null, "KjeyPapa", "Y", "N", "", "", "");

		// 0 is what a new application carries; it must never name an existing one.
		assertThat(sent().has("appId")).isFalse();
		assertThat(sent().get("mobileAppId").asLong()).isEqualTo(900001L);
	}

	@Test
	@DisplayName("an HTTP error is a failure, with the start of what Sambat said")
	void httpErrorFails() {
		sambatAnswer(400, "{\"message\":\"customerDecision is required\"}");

		assertThatThrownBy(() -> client.customerAccepted(900001L, 8558L, "KjeyPapa", "Y", "N", "", "", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("400")
				.hasMessageContaining("customerDecision is required");
	}

	private static void respond(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(bytes);
		}
	}
}
