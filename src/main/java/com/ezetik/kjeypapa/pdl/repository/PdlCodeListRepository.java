package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ezetik.kjeypapa.pdl.model.PdlCodeList;

public interface PdlCodeListRepository extends JpaRepository<PdlCodeList, Integer> {

	List<PdlCodeList> findByListNameOrderByNameEnAsc(String listName);

	PdlCodeList findByListNameAndCode(String listName, String code);
}
