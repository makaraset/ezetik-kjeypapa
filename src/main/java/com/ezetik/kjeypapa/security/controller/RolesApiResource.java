package com.ezetik.kjeypapa.security.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.RoleService;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/security/role")
@Tag(name = "02- User and security API", description = "User management")
@PreAuthorize("hasRole('ADMIN')")
public class RolesApiResource {

	@Autowired
	RoleService roleService;

	@GetMapping("")
//	@PreAuthorize("hasPermission('','READ_ROLE')")
	public ResponseEntity<?> findAll() {

		ResponseEntity<?> resp = null;
		try {
			List<Role> list = roleService.retrieveAll();
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<List<Role>>(list, HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to feth data.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@GetMapping("/active")
//	@PreAuthorize("hasPermission('','READ_ROLE')")
	public ResponseEntity<?> findActiveRoles() {

		ResponseEntity<?> resp = null;
		try {
			List<Role> list = roleService.retrieveAllActiveRoles();
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<List<Role>>(list, HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to feth data.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@GetMapping("/selfservice")
//	@PreAuthorize("hasPermission('','READ_ROLE')")
	public ResponseEntity<?> findSelfServiceRoles() {

		ResponseEntity<?> resp = null;
		try {
			List<Role> list = roleService.retrieveAllSelfServiceRoles();
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<List<Role>>(list, HttpStatus.OK);
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
//	@PreAuthorize("hasPermission('','READ_ROLE')")
	public ResponseEntity<?> findById(@PathVariable("id") long id) {

		ResponseEntity<?> resp = null;
		try {
			Optional<Role> list = roleService.retrieveOne(id);
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<Optional<Role>>(list, HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to feth data.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@PostMapping("")
//	@PreAuthorize("hasPermission('','CREATE_ROLE')")
	public ResponseEntity<?> createRole(@RequestBody Role role) {

		ResponseEntity<?> resp = null;
		try {
			Role data = roleService.createRole(role);
			resp = new ResponseEntity<Message<Role>>(new Message<Role>("SUCCESS", role.getName() + "-Saved", data),
					HttpStatus.OK);
		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to create role.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@PatchMapping("/{id}")
//	@PreAuthorize("hasPermission('','UPDATE_ROLE')")
	public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody Map<Object, Object> fields) {

		ResponseEntity<?> resp = null;

		try {

			Optional<Role> role = roleService.retrieveOne(id);

			if (role.isPresent()) {
				fields.forEach((key, value) -> {
					Field field = ReflectionUtils.findField(User.class, (String) key);
					field.setAccessible(true);
					ReflectionUtils.setField(field, role.get(), value);
				});

				Role data = roleService.updateRole(id, role.get());
				resp = new ResponseEntity<Message<Role>>(new Message<Role>("SUCCESS", id + "-Updated", data),
						HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("No data found!", HttpStatus.NOT_FOUND);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<Message<Role>>(new Message<>("FAIL", "Unable to update role.", null),
					HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}

		return resp;
	}

	@DeleteMapping("/{id}")
//	@PreAuthorize("hasPermission('','DELETE_ROLE')")
	public ResponseEntity<?> deleteRole(@PathVariable("id") long id) {

		ResponseEntity<?> resp = null;
		try {
			Optional<Role> role = roleService.retrieveOne(id);
			roleService.deleteRole(id);
			resp = new ResponseEntity<Message<Role>>(new Message<Role>("SUCCESS", id + "-Deleted", role.get()),
					HttpStatus.OK);
		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to create role.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@PutMapping("/disable/{id}")
//	@PreAuthorize("hasPermission('','DISABLE_ROLE')")
	public ResponseEntity<?> disableRole(@PathVariable("id") long id) {

		ResponseEntity<?> resp = null;
		try {
			long data = roleService.disableRole(id);
			Optional<Role> role = roleService.retrieveOne(id);
			if (data > 0) {
				resp = new ResponseEntity<Message<Role>>(new Message<Role>("SUCCESS", id + "-Disabled", role.get()),
						HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("Role not found.", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to disable role.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@PutMapping("/enable/{id}")
//	@PreAuthorize("hasPermission('','ENABLE_ROLE')")
	public ResponseEntity<?> enableRole(@PathVariable("id") long id) {

		ResponseEntity<?> resp = null;
		try {
			long data = roleService.enableRole(id);
			Optional<Role> role = roleService.retrieveOne(id);
			if (data > 0) {
				resp = new ResponseEntity<Message<Role>>(new Message<Role>("SUCCESS", id + "-Enabled", role.get()),
						HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("Role not found.", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to disable role.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}
}
