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

**Answered 2026-08-28 — thank you.** `CustP_CBMaritalStatus` takes the `cbcCode`,
`CustP_Nationality` takes `KHM`, and the `CustP_*LocationId` fields are Google
Maps coordinates. All three are implemented and tested. We do **not** collect a
map pin in the app, so the `LocationId` fields are sent empty — tell us if that
blocks an application and we will add a map step.

One consequence worth raising: your marital list has 7 entries and ours has 3,
including an "Other" that has no counterpart in yours. We send blank rather than
mapping it onto your "Unknown", which would assert something about the customer
you never told us. We would rather drive our dropdown from your list — confirm
and we will.

## 3. Geo codes — RESOLVED 2026-08-28

Confirmed that your ids ARE the NCDD codes, verified end to end
(`12` / `1208` / `120807` / `12080705`). We now mirror `/all-address` and
`/bulk-selection-mini` and store your codes directly. Nothing outstanding here.

One note for your side: our previous dataset romanised 14 districts differently
(we had "Sen Sok", you have "Saensokh") and had 1,648 fewer villages, so we have
retired ours entirely in favour of yours.

## 3b. The original geo question (kept for the record)

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

---

## Update — Sambat's reference payload received (2026-08-30)

Manith sent a real `POST /new-loan-application` reference. Diffed field-for-field
against ours (`docs/LOS_new-loan-application_our_payload.json`): **the key set
matches exactly, 102 for 102.**

**Their reference is an EMI, 3-installment, `LR_CBProductType="SIL"` loan — NOT
our single-bullet payday product.** So it settles customer-level fields but does
*not* answer the loan-terms questions; those still need payday-specific values.

### Bug the diff found (fixed, commit c9329c8)
Their dictionary returns geo codes as integers (Kampot = `7`, its districts
`701`/`702`); their loan payload restores the leading zeros (`07` / `0702` /
`070204`). We were sending the unpadded form, which would misfile every address
in the **nine single-digit provinces (codes 1–9)**. The mapper now zero-pads to
the NCDD width (2/4/6/8) at submit time.

### Answered — adopted
- `CustP_CBEmploymentType = E`, `CustP_CBIdIssuedBy = 1` → set as config; the
  submit gate no longer blocks on them.
- `MonthlyIncomes[].IncomeType = "S"` (Salary) → wired.
- `CustP_EmployerName` is a plain **name** ("Sambat Finance PLC"), not a code —
  we were already right.
- `LocationId` optional (they fill only EmpAdd); `CustP_EntityFactoryId` empty
  even for their employee (not mandatory for a normal employee).

### Still open — payday-specific
- `LR_CBProductType` (they sent SIL, swagger said PDL), `LR_CBLoanCategory`
  (null), `LR_CBRepaymentMethod` (EMI vs our bullet), `LR_LoanTerm` (3
  installments vs our 15 days), `LR_DisbursementScheme` (empty),
  `PC_PaymentChannel` / `PC_PaymentChannelName` (empty), `hidCurrentUserId`.

### Needs a signup-form change or master-list mapping
- `CustP_Occupation` (numeric code `61` → drive our dropdown from `/occupation`),
  `CustP_BusinessActivity` (8-digit), `CustP_CBEmploymentContractType`/`Status`,
  `CustP_ChildNo`, `MonthlyExpenses`, `LoanUtilizationProject`, and whether to
  split house number from street (`PRAddNo` + `PRAddStreet`).

---

## Update — the REAL payday reference (2026-08-31)

Sambat sent a second reference, this one genuinely payday
(`LR_CBProductType="PDL"`, a filled-in loan, real ~1.7 MB documents, customer
CIF 56). Still 102 for 102 on structure. It settled every payday loan code —
all now in `application.properties`:

| field | payday value |
|---|---|
| `LR_CBProductType` | `PDL` |
| `LR_CBLoanCategory` | `SIL` |
| `LR_CBRepaymentMethod` + `LR_LoanTerm` | `EMI` + `1` — one installment = a single bullet repayment |
| `LR_DisbursementScheme` | `4` |
| `PC_PaymentChannel` | `BANK` |
| `CustP_CBEmploymentContractType` | `N/A` (their payday convention) |

**The submit gate is now down to `hid-current-user-id` alone** — the LOS user
id for our integration account, which only Sambat can give us.

### Still needs Sambat / a signup change
- `hidCurrentUserId` — our integration user id (the one hard blocker).
- `PC_PaymentChannelName` — a bank CODE (`31`); we hold the bank NAME. Need the
  bank-code list.
- `CustP_EntityFactoryId` — now populated (`G30020`), so it IS required. Need an
  employer-entity code source.
- `CustP_Occupation` / `CustP_BusinessActivity` — numeric codes; drive our
  dropdowns from `/occupation` etc.
- `CustP_CBEmploymentStatus` (`2`), `CustP_ChildNo`, `MonthlyExpenses`,
  `LoanUtilizationProject`, `LR_TotalBudget*` — signup capture, or confirm they
  are optional for payday.
- Unknown place-of-birth commune/village: they send `000000` / `00000000`
  (zero-fill); we send blank — confirm which they require.
- Amounts/term arrive as strings in this reference, numbers in the earlier one;
  their swagger types them as numbers and we follow it — confirm the LOS coerces.

---

## Update — Sambat answered the remaining set (2026-08-31, implemented 2026-09-02)

Their answers, and what we did with each:

| question | their answer | our implementation |
|---|---|---|
| `hidCurrentUserId` | fixed `575` for now (UAT) | `los.hid-current-user-id=575` — **the submit gate is now fully satisfied**; only `los.mock.enabled` stands between the app and a real UAT submission |
| `PC_PaymentChannelName` | their "Payment Channel Code.xlsx"; "use only #12 for now" | `los.const.payment-channel-name=12` (#12 = BANK / HATTHA BANK PLC). Full 78-row sheet kept in-repo at `docs/payment_channel_codes.csv`; per-customer bank mapping later |
| `CustP_EntityFactoryId` | the employer, from their `GET /employer` list; assigned by the LPO after approving the account request, stored in our DB | Mirrored `/employer` into the dictionary as list `EMPLOYER` (326 rows, code = `comId`, admin-only — it is their client list, so the unauthenticated dictionary endpoint refuses it). New `employer_code` column on `pdl_employment_info`; the LPO console's Approve now opens an employer picker and the decision endpoint stores the choice; the mapper sends it |
| `CustP_CBEmploymentStatus` | screen 7's answer, as their id: 1 self-employed / 2 employee / 3 other | Mapped from the stored employment type (`Self-employed`→1, `Employee`→2, else→3; blank stays blank). Payday is always Employee → `2` |
| `CustP_ChildNo`, `MonthlyExpenses`, `LoanUtilizationProject` | keep optional for now | No change — they go out blank/0 |
| unknown place of birth | zero-fill (`00000000`) | POB commune/village always `000000`/`00000000` (never captured); blank POB province/district zero-fill too. A held code still goes out as itself |

Verified on the live preview (loan 18): all of the above render exactly as
specified, and `LosSubmitConfig.missingSettings()` is empty for the first time.

### Still open (non-blocking)
- `CustP_Occupation` / `CustP_BusinessActivity` — numeric codes; needs the
  signup dropdowns driven from `/occupation` (deliberately later).
- Per-customer bank → payment-channel-name mapping (sheet 3), once Sambat moves
  off "only #12".
- Amounts-as-strings coercion question stands.

---

## First real submit attempted — SBF-side failure (2026-09-02)

With the gate fully satisfied, the first two live submissions (loan 19,
customer CIF 70, $9.93/15d) failed on Sambat's side: attempt 1 → HTTP 500
after ~90 s; attempt 2 → no response within our 120 s timeout. Same
host/token served the dictionary endpoints fine minutes before, payload ≈1 MB
and format-identical to their reference. Full evidence for Sambat in
`docs/LOS_Submit_Incident_2026-09-02.md`. Loan 19 remains Draft — resubmit is
one tap once they confirm a fix. Open question for them: does the 500 come
from the custId 70 lookup or the unknown `doneBy`/user context?

---

## Incident RESOLVED — `doneBy` was the cause (2026-09-03)

Resent loan 19 unchanged except `doneBy` → `manith.khut` (a known LOS user)
instead of our app username `010849001`. The 90 s hang / 500 became a clean
**6.6 s** response with a proper `MissingData` list. So the LOS rejects an
unknown `doneBy` as a slow 500/timeout; `custId` 70 was never the issue.

`MissingData` for a payday application is now known (their authoritative
mandatory set beyond what we already send):
`CustP_BusinessActivity`, `CustP_Occupation`, `Doc_ECBCConsentForm`
(+ `_FileName`).

New asks for Sambat (in `docs/LOS_Submit_Incident_2026-09-02.md`): (1) which
LOS `doneBy` our integration should send; (2) `/occupation` code to drive the
dropdown; (3) source of the 8-digit business-activity codes; (4) what artefact
`Doc_ECBCConsentForm` expects (we hold a consent record, not a file).

Diagnostic lever left in code: `los.done-by-override` (empty by default, NOT in
the config gate) — set it to replay a submit under a chosen `doneBy`. Never
default it to a real name: it would file every customer's application under
that user.

---

## FIRST APPLICATION FILED ON THEIR UAT LOS 🎉 (2026-09-03)

Loan 19 (010849001 / CIF 70, $9.93 payday) submitted successfully:
`IsSuccess:"Success"`, **AppRefId 257839**, LOS AppId 8277, ~15 s round trip.
Loan is Submitted, consent stamped (CBC-19-v1-2026-08). The whole chain —
app data → mapper → real POST → their LOS — is proven end-to-end.

Getting there peeled their validator one MissingData at a time (each earlier
"optional" answer overridden by the validator itself):
1. `doneBy` — any *resolvable-shape* string; our numeric usernames 500 their
   server. Constant `los.done-by=KjeyPapa` (their instruction), verified.
2. occupation + business activity — now dictionary-driven: occupation =
   `/occupation` id; business activity = the **8-digit `bizCode`** (their
   reference's 21802005), NOT the row id — the mirror now stores bizCode.
   App captures both through searchable pickers backed by the mirror.
3. `Doc_ECBCConsentForm` — our system-rendered consent PNG (text, name, NID,
   ref, timestamp) was ACCEPTED. Their own reference reuses an arbitrary
   file here, so the rendered record stands until they say otherwise.
4. `LoanUtilizationProject` — MANDATORY. One row: category `23` (adopted from
   their PDL reference — no dictionary endpoint serves this list), unit 1,
   our amounts. `los.const.utilization-category`.
5. `MonthlyExpenses` — MANDATORY. One row, ExpenseType `S`, **amount 0**
   (accepted): we do not capture expenses and will not invent a figure.

### Remaining asks for Sambat
- ~~Confirm what utilization category `23` means~~ **ANSWERED (2026-09-03):
  23 = "General consumption purposes", fixed for payday. Adopted; nothing to
  change — the config already sends it.**
- Confirm the zero-amount expense row is acceptable long-term, or tell us to
  capture expenses at signup.
- Confirm the rendered e-CBC consent PNG satisfies compliance (it reproduces
  the exact in-app consent; no wet signature exists in the V8 flow).
- Provision/nominate the production `doneBy` + `hidCurrentUserId` pair.

### Second application, pure UI path (2026-09-03)
Loan 20 ($19.85) ran the whole journey ON-DEVICE — wizard, statement, CBC,
confirm, the app's own Submit — and filed cleanly: **AppRefId 257843**, LOS
AppId 8278, ~12 s. Observation for both sides' product rules: our backend and
their LOS both allowed a SECOND open application for the same customer
(CIF 70, while 257839 is pending). If one-open-application-per-customer is the
intended payday rule, one of us must enforce it — ask Sambat which side owns it.
