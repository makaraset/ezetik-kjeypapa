package com.ezetik.kjeypapa.security.service;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.security.model.OneTimePassword;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.OneTimePasswordRepository;

@Service
public class OneTimePasswordService {

	private final Long expiryInterval = 5L * 60 * 1000;

	@Autowired
	private OneTimePasswordRepository oneTimePasswordRepository;

	OneTimePasswordHelpService oneTimePasswordHelpService;

//	@Autowired
//	public OneTimePasswordService(OneTimePasswordRepository oneTimePasswordRepository) {
//		this.oneTimePasswordRepository = oneTimePasswordRepository;
//	}

	public OneTimePassword returnOneTimePassword(User user) {

		OneTimePassword oneTimePassword = new OneTimePassword();

		oneTimePassword.setOneTimePasswordCode(oneTimePasswordHelpService.createRandomOneTimePassword().get());
		oneTimePassword.setExpires(new Date(System.currentTimeMillis() + expiryInterval));
		oneTimePassword.setUser(user);

		oneTimePasswordRepository.save(oneTimePassword);

		return oneTimePassword;
	}
}
