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
**Severity:** low · **Status:** open, carried over from 2026-08-21

Two date fields on the same record in two different shapes, one of them a
formatted string rather than an instant. Worth fixing together with F4.

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

---

## Before first deployment

These are not bugs. They are steps that must happen once, in this order, and
each has a specific failure mode if skipped.

### D1 — widen `pdl_payday_loan_status_check`
Hibernate's `ddl-auto=update` **never** alters check constraints, so the
`Pending_Bank_Verification` enum value needs the constraint widened by hand. SQL
is in commit `604f642`. The same trap applies to every future value added to an
existing string-enum column.

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
