package com.ezetik.kjeypapa.security.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserModel {

	private int id;
	private String username;
	private String firstName;
	private String lastName;
	private String password;
	private String email;
	private String phoneNumber;
	private GenderEnum gender;
	private String dateOfBirth;
	private List<Long> roles;
	private boolean enabled;

}