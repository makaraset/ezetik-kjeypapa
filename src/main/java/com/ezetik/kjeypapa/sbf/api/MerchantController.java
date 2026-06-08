package com.ezetik.kjeypapa.sbf.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.sbf.model.Merchant;
import com.ezetik.kjeypapa.sbf.service.SBFApiService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/merchant")
@Tag(name = "04- Merchant management API", description = "Merchant management controller")
public class MerchantController {

	@Autowired
	private SBFApiService apiService;

	@Autowired
	private UserService userService;

	@GetMapping("")
	public ResponseEntity<Message<List<Merchant>>> findMyAllMerchant() {

		ResponseEntity<Message<List<Merchant>>> resp = null;

		try {

			resp = apiService.getMyMerchant(Long.valueOf(getCurrentUser().getRegistedId()));

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	User getCurrentUser() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		String userPrincipal = authentication.getName();
		return userService.findUserByUsername(userPrincipal);

	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','USER')")
	public ResponseEntity<Message<Merchant>> findByID(@PathVariable("id") int id) {

		ResponseEntity<Message<Merchant>> resp = null;

		try {

			resp = apiService.getMerchantById(id);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@GetMapping("/by-code/{code}")
	@PreAuthorize("hasAnyRole('ADMIN','USER')")
	public ResponseEntity<Message<Merchant>> findByCode(@PathVariable("code") String code) {

		ResponseEntity<Message<Merchant>> resp = null;

		try {

			resp = apiService.getMerchantByCode(code);

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

	@GetMapping("/all")
	@PreAuthorize("hasAnyRole('ADMIN','USER')")
	public ResponseEntity<Message<List<Merchant>>> findAllMerchant() {

		ResponseEntity<Message<List<Merchant>>> resp = null;

		try {

			resp = apiService.getAllMerchant();

		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;

	}

}
