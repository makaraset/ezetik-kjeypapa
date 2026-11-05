# Software Requirements Specification (SRS) & API Specification
## Sambat LOS (TurnKey Lender) ⇄ Kjey PAPA — Payday Loan (PDL) Integration

| | |
|---|---|
| **Document title** | LOS ⇄ Kjey PAPA PDL Integration — SRS & API Specification |
| **Version** | 1.0 (Draft for LOS review) |
| **Status** | For review by LOS / TurnKey Lender and related parties |
| **Prepared by** | Kjey PAPA / ezetik engineering |
| **Audience** | Sambat LOS / TurnKey Lender integration team; Sambat Finance; Campu Bank (disbursement); CBC (credit bureau) where relevant |
| **Product in scope** | Payday Loan (PDL). Salary loan products MSIL / PL are noted where they differ but are out of scope for v1. |

> **How to read this document.** It states the integration **Kjey PAPA requires** so that LOS can confirm, adjust, or map it to LOS's own API. Where a value, mechanism, or field is not yet agreed it is marked **`TBD — LOS to confirm`**. Endpoints, field names and JSON shapes that Kjey PAPA proposes are **proposals**; LOS's actual contract takes precedence once provided, and this document will be revised to match. Items already **implemented** on the Kjey PAPA side are marked **`[Kjey PAPA: IMPLEMENTED]`** so LOS knows our receiver is ready.

---

## 1. Introduction

### 1.1 Purpose
Define the technical requirements and the API contract for integrating the **Kjey PAPA** mobile lending platform with the **Sambat LOS (TurnKey Lender)** decisioning system for the **Payday Loan (PDL)** product. LOS owns credit decisioning, document generation (loan application form, loan contract, repayment schedule) and disbursement orchestration; Kjey PAPA owns the customer mobile experience and is the system of engagement.

### 1.2 Scope
- Customer registration / KYC capture (personal, employment, bank, mandatory documents) in the Kjey PAPA app.
- Submission of a PDL application from Kjey PAPA to LOS.
- LOS decision lifecycle (Loan Processing Officer → Branch Manager → HQ Credit Committee) and the resulting notifications (reject / rework / approved).
- Customer acceptance / rejection of an approved offer (including the digitally signed e-contract).
- Disbursement (LOS-orchestrated, Campu Bank direct-debit) status notification.
- Ongoing loan & repayment status synchronisation (outstanding balance, installments paid) for the customer's "My Loan / Payment Record" view.

Out of scope for v1: MSIL and PL salary products; general dashboard/back-office enhancements.

### 1.3 Definitions & Acronyms

| Term | Meaning |
|---|---|
| **LOS** | Loan Origination System — Sambat LOS, powered by TurnKey Lender. The decisioning system of record. |
| **Kjey PAPA app** | The customer-facing Flutter mobile application. |
| **Kjey PAPA backend** | The `ezetik-kjeypapa` Spring Boot service. It is the **only** party that talks to LOS (the mobile app never calls LOS directly). |
| **PDL** | Payday Loan. |
| **CBC** | Credit Bureau Cambodia. |
| **E‑CBC consent** | The customer's electronic consent for a credit-bureau enquiry. |
| **LPO / BM / HQC** | Loan Processing Officer / Branch Manager / HQ Credit Committee — the LOS approval roles. |
| **Application No / `losApplicationNo`** | The unique LOS-side identifier for a submitted application. |
| **Webhook** | An HTTPS callback from LOS to the Kjey PAPA backend. |

### 1.4 References
- Kjey PAPA PDL Business Requirement Specification (BRS), Sambat 2026 CR.
- Salary Loan Products CR (MSIL/MD).
- Kjey PAPA PDL implementation plan and architecture docs (internal).

### 1.5 Parties & Responsibilities

| Party | Responsibility |
|---|---|
| **Kjey PAPA app** | KYC & document capture; display application/loan status; capture accept/reject + signed e-contract. |
| **Kjey PAPA backend** | LOS **API client** (submit application, relay accept/reject); LOS **webhook receiver** (product sync, reject, rework, approved, disbursement, repayment); persistence; customer notifications. Holds all LOS secrets. |
| **LOS / TurnKey** | Credit decisioning; generate loan form, contract, repayment schedule; orchestrate disbursement; push status notifications to the backend. |
| **Campu Bank** | Disbursement and direct-debit repayment (via LOS). |
| **CBC** | Credit bureau enquiry (via LOS, under the customer's E-CBC consent). |

---

## 2. Integration Overview

### 2.1 Topology

```
   ┌─────────────────┐      HTTPS/JSON      ┌──────────────────────┐      HTTPS/JSON      ┌──────────────┐
   │  Kjey PAPA app  │  ◄──────────────►   │  Kjey PAPA backend   │  ◄──────────────►   │  Sambat LOS  │
   │  (mobile, Dart) │   (existing API)     │  (ezetik-kjeypapa)   │  (THIS SPEC)         │  (TurnKey)   │
   └─────────────────┘                      └──────────────────────┘                      └──────┬───────┘
                                                                                                  │
                                                                              orchestrates ▼      │ ▼ enquiry
                                                                          ┌──────────────┐   ┌─────────┐
                                                                          │  Campu Bank  │   │   CBC   │
                                                                          └──────────────┘   └─────────┘
```

**Hosting note.** "Sambat LOS" above is the **LOS API hosted on the Tricube (Sambat) server** — the same external host that already serves the SBF core-banking API. From the Kjey PAPA backend's perspective there is a **single external server and a single OAuth** (see §2.3, §3.2.1); the LOS endpoints sit alongside the SBF endpoints under the same `/api` base.

Two directions are in scope for this document:

- **Backend → LOS** — outbound calls Kjey PAPA makes to Tricube/LOS (submit application; relay customer decision). The **submit** call is defined in Appendix 2 (`POST /api/new-loan-application`) — see `LOS_Appendix2_Review_and_Gaps.md`.
- **LOS → Backend** — status notifications (product sync; reject; rework; approved; disbursement; repayment). **Confirmed: PUSH** — Tricube/LOS calls the Kjey PAPA backend webhooks whenever it performs an action (no polling). A1–A4 receivers are implemented; A5/A6 to be added. Inbound webhook authentication is the remaining item to agree (§10.3).

### 2.2 End-to-end PDL lifecycle

```
1.  (LOS → Backend)  Product sync — LOS publishes the active PDL product configuration.
2.  (App)            Customer registers / completes profile: personal, employment, bank,
                     and the 5 mandatory documents; grants E-CBC consent.
3.  (Backend → LOS)  Submit new application  ──►  LOS returns the Application No.
4.  (LOS internal)   Decisioning: LPO → BM → HQ Credit Committee. CBC enquiry performed.
5.  (LOS → Backend)  One of:
        • Reject     (codes R-LPO / R-AO)               → status Rejected
        • Rework     (PDL: treated as reject; MSIL/PL: true rework RW-LPO / RW-AO)
        • Approved   (+ loan form, e-contract, repayment schedule, loan terms)
6.  (App)            On Approved, customer Accepts (signs e-contract) or Rejects/lets expire.
7.  (Backend → LOS)  Relay decision: "Y" + signed contract  /  "N".
8.  (LOS → Campu)    On accept, LOS starts disbursement (direct-debit set-up).
9.  (LOS → Backend)  Disbursement status update (txn id, disbursed date).
10. (LOS → Backend)  Ongoing repayment / loan status updates (outstanding, installments paid,
                     days past due) for the customer's My Loan / Payment Record view.
```

### 2.3 Environments

**Single external server.** The LOS API is hosted on the **same Tricube (Sambat) server** that already serves the Sambat Finance core-banking API. There is therefore **one** external integration host, and the LOS endpoints sit alongside the existing SBF endpoints under the same `/api` base, secured by the **same OAuth** the backend already uses for SBF (§3.2.1). No separate LOS host or separate credentials are required.

| Environment | Kjey PAPA backend base URL | Tricube (SBF + LOS) base |
|---|---|---|
| UAT | `https://<kjeypapa-uat-host>/api/v1` | `https://tricube-uat.sambatfinance.com:6443/api` (token: `…:4443/oauth/token`) |
| Production | `https://<kjeypapa-prod-host>/api/v1` | `TBD — Tricube production host/port` |

The LOS "New Loan Application" call (Appendix 2) is therefore `POST https://tricube-uat.sambatfinance.com:6443/api/new-loan-application` (path per Appendix 2; confirm full base/port). **Still required from LOS:** the CBC code master lists, the remaining API appendices (§4 Groups A1–A6 / B2), a test applicant, and confirmation of whether status notifications are pushed or polled (§4 note).

---

## 3. General API Conventions

### 3.1 Transport & format
- **HTTPS only** (TLS 1.2+). Plain HTTP is not permitted in any environment.
- **JSON** request/response bodies, **UTF-8** encoded (must correctly carry Khmer text).
- `Content-Type: application/json` unless a file-transfer mechanism dictates otherwise (§7).

### 3.2 Authentication

**3.2.1 Backend → LOS (outbound) — reuses the existing SBF/Tricube OAuth.** Because LOS is on the same Tricube server as SBF core-banking (§2.3), outbound LOS calls reuse the **existing** OAuth the backend already implements for SBF (`SbfAuthorization`): OAuth 2.0 **password grant** to `…:4443/oauth/token` with a Basic client-credentials `Authorization` header; the bearer token is cached and auto-refreshed and is sent on each LOS API call. **No new token endpoint, grant, or credentials are needed** unless LOS requires a distinct scope. `Confirm with Sambat`: whether the LOS endpoints accept the same token/scopes as the SBF endpoints (expected: yes).

**3.2.2 LOS → Backend (inbound webhooks).** To prove a webhook genuinely originates from LOS, Kjey PAPA requires **both**:
1. A **shared-secret HMAC signature** of the raw request body, sent in a header — proposed `X-LOS-Signature: sha256=<hex hmac>` (HMAC-SHA256 over the exact bytes of the body, using a secret exchanged out of band); **and**
2. A source **IP allowlist** (LOS to provide its egress IP ranges).
Optionally a static bearer/API key header in addition.
`TBD — LOS to confirm`: which signature scheme LOS can support (HMAC header name/algorithm, or mutual TLS), and LOS egress IPs.

### 3.3 Formats
- **Date** (no time): `yyyy-MM-dd` (e.g. `2026-07-29`).
- **Date-time**: ISO-8601 UTC, e.g. `2026-07-29T03:24:14Z`.
- **Amounts**: JSON number, 2 decimal places, accompanied by an ISO-4217 **currency** code. Primary currency **USD**; **KHR** must also be supported.
- **Booleans**: `true` / `false`.
- All monetary values must specify currency explicitly; do not assume USD.

### 3.4 Idempotency
Every webhook and every outbound call must be safely retriable:
- LOS → Backend webhooks must carry the `losApplicationNo` and a unique `eventId`; the backend de-duplicates on `(losApplicationNo, eventId)`.
- Backend → LOS submit must carry a Kjey PAPA-side `clientReference`; resubmitting the same reference must not create a duplicate application.
`TBD — LOS to confirm`: idempotency key field name(s) LOS will honour.

### 3.5 Error envelope & HTTP status codes
Kjey PAPA endpoints return a consistent envelope:
```json
{ "type": "SUCCESS | INVALID | NOT_FOUND | INTERNAL_SERVER_ERROR", "message": "human readable", "data": null }
```
HTTP codes: `200` accepted/processed; `4xx` rejected (bad signature, validation, unknown application); `5xx` transient backend error (LOS should retry — §3.6). Kjey PAPA requests the same discipline for LOS endpoints, plus a stable machine-readable error code per failure.

### 3.6 Retries & delivery guarantees
LOS → Backend webhooks should be retried with exponential backoff on `5xx`/timeout (suggested: 5 attempts over ~30 minutes). Because the backend is idempotent (§3.4), duplicate deliveries are safe. A non-retriable `4xx` should be surfaced to LOS operations for manual follow-up.

### 3.7 Correlation
Every message should carry the `losApplicationNo` (once assigned) and an `eventId`/`requestId` to support tracing and reconciliation across both systems.

---

## 4. API Specifications

> Endpoint paths under **Group A** are the Kjey PAPA backend's actual receiver endpoints (relative to the backend base URL, §2.3) and are **implemented today**. Paths under **Group B** are **proposals** for LOS to map onto its own API.

### Group A — LOS → Backend (webhooks LOS must call)

#### A1. Product Sync — *BRS 2.2* `[Kjey PAPA: IMPLEMENTED]`
- **Purpose:** LOS publishes (create/update) the active PDL product configuration so the app can present correct amounts, tenors, rates and fees.
- **Endpoint:** `POST /pdl/los/product-sync`
- **Auth:** webhook signature (§3.2.2).
- **Request body:**

| Field | Type | Req | Description |
|---|---|---|---|
| `productCode` | string | Y | e.g. `PDL` (also `MSIL`, `PL` later). |
| `name` | string | Y | Display name. |
| `active` | boolean | Y | Whether the product is currently offered. |
| `minAmount` | decimal | Y | Minimum principal. |
| `maxAmount` | decimal | Y | Maximum principal. |
| `currency` | string | Y | ISO-4217. |
| `tenors` | array | `TBD` | Allowed loan periods / installment counts. |
| `interestRate` | decimal | `TBD` | Rate basis (per month/annum) — specify. |
| `feeSchedule` | object/array | `TBD` | Processing fee, CBC enquiry fee, other fees. |

- **Response:** `200` `{ "type":"SUCCESS" }`.
- `TBD — LOS to confirm`: the full product/tenor/rate/fee schema (BRS Appendix 1).

#### A2. Application Rejected — *BRS 2.4* `[Kjey PAPA: IMPLEMENTED]`
- **Purpose:** LOS notifies that an application is rejected.
- **Endpoint:** `POST /pdl/los/reject`
- **Request body:**

| Field | Type | Req | Description |
|---|---|---|---|
| `losApplicationNo` | string | Y | LOS application identifier. |
| `eventId` | string | Y | Unique event id (idempotency). |
| `statusCode` | string | Y | Return code — `R-LPO` or `R-AO` (§6.2). |
| `message` | string | N | Optional reason/comment (Kjey PAPA maps the code to the customer-facing message — §6.2). |

- **Behaviour:** backend sets application status → **Rejected**, stores the code, notifies the customer.
- **Response:** `200`.

#### A3. Application Rework — *BRS 2.5* `[Kjey PAPA: IMPLEMENTED]`
- **Purpose:** rework/return-for-correction notification.
- **Endpoint:** `POST /pdl/los/rework`
- **Request body:** same shape as A2, with `statusCode` ∈ `R-LPO`, `R-AO` (PDL) or `RW-LPO`, `RW-AO` (MSIL/PL).
- **Behaviour (PDL):** PDL has **no true rework** — rework is treated as **reject** (BRS rule). For MSIL/PL the same channel will carry true rework codes (`RW-*`) and the customer will be allowed to amend and resubmit. `TBD — LOS to confirm`: whether PDL ever emits `RW-*`, and the exact rework data (which fields/documents to correct).

#### A4. Application Approved — *BRS 2.6* `[Kjey PAPA: IMPLEMENTED — schedule + terms stored]`
- **Purpose:** LOS notifies approval and delivers the generated documents, the agreed loan terms and the repayment schedule.
- **Endpoint:** `POST /pdl/los/approved`
- **Request body:**

| Field | Type | Req | Description |
|---|---|---|---|
| `losApplicationNo` | string | Y | LOS application identifier. |
| `eventId` | string | Y | Idempotency. |
| `message` | string | N | Optional approval comment. |
| `loanRefNo` | string | Y | The loan reference number. |
| `currency` | string | Y | ISO-4217. |
| `tenor` | integer | Y | Number of installments. |
| `outstandingAmount` | decimal | Y | Initial outstanding (total repayable). |
| `settlementAccountNo` | string | Y | Direct-debit / settlement account. |
| `loanFormRef` | string/file | Y | Generated **loan application form** (see §7). |
| `loanContractFileRef` | string/file | Y | Generated **loan contract** to be signed (see §7). |
| `repaymentScheduleRef` | string/file | N | Generated repayment schedule document (PDF). |
| `loanDocRef` | string/file | N | Any additional loan document. |
| `schedule` | array<ScheduleRow> | Y | The repayment schedule rows (§5 ScheduleRow). |

- **Behaviour:** backend sets status → **Approved**, stores terms + documents + schedule, notifies the customer to review and accept.
- `TBD — LOS to confirm`: exact field names/types; whether documents are inline (base64) or referenced by URL/id (§7).

#### A5. Disbursement Status Update — *(extends BRS; required)* `[Kjey PAPA: IMPLEMENTED]`
- **Purpose:** after the customer accepts, LOS orchestrates disbursement (Campu Bank). LOS notifies the outcome.
- **Proposed endpoint:** `POST /pdl/los/disbursement`
- **Request body:**

| Field | Type | Req | Description |
|---|---|---|---|
| `losApplicationNo` | string | Y | |
| `eventId` | string | Y | Idempotency. |
| `disbursementStatus` | string | Y | e.g. `DISBURSED`, `FAILED`, `PENDING`. |
| `disbursementTxnId` | string | Y (on success) | Bank transaction reference. |
| `disbursedAmount` | decimal | Y (on success) | |
| `disbursedDate` | date | Y (on success) | |
| `failureReason` | string | N | On failure. |

- `TBD — LOS to confirm`: status vocabulary and whether disbursement is automatic on accept or requires a back-office step.

#### A6. Loan / Repayment Status Update — *(required for "My Loan / Payment Record")* `[Kjey PAPA: IMPLEMENTED]`
- **Purpose:** keep the customer's loan view current — outstanding balance, days past due, and per-installment paid amounts.
- **Proposed endpoint:** `POST /pdl/los/loan-update`
- **Request body:**

| Field | Type | Req | Description |
|---|---|---|---|
| `losApplicationNo` / `loanRefNo` | string | Y | Loan identifier. |
| `eventId` | string | Y | Idempotency. |
| `status` | string | N | `Active`, `Closed`, `Overdue`, … |
| `outstandingAmount` | decimal | Y | Current outstanding. |
| `overduePayment` | decimal | N | Current overdue amount. |
| `daysPastDue` | integer | N | |
| `lastPaidAmount` | decimal | N | |
| `lastTransactionDate` | date | N | |
| `schedule` | array<ScheduleRow> | N | Updated rows incl. paid breakdown (§5). |

- **Cadence:** `TBD — LOS to confirm` — event-driven (on each repayment) and/or daily batch.

### Group B — Backend → LOS (endpoints LOS must expose)

#### B0. OAuth Token
- **Purpose:** obtain a bearer token for the calls below.
- **Endpoint:** `POST {LOS}/oauth/token` — `TBD — LOS to provide` (URL, grant, scopes, credentials).

#### B1. Submit New Application — *BRS 2.3*
- **Purpose:** Kjey PAPA submits a complete PDL application after validating the 5 mandatory documents are present.
- **Proposed:** `POST {LOS}/applications`
- **Auth:** OAuth bearer (B0).
- **Request body (proposed):**

| Field | Type | Req | Description |
|---|---|---|---|
| `clientReference` | string | Y | Kjey PAPA-side unique reference (idempotency). |
| `productCode` | string | Y | `PDL`. |
| `requestAmount` | decimal | Y | Requested principal. |
| `currency` | string | Y | |
| `loanPeriodDays` / `tenor` | integer | Y | Requested term. |
| `applicant` | object | Y | **Applicant** object (§5). |
| `employment` | object | Y | **Employment** object (§5). |
| `bankAccount` | object | Y | **BankAccount** object (§5). |
| `cbcConsent` | boolean | Y | E-CBC consent given. |
| `cbcConsentRef` | string | N | Consent record reference. |
| `documents` | array<Document> | Y | The 5 mandatory documents (§5, §6.3, §7). |
| `isNewCustomer` | boolean | Y | New-to-bank vs existing CIF. |
| `cif` | string | N | Existing CIF, if any. |

- **Response (proposed):**

| Field | Type | Description |
|---|---|---|
| `losApplicationNo` | string | The LOS identifier used by all subsequent webhooks. |
| `status` | string | Initial LOS status. |
| `receivedAt` | datetime | |

- `TBD — LOS to confirm`: actual endpoint, request/response schema, whether documents are inline or pre-uploaded (§7), required vs optional fields, and validation rules.

#### B2. Customer Accept / Reject Decision — *BRS 2.7*
- **Purpose:** relay the customer's decision on an approved offer.
- **Proposed:** `POST {LOS}/applications/{losApplicationNo}/decision`
- **Request body (proposed):**

| Field | Type | Req | Description |
|---|---|---|---|
| `decision` | string | Y | `Y` = accept; `N` = reject/expiry. |
| `signedContractRef` | string/file | Y when `Y` | The digitally **signed** e-contract (see §7). |
| `decidedAt` | datetime | Y | |
| `decidedBy` | string | N | Customer identifier. |

- **Behaviour:** on `Y` + signed contract, LOS proceeds to "Pay Disbursement"; on `N` the application is closed ("Loan Application is rejected by you").
- `TBD — LOS to confirm`: endpoint, how the signed contract is conveyed, response schema.

#### B3. (Optional) Reconciliation reads
For resilience, Kjey PAPA would like read endpoints to reconcile state if a webhook is missed: `GET {LOS}/applications/{losApplicationNo}` (status + terms + schedule) and `GET {LOS}/products` (catalog). `TBD — LOS to confirm` availability.

---

## 5. Data Dictionary (shared objects)

**Applicant** (personal / KYC)

| Field | Type | Req | Notes |
|---|---|---|---|
| `khmerFamilyName`, `khmerFirstName` | string | Y | Khmer script. |
| `latinFamilyName`, `latinFirstName` | string | Y | Latin script. |
| `gender` | string | Y | `M` / `F`. |
| `dateOfBirth` | date | Y | |
| `idType` | string | Y | e.g. `National ID Card`, `Passport`. |
| `idNo` | string | Y | |
| `idIssuedDate`, `idExpiryDate` | date | Y | |
| `placeOfBirth` | object | N | country / province / district. |
| `nationality` | string | Y | |
| `maritalStatus` | string | N | |
| `mobilePhone` | string | Y | E.164, Cambodia `+855`. |
| `email` | string | N | |
| `correspondenceAddress` | Address | Y | |
| `permanentAddress` | Address | Y | |

**Address**: `country`, `province`, `district`, `commune`, `village`, `houseStreetNo`.

**Employment**

| Field | Type | Req |
|---|---|---|
| `employmentType` | string | Y |
| `employerName` | string | Y |
| `businessActivities` | string | N |
| `occupation` | string | Y |
| `employmentStartDate` | date | N |
| `employmentStatus` | string | N |
| `monthlyIncome` | decimal | Y |
| `currency` | string | Y |
| `workAddress` | Address | N |

**BankAccount**: `bankName`, `accountName`, `accountNo`, `currency`.

**Document** (§6.3, §7): `docType`, `fileName`, `mimeType`, and the payload (`base64` **or** `url` + `checksum` — §7).

**ScheduleRow** (repayment schedule / payment record)

| Field | Type | Notes |
|---|---|---|
| `installmentNo` | integer | |
| `dueDate` | date | |
| `principalDue`, `interestDue`, `feeDue`, `otherDue`, `totalDue` | decimal | Amounts due. |
| `principalPaid`, `interestPaid`, `feePaid`, `penaltyPaid`, `totalPaid` | decimal | Paid breakdown (populated as repayments occur). |
| `transactionDate` | date | Payment date. |
| `status` | string | `PENDING` / `PAID` / `OVERDUE`. |

---

## 6. Reference Data

### 6.1 Application status (Kjey PAPA internal lifecycle)
`Draft → Submitted → (Rejected | Approved) → Accepted → Disbursed → Active → Closed` (plus `Revoked`). LOS need only drive the transitions it owns via the webhooks above.

### 6.2 Decision / return codes and customer messages

| Code | Meaning | Customer-facing message (Kjey PAPA mapping) | PDL | MSIL/PL |
|---|---|---|---|---|
| `R-LPO` | Reject at LPO stage | "Insufficient Information or Documents" | ✔ | ✔ |
| `R-AO` | Reject at approving stage | "Not eligible for the loan" | ✔ | ✔ |
| `RW-LPO` | Rework at LPO stage | "Insufficient Information or Documents" | (PDL = reject) | ✔ true rework |
| `RW-AO` | Rework at approving stage | "Not eligible for the loan" | (PDL = reject) | ✔ true rework |

`TBD — LOS to confirm`: the authoritative code list and any additional codes.

### 6.3 Document types
Five **mandatory** application documents (Kjey PAPA → LOS): `E_CBC_CONSENT`, `PROFILE_PHOTO`, `NID`, `EMPLOYMENT_CARD`, `BANK_STATEMENT`. Plus `SIGNED_CONTRACT` (customer accept). LOS-generated documents (LOS → Kjey PAPA): loan application form, loan contract, repayment schedule.

### 6.4 Currencies
ISO-4217. `USD` (primary) and `KHR` must both be supported.

---

## 7. Document / File Handling
Both directions exchange documents (the 5 mandatory app docs; the LOS-generated form/contract/schedule; the signed contract). A single mechanism must be agreed. Options, in Kjey PAPA's order of preference:

1. **Pre-uploaded reference** — the document is uploaded to a file service and only a reference id/URL + checksum is exchanged in the JSON. (Cleanest for large files.)
2. **Base64 inline** — the document bytes are base64-encoded inside the JSON. Simple, but inflates payload size.
3. **Multipart** — the submit is a multipart request (JSON part + file parts).

For each document, the agreed envelope must convey `docType`, `fileName`, `mimeType`, file size, and a **SHA-256 checksum** for integrity. Max file size, allowed MIME types (PDF/JPEG/PNG), and virus-scanning expectations are `TBD — LOS to confirm`.

The **signed e-contract** (customer accept) integrity is security-critical: the mechanism must let LOS verify the contract the customer signed is the one LOS issued (e.g. checksum match, or LOS-side signing). `TBD — LOS to confirm`.

---

## 8. Security Requirements
- **Transport:** TLS 1.2+ both directions; no plaintext.
- **AuthN:** OAuth2 (backend→LOS); HMAC signature + IP allowlist (LOS→backend) — §3.2.
- **AuthZ:** least-privilege credentials scoped to PDL operations.
- **PII:** payloads carry sensitive KYC (ID numbers, addresses, bank, photos). Both parties must encrypt in transit and at rest, log access, and avoid logging full PII in plaintext.
- **Consent:** the CBC enquiry must only occur under a captured E-CBC consent; the consent reference travels with the application.
- **Data retention / deletion:** policy `TBD` — to align with Sambat Finance and regulatory requirements.
- **Replay protection:** webhook signature + `eventId` de-duplication (§3.4).

---

## 9. Non-Functional Requirements
- **Availability:** target 99.5%+ for the integration endpoints; both parties publish maintenance windows.
- **Latency:** synchronous calls (submit, decision) should respond within ~10s; the backend applies a 30s client timeout.
- **Throughput:** `TBD` — expected peak applications/day to size both sides.
- **Reliability:** at-least-once webhook delivery with retry + idempotent processing (§3.4, §3.6).
- **Observability:** correlation by `losApplicationNo` + `eventId`; both sides retain an audit trail for reconciliation.
- **Versioning:** API version in the URL or a header; backward-compatible field additions only within a version.

---

## 10. Open Items — Decisions Required from LOS
The following must be confirmed by LOS / TurnKey to finalise the contract. (These correspond to the BRS Appendices 1–6 that are currently empty.)

1. ~~Base URLs~~ **RESOLVED** — LOS is on the Tricube server (UAT `…tricube-uat.sambatfinance.com:6443/api`); confirm the **production** Tricube host/port. Still need **sandbox access + a test applicant**.
2. ~~Authentication (outbound)~~ **RESOLVED** — reuses the existing SBF/Tricube OAuth (`SbfAuthorization`). Only confirm the LOS endpoints accept the **same token/scopes** as the SBF endpoints.
3. ~~Notification mechanism — PUSH vs POLL~~ **RESOLVED — PUSH.** Tricube/LOS **pushes** a notification to the Kjey PAPA backend whenever it performs an action (reject / rework / approved / disbursement / repayment). This validates the existing webhook receivers (`/api/v1/pdl/los/*`; A1–A4 implemented); the A5 (disbursement) and A6 (repayment/loan) receivers will be added on the same pattern. **Still to confirm (inbound security):** the webhook **authentication** — signature scheme (HMAC header / mTLS / shared secret) and Tricube's **egress IP(s)** for the allowlist — so the endpoints can be secured in production (they are currently open for sandbox).
4. **Submit Application (B1)** — actual endpoint, full request/response schema, mandatory/optional fields, validation rules.
5. **Decision relay (B2)** — actual endpoint and how the signed e-contract is conveyed.
6. **Approved payload (A4)** — exact fields, loan-terms schema, and document delivery mechanism (§7).
7. **Disbursement (A5)** — status vocabulary; automatic-on-accept vs back-office step; Campu Bank txn reference format.
8. **Repayment/loan updates (A6)** — fields and cadence (event vs batch) for outstanding/DPD/installment paid data.
9. **Product catalog (A1)** — full product/tenor/rate/fee schema.
10. **Return codes (§6.2)** — authoritative code list; whether PDL ever emits `RW-*`.
11. **File transfer (§7)** — agreed mechanism, max size, MIME types, checksum, virus scanning.
12. **Idempotency keys (§3.4)** — field names LOS will honour for de-duplication.
13. **Reconciliation reads (B3)** — availability of status/catalog GET endpoints.
14. **New vs existing customer** — how LOS handles a brand-new applicant (no CIF) vs an existing Sambat CIF.
15. **SLAs, throughput, data retention** — operational parameters (§8, §9).

---

## 11. Appendix

### 11.1 Implementation status on the Kjey PAPA side
- **Implemented & tested (mock-LOS):** webhook receivers **A1–A6** (`/pdl/los/{product-sync,reject,rework,approved,disbursement,loan-update}`); application lifecycle; accept/reject; document capture; repayment-schedule storage **and per-installment paid updates**; the customer "My Application / My Loan / Payment Record" views. The integration runs end-to-end against a **mock LOS** today, so swapping in the real LOS contract is a configuration + field-mapping exercise.
- **Appendix 2 (submit) received:** the real `POST /api/new-loan-application` contract (102 fields, base64 docs, `{IsSuccess, Result:[{AppId, AppRefId}], MissingData}` response) is now documented — see the companion **`LOS_Appendix2_Review_and_Gaps.md`** for the field-by-field gap analysis. The main gap is that LOS expects mostly **CBC-coded** values + financial-assessment data the app does not yet collect; the **CBC code master lists** are the critical dependency.
- **To be added on confirmation:** wire `LosProviderImpl` (real mode) to assemble `newAppRequest` and call Tricube using the existing `SbfAuthorization` token (no separate LOS auth); decide push-vs-poll (§10.3) and implement either the A5/A6 receivers or the polling reads accordingly; obtain the remaining appendices (product sync, reject, rework, approved, accept/reject).

### 11.2 Change log

| Version | Date | Notes |
|---|---|---|
| 1.0 | (draft) | Initial specification issued to LOS for review. |

---

*End of document. Please return comments against the **Open Items (§10)** and against any field/endpoint where the LOS contract differs, so this specification can be finalised and signed off by both parties.*
