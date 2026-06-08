package com.ezetik.kjeypapa.security.service;

import java.util.Random;
import java.util.function.Supplier;

public class OneTimePasswordHelpService {

	private final static Integer LENGTH = 4;

	public static Supplier<Integer> createRandomOneTimePassword() {
		return () -> {
			Random random = new Random();
			StringBuilder oneTimePassword = new StringBuilder();
			for (int i = 0; i < LENGTH; i++) {
				int randomNumber = random.nextInt(9) + 1;
				oneTimePassword.append(randomNumber);
			}
			return Integer.parseInt(oneTimePassword.toString().trim());
		};
	}
}
