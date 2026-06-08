package com.ezetik.kjeypapa.sbf.payload;

import com.ezetik.kjeypapa.sbf.model.Merchant;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MerchantResp {

//	private Integer id;
	private String merchantCode;
	private String merchantName;
	private String merchantKhName;
	private Long merchantCustAcctId;
	private String merchantSetlement;
	private String phoneNumer;
	private String email;
	private String bankName;
	private String accountNo;
	private String benefitciaryName;
	private int activeNote;
	private Double outstanding;

	public MerchantResp(Merchant m) {
		super();
		this.merchantCode = m.getMerchantCode();
		this.merchantName = m.getMerchantName();
		this.merchantKhName = m.getMerchantKhName();
		this.merchantSetlement = m.getMerchantSetlement();
		this.merchantCustAcctId = m.getMerchantCustAcctId();
		this.phoneNumer = m.getPhoneNumer();
		this.email = m.getEmail();
		this.bankName = m.getBankName();
		this.accountNo = m.getAccountNo();
		this.benefitciaryName = m.getBenefitciaryName();
		this.activeNote = 0;
		this.outstanding = 0.0;
	}

}
