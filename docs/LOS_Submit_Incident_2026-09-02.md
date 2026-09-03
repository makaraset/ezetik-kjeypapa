# LOS submit incident — first real UAT submission attempts (2026-09-02)

> **RESOLVED 2026-09-03 — root cause was `doneBy`.** Resending the *identical*
> application with only `doneBy` changed from our app username `010849001` to a
> known LOS user (`manith.khut`) turned the 90 s hang / 500 into a clean
> **6.6 s** response carrying a proper `MissingData` list. So the crash was
> Sambat's LOS choking on a `doneBy` its user table does not know — **`custId`
> 70 was fine**. See "Resolution" at the bottom. The remaining ask for Sambat
> is which `doneBy` our integration should send (provision our own LOS user, or
> nominate one), plus the newly-revealed mandatory fields.

For Sambat/Tricube: our first two live calls to `POST /api/new-loan-application`
failed **server-side**. Please check your UAT logs around these times
(Asia/Phnom_Penh):

| attempt | sent (approx) | outcome |
|---|---|---|
| 1 | 2026-09-02 ~15:58:05 | after ~90s: **HTTP 500** `{"timestamp":1788339575640,"status":500,"error":"Internal Server Error","path":"/api/new-loan-application"}` (response epoch = 15:59:35.640 ICT) |
| 2 | 2026-09-02 ~16:05:04 | **no response in 120s** — our read timeout fired at 16:07:04 |

What we know is NOT the cause:
- **Gateway/auth is healthy**: the same host, OAuth token and client pulled
  `/employer`, `/all-address` and `/bulk-selection-mini` successfully minutes
  earlier the same day.
- **Size**: total request ≈ 1 MB (four docs, largest 216 KB raw / ~288 KB base64)
  — far under the reference application's ~3 MB.
- **Format**: matches the swagger and your PDL reference — raw base64 (no
  `data:` prefix), `yyyy-MM-dd` dates, all confirmed codes
  (PDL/SIL/EMI/term 1/scheme 4/BANK/#12/N/A/E/1), `hidCurrentUserId` 575,
  NCDD-padded geo codes, employer `E10185`.

Both failures took 90+ seconds, which looks like processing that hangs and
dies rather than validation (validation errors should return quickly as
`IsSuccess:"False"` + `MissingData`). Two things unique to this submission that
your side may want to check first:
- `custId: 70` — a real CIF created through the mobile-gateway signup
  (customer 110553867 / SET MAKARA). Your reference used custId 56.
- `doneBy: "010849001"` — our app username, unknown to the LOS user table.

Our loan stayed safely in Draft on both failures (no consent stamped, no state
advanced); we can resubmit the same application on request.

## Resolution — the `doneBy` was the cause (2026-09-03)

We resubmitted the **same** loan 19 (same `custId` 70, same everything) with a
single change: `doneBy` set to `manith.khut` instead of our app username
`010849001`.

| submit | `doneBy` | result |
|---|---|---|
| 1–2 (2026-09-02) | `010849001` | 500 after ~90 s, then a >120 s hang |
| 3 (2026-09-03 ~08:53 ICT) | `manith.khut` | **HTTP 200 in 6.6 s**, `MissingData` returned normally |

So the LOS was choking on a `doneBy` its user table does not recognise —
returning it as a slow 500/timeout rather than a clean error. `custId` 70 (our
real mobile-gateway CIF) was never the problem.

**What Sambat's `MissingData` then told us** (the payday application's real
mandatory set — everything else we send is accepted):

```
business activity, occupation, Doc_ECBCConsentForm, Doc_ECBCConsentForm_FileName
```

i.e. `CustP_BusinessActivity`, `CustP_Occupation`, and the e-CBC consent form
document. These were on our "optional / capture later" list; the LOS confirms
they are required. The loan again stayed in Draft (a `MissingData` response
files nothing), so no consent was stamped.

### What we now need from Sambat
1. **`doneBy`** — which LOS username should our mobile integration send? Either
   provision a LOS user for us, or nominate an existing one. (Our own customer
   usernames are not LOS users, so they cannot be used.)
2. **`CustP_Occupation`** — the code list. We hold the occupation *label*
   ("IT Staff"); we need to drive the signup dropdown from your `/occupation`
   dictionary and send its id.
3. **`CustP_BusinessActivity`** — where do the 8-digit business-activity codes
   come from? We do not collect this at all today.
4. **`Doc_ECBCConsentForm`** — we hold a consent *record* (date + version +
   generated ref), not a rendered file. What artefact do you expect here — a
   generated PDF of the consent text, or is a reference acceptable?

## Exact payload sent (documents abbreviated to their byte counts)

```json
{
  "appId": 0,
  "custId": 70,
  "doneBy": "010849001",
  "newAppRequest": {
    "AgreedFirstDueDate": "2026-09-18",
    "CustP_Age": 36,
    "CustP_BusinessActivity": "",
    "CustP_CAddCBCommune": "120807",
    "CustP_CAddCBCountry": "KHM",
    "CustP_CAddCBDistrict": "1208",
    "CustP_CAddCBProvinceCity": "12",
    "CustP_CAddCBVillage": "12080705",
    "CustP_CAddLocationId": "",
    "CustP_CAddNo": "541",
    "CustP_CAddPhoneNo": "010849001",
    "CustP_CAddStreet": "",
    "CustP_CBEmploymentContractType": "N/A",
    "CustP_CBEmploymentStatus": "2",
    "CustP_CBEmploymentType": "E",
    "CustP_CBIdIssuedBy": "1",
    "CustP_CBIdType": "N",
    "CustP_CBMaritalStatus": "M",
    "CustP_CBSex": "M",
    "CustP_CIFNo": "70",
    "CustP_ChildNo": 0,
    "CustP_DateOfBirth": "1990-01-03",
    "CustP_Email": "set.makara@yahoo.com",
    "CustP_EmpAddCBCommune": "121205",
    "CustP_EmpAddCBCountry": "KHM",
    "CustP_EmpAddCBDistrict": "1212",
    "CustP_EmpAddCBProvinceCity": "12",
    "CustP_EmpAddCBVillage": "12120501",
    "CustP_EmpAddLocationId": "",
    "CustP_EmpAddNo": "",
    "CustP_EmpAddStreet": "",
    "CustP_EmpPermitExpDate": "",
    "CustP_EmpPermitStartDate": "",
    "CustP_EmployerName": "Ezetik",
    "CustP_EntityFactoryId": "E10185",
    "CustP_FacebookName": "",
    "CustP_FamilyNameKH": "សែត",
    "CustP_FamilyNameLatin": "SET",
    "CustP_FirstNameKH": "មករា",
    "CustP_FirstNameLatin": "MAKARA",
    "CustP_IdExpiryDate": "2035-03-16",
    "CustP_IdIssuedDate": "2025-03-17",
    "CustP_IdNo": "110553867",
    "CustP_JobBusinessEndDate": "",
    "CustP_JobBusinessStartDate": "2020-01-01",
    "CustP_MiddleNameLatin": "",
    "CustP_MonthlyLoanRepaymentNotInCBC": 0,
    "CustP_NameOfTenant": "",
    "CustP_Nationality": "KHM",
    "CustP_NonCBCLoans": "",
    "CustP_Occupation": "",
    "CustP_POBCBCommune": "000000",
    "CustP_POBCBCountry": "KHM",
    "CustP_POBCBDistrict": "0701",
    "CustP_POBCBProvinceCity": "07",
    "CustP_POBCBVillage": "00000000",
    "CustP_PRAddCBCoincide": true,
    "CustP_PRAddCBCommune": "120807",
    "CustP_PRAddCBCountry": "KHM",
    "CustP_PRAddCBDistrict": "1208",
    "CustP_PRAddCBProvinceCity": "12",
    "CustP_PRAddCBVillage": "12080705",
    "CustP_PRAddLocationId": "",
    "CustP_PRAddNo": "541",
    "CustP_PRAddPhoneNo": "010849001",
    "CustP_PRAddStreet": "",
    "CustP_PhoneNo": "010849001",
    "CustP_StayPermitExpDate": "",
    "CustP_StayPermitStartDate": "",
    "CustP_TenAgreementExpDate": "",
    "CustP_TenAgreementStartDate": "",
    "Doc_BankStatement": "<base64, 58557 bytes>",
    "Doc_BankStatement_FileName": "BankStatement-19.png",
    "Doc_CustomerProfilePhoto": "<base64, 221190 bytes>",
    "Doc_CustomerProfilePhoto_FileName": "ProfilePhoto-19.jpg",
    "Doc_ECBCConsentForm": "",
    "Doc_ECBCConsentForm_FileName": "",
    "Doc_EmploymentCard": "<base64, 221190 bytes>",
    "Doc_EmploymentCard_FileName": "EmploymentCard-19.jpg",
    "Doc_NID": "<base64, 221190 bytes>",
    "Doc_NID_FileName": "NID-19.jpg",
    "LR_CBCurrency": "USD",
    "LR_CBLoanCategory": "SIL",
    "LR_CBProductType": "PDL",
    "LR_CBRepaymentMethod": "EMI",
    "LR_DisbursementDate": "2026-09-03",
    "LR_DisbursementScheme": "4",
    "LR_LoanRequestAmount": 9.93,
    "LR_LoanTerm": 1,
    "LR_TotalBudgetOfExpenses": 0.0,
    "LR_TotalBudgetOfSambatLoan": 0.0,
    "LoanUtilizationProject": [],
    "MonthlyExpenses": [],
    "MonthlyIncomes": [
      {
        "IncomeType": "S",
        "IncomeAmount": 500.0,
        "Currency": "USD"
      }
    ],
    "OF_InsuranceFee": 0.0,
    "OF_LawyerFee": 0.0,
    "PC_AccountNameSecondary": "",
    "PC_AccountNum": "112233",
    "PC_PaymentChannel": "BANK",
    "PC_PaymentChannelAccountName": "SET MAKARA",
    "PC_PaymentChannelName": "12",
    "hidCurrentUserId": 575
  }
}
```
