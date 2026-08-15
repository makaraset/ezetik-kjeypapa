# PDL Integration — Proposals to Sambat Finance

**From:** Kjey PAPA (Ezetik) team
**To:** Sambat Finance — LOS Integration Lead
**Date:** 2026-08-13
**Re:** Draft proposals requested in Sambat's answers to our 2026-08-13 clarification addendum (questions QB4.5, QC2.2, QC1.2)

In your answer form you asked us to recommend defaults for three items: the reject/reason code list (QB4.5), the Occupation and Employment Status option lists (QC2.2), and the repayment-amount tier list (QC1.2). Our proposals follow. All three are intended as sensible launch defaults that Sambat can amend at any time.

---

## 1. Proposed Reject / Reason Code List (QB4.5)

The current integration already carries four codes from the BRS: **R-LPO / RW-LPO** ("Insufficient Information or Documents", LPO review) and **R-AO / RW-AO** ("Not eligible for the loan", approving officer). We propose retaining those unchanged and adding codes for the situations that have come up since, so that every terminal outcome has a short, stable, machine-readable code.

| Code | Meaning | Customer-facing message (English) |
|------|---------|-----------------------------------|
| R-LPO | Rejected at LPO review — insufficient information or documents | Your application could not proceed because the information or documents provided were insufficient. |
| RW-LPO | Returned for rework at LPO review — information or documents need correction | Please review and resubmit the requested information or documents so we can continue processing your application. |
| R-AO | Rejected by approving officer — not eligible for the loan | We are sorry — you are not eligible for this loan at this time. |
| RW-AO | Returned for rework by approving officer | Please provide the additional information requested so the approving officer can complete the review. |
| R-BLACKLIST | Rejected — applicant found on internal blacklist | We are sorry — your application cannot be approved at this time. |
| R-CBC | Rejected — adverse finding in CBC (credit bureau) check | We are sorry — your application cannot be approved based on the credit bureau check. |
| R-BANK | Rejected — issue with the payroll bank account found during processing | Your application could not be completed because of an issue with your payroll bank account. Please contact us to update your account details. |
| R-CUTOFF | Auto-rejected — loan offer not confirmed before the daily 5:00 PM cut-off | Your loan offer expired because it was not confirmed before 5:00 PM. You are welcome to apply again. |
| R-CUSTOMER | Cancelled — rejected/withdrawn by the customer | Your application has been cancelled at your request. |
| R-DISBURSE | Failed — disbursement or hand-off to the bank failed | We could not complete the transfer of your loan. Please contact us so we can resolve this and re-attempt disbursement. |
| R-OTHER | Rejected — other/uncategorised reason | We are sorry — your application cannot be approved at this time. Please contact us for more information. |

Notes:

- Codes are terminal (R-\*) except RW-LPO and RW-AO, which return the application to the customer for rework, as today.
- The English messages above are the master text; the Kjey PAPA team produces and maintains the Khmer translations keyed on these codes, so Sambat only needs to send the code.
- If Sambat's internal workflow distinguishes further sub-reasons, they can be mapped onto these codes on your side without an app change.

**Please confirm or amend.**

---

## 2. Proposed Occupation & Employment Status Option Lists (QC2.2)

Per your answer ("create by default, will update later"), we will ship both fields as dropdown lists with the defaults below. They are configuration, not code: we can replace or extend either list on your word, with no app release required for the values themselves.

**Occupation (18 options)**

1. Factory Worker
2. Teacher
3. Civil Servant
4. Accountant
5. Sales Staff
6. Driver
7. NGO Officer
8. Nurse
9. Engineer
10. Technician
11. Security Guard
12. Cashier
13. Administrative Staff
14. Manager
15. IT Staff
16. Construction Worker
17. Hospitality Staff
18. Other

**Employment Status (5 options)**

1. Permanent / Confirmed
2. Probation
3. Contract (fixed-term)
4. Part-time
5. Other

The lists target the expected customer base — employees of private companies, garment factories, and NGOs, plus civil servants — and the Employment Status list covers the "Confirmed probation" example shown in the V8 mockups (as "Probation", with "Permanent / Confirmed" for confirmed staff).

**Please confirm or amend.**

---

## 3. Proposed Repayment-Amount Tier List (QC1.2)

Per your answer, tiers should be based on the repayment schedule. For the Payday product — capped at USD 50, single repayment, maximum 30-day term — we propose the customer selects the **total repayment amount** from fixed tiers:

**USD tiers:** $10 · $20 · $30 · $40 · $50

**KHR tiers:** 40,000 ៛ · 80,000 ៛ · 120,000 ៛ · 160,000 ៛ · 200,000 ៛

The KHR tiers assume an indicative rate of ~4,100 KHR/USD, rounded to sensible KHR note denominations. The exact KHR values (and the applicable exchange-rate policy) are Sambat's to set — we will load whatever figures you confirm.

**Worked example — $50 tier, 15-day period (confirmed pricing: 1.5% monthly interest pro-rated by loan period; processing fee $3; CBC enquiry fee $1):**

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

One point we ask you to confirm explicitly: our reading is that interest is **pro-rated by loan period** — i.e. 1.5% × days/30 — since that is the only interpretation that reproduces the figures in your mockups exactly (e.g. 0.75% for the 15-day period shown). Please confirm this proration rule so we can lock the calculation.

**Please confirm or amend.**

---

## Closing

These defaults will ship in the current build cycle so that end-to-end testing is not blocked, and every item above is replaceable on your confirmation — none of it is hard-coded against your final decision. For traceability, this document responds to the "[Please recommend]" / "create by default" items in your answers to our 2026-08-13 addendum, questions **QB4.5** (reject codes), **QC2.2** (occupation and employment status lists), and **QC1.2** (repayment tiers).

We look forward to your confirmation or amendments.
