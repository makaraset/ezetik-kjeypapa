# ezetik-kjeypapa (backend)

Spring Boot 3.0.1 / Java 17 REST API for the Kjey Papa lending app (security/RBAC,
SBF note-loan processing, image uploads, Firebase push notifications).

## Running locally

```bash
# 1. Provide local secrets (one-time): copy the template and fill in real dev values
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
#    - set DB / mail / SBF credentials
#    - generate a JWT key:  openssl rand -base64 32

# 2. Run with the `local` profile (no env vars needed — the profile supplies secrets)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.properties` is **gitignored** and overrides the `${ENV_VAR}`
placeholders in `application.properties`.

## Configuration & Secrets

The committed `application.properties` contains **no secrets** — every secret is a
`${ENV_VAR}` placeholder. Secrets come from the environment in production, or from the
gitignored `application-local.properties` in local dev. **Never commit real secret
values or `firebase-service-account.json`** — use the `*.example` templates.

### Required environment variables (production)

| Env var | Property | Purpose |
|---------|----------|---------|
| `JWT_SECRET_KEY` | `security.jwt.secret-key` | HMAC key for signing/verifying JWTs (base64 of ≥32 bytes) |
| `DB_USERNAME` | `spring.datasource.username` | PostgreSQL user |
| `DB_PASSWORD` | `spring.datasource.password` | PostgreSQL password |
| `MAIL_USERNAME` | `spring.mail.username` | Gmail SMTP user (OTP/reset emails) |
| `MAIL_APP_PASSWORD` | `spring.mail.password` | Gmail app password |
| `ADMIN_NOTIFY_EMAIL` | `spring.boot.admin.notify.mail.to` | Admin notification recipient |
| `SBF_USERNAME` | embedded in `urlencoded_token` | Sambat Finance API user |
| `SBF_PASSWORD` | embedded in `urlencoded_token` | Sambat Finance API password |
| `SBF_BASIC_AUTH_BASE64` | `authorization` | SBF OAuth client Basic-auth (base64, no `Basic ` prefix) |
| `FIREBASE_CREDENTIALS_PATH` *(optional)* | `gcp.firebase.service-account` | Path to the service-account JSON; defaults to `classpath:firebase-service-account.json` |

### Running in production

Export the variables above (or inject them via your orchestrator's secret store) and run
**without** the `local` profile so the placeholders bind from the environment:

```bash
export JWT_SECRET_KEY=...        # openssl rand -base64 32
export DB_USERNAME=...  DB_PASSWORD=...
export MAIL_USERNAME=... MAIL_APP_PASSWORD=... ADMIN_NOTIFY_EMAIL=...
export SBF_USERNAME=... SBF_PASSWORD=... SBF_BASIC_AUTH_BASE64=...
# optional, for a mounted credentials file:
# export FIREBASE_CREDENTIALS_PATH=file:/etc/secrets/firebase-service-account.json
./mvnw spring-boot:run
```

A missing required variable makes the app **fail fast at startup** (unresolved placeholder).

### Generating the JWT secret

```bash
openssl rand -base64 32
```

HS256 requires a ≥256-bit key; `JwtService` base64-decodes the value, so it must be base64
of 32 random bytes. Use **distinct** keys for dev and prod. Rotating `JWT_SECRET_KEY`
invalidates existing tokens — users must log in again (tokens expire after 24h regardless).
