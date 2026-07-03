package com.ezetik.kjeypapa.pdl.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.payload.LosProductSyncPayload;

/**
 * Mock-capable Sambat LOS client.
 *
 * While {@code los.mock.enabled=true} (the default) this returns canned results
 * so the whole PDL flow works end-to-end without a live LOS. When the real
 * contract arrives (BRS Appendices 1-6), implement the HTTP calls here (using a
 * {@code LosAuthorization} OAuth helper mirroring {@code SbfAuthorization}) and
 * set {@code los.mock.enabled=false}.
 */
@Service
public class LosProviderImpl implements LosProvider {

	@Value("${los.mock.enabled:true}")
	private boolean mockEnabled;

	@Override
	public String submitApplication(PaydayLoan loan) {
		if (mockEnabled) {
			return "LOS-MOCK-" + loan.getId();
		}
		throw new UnsupportedOperationException(
				"Real LOS submit is TBD — see plans/PDL_IMPLEMENTATION.md (BRS Appendix 2).");
	}

	@Override
	public void sendDecision(String losApplicationNo, String decision, String signedContractRef) {
		if (mockEnabled) {
			System.out.println("[LOS mock] decision " + decision + " for " + losApplicationNo
					+ (signedContractRef != null ? " (contract " + signedContractRef + ")" : ""));
			return;
		}
		throw new UnsupportedOperationException(
				"Real LOS accept/reject is TBD — see plans/PDL_IMPLEMENTATION.md (BRS Appendix 6).");
	}

	@Override
	public void onProductSync(LosProductSyncPayload payload) {
		// TODO: persist product config (BRS Appendix 1). No-op for the mock slice.
		System.out.println("[LOS mock] product sync: " + (payload != null ? payload.getProductCode() : "null"));
	}
}
