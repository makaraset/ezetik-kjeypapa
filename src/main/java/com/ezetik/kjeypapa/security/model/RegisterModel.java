package com.ezetik.kjeypapa.security.model;

import java.util.List;

import lombok.Data;

@Data
public class RegisterModel {

	private String registeredId; // CIF or MerchantId
	private String username;
	private String phoneNumber;
	private String password;
	private GenderEnum gender;
	private String dateOfBirth;
	private List<Long> roles;

}
