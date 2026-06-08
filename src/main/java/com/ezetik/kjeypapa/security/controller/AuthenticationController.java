package com.ezetik.kjeypapa.security.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.sbf.service.SMSService;
import com.ezetik.kjeypapa.security.auth.AuthenticationRequest;
import com.ezetik.kjeypapa.security.auth.AuthenticationResponse;
import com.ezetik.kjeypapa.security.model.ChangePasswordModel;
import com.ezetik.kjeypapa.security.model.OneTimePassword;
import com.ezetik.kjeypapa.security.model.PasswordModel;
import com.ezetik.kjeypapa.security.model.RegisterModel;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.OneTimePasswordService;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.EmailDetails;
import com.ezetik.kjeypapa.security.util.EmailService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "01- Authentication API", description = "User authentication controller")
public class AuthenticationController {
	@Autowired
	private UserService userService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private OneTimePasswordService otpService;

	private FileNameHelper fileHelper = new FileNameHelper();

	@Autowired
	private SMSService sms;

	@PostMapping("/authenticate")
	public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
		return ResponseEntity.ok(userService.authenticate(request));
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestBody RegisterModel userModel) {

		return userService.registerUser(userModel);
	}

	@PostMapping("/verifyOTP")
	public String verifyOTP(@RequestParam("otp") int otp) {
		String result = userService.validateOTP(otp);
		if (result.equalsIgnoreCase("valid")) {
			return "SUCCESS";
		}
		return result;
	}

	@PostMapping("/resendOTP/{userId}")
//	@PreAuthorize("hasAuthority('SCOPE_api.security')")
	public ResponseEntity<String> resendOTP(@PathVariable("userId") String userId) {

		ResponseEntity<String> resp = null;
		try {

			User user = userService.findUserByUsername(userId);
			if (user != null) {
				OneTimePassword otp = otpService.returnOneTimePassword(user);

				sms.sendSms(user.getPhoneNumber(), "Kjeypapa_OTP_" + otp.getOneTimePasswordCode());

				resp = new ResponseEntity<>("SUCCESS", HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>("NOT_FOUND", HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@PostMapping("/resetPassword")
	public ResponseEntity<String> resetPassword(@RequestParam("otp") int otp,
			@RequestBody PasswordModel passwordModel) {

		String result = userService.validateOTP(otp);
		if (!result.equalsIgnoreCase("valid")) {
			return new ResponseEntity<>("Invalid OTP", HttpStatus.BAD_REQUEST);
		}

		User user = userService.getUserByPasswordResetOTP(otp);
		if (user != null) {
			userService.changePassword(user, passwordModel.getNewPassword());
			return new ResponseEntity<>("Password Reset Successfully", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Invalid OTP", HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/savePassword")
	public String savePassword(@RequestParam("token") String token, @RequestBody PasswordModel passwordModel) {
		String result = userService.validatePasswordResetToken(token);
		if (!result.equalsIgnoreCase("valid")) {
			return "Invalid Token";
		}
		Optional<User> user = userService.getUserByPasswordResetToken(token);
		if (user.isPresent()) {
			userService.changePassword(user.get(), passwordModel.getNewPassword());
			return "Password Reset Successfully";
		} else {
			return "Invalid Token";
		}
	}

	@PostMapping("/changePassword")
	public ResponseEntity<String> changePassword(@RequestBody ChangePasswordModel passwordModel) {
		User user = userService.findUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName());

		if (!userService.checkIfValidOldPassword(user, passwordModel.getOldPassword())) {
			return new ResponseEntity<>("Invalid Old Password", HttpStatus.BAD_REQUEST);
		}
		// Save New Password
		userService.changePassword(user, passwordModel.getNewPassword());
		return new ResponseEntity<>("Password Changed Successfully", HttpStatus.OK);
	}

	@GetMapping("/me")
	public ResponseEntity<Message<User>> getMyInfo() {

		ResponseEntity<Message<User>> resp = null;
		try {
			User user = userService
					.findUserByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
			if (user != null) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", user), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "Data not found", null), HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", "Unable to feth data.", null),
					HttpStatus.INTERNAL_SERVER_ERROR);

		}

		return resp;
	}

	private String passwordResetTokenMail(User user, String applicationUrl, String token) {
		String url = applicationUrl + "/savePassword?token=" + token;

		// sendVerificationEmail()
		log.info("Click the link to Reset your Password: {}", url);
		return url;
	}

	private void resendOTPMail(OneTimePassword otp) {
		try {
			EmailDetails email = new EmailDetails();
			email.setSubject("SBF OTP");
			email.setRecipient(otp.getUser().getEmail());
			email.setMsgBody(otp.getOneTimePasswordCode().toString());
			String status = emailService.sendSimpleMail(email);
			log.info(status);
		} catch (Exception e) {
			log.error(e.getMessage());
		}

	}

	private String applicationUrl(HttpServletRequest request) {
		return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/user";
	}

}
