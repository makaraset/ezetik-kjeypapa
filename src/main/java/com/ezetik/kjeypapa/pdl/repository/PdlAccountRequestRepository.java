package com.ezetik.kjeypapa.pdl.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;

public interface PdlAccountRequestRepository extends JpaRepository<PdlAccountRequest, Integer> {

	Optional<PdlAccountRequest> findByUser_Username(String username);

	java.util.List<PdlAccountRequest> findByStatusOrderByIdDesc(
			com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum status);

	java.util.List<PdlAccountRequest> findAllByOrderByIdDesc();
}
