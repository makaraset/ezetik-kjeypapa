package com.ezetik.kjeypapa.pdl.payload.los;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * The 102-field body of SBF's {@code POST /new-loan-application}.
 *
 * <p>GENERATED from {@code docs/sbf_tricube_uat_api_docs.json} — every
 * {@code @JsonProperty} is the vendor's spelling verbatim, misspellings included
 * (see {@link LoanUtilizationProjectItem}). Do not "tidy" these names: they are
 * the wire contract, and LosApplicationMapperTest asserts the serialised key set
 * still equals the swagger's exactly.
 *
 * <p>Serialisation reads FIELDS ONLY. Lombok generates {@code getLR_CBCurrency()},
 * from which Jackson infers a second property {@code lr_CBCurrency}; with
 * getters visible, every {@code LR_}/{@code OF_}/{@code PC_} field went out
 * twice under two spellings — 119 keys instead of 102. The contract test pins
 * this.
 *
 * <p>Every value is initialised ({@code ""} / {@code 0} / empty list) and the
 * class is {@code JsonInclude.ALWAYS}, so all 102 keys are always present —
 * matching the vendor's own sample, which sends empty strings rather than
 * omitting optional fields.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
		isGetterVisibility = Visibility.NONE)
public class NewApplicationRequest {

	@JsonProperty("AgreedFirstDueDate")
	private String agreedFirstDueDate = "";

	@JsonProperty("CustP_Age")
	private int custP_Age = 0;

	@JsonProperty("CustP_BusinessActivity")
	private String custP_BusinessActivity = "";

	@JsonProperty("CustP_CAddCBCommune")
	private String custP_CAddCBCommune = "";

	@JsonProperty("CustP_CAddCBCountry")
	private String custP_CAddCBCountry = "";

	@JsonProperty("CustP_CAddCBDistrict")
	private String custP_CAddCBDistrict = "";

	@JsonProperty("CustP_CAddCBProvinceCity")
	private String custP_CAddCBProvinceCity = "";

	@JsonProperty("CustP_CAddCBVillage")
	private String custP_CAddCBVillage = "";

	@JsonProperty("CustP_CAddLocationId")
	private String custP_CAddLocationId = "";

	@JsonProperty("CustP_CAddNo")
	private String custP_CAddNo = "";

	@JsonProperty("CustP_CAddPhoneNo")
	private String custP_CAddPhoneNo = "";

	@JsonProperty("CustP_CAddStreet")
	private String custP_CAddStreet = "";

	@JsonProperty("CustP_CBEmploymentContractType")
	private String custP_CBEmploymentContractType = "";

	@JsonProperty("CustP_CBEmploymentStatus")
	private String custP_CBEmploymentStatus = "";

	@JsonProperty("CustP_CBEmploymentType")
	private String custP_CBEmploymentType = "";

	@JsonProperty("CustP_CBIdIssuedBy")
	private String custP_CBIdIssuedBy = "";

	@JsonProperty("CustP_CBIdType")
	private String custP_CBIdType = "";

	@JsonProperty("CustP_CBMaritalStatus")
	private String custP_CBMaritalStatus = "";

	@JsonProperty("CustP_CBSex")
	private String custP_CBSex = "";

	@JsonProperty("CustP_CIFNo")
	private String custP_CIFNo = "";

	@JsonProperty("CustP_ChildNo")
	private int custP_ChildNo = 0;

	@JsonProperty("CustP_DateOfBirth")
	private String custP_DateOfBirth = "";

	@JsonProperty("CustP_Email")
	private String custP_Email = "";

	@JsonProperty("CustP_EmpAddCBCommune")
	private String custP_EmpAddCBCommune = "";

	@JsonProperty("CustP_EmpAddCBCountry")
	private String custP_EmpAddCBCountry = "";

	@JsonProperty("CustP_EmpAddCBDistrict")
	private String custP_EmpAddCBDistrict = "";

	@JsonProperty("CustP_EmpAddCBProvinceCity")
	private String custP_EmpAddCBProvinceCity = "";

	@JsonProperty("CustP_EmpAddCBVillage")
	private String custP_EmpAddCBVillage = "";

	@JsonProperty("CustP_EmpAddLocationId")
	private String custP_EmpAddLocationId = "";

	@JsonProperty("CustP_EmpAddNo")
	private String custP_EmpAddNo = "";

	@JsonProperty("CustP_EmpAddStreet")
	private String custP_EmpAddStreet = "";

	@JsonProperty("CustP_EmpPermitExpDate")
	private String custP_EmpPermitExpDate = "";

	@JsonProperty("CustP_EmpPermitStartDate")
	private String custP_EmpPermitStartDate = "";

	@JsonProperty("CustP_EmployerName")
	private String custP_EmployerName = "";

	@JsonProperty("CustP_EntityFactoryId")
	private String custP_EntityFactoryId = "";

	@JsonProperty("CustP_FacebookName")
	private String custP_FacebookName = "";

	@JsonProperty("CustP_FamilyNameKH")
	private String custP_FamilyNameKH = "";

	@JsonProperty("CustP_FamilyNameLatin")
	private String custP_FamilyNameLatin = "";

	@JsonProperty("CustP_FirstNameKH")
	private String custP_FirstNameKH = "";

	@JsonProperty("CustP_FirstNameLatin")
	private String custP_FirstNameLatin = "";

	@JsonProperty("CustP_IdExpiryDate")
	private String custP_IdExpiryDate = "";

	@JsonProperty("CustP_IdIssuedDate")
	private String custP_IdIssuedDate = "";

	@JsonProperty("CustP_IdNo")
	private String custP_IdNo = "";

	@JsonProperty("CustP_JobBusinessEndDate")
	private String custP_JobBusinessEndDate = "";

	@JsonProperty("CustP_JobBusinessStartDate")
	private String custP_JobBusinessStartDate = "";

	@JsonProperty("CustP_MiddleNameLatin")
	private String custP_MiddleNameLatin = "";

	@JsonProperty("CustP_MonthlyLoanRepaymentNotInCBC")
	private int custP_MonthlyLoanRepaymentNotInCBC = 0;

	@JsonProperty("CustP_NameOfTenant")
	private String custP_NameOfTenant = "";

	@JsonProperty("CustP_Nationality")
	private String custP_Nationality = "";

	@JsonProperty("CustP_NonCBCLoans")
	private String custP_NonCBCLoans = "";

	@JsonProperty("CustP_Occupation")
	private String custP_Occupation = "";

	@JsonProperty("CustP_POBCBCommune")
	private String custP_POBCBCommune = "";

	@JsonProperty("CustP_POBCBCountry")
	private String custP_POBCBCountry = "";

	@JsonProperty("CustP_POBCBDistrict")
	private String custP_POBCBDistrict = "";

	@JsonProperty("CustP_POBCBProvinceCity")
	private String custP_POBCBProvinceCity = "";

	@JsonProperty("CustP_POBCBVillage")
	private String custP_POBCBVillage = "";

	@JsonProperty("CustP_PRAddCBCoincide")
	private boolean custP_PRAddCBCoincide = false;

	@JsonProperty("CustP_PRAddCBCommune")
	private String custP_PRAddCBCommune = "";

	@JsonProperty("CustP_PRAddCBCountry")
	private String custP_PRAddCBCountry = "";

	@JsonProperty("CustP_PRAddCBDistrict")
	private String custP_PRAddCBDistrict = "";

	@JsonProperty("CustP_PRAddCBProvinceCity")
	private String custP_PRAddCBProvinceCity = "";

	@JsonProperty("CustP_PRAddCBVillage")
	private String custP_PRAddCBVillage = "";

	@JsonProperty("CustP_PRAddLocationId")
	private String custP_PRAddLocationId = "";

	@JsonProperty("CustP_PRAddNo")
	private String custP_PRAddNo = "";

	@JsonProperty("CustP_PRAddPhoneNo")
	private String custP_PRAddPhoneNo = "";

	@JsonProperty("CustP_PRAddStreet")
	private String custP_PRAddStreet = "";

	@JsonProperty("CustP_PhoneNo")
	private String custP_PhoneNo = "";

	@JsonProperty("CustP_StayPermitExpDate")
	private String custP_StayPermitExpDate = "";

	@JsonProperty("CustP_StayPermitStartDate")
	private String custP_StayPermitStartDate = "";

	@JsonProperty("CustP_TenAgreementExpDate")
	private String custP_TenAgreementExpDate = "";

	@JsonProperty("CustP_TenAgreementStartDate")
	private String custP_TenAgreementStartDate = "";

	@JsonProperty("Doc_BankStatement")
	private String doc_BankStatement = "";

	@JsonProperty("Doc_BankStatement_FileName")
	private String doc_BankStatement_FileName = "";

	@JsonProperty("Doc_CustomerProfilePhoto")
	private String doc_CustomerProfilePhoto = "";

	@JsonProperty("Doc_CustomerProfilePhoto_FileName")
	private String doc_CustomerProfilePhoto_FileName = "";

	@JsonProperty("Doc_ECBCConsentForm")
	private String doc_ECBCConsentForm = "";

	@JsonProperty("Doc_ECBCConsentForm_FileName")
	private String doc_ECBCConsentForm_FileName = "";

	@JsonProperty("Doc_EmploymentCard")
	private String doc_EmploymentCard = "";

	@JsonProperty("Doc_EmploymentCard_FileName")
	private String doc_EmploymentCard_FileName = "";

	@JsonProperty("Doc_NID")
	private String doc_NID = "";

	@JsonProperty("Doc_NID_FileName")
	private String doc_NID_FileName = "";

	@JsonProperty("LR_CBCurrency")
	private String lR_CBCurrency = "";

	@JsonProperty("LR_CBLoanCategory")
	private String lR_CBLoanCategory = "";

	@JsonProperty("LR_CBProductType")
	private String lR_CBProductType = "";

	@JsonProperty("LR_CBRepaymentMethod")
	private String lR_CBRepaymentMethod = "";

	@JsonProperty("LR_DisbursementDate")
	private String lR_DisbursementDate = "";

	@JsonProperty("LR_DisbursementScheme")
	private String lR_DisbursementScheme = "";

	@JsonProperty("LR_LoanRequestAmount")
	private double lR_LoanRequestAmount = 0;

	@JsonProperty("LR_LoanTerm")
	private int lR_LoanTerm = 0;

	@JsonProperty("LR_TotalBudgetOfExpenses")
	private double lR_TotalBudgetOfExpenses = 0;

	@JsonProperty("LR_TotalBudgetOfSambatLoan")
	private double lR_TotalBudgetOfSambatLoan = 0;

	@JsonProperty("LoanUtilizationProject")
	private List<LoanUtilizationProjectItem> loanUtilizationProject = new ArrayList<>();

	@JsonProperty("MonthlyExpenses")
	private List<MonthlyExpenseItem> monthlyExpenses = new ArrayList<>();

	@JsonProperty("MonthlyIncomes")
	private List<MonthlyIncomeItem> monthlyIncomes = new ArrayList<>();

	@JsonProperty("OF_InsuranceFee")
	private double oF_InsuranceFee = 0;

	@JsonProperty("OF_LawyerFee")
	private double oF_LawyerFee = 0;

	@JsonProperty("PC_AccountNameSecondary")
	private String pC_AccountNameSecondary = "";

	@JsonProperty("PC_AccountNum")
	private String pC_AccountNum = "";

	@JsonProperty("PC_PaymentChannel")
	private String pC_PaymentChannel = "";

	@JsonProperty("PC_PaymentChannelAccountName")
	private String pC_PaymentChannelAccountName = "";

	@JsonProperty("PC_PaymentChannelName")
	private String pC_PaymentChannelName = "";

	@JsonProperty("hidCurrentUserId")
	private int hidCurrentUserId = 0;

}
