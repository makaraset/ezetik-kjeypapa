# Kjey PAPA (Ezetik) — Payday Loan Integration: Clarification Request

**To:** Sambat Finance — LOS Integration Lead
**From:** Kjey PAPA (Ezetik) — Mobile Application Team
**Date:** 2026-07-03
**Subject:** Payday Loan integration — items to confirm before finalizing the real LOS submit + disbursement

---

We have implemented the Payday Loan (PDL) flow end-to-end to match the **Customer's Journey V21** workflow — on-boarding/KYC document capture, the e-loan-contract acceptance with reminders and a daily cut-off, and the disbursement notification. Before we switch off our mock LOS provider and go live against the real `newAppRequest` submit and the disbursement flow, we need your confirmation on the items below.

The first item — the **Bank Mobile App redirect for disbursement consent** — currently **blocks an entire step of the journey** on our side, so we have placed it first. For the remaining items we have proposed a default so you can simply confirm if we have it right. Questions are individually numbered so you can reply inline.

A short **response by 2026-07-17** would keep us on schedule; **Item 1 sooner if possible**, as it blocks our build. We are also happy to walk through any of this on a short call.

---

## 1. Bank Mobile App Redirect for Disbursement Consent — **BLOCKER**

**Background:** V21 specifies that after the applicant accepts the e-contract, we **redirect to the Bank Mobile App** to verify the account and consent to disbursement + auto-debit; the bank verifies the loan reference and details, then LOS finalises disbursement and the status becomes `Disbursed`. This step is **not yet built on our side** because the bank-app launch and return contract are undefined. Today our accept flow ends at status `Accepted` with no hand-off, and we separately receive a disbursement notification that sets the loan to `Disbursed`.

**What we need from you:**

- **Q1.1 — Which bank app** do we redirect to, and is it **always the same app** or does it **vary by the customer's settlement bank**? If variable, we need the per-bank list.
- **Q1.2 — Launch mechanism:** iOS — universal link (`https://`) or custom URL scheme? Android — App Link (`https://`), Intent URI, or Play Store URI?
- **Q1.3 — Launch parameters:** which fields must we pass to identify the loan/customer (e.g. loan reference, applicant phone/NID, amount, currency, tenor)? Should we obtain a **one-time token** from LOS at accept time to prevent replay/tampering?
- **Q1.4 — Return of the result:** do you prefer a **server-to-server webhook to our backend** (our preference, for reliability) or a **deep-link callback back into our app**? If a deep-link callback, we will need to **register our return URL/scheme with you** — please tell us what you need from us to whitelist it.
- **Q1.5 — Failure & re-attempt:** on a FAILED verification, does the loan wait for re-attempt or revert? Is there a **retry limit**? Should temporary (network) vs. permanent (account closed) failures behave differently?
- **Q1.6 — Abandonment / timeout:** if the applicant opens the bank app but never completes or never returns, **how long does LOS hold the loan**, does it **auto-expire**, and do we receive any notification on expiry? What status should our app show meanwhile?
- **Q1.7 — Auto-debit mandate:** the consent bundles disbursement **and** auto-debit — which account is debited, is the mandate **confirmed back to us**, and can the customer revoke it?
- **Q1.8 — Boundary with the existing disbursement notification:** is this new bank-verification result **separate from, or the same as**, the disbursement notification we already receive? Which one is authoritative for the disbursement transaction id and the `Disbursed` transition?
- **Q1.9 — In-between status:** does LOS expose a status value for the *awaiting-bank-consent* state (via product-sync / loan-update) so our vocabulary matches yours? (We would otherwise introduce a local `Pending_Bank_Verification` state.)

**Proposed callback strawman — please amend:**

```
POST /api/v1/pdl/los/bank-verification
{
  loanRefNo:          String,      // durable correlation key (see Item 5)
  losApplicationNo?:  String,
  verificationStatus: "SUCCESS" | "FAILED",
  disbursementTxnId?: String,
  failureReason?:     String,      // machine code, see Item 4
  verificationDate:   String       // ISO-8601 with offset (see Item 5)
}
// Our endpoint acks with HTTP 200 echoing loanRefNo; please confirm your
// retry / at-least-once behaviour and a dedupe key so we can be idempotent.
```

**Note:** we **cannot proceed on an assumption** for this item — it is blocked pending your input. The strawman above is offered only as a starting point for discussion, not a default we will adopt silently.

---

## 2. Authoritative Document Set for the Application (`newAppRequest`)

**Background:** Appendix 2 lists **5 document fields** for `newAppRequest` — `Doc_ECBCConsentForm`, `Doc_CustomerProfilePhoto`, `Doc_NID`, `Doc_EmploymentCard`, `Doc_BankStatement` (all Base64-inline). V21 describes a **lean 3-document** on-boarding (NID photo, selfie, bank statement), treats **CBC as a consent, not a document**, and **removed the Employment-Card upload**. Our app follows V21: it captures NID (front + back), a selfie, and a bank statement; treats E-CBC as a **consent checkbox** (not a form upload); and validates only those three at submit.

**What we need from you:**

- **Q2.1** — Are **`Doc_EmploymentCard`** and **`Doc_ECBCConsentForm`** **mandatory** in the real `newAppRequest`, or optional?
- **Q2.2** — Is **`Doc_ECBCConsentForm`** an **uploaded document** or represented by a **consent flag**? If a form is required, is it a pre-generated PDF or a user-signed upload?
- **Q2.3** — Is **`Doc_NID`** a **single** photo, or do you accept **separate front + back**? If single, should we merge front+back as a **two-page PDF** or a **combined image**, and who owns the merge?
- **Q2.4** — If both extra docs are mandatory, **which contract is canonical** — Appendix 2's 5-field set or V21's 3-doc model — or must both coexist?
- **Q2.5 — Transport constraints** for the Base64-inline documents: **max size per document** and **max total request size**, accepted **MIME types**, and any required **minimum/maximum resolution or compression**. (We store images as references and will inline them at submit — please confirm that is expected.)

**Our assumption if we don't hear otherwise:** `Doc_EmploymentCard` and `Doc_ECBCConsentForm` are **optional**; E-CBC is a **consent flag only**; **`Doc_NID` is a single photo**; **V21 is the canonical customer journey**; and our three captured images map 1:1 to `Doc_NID`, `Doc_CustomerProfilePhoto`, `Doc_BankStatement`.

---

## 3. E-Loan Contract Acceptance — Reminders + Daily Cut-off Auto-Reject

**Background:** V21 states the applicant must confirm the E-Loan Contract, **reminded every 30 minutes, before the daily cut-off** (else auto-reject), and this is **ours to build**. We have implemented it: 30-minute reminders, a **5:00 PM** cut-off with a **30-minute grace** window, an automatic reject decision relayed to LOS, and a user notification. V21 also mentions a **"Bank cut-off 5:15 PM"** that differs from the 5 PM acceptance language.

**What we need from you:**

- **Q3.1 — Cut-off & timezone:** is the authoritative acceptance cut-off **5:00 PM** or **5:15 PM**, and is the timezone **Asia/Phnom_Penh**? Do **5:00 PM and 5:15 PM govern different things** — e.g. 5:00 PM = the applicant's acceptance deadline and 5:15 PM = the bank's same-day settlement/disbursement window — rather than one cut-off plus a buffer?
- **Q3.2 — Grace policy:** is a **30-minute** grace window correct (a loan approved at 4:35 PM survives the 5 PM sweep; one approved at 4:20 PM does not)?
- **Q3.3 — Reminder cadence / hours:** is **every 30 minutes, 8 AM–5 PM** the right window?
- **Q3.4 — Business calendar:** does the cut-off / auto-reject observe **Cambodian business days and public holidays**, and how should **after-hours or weekend** approvals be treated (e.g. a Friday-evening approval)?
- **Q3.5 — Double-reject risk:** does **LOS also enforce its own** acceptance cut-off / auto-reject? If yes, we must coordinate — and we would need the **exact status code LOS emits on its own timeout auto-reject** so we can detect it and suppress our duplicate reject.
- **Q3.6 — Timestamp basis:** what **timezone and format** is the approval timestamp we receive from LOS? We compute the cut-off from it, so a UTC-vs-local mismatch would silently mis-reject valid loans.

**Our assumption if we don't hear otherwise:** **5:00 PM Asia/Phnom_Penh** cut-off (5:15 PM = a bank-side settlement buffer); **30-minute** grace; reminders **every 30 min, 8 AM–5 PM**; the cut-off runs **every calendar day**; and **our backend solely owns** the cut-off (if LOS enforces one too, we will disable ours and defer to LOS).

---

## 4. Bank Payroll-Account Verification Failure — Codes & Re-attempt Semantics

**Background:** V21 and Appendix 2 require the bank to verify the payroll account (not-found / dormant / closed → reject) **before LOS processing and again at disbursement**. On our side these arrive via the existing **reject notification** (`POST /api/v1/pdl/los/reject`). We currently map three **assumed** codes — `R-BANK`, `INVALID_ACCOUNT`, `BANK_VERIFY_FAILED` — to one customer message ("Bank account could not be verified"), and the app offers a **"Re-attempt"** action.

**What we need from you:**

- **Q4.1 — Authoritative code(s):** which exact code(s) does LOS/Bank send for a bank-account-verification failure? Is it a **generic reject with a sub-code**, or a **distinct status**?
- **Q4.2 — Full enumeration:** please provide the **complete, stable list of reject/rework status codes** with their meanings and canonical messages, so we can map each to the correct customer-facing text. We would prefer to key our UI on the **machine code**, not the free-text message.
- **Q4.3 — Message ownership / localization:** is the `message` field **English-only** (so we own the Khmer/English translation from the code), or localized by LOS?
- **Q4.4 — Pre-processing vs disbursement check:** is the payroll-account verification **before LOS** and the disbursement-time bank check the **same gate or two different gates**, and do they use the same code(s)?
- **Q4.5 — Re-attempt scope:** what must the applicant **change** on re-attempt — a **new settlement account number**, an **updated bank statement**, or nothing? (A closed/dormant account will fail again if only the loan amount is re-entered.) Should re-attempt **re-submit the same application** (same reference) or **create a new one**, and is there a **maximum re-attempt count / cool-down**?

**Our assumption if we don't hear otherwise:** a **generic reject** notification carrying one of the codes above; the `message` is **English-only** (we localize from codes); and **re-attempt starts a new application** where the applicant can correct the failing bank account.

---

## 5. Cross-cutting items needed before go-live

These apply across all of the above and are needed to move off the mock and run a real end-to-end test:

- **Q5.1 — Environment & credentials:** the real **LOS base URL(s)**, a **UAT/sandbox vs. production** environment, and the **API credentials/keys** to replace our mock provider.
- **Q5.2 — Webhook authentication (both directions):** how does LOS **authenticate its calls to us** (HMAC signature + header, mTLS, or IP allowlist), how are **secrets provisioned/rotated**, and what is the **source IP range to allowlist**? How do we authenticate **our** outbound calls to LOS?
- **Q5.3 — Correlation identifier:** please confirm the **single durable key** that will appear on **every** callback (loan reference vs. application number vs. app-ref id), its **lifetime**, and whether it **changes on re-attempt** — so we can reliably match a callback to a loan.
- **Q5.4 — Timestamp format:** the **timezone and wire format** (recommend ISO-8601 with explicit offset) for **all** timestamps LOS pushes.
- **Q5.5 — Amount & currency:** the expected **amount format** (decimal vs. minor units), the **supported currencies** (USD / KHR), and any **min/max** the submit and bank app enforce.
- **Q5.6 — Named contact & UAT window:** a **named owner/contact** on your side for these items, and a proposed **joint UAT window / target go-live date** so we can sequence switching off the mock.

---

## Also outstanding (previously raised — kept brief per your "leave CBC for now")

Real `newAppRequest` submit still depends on items from our earlier note (`LOS_Remaining_Items_to_Fill.md` / `LOS_Appendix2_Review_and_Gaps.md`): the **CBC code master lists** for the coded fields, **which of the `newAppRequest` fields are mandatory** for PDL, how to populate the **financial-assessment sections** (monthly incomes/expenses, loan-utilization, budgets) that our app does not currently collect, and how the **`MissingData`** response should be surfaced to the applicant. We are not asking you to action these now — flagging only so they are not lost, as they remain the gating dependency for the real submit.

---

## Summary

| # | Topic | Priority | Blocked until answered |
|---|-------|----------|------------------------|
| 1 | Bank Mobile App redirect + disbursement-consent contract | **Blocker** | The entire post-accept → disbursement step (no code yet) |
| 2 | Authoritative document set + transport limits | High | Real `newAppRequest` document mapping |
| 3 | Acceptance cut-off time / grace / calendar / ownership | High | Correctness of auto-reject; avoiding double-reject |
| 4 | Bank-verification reject codes + re-attempt semantics | High | Correct reject messaging + a re-attempt that fixes the account |
| 5 | Environment, webhook auth, correlation id, formats, contact | High | Any real end-to-end test / go-live |
| — | CBC master lists + mandatory fields (previously raised) | Medium | Real submit field population (not actioning now) |

Thank you — happy to jump on a short call if that is faster for any of these.

*Prepared by the Kjey PAPA (Ezetik) mobile team; cross-references the Customer's Journey V21 workflow and LOS Appendix 2.*
