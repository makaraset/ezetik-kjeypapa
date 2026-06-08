package com.ezetik.kjeypapa.security.util;

public interface EmailService {

	String sendSimpleMail(EmailDetails details);
	String sendMailWithAttachment(EmailDetails details);
	
}
