package com.ezetik.kjeypapa.security.service;

import java.util.List;
import java.util.Optional;

import com.ezetik.kjeypapa.security.model.Role;

public interface RoleService {

	List<Role> retrieveAll();

	List<Role> retrieveAllActiveRoles();

	List<Role> retrieveAllSelfServiceRoles();

	Optional<Role> retrieveOne(Long roleId);

	Role createRole(Role role);

	Role updateRole(Long id, Role role);

	void deleteRole(Long id);

	long disableRole(long id);

	long enableRole(long id);

}
