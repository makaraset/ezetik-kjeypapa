package com.ezetik.kjeypapa.pdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ezetik.kjeypapa.pdl.payload.los.LosPostResponse;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins our side of SBF's {@code /new-loan-application} contract against the
 * vendor's own swagger and their two sample response bodies, both copied into
 * test resources verbatim.
 */
class LosContractTest {

	private final ObjectMapper om = new ObjectMapper();

	private JsonNode resource(String name) throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/los/" + name)) {
			assertNotNull(in, "missing test resource " + name);
			return om.readTree(in);
		}
	}

	@Test
	@DisplayName("the request DTO serialises exactly SBF's 102 keys, no more, no fewer")
	void keySetMatchesSwagger() throws Exception {
		JsonNode props = resource("swagger.json").path("definitions").path("NewApplicationRequest")
				.path("properties");
		Set<String> expected = new TreeSet<>();
		props.fieldNames().forEachRemaining(expected::add);

		JsonNode serialised = om.valueToTree(new NewApplicationRequest());
		Set<String> actual = new TreeSet<>();
		serialised.fieldNames().forEachRemaining(actual::add);

		assertEquals(102, expected.size(), "swagger itself should define 102 fields");
		// Names are the wire contract — a rename or a "corrected" misspelling
		// silently drops the field, and SBF would only report it as MissingData.
		assertEquals(expected, actual);
	}

	@Test
	@DisplayName("every key is present even when unset, matching the vendor's sample")
	void allKeysAlwaysSerialised() throws Exception {
		JsonNode serialised = om.valueToTree(new NewApplicationRequest());
		int count = 0;
		for (var it = serialised.fieldNames(); it.hasNext(); it.next())
			count++;
		assertEquals(102, count);
		assertFalse(serialised.toString().contains(":null"), "no key should serialise as null");
	}

	@Test
	@DisplayName("IsSuccess is a STRING — a truthiness read would accept a rejection")
	void successIsDecidedByTheStringNotABoolean() throws Exception {
		LosPostResponse ok = om.treeToValue(resource("response_success.json"), LosPostResponse.class);
		LosPostResponse bad = om.treeToValue(resource("response_missing.json"), LosPostResponse.class);

		assertEquals("Success", ok.getIsSuccess());
		assertEquals("False", bad.getIsSuccess());
		assertTrue(ok.succeeded());
		assertFalse(bad.succeeded());

		// The trap, stated explicitly: both values are non-empty strings, so
		// anything resembling `if (isSuccess)` treats "False" as a success.
		assertFalse(bad.getIsSuccess().isEmpty());
	}

	@Test
	@DisplayName("a rejection arrives as HTTP 200 with an EMPTY Result — never index it blind")
	void failureCarriesNoResult() throws Exception {
		LosPostResponse bad = om.treeToValue(resource("response_missing.json"), LosPostResponse.class);
		assertTrue(bad.getResult().isEmpty());
	}

	@Test
	@DisplayName("MissingData's trailing pipe does not become a phantom empty field")
	void missingDataParsing() throws Exception {
		LosPostResponse bad = om.treeToValue(resource("response_missing.json"), LosPostResponse.class);
		assertEquals(List.of("CustP_CAddCBCommune"), bad.missingFields());

		LosPostResponse multi = new LosPostResponse();
		multi.setMissingData("A | B | C | ");
		assertEquals(List.of("A", "B", "C"), multi.missingFields());

		LosPostResponse none = new LosPostResponse();
		assertTrue(none.missingFields().isEmpty());
		none.setMissingData("");
		assertTrue(none.missingFields().isEmpty());
	}

	@Test
	@DisplayName("the success body yields the reference we persist")
	void successCarriesIds() throws Exception {
		LosPostResponse ok = om.treeToValue(resource("response_success.json"), LosPostResponse.class);
		assertEquals(1, ok.getResult().size());
		assertEquals(8032L, ok.getResult().get(0).getAppId());
		assertEquals(254906, ok.getResult().get(0).getAppRefId());
		assertEquals(null, ok.errorText());
	}
}
