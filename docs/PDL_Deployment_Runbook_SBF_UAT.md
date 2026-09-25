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

`docker-compose.yml` therefore pins `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.
Validate turns a schema mismatch into a startup failure, which is exactly what
we want: loud, immediate, and before any traffic.

---

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

Then apply, in order: the baseline, then `003`, `004`, `005`, `006`.

---

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
| `spring.jpa.hibernate.ddl-auto` | `update` | **`validate`** | Unreviewed DDL against live TFF data |
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
