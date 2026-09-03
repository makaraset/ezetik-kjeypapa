package com.ezetik.kjeypapa.pdl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ezetik.kjeypapa.pdl.model.PdlLoanTypeEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlQuoteResponse;
import com.ezetik.kjeypapa.pdl.service.PdlPricingService;

/**
 * Quote math per Sambat's 2026-08-13 answers (QC1.3): monthly interest 1.5%
 * PRO-RATED by loan period — the confirmed worked example is repayment $50 over
 * 15 days → principal $49.63, interest $0.37, net credited $45.63.
 */
class PdlPricingServiceTest {

	private final PdlPricingService pricing = new PdlPricingService();

	@BeforeEach
	void config() {
		// Mirror application.properties defaults (no Spring context in unit tests).
		ReflectionTestUtils.setField(pricing, "monthlyInterestPercent", 1.5);
		ReflectionTestUtils.setField(pricing, "processingFee", 3.0);
		ReflectionTestUtils.setField(pricing, "cbcFee", 1.0);
		ReflectionTestUtils.setField(pricing, "periodDays", 15);
		ReflectionTestUtils.setField(pricing, "disbursementOffsetDays", 1);
		ReflectionTestUtils.setField(pricing, "paydayMaxAmount", 50.0);
		ReflectionTestUtils.setField(pricing, "usdTiers", "10,20,30,40,50");
		ReflectionTestUtils.setField(pricing, "khrTiers", "40000,80000,120000,160000,200000");
		ReflectionTestUtils.setField(pricing, "khrPerUsd", 4100.0);
	}

	@Test
	void quote_reproducesTheConfirmedSambatExample() {
		PdlQuoteResponse q = pricing.quote(PdlLoanTypeEnum.PAYDAY, "USD", 50.0);

		assertThat(q.getLoanAmount()).isEqualTo(49.63); // principal = disbursed
		assertThat(q.getInterestAmount()).isEqualTo(0.37);
		assertThat(q.getNetDisbursedAmount()).isEqualTo(45.63); // 49.63 − 3 − 1
		assertThat(q.getLoanPeriodDays()).isEqualTo(15);
		assertThat(q.getInterestRatePercent()).isEqualTo(1.5);
		assertThat(q.getRepaymentDate()).isAfter(q.getDisbursementDate());
		assertThat(q.getTiers()).containsExactly(10.0, 20.0, 30.0, 40.0, 50.0);
	}

	@Test
	void quote_rejectsAnOffTierAmount() {
		assertThatThrownBy(() -> pricing.quote(PdlLoanTypeEnum.PAYDAY, "USD", 45.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not an offered tier");
	}

	@Test
	void quote_rejectsUnlaunchedProducts() {
		assertThatThrownBy(() -> pricing.quote(PdlLoanTypeEnum.MICRO, "USD", 50.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not yet available");
	}

	@Test
	void quote_khrUsesKhrTiersAndConvertedFees() {
		PdlQuoteResponse q = pricing.quote(PdlLoanTypeEnum.PAYDAY, "KHR", 200000.0);

		// principal = 200000 / 1.0075 = 198511.166… → whole riel; fees = (3+1) × 4100
		assertThat(q.getLoanAmount()).isEqualTo(198511.0);
		assertThat(q.getProcessingFee()).isEqualTo(12300.0);
		assertThat(q.getCbcEnquiryFee()).isEqualTo(4100.0);
		assertThat(q.getNetDisbursedAmount()).isEqualTo(182111.0);
	}

	@Test
	void withinProductCap_capsPaydayAndBlocksUnlaunched() {
		assertThat(pricing.withinProductCap(PdlLoanTypeEnum.PAYDAY, "USD", 50.0)).isTrue();
		assertThat(pricing.withinProductCap(PdlLoanTypeEnum.PAYDAY, "USD", 50.01)).isFalse();
		assertThat(pricing.withinProductCap(PdlLoanTypeEnum.MICRO, "USD", 10.0)).isFalse();
		assertThat(pricing.withinProductCap(PdlLoanTypeEnum.PAYDAY, "KHR", 200000.0)).isTrue();
		assertThat(pricing.withinProductCap(PdlLoanTypeEnum.PAYDAY, "KHR", 200001.0)).isFalse();
	}

	@Test
	void quote_khrIsWholeRielAndTheBreakdownReconciles() {
		// Riel has no circulating subunit, so a KHR quote must not emit
		// fractions (we filed 39,702.23 KHR at Sambat before this was fixed).
		for (double tier : new double[] { 40000, 80000, 120000, 160000, 200000 }) {
			PdlQuoteResponse q = pricing.quote(PdlLoanTypeEnum.PAYDAY, "KHR", tier);
			assertThat(q.getLoanAmount()).isEqualTo(Math.rint(q.getLoanAmount()));
			assertThat(q.getInterestAmount()).isEqualTo(Math.rint(q.getInterestAmount()));
			assertThat(q.getNetDisbursedAmount()).isEqualTo(Math.rint(q.getNetDisbursedAmount()));
			// The figures on screen must add up exactly, after rounding.
			assertThat(q.getLoanAmount() + q.getInterestAmount()).isEqualTo(tier);
			assertThat(q.getNetDisbursedAmount() + q.getProcessingFee() + q.getCbcEnquiryFee())
					.isEqualTo(q.getLoanAmount());
		}
	}

	@Test
	void quote_usdKeepsCents() {
		PdlQuoteResponse q = pricing.quote(PdlLoanTypeEnum.PAYDAY, "USD", 20.0);
		assertThat(q.getLoanAmount()).isEqualTo(19.85);
		assertThat(q.getInterestAmount()).isEqualTo(0.15);
		assertThat(q.getNetDisbursedAmount()).isEqualTo(15.85);
	}
}
