package com.ezetik.kjeypapa.sbf.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ezetik.kjeypapa.sbf.model.Customer;
import com.ezetik.kjeypapa.sbf.model.Note;
import com.ezetik.kjeypapa.sbf.model.NoteStatusEnum;
import com.ezetik.kjeypapa.sbf.payload.NoteTransaction;

@Repository
public interface NoteRepository extends JpaRepository<Note, Integer> {

	@Query("select n from Note n order by n.id desc")
	List<Note> findAll();

	List<Note> findByUserId(int userId);

	List<Note> findByStatus(NoteStatusEnum status);

	@Query("select n from Note n where n.status in (:statuses)")
	List<Note> findByStatusIn(List<NoteStatusEnum> statuses);

//	@Query("select n from Note n where n.status = :status and n.isSignedNoteCorrect=false")
//	List<Note> findByStatusAndNoteToReviewSigned(NoteStatusEnum status);

	@Query("select new com.ezetik.kjeypapa.sbf.model.Customer(n) from Note n where n.status in('Pending_Delivery','Pending_Confirmation','Disbursed') and n.merchantCode=:merchantCode")
	List<Customer> findByMerchantCode(String merchantCode);

	@Query("select new com.ezetik.kjeypapa.sbf.model.Customer(n) from Note n where n.id=:noteId")
	Optional<Customer> findCustByNoteId(int noteId);

	@Query("select new com.ezetik.kjeypapa.sbf.payload.NoteTransaction(n) from Note n where n.user.id=:userId")
	List<NoteTransaction> findTransactionByUserId(int userId);

	@Query("select count(n) from Note n where n.merchantCode=:merchantCode and n.status=:status")
	int countNoteByMerchantCode(String merchantCode, NoteStatusEnum status);

	@Query("select n from Note n where n.status in(:status) and n.merchantCode=:merchantCode")
	List<Note> findByMerchantCodeAndStatus(String merchantCode, List<NoteStatusEnum> status);

}
