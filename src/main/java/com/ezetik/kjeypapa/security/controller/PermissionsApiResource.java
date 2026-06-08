package com.ezetik.kjeypapa.security.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ezetik.kjeypapa.security.model.Permission;
import com.ezetik.kjeypapa.security.repository.PermissionRepository;
import com.ezetik.kjeypapa.security.util.Message;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/security/permission")
@Tag(name = "02- User and security API", description = "User management")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionsApiResource {

	@Autowired
	PermissionRepository perRepo;

	@GetMapping("")
//	@PreAuthorize("hasPermission('','READ_PERMISSION')")
	public ResponseEntity<?> findAll() {

		ResponseEntity<?> resp = null;
		try {
			List<Permission> list = perRepo.findAll();
			if (list != null && !list.isEmpty()) {
				resp = new ResponseEntity<List<Permission>>(list, HttpStatus.OK);
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
//	@PreAuthorize("hasPermission('','READ_PERMISSION')")
	public ResponseEntity<?> findById(@PathVariable("id") Long id) {

		ResponseEntity<?> resp = null;
		try {
			Optional<Permission> data = perRepo.findById(id);
			if (data != null && !data.isEmpty()) {
				resp = new ResponseEntity<Optional<Permission>>(data, HttpStatus.OK);
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
//	@PreAuthorize("hasPermission('','CREATE_PERMISSION')")
	public ResponseEntity<?> createPermission(@RequestBody Permission permission) {

		ResponseEntity<?> resp = null;
		try {
			Permission data = perRepo.save(permission);
			resp = new ResponseEntity<Message<Permission>>(
					new Message<Permission>("SUCCESS", permission.getCode() + "-Saved", data), HttpStatus.OK);
		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to create permission.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@PutMapping("/{id}")
//	@PreAuthorize("hasPermission('','UPDATE_PERMISSION')")
	public ResponseEntity<?> updatePermission(@PathVariable("id") Long id, @RequestBody Permission permission) {

		ResponseEntity<?> resp = null;
		try {
			if (perRepo.existsById(id)) {
				Permission data = perRepo.save(permission);
				resp = new ResponseEntity<Message<Permission>>(
						new Message<Permission>("SUCCESS", permission.getCode() + "-Saved", data), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("Permission not found.", HttpStatus.OK);
			}

		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to update permission.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

	@DeleteMapping("/{id}")
//	@PreAuthorize("hasPermission('','DELETE_PERMISSION')")
	public ResponseEntity<?> deletePermission(@PathVariable("id") Long id) {

		ResponseEntity<?> resp = null;
		try {
			if (perRepo.existsById(id)) {
				Optional<Permission> p = perRepo.findById(id);
				perRepo.deleteById(id);
				resp = new ResponseEntity<Message<Permission>>(
						new Message<Permission>("SUCCESS", id + "-Deleted", p.get()), HttpStatus.OK);
			} else {
				resp = new ResponseEntity<String>("Permission not found.", HttpStatus.OK);
			}
		} catch (Exception e) {
			resp = new ResponseEntity<String>("Unable to delete permission.", HttpStatus.INTERNAL_SERVER_ERROR);
			e.printStackTrace();
		}
		return resp;
	}

}
