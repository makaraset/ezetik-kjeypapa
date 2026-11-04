# How to send approve and reject

Your LOS gives its decision on a payday-loan application by calling one of two
endpoints on our server. This note covers just those two. The other endpoints,
with an OpenAPI file and a Postman collection, are in *Our webhook endpoints —
what Sambat LOS calls on us* (28 October 2026).

## 1. The request

```
POST https://kjeypapa-uat.sambatfinance.com.kh/api/v1/pdl/los/approved
POST https://kjeypapa-uat.sambatfinance.com.kh/api/v1/pdl/los/reject

Content-Type: application/json
X-LOS-Signature: <the shared secret>
```

- **`appId` names the application.** Send the `AppId` your
  `/new-loan-application` returned, as a number. Field names are
  case-sensitive, so we do not read `AppId`.
- **`X-LOS-Signature` shows the call is yours,** in place of any login. We
  send you the secret separately, never in a document. A call without it, or
  with a wrong one, gets HTTP 401 and changes nothing.

> **UAT today.** UAT still runs our previous build, and we will tell you before
> we deploy the next one. Until then the header is not checked, an unknown
> `appId` answers `Application not found: null`, a body that names no
> application answers `NOT_FOUND` instead of 400, and an approve without
> `currency` loses it, so riel amounts show as dollars. A loan the customer has
> accepted also expires on our side at 17:00 if you have not disbursed it by
> then.

## 2. Approve: `POST /api/v1/pdl/los/approved`

The decision, the final terms and the repayment schedule, in one call:

```json
{
  "appId": 999999,
  "currency": "USD",
  "repaymentAmount": 50.0, "interestAmount": 0.37,
  "interestRatePercent": 1.5, "processingFee": 3.0,
  "cbcEnquiryFee": 1.0, "netDisbursedAmount": 45.63,
  "loanPeriodDays": 15,
  "loanRefNo": "PDL-2026-0042", "settlementAccountNo": "1122334",
  "tenor": 1, "outstandingAmount": 50.0,
  "schedule": [
    { "installmentNo": 1, "dueDate": "2026-09-21T00:00:00Z",
      "principalDue": 49.63, "interestDue": 0.37, "feeDue": 0.0,
      "otherDue": 0.0, "totalDue": 50.0 }
  ],
  "loanFormRef": "form-999999.pdf",
  "loanContractFileRef": "contract-999999.pdf",
  "repaymentScheduleRef": "schedule-999999.pdf",
  "loanDocRef": "docs-999999.zip"
}
```

Only `appId` is required, but send the full set every time.

- **`currency`**: `USD` or `KHR`. The customer's acceptance screen shows every
  amount in it.
- **The amounts and `loanPeriodDays`** are the terms the customer accepts.
  They replace the quote we showed when the customer applied.
- **`loanRefNo`, `settlementAccountNo`, `tenor`, `outstandingAmount`**: the
  loan as you booked it, for the customer's My Loan screen.
- **`schedule`**: one row per instalment. `dueDate` is a full ISO-8601 instant
  such as `2026-09-21T00:00:00Z`; a bare `2026-09-21` gets HTTP 400.
- **The four `…Ref` fields**: the documents you generated.
- **`statusCode`, `message`**: optional. We keep them with the decision.

**When it applies:** to an application waiting for your decision. Sent again
before the customer accepts, it replaces the terms and the schedule. At any
other point it is ignored (section 4).

**What happens next**

- The customer gets a notification and sees your terms in the app.
- They must accept before **17:00 Phnom Penh time**. At 17:00 each day, every
  offer not yet accepted expires, with no grace period. An approval sent at
  16:50 leaves the customer ten minutes; one sent after 17:00 lasts until
  17:00 the next day.
- When they accept, we call your `POST /customer-accepted` with the AppId.
  That is the step that lets you move the loan to disbursement.
- If they decline, or the offer expires, we close the application on our side.
  Your API has no endpoint for us to report either, so you do not hear from
  us. See question 3.

## 3. Reject: `POST /api/v1/pdl/los/reject`

```json
{
  "appId": 999999,
  "statusCode": "R-AO",
  "message": "Not eligible for the loan"
}
```

We read only these three fields. The customer sees the reason. For `R-LPO`
they read *Insufficient Information or Documents*, and for `R-AO`, *Not
eligible for the loan*. For any other code they read your `message` exactly as
you send it, so write it for the customer.

**When it applies:** while the application waits for your decision, or after
you approved it but before the customer accepts. Once the customer has
accepted, a reject is ignored.

`/rework` takes the same body and, for payday loans, does the same as a
reject. Its codes are `RW-LPO` and `RW-AO`.

## 4. Our answer

Every answer is JSON in the same envelope:

```json
{ "type": "SUCCESS", "message": "Reject processed", "data": null }
```

| HTTP and `type` | Meaning | Retry? |
|------------------|-----------------------|--------|
| 200 `SUCCESS` | Done: `Approved processed` or `Reject processed`. | No |
| 200 `SUCCESS`, message `Ignored — application is …` | The call does not apply to the application now, so nothing changed. The message names its status, such as `Accepted`, or `Revoked` if the customer withdrew. | No |
| 200 `NOT_FOUND` | No application has that `appId`. The message names the id we looked for. | No, check the id |
| 400 `BAD_REQUEST` | We could not read the body, or it names no application. The message says which. | No, fix the body |
| 401 `UNAUTHORIZED` | The header is missing or wrong. Nothing was processed. | No, fix the header |
| 500 `INTERNAL_SERVER_ERROR` | A fault on our side. | Yes, later |
| 503 `SERVICE_UNAVAILABLE` | Our side is misconfigured. Nothing was processed. | Yes, later |

No answer, or a timeout: retry. Treat any 200 as delivered. Sending the same
call twice is safe, so retry freely after a network error.

## 5. Try it

`appId` 999999 matches no application, so this call changes nothing:

```sh
curl -s -X POST \
  https://kjeypapa-uat.sambatfinance.com.kh/api/v1/pdl/los/reject \
  -H 'Content-Type: application/json' \
  -H "X-LOS-Signature: $SECRET" \
  -d '{"appId":999999,"statusCode":"R-AO","message":"test"}'
```

The answer is `NOT_FOUND`, with the message `Application not found: appId 999999`.

## 6. What we need from you

1. Confirmation that you will send `X-LOS-Signature` on every call.
2. `/customer-accepted` has two optional fields, `smsText` and `trnCode`. What
   should they contain? We send both empty today.
3. Do you need to hear when a customer declines or an offer expires? If so,
   which endpoint should we call?
