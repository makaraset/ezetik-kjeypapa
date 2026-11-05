package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.pdl.payload.camdigi.CamDigiLivenessRequest;
import com.ezetik.kjeypapa.pdl.payload.camdigi.VerifiedInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The gateway against a real local HTTP server: the CamDigi call must say how
 * far it got, and no SBF call may wait forever.
 */
class SbfGatewayClientTimeoutTest {

	private HttpServer server;
	private SbfGatewayClient client;
	private String base;

	private static final CamDigiLivenessRequest BODY = new CamDigiLivenessRequest(List.of("BLINK"), "KjeyPapa",
			"aWQ=", "bGl2ZQ==", new VerifiedInfo("03/01/1990", "", "MAKARA", "មករា", "M", "110553867", "", "SET", "សិត"));

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/oauth/token",
				ex -> respond(ex, 200, "{\"access_token\":\"t\",\"expires_in\":300}"));
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();

		client = new SbfGatewayClient();
		ReflectionTestUtils.setField(client, "tokenEndpoint", base + "/oauth/token");
		ReflectionTestUtils.setField(client, "urlencodedToken", "grant_type=client_credentials");
		ReflectionTestUtils.setField(client, "basicAuth", "Basic eA==");
		ReflectionTestUtils.setField(client, "urlApi", base + "/api");
		ReflectionTestUtils.setField(client, "mapper", new ObjectMapper());
		ReflectionTestUtils.setField(client, "livenessTimeoutSeconds", 1);
		ReflectionTestUtils.setField(client, "ocrTimeoutSeconds", 1);
		ReflectionTestUtils.setField(client, "lookupTimeoutSeconds", 1);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void liveness(int status, String body, long delayMs) {
		server.createContext("/api/verified-info-idimage-liveness", ex -> {
			sleep(delayMs);
			respond(ex, status, body);
		});
	}

	private SbfCallException failure() {
		try {
			client.verifiedInfoIdImageLiveness(BODY);
		} catch (SbfCallException e) {
			return e;
		}
		throw new AssertionError("expected an SbfCallException");
	}

	@Test
	@DisplayName("a 200 JSON reply comes back as the node")
	void ok() throws Exception {
		liveness(200, "{\"error\":0,\"message\":\"verified\"}", 0);
		assertThat(client.verifiedInfoIdImageLiveness(BODY).path("message").asText()).isEqualTo("verified");
	}

	@Test
	@DisplayName("no reply within the timeout is NO_REPLY, raised at the timeout, not later")
	void noReply() {
		liveness(200, "{}", 4_000);
		long t0 = System.nanoTime();
		SbfCallException e = failure();
		long ms = (System.nanoTime() - t0) / 1_000_000L;

		assertThat(e.getKind()).isEqualTo(SbfCallException.Kind.NO_REPLY);
		assertThat(ms).isLessThan(3_000);
	}

	@Test
	@DisplayName("5xx is SERVER_ERROR, another non-200 is REJECTED, and both keep the start of the body")
	void statuses() {
		liveness(503, "maintenance", 0);
		SbfCallException e = failure();
		assertThat(e.getKind()).isEqualTo(SbfCallException.Kind.SERVER_ERROR);
		assertThat(e.getHttpStatus()).isEqualTo(503);
		server.removeContext("/api/verified-info-idimage-liveness");

		liveness(400, "doneBy is not valid " + "x".repeat(2_000), 0);
		e = failure();
		assertThat(e.getKind()).isEqualTo(SbfCallException.Kind.REJECTED);
		assertThat(e.getDetail()).startsWith("doneBy is not valid").hasSize(512);
	}

	@Test
	@DisplayName("a 200 that is not a JSON object is BAD_REPLY")
	void badReply() {
		liveness(200, "<html>gateway</html>", 0);
		assertThat(failure().getKind()).isEqualTo(SbfCallException.Kind.BAD_REPLY);
	}

	@Test
	@DisplayName("nothing listening is UNREACHABLE, and so is a token endpoint that is down")
	void unreachable() throws Exception {
		int closed;
		try (ServerSocket s = new ServerSocket(0)) {
			closed = s.getLocalPort();
		}
		ReflectionTestUtils.setField(client, "urlApi", "http://127.0.0.1:" + closed + "/api");
		assertThat(failure().getKind()).isEqualTo(SbfCallException.Kind.UNREACHABLE);

		SbfGatewayClient noToken = new SbfGatewayClient();
		ReflectionTestUtils.setField(noToken, "tokenEndpoint", "http://127.0.0.1:" + closed + "/oauth/token");
		ReflectionTestUtils.setField(noToken, "urlencodedToken", "x");
		ReflectionTestUtils.setField(noToken, "basicAuth", "x");
		ReflectionTestUtils.setField(noToken, "urlApi", base + "/api");
		ReflectionTestUtils.setField(noToken, "mapper", new ObjectMapper());
		assertThatThrownBy(() -> noToken.verifiedInfoIdImageLiveness(BODY)).isInstanceOf(SbfCallException.class)
				.extracting(t -> ((SbfCallException) t).getKind()).isEqualTo(SbfCallException.Kind.UNREACHABLE);
	}

	@Test
	@DisplayName("NID OCR now times out too — it used to wait as long as SBF did")
	void ocrTimesOut() {
		server.createContext("/api/ocr-id-card", ex -> {
			sleep(4_000);
			respond(ex, 200, "{}");
		});
		long t0 = System.nanoTime();
		assertThatThrownBy(() -> client.ocrIdCard("aWQ=")).isInstanceOf(IOException.class);
		assertThat((System.nanoTime() - t0) / 1_000_000L).isLessThan(3_000);
	}

	private void customers(String body, long delayMs) {
		server.createContext("/api/customer-information/by-idno", ex -> {
			sleep(delayMs);
			respond(ex, 200, body);
		});
	}

	@Test
	@DisplayName("the customer lookup times out too — it waited as long as SBF did, inside the signup's transaction")
	void customerLookupTimesOut() {
		customers("{\"content\":[]}", 4_000);
		long t0 = System.nanoTime();
		assertThatThrownBy(() -> client.customerByIdNo("012345678")).isInstanceOf(IOException.class);
		assertThat((System.nanoTime() - t0) / 1_000_000L).isLessThan(3_000);
	}

	@Test
	@DisplayName("only an empty page of customers means none: a blank or odd reply throws, never reads as new")
	void onlyAnEmptyPageIsNoCustomer() throws Exception {
		customers("{\"content\":[],\"totalElements\":0}", 0);
		assertThat(client.customerByIdNo("012345678")).isNull();
		server.removeContext("/api/customer-information/by-idno");

		customers("{\"content\":[{\"custKeyNum\":70,\"idNo\":\"012345678\"}]}", 0);
		assertThat(client.customerByIdNo("012345678").path("custKeyNum").asLong()).isEqualTo(70L);
		server.removeContext("/api/customer-information/by-idno");

		customers("", 0);
		assertThatThrownBy(() -> client.customerByIdNo("012345678")).isInstanceOf(IllegalStateException.class);
		server.removeContext("/api/customer-information/by-idno");

		customers("{\"error\":\"maintenance\"}", 0);
		assertThatThrownBy(() -> client.customerByIdNo("012345678")).isInstanceOf(IllegalStateException.class);
	}

	private static void respond(HttpExchange ex, int status, String body) throws IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
		if (b.length > 0)
			try (OutputStream o = ex.getResponseBody()) {
				o.write(b);
			}
		ex.close();
	}

	private static void sleep(long ms) {
		if (ms <= 0)
			return;
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
