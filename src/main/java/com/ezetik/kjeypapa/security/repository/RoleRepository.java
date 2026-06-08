package com.ezetik.kjeypapa.security.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.security.model.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

	@Query("SELECT COUNT(a) FROM User a JOIN a.roles r WHERE r.id = :roleId AND a.deleted = false")
	Integer getCountOfRolesAssociatedWithUsers(@Param("roleId") Long roleId);

	@Query("SELECT role FROM Role role WHERE LOWER(role.name) = LOWER(:name)")
	Role getRoleByName(@Param("name") String name);

	List<Role> findByDisabled(boolean disabled);

	List<Role> findByName(String name);

	long countById(long id);

	long deleteByName(String name);

	@Modifying
	@Query("update Role r set r.disabled=true where r.id=:id")
	long disableRole(long id);

	@Modifying
	@Query("update Role r set r.disabled=false where r.id=:id")
	long enableRole(long id);
}
