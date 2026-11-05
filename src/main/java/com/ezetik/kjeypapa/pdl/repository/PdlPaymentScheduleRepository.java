package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlPaymentSchedule;

@Repository
public interface PdlPaymentScheduleRepository extends JpaRepository<PdlPaymentSchedule, Integer> {

	@Query("select s from PdlPaymentSchedule s where s.pdl.id = :pdlId order by s.installmentNo asc")
	List<PdlPaymentSchedule> findSchedule(int pdlId);
}
