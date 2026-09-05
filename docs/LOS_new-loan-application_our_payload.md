# `POST /new-loan-application` — the payload Kjey PAPA sends

Generated 2026-08-30 from a real fixture (loan 18, a USD 40 payday application on a test account) through the exact code that will submit for real. `Doc_*` base64 is replaced by its byte size; nothing else is edited.

**Flow:** app → `POST /pdl/{id}/submit` (our backend, no body) → our backend builds this JSON → `POST https://tricube-uat.sambatfinance.com:6443/api/new-loan-application`.

**Request to Sambat:** please send your reference payload for the same product so we can diff field by field. The questions below are what we cannot answer from the swagger or Appendix 2.

## Summary

| Status | Fields | Meaning |
|---|---:|---|
| From customer record | 43 | value comes from the customer's record |
| Fixed value | 5 | fixed by agreement |
| Document (base64) | 8 | base64 + file name |
| Awaiting Sambat code | 11 | we hold no code; the submit is blocked until you confirm it |
| Sent, semantics to confirm | 5 | we send a value but are not sure it is the one you mean |
| Not collected — needs product decision | 13 | Appendix 2 marks it Required; our signup does not capture it |
| Optional, left blank | 20 | Appendix 2 marks it Optional; sent empty |

## Field by field

### Envelope

| Field | Our value | Status | Note |
|---|---|---|---|
| `appId` | `0` | Awaiting Sambat code | Appendix 2 calls this “Mobile App ID”. A channel id you issue us, or our application id? We send our config value (0) until told. |
| `custId` | `0` | From customer record | Your CIF from GET /customer-information/by-idno at signup; 0 = new to Sambat. This fixture has none; the real-card account carries 70. |
| `doneBy` | `010849009` | From customer record | Our login id = the customer's phone number. |
### Integration

| Field | Our value | Status | Note |
|---|---|---|---|
| `hidCurrentUserId` | `0` | Awaiting Sambat code | The LOS user id our integration files as (your sample: 541). No safe default. |
### Identity

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_Age` | `26` | From customer record | Derived from DOB at submit time |
| `CustP_CBIdIssuedBy` | `` | Awaiting Sambat code | Required code; we never ask who issued the ID. Is there a default for a Cambodian NID? |
| `CustP_CBIdType` | `N` | From customer record | idCode from GET /idregistration (N = National ID). |
| `CustP_CBMaritalStatus` | `S` | From customer record | cbcCode from GET /maritalStatus, as you confirmed. Our form offers Single/Married only; “Other” would be sent blank. |
| `CustP_CBSex` | `M` | From customer record | M / F |
| `CustP_CIFNo` | `` | From customer record | Same CIF as custId, or blank when new. |
| `CustP_ChildNo` | `0` | Not collected — needs product decision | Required if married. Not asked at signup. |
| `CustP_DateOfBirth` | `2000-01-01` | From customer record | yyyy-MM-dd |
| `CustP_Email` | `` | From customer record |  |
| `CustP_FacebookName` | `` | Optional, left blank |  |
| `CustP_FamilyNameKH` | `សែត` | From customer record |  |
| `CustP_FamilyNameLatin` | `SET` | From customer record |  |
| `CustP_FirstNameKH` | `មករា` | From customer record |  |
| `CustP_FirstNameLatin` | `MAKARA` | From customer record |  |
| `CustP_IdExpiryDate` | `2030-01-31` | From customer record | yyyy-MM-dd |
| `CustP_IdIssuedDate` | `2023-01-03` | From customer record | yyyy-MM-dd |
| `CustP_IdNo` | `100849009` | From customer record | 9-digit national ID |
| `CustP_MiddleNameLatin` | `` | Optional, left blank |  |
| `CustP_Nationality` | `KHM` | Fixed value | KHM, as you confirmed. |
| `CustP_PhoneNo` | `010849009` | From customer record |  |
### Correspondence address

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_CAddCBCommune` | `120807` | From customer record | NCDD code (6-digit) from your /all-address, stored at signup. |
| `CustP_CAddCBCountry` | `KHM` | Fixed value | KHM |
| `CustP_CAddCBDistrict` | `1208` | From customer record | NCDD code (4-digit) from your /all-address, stored at signup. |
| `CustP_CAddCBProvinceCity` | `12` | From customer record | NCDD code (2-digit) from your /all-address, stored at signup. |
| `CustP_CAddCBVillage` | `12080705` | From customer record | NCDD code (8-digit) from your /all-address, stored at signup. |
| `CustP_CAddLocationId` | `` | Not collected — needs product decision | Google Maps pin, as you confirmed. The app has no map step; sent blank. Tell us if that blocks an application. |
| `CustP_CAddNo` | `7` | From customer record | We capture house and street as ONE free-text field; it is sent here. |
| `CustP_CAddPhoneNo` | `010849009` | From customer record | Customer's mobile. |
| `CustP_CAddStreet` | `` | Sent, semantics to confirm | Blank — house+street travel together in *No above. Split them if you need the street separately. |
### Permanent address

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_PRAddCBCoincide` | `true` | From customer record | true when permanent = correspondence. |
| `CustP_PRAddCBCommune` | `120807` | From customer record | NCDD code (6-digit) from your /all-address, stored at signup. |
| `CustP_PRAddCBCountry` | `KHM` | Fixed value | KHM |
| `CustP_PRAddCBDistrict` | `1208` | From customer record | NCDD code (4-digit) from your /all-address, stored at signup. |
| `CustP_PRAddCBProvinceCity` | `12` | From customer record | NCDD code (2-digit) from your /all-address, stored at signup. |
| `CustP_PRAddCBVillage` | `12080705` | From customer record | NCDD code (8-digit) from your /all-address, stored at signup. |
| `CustP_PRAddLocationId` | `` | Not collected — needs product decision | Google Maps pin, as you confirmed. The app has no map step; sent blank. Tell us if that blocks an application. |
| `CustP_PRAddNo` | `7` | From customer record | We capture house and street as ONE free-text field; it is sent here. |
| `CustP_PRAddPhoneNo` | `010849009` | From customer record | Customer's mobile. |
| `CustP_PRAddStreet` | `` | Sent, semantics to confirm | Blank — house+street travel together in *No above. Split them if you need the street separately. |
### Place of birth

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_POBCBCommune` | `` | Optional, left blank | Not captured for place of birth. |
| `CustP_POBCBCountry` | `KHM` | Fixed value | KHM |
| `CustP_POBCBDistrict` | `701` | From customer record | Place of birth: the form captures province and district. |
| `CustP_POBCBProvinceCity` | `7` | From customer record | Place of birth: the form captures province and district. |
| `CustP_POBCBVillage` | `` | Optional, left blank | Not captured for place of birth. |
### Employment

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_BusinessActivity` | `` | Not collected — needs product decision | Required 9-digit code (sample 020301081). Free text on our side today. |
| `CustP_CBEmploymentContractType` | `` | Awaiting Sambat code | Required for every employee (sample UDC). Not asked at signup — default for payroll customers? |
| `CustP_CBEmploymentStatus` | `` | Not collected — needs product decision | Required if contract type is UDC. Our form holds our own labels, not your codes. |
| `CustP_CBEmploymentType` | `` | Awaiting Sambat code | Your sample: E. Confirm E = Employee. |
| `CustP_EmpPermitExpDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_EmpPermitStartDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_EmployerName` | `EZETIK` | Sent, semantics to confirm | We send the employer's NAME as typed. Appendix 2 describes this field as “Employer name (other) code*”. Which do you want? |
| `CustP_EntityFactoryId` | `` | Not collected — needs product decision | “Employer (entity/factory) code*”, required unless employment type is Other. We have no employer registry — is there a lookup? |
| `CustP_JobBusinessEndDate` | `` | Optional, left blank |  |
| `CustP_JobBusinessStartDate` | `2020-01-01` | From customer record | yyyy-MM-dd |
| `CustP_MonthlyLoanRepaymentNotInCBC` | `0` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_NameOfTenant` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_NonCBCLoans` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_Occupation` | `` | Not collected — needs product decision | Required code. GET /occupation gives ids; our dropdown still holds our own 18 labels. Switching it to your list is planned — confirm the id is what goes here. |
| `CustP_StayPermitExpDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_StayPermitStartDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_TenAgreementExpDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
| `CustP_TenAgreementStartDate` | `` | Optional, left blank | Only required for non-KHM / FDU contracts (Appendix 2). |
### Employer address

| Field | Our value | Status | Note |
|---|---|---|---|
| `CustP_EmpAddCBCommune` | `120807` | From customer record | Employer address, NCDD code (6-digit). |
| `CustP_EmpAddCBCountry` | `KHM` | Fixed value | KHM |
| `CustP_EmpAddCBDistrict` | `1208` | From customer record | Employer address, NCDD code (4-digit). |
| `CustP_EmpAddCBProvinceCity` | `12` | From customer record | Employer address, NCDD code (2-digit). |
| `CustP_EmpAddCBVillage` | `12080705` | From customer record | Employer address, NCDD code (8-digit). |
| `CustP_EmpAddLocationId` | `` | Optional, left blank |  |
| `CustP_EmpAddNo` | `` | Optional, left blank | Employer house/street not captured. |
| `CustP_EmpAddStreet` | `` | Optional, left blank | Employer street not captured. |
### Loan request

| Field | Our value | Status | Note |
|---|---|---|---|
| `AgreedFirstDueDate` | `2026-09-10` | From customer record | The single repayment date (payday product: one bullet repayment). |
| `LR_CBCurrency` | `USD` | From customer record | USD / KHR as chosen. |
| `LR_CBLoanCategory` | `` | Awaiting Sambat code | Your sample: SIL. Confirm for payday. |
| `LR_CBProductType` | `` | Awaiting Sambat code | Your sample: PDL. Confirm this is the payday product. |
| `LR_CBRepaymentMethod` | `` | Awaiting Sambat code | Your sample: EMI — but payday is ONE bullet repayment on one due date. What is the right code? |
| `LR_DisbursementDate` | `2026-08-26` | From customer record | yyyy-MM-dd, the disbursement date quoted at application. |
| `LR_DisbursementScheme` | `` | Awaiting Sambat code | Your sample: 1. Confirm the code for disbursement to the customer's own account. |
| `LR_LoanRequestAmount` | `39.7` | From customer record | Requested amount (USD 39.70 on this fixture; the customer chose a USD 40 repayment). |
| `LR_LoanTerm` | `0` | Awaiting Sambat code | Your sample: 3. In what UNIT? Our product is 15 days, single repayment. Sending 15 may book 15 months; sending 1 may book 1 month. |
| `LR_TotalBudgetOfExpenses` | `0.0` | Not collected — needs product decision | Required per Appendix 2; not collected. Is it mandatory for payday? |
| `LR_TotalBudgetOfSambatLoan` | `0.0` | Not collected — needs product decision | Required per Appendix 2 (sample 25 against a 250 loan — relationship unclear). Not collected. |
### Payment channel

| Field | Our value | Status | Note |
|---|---|---|---|
| `PC_AccountNameSecondary` | `` | Optional, left blank |  |
| `PC_AccountNum` | `1122334` | From customer record | Customer's payroll account number (disbursement to own account). |
| `PC_PaymentChannel` | `` | Awaiting Sambat code | “Payment channel code*”, required for own-account disbursement. Your sample leaves all PC_* empty. |
| `PC_PaymentChannelAccountName` | `SET MAKARA` | From customer record | Account holder name as on the bank statement. |
| `PC_PaymentChannelName` | `` | Sent, semantics to confirm | Appendix 2 says a bank CODE. We hold the bank NAME (e.g. Campu Bank). Which list? |
### Other fees

| Field | Our value | Status | Note |
|---|---|---|---|
| `OF_InsuranceFee` | `0.0` | Optional, left blank | 0 — no insurance on payday. |
| `OF_LawyerFee` | `0.0` | Optional, left blank | 0 |
### Documents

| Field | Our value | Status | Note |
|---|---|---|---|
| `Doc_BankStatement` | `<base64, 3782067 bytes>` | Document (base64) | Bank statement (image or PDF). |
| `Doc_BankStatement_FileName` | `BankStatement-18.jpg` | Document (base64) |  |
| `Doc_CustomerProfilePhoto` | `<base64, 58557 bytes>` | Document (base64) | Selfie captured at signup. |
| `Doc_CustomerProfilePhoto_FileName` | `ProfilePhoto-18.png` | Document (base64) |  |
| `Doc_ECBCConsentForm` | `` | Not collected — needs product decision | We hold a consent RECORD (reference, timestamp, text version), not a file. Is a generated document acceptable? Must it carry Khmer text or a signature? |
| `Doc_ECBCConsentForm_FileName` | `` | Not collected — needs product decision | See above. |
| `Doc_EmploymentCard` | `<base64, 1857369 bytes>` | Document (base64) | Employment card photo. |
| `Doc_EmploymentCard_FileName` | `EmploymentCard-18.jpg` | Document (base64) |  |
| `Doc_NID` | `<base64, 1483890 bytes>` | Document (base64) | NID FRONT (JPEG). We hold front AND back; the schema has one slot. Front only, back, or both merged? |
| `Doc_NID_FileName` | `NID-18.jpg` | Document (base64) | NID-<our loan id>.jpg |
### Financial assessment arrays

| Field | Our value | Status | Note |
|---|---|---|---|
| `LoanUtilizationProject` | `[]` | Not collected — needs product decision | Required per Appendix 2 (loan purpose code, units, price). Not collected. Is a minimal single row acceptable for payday? |
| `MonthlyExpenses` | `[]` | Not collected — needs product decision | Required per Appendix 2. Not collected — a household budget we would be inventing. |
| `MonthlyIncomes` | `[{"IncomeType": "", "IncomeAmount": 500.0, "Currency": "USD"` | Sent, semantics to confirm | One row: amount + currency from the employment form. IncomeType is a code we do not hold — which value for salary? |

## Questions for Sambat

1. **Identifiers** — `hidCurrentUserId` (your LOS user for our integration) and what `appId` means.
2. **Loan term and repayment** — `LR_LoanTerm` unit and `LR_CBRepaymentMethod` for a 15-day single-bullet product; `LR_CBProductType`, `LR_CBLoanCategory`, `LR_DisbursementScheme` codes.
3. **Employment codes** — `CustP_CBEmploymentType`, `CustP_CBEmploymentContractType`, `CustP_CBEmploymentStatus`; whether `CustP_EmployerName` is a name or a code, and where `CustP_EntityFactoryId` comes from.
4. **Occupation / business activity** — is the `/occupation` id the value for `CustP_Occupation`? Where do the 9-digit business-activity codes come from?
5. **Financial assessment** — are `LoanUtilizationProject`, `MonthlyExpenses`, `LR_TotalBudget*` and `MonthlyIncomes[].IncomeType` genuinely mandatory for payday? If so we must change the signup flow.
6. **Payment channel** — `PC_PaymentChannel` code for own-account disbursement and the bank-code list behind `PC_PaymentChannelName`.
7. **Documents** — one `Doc_NID` slot for two card sides; what artefact satisfies `Doc_ECBCConsentForm`; the maximum request size (this one is ~3 MB of base64).
8. **Identity** — `CustP_CBIdIssuedBy` default for a national ID; `CustP_ChildNo` when married; whether the `LocationId` fields may stay empty.
9. **Duplicates and callbacks** — do you de-duplicate on `appId` if our response is lost? Which of `AppId` / `AppRefId` will your status callbacks quote?

## Raw JSON

```json
{
  "appId": 0,
  "custId": 0,
  "doneBy": "010849009",
  "newAppRequest": {
    "AgreedFirstDueDate": "2026-09-10",
    "CustP_Age": 26,
    "CustP_BusinessActivity": "",
    "CustP_CAddCBCommune": "120807",
    "CustP_CAddCBCountry": "KHM",
    "CustP_CAddCBDistrict": "1208",
    "CustP_CAddCBProvinceCity": "12",
    "CustP_CAddCBVillage": "12080705",
    "CustP_CAddLocationId": "",
    "CustP_CAddNo": "7",
    "CustP_CAddPhoneNo": "010849009",
    "CustP_CAddStreet": "",
    "CustP_CBEmploymentContractType": "",
    "CustP_CBEmploymentStatus": "",
    "CustP_CBEmploymentType": "",
    "CustP_CBIdIssuedBy": "",
    "CustP_CBIdType": "N",
    "CustP_CBMaritalStatus": "S",
    "CustP_CBSex": "M",
    "CustP_CIFNo": "",
    "CustP_ChildNo": 0,
    "CustP_DateOfBirth": "2000-01-01",
    "CustP_Email": "",
    "CustP_EmpAddCBCommune": "120807",
    "CustP_EmpAddCBCountry": "KHM",
    "CustP_EmpAddCBDistrict": "1208",
    "CustP_EmpAddCBProvinceCity": "12",
    "CustP_EmpAddCBVillage": "12080705",
    "CustP_EmpAddLocationId": "",
    "CustP_EmpAddNo": "",
    "CustP_EmpAddStreet": "",
    "CustP_EmpPermitExpDate": "",
    "CustP_EmpPermitStartDate": "",
    "CustP_EmployerName": "EZETIK",
    "CustP_EntityFactoryId": "",
    "CustP_FacebookName": "",
    "CustP_FamilyNameKH": "សែត",
    "CustP_FamilyNameLatin": "SET",
    "CustP_FirstNameKH": "មករា",
    "CustP_FirstNameLatin": "MAKARA",
    "CustP_IdExpiryDate": "2030-01-31",
    "CustP_IdIssuedDate": "2023-01-03",
    "CustP_IdNo": "100849009",
    "CustP_JobBusinessEndDate": "",
    "CustP_JobBusinessStartDate": "2020-01-01",
    "CustP_MiddleNameLatin": "",
    "CustP_MonthlyLoanRepaymentNotInCBC": 0,
    "CustP_NameOfTenant": "",
    "CustP_Nationality": "KHM",
    "CustP_NonCBCLoans": "",
    "CustP_Occupation": "",
    "CustP_POBCBCommune": "",
    "CustP_POBCBCountry": "KHM",
    "CustP_POBCBDistrict": "701",
    "CustP_POBCBProvinceCity": "7",
    "CustP_POBCBVillage": "",
    "CustP_PRAddCBCoincide": true,
    "CustP_PRAddCBCommune": "120807",
    "CustP_PRAddCBCountry": "KHM",
    "CustP_PRAddCBDistrict": "1208",
    "CustP_PRAddCBProvinceCity": "12",
    "CustP_PRAddCBVillage": "12080705",
    "CustP_PRAddLocationId": "",
    "CustP_PRAddNo": "7",
    "CustP_PRAddPhoneNo": "010849009",
    "CustP_PRAddStreet": "",
    "CustP_PhoneNo": "010849009",
    "CustP_StayPermitExpDate": "",
    "CustP_StayPermitStartDate": "",
    "CustP_TenAgreementExpDate": "",
    "CustP_TenAgreementStartDate": "",
    "Doc_BankStatement": "<base64, 3782067 bytes>",
    "Doc_BankStatement_FileName": "BankStatement-18.jpg",
    "Doc_CustomerProfilePhoto": "<base64, 58557 bytes>",
    "Doc_CustomerProfilePhoto_FileName": "ProfilePhoto-18.png",
    "Doc_ECBCConsentForm": "",
    "Doc_ECBCConsentForm_FileName": "",
    "Doc_EmploymentCard": "<base64, 1857369 bytes>",
    "Doc_EmploymentCard_FileName": "EmploymentCard-18.jpg",
    "Doc_NID": "<base64, 1483890 bytes>",
    "Doc_NID_FileName": "NID-18.jpg",
    "LR_CBCurrency": "USD",
    "LR_CBLoanCategory": "",
    "LR_CBProductType": "",
    "LR_CBRepaymentMethod": "",
    "LR_DisbursementDate": "2026-08-26",
    "LR_DisbursementScheme": "",
    "LR_LoanRequestAmount": 39.7,
    "LR_LoanTerm": 0,
    "LR_TotalBudgetOfExpenses": 0.0,
    "LR_TotalBudgetOfSambatLoan": 0.0,
    "LoanUtilizationProject": [],
    "MonthlyExpenses": [],
    "MonthlyIncomes": [
      {
        "IncomeType": "",
        "IncomeAmount": 500.0,
        "Currency": "USD"
      }
    ],
    "OF_InsuranceFee": 0.0,
    "OF_LawyerFee": 0.0,
    "PC_AccountNameSecondary": "",
    "PC_AccountNum": "1122334",
    "PC_PaymentChannel": "",
    "PC_PaymentChannelAccountName": "SET MAKARA",
    "PC_PaymentChannelName": "",
    "hidCurrentUserId": 0
  }
}
```
