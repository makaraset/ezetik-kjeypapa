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
 * {@link #sendDecision} relays a customer's acceptance through
 * {@code POST /customer-accepted} — the step that lets Sambat move an approved
 * application to disbursement.
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


	@Autowired
	private LosSubmitConfig submitConfig;

	@Autowired
	private SbfCifResolver cifResolver;

	@Value("${los.mock.enabled:true}")
	private boolean mockEnabled;

	@Value("${pdl.customer.hotline:023 9977 22 or 093 99 77 22}")
	private String hotline;

	/**
	 * Optional extras on {@code /customer-accepted}. Their swagger types both
	 * as plain strings and documents neither; sending our own guess could put
	 * invented wording into an SMS to a real customer, so both default to
	 * empty until Sambat says what belongs in them.
	 */
	@Value("${los.accept.sms-text:}")
	private String acceptSmsText;

	@Value("${los.accept.trn-code:}")
	private String acceptTrnCode;

	@Override
	public String submitApplication(PaydayLoan loan, byte[] consentPdf) {
		if (mockEnabled) {
			return "LOS-MOCK-" + loan.getId();
		}

		// Settle the CIF before filing. custId 0 for a customer Sambat already
		// hold earns a second CIF or an automatic reject (Sambat, 2026-11-05),
		// so 0 goes only to someone they have looked for and not found. Nothing
		// is filed while they cannot be asked, or hold the ID number under
		// other details. The mapper reads the CIF this stores on the user.
		SbfCifResolver.CifCheck cif = cifResolver.ensure(loan.getUser());
		if (cif.outcome() == SbfCifResolver.Outcome.UNAVAILABLE)
			throw new LosSubmitException("SBF_CUSTOMER_UNAVAILABLE",
					"We could not check your details with Sambat just now. Please try again in a few minutes.");
		if (cif.outcome() == SbfCifResolver.Outcome.MISMATCH)
			throw new LosSubmitException("SBF_CUSTOMER_MISMATCH",
					"Your details do not match Sambat's records for this ID number. Please call us on "
							+ hotline + ".");

		NewApplicationParam param = mapper.toParam(loan, consentPdf);
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
		loan.setLosAppId(result.getLosAppId());
		return String.valueOf(result.getAppRefId());
	}

	@Override
	public void sendDecision(PaydayLoan loan, String decision, String signedContractRef) {
		if (mockEnabled) {
			log.info("[LOS mock] decision {} for mobileAppId {}{}", decision, loan.getId(),
					signedContractRef != null ? " (contract " + signedContractRef + ")" : "");
			return;
		}

		// Sambat exposes NO decline endpoint — their API has /customer-accepted
		// and nothing for "the customer said no" or an expired offer. Those
		// close on our side only; raised with them. Logged so the gap is
		// visible in the record rather than silently dropped.
		if (!"Y".equalsIgnoreCase(decision)) {
			log.info("Loan {} declined/expired — Sambat has no decline endpoint, nothing relayed",
					loan.getId());
			return;
		}

		// Their /customer-accepted is keyed by AppId, so a loan filed before we
		// stored one cannot be relayed. Fail loudly: silently skipping would
		// leave an accepted loan that Sambat never disburses.
		if (loan.getLosAppId() == null)
			throw new LosSubmitException("LOS_NO_APP_ID",
					"This application has no Sambat application id, so the acceptance cannot be sent.");

		try {
			sbf.customerAccepted(loan.getId(), loan.getLosAppId(), submitConfig.getDoneBy(), acceptSmsText,
					acceptTrnCode);
		} catch (LosSubmitException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Acceptance relay failed for loan {}: {}", loan.getId(), e.toString());
			throw new LosSubmitException("LOS_ACCEPT_FAILED",
					"Could not confirm your acceptance with Sambat. Please try again.");
		}
	}

	@Override
	public void onProductSync(LosProductSyncPayload payload) {
		// TODO: persist product config (BRS Appendix 1). No-op for the mock slice.
		System.out.println("[LOS mock] product sync: " + (payload != null ? payload.getProductCode() : "null"));
	}
}
