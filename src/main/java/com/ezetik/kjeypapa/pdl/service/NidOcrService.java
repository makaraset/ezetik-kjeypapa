package com.ezetik.kjeypapa.pdl.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.payload.NidOcrResponse;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Signup screen-5 NID extraction (2026-08-26). SBF exposes CamDigi OCR at
 * {@code POST /ocr-id-card}, but the UAT endpoint currently 500s on every
 * payload (raised with Sambat) — so, same pattern as the LOS: a mock flag
 * serves the sample-card data until SBF fixes their side, while the CIF
 * lookup ({@code /customer-information/by-idno}) is LIVE either way.
 */
@Service
public class NidOcrService {

	@Value("${ocr.mock.enabled:true}")
	private boolean mockEnabled;

	@Autowired
	private SbfGatewayClient sbf;

	private static final DateTimeFormatter APP_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public ResponseEntity<Message<NidOcrResponse>> extract(String imageBase64) {
		try {
			if (imageBase64 == null || imageBase64.isBlank())
				return resp("INVALID", "Missing idImage", null, HttpStatus.EXPECTATION_FAILED);

			NidOcrResponse out;
			if (mockEnabled) {
				// Sample card (Makara's NID) so the app flow is fully drivable
				// before SBF's OCR endpoint works.
				out = new NidOcrResponse("សែត", "មករា", "SET", "MAKARA", "M",
						"03/01/1990", "110553867", "13/03/2025", "16/03/2035",
						"ផ្ទះ៩៤០ ផ្លូវ៨ ភូមិព្រៃខ្លា សង្កាត់ក្រាំងធ្នង់ ខណ្ឌសែនសុខ ភ្នំពេញ",
						null, true);
			} else {
				JsonNode r = sbf.ocrIdCard(imageBase64);
				if (r.path("error").asInt(0) != 0)
					return resp("OCR_FAILED", r.path("message").asText("OCR failed"), null,
							HttpStatus.EXPECTATION_FAILED);
				JsonNode d = r.path("data");
				out = new NidOcrResponse(
						d.path("lastNameKh").asText(""), d.path("firstNameKh").asText(""),
						d.path("lastNameEn").asText(""), d.path("firstNameEn").asText(""),
						normGender(d.path("gender").asText("")),
						normDate(d.path("dob").asText("")),
						d.path("idNumber").asText(""),
						normDate(d.path("issuedDate").asText("")),
						normDate(d.path("expiredDate").asText("")),
						d.path("address").asText(""), null, false);
			}

			// CIF resolution is LIVE regardless of the OCR mock (verified
			// working against UAT): null = new-to-SBF -> custId 0 at submit.
			if (out.getIdNumber() != null && !out.getIdNumber().isBlank()) {
				try {
					out.setCif(sbf.findCifByIdNo(out.getIdNumber()));
				} catch (Exception e) {
					// best-effort — signup must not fail on a CIF hiccup
				}
			}
			return resp("SUCCESS", "NID extracted", out, HttpStatus.OK);
		} catch (Exception e) {
			return resp("OCR_FAILED", "Could not read the ID card", null, HttpStatus.EXPECTATION_FAILED);
		}
	}

	/** Best-effort date normalisation to the app's dd/MM/yyyy. */
	static String normDate(String raw) {
		if (raw == null || raw.isBlank())
			return "";
		String s = raw.trim().replace('.', '/').replace('-', '/');
		for (String p : new String[] { "dd/MM/yyyy", "yyyy/MM/dd", "d/M/yyyy", "MM/dd/yyyy" }) {
			try {
				return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)).format(APP_FMT);
			} catch (Exception ignore) {
			}
		}
		return raw.trim();
	}

	static String normGender(String raw) {
		if (raw == null)
			return "";
		String s = raw.trim().toUpperCase();
		if (s.startsWith("M") || s.contains("ប្រុស"))
			return "M";
		if (s.startsWith("F") || s.contains("ស្រី"))
			return "F";
		return "";
	}

	private static <T> ResponseEntity<Message<T>> resp(String type, String msg, T data, HttpStatus st) {
		Message<T> m = new Message<>();
		m.setType(type);
		m.setMessage(msg);
		m.setData(data);
		return new ResponseEntity<>(m, st);
	}
}
