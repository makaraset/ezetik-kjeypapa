package com.ezetik.kjeypapa.security.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.security.model.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

	@Query("SELECT p FROM Permission p WHERE LOWER(TRIM(BOTH FROM p.code)) = LOWER(TRIM(BOTH FROM ?1))")
	Permission findOneByCode(String code);

	List<Permission> findByCanMakerChecker(boolean canMakerChecker);

}
