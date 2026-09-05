package com.ezetik.kjeypapa.security.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.sbf.model.ConsolidateData;
import com.ezetik.kjeypapa.sbf.model.CustomerInformation;
import com.ezetik.kjeypapa.sbf.model.Merchant;
import com.ezetik.kjeypapa.sbf.service.SBFApiService;
import com.ezetik.kjeypapa.security.auth.AuthenticationRequest;
import com.ezetik.kjeypapa.security.auth.AuthenticationResponse;
import com.ezetik.kjeypapa.security.event.RegistrationCompleteEvent;
import com.ezetik.kjeypapa.security.model.OneTimePassword;
import com.ezetik.kjeypapa.security.model.PasswordResetToken;
import com.ezetik.kjeypapa.security.model.RegisterModel;
import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.model.UserModel;
import com.ezetik.kjeypapa.security.model.VerificationToken;
import com.ezetik.kjeypapa.security.repository.OneTimePasswordRepository;
import com.ezetik.kjeypapa.security.repository.PasswordResetTokenRepository;
import com.ezetik.kjeypapa.security.repository.RoleRepository;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.repository.VerificationTokenRepository;
import com.ezetik.kjeypapa.security.util.Message;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private VerificationTokenRepository verificationTokenRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ApplicationEventPublisher publisher;

	@Autowired
	private OneTimePasswordRepository otpRepo;

	@Autowired
	private SBFApiService sbfService;

	@Autowired
	private RoleRepository roleRepo;

	@Autowired
	private com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository pdlAccountRequestRepository;

	@Override
	public ResponseEntity<Message<User>> registerUser(RegisterModel u) {

		ResponseEntity<Message<User>> resp = null;

		try {

			User user = new User();

			boolean isMerchant = false;
			boolean isCustomer = false;
			boolean isBoth = false;

			User isUsernameExist = userRepository.findByUsername(u.getUsername());

			if (isUsernameExist != null) {
				return new ResponseEntity<>(
						new Message<>("USERNAME_EXIST", "Username is already exist, Please change it.", null),
						HttpStatus.NOT_ACCEPTABLE);
			}

			if (userRepository.findByRegistedId(u.getRegisteredId()).isPresent()) {
				return new ResponseEntity<>(
						new Message<>("REGISTRED_ID_EXIST", "Your CIF/Merchant code is already used.", null),
						HttpStatus.NOT_ACCEPTABLE);
			}

			for (Long role : u.getRoles()) {

				if (role.equals(Long.valueOf(2)))
					isCustomer = true;

				if (role.equals(Long.valueOf(3)))
					isMerchant = true;

				if (role.equals(Long.valueOf(4)))
					isBoth = true;

				if (!role.equals(Long.valueOf(2)) && !role.equals(Long.valueOf(3)) && !role.equals(Long.valueOf(4)))
					return new ResponseEntity<>(
							new Message<>("INCORRECT_ROLE", "Your selected role is incorrect", null),
							HttpStatus.BAD_REQUEST);
			}

			if (isCustomer || isBoth) {

				ConsolidateData data = sbfService.getFacilityByCIF(Long.valueOf(u.getRegisteredId()));

				if (data.getCreditFacilityMaster() != null) {

					var checkUser = userRepository.findByRegistedId(u.getRegisteredId());
					if (checkUser.isPresent()) {
						return new ResponseEntity<>(new Message<>("INCORRECT_INFO", "CIF is already used", null),
								HttpStatus.BAD_REQUEST);
					}

					CustomerInformation cust = data.getCustomerInformation();

					if (!u.getGender().equals(cust.getSex()) || !u.getPhoneNumber().equals(cust.getPhoneNumber())
							|| !u.getDateOfBirth().equals(cust.getDob())) {
						return new ResponseEntity<>(
								new Message<>("INCORRECT_INFO", "Your information is incorrect", null),
								HttpStatus.BAD_REQUEST);
					}

					user.setEmail(cust.getEmail());
					user.setFirstname(cust.getFname());
					user.setLastname(cust.getLname());

				} else {
					resp = new ResponseEntity<>(
							new Message<>("CIF Not Found", "Please verify your customer ID again.", null),
							HttpStatus.BAD_REQUEST);
				}
			} else if (isMerchant) {

				Merchant merchant = null;
				var merchantSbf = sbfService.getMerchantByCode(u.getRegisteredId());
				if (merchantSbf.getStatusCode().equals(HttpStatus.OK)) {
					merchant = merchantSbf.getBody().getData();

					if (!merchant.getPhoneNumer().equals(u.getPhoneNumber())) {
						return new ResponseEntity<>(
								new Message<>("MERCHANT_CODE_NOT_FOUNT", "Merchant code not found", null),
								HttpStatus.BAD_REQUEST);
					}
					user.setEmail(merchant.getEmail());
					user.setFirstname(merchant.getMerchantName());
				}

			}

			user.setUsername(u.getUsername());
			user.setGender(u.getGender());
			user.setDateOfBirth(u.getDateOfBirth());
			user.setPhoneNumber(u.getPhoneNumber());
			user.setRegistedId(u.getRegisteredId());

			String password = u.getPassword();
			user.setPassword(passwordEncoder.encode(password));
			user.setPasswordNeverExpires(true);

			user.setAccountNonExpired(true);
			user.setCredentialsNonExpired(true);
			user.setCredentialsNonExpired(true);

			user.setEnabled(false);

			user.setDeleted(false);

			List<Role> roles = new ArrayList<>();
			for (Long r : u.getRoles()) {
				roles.add(roleRepo.findById(r).orElse(null));
			}
			user.setRoles(roles);

			user = userRepository.save(user);

			publisher.publishEvent(new RegistrationCompleteEvent(user));

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "User is created", user), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTER_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@Override
	public void saveVerificationTokenForUser(String token, User user) {
		VerificationToken verificationToken = new VerificationToken(user, token);

		verificationTokenRepository.save(verificationToken);
	}

	@Override
	public String validateVerificationToken(String token) {
		VerificationToken verificationToken = verificationTokenRepository.findByToken(token);

		if (verificationToken == null) {
			return "invalid";
		}

		User user = verificationToken.getUser();
		Calendar cal = Calendar.getInstance();

		if ((verificationToken.getExpirationTime().getTime() - cal.getTime().getTime()) <= 0) {
			verificationTokenRepository.delete(verificationToken);
			return "expired";
		}

		user.setEnabled(true);
		userRepository.save(user);
		return "valid";
	}

	@Override
	public VerificationToken generateNewVerificationToken(String oldToken) {
		VerificationToken verificationToken = verificationTokenRepository.findByToken(oldToken);
		verificationToken.setToken(UUID.randomUUID().toString());
		verificationTokenRepository.save(verificationToken);
		return verificationToken;
	}

	@Override
	public User findUserByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	@Override
	public void createPasswordResetTokenForUser(User user, String token) {
		PasswordResetToken passwordResetToken = new PasswordResetToken(user, token);
		passwordResetTokenRepository.save(passwordResetToken);
	}

	@Override
	public String validatePasswordResetToken(String token) {
		PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token);

		if (passwordResetToken == null) {
			return "invalid";
		}

//        User user = passwordResetToken.getUser();
		Calendar cal = Calendar.getInstance();

		if ((passwordResetToken.getExpirationTime().getTime() - cal.getTime().getTime()) <= 0) {
			passwordResetTokenRepository.delete(passwordResetToken);
			return "expired";
		}

		return "valid";
	}

	@Override
	public Optional<User> getUserByPasswordResetToken(String token) {
		return Optional.ofNullable(passwordResetTokenRepository.findByToken(token).getUser());
	}

	@Override
	public void changePassword(User user, String newPassword) {
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}

	@Override
	public boolean checkIfValidOldPassword(User user, String oldPassword) {
		return passwordEncoder.matches(oldPassword, user.getPassword());
	}

	@Override
	public User update(Integer userId, User user) {
		return userRepository.save(user);

	}

	@Override
	public User findUserByUsername(String username) {

		return userRepository.findByUsername(username);
	}

	@Override
	public List<User> retrieveAllUsers() {

		return userRepository.findAll();
	}

	@Override
	public Optional<User> retrieveUser(Integer userId) {

		return userRepository.findById(userId);
	}

	@Override
	public boolean isUsernameExist(String username) {
		int c = userRepository.countByUsername(username);

		if (c > 0)
			return true;

		return false;
	}

	@Override
	public long deleteUser(String username) {

		return userRepository.deleteByUsername(username);
	}

	@Override
	public AuthenticationResponse authenticate(AuthenticationRequest request) {

		// G7: a pre-login PDL account request gates sign-in until the LPO
		// decision approves it (OTP verification alone re-enables the user,
		// so the request status is the authoritative gate).
		pdlAccountRequestRepository.findByUser_Username(request.getUsername()).ifPresent(r -> {
			switch (r.getStatus()) {
			case PENDING -> throw new org.springframework.security.authentication.DisabledException("ACCOUNT_PENDING");
			case REJECTED -> throw new org.springframework.security.authentication.DisabledException("ACCOUNT_REJECTED");
			default -> { /* APPROVED — proceed */ }
			}
		});

		authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

		var user = findUserByUsername(request.getUsername());

		var jwtToken = jwtService.generateToken(user);
		return AuthenticationResponse.builder().token(jwtToken).build();
	}

	@Override
	public String validateOTP(int otp) {

		try {
			OneTimePassword oneTimePassword = otpRepo.findByOneTimePasswordCode(otp).orElse(null);

			System.out.println("OTP: " + oneTimePassword);

			if (oneTimePassword == null) {
				return "invalid";
			}

			User user = oneTimePassword.getUser();
			Calendar cal = Calendar.getInstance();

			if ((oneTimePassword.getExpires().getTime() - cal.getTime().getTime()) <= 0) {
				otpRepo.delete(oneTimePassword);
				return "expired";
			}

			user.setEnabled(true);
			userRepository.save(user);
			// otpRepo.delete(oneTimePassword);
			return "valid";
		} catch (Exception e) {
			e.printStackTrace();
			return "invalid";
		}

	}

	@Override
	public ResponseEntity<Message<User>> createUser(UserModel u) {
		ResponseEntity<Message<User>> resp = null;

		try {

			User user = new User();

			user.setId(u.getId());
			user.setEmail(u.getEmail());
			user.setFirstname(u.getFirstName());
			user.setLastname(u.getLastName());

			user.setUsername(u.getUsername());
			user.setGender(u.getGender());
			user.setDateOfBirth(u.getDateOfBirth());
			user.setPhoneNumber(u.getPhoneNumber());
			user.setRegistedId(u.getUsername());

			String password = u.getPassword();
			user.setPassword(passwordEncoder.encode(password));
			user.setPasswordNeverExpires(true);

			user.setAccountNonExpired(true);
			user.setCredentialsNonExpired(true);
			user.setCredentialsNonExpired(true);

			user.setEnabled(u.isEnabled());

			user.setDeleted(false);

			List<Role> roles = new ArrayList<>();
			for (Long r : u.getRoles()) {
				roles.add(roleRepo.findById(r).orElse(null));
			}
			user.setRoles(roles);

			user = userRepository.save(user);

			// publisher.publishEvent(new RegistrationCompleteEvent(user));

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "User is created", user), HttpStatus.OK);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTER_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@Override
	public User getUserByPasswordResetOTP(int otp) {

		Optional<OneTimePassword> opt = otpRepo.findByOneTimePasswordCode(otp);

		if (opt.isPresent())
			return opt.get().getUser();

		return null;
	}

	@Override
	public User findUserByRegisteredId(String registeredId) {
		var user = userRepository.findByRegistedId(registeredId);
		if (user.isPresent())
			return user.get();

		return null;
	}

}
