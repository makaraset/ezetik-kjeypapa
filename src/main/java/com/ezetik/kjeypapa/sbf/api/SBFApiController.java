package com.ezetik.kjeypapa.sbf.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.sbf.model.ConsolidateData;
import com.ezetik.kjeypapa.sbf.model.Holiday;
import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.service.SBFApiService;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/sbf")
@Tag(name = "06- SBF API", description = "Tricube api controller")
public class SBFApiController {

	@Autowired
	private SBFApiService service;

	@Autowired
	private UserService userService;

	@GetMapping("/note/{cif}")
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Message<ConsolidateData>> getAccountByCif(@PathVariable("cif") Long cif) throws Exception {

		return service.getAccountByCif(cif);
	}

	@PostMapping("/note")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Message<LoanFacility>> createNote(@RequestBody LoanFacility loanFac) {

		ResponseEntity<Message<LoanFacility>> resp = null;

		try {
			resp = new ResponseEntity<>(
					new Message<>("SUCCESS", "Note is sumitted to CBS", service.createLoanFac(loanFac)), HttpStatus.OK);
		} catch (Exception e) {
			resp = new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return resp;
	}

	@GetMapping("/holiday")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<Holiday>> getHoliday() {

		return service.getHolidays();
	}

	@GetMapping("/note/my-account")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<Message<ConsolidateData>> getMyAccount() throws Exception {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return new ResponseEntity<>(new Message<>("FAILED", "Authentication failed", null),
					HttpStatus.UNAUTHORIZED);
		}

		String userPrincipal = authentication.getName();
		User user = userService.findUserByUsername(userPrincipal);

		return service.getAccountByCif(Long.valueOf(user.getRegistedId()));
	}
}
