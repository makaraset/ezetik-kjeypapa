package com.ezetik.kjeypapa.security.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;

import com.ezetik.kjeypapa.security.auth.AuthenticationRequest;
import com.ezetik.kjeypapa.security.auth.AuthenticationResponse;
import com.ezetik.kjeypapa.security.model.RegisterModel;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.model.UserModel;
import com.ezetik.kjeypapa.security.model.VerificationToken;
import com.ezetik.kjeypapa.security.util.Message;

public interface UserService {

	ResponseEntity<Message<User>> registerUser(RegisterModel userModel);

	ResponseEntity<Message<User>> createUser(UserModel userModel);

	void saveVerificationTokenForUser(String token, User user);

	String validateVerificationToken(String token);

	String validateOTP(int otp);

	VerificationToken generateNewVerificationToken(String oldToken);

	User findUserByEmail(String email);

	void createPasswordResetTokenForUser(User user, String token);

	String validatePasswordResetToken(String token);

	Optional<User> getUserByPasswordResetToken(String token);

	User getUserByPasswordResetOTP(int otp);

	void changePassword(User user, String newPassword);

	boolean checkIfValidOldPassword(User user, String oldPassword);

	User update(Integer userId, User user);

	User findUserByUsername(String username);

	User findUserByRegisteredId(String registeredId);

	List<User> retrieveAllUsers();

	Optional<User> retrieveUser(Integer userId);

	boolean isUsernameExist(String username);

	long deleteUser(String username);

	AuthenticationResponse authenticate(AuthenticationRequest request);

}
