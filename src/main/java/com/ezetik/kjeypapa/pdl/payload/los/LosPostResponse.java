package com.ezetik.kjeypapa.pdl.payload.los;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Response of {@code POST /new-loan-application}.
 *
 * <p>Two traps, both confirmed against the vendor's own sample bodies in
 * {@code docs/api spec/New Loan API Request Json.json}:
 *
 * <ul>
 * <li><b>{@code IsSuccess} is a STRING</b>, {@code "Success"} or {@code "False"} —
 * not a boolean. Any truthiness read of it treats a rejection as an
 * acceptance.
 * <li><b>A failure still returns HTTP 200</b> with {@code "Result": []}, so
 * success must be judged from the body, never the status code, and the result
 * list must be checked before it is indexed.
 * </ul>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LosPostResponse {

	/** Typed loosely: swagger says object, observed values are string or null. */
	@JsonProperty("ErrorMessage")
	private JsonNode errorMessage;

	@JsonProperty("IsSuccess")
	private String isSuccess;

	@JsonProperty("MissingData")
	private String missingData;

	@JsonProperty("Result")
	private List<LosPostResult> result = new ArrayList<>();

	/** True only for the literal success marker. Anything else is a failure. */
	public boolean succeeded() {
		return isSuccess != null && "success".equalsIgnoreCase(isSuccess.trim());
	}

	/** Human-readable error text, or null. */
	public String errorText() {
		if (errorMessage == null || errorMessage.isNull())
			return null;
		return errorMessage.isTextual() ? errorMessage.asText() : errorMessage.toString();
	}

	/**
	 * {@code MissingData} is pipe-delimited and carries a trailing separator
	 * ({@code "CustP_CAddCBCommune | "}), so a naive split yields a phantom
	 * empty field.
	 */
	public List<String> missingFields() {
		if (missingData == null || missingData.isBlank())
			return List.of();
		return Arrays.stream(missingData.split("\\|"))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}
}
