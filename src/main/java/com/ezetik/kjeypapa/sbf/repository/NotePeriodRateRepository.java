package com.ezetik.kjeypapa.sbf.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.sbf.model.NotePeriodRate;

@Repository
public interface NotePeriodRateRepository extends JpaRepository<NotePeriodRate, Integer> {

	List<NotePeriodRate> findByNotePeriodIdOrderByIdAsc(int notePeriodId);

}
