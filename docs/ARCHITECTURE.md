# Kjey PAPA Backend — Architecture

> `ezetik-kjeypapa` — the Spring Boot REST API behind the Kjey PAPA lending app.
> Companion doc: [`ADD_LOAN_PRODUCT.md`](./ADD_LOAN_PRODUCT.md) — how to add a new loan product.
> App-side docs live in the Flutter repo: `kjeypapa_app/docs/`.

---

## 1. System landscape

```
 ┌─────────────────────┐        REST + JWT          ┌──────────────────────────┐
 │  Flutter app         │  ───────────────────────▶  │  ezetik-kjeypapa          │
 │  (customer +         │   /api/v1/...              │  (Spring Boot 3.0.1)      │
 │   merchant)          │  ◀───────────────────────  │                          │
 └─────────────────────┘                            │  PostgreSQL: kjey_papa_db │
                                                     └─────────────┬────────────┘
   Internal staff (Dashboard, not in this repo)                    │ OAuth2 + Bearer
   review/approve via ROLE_USER / ROLE_APPROVER                    ▼
                                            ┌─────────────────────────────────────┐
                                            │ Sambat Finance (SBF) core-banking    │
                                            │ tricube-uat.sambatfinance.com        │
                                            │ facilities · loan creation · disburse │
                                            └─────────────────────────────────────┘
```

The backend is the **system of record for the loan-application workflow** and a **gateway to SBF core-banking**. It owns users, roles, the note (loan) lifecycle, attachments, and notifications; it delegates the actual credit facility, loan creation, and disbursement to SBF over OAuth2.

> **Upcoming:** new products (Salary Loans — PDL/MSIL/PL, Trade Finance) will integrate with **Sambat LOS** (Loan Origination System), a *different* external system. See [`ADD_LOAN_PRODUCT.md`](./ADD_LOAN_PRODUCT.md).

---

## 2. Tech stack & running locally

| | |
|---|---|
| Language / framework | Java 17, Spring Boot 3.0.1 (Spring Security, Spring Data JPA, springdoc-openapi) |
| Build | Maven (`./mvnw`) |
| DB | PostgreSQL (`kjey_papa_db`), Hibernate `ddl-auto=update` |
| Auth | JWT (HS256), stateless |
| External | SBF core-banking via OAuth2 (`SbfAuthorization`) |
| Push | Firebase Admin SDK |
| Mail / SMS | Gmail SMTP (OTP, reset), `SMSService` |

```bash
# 1. local secrets (one-time): copy template, fill DB/mail/SBF/JWT values
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties

# 2. run with the `local` profile (no env vars needed — profile supplies secrets)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Requires a PostgreSQL with `kjey_papa_db` on `127.0.0.1:5432` (the dump `kjey_papa_db_*.sql` is a `pg_restore` custom-format archive). API docs at `/swagger-ui.html`.

---

## 3. Package structure

`com.ezetik.kjeypapa`

| Package | Responsibility |
|---|---|
| `security/` | Auth, JWT, RBAC, users/roles/permissions, OTP, password reset — **reusable across products** |
| `security/config/` | `SecurityConfiguration`, `JwtAuthenticationFilter`, `CorsFilter`, Swagger config |
| `security/controller/` | `AuthenticationController`, `UsersApiResource`, `RolesApiResource`, `PermissionsApiResource`, `OneTimePasswordController` |
| `security/model/` | `User`, `Role`, `Permission`, `Token`, `OneTimePassword`, `PasswordResetToken`, `VerificationToken`, DTOs |
| `security/service/` | `UserService`, `JwtService`, `CustomAuthenticationProvider`, **`SbfAuthorization`** (OAuth to SBF) |
| `sbf/` | **The loan product** (the "Note") — product-specific today |
| `sbf/api/` | `NoteController`, `SBFApiController`, `MerchantController`, `MerchantUserController`, `NotePeriodController`, `AttachmentController` |
| `sbf/model/` | Entities + DTOs + enums (`Note`, `NotePeriod`, `NoteAttachment`, `NoteStatusEnum`, `ConsolidateData`, `LoanFacility`, `DisburseModel`, …) |
| `sbf/payload/` | API request/response DTOs (`NotePayload`, `NoteTransaction`, `NoteApprovedResponse`, `NoteDisbursementUpdate`, …) |
| `sbf/repository/` | JPA repos (`NoteRepository`, `NoteAttachmentRepository`, `NotePeriodRepository`, …) |
| `sbf/service/` | `NoteService(Impl)` (lifecycle orchestration), `SBFApiService(Impl)` (external calls), `MerchantService(Impl)`, `SMSService` |
| `image/` | Generic file upload/storage — **reusable** |
| `notification/` | Firebase push — **reusable** |
| `config/` | Jackson / app config |

> **Mental model:** `security/`, `image/`, `notification/` are **shared infrastructure**. `sbf/` is **one product** (the Note) with no abstraction — every new product currently means new code alongside `sbf/`.

---

## 4. Security & RBAC

JWT bearer auth, stateless (`SecurityConfiguration`). CSRF disabled. Whitelisted (no auth):

```
/v3/api-docs/**  /v2/api-docs/**  /swagger-resources/**  /swagger-ui/**  /api/public/**  /api/v1/auth/**
```

All other endpoints require a valid JWT; method-level `@PreAuthorize(...)` enforces roles. JWT is issued by `JwtService` (HS256, claims carry authorities); validated by `JwtAuthenticationFilter`.

**Roles** (`ez_user_role`, joined via `ez_user_role_join`):

| Role | Who | In the loan flow |
|---|---|---|
| `ROLE_CUSTOMER` | Borrower (mobile app) | Create request, upload PO, accept note, confirm goods received, update dates |
| `ROLE_MERCHANT` | Vendor (mobile app) | See linked customers, confirm delivery |
| `ROLE_USER` | Internal reviewer (dashboard) | Review eligibility, review attachments |
| `ROLE_APPROVER` | Internal approver (dashboard) | Final approve/reject |
| `ROLE_ADMIN` | Admin | SBF facility creation, full access |

Permissions (`ez_user_permission` + `ez_role_permission`) carry `grouping/code/entityName/actionName/canMakerChecker` for fine-grained UI gating.

---

## 5. Endpoint catalog

Base path `/api/v1`. `Message<T>` is the standard wrapper: `{ type, message, data }` (`type` ∈ `SUCCESS|FAILED|NOT_FOUND|INTERNAL_SERVER_ERROR|…`).

### Auth — `AuthenticationController` (`/auth`)
| Method | Path | Access | Body / params | Returns |
|---|---|---|---|---|
| POST | `/auth/authenticate` | permitAll | `AuthenticationRequest` | `AuthenticationResponse` (JWT `token`) |
| POST | `/auth/register` | permitAll | `RegisterModel` | `Message<User>` |
| POST | `/auth/verifyOTP` | permitAll | `?otp=` | status string |
| POST | `/auth/resendOTP/{userId}` | permitAll | — | status string |
| POST | `/auth/resetPassword` | permitAll | `?otp=` + `PasswordModel` | string |
| POST | `/auth/savePassword` | permitAll | `?token=` + `PasswordModel` | string |
| POST | `/auth/changePassword` | authenticated | `ChangePasswordModel` | string |
| GET | `/auth/me` | authenticated | — | `Message<User>` |

### Note (loan) — `NoteController` (`/note`)
| Method | Path | Role | Purpose | Status effect |
|---|---|---|---|---|
| POST | `/note` | CUSTOMER | Create request; runs `checkCreditRule`, auto-rejects on fail | → `Draft` (or `Rejected`) |
| POST | `/note/{id}/review` | USER | `isEligible`, `reason` | `Draft` → `Reviewed` / `Rejected` |
| POST | `/note/{id}/approve` | APPROVER | `isApproved`, `comment` | `Reviewed` → `Approved` / `Rejected` |
| POST | `/note/{id}/accept` | CUSTOMER | `isAccepted` → `submitNoteToCBS()` | `Approved` → `Pending_Delivery` / `Rejected` |
| POST | `/note/{id}/received` | CUSTOMER | `isAccepted` → `autoDisburse()` | `Pending_Confirmation` → `Disbursed` / `Rejected` |
| POST | `/note/update-disbursement` | CUSTOMER | `NoteDisbursementUpdate` | dates only (no status change) |
| GET | `/note/{id}` | USER | one note + PO attachment (`NoteConsole`) | — |
| GET | `/note` | USER | all notes | — |
| GET | `/note/my-note` | CUSTOMER | caller's notes | — |
| GET | `/note/tasks` | USER/APPROVER | `?task=TO_REVIEW\|TO_APPROVE\|ALL` queue | — |
| GET | `/note/tasks/attachment` | USER/APPROVER | `isReviewed`, `docType` | — |
| GET | `/note/my-transaction` | CUSTOMER | enriched transactions (aging, attachments) | — |
| GET | `/note/holidayCheck` | CUSTOMER | `?date=` (ms) → boolean | — |
| POST | `/note/attachment` | CUSTOMER | multipart `entityClass`, `noteId`, `files[]` | uploads PO / delivery note |
| GET | `/note/{id}/attachment` | CUSTOMER/USER/MERCHANT | `?docType=` | — |
| POST | `/note/attatchment/{noteId}/review` | USER | `isCorrect`, `docType`, `reason` | attachment review |
| GET | `/note/my-merchants` | CUSTOMER | merchants with active loans | — |

### SBF passthrough — `SBFApiController` (`/sbf`)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/sbf/note/my-account` | CUSTOMER | caller's facility + loans (live from SBF) |
| GET | `/sbf/note/{cif}` | USER/ADMIN | facility by CIF |
| POST | `/sbf/note` | ADMIN | create loan facility in SBF |
| GET | `/sbf/holiday` | ADMIN | SBF non-business-days |

### Merchant — `MerchantController` (`/merchant`) + `MerchantUserController` (`/merchant-user`, ROLE_MERCHANT)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/merchant` | permitAll | merchant list |
| GET | `/merchant/{id}` , `/merchant/by-code/{code}` , `/merchant/all` | ADMIN/USER | merchant lookups |
| GET | `/merchant-user/my-customer` | MERCHANT | customers with active loans |
| GET | `/merchant-user/my-customer/{noteId}` | MERCHANT | customer by note |
| POST | `/merchant-user/confirm-delivery` | MERCHANT | multipart `noteId`, `files[]` → `Pending_Confirmation` |

### Lookups & files
- `NotePeriodController` (`/note-period`, `/note-period/rate`) — loan tenor/rate lookups (permitAll GET; ADMIN/USER POST).
- `AttachmentController` (`/file/...`) — generic upload (`/file/upload`, `/file/uploads`) + serve (`/file/show/...`, with optional `{width}/{height}` scaling). **Reusable for any product.**

---

## 6. Domain model & database

Tables: `ez_` = platform/shared, `sbf_` = the Note product.

| Table | Entity | Notes |
|---|---|---|
| `sbf_note` | `Note` | **The loan.** Amounts/dates, `notePeriod` FK, merchant fields, `status` (`NoteStatusEnum`) + workflow boolean/audit flags; extends `UserDateAudit` |
| `sbf_note_period` | `NotePeriod` | tenor (`noDays`) + `rate`, `description`/`descriptionKh` |
| `sbf_note_period_rate` | `NotePeriodRate` | fee matrix by amount (`noteAmount`, `noteFee`, `totalNote`) |
| `ez_note_attachment` | `NoteAttachment` | doc per note: `docType` (`DocumentTypeEnum`), review flags; → `Image` (1:M) |
| `ez_file_image` | `Image` | physical file storage (generic) |
| `ez_user` | `User` | customers, merchants, staff; `registedId` = **CIF** (the SBF customer key) |
| `ez_user_role` / `ez_user_permission` / `ez_role_permission` / `ez_user_role_join` | `Role`, `Permission` | RBAC |
| `ez_otp` | `OneTimePassword` | registration / reset OTP |
| `ez_verification_token`, `ez_user_password_reset_token` | tokens | email verify / reset |
| `ez_notification` | notification | push records |

**Not persisted locally — fetched live from SBF** (DTOs in `sbf/model`): `ConsolidateData`, `CreditFacilityMaster`, `LoanFacilityInfo`, `Customer`/`CustomerInformation`, `SavingAccount`, `Merchant`, `Holiday`.

**Key relationships:** `Note → NotePeriod` (N:1), `Note → User` (N:1), `Note → NoteAttachment` (1:M), `NoteAttachment → Image` (1:M). The customer's **CIF (`User.registedId`)** is the join key into SBF (facility, merchants).

---

## 7. The loan (Note) state machine

`NoteStatusEnum`: `Draft, Reviewed, Approved, Pending_Acceptance, Pending_Delivery, Pending_Confirmation, Disbursed, Rejected, Paid, Expired`.

```
 [customer POST /note]
        │  checkCreditRule() fails ─────────────► Rejected
        ▼
      Draft ──[USER /review isEligible=false]──► Rejected
        │ [USER /review isEligible=true]
        ▼
    Reviewed ──[APPROVER /approve isApproved=false]──► Rejected
        │ [APPROVER /approve isApproved=true]
        ▼
    Approved ──[CUSTOMER /accept isAccepted=false]──► Rejected
        │ [CUSTOMER /accept] → submitNoteToCBS() creates SBF LoanFacility (loanFacRefNo, loanFacId)
        ▼
 Pending_Delivery ──[MERCHANT /merchant-user/confirm-delivery uploads delivery note]──► Pending_Confirmation
        ▼
 Pending_Confirmation ──[CUSTOMER /received isAccepted=true] → autoDisburse() to SBF──► Disbursed
        ▼
     Disbursed ──[repayment in SBF]──► Paid          (any time, facility expiry ► Expired)
```

| From | Actor | Endpoint | Side effect | To |
|---|---|---|---|---|
| — | Customer | `POST /note` | `noteRequest()` + `checkCreditRule()` | `Draft` / `Rejected` |
| Draft | Reviewer (USER) | `POST /note/{id}/review` | sets `isReviewed/reviewedBy/Date` | `Reviewed` / `Rejected` |
| Reviewed | Approver (APPROVER) | `POST /note/{id}/approve` | sets `isApproved/approvedBy/Date/Comment` | `Approved` / `Rejected` |
| Approved | Customer | `POST /note/{id}/accept` | `submitNoteToCBS()` → SBF `saveLoanFacilities` | `Pending_Delivery` / `Rejected` |
| Pending_Delivery | Merchant | `POST /merchant-user/confirm-delivery` | uploads delivery note | `Pending_Confirmation` |
| Pending_Confirmation | Customer | `POST /note/{id}/received` | `autoDisburse()` → SBF `auto-disburse` | `Disbursed` / `Rejected` |
| Disbursed | SBF | (external repayment) | — | `Paid` |

**Hardcoded credit rules** (`NoteServiceImpl.checkCreditRule`): reject if repayment date > facility expiry, request amount > facility `amtLimit`, or any existing loan has `aging > 0`. (App mirrors these client-side, plus min disbursement date = today + 4 days and a non-working-day/holiday check.)

---

## 8. SBF core-banking integration

`SbfAuthorization` (in `security/service`) does OAuth2 password grant against the SBF token endpoint and caches/refreshes the access token. `SBFApiServiceImpl` then makes Bearer calls:

| Method | SBF endpoint (relative to `url_api`) | Used by |
|---|---|---|
| `getFacilityByCIF` / `getAccountByCif` | `GET /group-facilities/by-cid?custKeyNum={cif}` | `/sbf/note/my-account` (dashboard) |
| `createLoanFac` | `POST /saveLoanFacilities` | note **accept** (`submitNoteToCBS`) |
| `autoDisburse` | `POST /auto-disburse` (`DisburseModel`) | goods **received** (`confirmReceivedGood`) |
| `getMyMerchant` | `GET /merchant/by-cid?custKeyNum={cif}` | `/note/my-merchants` |
| `getMerchantById/ByCode/All` | `GET /merchant...` | merchant lookups |
| `getHolidays` | `GET /non-business-day` | holiday check |

**`DisburseModel`** maps note → SBF disbursement and carries several **SBF-specific hardcoded constants** (in `NoteServiceImpl`): on `submitNoteToCBS` — `creditTypeId=845` (= the SBF Note product), `currId=2` (KHR), `catId="CFL"`, `creditStatusId=4`, `busTypeId=1`, `empRepId=273`, `specialNoteId=1`, `isRollOver="N"`; on `autoDisburse` — `interestRate=0.00`, `monthlyFeeRate=notePeriod.rate`, `repaymentMethod="D"` (lump sum), `noOfInstallment=1`, `disburseBy="ACTR"`, `authBy/doneBy="Kjey_PAPA"`. Timezone `Asia/Phnom_Penh`.

> These constants are the clearest example of **product-specific coupling**: a different product (e.g. installment PDL) needs different `creditTypeId`, `repaymentMethod`, installments, and possibly a different external system entirely (Sambat LOS).

---

## 9. Configuration & secrets

`application.properties` holds only `${ENV_VAR}` placeholders — **no secrets committed**. Local dev overrides them in the gitignored `application-local.properties` (`local` profile); production uses environment variables.

| Concern | Keys |
|---|---|
| DB | `spring.datasource.url/username/password` |
| JWT | `security.jwt.secret-key` |
| SBF OAuth/API | `token_endpoint`, `url_api`, `authorization` (Basic), `urlencoded_token`, and the `url_*` path keys (`urlencoded_facility`, `url_create_loan_fac`, `url_disburse`, `url_merchant*`, `url_non_working_day`) |
| Mail | `spring.mail.*` (Gmail SMTP, OTP/reset) |
| Firebase | `gcp.firebase.service-account` |

Required prod env vars: `DB_USERNAME/PASSWORD`, `JWT_SECRET_KEY`, `MAIL_USERNAME/MAIL_APP_PASSWORD`, `ADMIN_NOTIFY_EMAIL`, `SBF_USERNAME/PASSWORD`, `SBF_BASIC_AUTH_BASE64`, `FIREBASE_CREDENTIALS_PATH`.

---

## 10. Where things live (quick index)

| You want… | Look at |
|---|---|
| An endpoint | `sbf/api/*Controller.java`, `security/controller/*` |
| Loan lifecycle logic | `sbf/service/NoteServiceImpl.java` |
| External SBF calls | `sbf/service/SBFApiServiceImpl.java`, `security/service/SbfAuthorization.java` |
| The loan entity / statuses | `sbf/model/Note.java`, `sbf/model/NoteStatusEnum.java` |
| Auth / roles | `security/config/SecurityConfiguration.java`, `security/service/JwtService.java` |
| Config / secrets | `src/main/resources/application.properties` (+ `application-local.properties`) |

To add a new loan product, read [`ADD_LOAN_PRODUCT.md`](./ADD_LOAN_PRODUCT.md).
