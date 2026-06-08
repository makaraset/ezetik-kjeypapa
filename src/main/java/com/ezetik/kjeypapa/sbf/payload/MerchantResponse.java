package com.ezetik.kjeypapa.sbf.payload;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@Table(name = "sbf_merchant_view")
@Subselect("SELECT m.id,m.merchant_code, m.merchant_name, m.merchant_kh_name, m.phone_numer, m.email, m.bank_name, m.account_no, m.benefitciary_name, m.is_active, n.user_id, n.loan_fac_ref_no FROM sbf_merchant m inner join sbf_note n on m.id = n.merchant_id where n.is_approved=true")
public class MerchantResponse {

	@Id
	private Integer id;
	private String merchantCode;
	private String merchantName;
	private String merchantKhName;
	private String phoneNumer;
	private String email;
	private String bankName;
	private String accountNo;
	private String benefitciaryName;
	private boolean isActive;
	private int userId;
	private String loanFacRefNo;

}
