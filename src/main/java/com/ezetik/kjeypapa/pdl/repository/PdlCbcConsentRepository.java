package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PdlCbcConsent;

/**
 * Consent records. Reads freely; writes only through
 * {@code PdlCbcConsentLedger}, which is what keeps the chain intact.
 */
@Repository
public interface PdlCbcConsentRepository extends JpaRepository<PdlCbcConsent, Integer> {

	Optional<PdlCbcConsent> findByLoan_Id(int loanId);

	List<PdlCbcConsent> findByUser_IdOrderByIdDesc(int userId);

	/** In chain order — the order the records were appended. */
	List<PdlCbcConsent> findAllByOrderByIdAsc();

	boolean existsByCaptureSource(String captureSource);

	/**
	 * The tail of the chain, locked for the duration of the transaction, so two
	 * concurrent appends cannot read the same predecessor and fork it.
	 */
	@Query(value = "select * from pdl_cbc_consent order by id desc limit 1 for update", nativeQuery = true)
	Optional<PdlCbcConsent> lockTail();
}
