package com.ezetik.kjeypapa.sbf.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.sbf.model.ConsolidateData;
import com.ezetik.kjeypapa.sbf.model.DisburseMessage;
import com.ezetik.kjeypapa.sbf.model.DisburseModel;
import com.ezetik.kjeypapa.sbf.model.Holiday;
import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.model.LoanFacilityInfo;
import com.ezetik.kjeypapa.sbf.model.Merchant;
import com.ezetik.kjeypapa.security.service.SbfAuthorization;
import com.ezetik.kjeypapa.security.util.Message;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Service
public class SBFApiServiceImpl implements SBFApiService {

	@Value("${url_api}")
	private String basedUrl;

	@Value("${urlencoded_facility}")
	private String postUrl;

	@Value("${url_create_loan_fac}")
	private String urlCreateLoanFac;

	@Value("${url_merchant_cif}")
	private String urlMerchantByCIf;

	@Value("${url_merchant}")
	private String urlMerchant;

	@Value("${url_merchant_code}")
	private String urlMerchantCode;

	@Value("${url_disburse}")
	private String urlDisburse;

	@Value("${url_non_working_day}")
	private String urlNonWorkingDay;

	@Autowired
	private SbfAuthorization auth;

	@Autowired
	private ObjectMapper mapper;

	@Override
	public ConsolidateData getFacilityByCIF(Long cif) {

		ConsolidateData data;
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + postUrl + cif))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				data = mapper.readValue(response.body(), ConsolidateData.class);

				return prepareData(data);
			} else {
				return null;
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	ConsolidateData prepareData(ConsolidateData data) {

		if (!data.equals(null)) {
			int activeNote = 0;
			double outstanding = 0.0;

			List<Integer> activeStatus = Arrays.asList(1, 3, 6, 7, 10);

			for (LoanFacilityInfo lf : data.getLoanFacilityInfo()) {

				if (activeStatus.contains(lf.getLoanStatus())) {
					activeNote++;
					outstanding += lf.getOutstanding();
				}
			}

			data.getCreditFacilityMaster().setActiveNote(activeNote);
			data.getCreditFacilityMaster().setOutstandingBalance(outstanding);

		} else {
			System.out.println("Null " + data);
		}

		return data;
	}

	@Override
	public ResponseEntity<Message<ConsolidateData>> getAccountByCif(Long cif) {

		ResponseEntity<Message<ConsolidateData>> resp = null;

		ConsolidateData data = new ConsolidateData();

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + postUrl + cif))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				data = mapper.readValue(response.body(), ConsolidateData.class);
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Successfully retrieved data", prepareData(data)),
						HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("FAILED", response.body(), null), HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public LoanFacility createLoanFac(LoanFacility loanFac) {

		try {
			LoanFacility resp = new LoanFacility();

			ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
			String json = ow.writeValueAsString(loanFac);

			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlCreateLoanFac))
					.header("Authorization", "Bearer " + auth.getAccessToken())
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			System.out.println("res: " + response);

			if (response.statusCode() == 200) {

				resp = mapper.readValue(response.body(), LoanFacility.class);

				return resp;
			} else {
				return null;
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public ResponseEntity<Message<List<Merchant>>> getMyMerchant(Long cif) {

		ResponseEntity<Message<List<Merchant>>> resp = null;

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlMerchantByCIf + cif))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				var data = mapper.readValue(response.body(), new TypeReference<Message<List<Merchant>>>() {
				});

				resp = new ResponseEntity<>(data, HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("FAILED", response.body(), null), HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<Merchant>> getMerchantById(int id) {

		ResponseEntity<Message<Merchant>> resp = null;

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlMerchant + "/" + id))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				var data = mapper.readValue(response.body(), new TypeReference<Message<Merchant>>() {
				});

				resp = new ResponseEntity<>(data, HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("FAILED", response.body(), null), HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<List<Merchant>>> getAllMerchant() {
		ResponseEntity<Message<List<Merchant>>> resp = null;

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlMerchant))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				var data = mapper.readValue(response.body(), new TypeReference<Message<List<Merchant>>>() {
				});

				resp = new ResponseEntity<>(data, HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("FAILED", response.body(), null), HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public ResponseEntity<Message<Merchant>> getMerchantByCode(String code) {
		ResponseEntity<Message<Merchant>> resp = null;

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlMerchantCode + code))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				var data = mapper.readValue(response.body(), new TypeReference<Message<Merchant>>() {
				});

				resp = new ResponseEntity<>(data, HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(new Message<>("FAILED", response.body(), null), HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@Override
	public DisburseMessage autoDisburse(DisburseModel disburseModel) {
		try {

			DisburseMessage resp = new DisburseMessage();

			ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
			String json = ow.writeValueAsString(disburseModel);

			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlDisburse))
					.header("Authorization", "Bearer " + auth.getAccessToken())
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {

				resp = mapper.readValue(response.body(), DisburseMessage.class);

				return resp;
			} else {
				return null;
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public ResponseEntity<List<Holiday>> getHolidays() {
		ResponseEntity<List<Holiday>> resp = null;

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(basedUrl + urlNonWorkingDay))
					.header("Authorization", "Bearer " + auth.getAccessToken()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {

				var data = mapper.readValue(response.body(), new TypeReference<List<Holiday>>() {
				});

				resp = new ResponseEntity<>(data, HttpStatus.OK);

			} else {
				resp = new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

}
