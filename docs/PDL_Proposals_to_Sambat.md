# PDL Integration — Proposals to Sambat Finance (v2)

**From:** Kjey PAPA (Ezetik) team
**To:** Sambat Finance — LOS Integration Lead
**Date:** 2026-08-19 *(v2 — supersedes the 2026-08-13 draft)*
**Re:** Defaults requested in your answers to our clarification addendum (QB4.5, QC2.2, QC1.2) + the geography list

In your answer form you asked us to recommend defaults for the reject/reason code list (QB4.5), the Occupation and Employment Status option lists (QC2.2), and the repayment-amount tier list (QC1.2). **All of the proposals below are now implemented and running in our build** — end-to-end testing is not blocked — and every item remains replaceable on your confirmation. This v2 adds the Khmer display labels we shipped (please correct them — you are the authority on the Khmer wording) and a fourth item: the provisional geography list we would like replaced by your official one.

---

## 1. Proposed Reject / Reason Code List (QB4.5)

The integration already carries four codes from the BRS: **R-LPO / RW-LPO** ("Insufficient Information or Documents", LPO review) and **R-AO / RW-AO** ("Not eligible for the loan", approving officer). We retain those unchanged and add codes for every other terminal outcome, so each has a short, stable, machine-readable code.

| Code | Meaning | Customer-facing message (English) |
|------|---------|-----------------------------------|
| R-LPO | Rejected at LPO review — insufficient information or documents | Your application could not proceed because the information or documents provided were insufficient. |
| RW-LPO | Returned for rework at LPO review | Please review and resubmit the requested information or documents so we can continue processing your application. |
| R-AO | Rejected by approving officer — not eligible | We are sorry — you are not eligible for this loan at this time. |
| RW-AO | Returned for rework by approving officer | Please provide the additional information requested so the approving officer can complete the review. |
| R-BLACKLIST | Rejected — applicant on internal blacklist | We are sorry — your application cannot be approved at this time. |
| R-CBC | Rejected — adverse finding in the CBC check | We are sorry — your application cannot be approved based on the credit bureau check. |
| R-BANK | Rejected — payroll-account issue found during LOS processing | Your application could not be completed because of an issue with your payroll bank account. Please contact us to update your account details. |
| R-CUTOFF | Auto-expired — offer not confirmed (or bank hand-off not completed) before the daily 5:00 PM cut-off | Your loan offer expired because it was not confirmed before 5:00 PM. You are welcome to apply again. |
| R-CUSTOMER | Cancelled — rejected/withdrawn by the customer | Your application has been cancelled at your request. |
| R-DISBURSE | Failed — disbursement could not be completed | We could not complete the transfer of your loan. Please contact us so we can resolve this and re-attempt disbursement. |
| R-OTHER | Rejected — other/uncategorised | We are sorry — your application cannot be approved at this time. Please contact us for more information. |

Notes:

- **R-CUTOFF is already live** in our build: the daily 5:00 PM sweep stamps it both when an approved offer was never confirmed and when a confirmed offer's bank hand-off was never completed (per your answer that Re-Attempt is disabled once the cut-off passes).
- Consistent with your answer that **no distinct bank-verification failure codes exist** for the post-accept hand-off: a hand-off failure is message-only and re-attemptable; **R-BANK** applies only to payroll-account rejects raised during LOS processing.
- English messages are the master text; we produce and maintain the Khmer translations keyed on these codes, so Sambat only needs to send the code.

**Please confirm or amend.**

---

## 2. Occupation & Employment Status Option Lists (QC2.2) — now live

Both fields ship as locale-aware dropdowns: the **English value** is what we store and would send to LOS; the **Khmer label** is what a Khmer-locale customer sees. Please confirm the values and, importantly, **correct our Khmer labels** where needed.

**Occupation (18 options)**

| # | Value (stored / sent to LOS) | Khmer display |
|---|---|---|
| 1 | Factory Worker | កម្មករ/កម្មការិនីរោងចក្រ |
| 2 | Teacher | គ្រូបង្រៀន |
| 3 | Civil Servant | មន្ត្រីរាជការ |
| 4 | Accountant | គណនេយ្យករ |
| 5 | Sales Staff | បុគ្គលិកផ្នែកលក់ |
| 6 | Driver | អ្នកបើកបរ |
| 7 | NGO Officer | មន្ត្រីអង្គការ |
| 8 | Nurse | គិលានុបដ្ឋាក/គិលានុបដ្ឋាយិកា |
| 9 | Engineer | វិស្វករ |
| 10 | Technician | អ្នកបច្ចេកទេស |
| 11 | Security Guard | សន្តិសុខ |
| 12 | Cashier | អ្នកគិតលុយ |
| 13 | Administrative Staff | បុគ្គលិករដ្ឋបាល |
| 14 | Manager | អ្នកគ្រប់គ្រង |
| 15 | IT Staff | បុគ្គលិកព័ត៌មានវិទ្យា |
| 16 | Construction Worker | កម្មករសំណង់ |
| 17 | Hospitality Staff | បុគ្គលិកបដិសណ្ឋារកិច្ច |
| 18 | Other | ផ្សេងៗ |

**Employment Status (5 options)**

| # | Value (stored / sent to LOS) | Khmer display |
|---|---|---|
| 1 | Permanent / Confirmed | អចិន្ត្រៃយ៍ / បញ្ជាក់រួច |
| 2 | Probation | សាកល្បងការងារ |
| 3 | Contract (fixed-term) | កិច្ចសន្យា (មានកំណត់) |
| 4 | Part-time | ក្រៅម៉ោង |
| 5 | Other | ផ្សេងៗ |

**Employment Type (2 options)** *(shown for completeness — locked to "Employee" in the Payday flow)*

| # | Value (stored / sent to LOS) | Khmer display |
|---|---|---|
| 1 | Employee | បុគ្គលិក |
| 2 | Self-employed | អាជីវកម្មផ្ទាល់ខ្លួន |

**Please confirm or amend (values and Khmer labels).**

---

## 3. Repayment-Amount Tier List (QC1.2) — now live

Per your answer, tiers are based on the repayment schedule. For the Payday product — capped at USD 50, single repayment, maximum 30-day term — the customer selects the **total repayment amount** from fixed tiers (these drive the quote screen in our build today):

**USD tiers:** $10 · $20 · $30 · $40 · $50

**KHR tiers:** 40,000 ៛ · 80,000 ៛ · 120,000 ៛ · 160,000 ៛ · 200,000 ៛

The KHR tiers assume an indicative ~4,100 KHR/USD, rounded to note denominations; the exact KHR values and exchange-rate policy are yours to set — the tiers are configuration.

**Worked example — $50 tier, 15-day period (your confirmed pricing: 1.5% monthly interest; processing fee $3; CBC enquiry fee $1) — this is what our quote engine computes and what the app now displays:**

| Item | Value |
|------|-------|
| Repayment amount (tier selected) | $50.00 |
| Loan period | 15 days |
| Interest rate applied | 1.5% × 15/30 = 0.75% |
| Principal disbursed A = 50 / (1 + 0.75%) | $49.63 |
| Interest | $0.37 |
| Processing fee | $3.00 |
| CBC enquiry fee | $1.00 |
| Net amount credited = 49.63 − 3 − 1 | $45.63 |
| Single repayment on due date | $50.00 |

Please confirm explicitly that interest is **pro-rated by loan period** (1.5% × days/30) — the only reading that reproduces your mockup figures exactly — so we can lock the calculation.

**Please confirm or amend.**

---

## 4. NEW — Geography list: please send the official one

The registration form now selects addresses **hierarchically** (Province → District dropdowns, in Khmer for Khmer-locale users; Commune and Village still free text). To enable this we loaded a **provisional gazetteer of 25 provinces and 202 districts** (e.g. Phnom Penh (ភ្នំពេញ), Banteay Meanchey (បន្ទាយមានជ័យ), Battambang (បាត់ដំបង), Kampong Cham (កំពង់ចាម), …) compiled from public administrative divisions.

We would like to replace it with **your official geography master list** — ideally the same province/district/commune/village list (with codes) used for the CBC-coded fields in the LOS `newAppRequest`, since that would solve both the address dropdowns and the CBC geo-code mapping in one delivery. Until it arrives, the provisional list stands; please treat its Khmer spellings as unreviewed.

**Please send the official province/district/commune/village list (with CBC codes if available).**

---

## Closing

Everything above is live in the current build and verified end-to-end against our mock LOS, so your confirmations are drop-in configuration changes — nothing is hard-coded against your final decision. For traceability this responds to questions **QB4.5**, **QC2.2**, **QC1.2** from your answers to our 2026-08-13 addendum, plus the geography item that supports the parked CBC-master-list work.

We look forward to your confirmations or amendments.
