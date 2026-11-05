package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;

@Repository
public interface PdlEmploymentInfoRepository extends JpaRepository<PdlEmploymentInfo, Integer> {

	@Query("select e from PdlEmploymentInfo e where e.user.id = :userId order by e.id desc")
	List<PdlEmploymentInfo> findByUser(int userId);
}
