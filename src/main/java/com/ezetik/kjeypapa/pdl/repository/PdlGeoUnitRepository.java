package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ezetik.kjeypapa.pdl.model.PdlGeoUnit;

public interface PdlGeoUnitRepository extends JpaRepository<PdlGeoUnit, Integer> {

	List<PdlGeoUnit> findByLevelOrderByNameEnAsc(String level);

	List<PdlGeoUnit> findByLevelAndParentCodeOrderByNameEnAsc(String level, String parentCode);

	PdlGeoUnit findByLevelAndCode(String level, String code);

	long countByLevel(String level);
}
