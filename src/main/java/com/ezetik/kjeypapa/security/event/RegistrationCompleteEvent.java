package com.ezetik.kjeypapa.security.event;

import org.springframework.context.ApplicationEvent;

import com.ezetik.kjeypapa.security.model.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationCompleteEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;
	private final User user;

	public RegistrationCompleteEvent(User user) {
		super(user);
		this.user = user;
//		this.otp = otp;
	}
}
