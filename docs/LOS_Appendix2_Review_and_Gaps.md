# Review: LOS "New Loan Application" API (Appendix 2) — Findings & Gap Analysis

**Reviewed files** (in `docs/api spec/`): `Appendix 2.docx` (the New-Loan-Application API spec), `New Loan API Request Json (2).json` (full sample request), `New Loan API Request Json.json` (sample success + failure responses).

**Bottom line:** Appendix 2 answers most of our open questions for the *submit application* call (endpoint, envelope, fields, documents, response). But the LOS `newAppRequest` requires **~102 fields, the majority as CBC standard *codes*** (geo, occupation, employer, employment, id, payment channel, etc.) plus **financial-assessment data (income / expenses / loan-utilization)** that the Kjey PAPA app does **not** collect today. Closing these gaps needs (a) the **CBC code master lists** from LOS, and (b) meaningful additions to our signup/request capture. Details below.

---

## 1. What Appendix 2 confirms (answers to our earlier Open Items)

| Topic | LOS contract |
|---|---|
| **Endpoint** | `POST /api/new-loan-application` (UAT), `Content-Type: application/json` |
| **Request envelope** | `{ appId (Mobile App ID), custId (CIF id if existing customer), doneBy (user name), newAppRequest: { …102 fields… } }` |
| **Documents** | **Base64 inline** — `Doc_<X>` + `Doc_<X>_FileName`. The five are exactly: `ECBCConsentForm`, `CustomerProfilePhoto`, `NID`, `EmploymentCard`, `BankStatement` (required "depending on admin setting"). ✅ matches the 5 we capture. |
| **Response (success)** | `{ "ErrorMessage": null, "IsSuccess": "Success", "Result": [{ "AppId": 8032, "AppRefId": 254906 }], "MissingData": null }` |
| **Response (failure)** | `{ "ErrorMessage": "Application invalid, required data is missing", "IsSuccess": "False", "Result": [], "MissingData": "CustP_CAddCBCommune | " }` |
| **Identifiers** | `AppId` = App#; `AppRefId` = "Application Ref Id, used for backend checking". → store **both** (these become our `losApplicationNo` / `loanRefNo`). |
| **Product framing** | `LR_CBProductType = "PDL"`, `LR_CBLoanCategory = "SIL"`, `LR_CBRepaymentMethod = "EMI"`. PDL sits under the **SIL (Salary Income Loan)** category and is modelled as an **EMI** loan. |
| **Loan term unit** | Sample: `LR_LoanTerm = 3` with amount 250 and an agreed first due date → **term is installments/months, not days.** (Our app currently uses `loanPeriodDays`.) |

`IsSuccess` is a **string** (`"Success"`/`"False"`), not a boolean — our client must parse it as such.

---

## 2. Field-by-field gap analysis (vs. what Kjey PAPA captures today)

Legend — **✅ have it** · **⚠ have value but LOS wants a CODE** · **❌ not captured**

### 2.1 Customer Profile (`CustP_*`)

| LOS field (Required\*) | Our source | Status / action |
|---|---|---|
| `CustP_Age` | DOB | ✅ derive from `dateOfBirth` |
| `CustP_FamilyNameKH/FirstNameKH` | personal | ✅ |
| `CustP_FamilyNameLatin/FirstNameLatin` | personal | ✅ (`MiddleNameLatin` optional — not captured, minor) |
| `CustP_CBSex` | gender | ✅ (M/F) |
| `CustP_DateOfBirth` | personal | ✅ |
| `CustP_Nationality` | personal | ✅ (sample uses `KHM` — confirm it's a code) |
| `CustP_CBIdType` | idType (text) | ⚠ need **ID-type code** (sample `N`) |
| `CustP_IdNo / IdIssuedDate / IdExpiryDate` | personal | ✅ |
| `CustP_CBIdIssuedBy` | — | ❌ **not captured** — need "issued by" code |
| `CustP_CBMaritalStatus` | maritalStatus (text) | ⚠ need **marital code** (sample `S`) |
| `CustP_ChildNo` (req if married) | — | ❌ **not captured** |
| `CustP_PhoneNo` / `CustP_Email` | personal/User | ✅ |
| `CustP_Occupation` | occupation (text) | ⚠ need **occupation code** |
| `CustP_BusinessActivity` | businessActivities (text) | ⚠ need **business-activity code** |
| `CustP_CBEmploymentType` | employmentType (text) | ⚠ need **employment-type code** (sample `E`) |
| `CustP_CBEmploymentContractType` (req if Employee) | — | ❌ **not captured** (e.g. `UDC`/`FDU`) |
| `CustP_CBEmploymentStatus` (req if Employee+UDC) | employmentStatus (text) | ⚠ need **status code** |
| `CustP_EmployerName` | employerName (text) | ⚠ LOS treats as **"employer name (other) code"** |
| `CustP_EntityFactoryId` (req if emp type ≠ other) | — | ❌ **not captured** — employer **entity/factory code** |
| `CustP_JobBusinessStartDate` | employmentStartDate | ✅ (`EndDate` only if FDU contract) |
| `CustP_EmpPermit*/StayPermit*/TenAgreement*` | — | conditional (only non-KHM + FDU) — mostly N/A for KHM |
| **Correspondence addr** `CustP_CAddCB{Country,ProvinceCity,District,Commune,Village}` | corr address (free text) | ⚠ **need CBC geo CODES** (we collect text) |
| **Permanent addr** `CustP_PRAddCB{…}` + `PRAddCBCoincide` | perm addr + same-as flag | ⚠ geo codes; ✅ coincide flag |
| **Place of birth** `CustP_POBCB{Country(req),ProvinceCity(req),District,Commune,Village}` | birth country/prov/dist (text) | ⚠ **need geo codes** |

### 2.2 Loan Request (`LR_*`)

| LOS field | Our source | Status |
|---|---|---|
| `LR_CBCurrency` | currency | ✅ |
| `LR_CBProductType` | constant `PDL` | ✅ |
| `LR_CBLoanCategory` | constant `SIL` | ⚠ confirm constant |
| `LR_CBRepaymentMethod` | constant `EMI` | ⚠ confirm constant |
| `LR_LoanRequestAmount` | requestAmount | ✅ |
| `LR_LoanTerm` | loanPeriodDays | ⚠ **unit mismatch** — LOS term = installments/months, app uses days |
| `LR_DisbursementDate` | disbursementDate | ⚠ who sets it? (app vs LOS) |
| `LR_DisbursementScheme` | — | ❌ **not captured** — need scheme code |
| `LR_TotalBudgetOfExpenses` | — | ❌ **not captured** |
| `LR_TotalBudgetOfSambatLoan` | — | ❌ **not captured** |
| `AgreedFirstDueDate` | — | ❌ **not captured** (could derive) |

### 2.3 Fees (`OF_*`), Payment channel (`PC_*`), Envelope, Arrays

| LOS field | Our source | Status |
|---|---|---|
| `OF_InsuranceFee`, `OF_LawyerFee` (Required) | — | ❌ not captured — default `0`? (confirm) |
| `PC_PaymentChannel`, `PC_PaymentChannelName` (codes), `PC_AccountNum`, `PC_PaymentChannelAccountName` | bank info | ⚠ map bank → payment-channel **codes** (req "if via own account") |
| `appId` (Mobile App ID) | our PaydayLoan id? | ⚠ **clarify** what LOS expects |
| `custId` (CIF if existing) | CIF | ✅ (0/empty if new) |
| `doneBy` (user name) | username | ✅ |
| `hidCurrentUserId` (Required) | — | ❌ **clarify** — a LOS user id (fixed integration account?) |
| `LoanUtilizationProject[]` (category, units, unit price, Sambat loan) | — | ❌ **not captured** |
| `MonthlyIncomes[]` (type, amount, currency) | one `monthlyIncome` | ⚠ partial — LOS wants a **typed list** |
| `MonthlyExpenses[]` (type, amount, currency) | — | ❌ **not captured** |

---

## 3. Key gaps & implications

1. **CBC code master data is the critical dependency.** Almost every categorical field is a *code* (the `*` items): geo (country/province/district/commune/village), occupation, business activity, employment type/contract/status, ID type & issued-by, marital status, employer "other"/entity-factory, loan category, disbursement scheme, payment channel. Our app collects free text / simple labels. **We need LOS/CBC to provide the code lists**, then rework the signup/request forms to use **coded dropdowns + cascading geo pickers**, and store the codes (not labels).

2. **Financial-assessment capture is missing.** LOS requires `MonthlyIncomes[]`, `MonthlyExpenses[]`, `LoanUtilizationProject[]`, and `LR_TotalBudgetOf*`. The app currently captures only a single monthly income. → new capture screens/fields needed.

3. **Missing required fields to add:** `CustP_ChildNo`, `CustP_CBIdIssuedBy`, `CustP_CBEmploymentContractType`, `CustP_EntityFactoryId`, `LR_DisbursementScheme`, `OF_InsuranceFee/LawyerFee`, `AgreedFirstDueDate`, `hidCurrentUserId`.

4. **Semantic clarifications needed:** loan **term unit** (months/installments vs our days); who sets **DisbursementDate / AgreedFirstDueDate**; whether `appId` is our application id; what `hidCurrentUserId` should be; default values for fees and budgets.

5. **What we already have aligns well:** names, sex, DOB, nationality, ID number/dates, phone/email, employment start, the 5 documents (base64), and the same-as-correspondence flag. The mapping layer in our backend (`LosProvider`) is the right place to assemble `newAppRequest`.

---

## 4. What we still need from LOS to finish the integration

- **CBC / master code lists** for every `*` (coded) field — this is the top blocker. (Geo codes, occupation, business activity, employer entity/factory, employment type/contract/status, ID type/issued-by, marital, loan category, disbursement scheme, payment channel.)
- **The remaining API appendices** still outstanding (we only have Appendix 2 = submit):
  - Product sync (catalog) · **Reject** notification · **Rework** status · **Approved** (+ generated docs + repayment schedule) · **Accept/Reject** decision relay · **Disbursement** status · **Repayment/loan** status updates.
  - These map to the webhook + outbound endpoints in `LOS_Integration_API_Spec.md` §4 (Groups A5/A6 and B2, plus A1–A4).
- **Authentication — already resolved.** LOS runs on the **same Tricube server** as SBF core-banking, so `/api/new-loan-application` reuses the existing SBF/Tricube OAuth (`SbfAuthorization`, bearer token). No separate LOS auth/host. Only confirm LOS accepts the same token/scopes. The open auth question is now only the **notification mechanism** (push webhooks vs polling Tricube for status) — see `LOS_Integration_API_Spec.md` §10.3.
- **Field clarifications** from §3.4 above; default values for required-but-not-collected fields (fees, budgets).
- **Sandbox access** + a test applicant for an end-to-end dry run.

---

## 5. Suggested next actions on our side
1. Send LOS the consolidated **`LOS_Integration_API_Spec.md`** (covers the other appendices we still need) **+ this gap list / the request for the CBC code lists**.
2. Once code lists arrive: extend `PdlPersonalInfo` / `PdlEmploymentInfo` / `PdlBankInfo` (+ new financial-info + loan-utilization tables) to store **codes**, and update the app's signup/request forms to coded dropdowns + cascading geo pickers.
3. Implement `LosProviderImpl` (real mode) to assemble `newAppRequest` from our models and `POST` to the Tricube `/api/new-loan-application` endpoint **using the existing `SbfAuthorization` token** (same external server as SBF — no separate `LosAuthorization`), parsing `IsSuccess`/`Result`/`MissingData`.
4. Keep `los.mock.enabled=true` until the code lists + auth + remaining appendices are confirmed.

*Prepared from `Appendix 2.docx` + the sample request/response JSON in `docs/api spec/`.*
