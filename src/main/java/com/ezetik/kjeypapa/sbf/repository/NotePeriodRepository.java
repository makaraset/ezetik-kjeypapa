package com.ezetik.kjeypapa.sbf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.sbf.model.NotePeriod;

@Repository
public interface NotePeriodRepository extends JpaRepository<NotePeriod, Integer> {

}
