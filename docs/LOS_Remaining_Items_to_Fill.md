# Sambat LOS (PDL) — Remaining Items to Confirm

> **How to use:** fill the blanks (`____`). Direction key:
> **→ us** = Tricube/LOS **pushes** this to the Kjey PAPA backend (we already host the receiver — we only need the JSON payload you will send).
> **← us** = a LOS endpoint on Tricube that **we will call**.
> If a request/response is large, just attach a sample JSON and write *"see attached"*.

---

## #2 — Remaining API specifications

### 2.1 Loan Product / catalog  (direction: ____ )
- Trigger / when sent: `____`
- Endpoint & method: `____`
- Fields or sample JSON: `____`

### 2.2 Reject notification  (→ us, push)
- Trigger: `____`
- Payload you will POST to us (fields / sample JSON): `____`
- Status/return codes used: `____`

### 2.3 Rework / return-for-correction  (→ us, push)
- Applies to PDL? (Y/N): `____`  · Codes: `____`
- Payload (fields / sample): `____`

### 2.4 Approved  (→ us, push)  — includes generated documents
- Payload (fields / sample): `____`
- Generated **loan application form** delivered as: (base64 / URL / id) `____`
- Generated **loan contract** delivered as: `____`
- **Repayment schedule** delivered as: (JSON rows / PDF / both) `____`
- Loan terms included (loan ref no, tenor, settlement account, currency, etc.): `____`

### 2.5 Customer Accept / Reject decision  (← us — your endpoint we call)
- Endpoint & method: `____`
- Request fields (decision Y/N, signed contract, …): `____`
- How we send the **signed e-contract**: (base64 / URL / id) `____`
- Response: `____`

### 2.6 Disbursement status  (→ us, push)
- Trigger (auto on accept / back-office step): `____`
- Payload (txn id, amount, date, status, failure reason): `____`

### 2.7 Repayment / loan status update  (→ us, push)
- Trigger (each repayment / daily batch): `____`
- Payload (outstanding, overdue, days past due, last paid, installment paid breakdown): `____`

---

## #3 — Access & environments

- **UAT base URL:** `https://tricube-uat.sambatfinance.com:6443/api`  → confirm: `____`
- **Outbound auth:** reuse the existing SBF/Tricube OAuth for LOS calls? (Y/N): `____`  · extra scope if any: `____`
- **Test applicant** for an end-to-end dry run (CIF / sample data / credentials): `____`
- **Inbound webhook auth** (how the backend verifies a call is really from Tricube):
  - Signature header & algorithm (e.g. HMAC-SHA256): `____`
  - Shared secret / API key: `____`  ·  Tricube **source IP(s)** for allowlist: `____`
- **Production Tricube host/port:** `____`
- **Technical contact** (name / email): `____`  ·  **Target go-live date:** `____`

---
*Return this with any sample JSON files. We will map our data to your fields and enable the live integration.*
