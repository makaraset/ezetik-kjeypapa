package com.ezetik.kjeypapa.pdl.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerification;
import com.ezetik.kjeypapa.pdl.model.PdlIdentityVerificationStatusEnum;

public interface PdlIdentityVerificationRepository extends JpaRepository<PdlIdentityVerification, Integer> {

	Optional<PdlIdentityVerification> findByChallengeHash(String challengeHash);

	List<PdlIdentityVerification> findByAccountRequestIdOrderByIdDesc(Integer accountRequestId);

	List<PdlIdentityVerification> findByAccountRequestIdAndStatus(Integer accountRequestId,
			PdlIdentityVerificationStatusEnum status);

	boolean existsByAccountRequestIdAndStatusAndMock(Integer accountRequestId,
			PdlIdentityVerificationStatusEnum status, boolean mock);

	/**
	 * Attempts since {@code since} that may have reached CamDigi, and so may
	 * have been charged. One that never left us — no connection, no token, no
	 * doneBy — costs nothing, and must not use up the customer's attempts.
	 */
	@Query("select count(v) from PdlIdentityVerification v where v.accountRequestId = :id"
			+ " and v.submittedAt > :since"
			+ " and (v.errorKind is null or v.errorKind not in ('UNREACHABLE', 'NOT_CONFIGURED', 'NOT_ELIGIBLE'))")
	long countChargeableSince(@Param("id") Integer accountRequestId, @Param("since") Instant since);

	/**
	 * Moves a challenge to IN_FLIGHT only if it is still CHALLENGED. Of two
	 * concurrent submits of the same challenge, exactly one gets 1 back.
	 */
	@Modifying
	@Transactional
	@Query("update PdlIdentityVerification v set v.status = :to, v.submittedAt = :now"
			+ " where v.id = :id and v.status = :from")
	int claim(@Param("id") Integer id, @Param("from") PdlIdentityVerificationStatusEnum from,
			@Param("to") PdlIdentityVerificationStatusEnum to, @Param("now") Instant now);
}
