package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;

@Repository
public interface PdlPersonalInfoRepository extends JpaRepository<PdlPersonalInfo, Integer> {

	@Query("select p from PdlPersonalInfo p where p.user.id = :userId order by p.id desc")
	List<PdlPersonalInfo> findByUser(int userId);

	/** Every row carrying this ID number — used to refuse a second identity. */
	List<PdlPersonalInfo> findByIdNo(String idNo);
}
