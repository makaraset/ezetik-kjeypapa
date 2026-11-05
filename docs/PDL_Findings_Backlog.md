# PDL findings backlog

Incidental findings turned up while building something else, plus the items that
must happen before the first deployment. Kept here rather than in a commit
message because nothing in this list is finished, and a finding nobody can find
again is the same as one nobody made.

Started 2026-09-05 during the consent-record hardening work. Each entry says how
it was found, so a reader can judge how much it has actually been checked.

---

## Open findings

### F27 — a failed CIF lookup filed an existing customer as new
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-11-05, not yet deployed** ·
**Found:** 2026-11-05, reviewing how we get and store the CIF, at Sambat's request

Sambat's answers decide the severity: `custId` 0 for an ID number they already
hold earns a second CIF or an automatic reject. A new customer's CIF is created
on approval, one ID number has one CIF, a CIF never changes, and the match must
be the full identity.
- **A failed lookup read as a new customer.** Signup and the profile reload
  dropped any Sambat error without a log line, and the submit sent 0 for any
  blank CIF.
- **The match was the ID number alone.** We took the first record and never
  compared the date of birth, sex or name. A mistyped number belonging to
  someone else stored their CIF, and My Profile's settlement-account fallback
  would then have shown their account and balance.
- **The LPO approved without seeing the CIF.**
- **The lookup had no read timeout**, and at signup it runs inside the
  database transaction. The two saving-account lookups had none either.

Now (`SbfCifResolver`, `SbfCustomerMatch`):
- The submit settles the CIF first: a stored one, a full match it then
  stores, or a confirmed new customer (0). A Sambat outage holds the
  application (`SBF_CUSTOMER_UNAVAILABLE`); different details hold it too
  (`SBF_CUSTOMER_MISMATCH`, with the hotline).
- The check: ID number, date of birth and sex must agree, and the name in
  Khmer or Latin script. A blank or unreadable field counts as different.
- Signup and the profile reload store a CIF only on a full match. A stored CIF
  is final.
- The LPO review shows the CIF, "new to Sambat", what differs, or that Sambat
  could not be reached.
- `pdl.sbf.lookup-timeout-seconds` (10 s) on all three lookups.

Not done:
- the ID type is not compared (their `regisType` codes are unknown);
- the field formats of their record are unverified;
- CIFs stored before this change were matched on the ID number alone. Every row
  is a fixture before the first deployment, so none is re-checked.

Still open, not for Sambat: `registedId` also holds TFF CIFs, merchant codes and
staff usernames, and is not unique. A TFF customer who signs up for PDL ends up
as two users with one CIF. Whether one person may hold both accounts is a
product decision.

Unit-tested; not yet run against Sambat. `docs/probe-sbf-customer-shape.sh`
shows the forms their record uses.

### F26 — three ids for one application, and the customer saw two of them
**Severity:** medium · **Status:** ~~open~~ **FIXED 2026-11-05, not yet deployed** ·
**Found:** 2026-11-05, reviewing every id that crosses to Sambat

Our id, Sambat's LOS AppId and their AppRefId all crossed the boundary, with
the loan ref as a further fallback key.
- **`los.app-id` made the envelope's appId a setting.** Default 0, labelled
  "Mobile App ID" in Appendix 2. Any other value would have made every first
  submit an update of that one LOS application, filing each customer over the
  last.
- **The customer saw two App IDs.** The dashboard and the confirm page showed
  Sambat's AppId; the acceptance page and My Loan showed ours. "Loan Ref No"
  fell back to the AppId.
- **"Mark as filed" accepted an AppRefId alone.** That left a loan whose
  acceptance could never be relayed.
- **Callbacks looked an `appId` up in the AppRefId column first.** No live
  impact while AppIds are 4-digit and AppRefIds 6-digit.
- **Dev and UAT used different keys.** Mock mode ran on `losApplicationNo`
  (`LOS-MOCK-n`), UAT on `appId`.

Agreed with Sambat on 2026-11-05:
- one id, **`mobileAppId` = ours**, carried on every callback
- theirs is `losAppId`, kept only where a call needs it
- the loan ref is not an identifier
- `/payday-disbursement` is their own step
- customers see only the `mobileAppId`

Now:
- **Submit:** carries `mobileAppId`, and 0 is a constant.
- **`/customer-accepted`:** carries `mobileAppId` beside `appId`.
- **Callbacks:** found by `mobileAppId`. `appId` is still read until Sambat
  switch.
- **"Mark as filed":** requires the LOS AppId.
- **App:** shows only our id, and its models no longer hold the LOS ids. The
  customer projection no longer carries them either.

Waiting on Sambat (`LOS_Submit_Open_Questions.md`, last section):
- whether the envelope's `appId` moves to `losAppId`
- whether `/customer-accepted` keys on `mobileAppId`
- the date their callbacks switch

Unit-tested; not yet run on a device or against Sambat.

### F25 — our decisions to Sambat do not follow their Mobile App Dictionary
**Severity:** high · **Status:** open, waiting on Sambat · **Found:** 2026-11-05,
reviewing their *Mobile App_Dictionary.xlsx* (30 October)

The dictionary has us send the LOS one of three codes:
- **Y**: the customer accepted and the Campu Bank transaction succeeded. It
  carries the signed e-contract, e-schedule and e-application.
- **N**: the customer rejected, or accepted and the bank step failed.
- **E**: the offer lapsed unconfirmed.

We send only Y, at the moment the customer accepts, with nothing attached (the
"signed contract" is the app's placeholder `APP-SIGNED-{id}`).
`LosProviderImpl.sendDecision` drops N, believing Sambat had no decline call.
Their swagger describes `/customer-accepted` as "Accepted or Rejected from
customer". The cut-off also sends N where they want E.

Separately, the dictionary gives payday loans two approval codes: A-LPO, then
A-AO from the Approving Officer. Our `/approved` treats every call as the final
offer and ignores the code. If A-LPO reaches it, the customer can accept before
the officer decides, and a later R-AO is ignored once accepted.

Not built until Sambat answer:
- whether Y/N/E go in `trnCode` on `/customer-accepted`
- whether A-LPO is ever sent to us
- whether the money moves in the Campu Bank app or by the LOS after Y
- which field and format carry the attachments

### F24 — rejection wording: wrong for R-AO, English only, blank for an unknown code
**Severity:** medium · **Status:** ~~open~~ **FIXED 2026-11-05, not yet deployed** ·
**Found:** 2026-11-05, reviewing Sambat's Mobile App Dictionary (F25)

The dictionary says the LOS sends codes only, and gives the customer wording
for each.
- **R-AO** told payday-loan customers *Not eligible for the loan*, which is
  their Micro/Personal wording. For payday loans it is *Apologies. Your loan
  application has been rejected.* R-LPO and the cut-off's lapse message are now
  their sentences too.
- **Khmer users read English reasons.** The Rejected card showed the backend's
  English text. The app's own code-to-text mapping, Khmer included, was never
  called. The card now words the reason from the code, in the customer's
  language. Our own rejections carry codes for this, never sent to Sambat:
  R-CUTOFF as before, and R-CUSTOMER when the customer rejects.
- **An unknown code with no text showed no reason.** It now shows the generic
  rejection.
- **The reject and rework pushes carried the loan id as their body.** They now
  carry the reason and the hotline, as the dictionary's wording asks. A rework
  push says rejected, not "needs attention": payday loans have no rework.

The Khmer is our draft, because the dictionary is English only. The hotline
stands in for their "XXX" and is unconfirmed (`pdl.customer.hotline`). The
dead `R-BANK` mapping is gone, and test script §F now checks these codes in
place of it. Build 18 keeps showing the backend's English until the next app
build. Unit-tested; not yet run on a device.

### F23 — the 17:00 cut-off expired loans the customer had already accepted
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-11-04, not yet deployed** ·
**Found:** 2026-11-04, writing SBF's approve-and-reject guide

`PdlAcceptanceScheduler.enforceCutoff` swept `Accepted` loans along with
`Approved` ones. That is right for the V26 bank hand-off, where an accepted loan
whose hand-off has not succeeded by 17:00 expires (QB4.1). But the hand-off is
off in the app (`PdlFeatureFlags.bankAppHandoffEnabled`), so accepting goes
straight to Sambat as `/customer-accepted` and the loan only waits for their
disbursement. A loan accepted in the morning and not disbursed by 17:00 was
marked Rejected `R-CUTOFF` and the customer told the offer had expired, while
Sambat, who cannot be told (their API has no decline call), went on to disburse
it. Our disbursement webhook then moved it to Disbursed, after the "expired"
notification. Read from the code; not yet checked against UAT's data.

The sweep now takes `Accepted` loans only while `pdl.bank-verification.enabled`
is on, and that flag is off again, as its own comment and test script §H
already said it should be. Turn it on only together with the app's flag; a
test pins the scheduler and the webhook to the same switch and default.
Loans already expired this way on UAT stay Rejected.

Found alongside:
- The dashboard's Accepted card offered "Next: verify bank" into the SAMPLE
  bank screen whatever the app flag said. The flag was meant to keep that
  screen away from testers. The card now offers the schedule while the
  hand-off is off. Build 18 still shows the old button, which after the F16
  deploy answers 401, because the app does not hold the webhook secret.
- The reminder cron ran to 17:30. The 17:00 reminder landed on the expiry
  itself, and the 17:30 one told offers approved after the cut-off to confirm
  "before 5 PM today". The last reminder is now 16:30, and a test keeps every
  reminder before the cut-off.

### F22 — the OCR and liveness rate limits can be skipped by encoding the path
**Severity:** medium · **Status:** open · **Found:** 2026-10-28, fixing F16

`OcrRateLimitFilter` picks its budget with `limitFor(request.getRequestURI())`,
and the raw URI keeps any percent-encoding while Spring MVC matches the decoded
path. So `/api/v1/pdl/%6fcr-nid` should reach the OCR handler with no budget
at all, the way `/api/v1/pdl/%6cos/reject` reached the reject handler past the old
webhook filter (verified on UAT for F16). Not exercised against OCR itself,
because every OCR call goes to SBF.

Fix: decide by handler, as `LosWebhookAuthInterceptor` now does, or match on the
decoded path.

### F21 — an approve without `currency` erased the application's own
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-10-28, not yet deployed** ·
**Found:** 2026-10-28, answering SBF's "how do we send approve/reject"

`LosWebhookService.handleApproved` copied nine fields from the body
unconditionally, so a field Sambat left out overwrote what we held with null.
For `currency` that was the application's own, and `pdl_accept_page.dart:84`
shows anything that is not `KHR` as dollars: a riel application approved
without `currency` would have been offered to the customer in USD, on the
screen where they accept the contract. A field left out now keeps its value,
the same rule the fee set already followed.

Found alongside: every `NOT_FOUND` on an approve, reject or rework body read
`Application not found: null` when only `appId` was sent, because it printed
`losApplicationNo` — so Sambat's first test would have looked as if we ignored
the AppId. It now names the identifier looked for, and a body naming no
application at all answers 400 instead of `NOT_FOUND`.

### F20 — OTP verification matches on the code alone, across every user
**Severity:** medium · **Status:** open · **Found:** 2026-10-27, designing the
CamDigi step, which gates on "OTP done"

`POST /auth/verifyOTP?otp=NNNN` looks the code up by its value alone
(`UserServiceImpl.validateOTP`, `:355`) and enables whichever user it belongs
to (`:370`). Nothing ties the code to the phone number being verified, and the
codes are four digits drawn from one space shared by every account. Codes do
expire (`:365`), but a valid one is not deleted when used (the delete at `:372`
is commented out), so it stays usable until it expires.

So anyone can enable a disabled account by cycling through the 10,000 codes
while some are unexpired, without knowing whose they are. For a PDL account the
damage is bounded, because the request status, not `enabled`, is what gates
login — but `enabled` is exactly what "the phone was verified" means to the
CamDigi step, and the TFF flow relies on it directly.

Also: `:357` prints the OTP record to stdout, so live codes land in the
container log.

Fix: verify `{username, otp}` together, delete the code on use, cap attempts
per user, and drop the print.

### F19 — the signup response handed back the customer's SBF CIF
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-10-27** · **Found:**
2026-10-27, by the CamDigi design review

`POST /api/v1/pdl/account-request` is anonymous, and returned the whole saved
`PdlAccountRequest`. Its eager `user` serialised `registedId` — which, unlike
the password and roles, is not `WRITE_ONLY` (`User.java:65`) — and the request
had just filled `registedId` by looking the submitted NID number up at SBF.

So submitting a bundle with a stranger's 9-digit NID answered whether they bank
with SBF, and their CIF: the same oracle that was deliberately removed from the
OCR endpoint (`NidOcrService.java:98-102`). The app never read the field.

It now answers `{requestId, status}` only (`PdlAccountRequestCreated`), and
`PdlAccountRequestServiceTest` pins that shape.

### F18 — UAT DNS has a bad A record pointing at SBF's Exchange server
**Severity: HIGH — blocks all device testing** · **Status:** open, SBF's to fix ·
**Found:** 2026-09-30

`kjeypapa-uat.sambatfinance.com.kh` resolves to **two** addresses, and only one
of them is ours:

| Address | Server | Certificate | Result |
|---|---|---|---|
| `203.189.133.141` | nginx/1.30.5 | `*.sambatfinance.com.kh` | **200** — correct |
| `103.118.45.11` | Microsoft-IIS/10.0, `x-owa-version`, `x-feserver: DC-EXCH01` | `*.sambatfinance.com` | **401** — Outlook Web App |

The second is their Exchange server. Its wildcard covers `*.sambatfinance.com`,
which does **not** match a `.com.kh` host, so a client doing ordinary strict TLS
— which every mobile app does — fails outright rather than degrading.

Measured from one machine: **10 of 10** strict-TLS attempts failed, because the
resolver kept landing on `.11`. Pinned to `.141` everything works, including
strict TLS with no `-k`.

`.11` was serving this correctly on 2026-09-29, so something moved on their side
today.

**Ask SBF to remove the `103.118.45.11` A record** for this hostname. Nothing on
our side can work around it: round-robin DNS means roughly half of all clients
get a TLS failure, and an app cannot choose its A record.

### F17 — an expired token broke the whole unauthenticated signup flow
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-09-30** (`9a3db88`)

`JwtAuthenticationFilter` called `extractUsername` with no try/catch, and the
filter runs on **every** request including whitelisted ones. A device holding an
old token therefore got 403 on the NID scan and on the account-request probe —
paths that need no authentication at all.

Found on a real device: the phone still held a token from 2026-09-06 in the iOS
keychain, which survives app reinstalls, so reinstalling would not have cleared
it. The log said `ExpiredJwtException`; the user saw "cannot read OCR".

A token the server cannot use means *not authenticated*, never *fail the
request*. The filter now continues the chain unauthenticated, so whitelisted
endpoints serve normally and protected ones are refused downstream with a proper
403 from the authorization layer rather than an exception thrown inside a
filter. Three regression tests cover expired, malformed, and absent tokens.

### F16 — the LOS webhooks are unauthenticated and reachable from the public internet
**Severity: HIGH — close before the first real application exists on UAT**
**Status:** open — fix built 2026-10-28, closes when UAT runs it with a secret and
Sambat send it · **Found:** 2026-09-29, while building the simulator against UAT

`kjeypapa-uat.sambatfinance.com.kh` is **not** internal. It resolves publicly to
`103.118.45.11` and `203.189.133.141`, and answers from the open internet —
verified from a machine with no VPN route to `10.32.x` at all.

Authentication holds everywhere it is supposed to: every admin route returns 403
anonymously. But `/api/v1/pdl/los/**` is on the security whitelist by design,
because Sambat call it server-to-server, and
`pdl.webhook.auth.enabled=false` — so there is no IP allowlist and no shared
secret in front of it.

From the public internet, without credentials, these are all reachable and all
act on the loan they match:

| Endpoint | What an anonymous caller could do |
|---|---|
| `/pdl/los/approved` | Approve a loan, and set its terms, fee set and repayment schedule |
| `/pdl/los/reject` | Reject an application |
| `/pdl/los/disbursement` | Mark a loan disbursed |
| `/pdl/los/loan-update` | Rewrite balances, arrears and paid installments |

The identifier is guessable: Sambat's `AppId` is a 4-digit sequential number.

**Not exploitable today** — `pdl_payday_loan` is empty on UAT, so every call
answers `NOT_FOUND`. That is the only thing standing between this and a real
problem, and it stops being true with the first filed application.

**Fix, in order of speed:**
1. **Ask SBF to restrict `/api/v1/pdl/los/**` at the load balancer** to Tricube's
   source addresses. No code change, no deploy, effective immediately.
2. Set `PDL_WEBHOOK_SECRET` and enable `PDL_WEBHOOK_AUTH_ENABLED=true`, then give
   Sambat the secret.
3. Longer term, the mechanism Sambat prefer (HMAC over the body, or mTLS), which
   is already an open question with them.

**Re-checked 2026-10-28:** still open from the internet — `approved` and
`reject` both answered an anonymous call. Building item 2 turned up three
defects in the filter itself, any one of which would have left it open after
being switched on:

- **An encoded path skipped it.** It ran only when the raw URI started with
  `/api/v1/pdl/los/`, while Spring matches the decoded path.
  `/api/v1/pdl/%6cos/reject` and `/%61pi/v1/pdl/los/reject` both reached the
  reject handler on UAT, through SBF's load balancer.
- **The allowlist believed a forged address.** It read the left-most
  `X-Forwarded-For` entry, which the caller writes.
- **On but unconfigured passed everything**, the note this item used to carry.

All three are fixed by `LosWebhookAuthInterceptor`, which replaces the filter:
it checks every request that will run a `LosWebhookController` method, however
the path is spelled; resolves the address through `ClientAddress`; refuses every
call (503) when switched on without a secret of at least 32 characters or an
allowlist; and answers 401 in the webhook envelope instead of `sendError`. The
compose file now defaults it on.

This also retires the premise behind **D8**: Swagger was left enabled on the
argument that UAT sits on a private network. It does not. `/swagger-ui` and
`/v3/api-docs` are both served to the public internet, publishing all 132
endpoints.

### F1 — FCM token travels in a URL query string
**Severity:** low · **Status:** open · **Found:** 2026-09-05, in an access log
enabled to verify the Step-5 headers

`POST /api/v1/user/fcm-token?token=…` puts the registration token in the query
string, so it lands in web-server access logs, proxy logs and anywhere else URLs
are retained. Observed directly — the token appeared in full in the access log
the moment one was enabled.

An FCM registration token is not an account credential; on its own it lets
nobody read anything. Combined with leaked server credentials it allows pushes to
that device, which is why it does not belong in a log. Query strings are the
wrong place for any secret-adjacent value.

**Fix:** move it to a request body or header. Small change, but it touches the
app and the endpoint together, so it wants its own commit.

### F2 — `bankConsent` stays false after successful bank verification
**Severity:** low · **Status:** open, carried over from the 2026-08-21 journey
regression · **Found:** device testing, not re-verified since

Backend serialization issue. The flag does not follow the verification result.

### F3 — `acceptedDate` is a `dd-MM` string, off by one against `approvedDate`
**Severity:** low · **Status:** ~~open~~ **FIXED 2026-09-05** (backend `db3f575`)

Root cause was F7: the field was a `java.sql.Date`, so it arrived as a bare
`dd-MM-yyyy` and the app kept it verbatim while every other date went through
the shared ISO formatter. Both are `Instant` now and the app formats them the
same way as the rest.

### F4 — `hibernateLazyInitializer` leaks into nested-entity JSON
**Severity:** low · **Status:** open, carried over from 2026-08-21

Proxy internals appear in API responses. Cosmetic until something downstream
tries to parse them strictly.

### F15 — client IP behind SBF's load balancer
**Severity:** was high in effect · **Status:** ~~open~~ **RESOLVED 2026-09-28**

Anticipated as a design concern; turned out to be a live misconfiguration, and
the cause was not the one predicted.

SBF replaced our nginx container with their own load balancer
(`NGXLB-DMZ`, `kjeypapa-uat.sambatfinance.com.kh`). Their LB is configured
correctly — a packet capture shows it appends the true client to
`X-Forwarded-For` and also sets `X-Real-IP`, so our right-most-entry logic is
right and needs no change.

What broke was `TRUSTED_PROXIES`, still pointing at `172.28.0.10` — the static
address of the nginx container they had removed. Nothing matched it, so
`ClientAddress` fell back to the socket peer and attributed **every** request to
the load balancer. Two consequences, neither of which announces itself:

- every consent would record the LB's address rather than the customer's;
- the anonymous OCR rate limiter shared **one bucket across all customers** —
  six signups a minute in total, and the seventh person fails.

**The trap worth remembering:** the correct value is the address connections
*arrive from*, not the address the hostname *resolves to*. DNS gives the VIP
`10.32.6.80`; connections come from the node `10.32.6.81`. Setting the DNS
address looks right and fixes nothing. Now `10.32.6.80,10.32.6.81`.

Verified after the fix: two different clients through the same LB get separate
rate-limit buckets, and eight forged `X-Forwarded-For` values from one client
are still ignored.

**Ask SBF** for the full set of LB node addresses — an HA pair may add more, and
a new node would silently reintroduce this. Their LB also sets `X-Real-IP`,
which would be simpler and less positional than parsing `X-Forwarded-For`;
worth considering if the node list proves unstable.

### F14 — `Note.requestAmount` is a `Double` over a `numeric(38,2)` money column
**Severity:** high · **Status:** open · **Found:** 2026-09-25, by replaying our
schema against a copy of SBF UAT

`sbf_note.request_amount` on SBF's database is `numeric(38,2)` and holds 383 live
amounts totalling 175,330.10. Our entity declares `Double`, which Hibernate 7
expects to be `float8`.

Two consequences. `ddl-auto=update` would **convert exact decimal money to
binary floating point on live data**, silently. And `ddl-auto=validate` refuses
to start, which is how it was found.

Their schema is right; ours is wrong. Money belongs in `BigDecimal`. The fix
reaches `Note`, `NoteMerge`, `NotePayload`, `NoteTransaction`, `Customer` and the
comparison arithmetic in `NoteServiceImpl`, so it needs TFF regression testing
rather than a quick edit. Deploy with `ddl-auto=none` until it lands, then move
to `validate`.

Very likely upgrade drift: the column was created when `Double` mapped to
`numeric`, and the Hibernate 7 upgrade changed the expectation. If so, other
`Double` fields may differ too — the before/after diff showed only this one on
`sbf_note`, but re-run that diff after any entity change.

### F13 — one notification per user, forever — and it broke every repeat webhook
**Severity:** high · **Status:** ~~open~~ **FIXED 2026-09-07** (`0092644`)

`NotificationModel.user` was mapped `@OneToOne`, so Hibernate generated
`UNIQUE (user_id)` on `ez_notification`. A user could hold exactly one
notification, ever. The first insert succeeded; every one after it violated the
constraint, marked the surrounding transaction rollback-only, and took the whole
request down.

Worst on the inbound LOS webhooks: **a loan status update from Sambat was
discarded because a notification could not be written**, and Sambat were
answered with an opaque HTTP 403 — the rollback exception reaching the error
dispatch, which is not on the security whitelist. In production this would have
failed for every customer after their first-ever notification.

Found by running the example bodies for Manith's API pack against the live
backend: four of seven came back with empty responses.

It hid because `notify()` skips the database write when a user has no FCM token,
so most test users never stored even a first row — and the one user who had one
is why it surfaced at all.

`@ManyToOne` now, with migration 006 to drop the constraint (`ddl-auto` will not
drop it — same trap as 002 and 004). Verified: the same webhook three times in a
row answers 200, 200, 200.

### F12 — a malformed webhook body answered HTTP 403 with no content
**Severity:** medium · **Status:** ~~open~~ **FIXED 2026-09-07** (`0e26ff2`)

A body Jackson could not parse never reached a controller, so the request fell
through to the error dispatch, which is not on the security whitelist, and
Sambat were told **403 Forbidden with an empty body** about what was really one
badly formatted date. We lost a day to this on 2026-08-21 looking at credentials.

Now a 400 naming the field and the expected format, including inside nested
schedule rows. Documented for Sambat in
`PDL_Our_Webhook_Endpoints_for_LOS.md` §5.

**Carries its own trap:** the handler must import
`tools.jackson.databind.exc.InvalidFormatException`. This project is on Jackson
3, but the Jackson 2 class still resolves at compile time — import the wrong one
and it compiles cleanly, never matches at runtime, and the field name silently
vanishes from every message. `Reference.getFieldName()` is also
`getPropertyName()` in Jackson 3. A test drives a real Jackson read rather than
a constructed exception, so it would catch a regression to either.

### F11 — a direct SQL status change does not stamp `closedDate`
**Severity:** informational · **Status:** by design · **Found:** 2026-09-05,
cleaning up test fixtures with an UPDATE

`PaydayLoan.stampClosure()` is a JPA lifecycle callback, so it fires only for
writes that go through the entity. A status changed by hand in SQL leaves
`closed_date` null, and consent retention then falls back to its approximate
date for that loan.

Not a defect — the fallback exists for exactly this — but worth knowing before
anyone fixes production data with an UPDATE. Set `closed_date` in the same
statement, as migration 004 does.

### F10 — `PC_PaymentChannelName` is a constant, and a second bank is coming
**Severity:** medium *when bank two lands*, none before · **Status:** open by
design · **Confirmed by Sambat 2026-09-05**

Every application sends `CAMBODIAN PUBLIC BANK PLC`. Sambat confirmed the name
is right and that **two more banks are coming**. A constant is correct only
while exactly one payroll bank is integrated; the moment the second arrives,
every application still claims the customer banks at the first — wrong data on a
real credit file, accepted silently, exactly the class of error the submit gate
exists to prevent.

**Before integrating bank two:** ask Sambat for the canonical sheet spellings of
the new banks (ours, e.g. "Campu Bank", are not theirs and cannot be sent as-is)
and make this a per-customer lookup keyed on the payroll bank. Noted in
`application.properties` beside the property.

### F8 — `sbf_note` has both `repayment_date` and `repyament_date`
**Severity:** low · **Status:** open · **Found:** 2026-09-05, while widening the
Note date columns

Both columns exist, both `timestamptz`. Only the correctly spelled one is mapped
by an entity field, so the misspelled one is dead weight that nothing writes and
nothing reads — but it is one autocomplete away from being used by mistake.

Not fixed in migration 005: dropping a column is not something to slip into a
type migration. Check it is empty, then drop it in its own change.

### F9 — Note workflow dates rendered a day early in the app
**Severity:** low · **Status:** ~~open~~ **FIXED 2026-09-05** (app `9856672`)

Found while sweeping F7. `note_transaction_model.dart` formatted eight dates with
`DateFormat(...).format(DateTime.parse(v))` and no `.toLocal()`, so any instant
late in the UTC day rendered as the previous day in Phnom Penh (UTC+7). Live data
shows it: note 1's `reviewedDate` is `2023-02-12T17:00:00Z`, which is 13 Feb
locally and was displaying as the 12th.

This predates the sweep — `disbursementDate`, `repaymentDate` and `createdAt`
were already Instants and already wrong. All eight now go through one helper that
converts to local time and returns blank rather than throwing on an unreadable
value.

### F5 — a stray pre-ledger `E_CBC_CONSENT` image row
**Severity:** informational · **Status:** no action proposed · **Found:**
2026-09-05 while listing archived consents

`ez_file_image` holds one 67-byte PNG tagged `E_CBC_CONSENT` for entity_id 7,
from a flow that predates consent archiving. It is not referenced by any consent
record and is harmless; noted only so nobody later mistakes it for a corrupted
archive. The dev database is disposable, so it will not survive to production.

### F6 — the schema records no loan-closure date
**Severity:** medium · **Status:** ~~open~~ **FIXED 2026-09-05** (backend `db3f575`)

`PaydayLoan.closedDate` now exists, stamped by a `@PrePersist`/`@PreUpdate`
callback on every transition into a terminal state, with `PdlStatusEnum.isTerminal()`
as the single definition the stamp and retention both read. Migration 004
backfills history. Retention reports `loanEndedSource = "closedDate"` with no
approximations left; the derived fallbacks remain only for a row that closed
before the column existed and has not been saved since.

*Original finding, for context:*

Retention is "the life of the loan file", which needs a date the file ended, and
there isn't one. `PaydayLoan` has `applicationDate`, `approvedDate`,
`acceptedDate`, `revokedDate`, `disbursementDate`, `repaymentDate` and
`lastTransactionDate` — but nothing stamped when a loan reaches `Closed` or
`Rejected`. `PdlConsentRetentionService` falls back to the audit `updatedAt`,
which is a proxy that moves whenever anything touches the row, and every
candidate it reports names the date it used and flags it as approximate.

That is honest but it is not a foundation for disposing of evidence. **Before
retention is ever switched on for real, add a real closure timestamp** stamped
on the transition into `Closed`/`Rejected`, and switch the service to it.

### F7 — `acceptedDate` and `revokedDate` are `java.sql.Date`
**Severity:** low · **Status:** ~~open~~ **FIXED 2026-09-05** (backend `db3f575`)

Both are `Instant`. Migration 004 widens the columns, guarded on the current
type. **Worth knowing:** on the dev database `ddl-auto=update` widened them by
itself, which is *not* what it does for check constraints (migration 002) — do
not generalise either behaviour. **Swept 2026-09-05 (`40f4470`):** the TFF Note product had eleven more —
six on `Note`, five on the `NoteMerge` DTO — plus `NoteTransaction.rejectDate`,
which sat in a DTO whose other date fields were already `Instant`. All now
`Instant`; migration 005 widens the seven `sbf_note` columns. No
`java.sql.Date` remains anywhere in `src/main`.

*Original finding, for context:*

`PaydayLoan` imports `java.sql.Date` for these two. Two consequences:
`java.sql.Date.toInstant()` throws `UnsupportedOperationException` at runtime —
the retention service called it and would have failed on the first revoked loan,
caught here only because a test set one — and the type is date-only, so any time
component is silently lost.

This is very likely the root of **F3** (`acceptedDate` rendering as a `dd-MM`
string, off by one against `approvedDate`). Fix them together: move both to
`Instant` like every other date on the entity, with a migration for the column
type.


---

## Requested, not yet built

### R1 — CamDigi NID image + liveness verification
**Requested by SBF 2026-10-03 · re-requested 2026-10-27 · Stage 1 BUILT
2026-10-27, behind `pdl.liveness.enabled=false` · Stage 2 waits on SBF**

    POST /verified-info-idimage-liveness          tag: 18-CamDigi
    request   UserInfoLivenessRequest { actions[], doneBy, idImage, liveness, userInfo }
    response  ResponseInfo            { data, error, message }

**Where it runs: after OTP, against what was submitted** — SBF's own journey
order (`Customer_Journey_V21_Review.md:12`). The backend builds `idImage` from
the stored NID front and `userInfo` from the stored, customer-confirmed
identity; the app uploads only the capture. CamDigi therefore checks exactly
what the LPO approves, and the endpoint cannot be used to test a stranger's
identity. `PdlIdentityVerificationService` has the detail.

- **Who may call it.** A PENDING customer cannot log in, so
  `POST /pdl/account-request/liveness/challenge` checks the request's own
  password. It is the one way in, both straight after OTP and on any resume
  from the login screen. The challenge id it returns is single-use, valid 10
  minutes, and stored only as a hash.
- **`doneBy` is `los.done-by` (`KjeyPapa`)**, refused locally if blank — never
  `""`, never a username (September's opaque 500s), never the legacy `Kjey_PAPA`.
- **Attempts.** At most 5 that may have been charged, per request, in a rolling
  24 hours; one that never left us does not count. A pass is final.
- **Approval gate** in `decide()`, off until `required-for-approval` is set.
- **The earlier note that this "pairs with the OCR path" held only for the
  backend.** The signup selfie cannot serve as the capture: it comes from the
  rear-default system camera with the gallery allowed, which proves nothing
  about who holds the phone. The app has a new front-camera screen
  (`pdl_liveness_page.dart`) with the capture behind a swappable interface.

**Stage 2 waits on SBF** (questions drafted in
`docs/SBF_UAT_Feedback_2026-10-27.md`, not yet sent): what `liveness` holds — a still, a video, or a vendor SDK
package — the action names, the `userInfo` date and gender formats, what
`data` and `error` mean (an info mismatch is not a face mismatch), retention,
and whether the endpoint is provisioned at all — the CamDigi group returned 500s
on 2026-08-27 and nobody has re-checked. Until then `classify()` is a
fail-safe placeholder, and `CamDigiUserInfoMapper` holds the two format
constants to change.

## Before first deployment

These are not bugs. They are steps that must happen once, in this order, and
each has a specific failure mode if skipped.

### D1 — widen `pdl_payday_loan_status_check`
Hibernate's `ddl-auto=update` **never** alters check constraints, so the
`Pending_Bank_Verification` enum value needs the constraint widened by hand. SQL
is in commit `604f642`. The same trap applies to every future value added to an
existing string-enum column.

### D1b — apply `docs/migrations/004_pdl_loan_dates.sql`
Widens `accepted_date`/`revoked_date` to `timestamptz` (guarded — a no-op where
ddl-auto already did it) and backfills `closed_date` for loans that ended before
that column existed. Without the backfill, retention falls back to approximations
for every historical loan.

### D1c — apply `docs/migrations/005_sbf_note_dates.sql`
Widens the seven `sbf_note` date columns to `timestamptz` (guarded — a no-op
where ddl-auto already did it). Without it, `Note` reads `Instant` from `date`
columns wherever ddl-auto is off.

### D1d — apply `docs/migrations/006_notification_many_per_user.sql`
Drops `UNIQUE (user_id)` from `ez_notification`. Without it, every LOS webhook
to a customer who already has one notification fails with an opaque 403 and the
status update is lost. See F13.

### D1e — apply `docs/migrations/007_pdl_address_country.sql` and `008_pdl_identity_verification.sql`
007 fills the blank correspondence and permanent countries that every SBF UAT
account was created with (SBF's "addresses are missing country"); without it
they keep showing "Country: -" until each customer re-saves. 008 creates the
CamDigi attempts table. SBF UAT runs with ddl-auto off, so without 008 every
verification fails on a missing table. Apply both **before** deploying.

### D1f — the CamDigi switches
`pdl.liveness.enabled` stays `false` until SBF has answered the R1 questions
and a real capture has passed against their UAT. `pdl.liveness.mock` must be
`false` everywhere but a developer's machine: it passes every capture. A mock
pass stops counting once the mock is off, and the LPO sees it marked MOCK, but
neither makes it safe to leave on. Set `required-for-approval` only after real
passes have been seen; turning it on blocks every PENDING request that has not
verified, until each customer signs in and does.

### D2 — apply `docs/migrations/003_pdl_cbc_consent_ledger.sql`
`ddl-auto` creates the consent table but never its indexes, CHECK constraints or
the append-only triggers. Without this the ledger is an ordinary table that
anything can rewrite.

    psql -v ON_ERROR_STOP=1 --single-transaction -f docs/migrations/003_pdl_cbc_consent_ledger.sql

### D3 — run the consent backfill **once, before any live consent**
`POST /api/v1/pdl/admin/consent-ledger/backfill`. A hash chain can be appended to
but never prepended to, so consents predating the ledger can only join it while
no live record exists. The endpoint enforces this and will refuse afterwards —
at which point earlier consents can never be chained.

### D4 — set `pdl.ocr.rate-limit.trusted-proxies`
Behind a load balancer or reverse proxy this must name that peer's address.
Left empty, every consent records the proxy's own IP and the field is worthless.
Set to something untrusted, the address becomes whatever the client chose to
write. Also governs the anonymous OCR rate limiter, which is why it is one
property.

### D5 — give the application a non-superuser database role
The app connects as `postgres`, and a superuser ignores table privileges — so
`REVOKE UPDATE, DELETE` on the consent ledger enforces nothing today, and the
append-only guarantee rests on a trigger a superuser can disable. The role is
sketched at the bottom of migration 003. Defence in depth, not a substitute for
the trigger.

### D6 — ship the application logs somewhere the database cannot reach
The nightly job writes `CONSENT-LEDGER OK … head_hash=…`. That line is the only
part of the tamper-evidence design that survives someone with superuser access
editing rows and recomputing the chain, and it only works if the logs are
retained off the database host. Alert on `CONSENT-LEDGER FAILED` and
`CONSENT-ARCHIVE FAILED`.

### D8 — Swagger and the API docs are publicly whitelisted
`/v3/api-docs/**` and `/swagger-ui/**` sit on `WHITE_LIST_URLS`, so any host that
can reach the application can enumerate all 132 endpoints, admin routes
included. SpringDoc itself logs a warning about this on every boot.

**Deliberately left on for UAT (2026-09-25)** — the team needs it to test, and
`10.32.8.99` is on a private network. Enumeration is the whole exposure:
authentication still holds, and every admin route answers 403 to an anonymous
caller, verified from off-host.

**Must be closed before anything internet-facing.** Two things to do together,
because either alone is insufficient:
1. Drop `/v3/api-docs/**` and `/swagger-ui/**` from `WHITE_LIST_URLS`, or set
   `springdoc.api-docs.enabled=false` in the production profile.
2. Set `BIND_ADDR=127.0.0.1` and put a reverse proxy in front. There is no host
   firewall on that box, so the bind address is the only thing deciding who can
   reach the application.

### D9 — SBF UAT: the app user is a database superuser
`kjey_papa` on `10.32.2.33` is a superuser. The consent ledger's append-only
guarantee then rests entirely on its trigger, since a superuser ignores table
privileges, and our application can alter every TFF table in the same database.
Ask their DBA for a plain role. See the deployment runbook §6.

### D7 — the standing environment items
**`los.mock.enabled` is committed as `true`.** Every live UAT submission this
project has made was driven by a command-line override; the checked-in default
still points at the mock. Deployed as-is the app looks *entirely successful* —
applications "submit", the customer sees a confirmation, and a reference like
`LOS-MOCK-41` comes back — while Sambat receive nothing at all. It is the
quietest possible failure and it belongs at the top of any release check.

Also: `ocr.mock.enabled=false` (confirm false, not merely absent — the mock
returns one person's identity for *any* image), `pdl.webhook.auth.enabled=true`,
`pdl.dictionary.refresh.enabled=true` once the cadence is agreed, `API_BASE_URL`
defined for release builds, and the temporary localhost API URL removed from the
app.

---

## Settled by Sambat, 2026-09-05

- **Consent records are not destroyed.** No retention period, no disposal, and
  none expected. `pdl.consent.retention-years` stays empty and the retention job
  stays off; both now mean "this is the answer", not "we are waiting". Do not set
  a period without a written instruction that supersedes this.
- **CBC consent cannot be withdrawn** — the enquiry it authorises is automatic
  during assessment, so there is nothing left to stop. Removed from the app and
  refused by the API.
- **Double-submit protection is ours to build.** Done: an unanswered submission
  blocks resubmission until an operator reconciles it.

## Known, accepted, not a finding

- `EzetikKjeypapaApplicationTests.contextLoads` errors locally. It needs a live
  database and Firebase credentials; it is an environment gap, not a regression.
  Every other test passes — check the count, not the build status.
- Twelve consents record an empty channel, language and wording hash. They
  predate those columns and are stored as `''` rather than guessed. `verify()`
  reports them as `incomplete`, never as `broken`.
