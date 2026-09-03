package com.ezetik.kjeypapa.pdl.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * The values in SBF's loan application that we cannot derive from our own data
 * — their CBC master-list codes and their integration identifiers.
 *
 * <p>Every one of these defaults to EMPTY on purpose. A real submit calls
 * {@link #assertConfigured()} first and refuses when any is unset, because the
 * alternative is worse than a failed call: SBF's UAT accepts unknown codes
 * silently, so a guess does not bounce — it files a real credit application at
 * Sambat carrying a wrong occupation, a wrong employer, or an address in the
 * wrong commune, under a real customer's name. Fail loudly, fix the config.
 *
 * <p>Fill these in from Sambat's master lists, then flip
 * {@code los.mock.enabled=false}. Everything listed by
 * {@link #missingSettings()} is exactly what still has to be asked for.
 */
@Component
@Getter
public class LosSubmitConfig {

	/** LOS-side user id for our integration account. Vendor sample: 541. */
	@Value("${los.hid-current-user-id:}")
	private String hidCurrentUserId;

	/** "Mobile App ID" per Appendix 2. Vendor sample sends 0. */
	@Value("${los.app-id:0}")
	private long appId;

	/** CBC product type code. Vendor sample: PDL. */
	@Value("${los.const.product-type:}")
	private String productType;

	/** CBC loan category code. Vendor sample: SIL. */
	@Value("${los.const.loan-category:}")
	private String loanCategory;

	/** CBC repayment method code. Vendor sample says EMI, but PDL is a single bullet repayment. */
	@Value("${los.const.repayment-method:}")
	private String repaymentMethod;

	/** CBC disbursement scheme code. Vendor sample: 1. */
	@Value("${los.const.disbursement-scheme:}")
	private String disbursementScheme;

	/** Loan term in whatever unit LOS counts. UNCONFIRMED — see the class note. */
	@Value("${los.const.loan-term:}")
	private String loanTerm;

	/** CBC employment type code. Vendor sample: E. */
	@Value("${los.const.employment-type:}")
	private String employmentType;

	/** CBC employment contract type code. Vendor sample: UDC. */
	@Value("${los.const.employment-contract-type:}")
	private String employmentContractType;

	/** CBC "id issued by" code. Vendor sample: 1. */
	@Value("${los.const.id-issued-by:}")
	private String idIssuedBy;

	/** CBC payment channel code — PDL disburses to the customer's own account. */
	@Value("${los.const.payment-channel:}")
	private String paymentChannel;

	/** Payment-channel sheet row id (bank). "Use only #12" for now — Sambat, 2026-08-31. */
	@Value("${los.const.payment-channel-name:}")
	private String paymentChannelName;

	/**
	 * {@code UltilizationCategory} for the single payday utilization row.
	 * CONFIRMED by Sambat (2026-09-03): 23 = "General consumption purposes",
	 * fixed for payday. Mandatory: their MissingData insists on
	 * LoanUtilizationProject, overriding the earlier "keep optional" answer.
	 */
	@Value("${los.const.utilization-category:}")
	private String utilizationCategory;

	/**
	 * The envelope {@code doneBy}. Sambat (2026-09-03): any string passes
	 * validation — but empirically their LOS dies (slow 500/timeout) on our
	 * numeric app usernames, so this is a CONSTANT, never the customer's
	 * username. "KjeyPapa" per their instruction; verified live same day.
	 */
	@Value("${los.done-by:}")
	private String doneBy;

	/** Ordered so the operator sees them in the shape of the questions to ask. */
	private Map<String, String> settings() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("los.hid-current-user-id", hidCurrentUserId);
		m.put("los.done-by", doneBy);
		m.put("los.const.product-type", productType);
		m.put("los.const.loan-category", loanCategory);
		m.put("los.const.repayment-method", repaymentMethod);
		m.put("los.const.disbursement-scheme", disbursementScheme);
		m.put("los.const.loan-term", loanTerm);
		m.put("los.const.employment-type", employmentType);
		m.put("los.const.employment-contract-type", employmentContractType);
		m.put("los.const.id-issued-by", idIssuedBy);
		m.put("los.const.payment-channel", paymentChannel);
		m.put("los.const.payment-channel-name", paymentChannelName);
		m.put("los.const.utilization-category", utilizationCategory);
		return m;
	}

	/** Property names still unset — i.e. what Sambat has not answered yet. */
	public List<String> missingSettings() {
		List<String> missing = new ArrayList<>();
		settings().forEach((k, v) -> {
			if (v == null || v.isBlank())
				missing.add(k);
		});
		return missing;
	}

	/**
	 * @throws LosSubmitException when any required code is still unset, naming
	 *         every one of them so the gap is fixed in a single round trip.
	 */
	public void assertConfigured() {
		List<String> missing = missingSettings();
		if (!missing.isEmpty())
			throw new LosSubmitException("LOS_NOT_CONFIGURED",
					"Real LOS submit is not configured yet — unset: " + String.join(", ", missing));
	}
}
