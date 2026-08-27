package com.ezetik.kjeypapa.pdl.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.payload.LosProductSyncPayload;
import com.ezetik.kjeypapa.pdl.payload.los.LosPostResponse;
import com.ezetik.kjeypapa.pdl.payload.los.LosPostResult;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Sambat LOS client.
 *
 * <p>While {@code los.mock.enabled=true} (still the default) this returns
 * canned results so the whole PDL flow works end-to-end without touching
 * Sambat. With it false, {@link #submitApplication} really files the
 * application via {@code POST /new-loan-application} on the same Tricube
 * gateway and OAuth server the rest of our SBF calls already use — there is no
 * separate LOS host.
 *
 * <p>Real mode refuses to start until {@link LosSubmitConfig} is fully
 * populated, because their master-list codes cannot be guessed safely.
 * {@code sendDecision} is still mock-only.
 */
@Slf4j
@Service
public class LosProviderImpl implements LosProvider {

	@Autowired
	private LosApplicationMapper mapper;

	@Autowired
	private SbfGatewayClient sbf;

	@Autowired
	private ObjectMapper om;


	@Value("${los.mock.enabled:true}")
	private boolean mockEnabled;

	@Override
	public String submitApplication(PaydayLoan loan) {
		if (mockEnabled) {
			return "LOS-MOCK-" + loan.getId();
		}

		NewApplicationParam param = mapper.toParam(loan);
		JsonNode raw;
		try {
			raw = sbf.newLoanApplication(param);
		} catch (LosSubmitException e) {
			throw e;
		} catch (Exception e) {
			log.warn("LOS submit transport failure for loan {}: {}", loan.getId(), e.toString());
			throw new LosSubmitException("LOS_UNAVAILABLE", "Could not reach Sambat's loan system.");
		}

		LosPostResponse response;
		try {
			response = om.treeToValue(raw, LosPostResponse.class);
		} catch (Exception e) {
			log.warn("LOS submit: unreadable response for loan {}: {}", loan.getId(), e.toString());
			throw new LosSubmitException("LOS_BAD_RESPONSE", "Sambat's loan system returned an unexpected response.");
		}

		// A rejection arrives as HTTP 200 with IsSuccess "False", so the body
		// decides, not the status code.
		if (!response.succeeded()) {
			List<String> missing = response.missingFields();
			if (!missing.isEmpty())
				throw new LosSubmitException("R-MISSINGDATA",
						"Sambat needs more information before this application can be filed.", missing);
			String why = response.errorText();
			throw new LosSubmitException("R-REJECTED",
					why == null || why.isBlank() ? "Sambat rejected the application." : why);
		}

		// Guard before indexing: a failure body carries "Result": [].
		if (response.getResult() == null || response.getResult().isEmpty()
				|| response.getResult().get(0).getAppRefId() == null)
			throw new LosSubmitException("LOS_BAD_RESPONSE",
					"Sambat accepted the application but returned no reference.");

		LosPostResult result = response.getResult().get(0);
		loan.setLosAppId(result.getAppId());
		return String.valueOf(result.getAppRefId());
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
