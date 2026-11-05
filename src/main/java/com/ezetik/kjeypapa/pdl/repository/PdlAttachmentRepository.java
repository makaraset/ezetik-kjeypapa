package com.ezetik.kjeypapa.pdl.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlAttachment;
import com.ezetik.kjeypapa.pdl.model.PdlDocTypeEnum;

@Repository
public interface PdlAttachmentRepository extends JpaRepository<PdlAttachment, Integer> {

	@Query("select a from PdlAttachment a where a.pdl.id = :pdlId and a.docType = :docType and a.isDeleted = false")
	Optional<PdlAttachment> findActive(int pdlId, PdlDocTypeEnum docType);

	@Query("select count(a) from PdlAttachment a where a.pdl.id = :pdlId and a.docType = :docType and a.isDeleted = false")
	int countActive(int pdlId, PdlDocTypeEnum docType);
}
