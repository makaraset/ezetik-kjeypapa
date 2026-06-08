package com.ezetik.kjeypapa.sbf.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.ezetik.kjeypapa.sbf.model.ConsolidateData;
import com.ezetik.kjeypapa.sbf.model.DisburseMessage;
import com.ezetik.kjeypapa.sbf.model.DisburseModel;
import com.ezetik.kjeypapa.sbf.model.Holiday;
import com.ezetik.kjeypapa.sbf.model.LoanFacility;
import com.ezetik.kjeypapa.sbf.model.Merchant;
import com.ezetik.kjeypapa.security.util.Message;

public interface SBFApiService {

	public ConsolidateData getFacilityByCIF(Long cif);

	public ResponseEntity<Message<ConsolidateData>> getAccountByCif(Long cif);

	public LoanFacility createLoanFac(LoanFacility loanFac);

	public DisburseMessage autoDisburse(DisburseModel disburseModel);

	public ResponseEntity<Message<List<Merchant>>> getMyMerchant(Long cif);

	public ResponseEntity<Message<Merchant>> getMerchantById(int id);

	public ResponseEntity<Message<List<Merchant>>> getAllMerchant();

	public ResponseEntity<Message<Merchant>> getMerchantByCode(String code);

	public ResponseEntity<List<Holiday>> getHolidays();

}
