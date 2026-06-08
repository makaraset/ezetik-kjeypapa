package com.ezetik.kjeypapa.security.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.repository.RoleRepository;

@Service
public class RoleServiceImpl implements RoleService {

	@Autowired
	RoleRepository roleRepo;

	@Override
	public List<Role> retrieveAll() {

		return roleRepo.findAll();
	}

	@Override
	public List<Role> retrieveAllActiveRoles() {

		return roleRepo.findByDisabled(false);
	}

	@Override
	public List<Role> retrieveAllSelfServiceRoles() {

		return roleRepo.findByName("SELF_SERVICE_USER_ROLE");
	}

	@Override
	public Optional<Role> retrieveOne(Long roleId) {

		return roleRepo.findById(roleId);
	}

	@Override
	public Role createRole(Role role) {

		return roleRepo.save(role);
	}

	@Override
	public Role updateRole(Long id, Role role) {

		if (roleRepo.countById(id) > 0) {
			return roleRepo.save(role);
		}

		return null;
	}

	@Override
	public void deleteRole(Long id) {

		roleRepo.deleteById(id);
	}

	@Override
	public long disableRole(long id) {

		return roleRepo.disableRole(id);
	}

	@Override
	public long enableRole(long id) {

		return roleRepo.enableRole(id);
	}

}
