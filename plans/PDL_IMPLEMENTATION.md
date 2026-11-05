# PDL (Payday Loan) — Backend Implementation Plan

> Detailed, file-by-file plan to add the **Payday Loan (PDL)** product to `ezetik-kjeypapa`.
> Read first: `docs/ARCHITECTURE.md`, `docs/ADD_LOAN_PRODUCT.md`. App side: `kjeypapa_app/plans/PDL_IMPLEMENTATION.md`.

## 1. Summary & decisions

PDL is the first **Salary Loan** product. It integrates with **Sambat LOS (TurnKey Lender)**, **not** SBF core-banking.

- **Topology:** app → **this backend** → LOS. The backend is the LOS **client** (submit application; send accept/reject) and the LOS **webhook receiver** (reject / rework / approved / product-sync). LOS owns decisioning (LPO→BM→HQC), generates the loan form + contract + **repayment schedule**, and runs disbursement (Campu Bank direct-debit).
- **LOS contract = TBD** (BRS Appendices 1–6 are empty). Build a clean **`LosProvider` boundary** + DTO shells; mark endpoints/params `// TODO: per LOS Appendix N`. Until then, `LosProviderImpl` runs in **mock mode** so the whole app↔backend flow is testable.
- **Approach: replicate the `sbf/` Note pattern** in a new **`com.ezetik.kjeypapa.pdl`** package. The existing Note/TFF product is **untouched**.
- **The backend stores LOS-pushed data (schedule, contract); it does NOT compute interest/schedule or own approval.**

**Reuse as-is:** `security/` (JWT, RBAC, `UserService`, `User`), `security/audit/UserDateAudit`, `image/` + `AttachmentController` (`/file/...`) for the 5 docs, `notification/` for push, `security/util/Message`.

## 2. New package layout (`src/main/java/com/ezetik/kjeypapa/pdl/`)

```
pdl/
├── model/      PaydayLoan, PdlStatusEnum, PdlDocTypeEnum, PdlPaymentSchedule,
│               PdlEmploymentInfo, PdlBankInfo, PdlAttachment
├── payload/    PdlApplicationPayload, PdlTransaction, PdlAcceptDecision,
│               LosNotificationPayload, LosProductSyncPayload, PdlPaymentScheduleResponse
├── repository/ PaydayLoanRepository, PdlPaymentScheduleRepository, PdlAttachmentRepository,
│               PdlEmploymentInfoRepository, PdlBankInfoRepository
├── service/    PaydayLoanService(+Impl), LosProvider(+Impl), LosAuthorization,
│               LosWebhookService
└── api/        PaydayLoanController, LosWebhookController
```

Template files to copy/adapt (don't edit the originals): `sbf/model/Note.java`, `NoteStatusEnum`, `NoteAttachment`, `sbf/payload/{NotePayload,NoteTransaction,NoteAttachmentResponse}`, `sbf/repository/{NoteRepository,NoteAttachmentRepository}`, `sbf/service/{NoteService,NoteServiceImpl}`, `sbf/api/NoteController`, `security/service/SbfAuthorization`.

---

## 3. Phase 1 — Model + enums + DB

### `pdl/model/PaydayLoan.java` (entity `pdl_payday_loan`, extends `UserDateAudit`)
The loan application record. Fields:
- `@Id @GeneratedValue Integer id`
- `@ManyToOne User user` — the borrower
- `Double requestAmount`, `Double repaymentAmount`, `Double interestAmount`, `Double processingFee`
- `Integer loanPeriodDays` (e.g. 15), `Instant disbursementDate`, `Instant repaymentDate`, `Instant applicationDate`
- `@Enumerated(STRING) PdlStatusEnum status`
- `@ManyToOne PdlEmploymentInfo employmentInfo`, `@ManyToOne PdlBankInfo bankInfo`
- `String cbcConsentRef`, `Boolean bankConsent`
- **LOS sync:** `String losApplicationNo`, `String losStatusCode` (`R-LPO`/`R-AO`/…), `String losMessage`, `String loanContractFileRef`, `String repaymentScheduleRef`, `String loanFormRef` (refs to LOS-pushed generated docs stored via `image/`)
- **Decision/audit:** `acceptedBy/Date`, `Boolean isAccepted`, `signedContractRef`, `revokedBy/Date`, `String revokeReason`, `disbursementTxnId`
- **No** `merchantCode/facilityNo/loanFacId` (PDL has no merchant/facility).

### `pdl/model/PdlStatusEnum.java`
```java
Draft, Submitted, Rejected, Approved, Accepted, Disbursed, Active, Closed, Revoked
```
(PDL has **no `Rework`** — rework=reject. Add `Rework` only when MSIL/PL are introduced.)

### `pdl/model/PdlDocTypeEnum.java`
```java
E_CBC_CONSENT, PROFILE_PHOTO, NID, EMPLOYMENT_CARD, BANK_STATEMENT, SIGNED_CONTRACT
```
(The 5 mandatory application docs per BRS §2.3, plus the customer-signed contract for accept.)

### `pdl/model/PdlEmploymentInfo.java` (entity `pdl_employment_info`, extends `UserDateAudit`)
Per mockup `5.3`/`11`: `@ManyToOne User user`, `employmentType` (Employee/…), `employerName`, `businessActivities`, `occupation`, `Instant employmentStartDate`, `employmentStatus`, `Double monthlyIncome`, `currency`, work-address (`country/province/district/commune/village`), `employmentCardFileRef`, `verified` flags.

### `pdl/model/PdlBankInfo.java` (entity `pdl_bank_info`, extends `UserDateAudit`)
Per mockup `5.4`: `@ManyToOne User user`, `bankName`, `accountName`, `accountNo`, `currency`, `bankStatementFileRef`, `Boolean consentGiven`, `verified`.

### `pdl/model/PdlPaymentSchedule.java` (entity `pdl_payment_schedule`)
Installment rows, **stored from the LOS-pushed schedule**: `@ManyToOne PaydayLoan pdl`, `Integer installmentNo`, `Instant dueDate`, `Double principalDue`, `Double interestDue`, `Double feeDue`, `Double totalDue`, `Double amountPaid`, `Instant paidDate`, `String status` (PENDING/PAID/OVERDUE).

### `pdl/model/PdlAttachment.java` (entity `pdl_attachment`, mirror `NoteAttachment`)
`@ManyToOne PaydayLoan pdl`, `@Enumerated PdlDocTypeEnum docType`, `@OneToMany List<Image> attachFiles` (reuse `image.model.Image`), review flags.

**DB:** `ddl-auto=update` auto-creates `pdl_*` tables in dev. Write a Flyway/manual migration for prod.

---

## 4. Phase 2 — Payloads (the app↔backend contract)

- **`PdlApplicationPayload`** — app → backend submit body: the loan details (`requestAmount`, `repaymentAmount`, `loanPeriodDays`, `disbursementDate`), `employmentInfoId`/`bankInfoId` (or inline), `cbcConsentRef`, `bankConsent`, customer flag (new vs existing CIF). The personal-info fields map to `User` (registration).
- **`PdlTransaction`** — list/detail projection (id, losApplicationNo, requestAmount, applicationDate, status, losStatusCode, message, attachments) — mirror `NoteTransaction`; build via repository JPQL `new …PdlTransaction(p)`.
- **`PdlAcceptDecision`** — accept body: `decision` (`Y`/`N`), `signedContractRef` (when `Y`).
- **`LosNotificationPayload`** — **inbound webhook** from LOS: `losApplicationNo`, `event` (REJECT/REWORK/APPROVED/PRODUCT_SYNC), `statusCode`, `message`, generated-doc refs (form/contract/schedule), `signature`. *(field-level TBD per Appendices 3–5.)*
- **`LosProductSyncPayload`** — product config LOS pushes (BRS §2.2): product code (PDL/MSIL/PL), tenor/rate/fee tables, limits. *(TBD per Appendix 1.)*
- **`PdlPaymentScheduleResponse`** — schedule rows for the app.

---

## 5. Phase 3 — Repositories

Mirror `NoteRepository`:
- `PaydayLoanRepository extends JpaRepository<PaydayLoan,Integer>`: `findByUserId(int)`, `findByStatus(PdlStatusEnum)`, `findByStatusIn(List)`, `findByLosApplicationNo(String)`, `@Query … new …PdlTransaction(p) … findTransactionByUserId(int)`.
- `PdlPaymentScheduleRepository`: `findByPdlIdOrderByInstallmentNoAsc(int)`.
- `PdlAttachmentRepository`: `findByPdlIdAndDocType(int, PdlDocTypeEnum)`, review/count helpers (mirror `NoteAttachmentRepository`).
- `PdlEmploymentInfoRepository`, `PdlBankInfoRepository`: `findByUserId`.

---

## 6. Phase 4 — Services (incl. the LOS boundary)

### `pdl/service/PaydayLoanService` (+`Impl`, `@Service`)
Methods (mirror `NoteServiceImpl` structure; `getCurrentUser()` via `SecurityContextHolder`+`UserService`):
- `submitApplication(PdlApplicationPayload)` → create `PaydayLoan` (status `Draft`), **validate the 5 mandatory docs are present** (else return a `Message` error per BRS §2.3), call `losProvider.submitApplication(...)`, set `losApplicationNo` + status `Submitted`.
- `getMyApplications()` / `getMyTransactions()` — caller's list (status-filtered for the app's Rework/Approved/Rejected sub-tabs).
- `accept(int id, PdlAcceptDecision)` → if `Y`: store `signedContractRef`, set `Accepted`, `losProvider.sendDecision(losAppNo, "Y", contract)`; if `N`: set `Rejected`(by customer), `sendDecision(..., "N", null)`.
- `revoke(int id, reason)` → set `Revoked` (+ audit), notify LOS if required.
- `uploadDocument(int id, PdlDocTypeEnum, MultipartFile[])` → reuse the Note attachment flow (`Image.buildImage`, `image` storage).
- `getPaymentSchedule(int id)` → `PdlPaymentScheduleRepository`.

### `pdl/service/LosProvider` (interface) + `LosProviderImpl` (`@Service`) — **THE INTEGRATION BOUNDARY**
```java
public interface LosProvider {
    LosSubmitResult submitApplication(PaydayLoan loan, ApplicantDocs docs); // BRS §2.3
    void sendDecision(String losAppNo, String yn, String signedContractRef); // BRS §2.7
    void onProductSync(LosProductSyncPayload payload);                       // BRS §2.2
}
```
`LosProviderImpl`: builds the LOS request (CIF for existing / no-CIF for new customer; loan details; the 5 docs), POSTs to LOS, parses the response. **All endpoints/params are `// TODO: per LOS Appendix N`.** Add a config flag `los.mock.enabled=true` → return canned responses so the full flow works without live LOS. `LosAuthorization` (copy of `SbfAuthorization`) handles LOS OAuth (TBD token endpoint).

### `pdl/service/LosWebhookService` (`@Service`)
`handleReject / handleRework / handleApproved / handleProductSync(LosNotificationPayload)`:
- verify `signature` (TBD scheme),
- load `PaydayLoan` by `losApplicationNo`,
- **Reject** (§2.4) → status `Rejected`, store `losStatusCode`/`message`, push notification.
- **Rework** (§2.5) → for PDL map `R-LPO`/`R-AO` to `Rejected` + the user message; store code (the app shows the message).
- **Approved** (§2.6) → status `Approved`, store loan-form/contract/**repayment-schedule** refs (save the schedule rows into `pdl_payment_schedule`), push notification.
- **Product sync** (§2.2) → upsert product config.

---

## 7. Phase 5 — Controllers + security

### `pdl/api/PaydayLoanController` (`@RequestMapping("/api/v1/pdl")`)
| Method | Path | `@PreAuthorize` | Body/params |
|---|---|---|---|
| POST | `/` | CUSTOMER | `PdlApplicationPayload` → submit |
| GET | `/my-applications` | CUSTOMER | — |
| GET | `/my-transactions` | CUSTOMER | — |
| GET | `/{id}` | CUSTOMER/USER | detail |
| POST | `/{id}/accept` | CUSTOMER | `PdlAcceptDecision` (Y/N) |
| POST | `/{id}/revoke` | CUSTOMER | `?reason=` |
| GET | `/{id}/payment-schedule` | CUSTOMER | — |
| POST | `/document` | CUSTOMER | multipart `pdlId`, `docType`, `files[]` (mirror `NoteController.noteAttachment`) |
| GET | `/product` | CUSTOMER | synced product config |

### `pdl/api/LosWebhookController` (`@RequestMapping("/api/v1/pdl/los")`)
`POST /reject`, `/rework`, `/approved`, `/product-sync` — inbound from LOS, body `LosNotificationPayload`/`LosProductSyncPayload`, `X-LOS-Signature` header. Delegates to `LosWebhookService`.

### `security/config/SecurityConfiguration` (MODIFY — minimal)
Add `/api/v1/pdl/los/**` to the auth strategy: either whitelist + verify the LOS signature inside the controller, or a dedicated webhook filter. (Everything else under `/api/v1/pdl/**` stays JWT-protected.)

### `application.properties` (MODIFY — add, values TBD)
```
los.mock.enabled=true
los.api.baseurl=${LOS_API_BASEURL:}
los.oauth.token_endpoint=${LOS_TOKEN_ENDPOINT:}
los.oauth.client_id=${LOS_CLIENT_ID:}
los.oauth.client_secret=${LOS_CLIENT_SECRET:}
los.webhook.secret=${LOS_WEBHOOK_SECRET:}
# endpoint paths TBD per Appendices 1-6
```

---

## 8. CREATE / MODIFY summary

**CREATE (≈24):** `pdl/model/*` (7), `pdl/payload/*` (6), `pdl/repository/*` (5), `pdl/service/*` (5: PaydayLoanService+Impl, LosProvider+Impl, LosAuthorization, LosWebhookService), `pdl/api/*` (2).
**MODIFY (2):** `security/config/SecurityConfiguration.java`, `src/main/resources/application.properties` (+ `application-local.properties` for local LOS creds when available).
**REUSE (no change):** `security/audit/UserDateAudit`, `image/` + `AttachmentController`, `notification/`, `security/service/UserService`, `security/util/Message`.

## 9. Phasing (PR-sized)
1. models + enums + repos (+ DB) — compiles, tables created.
2. payloads + `PaydayLoanService` + `LosProvider` boundary in **mock mode**.
3. `PaydayLoanController` + document upload.
4. `LosWebhookController` + `LosWebhookService` (reject/rework/approved/product-sync) + security.
5. (when LOS contract arrives) fill `LosProviderImpl`/DTOs from Appendices 1–6; flip `los.mock.enabled=false`.
6. tests.

## 10. Verification
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` → `pdl_*` tables created; endpoints visible in `/swagger-ui.html`.
- With `los.mock.enabled=true`: `POST /api/v1/pdl` (CUSTOMER JWT) creates a `Submitted` app; `POST /api/v1/pdl/los/approved` (mock payload) transitions to `Approved` and stores a schedule; `GET /{id}/payment-schedule` returns it; `POST /{id}/accept` (Y) → `Accepted`.
- Unit-test the status transitions + the 5-doc validation; mock `LosProvider` in `PaydayLoanServiceImpl` tests.
- Hand the finalized `/api/v1/pdl/*` request/response shapes to the app team (`kjeypapa_app/plans/PDL_IMPLEMENTATION.md`).

## 11. Open items (confirm with TurnKey / business)
- LOS Appendices 1–6 (endpoints, params, auth, signature scheme).
- E-CBC consent: printed doc vs acknowledgement flag? Employment card / bank statement truly mandatory?
- Accept confirmation TTL (T or T+1) and the Campu Bank direct-debit handshake.
- Interest/fee: confirmed that **LOS computes & pushes** the schedule (backend only stores).
