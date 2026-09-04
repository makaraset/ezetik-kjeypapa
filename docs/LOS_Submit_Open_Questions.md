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

---

## KHR submissions FAIL on their LOS — USD succeeds (2026-09-03) — **FIXED by Sambat, verified same day**

> **RESOLVED.** Sambat fixed it and asked us to retry; **KHR filed successfully
> — AppRefId 257861, 10 s** (loan 23, 39,702.23 KHR). Mixed currency is fine
> too: that application carried `MonthlyIncomes` in USD alongside a KHR loan.
> The retry needed care — our first two attempts after their fix still failed,
> but a same-minute **USD control also timed out**, proving a transient outage
> on their side rather than a KHR fault; minutes later USD (257857) and then
> KHR (257861) both filed. Always pair a currency test with a same-window
> control before concluding anything about their LOS.
>
> ~~Still ours to decide: the KHR quote emits fractional riel~~ **DONE
> (2026-09-03): KHR is now quoted in whole riel.** 40,000 KHR → loan 39,702 +
> interest 298, net 23,302; every tier reconciles exactly (principal +
> interest = tier, net + fees = principal). USD keeps cents. Note for
> reconciliation: application **257861 was filed before this fix** and carries
> 39,702.23 KHR.

### Original report (kept for the record)

Third on-device run chose **KHR** (never sent live before). It failed
reproducibly, and a same-loan control proved it is the currency, not an outage:

| attempt | currency / amount | result |
|---|---|---|
| 1 | KHR 39,702.23 | **HTTP 500** after ~60 s |
| 2 | KHR 39,702.23 | **HTTP 500** |
| 3 | KHR 39,702 (whole riel — rules out decimals) | **no response in 120 s** |
| 4 (control) | **USD 19.85, same loan, minutes later** | **filed in 9 s — AppRefId 257852** |

The failure signature (slow 500 / hang, no clean validation error) is the same
one their LOS gave for an unresolvable `doneBy`. The payload diff between the
filed USD application and the failing KHR one is only:
`LR_CBCurrency` USD→KHR, `LR_LoanRequestAmount`, the utilization row's amounts,
and the expense row's currency. Everything else is byte-identical.

**Questions for Sambat**
1. Is **KHR supported for the PDL product** on the LOS at all? If it needs
   enabling per product/currency, please enable it on UAT.
2. If KHR is supported, does a **mixed-currency application** break it? Ours
   sends `MonthlyIncomes` in USD (the customer's salary currency) while the
   loan and expense rows are KHR. If income and loan must share a currency,
   say so and we will convert.
3. Please make the LOS return a validation error rather than a 500/timeout for
   an unsupported currency — a 2-minute hang is indistinguishable from an
   outage on our side.

**Our own follow-up (product decision, not yet changed):** the KHR quote
produces **fractional riel** (39,702.23 KHR). Riel has no subunit in
circulation, so KHR amounts should be rounded — to whole riel at minimum, and
arguably to 100 KHR, the smallest practical note. Not the cause of the failure
(whole riel failed too), but wrong regardless. Meanwhile a customer choosing
KHR waits ~2 minutes and gets "Could not reach Sambat's loan system";
consider hiding the KHR option until Sambat confirms support.

---

## AppId is Sambat's identifier, not AppRefId (2026-09-03)

Sambat: **they identify a LOS application by `AppId`** (4-digit, e.g. 8281);
`AppRefId` (6-digit, e.g. 257861) is not used in general. We had it backwards —
`AppRefId` was what `submitApplication` returned, what we stored as
`losApplicationNo`, what every inbound webhook matched on, and what the app
displayed.

**Latent bug this exposed:** all six webhook handlers resolved the loan via
`losApplicationNo` / `loanRefNo` only. A status update keyed on AppId — which
is how Sambat refers to applications — would have matched nothing and returned
NOT_FOUND silently, so approvals, rejections and disbursements would never have
landed. Fixed: the resolver now tries `losApplicationNo`, then `losAppId`, then
`loanRefNo`, so whichever id they send resolves. Regression test added.

Both ids are stored per loan (`los_app_id`, `los_application_no`) — filed so
far: 8277/257839, 8278/257843, 8279/257852, 8280/257857, 8281/257861 (KHR).

**Please confirm:** which field name will the webhook payload carry the AppId
in? We match defensively on both columns today, but if you send it as
something other than `losApplicationNo` we should map it explicitly.

---

## The envelope `appId` IS the LOS AppId — retries were duplicating (2026-09-03)

Sambat: the request envelope's `"appId": 0` is their **LOS AppId**. So `0`
means "create an application", and sending an existing AppId updates that one.

We were sending `0` on **every** submit, including retries — so a loan that
already had an AppId (loan 23 held 8281) would, on resubmit, have filed a
SECOND credit application for the same loan, with its own CBC enquiry, under
the customer's name. Fixed: the mapper now sends the loan's stored
`losAppId` when it has one, and only falls back to `los.app-id=0` for a first
submit. Verified live — loan 23's envelope now carries `appId: 8281`.

### Please check for orphaned applications on your side
Two of our earlier attempts **timed out at 120 s with no response**, so we
cannot know whether your LOS created an application before we gave up:

- 2026-09-03 ~15:05, customer CIF 70 (loan 21 flow, KHR then USD control)
- the 12:07 attempt on the same customer (loan 19 flow)

Our records hold exactly five AppIds for CIF 70: **8277, 8278, 8279, 8280,
8281**. If your console shows any others for that customer, they are orphans
created by those timed-out requests and should be cancelled — please confirm
the full list so we can reconcile.

---

## Sambat answered the full open list — implemented (2026-09-03)

| # | Their answer | What changed |
|---|---|---|
| A1 | `doneBy="KjeyPapa"`, `hidCurrentUserId=575` **for production** | Comments updated; values already live |
| A2 | The webhook field is **`appId`** | `appId` added to all four callback payloads and preferred when resolving the loan |
| A3 | Send the stored AppId on retry; they will restrict duplicates LOS-side | Already implemented |
| A4 | Leave the timed-out orphans on UAT | No action |
| A5 | Zero-amount `MonthlyExpenses` is fine long term — "micro loan doesn't need too much info" | Kept; comment no longer calls it provisional |
| A6 | The utilization row IS the loan purpose: 23 = General consumption, unit 1, price = loan | Confirmed as built |
| A7 | `CustP_ChildNo` / `LR_TotalBudget*` stay optional | No action |
| A8 | Consent **must be in Khmer**; compliance sign-off later | Consent form now renders in Khmer |
| A9 | Send **both ID sides merged into one image** | `LosDocumentAssembler.mergedNid` stacks front+back |
| A10 | Max request size **50 MB** | Guard refuses locally above 50 MB with an actionable message |
| A11/A18 | Multiple open applications allowed for now | No block added |
| A12 | Payment channel is **#31 CAMBODIAN PUBLIC BANK PLC**, not #12 | `los.const.payment-channel-name` — and see below: it takes the NAME, not the id |
| A13 | Numbers (not strings) accepted | Closed |
| A14 | They will fix the 500/timeout-on-bad-input behaviour | Closed our side |
| A19 | Round KHR to **100 riel** (Makara) | Quote rounds to the nearest 100 |

Verified live after the changes: **AppId 8286 / AppRefId 257912** (USD) and
**AppId 8287 / AppRefId 257916** (KHR 39,700) — both carrying the merged NID,
the Khmer consent form and channel 31.

### Note on their UAT stability
Between 22:54 and 23:04 their LOS returned 500s and 120 s timeouts for **both**
currencies, then recovered and filed in 9 s with no change on our side — the
third such window today. Any test against their UAT needs a same-window
control before a conclusion is drawn.

### Still open
- **A8 compliance sign-off** on the Khmer consent form, and the final EN+KM
  legal text (A15) — our Khmer wording is an interim translation pending
  native review.
- A16 tiers / rejection codes, A17 real bank-app contract + webhook auth
  credentials, A20 UAT server setup.

---

## Acceptance relay implemented — and a live-mode crash fixed (2026-09-04)

`LosProvider.sendDecision` **threw `UnsupportedOperationException` whenever
`los.mock.enabled=false`**. Since submitting is now live, an approved customer
tapping Accept would have hit that: the acceptance never reached Sambat, so the
application would sit approved and never disburse. Implemented via
`POST /customer-accepted`, keyed by their **AppId** (their swagger types the
field `appId`, int32 — the same identifier as everywhere else).

The interface now takes the loan rather than a reference string, because the
AppRefId we previously passed is not what that endpoint accepts.

### Questions this raises for Sambat
1. **`smsText` and `trnCode`** on `/customer-accepted` — your swagger types
   both as strings and documents neither. We send them **empty** rather than
   invent content, because `smsText` looks like it may be sent to the customer
   and we will not put invented wording in front of a real person. What belongs
   in each?
2. **No decline endpoint.** Your API has `/customer-accepted` and nothing for
   "the customer declined" or "the offer expired at cut-off". Today those close
   on our side only and your LOS is never told. Is there an endpoint we have
   missed, or should the application simply lapse on your side?
3. **`/payday-disbursement`** — is that ours to call after disbursement, or
   yours to call us? We currently receive disbursement on our own webhook
   (`/pdl/los/disbursement`) and do not call theirs.

---

## `PC_PaymentChannelName` takes the bank NAME (2026-09-04)

Sambat: "submit only name", e.g. `CAMBODIAN PUBLIC BANK PLC`. We had been
sending the sheet row id (`31`). Changed and filed live — AppId 8290 /
AppRefId 257937.

Worth recording because **their own PDL reference payload sends `"31"`**, so
the reference now contradicts the instruction on this field. Anyone diffing
against it later will be tempted to put the number back; the config comment and
a test both say otherwise.

Note for when other banks are added: our stored bank names (e.g. "Campu Bank")
are NOT their canonical spellings, so they cannot be sent as-is. The mapping
will come from the same payment-channel sheet, which carries the canonical
names — `docs/payment_channel_codes.csv`.

---

## Customer can view what we filed; App ID is the LOS one (2026-09-04)

Sambat, against V8 screen 18: **"App ID is from LOS"**, and **"we allow customer
to view all the documents that we post to LOS"**.

- The submitted screen's **App ID** now shows Sambat's LOS AppId (e.g. 8293).
  It only exists once the application is filed, so before submission the screen
  still shows our own application number.
- **Attachment → View** now lists every document filed, not just the bank
  statement: ID card (both sides, merged as sent), photo, employment card, bank
  statement, and the e-CBC consent form.

New endpoints: `GET /pdl/{id}/los-documents` (the slots) and
`GET /pdl/{id}/los-document/{slot}` (one document), both ownership-checked.
Each is produced by the **same assembler the submission uses**, so what the
customer sees is what was sent, by construction rather than by duplication.

Two details worth knowing:
- The consent is served as an image while the wire format is PDF — identical
  content, and it displays without a PDF reader in the app.
- Re-rendering the consent now uses the **stamped** consent time rather than
  the current time, so a customer opening it months later sees the moment they
  consented, not the moment they looked.

---

## Final CBC consent wording adopted (2026-09-04)

Sambat sent `CBC Consent for Mobile App _ KH_Final.docx`. Now live in both
places: the app's CBC page and the PDF filed as `Doc_ECBCConsentForm`, from one
source — `src/main/resources/cbc/consent-km.txt` — so the two cannot drift.

`pdl.cbc.text-version` bumped **v1-2026-08 → v2-2026-09**. Consents filed
before today keep pointing at v1 and its hash, which is exactly why the record
carries both a version and a hash.

### ~~⚠️ Their final document contains a corrupted fragment~~ CORRECTED same day

Sambat sent clean text; the stray fragment is gone and the wording now in use
is theirs verbatim. Their form footer — `Form _ CBC 01 _ CBC Consent Form For
Borrowers _ V2 _ 09122025` — is printed on the rendered form and shown on the
app's consent page. Confirmed there is **no English version**: the consent is
Khmer only, so the English block has been removed from the form and the app
shows the Khmer legal text in both locales (a customer must agree to the real
text, never to an English paraphrase of ours while we file the Khmer one).

Consents now re-render at the **version they were filed under** —
`cbc/consent-km-<version>.txt` — with the stored reference, so a document shown
back to a customer matches the record and hash filed against it. Verified: loan
29 re-renders as v1-2026-08 with its old wording, loan 30 (filed after the
change, AppId 8300) as v2-2026-09 with the corrected text.

<details><summary>The original problem, for the record</summary>

Paragraph 2, inside the sentence giving CBC's and Sambat's registered
addresses, reads:

> …រាជធានីភ្នំពេញ **នអាសយដ្ឋាននៅផ្ទះលេខ 228ជេធ. visit the r is not reachable
> via phone call,** ទទួលខុសត្រូវលើការប្រមូល…

It is in the .docx itself — not a tracked change, not a comment, and not an
extraction artefact (checked all three). It looks like text pasted in by
accident during editing.

We have shipped the wording **exactly as supplied**, because silently editing
a legal consent is not ours to do. **Please ask Sambat for a corrected file.**
The moment it arrives it is a one-file replacement plus a version bump; the
fragment is currently visible to customers on the consent page and printed on
every consent PDF filed with them.

</details>

---

## Consent form follows Sambat's own template (2026-09-04)

Rebuilt to `CBCConsentforMobileApp_KH_Final`: A4, **Khmer OS Content**, their
page geometry read from the .docx (0.59in sides, 0.30in top, 0.49in bottom),
12pt bold underlined heading, 11pt justified body, the borrower block in their
label order, and their form reference bottom-right at 7.5pt.

Two things worth knowing about the result:

- **Not every build of Khmer OS Content works.** The font ships in several
  builds and they are not equivalent. The "v6.00 2010" one **renders Khmer
  incorrectly under Java** — subscripts stop stacking, vowels move — and it
  fails *silently*, which is how a first attempt shipped mis-shaped Khmer. The
  **2007 v1.10** build shapes correctly and is the one bundled. Its bold
  counterpart is broken the same way, so bold is synthesised from the regular.
  A test now asserts shaping actually happens (a coeng cluster must collapse to
  fewer glyphs than characters) rather than merely checking the file name.
- Coverage also varies by build — the 2010 one has no Latin letters at all — so
  text is drawn run by run against `canDisplay` with a fallback face.
- **Justification is capped.** Khmer puts spaces at phrase boundaries rather
  than between every word, so a line may have only two or three gaps to absorb
  the slack; stretching them tore holes in the paragraph. Lines needing more
  than about four spaces' worth are left ragged. Word achieves an even
  justification by also stretching between characters, which Java2D will not do
  for us — if Sambat need exact justification, that is the gap to close.
