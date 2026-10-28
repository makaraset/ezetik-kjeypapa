# Our webhook endpoints — what Sambat LOS calls on us

| | |
|---|---|
| **For** | Sambat Finance / Tricube LOS — integration developers |
| **From** | Kjey PAPA (Ezetik) |
| **Date** | 2026-10-28 (first issued 2026-09-07) |
| **Subject** | The seven endpoints you push to, their exact bodies, and how we answer |

This is the reverse direction of `LOS_Integration_API_Spec.md`: everything here
is **you calling us**. All seven are built, live on our UAT, and exercised
end-to-end.

**Two machine-readable files ship with this document** — import either rather
than typing the bodies out:

| File | What it is |
|---|---|
| `api/kjeypapa-los-webhooks.openapi.json` | OpenAPI 3.1, these seven endpoints and their schemas only. Imports into Swagger UI, Postman or Insomnia. |
| `api/kjeypapa-los-webhooks.postman_collection.json` | Postman collection with a working example body for each. `base` is set to our UAT host; set `secret` to the value we send you. |

Every example body in the collection was sent against our UAT and accepted. They
use `appId: 999999`, which deliberately matches no application, so running the
collection as-is answers `NOT_FOUND` and cannot alter a real loan — replace it
with the AppId from your own submit response.

---

## 1. Where to send

```
POST  https://kjeypapa-uat.sambatfinance.com.kh/api/v1/pdl/los/{event}
Content-Type: application/json
X-LOS-Signature: <the shared secret we send you>
```

That is our UAT host. We will confirm the production host with you before go-live.

Server-to-server. **No customer JWT**: the shared secret in section 3 is what
authenticates you.

## 2. How we answer

Every response is the same envelope, always `application/json`:

```json
{ "type": "SUCCESS", "message": "…", "data": null }
```

| `type` | HTTP | Meaning | Should you retry? |
|---|---|---|---|
| `SUCCESS` | 200 | Applied. | No |
| `SUCCESS`, message starts `Ignored —` | 200 | Understood, no action taken — a transition that no longer applies to the loan's state, or a feature switched off. The message says which. | No |
| `NOT_FOUND` | 200 | No loan matches the identifier you sent. The message names it. | Not automatically — see §4 |
| `BAD_REQUEST` | 400 | We could not read the body, or it names no application. The message says which. | No, fix and resend |
| `UNAUTHORIZED` | 401 | `X-LOS-Signature` missing or wrong. Nothing was processed. | No, fix the header |
| `FORBIDDEN` | 403 | Only once we allowlist your addresses (§3): the call came from another one. | No, tell us |
| `INTERNAL_SERVER_ERROR` | 500 | Failed on our side. | Yes, later |
| `SERVICE_UNAVAILABLE` | 503 | Our authentication is misconfigured. Nothing was processed. | Yes, later, and tell us |

No response at all, or a timeout: retry.

Note the second row: a no-op is a **success**, not a separate status. Your queue
should treat any 200 as delivered; the message is for a human reading a log, not
for branching on.

**A duplicate delivery is safe.** Every handler is idempotent and guarded on the
loan's current status: re-sending an `approved` you already sent does not create
a second schedule, and a message that no longer applies to the loan's state
is acknowledged and ignored rather than corrupting it. Retry freely on a network failure.

## 3. Authentication

**Required on every call.** Send the shared secret we give you, unchanged, in
the `X-LOS-Signature` header. We send the value separately, never in this
document or the files that come with it.

Without it, or with a wrong one, you get HTTP 401:

```json
{ "type": "UNAUTHORIZED", "message": "Missing or wrong X-LOS-Signature header.", "data": null }
```

We check the header before reading the body, so a refused call never reaches a
loan.

**Optional, on request:** a source-address allowlist on top of the secret. Send
us the addresses your LOS calls from and we will add it; calls from anywhere
else then get HTTP 403. If you would rather sign each message (an HMAC over the
body) or use mutual TLS, tell us. Every payload still carries an unused
`signature` field for the HMAC case.

## 4. How we find the loan

**Send `appId`**: the AppId your `/new-loan-application` returned. You confirmed
on 2026-09-03 that it is the id you identify an application by.

Two others are accepted, for older callers: `losApplicationNo` (your AppRefId,
used only when there is no `appId`) and `loanRefNo` (your loan reference, tried
last).

If nothing matches we answer `NOT_FOUND` with HTTP 200 rather than an error, so
your queue does not treat it as a transport failure, and name what we looked for:

```json
{ "type": "NOT_FOUND", "message": "Application not found: appId 999999", "data": null }
```

Investigate rather than retry — it usually means the application was filed but
our record of the id is wrong, which is worth a look on both sides.

A body with none of the three is malformed, not missing, and gets HTTP 400. Field
names are case-sensitive, so `AppId` for `appId` lands here too.

## 5. Date and number formats — please read

**Every timestamp must be a full ISO-8601 instant.** `2026-08-21T09:00:00Z` is
accepted; a bare `2026-08-21` is not, and neither is a local time with no zone.

This previously answered with an opaque **HTTP 403 and an empty body**, which
sent us looking at credentials for a day before we found the real cause. As of
2026-09-07 it answers properly:

```json
{
  "type": "BAD_REQUEST",
  "message": "Field 'disbursedDate' is a date-time and must be a full ISO-8601 instant such as 2026-08-21T09:00:00Z. Received: '2026-09-07'.",
  "data": null
}
```

It names the field even inside a nested schedule row. Amounts are JSON numbers,
not strings.

---

## 6. The seven endpoints

### 6.1 `POST /api/v1/pdl/los/approved`

The decision, with terms, schedule and generated documents. The one that carries
the most.

| Field | Type | Notes |
|---|---|---|
| `appId` | int64 | **Preferred identifier** |
| `losApplicationNo` | string | Alternative identifier |
| `loanRefNo` | string | Your loan reference |
| `statusCode`, `message` | string | Shown to the customer |
| `loanFormRef`, `loanContractFileRef`, `repaymentScheduleRef`, `loanDocRef` | string | Generated document references |
| `settlementAccountNo`, `currency`, `tenor` | string / string / int | Loan terms |
| `outstandingAmount` | number | |
| `repaymentAmount`, `interestAmount`, `interestRatePercent`, `processingFee`, `cbcEnquiryFee`, `netDisbursedAmount` | number | Fee set — these **override** the quote we computed at request time |
| `loanPeriodDays` | int | |
| `schedule[]` | array | `installmentNo` (int), `dueDate` (instant), `principalDue`, `interestDue`, `feeDue`, `otherDue`, `totalDue` (numbers) |
| `signature` | string | Unused today, see §3 |

**A field you leave out keeps the value we hold.** That includes `currency`,
which comes from the customer's application. Send it anyway: the customer's
acceptance screen shows the amounts in it.

Applied to a loan awaiting a decision. Sent again before the customer accepts,
it replaces the terms and the schedule. Anything else is acknowledged with an
`Ignored — application is <status>` message.

### 6.2 `POST /api/v1/pdl/los/reject`

Same body as `approved`; only the identifier, `statusCode` and `message` are
read. The message reaches the customer as the reason, exactly as you send it,
so please send something a person can act on. For `R-LPO` and `R-AO` the
customer sees our standard wording instead.

Applied while the application awaits a decision, or is approved but not yet
accepted by the customer. Once accepted, a reject is acknowledged and ignored.

### 6.3 `POST /api/v1/pdl/los/rework`

Same body. **For PDL we treat rework as a rejection** (BRS 2.5.1) — there is no
Rework state in this product. Tell us if that is wrong.

### 6.4 `POST /api/v1/pdl/los/disbursement`

| Field | Type | Notes |
|---|---|---|
| `appId` / `losApplicationNo` / `loanRefNo` | | Identifier |
| `eventId` | string | Yours, for de-duplication |
| `disbursementStatus` | string | `DISBURSED` / `FAILED` / `PENDING` |
| `disbursementTxnId` | string | |
| `disbursedAmount` | number | |
| `disbursedDate` | **instant** | Full ISO-8601 — see §5 |
| `failureReason` | string | On `FAILED` |

### 6.5 `POST /api/v1/pdl/los/loan-update`

Servicing: balances, arrears and what has been paid.

| Field | Type | Notes |
|---|---|---|
| `appId` / `losApplicationNo` / `loanRefNo` | | Identifier |
| `eventId` | string | |
| `status` | string | `Active` / `Closed` / `Overdue` |
| `outstandingAmount`, `overduePayment`, `lastPaidAmount` | number | |
| `daysPastDue` | int | |
| `lastTransactionDate` | **instant** | |
| `schedule[]` | array | Per installment: `installmentNo`, `principalPaid`, `interestPaid`, `feePaid`, `penaltyPaid`, `otherPaid`, `totalPaid`, `amountPaid`, `paidDate`, `transactionDate`, `status` (`PAID` / `OVERDUE` / `PENDING`) |

A fully repaid loan sent with `status: "Closed"` is what moves it to the
customer's Closed tab and stamps the end of the loan file.

### 6.6 `POST /api/v1/pdl/los/bank-verification`

The result of the bank-app payroll-account authorisation.

| Field | Type | Notes |
|---|---|---|
| `appId` / `losApplicationNo` / `loanRefNo` | | Identifier |
| `eventId` | string | |
| `verificationStatus` | string | `SUCCESS` / `FAILED` / `PENDING` |
| `disbursementTxnId` | string | |
| `failureReason` | string | A machine code, which we map to customer wording |
| `verificationDate` | **instant** | |

`SUCCESS` moves the loan on; `FAILED` keeps it accepted with a re-attempt
available until the 17:00 cut-off — it does **not** reject the application.

This shape is our proposal, since the bank contract does not exist yet. Change
it freely and we will follow.

### 6.7 `POST /api/v1/pdl/los/product-sync`

| Field | Type |
|---|---|
| `productCode` | string — `PDL` / `MSIL` / `PL` |
| `name` | string |
| `minAmount`, `maxAmount` | number |
| `active` | boolean |

> **Accepted but not yet applied.** This endpoint answers `SUCCESS` and the
> payload is logged, but the product configuration is **not stored** — our
> pricing tiers are still configuration on our side, per the ruling that we own
> the quote. Please do send it if it is convenient; we will wire it to real
> product config when you want the catalogue to drive our tiers, and we would
> rather tell you now than have you assume it takes effect.

---

## 7. What we need back from you

1. **Confirmation that you send `X-LOS-Signature`** on every call, or which
   alternative you need instead (HMAC or mTLS, §3).
2. **Your source IP addresses**, if you want the allowlist on top of the secret.
3. **Confirmation that rework = reject** for PDL (§6.3).
4. **The bank-verification body**, once that contract exists (§6.6).
5. Whether `/payday-disbursement` is a call **we** make after disbursement, or
   entirely yours.

Nothing above is blocked on these: all seven endpoints accept authenticated
traffic today.
