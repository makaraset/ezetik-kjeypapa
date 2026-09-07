# Our webhook endpoints — what Sambat LOS calls on us

| | |
|---|---|
| **For** | Sambat Finance / Tricube LOS — integration developers |
| **From** | Kjey PAPA (Ezetik) |
| **Date** | 2026-09-07 |
| **Subject** | The seven endpoints you push to, their exact bodies, and how we answer |

This is the reverse direction of `LOS_Integration_API_Spec.md`: everything here
is **you calling us**. All seven are built, live on our UAT, and exercised
end-to-end.

**Two machine-readable files ship with this document** — import either rather
than typing the bodies out:

| File | What it is |
|---|---|
| `api/kjeypapa-los-webhooks.openapi.json` | OpenAPI 3.1, these seven endpoints and their schemas only. Imports into Swagger UI, Postman or Insomnia. |
| `api/kjeypapa-los-webhooks.postman_collection.json` | Postman collection with a working example body for each. Set the `base` variable to the host we give you. |

Every example body in the collection was sent against our UAT and accepted. They
use `appId: 999999`, which deliberately matches no application, so running the
collection as-is answers `NOT_FOUND` and cannot alter a real loan — replace it
with the AppId from your own submit response.

---

## 1. Where to send

```
POST  {base}/api/v1/pdl/los/{event}
Content-Type: application/json
```

`{base}` is the Kjey PAPA backend host — we will confirm the UAT and production
hostnames with you separately, as they are not fixed in this document.

Server-to-server. **No customer JWT.** These paths are on our security
whitelist, which is why the authentication in section 3 matters.

## 2. How we answer

Every response is the same envelope, always `application/json`:

```json
{ "type": "SUCCESS", "message": "…", "data": null }
```

| `type` | HTTP | Meaning | Should you retry? |
|---|---|---|---|
| `SUCCESS` | 200 | Applied. | No |
| `SUCCESS`, message starts `Ignored —` | 200 | Understood, no action taken — a transition that no longer applies to the loan's state, or a feature switched off. The message says which. | No |
| `NOT_FOUND` | 200 | No loan matches the identifier you sent. | Not automatically — see §4 |
| `BAD_REQUEST` | 400 | We could not read the body. The message names the field. | No, fix and resend |

Note the second row: a no-op is a **success**, not a separate status. Your queue
should treat any 200 as delivered; the message is for a human reading a log, not
for branching on.

**A duplicate delivery is safe.** Every handler is idempotent and guarded on the
loan's current status: re-sending an `approved` you already sent does not create
a second schedule, and a message that no longer applies to the loan's state
is acknowledged and ignored rather than corrupting it. Retry freely on a network failure.

## 3. Authentication — needs your decision

Built and **currently disabled** (`pdl.webhook.auth.enabled=false`), because we
do not want to guess the mechanism and lock you out. Two layers are ready:

| Layer | Config | Behaviour |
|---|---|---|
| Source-IP allowlist | `pdl.webhook.allowed-ips` | Requests from other addresses are rejected |
| Shared secret header | `pdl.webhook.secret`, header name `X-LOS-Signature` | Constant-time comparison |

> **We need from you:** the source addresses your LOS calls from, and whether
> you prefer a static shared secret, an HMAC over the body, or mTLS. Every
> payload below already carries an unused `signature` field if you want to sign
> per-message. **This must be switched on before we go live** — until it is,
> these endpoints are reachable by anyone who knows the URL.

## 4. How we find the loan

You send one identifier; we try three things in order:

1. `losApplicationNo` — matched against the reference we stored at submit
2. `appId` — matched against the `AppId` you returned from `/new-loan-application`
3. `loanRefNo` — your own loan reference, once the loan exists

**Prefer `appId`.** You confirmed on 2026-09-03 that it is the id you identify an
application by, and it is the one we key on.

If nothing matches we answer `NOT_FOUND` with HTTP 200 rather than an error, so
your queue does not treat it as a transport failure. Investigate rather than
retry — it usually means the application was filed but our record of the id is
wrong, which is worth a look on both sides.

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

Applied only to a loan awaiting a decision; anything else is acknowledged with
an `Ignored — application is <status>` message.

### 6.2 `POST /api/v1/pdl/los/reject`

Same body as `approved`; only the identifier, `statusCode` and `message` are
read. The message reaches the customer, so please send something a person can
act on.

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

1. **The source IP addresses** your LOS calls from.
2. **The authentication mechanism** — shared secret, HMAC, or mTLS (§3).
3. **Confirmation that rework = reject** for PDL (§6.3).
4. **The bank-verification body**, once that contract exists (§6.6).
5. Whether `/payday-disbursement` is a call **we** make after disbursement, or
   entirely yours.

Nothing above is blocked on these: all seven endpoints accept traffic today, and
turning authentication on is a configuration change on our side once you tell us
what to expect.
