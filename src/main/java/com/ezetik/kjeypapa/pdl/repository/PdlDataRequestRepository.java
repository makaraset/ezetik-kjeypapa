package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlDataRequest;
import com.ezetik.kjeypapa.pdl.model.PdlDataRequestStatusEnum;

@Repository
public interface PdlDataRequestRepository extends JpaRepository<PdlDataRequest, Integer> {

	/** A customer's own requests, newest first. */
	List<PdlDataRequest> findByUser_IdOrderByIdDesc(Integer userId);

	/** The operator queue. */
	List<PdlDataRequest> findByStatusOrderByIdAsc(PdlDataRequestStatusEnum status);

	List<PdlDataRequest> findAllByOrderByIdDesc();

	/** Open requests of one type for one customer — see the duplicate guard in the service. */
	List<PdlDataRequest> findByUser_IdAndStatusIn(Integer userId, List<PdlDataRequestStatusEnum> statuses);
}
