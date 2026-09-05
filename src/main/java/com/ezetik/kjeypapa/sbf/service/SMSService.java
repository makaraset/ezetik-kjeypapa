package com.ezetik.kjeypapa.sbf.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import com.ezetik.kjeypapa.security.service.SbfAuthorization;

@Service
public class SMSService {

	@Value("${url_api}")
	private String basedUrl;

	@Value("${url_sms}")
	private String smsUrl;

	@Autowired
	private SbfAuthorization auth;

	public String sendSms(String phoneNumber, String message) {

		try {
			String uri = basedUrl + smsUrl + "?mgsStr=" + UriUtils.encodePath(message, "UTF-8") + "&sendToNumber="
					+ UriUtils.encodeQueryParam(phoneNumber, "UTF-8");

			HttpRequest request = HttpRequest.newBuilder().uri(new URI(uri))
					.header("Authorization", "Bearer " + auth.getAccessToken())
					.POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				return response.body();
			} else {
				return "Error: " + response.body();
			}

		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

}
