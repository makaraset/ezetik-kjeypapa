package com.ezetik.kjeypapa.security.service;

import java.security.SecureRandom;

public class RandomPasswordGenerator {
	private final int numberOfCharactersInPassword;
    private static final SecureRandom secureRandom = new SecureRandom();

    public RandomPasswordGenerator(final int numberOfCharactersInPassword) {
        this.numberOfCharactersInPassword = numberOfCharactersInPassword;
    }

    public String generate() {

        final StringBuilder passwordBuilder = new StringBuilder(this.numberOfCharactersInPassword);
        for (int i = 0; i < this.numberOfCharactersInPassword; i++) {
            passwordBuilder.append((char) ((int) (secureRandom.nextDouble() * 26) + 97));
        }
        return passwordBuilder.toString();
    }
}
