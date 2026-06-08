package com.ezetik.kjeypapa.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.sbf.service.SMSService;
import com.ezetik.kjeypapa.security.service.OneTimePasswordService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/otp/")
@Tag(name = "01- Authentication API", description = "User authentication controller")
@PreAuthorize("hasRole('ADMIN')")
public class OneTimePasswordController {

	@Autowired
	private OneTimePasswordService oneTimePAsswordService;

	@Autowired
	private SMSService sms;

//	@Autowired
//	public OneTimePasswordController(OneTimePasswordService oneTimePAsswordService) {
//		this.oneTimePAsswordService = oneTimePAsswordService;
//	}

	@GetMapping("/create")
	@ResponseBody
	private Object getOneTimePassword() {
		try {
			return ResponseEntity.ok(oneTimePAsswordService.returnOneTimePassword(null));
		} catch (Exception exception) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/sms")
	public String sendSms(String phoneNumber, String message) {
		return sms.sendSms(phoneNumber, message);
	}
}
