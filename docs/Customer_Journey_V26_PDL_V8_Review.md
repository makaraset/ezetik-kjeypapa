<!-- Sources: docs/Customer's Journey _ Payday Loan - V26.pdf (2026-08-12 "update" revision, md5 ae13dc10)
     + docs/PDL V8.rar (45 screens), reviewed 2026-08-12 by 4 spec-page + 7 mockup-group vision readers
     + synthesis, against Customer_Journey_V21_Review.md and the current feature/pdl-los-integration build.
     Spot-checked by direct image read: screen 33 (Status "Expired" under the Approved tab, muted
     Confirm/Reject, full fee table) and screen 30 (Re-Attempt as a full-width button on an
     Approved-tab card) — both confirmed as stated in G12/G14.
     Implementation plan: kjeypapa_app/plans/PDL_V26_V8_GAP_PLAN.md -->

# Review — "Customer's Journey: Payday Loan" V26 + PDL Mockups V8

> **ANSWERS RECEIVED (2026-08-13):** Sambat returned the 39-question answer form
> (`LOS_Clarification_Addendum_Answer_Form.docx`). Key rulings that amend this review:
> cut-off produces **Rejected** (not Expired — G12 cancelled, screen 33's label superseded,
> grace = none); a **sample bank app** is approved to unblock G4/G13/G14 now; settlement
> balance = **SBF core banking, real-time** (G20 unblocked); street/building = one combined
> field; email optional; label "Note Period"; no bank-verification failure codes; pricing
> formula confirmed (net $45.63; interest pro-rated by period). Amended plan:
> `kjeypapa_app/plans/PDL_V26_V8_GAP_PLAN.md` (amendments marked [A]).

Reviewed against the **V21 baseline** (`docs/Customer_Journey_V21_Review.md`, esp. §3 G1–G6 and §6 build status) and the **current build** (V21 gaps G1–G6 implemented, mock LOS, G4 deferred/flag-off). Sources: V26 flowchart (4 pages), PDL V8 mockups (screens 1–45).

---

## 1. What changed V21 → V26 (spec)

### A. On-Boarding & KYC (page 1)
- **NEW: "Select User Type 'Employee'"** step (the page's only yellow-highlighted box), inserted between "Initiate User Sign Up" and the NID photo. User-type selection is now an explicit part of PDL onboarding; only "Employee" is named.
- Drawing-level only: "Process User Account Request" and "Sent OTP…" boxes moved from the SAMBAT System lane into the Mobile App lane. Sequence/semantics unchanged — reads as a redraw artifact, but as drawn the ownership moved (see "Verify by eye").
- **Nothing else changed**: doc set (single NID photo + selfie + bank statement via Note 3), Notes 1–4 verbatim identical (still no Email field, still no employment card, E-CBC still a consent), T&C gate, OTP 3-try rule, CamDX E-KYC, LPO verification, notifications, END placements.

### B. Loan Application (page 2)
- **NEW: in-flow edit/update loops** on the three confirm steps. "Confirm Personal/Employment Info" became "**Verify** Personal/Employment Info", each with a **Correct?** decision: No → "Update … Info" → **connector ① back to "Click 'Request For New Loan'"** (i.e. update restarts the request); Yes → explicit "Click Confirm Button".
- **CHANGED: "Input loan request amount" → "Select loan amount"** — hints at a picklist/preset amounts rather than free entry (V8 confirms: repayment-amount dropdown).
- **CHANGED: page scope.** Approved?=Yes now ends the page with a new explicit "**Notification to Applicant loan request is approved**" box → END; acceptance/disbursement moved to a new page 3. (Our A4-webhook FCM push already matches the new box.)
- Connector renumbering: the reject path is now **②** (was ①); the new **①** serves the update loops. Cosmetic.
- **REMOVED: V21 Note 2 "Daily cut off time for the bank is 5:15 PM"** — gone from page 2 and does not reappear on page 3. Affects our G3 cut-off/grace assumption (Q3).

### B2. Disbursement (page 3 — new standalone process)
- Split into its own flow with its own Start and a new explicit "Loan Application is approved by Approving Officer in LOS" box.
- **"every 30 mins" removed** from the acceptance-notification box. The 5 PM daily cut-off decision itself is unchanged, but the reminder cadence is no longer specified.
- **NEW already-disbursed pre-check** after Accepted?=Yes ("Verify if loan has been… disbursed" → Disbursed?=Yes → "Your loan has been disbursed" → END) and a new explicit "Submit loan details to Bank" box.
- **Redirect box reworded (yellow-highlighted)**: now "Re-direct Applicant to Bank Mobile App for **verification of the transaction**" — the V21 wording about account verification and **consent for auto-debit is dropped** (Q4).
- **Bank-verification sub-flow heavily simplified**: V21's loan-ref matching, Matched?/Verification-completed?/Confirmed-by-Applicant? decisions, failed/incomplete-transaction notifications, and the second cut-off check are ALL GONE. Replaced by: Mobile-App **Success?** → Bank "checks if… disbursed successfully" → **Transaction completed?** (Yes→END) → "Verify and confirm loan details by Applicant in Bank Mobile App" → **Correct?**.
- **Re-attempt is now an explicit user button** ("Press 'Re-Attempt' button…") on Success?=No, looping back to the acceptance-notification box (re-passing the 5 PM check). Triggered by a generic hand-off failure, not by specific bank-verify reject codes (Q5).
- **Rejected path re-trigger**: fed only by Correct?=No; cut-off / not-accepted branches exit via connector ② to page 2's reject flow. New verbatim customer copy: **"Loan is rejected by you"**.
- **NEW customer success notification box**: "Loan Disbursement is successful" (V21 had only the status change).
- **"Tricube" renamed "Core Banking System"** in the disbursement box.

### C. Repayment — Auto Direct Debit (page 4)
- **No change.** Character-for-character identical to V21 (title, lanes, boxes, decisions, ENDs). Only positional: now page 4 of 4. Still absent (same as V21): SAMBAT-side handling after final-failure notify, overdue/late status, partial payments, customer repayment push. No new work implied.

### Notes
- Page-1 Notes 1–4 and page-2 Note 1 unchanged verbatim. The **only** note-level change is the deletion of the 5:15 PM bank cut-off note.

---

## 2. What changed V7 → V8 mockups (structural UX)

1. **PDL signup becomes the real pre-login registration** (screens 4–10): user-type radio (Employee / Business owner / Merchant) → NID front+back → personal info **including User ID (phone) + password creation** → employment → payroll bank → **OTP after the full wizard** → terminal "**account-creation request submitted, pending**" screen (no dashboard entry). Our wizard is currently post-login only.
2. **Loan request is now an 8-step wizard** (screens 11–18): product select (Payday/Micro/Personal) → three per-section **legal declaration** screens with "Click Here to Update…" links → amount/terms screen driven by a **Repayment Amount dropdown** with derived pricing + "Upload Latest Bank Statement" → **full-legal-text CBC consent page** → pre-submit **Confirmation** screen with a server-computed application table (incl. **CBC Enquiry Fee** and **Net Loan Received by You**) → success screen with the same table. Replaces our single lean page.
3. **Notification suite** (19, 21×2, 22–24, 41): notification center list (matches our page), plus **full-screen interstitials** for pending-acceptance reminder, "accepted successfully → Next: verify your bank account", "rejected by you", "declined", and "disbursed" — implying push deep-linking into result screens.
4. **Application status view rework** (29–34): **Processing / Approved / Rejected** segmented tabs; rich cards; a dedicated **full-page acceptance screen** (fees, interest rate, settlement + bank account blocks, **Loan Documents** viewers, T&C checkbox gating Confirm/Reject); **"Expired"/lapsed** offers stay on the Approved tab with disabled buttons (NOT moved to Rejected); **Re-Attempt lives on the Approved tab with explicit enabled/disabled states** (screens 30/31) and does NOT appear on Rejected (34).
5. **Profile** (26–28): 3 segments (Personal/Address/Employment) with **bank cards merged into Personal**, a new **Settlement Account card with live Balance**, employment-card View, Street No / Building No split, "Request for New Loan" button pinned on every segment.
6. **My Loan** (35–37): **Active/Closed toggle**; **CBC Consent [View]** and **Loan Contract [View]** buttons on every loan card; closed loans rendered as a **settlement-breakdown card** (not the 12-col table); no View-Schedule button.
7. **Dashboard IA**: circular icon nav **My Profile / My Application / My Loan / Request** + bell + hamburger; **no Payment Record item** anywhere in the V8 nav.
8. All V8 copy is **English-only** — every new screen needs km keys sourced separately.

---

## 3. Gap list vs current build (G7+, de-duplicated)

**G7 — Re-home PDL signup to pre-login with user type, credentials, OTP-last, pending terminal** `[BLOCKER]`
- *Spec/mock:* V26 page 1 new "Select User Type 'Employee'" step; V8 screens 4–10: pre-login wizard with Employee/Business owner/Merchant radios, User ID(=phone)+password inside the personal step, OTP fires **after** the bank-info Submit, ends on "request submitted, pending" (no dashboard).
- *Build:* PDL wizard (`pdl_signup_*_page.dart`) is post-login only (sole entry `pdl_profile_page.dart` → Update); pre-login uses the generic `register_user_type_page.dart` (Customer/Merchant) → `register_info_page.dart` → OTP → complete-profile. No OTP in the PDL wizard; result page passes only success/failed and routes to dashboard.
- *Work — mobile:* merge the two type selectors into one Employee/Business-owner/Merchant radio screen; chain type→NID→personal(+credentials)→employment→bank→OTP (reuse `otp_page.dart` with configurable onSuccess)→pending result routed to Sign In; keep the post-login Update entry into the same pages. *Backend:* unauthenticated "user account request" capture path (current capture endpoints are JWT-protected), an approval step/status before the account is usable, approval push (FCM plumbing exists). Map Employee→PDL customer role.
- *Sambat:* none directly; V26 confirms the pre-account sequencing.

**G8 — Signup data-model extensions (fields + pickers)** `[HIGH]`
- *Spec/mock:* V8 screen 6 adds **Place of Birth (country/province/district)** and **Email** to the signup form; addresses are **Country/Province/District/Commune/Village cascading dropdowns** with no house/street; screen 7 locks Employment Type to "Employee", makes Occupation and Employment Status dropdowns ("Confirmed probation"), work address = full 5-level set; screens 27/28 split **Street No / Building No** on all three addresses incl. work. (Note: Email is V8-only — it is NOT in V26 Note 1, which is verbatim V21.)
- *Build:* `pdl_signup_personal_page.dart` has no email/place-of-birth inputs; addresses are free-text with a combined house_street_no; work address is free-text Province+District only; occupation/status free-text; employment type a 2-item dropdown.
- *Work — mobile:* Cambodia gazetteer dataset + cascading pickers; add email (+ place-of-birth capture); street/building split decision; option lists for Occupation/Employment Status; lock type for the PDL path. *Backend:* new columns/DTO fields (email, workCommune/Village/Country, work+corr/perm street/building), enum values for employment status. D/M/Y dropdown triplets are cosmetic.
- *Sambat:* confirm Occupation/Employment-Status value lists; confirm street/building split vs combined.

**G9 — Document set rework: employment card returns, statement moves + per-request statement, bank-disclosure consent** `[HIGH]` — touches **Q1**
- *Spec/mock:* V8 screen 7: **Upload Employment ID Card** (4th doc); screen 8: bank-statement upload lives on the **bank** step with a **bank account-information disclosure consent** checkbox (verbatim legal copy, no T&C checkbox there); screen 15: fresh "**Upload Latest Bank Statement**" at request time (red = likely mandatory); screen 28: employment card viewable in profile. V26 page 1 still shows only 3 docs (spec/mock conflict).
- *Build:* G2 decision = 3 docs; employment-card capture was explicitly removed; statement upload sits on the employment page; bank page has confirm-true + T&C checkboxes only; request flow never re-uploads (submitFullApplication skips uploadPickedDocuments, which exists unused).
- *Work — mobile:* re-add `docEmploymentCard` tile on employment page; move statement `_uploadRow` to the bank page; add the disclosure-consent checkbox (km copy needed); wire request-time BANK_STATEMENT pick → `uploadPickedDocuments()` against the created draft. *Backend:* employment-card doc type/storage, signup consent flag persistence, relax/confirm the 3-ref submit gate for the request-time statement.
- *Sambat:* **Q1 is substantially answered by V8** (4 docs incl. employment card; NID = front+back per screen 5; E-CBC stays a consent) but contradicts V26 page 1 — get written confirmation; also whether the request-time statement replaces or supplements the signup one and whether it's mandatory.

**G10 — Loan-request wizard restructure (product select, declarations+edit loops, CBC page, two-phase confirm/submit)** `[BLOCKER]` — touches **Q2**
- *Spec/mock:* V26 page 2 verify→Correct?→Update→restart loops; V8 screens 11–18: product radio (Payday ≤$50/30d, Micro ≤$500/6m, Personal ≤$5,000/24m), three declaration screens with legal copy + update links, full-legal-text CBC consent page (Article 9/25 Prakas text, Confirm button), pre-submit **Confirmation** page rendering the server-echoed application (App ID visible pre-submit, Attachment [View]), success page with the same table.
- *Build:* single `pdl_request_page.dart` (read-only card + currency + free amount + CBC checkbox), create→submit in one shot; no product concept anywhere; generic `pdl_success_page.dart` with no details; no edit path from the request flow.
- *Work — mobile:* wizard route chain with draft state in PdlProvider; product-select step; declaration screens deep-linking into the `pdl_signup_*` editors and returning/restarting per the ① loop; scrollable CBC legal page (final EN/KM copy from Sambat) writing a versioned/timestamped `cbcConsentRef`; split create (on entering confirmation) from submit; extend success page with the application table. *Backend:* `loanType`/product field on `PdlApplicationRequest`/entity + per-product amount/term validation; product config; decide Micro/Personal as disabled "coming soon" vs routed.
- *Sambat:* Q2 extended — product list/limits, whether Micro/Personal are in scope now.

**G11 — Pricing/quote engine + fee fields (repayment-amount tiers, CBC fee, net amount, interest rate)** `[BLOCKER]` — touches **Q2** + new fee Qs
- *Spec/mock:* V26 "Select loan amount"; V8 screen 15: **Repayment Amount dropdown** ($50) derives Loan Amount $49.63 / Interest $0.37 / Processing Fee $3 with read-only Disbursement/Repayment dates and 15-day period; screens 17/32: Monthly Interest Rate **1.50%**, **CBC Enquiry Fee $1**, **Net Loan Received by You**; screen 32 net = 49.63 − 3 − 1 = $45.63 (screen 17 shows $45.36 — inconsistent, see verify-by-eye).
- *Build:* free-text amount (>0 only) + USD/KHR dropdown; draft model already carries repayment/interest/fee/date fields but nothing populates them; no `cbcEnquiryFee`, no `netDisbursedAmount`, no `interestRate` anywhere (model, provider, service, backend entity); no quote endpoint.
- *Work — backend:* quote endpoint (repayment amount + salary date → dates, period, principal, interest, fees, net); repayment-amount tier list; add cbcFee/cbcEnquiryFee, netDisbursed, interestRate to PaydayLoan + webhook payload + DTOs. *Mobile:* render quote read-only into the draft fields; tier dropdown; show rate/fees/net on confirmation, acceptance (screen 32) and success screens.
- *Sambat:* net formula + whether interest is pre-deducted; CBC fee amount/currency; where the salary/repayment date comes from; whether **KHR is dropped for PDL** (screen 15 shows no currency control despite the filename — do not remove KHR until confirmed).

**G12 — "Expired" (lapsed) offer state replaces cut-off auto-Reject; reminder cadence unspecified** `[HIGH]` — touches **Q3**
- *Spec/mock:* V8 screen 33: lapsed offer shows Status **"Expired"**, stays under the **Approved** tab with documents viewable and Confirm/Reject disabled — not moved to Rejected. V26 removed "every 30 mins" from the reminder box and deleted the 5:15 PM note; the 5 PM cut-off decision itself remains.
- *Build:* `PdlAcceptanceScheduler.enforceCutoff()` flips Approved → **Rejected** ("Not confirmed before the daily cut-off") and relays "N" to LOS; `PdlStatusEnum` has no Expired; app has no Expired chip. Reminders hardcoded to 30-min cadence, grace 30 min keyed to the now-deleted 5:15 note.
- *Work — backend:* add `PdlStatusEnum.Expired`; cutoff sets Expired instead of Rejected (decide if the "N" relay stays); make cadence/grace config the source of truth pending Q3. *Mobile:* Expired label/chip (en+km), bucket under Approved, read-only acceptance page with disabled buttons. Migration note: historical cutoff-rejects identifiable by losMessage.
- *Sambat:* Q3 still open on ownership, now **plus**: confirm Expired vs Rejected semantics toward LOS, reminder cadence (V26 dropped "every 30 mins"), and grace now that 5:15 PM is gone.

**G13 — Full acceptance experience (detail page, T&C gate, full-screen results, "Next → bank verification")** `[HIGH]` — touches **Q4**
- *Spec/mock:* V8 screen 32: routed acceptance page with App ID/Loan Ref/dates/rate/amounts/fees/net, Settlement Account No, "This loan will be created to bank account below" block, Loan Documents (Form/Contract/Schedule) viewers, mandatory T&C checkbox enabling blue **Confirm Acceptance** / red **Reject Loan**. Screen 21-accepted: full-screen success whose button is **"Next"** into bank-account verification; screen 22: full-screen "rejected by you".
- *Build:* inline card buttons → bare AlertDialog → `pdl.accept()` → snackbar. All required data exists on `PdlLoan` (bankInfo, settlementAccountNo, currency) but isn't rendered in the accept flow; accept API done.
- *Work — mobile:* new `pdl_accept_page.dart` fed by loadLoanDetail (full row set + bank block + doc viewers + checkbox gate; checkbox event backs `signedContractRef`); replace snackbars with full-screen result variants; "Pending Acceptance" display label for un-accepted Approved (cosmetic mapping). *Backend:* only the G11 fee/rate fields. The "Next → verify bank account" hand-off stays behind the G4 flag until Q4 is answered.

**G14 — Re-attempt semantics rework (Approved-tab, enable/disable, hand-off-failure trigger)** `[HIGH — partially blocked on Q4/Q5]`
- *Spec/mock:* V26 page 3: "Press 'Re-Attempt' button" on the bank-app hand-off **Success?=No**, looping back through the acceptance/cut-off checks — a generic failure trigger, not reject codes. V8 screens 30/31: Re-Attempt is a full-width button under an **Approved** application with explicit **enabled and disabled** renderings; screen 34 shows **no** Re-Attempt on Rejected.
- *Build:* re-attempt button renders only on **Rejected** loans with provisional codes {R-BANK, INVALID_ACCOUNT, BANK_VERIFY_FAILED}, always enabled, routes to a **new request**. Contradicted on placement, trigger, and target.
- *Work:* once Q4/Q5 answered — move to the Approved bucket detail; define the disable rule (attempt cap / time window unknown); re-point the action to retry the bank verification/hand-off (fits the reserved Pending_Bank_Verification design) rather than opening `pdlRequest`. Keep current code behind review until then.

**G15 — In-app document viewer + all View affordances** `[HIGH]`
- *Spec/mock:* View buttons appear on: application-card **Attachment** (29/34), **Loan Documents** Form/Contract/Schedule (32/33), **CBC Consent** and **Loan Contract** on every loan card incl. collapsed state (35/36), **Uploaded Employment Card** in profile (28), confirmation-screen Attachment (17/18).
- *Build:* refs exist (`loanFormRef`, `loanContractFileRef`, `repaymentScheduleRef`, `loanDocRef`, `employmentCardFileRef` — servable via `GET /file/show/{fileName}`) but **no screen renders or opens any of them**; `cbcConsentRef` is the placeholder literal `'APP-CBC-CONSENT'`, not fetchable.
- *Work — mobile:* one reusable image/PDF viewer + wire all entry points. *Backend:* real consent artifact behind `cbcConsentRef` (generated at submit); mock LOS must push real file refs for testing.
- *Sambat:* which ref backs the card-level "Attachment"; what document the CBC-consent View opens.

**G16 — My Application: status buckets, enriched cards, Reason + contact footer** `[MEDIUM]`
- *Spec/mock:* screens 29/30/34: Processing | Approved | Rejected segmented filter; cards show Created/Disbursement/Repayment dates, Loan Period, Loan/Interest/Repayment amounts; Rejected cards get a labeled **Reason** row and footer "Please contact us at 023 9977 22 | 093 99 77 22".
- *Build:* one flat list with raw status chips; `PdlTransaction` projection lacks dates/period/interest/repayment fields (they exist on the full loan); reason is an unlabeled red caption; no contact footer.
- *Work — mobile:* segmented filter mapping Draft/Submitted→Processing; Approved/Accepted/Pending_Bank_Verification→Approved; Rejected/Revoked/Expired→per G12; card rebuild; Reason row + configurable hotline footer (en+km). *Backend:* enrich the my-transactions projection (or reuse the full-loan list).

**G17 — My Loan: Active/Closed toggle + closed-loan settlement card** `[MEDIUM]`
- *Spec/mock:* screens 35–37: Active/Closed pill; closed loans as key-value settlement cards (Principal/Interest/Monthly Fee/Other Due & Paid, Penalty Paid, Totals, Transaction Date, DPD); no View-Schedule button.
- *Build:* one mixed list (Accepted…Closed together); closed loans reuse the active card; schedule lives in the Payment Record tab. Model gap: `pdl_payment_schedule.dart` has `otherDue` but **no `otherPaid`**.
- *Work — mobile:* split provider getters (active vs Closed); closed-card widget merging loan-level (ref, status, DPD) with schedule totals. *Backend:* add `otherPaid`. Decide the schedule table's fate with G18.

**G18 — Dashboard/profile IA restructure (icon nav, Request promotion, Payment Record relocation, 3-segment profile)** `[MEDIUM]`
- *Spec/mock:* all V8 dashboard screens: circular icon nav My Profile / My Application / My Loan / **Request** + bell + hamburger; **no Payment Record tab**; profile is 3 segments with Settlement/Bank cards inside Personal Info (collapsible); "Request for New Loan" button pinned under Update on every profile segment; V8 drawer (42) shows no Payday Loan / Fee Schedule rows.
- *Build:* text TabBar (my_application/my_profile/my_loan/payment_record), Request as an app-bar "+", profile has 4 tabs with a standalone bank tab, drawer has Payday Loan + Fee Schedule entries.
- *Work — mobile only:* nav restyle, promote Request, merge bank tab into Personal, add the second profile button, collapsible account cards. **Do not delete** the Payment Record data or the drawer PDL entry until Sambat confirms where they live in V8 (drawer 42 may only depict the settings subset).

**G19 — Notification pipeline: type→severity mapping, km titles, deep-link router + interstitials** `[HIGH]`
- *Spec/mock:* screen 19: green-check/red-X per event, Khmer titles; screens 21–24: full-screen result surfaces reached from pushes.
- *Build:* icon mapping is hardcoded to the single legacy title `'Request approved'` — **every PDL push currently renders the red X** (backend titles: 'Loan approved', 'Loan disbursed', 'Confirm your loan offer', etc.); those English titles have no km entries; FCM taps never navigate (`main.dart` only calls `LocalNotificationService.showNotification`, no tap handler); dead debug `notificationMsg` code in `notification_page.dart`.
- *Work — backend:* add a machine `type` (+ loan id) to the FCM data payload. *Mobile:* severity mapping off `type`, km/en keys (or key-based payloads), tap handlers (onMessageOpenedApp/getInitialMessage + local-notification response) routing to a parameterized result screen (extend `PdlSuccessPage` with error/red variants), clean up dead listeners, re-enable initState load.

**G20 — Settlement Account card with live Balance** `[MEDIUM — blocked on data source]`
- *Spec/mock:* screen 26: collapsible Settlement Account Info card (Account No/Name, **Balance $10.00**, Currency) on the profile — a Kjey PAPA settlement/wallet account distinct from the payroll bank account.
- *Build:* no settlement data in the profile payload; only `PdlLoan.settlementAccountNo` string shown on the loan view; no balance API.
- *Work — backend:* settlement account + real-time balance on the profile payload or a dedicated endpoint. *Mobile:* collapsible card in `_personal()`.
- *Sambat:* **new question** — balance source (LOS vs core banking) and refresh semantics.

**G21 — Hardening/consistency bundle** `[LOW]`
- Min-6 password validation missing client-side in `create_password_page.dart` / `change_password_page.dart`; **`register_info_page.dart` has `maxLength: 6` on password — an exact-6 cap conflicting with "min. 6"** (fix regardless of V8).
- Hard-coded "1.0.0" in About App + drawer footer vs pubspec 1.2.5+16 → add `package_info_plus`.
- Drawer logout only navigates to LoginPage — doesn't clear the stored session/token or unregister FCM.
- T&C: V8 shows native, loan-specific "Customer Terms & Conditions" copy vs our single-URL webview — confirm with Sambat whether the site URL updates (no app work) or native/role-specific rendering is required.

---

## 4. Already covered (do NOT redo)

- **NID front+back capture** (`pdl_signup_id_page.dart`) — matches V8 screen 5 exactly; also settles single-vs-double NID in favour of front+back.
- **OTP screen mechanics** — 4 digits, resend, max-3 tries + timeout/restart (G6/Note 4, unchanged in V26). Only re-sequencing (G7) needed.
- **Splash, Language, Get-Started, Sign-In, Forgot-Password flow (38–40), Change Password (43), About App (44)** — structure matches; cosmetic drift only.
- **Notification center list structure** (screen 19) — icons, `title (ref)`, timestamp format, bin delete, refresh/empty states all match.
- **Acceptance scheduler machinery** (30-min reminders + 5 PM sweep, Asia/Phnom_Penh, FCM) — the mechanism V8's Expired state rides on; only the terminal status changes (G12).
- **Approve/disburse push notifications** — V26's new explicit "Notification… approved" and "Loan Disbursement is successful" boxes match our A4/A5→FCM pushes (copy tweaks only).
- **Expanded active-loan field set + See More/Less** (`pdl_loan_page.dart`) — matches screen 35's rows.
- **Accept/reject API** (explicit Y/N, ownership-checked) — presentation changes only (G13).
- **CBC consent gate before submit** (concept + `cbcConsentRef` contract) — presentation changes only (G10).
- **Re-attempt impossible after disbursement** (screen 24's implication) — a Disbursed loan can never show the button today.
- **Repayment page (V26 p.4)** — byte-identical to V21; A6 webhook + Payment Record remain consistent; zero new work.
- **Payroll bank card data** (screen 26) — all fields incl. bankName exist; placement change only.

---

## 5. Impact on open Sambat questions

- **Q1 (doc set)** — **Largely answered by V8, but now spec-vs-mockup conflicted.** V8 shows 4 uploads (NID **front+back** per screen 5, selfie, bank statement on the bank step, **Employment ID Card** on the employment step) plus a per-request bank statement; E-CBC remains a consent (now a full-text screen, still no uploaded form). V26 page 1, however, is verbatim V21 (3 docs, single NID drawn). Recommend closing Q1 as "V8 doc set (4 + request-time statement)" pending written confirmation.
- **Q3 (cut-off ownership)** — **Not answered; semantics changed.** V26 deletes both "every 30 mins" and the 5:15 PM note (cadence/grace now unspecified), and V8 screen 33 shows the lapsed offer as **"Expired" under Approved** — i.e. Lapsed effectively replaces our cut-off auto-Reject in the UI, though V26 page 3 still routes not-confirmed via connector ② into the reject flow, so whether LOS still receives "N" is genuinely ambiguous. Reword Q3 to cover: ownership, cadence, grace, Expired-vs-Rejected toward LOS.
- **Q4 (bank-app redirect contract)** — **Direction confirmed, contract still open.** V26 keeps the redirect (yellow-flagged) but rewords it to "verification of the transaction", **dropping the account-verification/auto-debit-consent wording**; adds an already-disbursed pre-check and "Submit loan details to Bank". V8 screen 21-accepted confirms the app-side hand-off ("Next → verify your bank account"). Deep-link/return contract still unanswered — G4 stays flag-off; G13/G14 partially blocked.
- **Q5 (bank-verify codes / re-attempt semantics)** — **Changed, still open.** V26/V8 model re-attempt as a **user button on an application that stays Approved**, triggered by a generic hand-off "Success?=No" (V26 p.3) with an enable/disable condition (V8 30/31, rule unspecified) — contradicting our provisional reject-code trigger on Rejected. The simplified V26 bank-verify sub-flow also removes the failure-notification boxes our code assumptions were based on. Reword Q5: what enables/disables Re-Attempt (cap? window?), what the retry action calls, and whether any reject codes remain relevant.
- **Q2 (request-time capture)** — extended, not answered: product selection, repayment-amount tier list, pricing/rate/fee config ownership, and the currency question (KHR dropped?).
- **New questions to send:** settlement-account balance source (G20); Payment Record's home in the V8 IA (G18); CBC fee amount/currency + net-amount formula (G11); Occupation/Employment-Status option lists (G8); salary/repayment-date source (G11); drawer PDL entry point (G18); T&C content delivery (G21); screen-24 intent (below).

---

## 6. Verify by eye / mockup inconsistencies (do not build against these yet)

- **V26 p.1 lane shift** of "Process User Account Request" + "Sent OTP" into the Mobile App lane — likely a redraw artifact, but confirm OTP-send ownership hasn't actually moved.
- **V26/V21 p.4** — arrowhead direction on the horizontal segment between "Credit Loan Repayment into SAMBAT Escrow Account" and "Notify transactions via SAMBAT API" is not discernible at available resolution (same ambiguity in both versions): is the success path also notified via SAMBAT API?
- **V8 screen 24** — filename says "re-attempt disabled" but content is "Your loan has been disbursed" with a **red alert icon** (a positive event) — ask design whether this is the disbursement notice or a distinct "re-attempt no longer available" state.
- **Net-amount discrepancy** — screen 17 shows Net (E) **$45.36**; screen 32 shows **$45.63** (= 49.63 − 3 − 1). Formula and figure need confirmation.
- **Screen 26 data bugs** — Sex shows a date ("31-08-2024"); Marital Status typo "Signle". Do not replicate.
- **Screen 37** — "Loan Ref No" shows App-ID-like values (102/95) and the top nav highlights "My Application" despite being the My Loan > Closed view — mockup inconsistencies.
- **Screen 15** — filename mentions currency but no currency control is visible; treat KHR removal as unconfirmed.
- **Screen 45 (T&C)** — low resolution; wording partially inferred.
- **"Note Period" (32/33) vs "Loan Period" (29/34)** — naming to reconcile with design.
- **V8 drawer (42)** omitting Payday Loan / Fee Schedule may depict only the settings subset — confirm before removing entries.

---

*Prepared 2026-08-12 against `Customer_Journey_V21_Review.md`, the V26 flowchart pages 1–4, PDL V8 screens 1–45, and the current v2.0 build (mobile `lib/views/customer/pdl/*`, backend `ezetik-kjeypapa` `pdl/*`). Open Sambat questions sent 2026-07-03 remain unanswered as of this review.*