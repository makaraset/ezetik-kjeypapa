# PDL findings backlog

Incidental findings turned up while building something else, plus the items that
must happen before the first deployment. Kept here rather than in a commit
message because nothing in this list is finished, and a finding nobody can find
again is the same as one nobody made.

Started 2026-09-05 during the consent-record hardening work. Each entry says how
it was found, so a reader can judge how much it has actually been checked.

---

## Open findings

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
not generalise either behaviour. `sbf/model/Note.acceptedDate` is still a
`java.sql.Date`; left alone as it belongs to the TFF product, but it carries the
same trap.

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

### D7 — the standing environment items
`ocr.mock.enabled=false` (confirm false, not merely absent), webhook inbound auth
enabled, `API_BASE_URL` defined for release builds, and the temporary localhost
API URL removed from the app.

---

## Known, accepted, not a finding

- `EzetikKjeypapaApplicationTests.contextLoads` errors locally. It needs a live
  database and Firebase credentials; it is an environment gap, not a regression.
  Every other test passes — check the count, not the build status.
- Twelve consents record an empty channel, language and wording hash. They
  predate those columns and are stored as `''` rather than guessed. `verify()`
  reports them as `incomplete`, never as `broken`.
