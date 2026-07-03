# Review — "Customer's Journey: Payday Loan" (V21)

Reviewed against the current Kjey PAPA build (backend `pdl/*`, mobile `views/customer/pdl/*`) and the LOS **Appendix 2** contract. Source: `docs/Customer's Journey _ Payday Loan - V21.pdf` (3 pages).

## 1. What V21 specifies

**A. On-Boarding & KYC** (lanes: *Mobile App* | *SAMBAT System*)
Download app → sign up → **upload NID photo** → Personal Info (Note 1) → Employment Info (Note 2) → Payroll Bank Info (Note 3) → **upload current selfie** → accept T&Cs → **SAMBAT sends OTP** → enter OTP (max 3 tries, then timeout→restart) → **E-KYC via CamDX** (national ID verified?) → **LPO verifies uploaded documents** → success / failed account-creation notification.

**B. Payday Loan Application** (lanes: *Mobile App* | *SAMBAT System* | *Bank System*)
Login → "Request For New Loan" → select **Payday Loan** → **Confirm** Personal / Employment / Bank info → **Select loan currency** → **Input loan amount** → **Consent for CBC Enquiry** → SAMBAT initiates request → **Bank verifies payroll account** (not-found / dormant / closed → reject) → create App in **LOS** → **Internal blacklist check** → **CBC enquiry** (blacklisted → reject) → **Decision process** (approve/reject) → Mobile App notifies applicant to **review & confirm the E-Loan Contract, reminded every 30 min, must confirm before the 5 pm daily cut-off** (else auto-reject) → on accept, **redirect to the Bank Mobile App** to verify + consent to disbursement & auto-debit → escrow debit → **credit to payroll account** → notify SAMBAT API → **LOS/Tricube finalise disbursement** → status **Disbursed** → archive.

**C. Repayment — Auto Direct Debit** (lanes: *SAMBAT Core Banking* | *SAMBAT API* | *Bank System*)
On due date the **bank auto-deducts** from the payroll account; on failure it **retries daily up to 3 days**; success credits the **SAMBAT escrow**, then notifies via the SAMBAT API.

**Notes:** 1 (Personal): Full Name KH/ENG, Sex, Nationality, NID No, ID Type, NID Expiry, DoB, Place of Birth (country+province), Marital Status, Phone, Full Permanent + Correspondence address. 2 (Employment): Employment Type, Employer Name, Monthly Salary, Occupation, **Length of Service**, Work Address. 3 (Bank): Bank Name, Account Name, Account No, Currency, **upload Bank Statement**. 4: OTP max 3. Bank cut-off 5:15 PM.

## 2. Aligns well with what we built ✅
- **Signup data model matches Notes 1–3** almost exactly — `PdlPersonalInfo` covers all of Note 1; `PdlEmploymentInfo` covers Note 2 (our `employmentStartDate` ≈ "Length of Service"); `PdlBankInfo` covers Note 3.
- **Async account creation** — V21's OTP → CamDX E-KYC → LPO doc-verify → success/failed maps cleanly onto our signup **pending / success / failed** result screens.
- **Decision outcomes** — V21's reject / approve / disburse / repay map onto our webhook receivers **A2/A3/A4 (reject/rework/approved), A5 (disbursement), A6 (repayment)**.
- **Disbursement runs in Tricube/LOS** — matches our design (LOS-orchestrated, we receive the status).
- Our architecture split (mobile-app backend vs SAMBAT LOS/Core-Banking vs Bank) matches the three+ swim-lanes.

## 3. Changes / gaps V21 introduces (actionable) ⚠

**G1 — The loan Request flow is over-built.** V21's loan request is lean: *confirm* existing Personal/Employment/Bank info, then **select currency + input amount + give CBC consent**. It does **not** re-upload documents and does **not** ask a loan period/tenor at request time. Our `pdl_request_page.dart` collects the **5 documents again** + a loan-period field. → **Remove the 5-doc re-upload and the loan-period input from the request flow; add a currency selector.** Documents are captured once at signup.

**G2 — Document set mismatch (V21 vs LOS Appendix 2 vs our model).** V21 signup uploads only **NID photo + selfie + Bank Statement** (3), and treats **CBC as a consent, not a document**. But **LOS Appendix 2** expects **5 `Doc_` fields** (adds `Doc_EmploymentCard` and `Doc_ECBCConsentForm`), and our `PdlDocTypeEnum` also has 5. → **Reconcile the authoritative document set** with Sambat: is an **Employment Card** upload required? Is **E-CBC** a consent flag or an uploaded form? (Our signup currently uploads employment card + treats E-CBC as a doc — both may be unnecessary per V21.) Also V21 shows a **single** NID photo while our signup captures **NID front + back** — confirm.

**G3 — E-Loan Contract acceptance has a reminder + hard cut-off.** V21 requires the Mobile App to **remind the applicant every 30 minutes** to confirm the e-contract, and to **auto-reject if not confirmed before the 5 pm daily cut-off**. We have the accept/reject action but **no 30-min reminder and no cut-off auto-reject**. → New backend **scheduled job** (daily 5 pm sweep of un-accepted Approved apps → Rejected) + **recurring push notifications**. *(Whether this lives in our backend or LOS needs confirming — see Q3.)*

**G4 — Bank Mobile App redirect for disbursement consent.** After acceptance, V21 **redirects the applicant to the Bank's Mobile App** to verify the account and consent to disbursement + auto-debit, and the bank verifies the **loan reference number + loan details** before crediting. We currently just send accept `Y` + signed contract. → New **hand-off step** (deep-link to the bank app) and handling of the bank-verification result (success / failed / re-attempt). Needs the bank-app integration details.

**G5 — Bank payroll-account verification is a distinct step & rejection reason.** V21 has the **bank verify the payroll account** (dormant/closed/not-found → reject) *before* LOS processing, and again at disbursement. This is SAMBAT/Bank-side, but it introduces reject outcomes our UI should surface (a "bank verification failed / re-attempt" state). LOS Appendix 2's bank-info "Note 1" (return account status dormant/active) is the same check. → Ensure our **status/rejection handling** covers a bank-verification-failed outcome and the **re-attempt** path.

**G6 — Minor field/label items.** "Length of Service" (Note 2) — we store `employmentStartDate` (derivable, but confirm the expected form). CamDX E-KYC + OTP "max 3 then restart" is SAMBAT-side but our OTP screen should honour the 3-try/timeout rule.

## 4. Implications for our implementation
1. **Trim the request flow** (G1): confirm-only screens + currency + amount + CBC consent; drop doc re-upload + loan-period. (`pdl_request_page.dart`, `PdlProvider`, `PdlApplicationPayload` already has `currency`? add `currency` to the request model.)
2. **Decide the document model** (G2) before wiring the real LOS submit — it changes `PdlDocTypeEnum`, the signup doc pickers, and the `Doc_*` mapping into `newAppRequest`.
3. **Acceptance cut-off/reminders** (G3): if ours, add a scheduler + notification cadence; if LOS-owned, nothing to build but confirm.
4. **Bank-app hand-off** (G4): scope the deep-link + result handling once the bank integration is defined.
5. **Surface bank-verification-failed + re-attempt** states (G5) in the app status mapping.

## 5. Questions to confirm with Sambat
- **Q1 (G2):** Authoritative mandatory document set — is Employment Card required? Is E-CBC a consent flag or an uploaded form? Single NID photo or front+back? (This must match LOS Appendix 2's `Doc_*` fields.)
- **Q2 (G1):** At loan request, is anything besides currency + amount + CBC consent captured (e.g. tenor), or does the product/term come from LOS product config?
- **Q3 (G3):** Who owns the **30-min reminder + 5 pm cut-off auto-reject** — our backend or LOS? If ours, what exactly triggers the reminder and the reject?
- **Q4 (G4):** The **Bank Mobile App redirect** — which bank app, what deep-link/return contract, and how is the verification result returned to us?
- **Q5 (G5):** How is a **bank-account-verification failure** surfaced to the mobile app (a reject webhook with a specific code? a dedicated status)?
- **Q6:** Does V21 supersede parts of the earlier BRS? (It adds CamDX E-KYC, escrow disbursement, bank-app consent, and the acceptance cut-off that weren't in the original spec.)

## 6. Implementation status — what was built (2026-07-03)

Decisions locked with the product owner: **G2** → follow V21 literally (3 docs); **G3** → build on our backend; **G4** → defer (plan only).

- **G1 — Trimmed request flow + currency (DONE).** `pdl_request_page.dart` rebuilt to the lean V21 form: a read-only "Confirm Your Information" card (name / employer / bank / account from the loaded profile) + **currency dropdown (USD/KHR)** + amount + **CBC-consent** checkbox. Removed the loan-period field, the 5 document pickers, and the bank-consent checkbox. `PdlProvider.submitFullApplication()` now just **create → submit** (no request-time doc upload). Backend: `currency` added to `PdlApplicationPayload` + `PaydayLoan` (column live), `bankConsent` boxed to `Boolean` (Jackson-3 null-safety).
- **G2 — Document set = V21 (3 docs at signup) (DONE).** Signup captures **NID (front+back), selfie, bank statement** only; the **Employment-Card upload was removed** from `pdl_signup_employment_page.dart` and `uploadCapturedDocs()`. **E-CBC is a consent flag**, not a document. The submit gate no longer counts request-side `PdlAttachment`s — it validates the **3 signup profile file-refs** (`nidFrontFileRef`, `profilePhotoFileRef`, `bankStatementFileRef`) → `MISSING_DOCUMENT` if absent. *`PdlDocTypeEnum` left intact (unused values are harmless).* **See Q1** — if Sambat says `Doc_EmploymentCard` / `Doc_ECBCConsentForm` are mandatory in LOS Appendix 2, re-add the employment-card capture + an E-CBC consent PDF.
- **G3 — 30-min reminders + 5 pm cut-off auto-reject (DONE, our backend).** `@EnableScheduling` on; `approvedDate` added to `PaydayLoan` (column live) and stamped by `LosWebhookService.handleApproved`. New `PdlAcceptanceScheduler` (`@ConditionalOnProperty pdl.acceptance.scheduler.enabled`, default on): a **reminder job** (`0 0/30 8-17 * * *`) pushes "confirm before 5 PM" to still-Approved loans, and a **cut-off job** (`0 0 17 * * *`) sets Approved-past-(cut-off − grace) loans → **Rejected** + `losMessage` + `losProvider.sendDecision(no,"N",null)` + notify. Grace default 30 min; all crons/flag/grace externalised in `application.properties`. Unit-tested (`PdlAcceptanceSchedulerTest`, 3 tests). **See Q3** to confirm cut-off/grace/cadence and whether LOS duplicates this (→ flip the flag).
- **G5 — Bank-verification-failed outcome + re-attempt (DONE).** `LosWebhookService.messageForCode` maps `R-BANK` / `INVALID_ACCOUNT` / `BANK_VERIFY_FAILED` → "Bank account could not be verified" (arrives via the existing reject webhook). Mobile dashboard shows a **"Re-attempt"** action on such rejects (`_isBankVerifyReject` → reopen the request flow). **See Q5** for the exact code + whether re-attempt re-submits the same app or starts new.
- **G6 — Minor items (DONE).** Derived **"Length of Service"** (computed from `employmentStartDate`) now displayed on the My-Profile employment section. `otp_page.dart` now enforces **max 3 incorrect entries → timeout → restart registration** and **max 3 resend requests** (V21 Note 4).
- **G4 — Bank Mobile App redirect (DEFERRED, plan only).** No code. Planned shape reserved: a `Pending_Bank_Verification` status between Accepted and Disbursed, a mobile deep-link to the bank app after accept, and a `POST /api/v1/pdl/los/bank-verification` callback. **Blocked on Q4** (bank-app deep-link + return contract).

**Verification:** backend 25/25 unit tests green; boots clean with the scheduler + `approved_date`/`currency`/`bank_consent` columns live on the DB; `flutter analyze` clean on all touched mobile files; en/km i18n parity for all new keys.

*Prepared from Customer's Journey V21; cross-referenced with `LOS_Appendix2_Review_and_Gaps.md` and the current build.*
