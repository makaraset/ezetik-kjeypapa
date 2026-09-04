package com.ezetik.kjeypapa.pdl.service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.payload.LosProductSyncPayload;

/**
 * Integration boundary to Sambat LOS (TurnKey Lender).
 *
 * The exact endpoints/params are TBD (BRS Appendices 1-6); {@link LosProviderImpl}
 * runs in mock mode until they are delivered, so the full app↔backend flow is
 * testable without a live LOS.
 */
public interface LosProvider {

	/** BRS 2.3 — submit a new application; returns the LOS application number. */
	String submitApplication(PaydayLoan loan);

	/**
	 * BRS 2.7 — relay the customer's accept ("Y" + signed contract) / reject
	 * ("N"). Takes the loan rather than a reference because Sambat's
	 * {@code /customer-accepted} is keyed by their AppId.
	 */
	void sendDecision(PaydayLoan loan, String decision, String signedContractRef);

	/** BRS 2.2 — receive a product-config push from LOS. */
	void onProductSync(LosProductSyncPayload payload);
}
