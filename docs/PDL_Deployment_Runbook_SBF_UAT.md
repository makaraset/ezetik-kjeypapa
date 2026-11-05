# Deploying to SBF UAT — runbook

| | |
|---|---|
| **For** | Kjey PAPA (Ezetik) — internal |
| **Date** | 2026-09-25 |
| **Targets** | API `10.32.8.99` (Docker) · DB `10.32.2.33` (no Docker) |

Both hosts are on SBF's private network and need the VPN up.

---

## 1. The environment, as surveyed 2026-09-25

**API host `10.32.8.99`** — Ubuntu 26.04.1 LTS, x86_64, 2 CPU, 4 GB RAM, 33 GB
free. Docker 29.8.1 with Compose v5.5.1. Java 21 on the host; no Maven, no
`psql`. SSH as `root`. **Host timezone is UTC.** No containers were running —
the box is otherwise unused. It reaches the database host, and it reaches Docker
Hub.

**Database `10.32.2.33`** — **PostgreSQL 18.4**. We develop against 14, so treat
any version-sensitive SQL with suspicion. Our application user `kjey_papa` **is a
superuser**, which is worth changing (see §6).

### The thing that changes the plan

**This is not an empty database.** It is SBF's live TFF/Note system with real
business data, and **no PDL tables at all**:

| Table | Rows |
|---|---|
| `ez_file_image` | 709 |
| `ez_note_attachment_attach_files` | 623 |
| `ez_note_attachment` | 622 |
| `ez_notification` | 497 |
| `sbf_note` | 383 |
| `ez_user` | 116 |

Sixteen tables, none of them ours. We are adding a product to a database that
other people's work already depends on. Everything below follows from that.

Two pieces of good news from the survey:

- `ez_notification` has **no** `UNIQUE (user_id)` — their schema already matches
  the `@ManyToOne` shape we corrected in F13, so migration 006 is a no-op here.
  Their 497 notifications across 116 users could not exist otherwise.
- `ez_user` already carries `registed_id`, `fcm_token` and `profile_photo`, so
  the shared user entity is in step.

---

## 2. Never run `ddl-auto=update` against this database

It is the committed default and it must not reach this host. Against a database
holding 383 live notes, Hibernate would be free to alter existing TFF tables the
moment our entities drift from their schema — unreviewed, at startup, with no
record of what changed.

`docker-compose.yml` therefore pins `SPRING_JPA_HIBERNATE_DDL_AUTO` to `none`,
overridable with `DDL_AUTO` for a scratch database. `validate` is the setting we
actually want — a schema mismatch should stop a boot, loudly and before any
traffic — but it cannot be used yet. §2b explains why, and it is worth reading
before changing this.

---

## 2b. What the replay found — two schema drifts

The replay in §3 was run on 2026-09-25. Diffing their tables before and after
showed our application would change **two things in `sbf_note`**, a table holding
383 live notes:

**Six date columns → `timestamptz`.** Expected and wanted: it is our
`java.sql.Date` → `Instant` sweep, and migration `005` performs it deliberately
and reversibly.

**`request_amount`: `numeric(38,2)` → `double precision`. Not wanted.** That
column holds 383 real amounts totalling 175,330.10. Converting exact decimal
money to binary floating point on live financial data is precisely the kind of
silent damage `ddl-auto=update` makes easy, and nothing would have reported it.

The cause is almost certainly a Hibernate upgrade: the column was created when
`Double` mapped to `numeric`, and Hibernate 7 expects `float8`. **SBF's schema is
right and our entity is wrong** — money belongs in `BigDecimal`, not `Double`.

Consequences for this deployment:

- `ddl-auto=validate` **fails to start**, correctly, on that one column. Verified.
- `ddl-auto=none` starts and serves normally. Verified — 16.4 s to boot, 200 on
  the dictionary endpoint, container healthy.

**So deploy with `none` for now**, which preserves their schema exactly and
matches how their current TFF build already runs. Changing `Note.requestAmount`
to `BigDecimal` is the real fix, but it reaches `NoteMerge`, `NotePayload`,
`NoteTransaction`, `Customer` and the money arithmetic in `NoteServiceImpl` — it
is a tested change, not something to rush under a deployment. Filed as a
follow-up. Switch to `validate` once it lands; it is worth having.

## 3. Producing the baseline schema

Because the database is not empty, the useful artefact is not "our full schema"
— it is **precisely what our application would add to theirs, and nothing else**.
Produce it by replay, on the API host:

1. Dump their schema only (needs `pg_dump` **18**; an older one refuses a newer
   server):
   `docker run --rm -e PGPASSWORD=… postgres:18-alpine pg_dump -h 10.32.2.33 -U kjey_papa -d kjey_papa_db --schema-only --no-owner --no-privileges > sbf-uat-schema.sql`
2. Start a throwaway `postgres:18-alpine` container and load that dump into it,
   so the scratch is an exact copy of their shape with none of their data.
3. Run our image against the scratch with `ddl-auto=update`.
4. Dump the scratch again and diff against step 1.

**That diff is the baseline migration.** It is reviewable, it is provably only
additive, and it never touched the real database. Hand it to their DBA.

This has been done — the result is committed as
`docs/migrations/001_pdl_baseline_schema.sql`, twelve tables. Apply in order:

    001_pdl_baseline_schema.sql
    002_pdl_personal_info_hardening.sql
    003_pdl_cbc_consent_ledger.sql
    004_pdl_loan_dates.sql
    005_sbf_note_dates.sql          <- the only one touching an SBF table
    006_notification_many_per_user.sql

Verified 2026-09-25: the whole sequence applies to a copy of their schema with
zero errors, and the application then boots against it.

---

## 3b. Nine environment variables have no default

Any one of them missing and the application does not start, failing in bean
creation with a placeholder-resolution error:

`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `MAIL_USERNAME`,
`MAIL_APP_PASSWORD`, `ADMIN_NOTIFY_EMAIL`, `SBF_USERNAME`, `SBF_PASSWORD`,
`SBF_BASIC_AUTH_BASE64`.

`.env.example` lists all of them. Generate the JWT key per environment with
`openssl rand -base64 48`; changing it later logs every user out.

**A valid Firebase service-account file is also mandatory.** The application
will not boot without one — the placeholder inside the jar fails credential
parsing. Mount the real file and point `FIREBASE_CREDENTIALS_PATH` at it, as
`docker-compose.yml` does.

## 3c. Two things the first real deploy tripped on

**Mounted secrets must be owned by uid 1001.** The container runs as the
unprivileged `kjeypapa` user (uid 1001). A secret copied in as `root:root 0600`
is unreadable to it, and the failure is a bare
`FileNotFoundException: … (Permission denied)` buried four `Caused by` levels
down. Keep the mode at 600 and `chown 1001:1001` — loosening to 644 would work
and would be the wrong fix.

    chown 1001:1001 /opt/kjeypapa/secrets/firebase-service-account.json
    chown -R 1001:1001 /opt/kjeypapa/logs

**Re-copy `docker-compose.yml` whenever it changes.** The source bundle is a
snapshot; a compose edit made after the last sync is not on the host, and the
symptom is the application behaving as an older revision said it should. It
cost one failed boot here, on the `ddl-auto` setting of all things.

## 4. Deploy

```
scp source → /opt/kjeypapa on 10.32.8.99
cd /opt/kjeypapa
cp .env.example .env          # fill in; .env is gitignored
mkdir -p secrets && place firebase-service-account.json there
docker compose up -d --build
docker compose logs -f api
```

The image build needs outbound internet for Maven. It is a 2-CPU box, so expect
the first build to take several minutes; dependencies are cached in their own
layer, so later builds are quick.

---

## 5. The flags that decide whether this works

Correct for development, wrong here. `docker-compose.yml` sets the first three;
the rest need values from Sambat.

| Setting | Committed | Required | If wrong |
|---|---|---|---|
| `los.mock.enabled` | `true` | **`false`** | The app looks perfectly healthy, returns `LOS-MOCK-nn`, and files **nothing** with Sambat |
| `spring.jpa.hibernate.ddl-auto` | `update` | **`none`** (see §2b) | `update` converts a live money column to floating point; `validate` refuses to start until the entity is fixed |
| `spring.jpa.show-sql` | `true` | `false` | Logs every statement, with values |
| `pdl.webhook.auth.enabled` | `false` | `true` | Sambat's webhooks are reachable by anyone who finds the URL |
| `pdl.ocr.rate-limit.trusted-proxies` | empty | the proxy address | Every consent records the proxy's IP, not the customer's |
| `ocr.mock.enabled` | `false` ✓ | confirm false | The mock returns one person's identity for *any* image |

### Also: Swagger is publicly exposed

`/v3/api-docs/**` and `/swagger-ui/**` are on the security whitelist. On a
reachable host that publishes all 132 endpoints — including every admin route —
to anyone. Disable them here, or restrict them to the internal network. This is
not the same file we prepared for Manith, which deliberately contains only his
seven webhook paths.

---

## 6. Recommend to SBF: stop being superuser

`kjey_papa` is a superuser on their database. Two consequences:

- The consent ledger's append-only guarantee rests **entirely** on its trigger,
  because a superuser ignores table privileges. `REVOKE UPDATE, DELETE` would be
  silently bypassed.
- Our application can alter any table in the database, including TFF's.

A plain role with `SELECT, INSERT, UPDATE, DELETE` on its own tables, plus
`INSERT`-only on `pdl_cbc_consent`, would make the ledger meaningfully stronger
and limit the blast radius of any defect in our code. Their DBA's call, but
worth asking for.

---

## 6b. Deployed — 2026-09-25

Live on `10.32.8.99`, container `kjeypapa-api`, healthy, 0 restarts, boot 16.7 s.

| | |
|---|---|
| Backup taken first | `/root/backups/kjey_papa_db-prePDL-20260925-094433.dump`, 467 MB, `pg_restore --list` verified |
| Migrations | `001`–`006`, all applied, zero errors |
| Schema | 28 tables — their 16 plus our 12; consent triggers live |
| Their data | `sbf_note` 383 rows, sum 175,330.10 — **unchanged**; `request_amount` still `numeric` |
| `ddl-auto` | `none` · `los.mock.enabled` | `false` |
| Admin endpoints | 403 anonymous, all three |
| Errors since boot | none |

**Reachable on the UAT network as of 2026-09-25** — `BIND_ADDR=0.0.0.0` in the
host's `.env`, so the team can use Swagger to test:

| | |
|---|---|
| Swagger UI | `http://10.32.8.99:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://10.32.8.99:8080/v3/api-docs` |

Note `/swagger-ui.html` returns 403 — only the `/swagger-ui/**` pattern is
whitelisted, so use `/swagger-ui/index.html`.

The repo default stays `127.0.0.1`; only this host opts out. Verified from
off-host that authentication still holds — every admin route answers 403 to an
anonymous caller, and the whole API is plain HTTP, so this is a private-network
arrangement and nothing more.

### TLS, 2026-09-25

nginx terminates TLS in front of the application with a self-signed certificate;
SBF terminate real SSL at their gateway, so this only has to satisfy that hop.

| | |
|---|---|
| URL | `https://kjey-papa.samatfinance.com.kh` → `10.32.8.99` |
| Certificate | self-signed, CN `kjey-papa.samatfinance.com.kh`, expires 2028-09-24 |
| SANs | both `samatfinance` and `sambatfinance` spellings, plus `localhost` and `10.32.8.99` |
| HTTP :80 | 301 to HTTPS |
| App :8080 | host loopback only — not reachable from the network |

Three nginx defaults were raised, each because leaving it would break something
quietly:

- `client_max_body_size` 1m → **64m**. A signup bundle carries five documents
  inline as base64; the default rejects every account request with a 413 that
  never reaches the application log.
- `proxy_read_timeout` 60s → **180s**. Filing with Sambat can take up to 120s.
  The default cuts the connection mid-submit, manufacturing the
  unknown-outcome case on every slow filing instead of the rare one.
- The healthcheck uses `127.0.0.1`, not `localhost`. The config is mounted
  read-only, so nginx's entrypoint cannot add its IPv6 listener, and `localhost`
  resolves to `::1` where nothing listens — connection refused, forever.

`TRUSTED_PROXIES=172.28.0.10`, the proxy's **static** address. The compose
network pins a subnet for this: `ClientAddress` compares the peer by exact
string, so a DHCP-assigned container IP would silently stop matching after any
recreate and every consent would start recording the proxy.

Verified end to end: eight requests with eight different spoofed
`X-Forwarded-For` values were counted as one client and rate-limited at the
seventh, so forged headers are ignored and the real peer is used.

**Open — see F15:** once SBF's gateway is in front, the right-most hop becomes
the gateway, not the customer. Ask them which headers it sets before relying on
the recorded IP.

**Still not fit to be internet-facing.** The container serves plain HTTP — it speaks plain HTTP, and neither customer traffic nor Sambat's
webhooks should cross a network unencrypted. A reverse proxy terminating TLS is
the next step, and `TRUSTED_PROXIES` must be set to its address at the same
time, or every consent will record the proxy's IP instead of the customer's.

**Still to do before this is usable end to end:** the proxy and TLS above;
`pdl.webhook.auth.enabled` once Sambat give us their source IPs; disabling
Swagger (the framework logs a warning about it on every boot); and a TFF
regression pass by someone who can log in — we altered six `sbf_note` date
columns, so that is not a formality.

## 6c. Deployed — 2026-10-27

Backend `000a236` is live on `10.32.8.99`. Docker reports it **healthy**, with
0 restarts and a 17.1 s boot.

The release answers SBF's first round of UAT feedback:
- every address carries a country;
- work status and the other Appendix-2 employment fields are required;
- the CamDigi face check is built but switched off.

It also stops the signup reply exposing the customer's CIF (F19).

| | |
|---|---|
| Backup taken first | `/root/backups/kjey_papa_db-pre007-20261027-092936.dump`, with the IDs of the rows 007 touched in `pre007-personal-ids-20261027-092936.txt` |
| `007` | filled 18 correspondence and 18 permanent countries; birth and work had no blanks |
| `008` | created `pdl_identity_verification`, with no CHECK |
| Flags | `pdl.liveness.enabled`, `mock` and `required-for-approval` all `false` |
| Rollback image | `kjeypapa-api:rollback-20261027`, the 2026-09-30 build |
| Previous source | `src.prev-20261027-1401` and `pom.xml.prev-20261027-1401` in `/opt/kjeypapa` |
| Errors since boot | none, and only the five framework WARNs every boot prints |

Checked through the public URL, `https://kjeypapa-uat.sambatfinance.com.kh`:

| Probe | Result |
|---|---|
| `GET /api/v1/pdl/dictionary/version` | 200 |
| `POST /api/v1/pdl/account-request/liveness/challenge` | 200, `NOT_REQUIRED` |
| `POST /api/v1/pdl/account-request`, work status blank | 417 `INVALID`, "employment status is required"; nothing written |

**The healthcheck works again.** The previous image's probe used plain HTTP,
which gets a 400 once the application serves TLS. Docker therefore reported a
working container as unhealthy from then on. The HTTPS-first probe from
`e29306f` reached the host's Dockerfile on 2026-09-30, but no image had been
built from it until this one. That commit's token fix was already live: it was
deployed and verified before it was committed.

**This host no longer matches 6b.**
- The application terminates TLS itself: SBF's compose sets `SERVER_SSL_*`, and
  Tomcat reports `8080 (https)`.
- The public name resolves to SBF's front end at `10.32.6.80`.

**The swap changed exactly `e29306f..000a236`:** 9 files modified and 22
added. It dropped six untracked items from the old source, none of which was in
use:
- `.DS_Store` and three empty folders;
- `application-local.properties`: the `local` profile is never active here;
- a copy of `firebase-service-account.json`: the application reads the mounted
  `./secrets/firebase-service-account.json` through
  `FIREBASE_CREDENTIALS_PATH`, an identical file, and would not boot without it.

**Client addresses are still right behind the front end.** F15 was resolved on
2026-09-28; this re-checks it after the rebuild. SBF trust forwarded addresses
only from their three front-end nodes, at both layers:
- Tomcat: `SERVER_FORWARD_HEADERS_STRATEGY=native` with
  `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=10\.32\.6\.(80|81|82)`;
- our check: `TRUSTED_PROXIES=10.32.6.80,10.32.6.81,10.32.6.82`.

Verified through the public URL: seven requests carrying seven forged
`X-Forwarded-For` values counted as one client, and the seventh got 429. So the
per-IP limits on OCR and liveness see each customer's own address.

## 7. Verify after deploy

1. `docker compose ps` — healthy, not just running.
2. `GET /api/v1/pdl/dictionary/version` returns 200 (proves the app **and** the
   database connection).
3. `GET /api/v1/pdl/admin/consent-ledger/verify` — chain intact, head hash
   recorded somewhere off this host.
4. Confirm the TFF product still works: existing notes list, existing users log
   in. We changed `NotificationModel` and the `sbf_note` date columns, so this
   is not a formality.
5. File **one** application against Sambat UAT and confirm a real AppId comes
   back — not `LOS-MOCK-nn`.
6. Before relying on the CIF check (F27), run
   `docs/probe-sbf-customer-shape.sh <your ID number>` on the VPN. It prints
   the form of each field in Sambat's customer record, never the values.
   Confirm `dob`, `sex` and the names use forms `SbfCustomerMatch` reads. If one
   does not, every existing customer's application waits with
   `SBF_CUSTOMER_MISMATCH`.
