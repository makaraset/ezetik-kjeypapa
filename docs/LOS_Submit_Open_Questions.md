# `POST /new-loan-application` — what we still need from Sambat

The integration is built and tested. It refuses to run until the values below
are supplied, and that refusal is deliberate.

**Why we are asking instead of guessing.** Your UAT accepts unknown codes
without complaining — a wrong `CustP_Occupation` or a wrong commune code does
not bounce, it files a real credit application under a real customer's name
carrying wrong data. A failed call is cheap; a silently wrong credit file is
not. So every value we cannot derive from our own records is left empty and the
submit is blocked until you confirm it.

Our side is ready: the 102-field request is generated from your swagger, the
response handling is written against your two sample bodies, and documents are
read straight from our store as base64.

---

## 1. Integration identifiers (blocking — 2 values)

| Field | Your sample | What we need |
|---|---|---|
| `hidCurrentUserId` | `541` | The LOS user id our integration should file applications as. We have no safe default: `0` either bounces or attributes every Kjey PAPA application to whoever user 0 is. |
| `appId` | `0` | Appendix 2 calls this "Mobile App ID". Is it a registered channel id you issue us, or our own application id? We currently send our loan id, which is wrong if it means the former. |

## 2. Master-list codes (blocking — 10 values)

Appendix 2 marks these Required and flags each with `*` = "CBC code". None has a
lookup endpoint we could resolve at runtime.

| Field | Your sample | Question |
|---|---|---|
| `LR_CBProductType` | `PDL` | Confirm `PDL` is the payday product. |
| `LR_CBLoanCategory` | `SIL` | Confirm for payday. |
| `LR_CBRepaymentMethod` | `EMI` | **Our product is a single bullet repayment on one due date, not EMI.** What is the correct code? Sending `EMI` would misdescribe the product to your credit engine and may drive a wrong schedule back to us. |
| `LR_LoanTerm` | `3` | **What unit?** Our product is **15 days**, single repayment. Sending `15` may book a 15-month loan; sending `1` may book a 1-month loan. Both would be accepted and both are wrong. |
| `LR_DisbursementScheme` | `1` | Confirm the code for disbursement to the customer's own account. |
| `CustP_CBEmploymentType` | `E` | Confirm `E` = Employee. |
| `CustP_CBEmploymentContractType` | `UDC` | Required for every employee. We do not capture this today — should we add it to signup, or is there a default for payroll customers? |
| `CustP_CBIdIssuedBy` | `1` | Required. We never ask who issued the ID. Is there a default for a Cambodian NID? |
| `CustP_CBIdType` | — | Code for a Cambodian national ID card. |
| `PC_PaymentChannel` | *(empty in your sample)* | Required "if via own account", which payday always is. Your sample leaves all `PC_*` empty, so it gives us no guidance. |

## 3. Geo codes (blocking — 20 fields)

Your sample sends NCDD-style numeric codes:

```
CustP_CAddCBProvinceCity: "12"   District: "1214"
Commune: "121402"                Village: "12140204"      Country: "KHM"
```

We store official **names** ("Phnom Penh", "Sen Sok", "Krang Thnong",
"Prey Khla") from the NCDD-coded Pumi dataset — the same source your codes come
from — but without the code column.

Two ways forward, and we would rather you chose:

- **(a)** We re-import our gazetteer with the NCDD code column and map names →
  codes ourselves. Data-only, no UI change. But we would want **one live record
  verified against your system** before trusting it — a wrong commune code
  files a credit application against the wrong locality.
- **(b)** You confirm whether `GET /province`, `/district`, `/commune`,
  `/village` return `id` values equal to the NCDD codes. They currently return
  `{id, description, deleted, parentId}` with no `cbcCode` field, so we cannot
  tell.

## 4. Data we do not collect at all (needs a product decision)

Appendix 2 marks these Required. They are not in our signup flow, and inventing
them would fabricate a household budget and a stated loan purpose on a credit
application — the worst thing on this list to guess.

- `LoanUtilizationProject[]` — loan purpose, category code, unit, price
- `MonthlyExpenses[]` — expense type code + amount
- `LR_TotalBudgetOfExpenses`, `LR_TotalBudgetOfSambatLoan`
- `MonthlyIncomes[].IncomeType` — we send the amount and currency we hold, but not the type code
- `CustP_ChildNo` — required if married; we never ask
- `CustP_Occupation`, `CustP_BusinessActivity` — required codes; our occupation list is our own proposal, not your master list
- `CustP_EmployerName` (described as a **code**, despite the name) and `CustP_EntityFactoryId` — we hold a free-text employer name and have no registry

**Are these genuinely mandatory for the payday product?** Your sample sends
exactly one row per array, which suggests a minimal set would be accepted. If
they are mandatory, this is a change to our signup screens, not a mapping fix.

## 5. Documents

- **One `Doc_NID` slot, two sides.** We hold front and back. We currently send
  the **front** (it carries the printed fields). Do you want the back instead,
  or the two merged into one image?
- **`Doc_ECBCConsentForm`** — we hold a consent *record* (reference, timestamp,
  text version), not a file. Is a generated document acceptable, and must it
  carry Khmer text or a customer signature?
- Your sample has `Doc_NID_FileName: "Test3.PDF"` on JPEG content, so filenames
  appear not to be validated against content. Confirm?

## 6. Operational

- **Maximum request size.** Your 5-document sample is 488 KB. Real phone-camera
  photos plus a multi-page bank statement will be several MB after base64. Is
  there a gateway limit?
- **Duplicate submissions.** If you accept an application and the response is
  lost in transit, we have no way to know. Do you de-duplicate on `appId`, or
  can you expose a lookup so we can check before retrying?
- **Which identifier comes back?** You return both `AppId` (int64) and
  `AppRefId` (int32). `/customer-accepted` takes `appId` as int32, and
  `/payday-disbursement` carries no application identifier at all. Which one
  will your status callbacks quote? We store both, but getting this wrong
  orphans every callback.

---

*Prepared from `docs/sbf_tricube_uat_api_docs.json`, `docs/api spec/Appendix 2.docx`
and your two sample payloads.*
