# PDL Integration — Addendum Answer Form (V26 / V8)

**Respondent:** ______________________  **Role:** ______________________  **Date:** ____________

Companion to *"Addendum to our Clarification Request" (2026-08-13)* — same question numbers.
**How to answer:** tick one box per question (☐ → ☒). Options marked **(default)** are what we build if a question is left blank — so you only need to tick where we're wrong, and fill the few lists marked ✍.

---

## B1 — Bank Mobile App redirect

**QB1.1 — The auto-debit consent (dropped from the V26 redirect wording) is:**
☐ Established elsewhere — where: ______________________
☐ Still part of the bank-app step (just not drawn)
☐ No longer required
☐ Other: ______________________

**QB1.2 — The "already disbursed?" pre-check before the redirect is done by:**
☐ SAMBAT server-side **(default)**
☐ The app, calling an API first — endpoint: ______________________

## B2 — Documents

**QB2.1 — Authoritative sign-up document set:**
☐ **V8 set**: NID front+back, selfie, employment card, bank statement **(default)**
☐ V26 set: single NID, selfie, bank statement
☐ Other: ______________________

**QB2.2 — Request-time bank statement (screen 15):**
Mandatory? ☐ Yes, every application **(default)** ☐ Optional ☐ Only if: ____________
It: ☐ Supplements the sign-up statement **(default)** ☐ Replaces it

**QB2.3 — Bank account-information disclosure consent (screen 8) stored as:**
☐ Timestamped consent record **(default)** ☐ Simple flag ☐ Signed form upload
Legal copy: ☐ We may use the V8 English + our Khmer translation **(default interim)** ☐ You will send final EN+KM

**QB2.4 — Base64 document transport limits** ✍
Max size per document: ____________  Max total request: ____________
Accepted MIME types: ____________  Min/max resolution: ____________

**QB2.5 — Mapping to Appendix 2 `Doc_*` fields:**
NID front+back: ☐ We merge into one `Doc_NID` as **PDF** ☐ We merge as **combined image** ☐ LOS accepts two files ☐ Other: ____________
Employment card → `Doc_EmploymentCard`? ☐ Yes ☐ Other: ____________
Request-time statement → `Doc_BankStatement`? ☐ Yes ☐ Other/new field: ____________

## B3 — Acceptance cut-off / Expired

**QB3.1 — Offer unconfirmed at cut-off becomes:**
☐ **Expired** (stays under Approved, buttons disabled) **(default)** ☐ Rejected ☐ Other: ____________

**QB3.2 — At that moment, LOS:**
☐ Still expects our reject ("N") decision **(default)** ☐ Expires it on the LOS side — we send nothing ☐ Other: ____________

**QB3.3 — Reminder cadence:**
☐ Every 30 min, 8 AM–5 PM **(default)** ☐ Hourly ☐ Fixed times: ____________ ☐ No reminders

**QB3.4 — Cut-off time (Asia/Phnom_Penh):**
☐ 5:00 PM **(default)** ☐ 5:15 PM ☐ Other: ____________
Grace window: ☐ 30 min **(default)** ☐ None ☐ Other: ____________

**QB3.5 — After expiry, the customer:**
☐ Must submit a new application **(default)** ☐ Can re-open the expired offer — how: ____________

**QB3.6 — Approval timestamp you push to us is formatted as:**
☐ ISO-8601 with offset (recommended) ☐ ISO-8601 UTC ☐ Epoch millis ☐ Local time, format: ____________

**QB3.7 — Cut-off calendar:**
☐ Every calendar day **(default)** ☐ Business days only (holiday calendar attached)
After-hours/weekend approval: ☐ Runs to the next day's cut-off ☐ Same-day rule ☐ Other: ____________

## B4 — Re-attempt & reject codes

**QB4.1 — Re-Attempt button is disabled when:**
☐ The daily cut-off has passed **(default)** ☐ Attempt limit reached — N = ______ ☐ Time window passed — window = ______ ☐ Combination: ____________

**QB4.2 — Pressing Re-Attempt:**
☐ Re-launches the bank-app hand-off for the same application **(default)** ☐ Re-submits to LOS ☐ Other: ____________

**QB4.3 — Bank-verification failure codes:**
☐ None — hand-off failure is generic **(default)** ☐ Codes exist — list: ______________________

**QB4.4 — Screen 24 ("loan has been disbursed" with alert icon) represents:**
☐ The disbursement notice (re-attempt no longer possible) ☐ A distinct "re-attempt disabled" state ☐ Mockup error — ignore

**QB4.5 — Complete reject/reason code list** ✍ (for the Rejected-card "Reason" row)
☐ Attached / will send:  ______________________________________________

**QB4.6 — Reject `message` field is:**
☐ English-only — we localize Khmer from codes **(default)** ☐ Localized by LOS

## C1 — Products & pricing  ⚡ *most urgent*

**QC1.1 — In scope for this release:**
☐ Payday only — Micro/Personal shown disabled "coming soon" **(default)** ☐ All three ☐ Payday + Micro ☐ Other: ____________

**QC1.2 — Repayment-amount tier list (dropdown values)** ✍ per product (and per currency if KHR stays):
______________________________________________

**QC1.3 — Pricing:**
a. Monthly interest rate: ☐ 1.50 % ☐ Other: ______
b. Processing fee: ☐ $3 ☐ Other: ______   CBC enquiry fee: ☐ $1 ☐ Other: ______
c. Net amount (screen 17 $45.36 vs screen 32 $45.63): ☐ **$45.63** = loan − processing − CBC ☐ $45.36 — formula: ____________
d. Interest is: ☐ Collected at repayment **(default — screens: 49.63 + 0.37 = $50 repaid)** ☐ Pre-deducted from disbursement

**QC1.4 — Disbursement/repayment dates come from:**
☐ Customer's salary date on file ☐ LOS product config ☐ Fixed offset from application date ☐ Other: ____________

**QC1.5 — KHR for PDL:**
☐ KHR remains — keep the currency selector **(default)** ☐ Dropped — USD only

**QC1.6 — Quote computation is owned by:**
☐ Us, from the formula above, config-driven **(default)** ☐ A SAMBAT/LOS pricing API — spec: ____________

## C2 — Sign-up & account approval

**QC2.1 — Pre-login account request is approved by:**
☐ LPO (loan processing officer) review, decision returned via the journey's account-creation notification — channel/contract: ______________________
☐ Auto-approved after E-KYC
☐ Other: ______________________

**QC2.2 — Occupation + Employment Status option lists** ✍
☐ Attached / will send: ______________________________________________  *(until then we ship free-text inputs)*

**QC2.3 — Sign-up field list (V8 vs V26 Note 1):**
Email: ☐ Required ☐ Optional ☐ Not collected
Place of Birth: ☐ Collect (country/province/district) ☐ Not collected
Street No / Building No: ☐ Two separate fields ☐ One combined field

## C3 — Profile, navigation & data

**QC3.1 — Settlement-account Balance (screen 26) source:**
☐ Core banking, real-time ☐ LOS ☐ Cached (refresh: ____________) ☐ Not in this release

**QC3.2 — Repayment schedule ("Payment Record"):**
☐ Keep, accessible inside My Loan **(default)** ☐ Drop from the customer app ☐ Other: ____________

**QC3.3 — Menu (screen 42) without "Payday Loan"/"Fee Schedule" entries:**
☐ Settings subset only — keep the entries **(default)** ☐ Remove those entries

## C4 — Documents-to-view, content & confirmations

**QC4.1 — The application-card "Attachment [View]" opens:**
☐ The uploaded bank statement ☐ The full application form ☐ Other: ____________

**QC4.2 — The "CBC Consent [View]" on loan cards opens:**
☐ A consent record we generate at submit **(default)** ☐ An LOS-generated document (ref pushed to us) ☐ Other: ____________

**QC4.3 — CBC consent legal text (screen 16):**
☐ Final EN+KM attached / will send ☐ Use the V8 English + our Khmer translation until then **(default interim)**

**QC4.4 — Terms & Conditions (screen 45):**
☐ Website T&C will be updated — no app change **(default)** ☐ App must render dedicated PDL terms — copy: ____________

**QC4.5 — Correct label:** ☐ "Loan Period" ☐ "Note Period"

**QC4.6 — Flow confirmations:**
a. OTP sending remains on the SAMBAT side (despite the V26 p.1 lane redraw): ☐ Yes **(default)** ☐ No — moved: ____________
b. Successful repayments are also notified via the SAMBAT API (like failures): ☐ Yes ☐ No — failures only

**QC4.7 — Mockup placeholder values (screen 26 Sex field, screen 37 ref values) are illustrative:**
☐ Confirmed — ignore ☐ Corrections: ______________________

---

**Anything else we should know:**  ______________________________________________

*Return to the Kjey PAPA (Ezetik) mobile team — ideally by 2026-08-27. Blank questions = the marked defaults apply.*
