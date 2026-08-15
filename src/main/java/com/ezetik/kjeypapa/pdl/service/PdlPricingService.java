package com.ezetik.kjeypapa.pdl.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PdlLoanTypeEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlQuoteResponse;

/**
 * Server-side quote engine (we own the computation — Sambat 2026-08-13 QC1.6).
 *
 * Pricing per QC1.3: monthly interest 1.5% pro-rated by loan period
 * (the only reading that reproduces the confirmed mockup figures:
 * repayment $50 / 15 days → principal $49.63, interest $0.37, net $45.63),
 * processing fee $3, CBC enquiry fee $1, net = principal − fees.
 * Dates are fixed offsets from the application date (QC1.4).
 * Tier lists are our Phase-6 proposal (QC1.2) pending Sambat confirmation.
 * Every value is configuration — a Sambat correction is a properties edit.
 */
@Service
public class PdlPricingService {

	@Value("${pdl.pricing.monthly-interest-percent:1.5}")
	private double monthlyInterestPercent;

	@Value("${pdl.pricing.processing-fee:3.0}")
	private double processingFee;

	@Value("${pdl.pricing.cbc-fee:1.0}")
	private double cbcFee;

	@Value("${pdl.pricing.period-days:15}")
	private int periodDays;

	@Value("${pdl.pricing.disbursement-offset-days:1}")
	private int disbursementOffsetDays;

	@Value("${pdl.pricing.max-amount.payday:50.0}")
	private double paydayMaxAmount;

	/** Repayment-amount tiers (USD). Proposal pending Sambat (QC1.2). */
	@Value("${pdl.pricing.tiers.usd:10,20,30,40,50}")
	private String usdTiers;

	/** Repayment-amount tiers (KHR) — indicative ~4,100 KHR/USD, Sambat to set. */
	@Value("${pdl.pricing.tiers.khr:40000,80000,120000,160000,200000}")
	private String khrTiers;

	/** Quote for a selected repayment-amount tier. Throws on an invalid request. */
	public PdlQuoteResponse quote(PdlLoanTypeEnum loanType, String currency, double repaymentAmount) {
		if (loanType == null)
			loanType = PdlLoanTypeEnum.PAYDAY;
		if (loanType != PdlLoanTypeEnum.PAYDAY)
			throw new IllegalArgumentException("Loan product not yet available: " + loanType);

		String cur = normalizeCurrency(currency);
		List<Double> tiers = tiers(cur);
		if (!tiers.contains(repaymentAmount))
			throw new IllegalArgumentException("Repayment amount " + repaymentAmount
					+ " is not an offered tier for " + cur + " (" + tiers + ")");

		// interest factor for the period: monthlyRate × days/30 (pro-rated, QC1.3)
		double factor = (monthlyInterestPercent / 100.0) * periodDays / 30.0;
		double principal = round2(repaymentAmount / (1 + factor));
		double interest = round2(repaymentAmount - principal);
		double fees = feesFor(cur);
		double net = round2(principal - fees);

		Instant now = Instant.now();
		Instant disburse = now.plus(disbursementOffsetDays, ChronoUnit.DAYS);
		Instant repay = disburse.plus(periodDays, ChronoUnit.DAYS);

		return new PdlQuoteResponse(loanType.name(), cur, repaymentAmount, principal, interest,
				monthlyInterestPercent, feeComponent(cur, processingFee), feeComponent(cur, cbcFee),
				net, periodDays, disburse, repay, tiers);
	}

	/** The selectable tiers for a currency (USD default). */
	public List<Double> tiers(String currency) {
		String csv = "KHR".equalsIgnoreCase(normalizeCurrency(currency)) ? khrTiers : usdTiers;
		return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.map(Double::parseDouble).collect(Collectors.toList());
	}

	/** Product cap check for legacy free-amount requests (Payday ≤ $50 equivalent). */
	public boolean withinProductCap(PdlLoanTypeEnum loanType, String currency, double principal) {
		if (loanType != null && loanType != PdlLoanTypeEnum.PAYDAY)
			return false; // not launched
		if ("KHR".equalsIgnoreCase(normalizeCurrency(currency))) {
			List<Double> t = tiers("KHR");
			return principal <= t.get(t.size() - 1);
		}
		return principal <= paydayMaxAmount;
	}

	private double feesFor(String currency) {
		return feeComponent(currency, processingFee) + feeComponent(currency, cbcFee);
	}

	/**
	 * Fees are defined in USD ($3/$1, QC1.3). For KHR quotes convert at the
	 * configured indicative rate so the arithmetic stays in one currency.
	 */
	@Value("${pdl.pricing.khr-per-usd:4100}")
	private double khrPerUsd;

	private double feeComponent(String currency, double usdFee) {
		if ("KHR".equalsIgnoreCase(normalizeCurrency(currency)))
			return round2(usdFee * khrPerUsd);
		return usdFee;
	}

	private String normalizeCurrency(String c) {
		return (c == null || c.isBlank()) ? "USD" : c.trim().toUpperCase();
	}

	private static double round2(double v) {
		return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}
}
