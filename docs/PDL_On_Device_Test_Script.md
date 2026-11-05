# PDL On-Device Test Script (mock LOS)

Manual end-to-end test of the Payday Loan customer journey against the **mock LOS**
backend. Validates V21 gaps **G1, G2, G3, G5, G6** plus the full flow. Because LOS
notifications are **push (webhooks)**, some state changes (approve / reject / disburse /
loan-update) are driven by `curl` while you observe the app — those are the **"DRIVE"**
steps. You tap the **"APP"** steps.

**Test account:** `012551101` / `123456` (CUSTOMER).
Estimated time: ~20 min (plus optional G3 cron test).

---

## 0. Prerequisites

- [ ] **Postgres** up: `docker ps` shows `kjeypapa-pg` (host port 55432).
- [ ] **Backend** running on `:9090` with mock LOS (`los.mock.enabled=true`):
  ```
  cd <backend>
  JAVA_HOME=<jdk-21> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.arguments="--server.port=9090 --spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/kjey_papa_db"
  ```
- [ ] **App points at the backend** — pick per device:

  | Target | `API_BASE_URL` to pass at run |
  |--------|------------------------------|
  | iOS simulator | (default) `http://localhost:9090/api/v1` — no define needed |
  | Android emulator | `http://10.0.2.2:9090/api/v1` |
  | Physical device (same Wi-Fi) | `http://<MAC_LAN_IP>:9090/api/v1` |

  Run e.g.: `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:9090/api/v1`
  Find the Mac IP: `ipconfig getifaddr en0`. For a physical device also confirm the
  backend binds `0.0.0.0:9090` (it does) and the macOS firewall allows inbound 9090.

### Driver setup (a terminal for the DRIVE curls)

```bash
BASE=http://127.0.0.1:9090/api/v1
TOKEN=$(curl -s -X POST "$BASE/auth/authenticate" -H "Content-Type: application/json" \
  -d '{"username":"012551101","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
echo "token: ${TOKEN:0:16}..."

# Helper: each application's id (its mobileAppId) / status
apps(){ curl -s "$BASE/pdl/my-transactions" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json;[print(a.get('id'),a.get('status')) for a in (json.load(sys.stdin).get('data') or [])]"; }
apps   # run after each app action to read id / status
```

---

## A. Profile capture — 3 documents at signup (G2)

1. [ ] **APP** Login as `012551101 / 123456`.
2. [ ] **APP** Open the drawer → **Payday Loan** → PDL dashboard.
3. [ ] **APP** **My Profile** tab → **Update** → walk the signup steps: **ID (NID front + back)**,
       Personal, Employment, **Bank (upload Bank Statement)**, and the **selfie**.
   - **Expect:** the signup **Employment step has NO "Employment Card" upload** (G2 — removed).
   - **Expect:** you capture exactly **NID front+back, selfie, bank statement** (3 docs).
4. [ ] **APP** Finish; profile saves without error.

---

## B. G6 — Length of Service + OTP rule

5. [ ] **APP** My Profile → **Employment** tab.
   - **Expect:** a **"Length of Service"** row, derived from the employment start date
     (e.g. "2 Year 3 Month"). (G6)
6. [ ] **OTP 3-try (registration flow, separate):** on a fresh sign-up, enter a **wrong OTP 3×**.
   - **Expect:** after the 3rd wrong entry → error + **kicked back to the start of registration**.
   - **Expect:** tapping **Resend more than 3×** → same timeout/restart. (G6)
   - *Skip if you don't want to exercise real registration/SMS; logic is otherwise unit-covered.*

---

## C. Loan request — lean V21 form + currency (G1)

7. [ ] **APP** Dashboard → **Request** a new Payday Loan.
   - **Expect (G1):** a read-only **"Confirm Your Information"** card (name / employer / bank / account),
     a **currency dropdown (USD / KHR)**, an **amount** field, and a **CBC-consent** checkbox — **and nothing else**.
   - **Expect (G1):** **NO** loan-period field, **NO** document pickers, **NO** bank-consent checkbox.
8. [ ] **APP** Select **KHR**, enter an amount (e.g. `500`), tick **CBC consent**, submit.
   - **Expect:** submit is blocked until amount > 0 **and** CBC consent is ticked.
9. [ ] **DRIVE** `apps` → note the new **id** (its `mobileAppId`), status **Submitted**.

> **G2 negative check (optional):** on a profile missing the bank-statement ref, submit should
> return **`MISSING_DOCUMENT` — "Missing document: bank statement"**. (Requires clearing
> `bankStatementFileRef`; skip unless verifying the gate explicitly.)

---

## D. LOS approve + Accept (sets up G3)

Set the application id from step 9. Every callback names the application by
it, as `mobileAppId`:
```bash
ID=<id>     # <-- from `apps`
```

10. [ ] **DRIVE** push the **approved** webhook:
    ```bash
    curl -s -X POST "$BASE/pdl/los/approved" -H "Content-Type: application/json" -d '{
      "mobileAppId":'"$ID"',
      "message":"Approved",
      "loanRefNo":"PDL-2026-TEST",
      "currency":"KHR",
      "tenor":3,
      "outstandingAmount":540.0,
      "settlementAccountNo":"000123456",
      "schedule":[
        {"installmentNo":1,"principalDue":166.66,"interestDue":13.34,"feeDue":0.0,"otherDue":0.0,"totalDue":180.0},
        {"installmentNo":2,"principalDue":166.66,"interestDue":13.34,"feeDue":0.0,"otherDue":0.0,"totalDue":180.0},
        {"installmentNo":3,"principalDue":166.68,"interestDue":13.32,"feeDue":0.0,"otherDue":0.0,"totalDue":180.0}
      ]}' ; echo
    ```
    - **Expect:** `{"type":"SUCCESS",...}`.
11. [ ] **APP** Pull-to-refresh **My Application**.
    - **Expect:** status **Approved**; an **Accept / Reject / Revoke** action set on the card.
12. [ ] **APP** Tap **Accept**, confirm.
    - **Expect (D):** `apps` shows status **Accepted**.

---

## E. G3 — reminder + daily cut-off auto-reject (optional / advanced)

The scheduler is unit-tested; this exercises it live. It needs a backend restart with a
near-future cron, so it's **optional**.

13. [ ] Leave an **Approved (un-accepted)** loan (approve another via step 10, don't Accept).
14. [ ] **Reminder:** between 08:00 and 16:30 Phnom-Penh time, at the next :00/:30 tick →
    **Expect** a push: *"Please review and confirm your loan offer before 5 PM today."*
15. [ ] **Cut-off (fast recipe):** set in `application.properties`
    `pdl.acceptance.cutoff-cron=0 <now+2min> * * * *` and `pdl.acceptance.grace-minutes=0`,
    restart the backend, wait for the tick.
    - **Expect:** the Approved-un-accepted loan → **Rejected** with Sambat's lapse wording,
      *"Apologies. Your loan request is lapsed due to failure in providing your final decision.
      Please try it again on the next working day."* (in Khmer when the app is in Khmer), a push
      arrives, and the mock LOS receives an `"N"` decision (backend log).
    - **Expect:** the **Accepted** loan from step 12 is untouched: it waits for the disbursement
      in G. Only with `pdl.bank-verification.enabled=true` does an Accepted loan expire too (F23).
    - **Restore** the cron/grace afterwards.

---

## F. Sambat's rejection codes (Mobile App Dictionary, 30 Oct 2026)

Sambat send a code and no text, so the wording is ours to show. (This section used to test an
`R-BANK` reject with a Re-attempt button. Sambat have no bank failure codes, QB4.3, and the
bank re-attempt lives in the hand-off, section H.)

Use a **fresh** application (reject only acts on Submitted/Approved). Submit a new loan in the
app (steps 7–9), set `ID` to its id, then:

16. [ ] **DRIVE** reject it the way Sambat will, with a code and no message:
    ```bash
    curl -s -X POST "$BASE/pdl/los/reject" -H "Content-Type: application/json" \
      -d '{"mobileAppId":'"$ID"',"statusCode":"R-AO"}' ; echo
    ```
17. [ ] **APP** Refresh **My Application**.
    - **Expect:** status **Rejected**, Reason *"Apologies. Your loan application has been
      rejected."* and the hotline footer below it. The push reads the same, then *"Please call
      023 9977 22 or 093 99 77 22 for more information."*
    - **Expect:** switch the app to Khmer and the Reason reads
      *សូមអភ័យទោស។ ពាក្យស្នើសុំកម្ចីរបស់អ្នកត្រូវបានបដិសេធ។*
18. [ ] **DRIVE + APP** Repeat 16–17 on another fresh application with `"statusCode":"R-LPO"`.
    - **Expect:** *"Apologies. Your application has been rejected due to insufficient
      information or documents."*, and its Khmer in Khmer.

---

## G. Disbursement + repayment update (full-flow display)

Use the **Accepted** loan from step 12 (`ID` = its id).

19. [ ] **DRIVE** push **disbursement**:
    ```bash
    curl -s -X POST "$BASE/pdl/los/disbursement" -H "Content-Type: application/json" -d '{
      "mobileAppId":'"$ID"',
      "disbursementStatus":"DISBURSED","disbursementTxnId":"TXN-001","disbursedAmount":500.0}' ; echo
    ```
    - **Expect:** status **Disbursed**; the **My Loan** tab populates (ref, tenor, outstanding),
      and **Payment Record** shows the 3-installment schedule.
20. [ ] **DRIVE** push a **loan-update** (a repayment):
    ```bash
    curl -s -X POST "$BASE/pdl/los/loan-update" -H "Content-Type: application/json" -d '{
      "mobileAppId":'"$ID"',"status":"Active",
      "outstandingAmount":360.0,"daysPastDue":0,"lastPaidAmount":180.0,
      "schedule":[{"installmentNo":1,"principalPaid":166.66,"interestPaid":13.34,"totalPaid":180.0,"amountPaid":180.0,"status":"PAID"}]}' ; echo
    ```
    - **Expect:** **My Loan** outstanding drops to 540→360; **Payment Record** row 1 shows PAID.

---

## H. Bank-verification endpoint is inert (G4 scaffold, flag-off)

21. [ ] **DRIVE**:
    ```bash
    curl -s -X POST "$BASE/pdl/los/bank-verification" -H "Content-Type: application/json" \
      -d '{"mobileAppId":'"$ID"',"verificationStatus":"SUCCESS"}' ; echo
    ```
    - **Expect:** `"Ignored — bank-verification hand-off not enabled (pending Sambat contract, Q4)"`
      and **no** status change in the app. (Confirms the G4 scaffold stays dormant until enabled.)

---

## I. Phase-2 additions (V8 foundations — test after the A–H flow)

> New since the July run: V8 icon nav, buckets, acceptance page, viewers, typed pushes.

22. [ ] **APP** Dashboard shell: circular icon nav **My Profile / My Application / My Loan / Request**
    (no Payment Record item); tapping **Request** opens the request flow. (G18)
23. [ ] **APP** **My Application** tab: **Processing | Approved | Rejected** pills filter the list;
    cards show dates/period/amount rows when populated; a Rejected card shows a labeled
    **Reason** row + the hotline footer; **no Re-attempt button on Rejected**. (G16/QB4)
24. [ ] **APP** An **Approved** application shows one **View / Confirm** button → the full
    **acceptance page**: fee table (rate, processing fee, CBC fee, **Net Loan Received**),
    "credited to the account below" bank block, **Loan Documents [View]** buttons, and a T&C
    checkbox that **enables** Confirm/Reject only when ticked. (G13)
    - **DRIVE** to stage one: approve a fresh Submitted app (step 10 curl) — include
      `"loanFormRef":"<any-stored-file>","loanContractFileRef":"<any-stored-file>"` plus the fee
      fields (`"interestRatePercent":1.5,"processingFee":3.0,"cbcEnquiryFee":1.0,
      "netDisbursedAmount":45.63,"repaymentAmount":50.0,"loanPeriodDays":15`).
25. [ ] **APP** Tap a **[View]** on a loan document → the in-app viewer renders the image
    (pinch-zoom); a bogus ref shows the "preview unavailable" placeholder. (G15)
26. [ ] **APP** Confirm acceptance (checkbox → **Confirm**) → full-screen **"Loan Accepted"**
    result; Reject shows **"Loan Rejected by You"**. (V8 21/22)
27. [ ] **APP** **My Loan**: **Active | Closed** pills; an Active card shows **CBC Consent [View]**
    (→ the consent record page) and **Loan Contract [View]**; **View Schedule** opens the
    standalone **Payment Record** page. A Closed loan renders the settlement card. (G17/QC3.2)
28. [ ] **APP** **My Profile**: 3 segments (Personal / Address / Employment); the **bank card sits
    inside Personal**; bottom shows **Update** + **Request for New Loan**. (G18)
29. [ ] **Typed push deep-link (G19):** with the app in the **background**, DRIVE any webhook that
    pushes (approve/reject/disburse) → tap the system banner → the app opens the matching
    **full-screen result screen** (not the home). In the notification center list, positive events
    show the **green tick**, rejects/failures the **red cross** (the old red-X-everywhere bug).
    *(Simulator note: APNS pushes don't arrive on the iOS simulator — test push-taps on a real
    device; on the sim verify the icon mapping + result screens by navigation.)*
30. [ ] **G21 spot-checks:** About App + drawer footer show the real version (1.2.5+16);
    registration password accepts >6 chars; logout returns to Sign In with the back-stack cleared.

> **Profile prep for the V8 submit gate:** the gate now also requires **NID back + employment
> card** refs. For the legacy test account, copy an existing stored ref into the missing fields
> (POST /pdl/personal-info with nidBackFileRef, POST /pdl/employment-info with
> employmentCardFileRef) before testing submit.

## Results summary

| # | Area | Gap | Pass? | Notes |
|---|------|-----|-------|-------|
| A | 3-doc signup, no employment-card | G2 | ☐ | |
| B | Length of Service shown | G6 | ☐ | |
| B | OTP 3-try → restart | G6 | ☐ | |
| C | Lean request form + currency | G1 | ☐ | |
| C | No docs/period/bank-consent | G1 | ☐ | |
| D | Approve → Accept | — | ☐ | |
| E | Cut-off auto-reject + reminder | G3 | ☐ | optional |
| F | Sambat's rejection codes, EN + KM | Dictionary | ☐ | |
| G | Disburse + repayment display | — | ☐ | |
| H | Bank-verification endpoint inert | G4 | ☐ | |
| I | V8 IA / buckets / accept page / viewers / pushes | G13-G19 | ☐ | Phase 2 |

**Environment:** device ____________ · API_BASE_URL ____________ · date ____________ · tester ____________

> If any step fails, capture the app screen + the backend log line and note the `id`.
> Reminder: this runs against **mock LOS** — the real `newAppRequest` submit is still pending Sambat.
