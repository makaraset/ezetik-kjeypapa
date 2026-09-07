# PDL integration points, step by step along the V26 journey

| | |
|---|---|
| **For** | Sambat Finance / Tricube LOS, and our own team |
| **From** | Kjey PAPA (Ezetik) |
| **Date** | 2026-09-07 |
| **Subject** | Every point where the app and Sambat exchange anything, in the order the customer meets them |

`LOS_Integration_API_Spec.md` describes the same integration organised by API.
This one walks the **V26 customer journey** and stops at each point where the two
systems touch, because "what happens at screen 14" is the question that actually
gets asked.

**Legend** — **→ Sambat**: we call them. **← Sambat**: they push to us.
**Ours**: no integration; we own it end to end.

---

## Summary

| | Count |
|---|---|
| Endpoints we call | 10 |
| Webhooks they push | 7 |
| Steps that are entirely ours | 4 |
| Still unbuilt / unanswered | 3 (marked ⚠ below) |

Everything runs over **one** Tricube host, reusing the existing SBF OAuth
session — there is no separate LOS host or credential.

---

## Stage 0 — before any customer: the dictionary

**→ Sambat** `GET /bulk-selection-mini` · `GET /all-address` · `GET /village` ·
`GET /employer`

Their master lists: occupations, business activities, ID types, marital status,
employment codes, the full geo gazetteer, and the employer registry. We mirror
them and store **their codes**, never our own labels.

Refreshed by a scheduled job, `pdl.dictionary.refresh-cron` at 03:30. It ships
**disabled** (`pdl.dictionary.refresh.enabled=false`) — turn it on once the
cadence is agreed, or refresh on demand from the admin console.

> Why it matters: their UAT accepts unknown codes without complaining. A wrong
> occupation code does not bounce, it files a real credit application carrying
> wrong data. Mirroring their lists is what stops us inventing values.

---

## Stage 1 — sign-up (V26 page 1 · V8 screens 4–10)

### 1a. Read the customer's ID card — screen 5

**→ Sambat** `POST /ocr-id-card`

We send the NID photo; they return the extracted fields. **Live since
2026-08-27** (`ocr.mock.enabled=false`).

This is now the *only* reader — our own MRZ parser was removed at your request,
so extraction requires the network and their service being up. A failure leaves
screen 6 blank for manual entry, which is the accepted behaviour.

### 1b. Find an existing customer — screen 6

**→ Sambat** `GET /customer-information/by-idno?idNo=`

Returns the CIF number if this person already banks with SBF. Stored on the user
and self-heals on later profile reads. It is what links a PDL applicant to their
existing core-banking record.

### 1c. Capture and submit the account request — screens 7–10

**Ours.** `POST /api/v1/pdl/account-request`, unauthenticated, one bundle:
credentials, personal / employment / bank sections, and five documents as
base64. Creates a disabled user and fires the existing OTP.

---

## Stage 2 — account approval

**Ours.** Confirmed by Sambat on 2026-08-21: SBF/LOS do **not** approve accounts.
An LPO reviews the request in our admin console and approves or rejects it,
choosing the employer code at the same time. Approval enables login and stamps
the KYC rows as verified.

No integration point. Named here because it is the step people most often assume
belongs to Sambat.

---

## Stage 3 — login and home

**Ours.** A customer with an approved PDL account request lands on the PDL
dashboard as their home; TFF customers are unaffected.

---

## Stage 4 — the loan request wizard (V8 screens 11–18)

### 4a. Product, amount and quote — screens 13–15

**Ours.** Pricing is our own configuration, per Sambat's ruling that we own the
quote: 1.5%/month pro-rated by days, a $3 processing fee and a $1 CBC fee, over
tiers of USD 10–50 or KHR 40k–200k.

### 4b. Settlement account and balance — screen 26 and the profile

**→ Sambat** `GET /customer-balance?accountNo=` · `GET /saving-info-by-cid?cifNo=`

Live balance from core banking (`pdl.settlement.mock=false`), by account number
first and CIF as fallback. A balance that cannot be read shows as unknown rather
than a fabricated zero.

### 4c. CBC consent — screen 17

**Ours to capture, yours to receive.** The wording is served from the backend so
the text the customer reads is the text we hash. Nothing is sent to Sambat at
this point; the consent travels with the application in the next step.

---

## Stage 5 — filing the application

**→ Sambat** `POST /new-loan-application`

The centre of the integration. One request, **102 fields**, with all five
documents inline as base64:

| Document | Source |
|---|---|
| `Doc_NID` | front and back merged into one file |
| `Doc_CustomerProfilePhoto` | signup selfie |
| `Doc_EmploymentCard` | signup capture |
| `Doc_BankStatement` | request-time upload |
| `Doc_ECBCConsentForm` | generated Khmer PDF, your wording |

Their response carries `AppId` and `AppRefId`. **`AppId` is the identifier every
later callback quotes** — we store both, and getting this wrong orphans every
subsequent message.

Three things guard this call:

- **The config gate.** The submit refuses to run while any of their master-list
  codes is unset, rather than guessing a value their UAT would silently accept.
- **Consent is recorded only on success**, so a stamped consent is itself proof
  the application went.
- **Double-submit protection** (below).

### ⚠ 5a. When the reply never arrives

If the request is sent and no answer comes back — a read timeout, a dropped
response, or an HTTP 5xx — the application may or may not exist at Sambat.
Sambat confirmed on 2026-09-05 that guarding this is **ours**. It is built: the
loan is marked outcome-unknown and **will not be resubmitted** until an operator
checks their console and records which way it went.

> **Open with Sambat:** they expose no "does an application exist for this
> customer" lookup, so that reconciliation is manual. A lookup, or
> de-duplication on an idempotency key we supply, would remove the human step.

---

## Stage 6 — the decision (V8 screens 29–31)

**← Sambat** `POST /api/v1/pdl/los/reject` · `/rework` · `/approved` ·
`/product-sync`

They push; we do not poll. `approved` carries the loan terms, the repayment
schedule and the generated documents. `reject` and `rework` carry a reason we
show the customer. All handlers are idempotent and transition-guarded, so a
re-delivery cannot double-apply.

⚠ **Inbound authentication is scaffolded but off** (`pdl.webhook.auth.enabled=false`).
IP allowlist plus a shared secret, awaiting their confirmation of the mechanism.
This must be on before first deployment.

---

## Stage 7 — the customer accepts (V8 screen 32)

**→ Sambat** `POST /customer-accepted`

The customer reads the offer, ticks the T&C gate and confirms; we relay the
decision with their application id.

⚠ Two fields on this call, `smsText` and `trnCode`, are typed as strings in your
swagger and documented nowhere. We send them empty. `smsText` may reach a real
customer, so we will not invent wording — **awaiting your answer.**

A cut-off sweep at 17:00 Asia/Phnom_Penh auto-rejects offers left unconfirmed
and relays an "N" decision.

---

## Stage 8 — bank verification (V26 hand-off)

**Ours today, yours later.** The customer is handed to the bank's mobile app to
authorise the payroll account and auto-debit, and the bank's server calls back
`POST /api/v1/pdl/los/bank-verification`.

We ship a **sample bank app** with a visible SAMPLE watermark, approved by
Sambat, standing in until the real contract exists. Success moves the loan to
`Pending_Bank_Verification`; failure keeps it Accepted with a re-attempt
available until cut-off.

---

## Stage 9 — disbursement (V8 screen 24)

**← Sambat** `POST /api/v1/pdl/los/disbursement`

They push the outcome; we mark the loan Disbursed, store the transaction id and
notify the customer.

⚠ **Open:** whether `/payday-disbursement` is a call *we* make after
disbursement, or purely their own step. Not built pending your answer.

---

## Stage 10 — servicing and closure (V8 screens 35–37)

**← Sambat** `POST /api/v1/pdl/los/loan-update`

Outstanding balance, days past due, last payment, and the per-installment paid
breakdown. Matched by `losApplicationNo` or `loanRefNo`. A fully repaid loan
arrives here as closed, which is what moves it to the Closed tab and stamps the
loan-file end date.

---

## What is still open

| # | Item | With |
|---|---|---|
| 1 | `smsText` / `trnCode` on `/customer-accepted` | Sambat |
| 2 | A duplicate-submission lookup or idempotency key | Sambat |
| 3 | Whether `/payday-disbursement` is ours to call | Sambat |
| 4 | Webhook inbound authentication mechanism | Sambat, then us |
| 5 | The nine consent fields on `newAppRequest` | Sambat *(see `CBC_Consent_Record_Spec.md` §8)* |

## Before first deployment

Flags that are correct for development and wrong for release:

| Property | Committed | Must be |
|---|---|---|
| `los.mock.enabled` | `true` | **`false`** — otherwise the app files nothing with Sambat and returns `LOS-MOCK-nn`, looking entirely successful |
| `pdl.webhook.auth.enabled` | `false` | `true` |
| `pdl.dictionary.refresh.enabled` | `false` | `true`, once the cadence is agreed |
| `ocr.mock.enabled` | `false` ✓ | confirm it is false, not merely absent |

The full list, including the database migrations, is in
`PDL_Findings_Backlog.md`.
