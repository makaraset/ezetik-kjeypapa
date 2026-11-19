package com.ezetik.kjeypapa.security.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What Swagger shows for the body of {@code PATCH /api/v1/user/update/{id}}.
 *
 * <p>Documentation only: nothing binds to this type. The handler takes a map
 * and writes each key, by name, onto the user record. This names the plain
 * details a caller is meant to send, so Swagger shows them and not
 * {@code additionalProp1..3}. Each one must stay a text field of {@link User}
 * that the handler can write; {@code UpdateFieldsDocTest} holds it to that.
 *
 * <p>The example is empty on purpose. The call changes whatever it is sent,
 * so a sample with values in it, sent as it stands, would overwrite a real
 * user's details.
 */
@Schema(description = "The details to change. Only the fields sent are changed.", example = "{}")
public record UserUpdateFields(

		@Schema(description = "First name.")
		String firstname,

		@Schema(description = "Last name.")
		String lastname,

		@Schema(description = "Email address.")
		String email,

		@Schema(description = "Phone number.")
		String phoneNumber,

		@Schema(description = "Date of birth, as text.")
		String dateOfBirth) {
}
