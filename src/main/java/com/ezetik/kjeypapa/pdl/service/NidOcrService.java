package com.ezetik.kjeypapa.pdl.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.ezetik.kjeypapa.pdl.payload.NidOcrResponse;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Signup screen-5 NID extraction. SBF's CamDigi OCR at
 * {@code POST /ocr-id-card} is LIVE as of 2026-08-27 — it 500'd on every
 * payload until Sambat fixed a cert on their side — and is now the default
 * ({@code ocr.mock.enabled=false}). The mock still serves sample-card data
 * for offline/demo runs. The CIF lookup
 * ({@code /customer-information/by-idno}) is LIVE either way.
 */
@Service
@Slf4j
public class NidOcrService {

	@Value("${ocr.mock.enabled:true}")
	private boolean mockEnabled;

	@Autowired
	private SbfGatewayClient sbf;

	@Autowired
	private KhAddressResolver addressResolver;

	private static final DateTimeFormatter APP_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public ResponseEntity<Message<NidOcrResponse>> extract(String imageBase64) {
		try {
			if (imageBase64 == null || imageBase64.isBlank())
				return resp("INVALID", "Missing idImage", null, HttpStatus.EXPECTATION_FAILED);

			NidOcrResponse out;
			if (mockEnabled) {
				// Sample card (Makara's NID) so the app flow stays drivable
				// with no network / when SBF's UAT is down.
				out = new NidOcrResponse();
				out.setKhmerFamilyName("សែត");
				out.setKhmerFirstName("មករា");
				out.setLatinFamilyName("SET");
				out.setLatinFirstName("MAKARA");
				out.setGender("M");
				out.setDateOfBirth("03/01/1990");
				out.setIdNumber("110553867");
				out.setIdIssuedDate("13/03/2025");
				out.setIdExpiryDate("16/03/2035");
				out.setAddress("ផ្ទះ៩៤០ ផ្លូវ៨ ភូមិព្រៃខ្លា សង្កាត់ក្រាំងធ្នង់ ខណ្ឌសែនសុខ ភ្នំពេញ");
				out.setMock(true);
			} else {
				JsonNode r = sbf.ocrIdCard(imageBase64);
				if (r.path("error").asInt(0) != 0)
					return resp("OCR_FAILED", r.path("message").asText("OCR failed"), null,
							HttpStatus.EXPECTATION_FAILED);
				JsonNode d = r.path("data");
				out = new NidOcrResponse();
				out.setKhmerFamilyName(d.path("lastNameKh").asText(""));
				out.setKhmerFirstName(d.path("firstNameKh").asText(""));
				out.setLatinFamilyName(d.path("lastNameEn").asText(""));
				out.setLatinFirstName(d.path("firstNameEn").asText(""));
				out.setGender(normGender(d.path("gender").asText("")));
				out.setDateOfBirth(normDate(d.path("dob").asText("")));
				out.setIdNumber(d.path("idNumber").asText(""));
				out.setIdIssuedDate(normDate(d.path("issuedDate").asText("")));
				out.setIdExpiryDate(normDate(d.path("expiredDate").asText("")));
				out.setAddress(d.path("address").asText(""));
				out.setMock(false);
			}

			// Resolve the card's address line into Sambat's own geo codes. Done
			// here, on the server, because matching has to happen against THEIR
			// gazetteer — the app ships a different vintage whose romanisation
			// disagrees on 14 districts, so an app-side match yields names
			// Sambat cannot code.
			KhAddressResolver.Resolved geo = addressResolver.resolve(out.getAddress());
			out.setProvinceCode(geo.getProvinceCode());
			out.setProvinceName(geo.getProvinceName());
			out.setDistrictCode(geo.getDistrictCode());
			out.setDistrictName(geo.getDistrictName());
			out.setCommuneCode(geo.getCommuneCode());
			out.setCommuneName(geo.getCommuneName());
			out.setVillageCode(geo.getVillageCode());
			out.setVillageName(geo.getVillageName());
			out.setHouseStreetNo(geo.getHouseStreetNo());

			// No CIF lookup here any more (2026-08-29). This endpoint is
			// anonymous, and returning a live Sambat CIF for any image anyone
			// posts turned it into an oracle. The backend resolves the CIF itself
			// at account-request time (PdlAccountRequestService.create), and the
			// app never used the value.
			return resp("SUCCESS", "NID extracted", out, HttpStatus.OK);
		} catch (Exception e) {
			// The customer-facing message stays generic, but the cause must not
			// vanish: when a capture failed on-device the only clue we had was
			// "Could not read the ID card", which says nothing about whether SBF
			// returned 500, timed out, or rejected the payload size.
			log.warn("NID OCR failed (image base64 chars={}): {}",
					imageBase64 == null ? 0 : imageBase64.length(), e.toString());
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
