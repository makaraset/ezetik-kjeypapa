package com.ezetik.kjeypapa.security.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.RoleUpdateFields;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.model.UserUpdateFields;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.service.RoleService;

/**
 * What Swagger says about the two older update calls, held to what they do.
 *
 * <p>Both handlers take a map and write each key onto a record by reflection.
 * {@link UserUpdateFields} and {@link RoleUpdateFields} exist only so Swagger
 * shows field names and not {@code additionalProp1..3}; nothing binds to them.
 * Without these tests a renamed field would leave Swagger naming one the
 * handler cannot write.
 */
class UpdateFieldsDocTest {

	@Test
	@DisplayName("every user field Swagger names is a text field of the user record")
	void everyNamedUserFieldIsATextFieldOfUser() {
		for (RecordComponent named : UserUpdateFields.class.getRecordComponents()) {
			Field field = ReflectionUtils.findField(User.class, named.getName());

			assertThat(field).as("User.%s", named.getName()).isNotNull();
			// The handler stores the JSON value as it arrives, so only a text
			// field takes it: a date or an enum field refuses a string.
			assertThat(field.getType()).as("User.%s", named.getName()).isEqualTo(String.class);
			assertThat(named.getType()).as("UserUpdateFields.%s", named.getName()).isEqualTo(String.class);
		}
	}

	@Test
	@DisplayName("the user call writes the fields it is sent and leaves the rest")
	void theUserCallWritesWhatItIsSent() {
		User user = new User();
		user.setFirstname("Sok");
		user.setLastname("Dara");
		user.setEmail("old@example.com");
		UserRepository users = mock(UserRepository.class);
		when(users.findById(7)).thenReturn(Optional.of(user));
		when(users.saveAndFlush(user)).thenReturn(user);
		UsersApiResource api = new UsersApiResource();
		ReflectionTestUtils.setField(api, "userRepo", users);

		Map<Object, Object> body = new LinkedHashMap<>();
		for (RecordComponent named : UserUpdateFields.class.getRecordComponents())
			if (!named.getName().equals("lastname"))
				body.put(named.getName(), "new " + named.getName());
		ResponseEntity<?> answer = api.updateInfo(7, body);

		assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(user.getFirstname()).isEqualTo("new firstname");
		assertThat(user.getEmail()).isEqualTo("new email");
		assertThat(user.getPhoneNumber()).isEqualTo("new phoneNumber");
		assertThat(user.getDateOfBirth()).isEqualTo("new dateOfBirth");
		assertThat(user.getLastname()).as("not sent, so not changed").isEqualTo("Dara");
		verify(users).saveAndFlush(user);
	}

	@Test
	@DisplayName("the user call's empty sample changes nothing")
	void theEmptySampleChangesNoUserField() {
		User user = new User();
		user.setFirstname("Sok");
		user.setEmail("old@example.com");
		UserRepository users = mock(UserRepository.class);
		when(users.findById(7)).thenReturn(Optional.of(user));
		when(users.saveAndFlush(user)).thenReturn(user);
		UsersApiResource api = new UsersApiResource();
		ReflectionTestUtils.setField(api, "userRepo", users);

		ResponseEntity<?> answer = api.updateInfo(7, new LinkedHashMap<>());

		assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(user.getFirstname()).isEqualTo("Sok");
		assertThat(user.getEmail()).isEqualTo("old@example.com");
	}

	@Test
	@DisplayName("every role field Swagger names is a field of the role")
	void everyNamedRoleFieldIsAFieldOfRole() {
		for (RecordComponent named : RoleUpdateFields.class.getRecordComponents()) {
			Field field = ReflectionUtils.findField(Role.class, named.getName());

			assertThat(field).as("Role.%s", named.getName()).isNotNull();
			assertThat(field.getType()).as("Role.%s", named.getName()).isEqualTo(named.getType());
		}
	}

	@Test
	@DisplayName("the role call refuses any field today (F32)")
	void theRoleCallRefusesAnyFieldToday() {
		// Swagger says so in the call's description. The handler looks each
		// name up on User, not Role. When that is fixed this test fails:
		// delete it, and take the warning out of RolesApiResource.
		Role role = new Role();
		role.setName("ROLE_TELLER");
		RoleService roles = mock(RoleService.class);
		when(roles.retrieveOne(3L)).thenReturn(Optional.of(role));
		RolesApiResource api = new RolesApiResource();
		api.roleService = roles;

		for (RecordComponent named : RoleUpdateFields.class.getRecordComponents()) {
			Map<Object, Object> body = new LinkedHashMap<>();
			body.put(named.getName(), named.getType() == Boolean.class ? Boolean.TRUE : "changed");

			ResponseEntity<?> answer = api.update(3L, body);

			assertThat(answer.getStatusCode()).as(named.getName()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		assertThat(role.getName()).isEqualTo("ROLE_TELLER");
		verify(roles, never()).updateRole(anyLong(), any());
	}

	@Test
	@DisplayName("the role call's empty sample changes nothing and is accepted")
	void theEmptySampleChangesNoRoleField() {
		Role role = new Role();
		role.setName("ROLE_TELLER");
		RoleService roles = mock(RoleService.class);
		when(roles.retrieveOne(3L)).thenReturn(Optional.of(role));
		when(roles.updateRole(3L, role)).thenReturn(role);
		RolesApiResource api = new RolesApiResource();
		api.roleService = roles;

		ResponseEntity<?> answer = api.update(3L, new LinkedHashMap<>());

		assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(role.getName()).isEqualTo("ROLE_TELLER");
	}
}
