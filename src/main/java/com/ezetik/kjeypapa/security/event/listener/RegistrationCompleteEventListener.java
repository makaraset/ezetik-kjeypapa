package com.ezetik.kjeypapa.security.event.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.sbf.service.SMSService;
import com.ezetik.kjeypapa.security.event.RegistrationCompleteEvent;
import com.ezetik.kjeypapa.security.model.OneTimePassword;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.OneTimePasswordService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RegistrationCompleteEventListener implements ApplicationListener<RegistrationCompleteEvent> {

//	@Autowired
//	private UserService userService;
//
//	@Autowired
//	private EmailService emailService;

	@Autowired
	private OneTimePasswordService otpService;

	@Autowired
	private SMSService sms;

	@Override
	public void onApplicationEvent(RegistrationCompleteEvent event) {
		// Create the Verification Token for the User with Link
		User user = event.getUser();
		OneTimePassword otp = otpService.returnOneTimePassword(user);

		sms.sendSms(user.getPhoneNumber(), "Kjeypapa_OTP_" + otp.getOneTimePasswordCode());

//		https://tricube-uat.sambatfinance.com:6443/api/sendmessgae?mgsStr=Kjeypapa_OTP_314612&sendToNumber=012551101
//		https://tricube-uat.sambatfinance.com:6443/api/sendmessgae?mgsStr=Kjeypapa_OTP_314612&sendToNumber=012551101

//		String token = UUID.randomUUID().toString();
//		userService.saveVerificationTokenForUser(token, user);
		// Send Mail to user
		// String url = event.getApplicationUrl() + "/verifyRegistration?token=" +
		// token;

		// sendVerificationEmail()
		log.info("***********OTP***********: {}", otp.getOneTimePasswordCode());
//		try {
//			EmailDetails email = new EmailDetails();
//			email.setSubject("SBF OTP");
//			email.setRecipient(user.getEmail());
//			email.setMsgBody(otp.getOneTimePasswordCode().toString());
//			String status = emailService.sendSimpleMail(email);
//			log.info(status);
//		} catch (Exception e) {
//			log.error(e.getMessage());
//		}

	}
}
