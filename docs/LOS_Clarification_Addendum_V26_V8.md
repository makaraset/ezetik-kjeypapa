# Kjey PAPA (Ezetik) — Payday Loan Integration: Addendum to our Clarification Request

**To:** Sambat Finance — LOS Integration Lead
**From:** Kjey PAPA (Ezetik) — Mobile Application Team
**Date:** 2026-08-13
**Subject:** Addendum after reviewing Customer's Journey V26 + PDL Mockups V8 — updated and new questions

---

Thank you for the updated **Customer's Journey — Payday Loan V26** workflow and the **PDL V8** mockups. We have completed a full review against our current build and have begun implementing toward them.

This addendum does three things: (1) records which items from our **2026-07-03 clarification request** V26/V8 now answer, (2) rewords the items whose shape V26/V8 changed, and (3) adds new questions the updated documents introduce. Question numbers here are prefixed **QB/QC** to avoid collision with Q1–Q5 in the 2026-07-03 memo; inline answers against the numbers are perfect. Where we can, we state the **assumption we will build on**, so you only need to correct us where we are wrong.

A reply **by 2026-08-27** would keep us on schedule — the **pricing and product items in §C1** gate the largest piece of our build, so sooner on those helps most. Or we're happy to walk through the priority items on a short call before then.

---

## A. Items from our 2026-07-03 memo — status after V26/V8

| Memo item | Status |
|---|---|
| **Item 1 — Bank Mobile App redirect** | **Still open, unchanged priority (blocker).** V26 confirms the redirect step (now "verification of the transaction"); the deep-link/launch/return contract we asked for (Q1.1–Q1.9) is still what we need. Two small updates in §B1. |
| **Item 2 — Document set** | **Capture set answered by V8 (§B2 to confirm); the wire mapping and transport limits from Q2.3/Q2.5 remain open** — see QB2.4/QB2.5. |
| **Item 3 — Acceptance cut-off** | **Changed by V26 — reworded in §B3.** The timestamp-format and business-calendar parts (Q3.4/Q3.6) are unchanged and carried forward there. |
| **Item 4 — Bank-verification failure / re-attempt** | **Changed by V26/V8 — reworded in §B4**, including the code-list and localization parts of Q4.2/Q4.3. |
| **Item 5 — Environment, webhook auth, correlation id, formats, contact** | **Carried forward unchanged** — Q5.1–Q5.6 remain the prerequisite for any real end-to-end test. |
| **"Also outstanding" (CBC master lists, mandatory `newAppRequest` fields, financial-assessment sections, `MissingData`)** | **Still parked, not lost — and now more relevant:** the V8 pre-submit Confirmation screen (17) renders a server-echoed application table, so how `MissingData` is returned and surfaced is part of that screen's contract. |

---

## B. Updated questions

### B1 — Bank Mobile App redirect (updates to Item 1)

- **QB1.1** — V26 rewords the redirect to *"verification of the transaction"* and **drops the V21 wording about account verification and consent to auto-debit**. Is the auto-debit mandate now established elsewhere (e.g. at account opening), or is it still part of this step but not drawn?
- **QB1.2** — V26 adds a pre-check *"verify if loan has been disbursed"* before the redirect. Should the app perform this check via an API call before launching the bank app, or does SAMBAT gate it server-side?

### B2 — Document set (Item 2)

The V8 mockups show, at sign-up: **NID (front + back)** (screen 5), **Employment ID Card** (screen 7), **payroll bank statement** (screen 8), plus a **selfie**; and at loan request: **"Upload Latest Bank Statement"** (screen 15). E-CBC remains a **consent** (full-text consent screen 16), not an uploaded form. However, **V26 page 1 still lists only 3 documents** (single NID photo, selfie, bank statement — Note 3), as in V21.

- **QB2.1** — Please confirm the authoritative set is the **V8 set** (NID front+back, selfie, employment card, bank statement) — we are building to V8.
- **QB2.2** — Is the **request-time bank statement** (screen 15) mandatory on every application, and does it **replace or supplement** the sign-up statement?
- **QB2.3** — Screen 8 shows a **bank account-information disclosure consent** checkbox at sign-up. Please confirm its exact legal copy (EN + KM) and whether it must be stored as a consent record.
- **QB2.4** *(carried from Q2.5)* — For the Base64-inline documents in `newAppRequest`: **max size per document, max total request size, accepted MIME types**, and any resolution/compression requirements.
- **QB2.5** *(carried from Q2.3)* — How does the V8 four-document set map onto Appendix 2's five `Doc_*` fields? In particular: are NID front+back **merged into the single `Doc_NID`** (by us? as a two-page PDF or combined image?), and which fields do the employment card and the request-time statement occupy?

**Assumption if we don't hear otherwise:** the V8 four-document set; request-time statement is mandatory and supplements the sign-up one; disclosure consent stored as a timestamped consent record using the V8 English copy with our own Khmer translation, replaceable on your confirmation.

### B3 — Acceptance cut-off (rewording of Item 3)

V26 **removed** both the *"every 30 mins"* reminder wording and the *"Daily cut off time for the bank is 5:15 PM"* note, and the V8 mockups (screen 33) show an offer that misses the cut-off as **Status "Expired", remaining under the Approved tab** — rather than moving to Rejected.

- **QB3.1** — When an approved offer passes the 5 PM cut-off unconfirmed, is its status **"Expired"** (as V8 shows) rather than "Rejected"?
- **QB3.2** — At that moment, does LOS still expect to receive a **reject ("N") decision** from us, or does LOS expire the offer on its side?
- **QB3.3** — With "every 30 mins" removed: what reminder cadence (and hours) do you want, if any?
- **QB3.4** — With the 5:15 PM note removed: is **5:00 PM Asia/Phnom_Penh** the single authoritative cut-off, and is any grace window required?
- **QB3.5** — Can a customer **re-open or re-request** an Expired offer, or is a new application required?
- **QB3.6** *(carried from Q3.6)* — The **timezone and wire format of the approval timestamp** LOS pushes: we compute the cut-off/expiry from it, so a UTC-vs-local mismatch would silently expire valid offers.
- **QB3.7** *(carried from Q3.4)* — Does the cut-off observe **business days / Cambodian public holidays**, and how should an after-hours or weekend approval be treated?

**Assumption if we don't hear otherwise:** status becomes **Expired** (shown under Approved, buttons disabled); we still relay "N" to LOS at cut-off; reminders every 30 min 8 AM–5 PM; 5:00 PM Asia/Phnom_Penh, 30-min grace, every calendar day; a new application is required after expiry.

### B4 — Re-attempt (rewording of Item 4)

V26/V8 change the model we asked about: **Re-Attempt is now a customer button** shown on an application that **stays under Approved** (V8 screens 30/31 show it **enabled and disabled**), triggered when the bank-app hand-off does not succeed (V26 page 3 "Success? = No") — not by specific bank reject codes on a Rejected application, as we had assumed.

- **QB4.1** — What rule **enables vs disables** the Re-Attempt button (an attempt limit? a time window? the daily cut-off)? Screens 30/31 show both states but not the rule.
- **QB4.2** — What does pressing Re-Attempt **do** — re-launch the bank-app hand-off for the same application, or re-submit anything to LOS?
- **QB4.3** — Do bank-verification **failure codes** still exist anywhere in the flow (e.g. on the final Rejected outcome), or is hand-off failure now purely generic?
- **QB4.4** — Screen 24 appears in the re-attempt sequence, but its content reads *"Your loan has been disbursed"* with an alert icon — could you confirm which state this screen represents?
- **QB4.5** *(carried from Q4.2)* — The **complete, stable list of reject/reason codes with their meanings** — V8 screen 34's Rejected cards show a labeled **"Reason"** row, which we want to render from machine codes, not free text.
- **QB4.6** *(carried from Q4.3)* — Is the reject `message` field **English-only** (we localize Khmer from the codes), or localized by LOS?

**Assumption if we don't hear otherwise:** Re-Attempt re-invokes the Item-1 bank hand-off on the same Approved application — so its behaviour remains blocked until Item 1 is answered; what we build now is the button placement and an enable/disable rule defaulting to "disabled at the daily cut-off". No distinct bank reject codes; reason codes rendered from QB4.5's list.

---

## C. New questions from V26/V8 — **§C1 gates our largest build item**

### C1 — Products & pricing (highest urgency)

V8 screen 11 offers three products — **Payday (≤ $50, 30 days), Micro (≤ $500, 6 months), Personal (≤ $5,000, 24 months)** — and screen 15 drives the request by a **"Repayment Amount" dropdown** that derives Loan Amount $49.63, Interest $0.37, Processing Fee $3, with fixed disbursement/repayment dates; screens 17/32 add **Monthly Interest Rate 1.50%**, **CBC Enquiry Fee $1**, and **"Net Loan Received by You"**.

- **QC1.1** — Are **Micro and Personal in scope now**, or is this release Payday-only? (We plan to show them disabled as "coming soon".)
- **QC1.2** — Please provide the **repayment-amount tier list** (the dropdown values) per product — and per currency, if KHR remains (see QC1.5).
- **QC1.3** — Please confirm the pricing values and formula:
  - **a.** monthly interest rate (1.50 %?);
  - **b.** processing fee ($3?) and CBC enquiry fee ($1?);
  - **c.** the **net-amount formula** — screen 17 shows Net **$45.36** while screen 32 shows **$45.63** (= 49.63 − 3 − 1) for the same case; which is correct?
  - **d.** is interest **deducted from the disbursed amount** (pre-deducted) or collected at repayment?
- **QC1.4** — Where do the **disbursement and repayment dates** come from (customer's salary date on file? LOS product config)?
- **QC1.5** — Screen 15 shows **no currency selector** although V21/our build offer USD/KHR. Is **KHR dropped** for PDL? (We keep the selector until you confirm.)
- **QC1.6** — Who **owns the quote computation** — should the app call a SAMBAT/LOS pricing API, or is the calculation ours to implement from the confirmed formula? (We are building it on our side from the formula, configuration-driven, unless you tell us LOS provides it.)

### C2 — Sign-up & account approval

- **QC2.1** — V8 signs the customer up **pre-login**, ending in a *"request submitted, pending"* screen. What **approves** the account — the LPO (loan processing officer) review shown in the journey? — and how is the decision returned to us: is the account-creation success/failed notification shown on journey page 1 the full contract, or is there an API/webhook we should implement?
- **QC2.2** — Screen 7 locks Employment Type to "Employee" and makes **Occupation** and **Employment Status** dropdowns (e.g. "Confirmed probation"). Please provide the **option lists** for both. *(Until they arrive we will ship free-text inputs.)*
- **QC2.3** — Screen 6 adds **Email** and **Place of Birth**, and V8 addresses use **Street No / Building No** as separate fields — but V26 Note 1 is unchanged from V21 (no Email). Please confirm the authoritative field list.

### C3 — Profile, navigation & data

- **QC3.1** — Screen 26 shows a **Settlement Account card with a live Balance** ($10.00). What is the **source** of this balance (LOS? core banking?) and how fresh must it be (real-time call vs cached)?
- **QC3.2** — The V8 navigation (My Profile / My Application / My Loan / Request) has **no "Payment Record"** view. Our current app shows the full repayment schedule there — should the schedule remain accessible (e.g. inside My Loan), or is it intentionally dropped from the customer app?
- **QC3.3** — The V8 menu (screen 42) shows no "Payday Loan" or "Fee Schedule" entries — does that screen depict only the settings subset, or should those entries be removed? *(We keep both until confirmed.)*

### C4 — Documents-to-view, content & minor confirmations

- **QC4.1** — Application cards and the confirmation screen show an **"Attachment [View]"** action (screens 17/18/29/34): which uploaded or generated document should this open?
- **QC4.2** — Loan cards show a **"CBC Consent [View]"** action (screens 35/36): what artifact backs it — an LOS-generated consent document, or a consent record we generate at submit? (Today we store a consent flag/reference, not a viewable document.)
- **QC4.3** — The **CBC consent** is now a full legal page (screen 16, Prakas article references). Please provide the final **EN + KM legal text**. *(Until then we ship the V8 English copy with our own Khmer translation.)*
- **QC4.4** — **Terms & Conditions** (screen 45) appears — at the resolution available to us — to show native, loan-specific copy rather than our current website page. Will the web T&C be updated (no app change), or must the app render dedicated PDL terms?
- **QC4.5** — Screens use both **"Note Period"** (32/33) and **"Loan Period"** (29/34) for the same field — which label is correct?
- **QC4.6** — Two flow confirmations: (a) V26 page 1 redraws "Process User Account Request" and "Sent OTP" into the Mobile App lane — we assume OTP sending remains on the SAMBAT side; (b) on the repayment page, is a **successful** repayment also notified to us via the SAMBAT API, as failures are?
- **QC4.7** — A few data values in the mockups appear to be placeholders (e.g. screen 26's Sex field shows a date; screen 37 shows application-ID-style values under Loan Ref No). We will treat these as illustrative unless you tell us otherwise.

---

## Summary

| # | Topic | Priority | What it gates |
|---|-------|----------|---------------|
| QC1.x | Products, tiers, pricing formula, currency | **Blocker** | The new loan-request wizard build |
| Item 1 + QB1.x | Bank-app redirect contract | **Blocker (unchanged)** | Post-accept → disbursement step; also QB4's re-attempt behaviour |
| QB3.x | Expired semantics, cut-off, timestamp basis | High | Cut-off behaviour + acceptance screens |
| QB2.x | Document set confirmation + wire mapping/limits | High | Sign-up capture + real submit |
| QB4.x | Re-attempt rule + reject-code list | High | Approved-tab actions; Rejected-card Reason row |
| QC2.x | Account approval + option lists | High | Pre-login sign-up build |
| QC3.x | Settlement balance; Payment Record & menu homes | Medium | Profile card; navigation |
| QC4.x | Document viewers, legal copy, labels | Medium | View actions, final content, translations |
| Item 5 | Environment, webhook auth, correlation id (2026-07-03) | High | Any real end-to-end test |
| — | CBC master lists, mandatory fields, `MissingData` (2026-07-03) | Medium | Real submit + the V8 confirmation screen |

Thank you — inline answers against the question numbers are perfect, and we're happy to walk through any section on a call.

*Kjey PAPA (Ezetik) mobile team — references: Customer's Journey V26 (Aug-2026 update), PDL V8 screens 1–45, and our 2026-07-03 clarification request.*
