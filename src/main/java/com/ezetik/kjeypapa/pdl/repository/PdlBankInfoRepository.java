package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;

@Repository
public interface PdlBankInfoRepository extends JpaRepository<PdlBankInfo, Integer> {

	@Query("select b from PdlBankInfo b where b.user.id = :userId order by b.id desc")
	List<PdlBankInfo> findByUser(int userId);
}
