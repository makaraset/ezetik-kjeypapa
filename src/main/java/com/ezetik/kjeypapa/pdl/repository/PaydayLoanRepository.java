package com.ezetik.kjeypapa.pdl.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.PdlTransaction;

@Repository
public interface PaydayLoanRepository extends JpaRepository<PaydayLoan, Integer> {

	@Query("select p from PaydayLoan p order by p.id desc")
	List<PaydayLoan> findAll();

	List<PaydayLoan> findByUserId(int userId);

	List<PaydayLoan> findByStatus(PdlStatusEnum status);

	@Query("select p from PaydayLoan p where p.status in (:statuses)")
	List<PaydayLoan> findByStatusIn(List<PdlStatusEnum> statuses);

	Optional<PaydayLoan> findByLosApplicationNo(String losApplicationNo);

	Optional<PaydayLoan> findByLoanRefNo(String loanRefNo);

	@Query("select new com.ezetik.kjeypapa.pdl.payload.PdlTransaction(p) from PaydayLoan p where p.user.id = :userId order by p.id desc")
	List<PdlTransaction> findTransactionByUserId(int userId);
}
