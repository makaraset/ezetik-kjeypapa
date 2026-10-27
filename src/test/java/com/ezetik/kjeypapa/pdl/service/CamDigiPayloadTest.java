package com.ezetik.kjeypapa.pdl.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.camdigi.VerifiedInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What we send CamDigi, and what we keep of its reply. */
class CamDigiPayloadTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private PdlPersonalInfo makara() {
		PdlPersonalInfo p = new PdlPersonalInfo();
		p.setIdNo(" 110553867 ");
		p.setDateOfBirth("1990-01-03"); // a legacy ISO value: still sent in the one format
		p.setGender("M");
		p.setLatinFamilyName("SET");
		p.setLatinFirstName("MAKARA");
		p.setKhmerFamilyName("សិត ");
		p.setKhmerFirstName("មករា");
		p.setIdIssuedDate("17/03/2025");
		return p;
	}

	@Test
	@DisplayName("family names are lastName, given names firstName, and every date leaves in one format")
	void mapsTheIdentity() {
		VerifiedInfo v = CamDigiUserInfoMapper.from(makara());
		assertThat(v.lastNameEn()).isEqualTo("SET");
		assertThat(v.firstNameEn()).isEqualTo("MAKARA");
		assertThat(v.lastNameKh()).isEqualTo("សិត"); // trimmed: Khmer names were not, on the way in
		assertThat(v.firstNameKh()).isEqualTo("មករា");
		assertThat(v.idNumber()).isEqualTo("110553867");
		assertThat(v.dob()).isEqualTo("03/01/1990");
		assertThat(v.issuedDate()).isEqualTo("17/03/2025");
		assertThat(v.expiredDate()).isEmpty(); // optional on our side: sent blank, not refused
		assertThat(v.gender()).isEqualTo("M");
	}

	@Test
	@DisplayName("the serialised field names are exactly SBF's VerifiedInfo")
	void fieldNamesMatchTheSwagger() throws Exception {
		JsonNode n = JSON.valueToTree(CamDigiUserInfoMapper.from(makara()));
		assertThat(n.properties()).extracting(e -> e.getKey()).containsExactlyInAnyOrder("dob", "expiredDate",
				"firstNameEn", "firstNameKh", "gender", "idNumber", "issuedDate", "lastNameEn", "lastNameKh");
	}

	@Test
	@DisplayName("missing identity fields are named; issue and expiry dates are not among them")
	void missing() {
		assertThat(CamDigiUserInfoMapper.missing(makara())).isEmpty();
		PdlPersonalInfo p = makara();
		p.setIdNo(null);
		p.setKhmerFirstName("");
		assertThat(CamDigiUserInfoMapper.missing(p)).containsExactly("ID number", "Khmer name");
		assertThat(CamDigiUserInfoMapper.missing(null)).containsExactly("personal information");
	}

	@Test
	@DisplayName("long strings are elided at any depth, short ones kept, the input untouched")
	void excerpt() throws Exception {
		String image = "B".repeat(300);
		JsonNode reply = JSON.readTree("{\"error\":0,\"data\":{\"faces\":[\"" + image + "\",\"ok\"],"
				+ "\"nested\":{\"photo\":\"" + image + "\"},\"name\":\"SET MAKARA\"}}");

		String kept = ResponseExcerpt.of(reply, 4096);

		assertThat(kept).doesNotContain(image).contains("<omitted 300 chars>").contains("SET MAKARA")
				.contains("\"ok\"");
		assertThat(reply.at("/data/nested/photo").asText()).isEqualTo(image);
	}

	@Test
	@DisplayName("the excerpt is capped")
	void excerptCap() throws Exception {
		StringBuilder many = new StringBuilder("{\"error\":0,\"data\":[");
		for (int i = 0; i < 500; i++)
			many.append(i == 0 ? "" : ",").append("\"short value ").append(i).append('"');
		String kept = ResponseExcerpt.of(JSON.readTree(many.append("]}").toString()), 200);
		assertThat(kept).hasSize(203).endsWith("...");
	}
}
