package com.ezetik.kjeypapa.security.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What Swagger shows for the body of {@code PATCH /api/v1/security/role/{id}}.
 *
 * <p>Documentation only: nothing binds to this type. The handler takes a map
 * and writes each key, by name, onto the role. This names the fields of
 * {@link Role} the call was written for, so Swagger shows them and not
 * {@code additionalProp1..3}.
 *
 * <p>The call does not work today (backlog F32): it looks each name up on the
 * user record, not the role, so any field sent is refused. The example is
 * empty, which is the one body the call accepts.
 */
@Schema(description = "The fields to change. Only the fields sent are changed.", example = "{}")
public record RoleUpdateFields(

		@Schema(description = "The role's name.")
		String name,

		@Schema(description = "What the role is for.")
		String description,

		@Schema(description = "true switches the role off.")
		Boolean disabled) {
}
