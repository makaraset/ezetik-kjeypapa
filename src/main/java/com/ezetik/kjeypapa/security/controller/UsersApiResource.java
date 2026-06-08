package com.ezetik.kjeypapa.security.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.payload.ImageResponse;
import com.ezetik.kjeypapa.image.service.ImageService;
import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.model.UserModel;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;

@RestController

@RequestMapping("/api/v1/user")
@Tag(name = "02- User and security API", description = "User management")
public class UsersApiResource {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private ImageService imageService;

	private FileNameHelper fileHelper = new FileNameHelper();

	@PostMapping("/create")
	public ResponseEntity<Message<User>> createUserUser(@RequestBody UserModel userModel) {

		return userService.createUser(userModel);
	}

	@GetMapping("")
	@PreAuthorize("hasRole('ADMIN')")
//	@PreAuthorize("hasPermission('','READ_ALL_USER')")
//	@ApiOperation(value = "Get all users")
	public ResponseEntity<?> findAll() {

		ResponseEntity<?> resp = null;
		try {
			List<User> list = userService.retrieveAllUsers();
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<List<User>>(list, HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to feth data.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@GetMapping("/{id}")
//	@PreAuthorize("hasPermission('','READ_ALL_USER') or hasAuthority('SCOPE_api.security')")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> findById(@PathVariable("id") int id) {

		ResponseEntity<?> resp = null;
		try {
			Optional<User> user = userRepo.findById(id);
			if (user != null) {
				resp = new ResponseEntity<Optional<User>>(user, HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to feth data.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@GetMapping("exist/{username}")
//	@PreAuthorize("hasPermission('','READ_USER')")
	@PreAuthorize("hasRole('ADMIN')")
	public boolean isUsernameExist(@PathVariable("username") String username) {

		return userService.isUsernameExist(username);
	}

	@DeleteMapping("/{username}")
//	@PreAuthorize("hasPermission('','DELETE_USER')")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> delete(@PathVariable("username") String username) {

		ResponseEntity<?> resp = null;
		try {
			long data = userService.deleteUser(username);
			if (data > 0) {
				resp = new ResponseEntity<String>("User " + username + " Successful deleted.", HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@PatchMapping("/update/{id}")
	@PreAuthorize("hasRole('ADMIN')")
//	@PreAuthorize("hasPermission('','UPDATE_USER') or #username == authentication.principal")
	public ResponseEntity<?> updateInfo(@PathVariable("id") int id, @RequestBody Map<Object, Object> fields) {

		ResponseEntity<?> resp = null;

		try {

			Optional<User> user = userRepo.findById(id);

			if (user.isPresent()) {
				fields.forEach((key, value) -> {
					Field field = ReflectionUtils.findField(User.class, (String) key);
					field.setAccessible(true);
					ReflectionUtils.setField(field, user.get(), value);
				});

				User data = userRepo.saveAndFlush(user.get());
				resp = new ResponseEntity<Message<User>>(new Message<User>("SUCCESS", id + "-Updated", data),
						HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.NOT_FOUND);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<Message<User>>(new Message<>("FAIL", "Unable to save data.", null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@PutMapping("/role/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<User> updateRoles(@PathVariable("id") int id, @RequestBody List<Role> roles) {
		Optional<User> existingItemOptional = userRepo.findById(id);
		if (existingItemOptional.isPresent()) {
			User existingItem = existingItemOptional.get();
			existingItem.setRoles(roles);
			return new ResponseEntity<>(userRepo.save(existingItem), HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PostMapping(path = "/profile-photo", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@Transactional
	public ResponseEntity<Message<ImageResponse>> udateProfilePhoto(
			@RequestPart(name = "file", required = true) MultipartFile file) {

		ResponseEntity<Message<ImageResponse>> resp = null;
		try {
			String user = SecurityContextHolder.getContext().getAuthentication().getName();

			Image image = Image.buildImage(file, fileHelper);
			image.setEntityClass("PROFILE_PHOTO");
			image.setEntityId(user);

			imageService.save(image);
			userRepo.updateProfilePhoto(image.getFileName(), user);

			resp = new ResponseEntity<>(new Message<>("SUCCESS", "Profile is saved", new ImageResponse(image)),
					HttpStatus.OK);
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("FAIL", "Unable to save data.", null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@PostMapping(path = "/fcm-token")
	@Transactional
	public ResponseEntity<Message<String>> setFCMToken(String token) {

		ResponseEntity<Message<String>> resp = null;
		try {
			String user = SecurityContextHolder.getContext().getAuthentication().getName();

			int updated = userRepo.setFCMToken(token, user);

			if (updated > 0) {
				resp = new ResponseEntity<>(new Message<>("SUCCESS", "Token is save", ""), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<>(new Message<>("NOT_FOUND", "No user found", ""),
						HttpStatus.EXPECTATION_FAILED);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("FAIL", "Unable to save data.", null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

}
