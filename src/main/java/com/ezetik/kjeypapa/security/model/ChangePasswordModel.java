package com.ezetik.kjeypapa.security.model;

import lombok.Data;

@Data
public class ChangePasswordModel {

	private String oldPassword;
	private String newPassword;
}
