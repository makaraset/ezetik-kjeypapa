package com.ezetik.kjeypapa.security.model;

import lombok.Data;

@Data
public class PasswordModel {

	private String userName;
	private String oldPassword;
	private String newPassword;
}
