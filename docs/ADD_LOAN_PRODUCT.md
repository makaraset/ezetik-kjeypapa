# Backend — Adding a New Loan Product

> How to add a new loan product to `ezetik-kjeypapa`. Read [`ARCHITECTURE.md`](./ARCHITECTURE.md) first.
> The app side is covered in `kjeypapa_app/docs/ADD_LOAN_PRODUCT.md` — **define the API contract here first, then build the app**.

The codebase today implements **one** product, the **SBF Note** (`sbf/` package — `Note`, `NoteController`, `NoteServiceImpl`, `NoteStatusEnum`). There is **no product abstraction**. This guide gives you two paths:

- **Part A — Replicate the Note pattern.** Works with today's code, zero refactor. Best for adding the *first* one or two extra products fast.
- **Part B — Introduce a product abstraction.** A one-time refactor that makes every *future* product a small, isolated addition. Recommended once you know ≥3 products are coming (PDL, MSIL, PL, Trade Finance, …).

A **worked example** (PDL / Salary Loan via Sambat LOS) runs through both.

---

## What is reusable vs product-specific

**Reusable as-is — do NOT duplicate:**
- `security/` — auth, JWT, RBAC, users, OTP, password reset.
- `image/` + `AttachmentController` (`/file/...`) — generic file upload/serve.
- `notification/` — Firebase push.
- `Message<T>`, `UserDateAudit`, the `@PreAuthorize` role model, the `application-*.properties` secrets pattern.

**Product-specific — new product needs its own:**
- Entity + status enum + workflow flags (`Note`, `NoteStatusEnum`).
- Repository, payloads, service, controller (`NoteRepository`, `NotePayload`, `NoteServiceImpl`, `NoteController`).
- External-system integration (`SBFApiServiceImpl` + the `DisburseModel`/`LoanFacility` mapping and the hardcoded SBF constants — `creditTypeId=845`, `repaymentMethod="D"`, etc.).
- Document types (`DocumentTypeEnum`), tenor/rate model (`NotePeriod`) if the product prices differently.

---

# Part A — Replicate the Note pattern (no refactor)

Build the new product as a sibling of the Note, under `sbf/` (or a new package, e.g. `salaryloan/`). Order matters — go top-down from the data model.

### A1. Entity + status
- [ ] New entity extending `UserDateAudit` (e.g. `SalaryLoan` → table `salary_loan`). Copy the shared columns (user, amount, dates, audit) and add product-specific ones (e.g. employment info, salary, no merchant).
- [ ] New status enum (e.g. `SalaryLoanStatusEnum`) — **do not reuse `NoteStatusEnum`** if the flow differs. PDL: `Draft, Submitted, Processing, Approved, Rejected, Reworked, Accepted, Disbursed, Closed` (note: **no `Pending_Delivery`/`Pending_Confirmation`** — there's no merchant/goods step).
- [ ] Product-specific workflow/audit flags as needed (acceptedBy/Date, e-contract ref, CBC-consent flag).

### A2. Repository
- [ ] `SalaryLoanRepository extends JpaRepository<SalaryLoan, Integer>` with the queries you need (`findByUserId`, `findByStatus`, …) — mirror `NoteRepository`.

### A3. Payloads (the API contract — agree with the app team)
- [ ] Request payload (`SalaryLoanPayload`) — what the app POSTs to create.
- [ ] Response/transaction payload (`SalaryLoanTransaction`) — what the app lists.
- [ ] Action payloads (accept/reject, e-contract upload) as the flow requires.

### A4. Service
- [ ] `SalaryLoanService` interface + `SalaryLoanServiceImpl` mirroring `NoteServiceImpl`'s methods that apply: `request()`, `checkCreditRule()`, `accept()`, etc. **Re-implement only the steps the product has** (PDL has no `confirmReceivedGood`).
- [ ] Product-specific credit rules and external calls live here.

### A5. External integration
- [ ] If the product uses **SBF core-banking**, reuse `SBFApiService` but pass product-specific values (different `creditTypeId`, repayment method, installments).
- [ ] If it uses a **different system (e.g. Sambat LOS)**, add a new client service (`LosApiService(Impl)` + `LosAuthorization` if a separate OAuth) alongside `SbfAuthorization` — same shape as `SBFApiServiceImpl`. Add its URL/secret keys to `application.properties`/`-local`.

### A6. Controller
- [ ] `SalaryLoanController` (`@RequestMapping("/api/v1/salary-loan")`) mirroring `NoteController` — one endpoint per transition, each `@PreAuthorize(...)`. Keep `Message<T>` responses.

### A7. Attachments, lookups, RBAC, DB
- [ ] Extend `DocumentTypeEnum` (or add a product enum) for new doc types (e.g. `EMPLOYMENT_LETTER`, `SIGNED_CONTRACT`, `CBC_CONSENT`). Reuse `/file/...` + `NoteAttachment` pattern.
- [ ] New tenor/rate model only if pricing differs from `NotePeriod`.
- [ ] Add any new roles (e.g. `ROLE_LPO`, `ROLE_BM`, `ROLE_HQC`) to the role tables + `@PreAuthorize`.
- [ ] `ddl-auto=update` creates the new table automatically in dev; write a migration for prod.

### A8. Notifications
- [ ] Reuse `notification/` to push status changes (approved / rework / rejected).

> **Cost of Part A:** you now have two near-parallel stacks (`Note*` and `SalaryLoan*`). Shared logic (auth gating, attachment handling, notification dispatch) is duplicated. Acceptable for 2 products; painful at 4+.

---

# Part B — Recommended product abstraction (one-time refactor)

Introduce a thin abstraction so each product is an isolated *implementation*, and shared orchestration lives once. Keep the existing `Note` working by making it the first implementation.

### B1. Entity hierarchy
```java
@Entity @Inheritance(strategy = InheritanceType.JOINED)
public abstract class LoanProductEntity extends UserDateAudit {
    @Id @GeneratedValue Integer id;
    @Enumerated(EnumType.STRING) ProductType productType;   // SBF_NOTE, PDL, MSIL, PL, TRADE_FINANCE
    BigDecimal amount;
    LocalDate disbursementDate, repaymentDate;
    String statusCode;                                       // product-defined status, stored as string
    // shared audit/customer fields
}

@Entity public class Note extends LoanProductEntity { /* existing SBF-specific columns */ }
@Entity public class SalaryLoan extends LoanProductEntity { /* employment, salary, eContractRef, cbcConsent */ }
```
JOINED inheritance keeps `loan_product` (shared columns) + per-product child tables; `Note`'s data migrates into `loan_product` + `note`.

### B2. Service strategy + factory
```java
public interface LoanProductService {
    ProductType type();
    Message<?> create(LoanProductPayload payload);
    Message<?> review(int id, boolean eligible, String reason);
    Message<?> approve(int id, boolean approved, String comment);
    Message<?> accept(int id, boolean accepted);
    Message<?> disburse(int id);
    List<DocType> requiredDocuments();
    List<StatusDef> statusModel();                 // each product declares its own state machine
}

@Service class SbfNoteServiceImpl implements LoanProductService { /* today's NoteServiceImpl */ }
@Service class SalaryLoanServiceImpl implements LoanProductService { /* PDL logic */ }

@Service class LoanProductServiceFactory {
    private final Map<ProductType, LoanProductService> byType;       // Spring injects all impls
    LoanProductService get(ProductType t) { return byType.get(t); }  // keyed by service.type()
}
```
Not every product implements every method (PDL's `disburse` differs; it has no goods step) — give the interface sensible defaults or split into capability interfaces.

### B3. Pluggable core-system provider
Abstract the external integration so SBF-core vs Sambat-LOS is swappable per product:
```java
public interface CoreLendingProvider {
    String name();                                 // "SBF", "LOS"
    FacilityInfo getFacility(String cif);
    LoanRef createLoan(CreateLoanCmd cmd);
    DisburseResult disburse(DisburseCmd cmd);
}
@Service class SbfCoreProvider implements CoreLendingProvider { /* SBFApiServiceImpl */ }
@Service class SambatLosProvider implements CoreLendingProvider { /* new LOS client */ }
```
Each `LoanProductService` depends on the provider it needs (Note → `SbfCoreProvider`; PDL → `SambatLosProvider`). This is the key seam for the upcoming LOS products.

### B4. Polymorphic (or per-product) controller
Either keep per-product controllers (clear, explicit) or add one router:
```java
@RestController @RequestMapping("/api/v1/loan-product")
class LoanProductController {
    @PostMapping("/{type}") Message<?> create(@PathVariable ProductType type, @RequestBody LoanProductPayload p) {
        return factory.get(type).create(p);
    }
    // /{type}/{id}/review|approve|accept|disburse ...
}
```
Recommendation: keep `/api/v1/note` for the existing product (don't break the app), add new products under their own path **or** the router — and have new app screens target the router.

### B5. Registries
- A `ProductType` enum + a small config/table describing each product (display name, the `creditTypeId`/LOS product code, doc types, status model). New product = add an enum value + register its service + its provider.

> **Migration path:** (1) extract `LoanProductEntity` and make `Note extends` it (data migration), (2) wrap today's `NoteServiceImpl` behind `LoanProductService`, (3) extract `SbfCoreProvider` from `SBFApiServiceImpl`. After that, PDL = new `SalaryLoan` entity + `SalaryLoanServiceImpl` + `SambatLosProvider` + enum value. No existing code changes.

---

# Worked examples — TFF (cheap) and PDL (the full exercise)

From the Sambat 2026 BRS + Salary-Loan CR + UI mockups (`kjeypapa_app/docs/`).

**TFF (Trade Finance Facility) ≈ the existing backend.** TFF is the *same* facility + merchant + purchase-order note flow that `Note` / `NoteServiceImpl` / the SBF integration already implement (the app's TFF request screen is today's Note request). Adding TFF is mostly **registration + branding** — reuse `Note`, `NoteStatusEnum`, and `SbfCoreProvider`. The real test of this guide is **PDL**.

**PDL / Salary Loan** differs from the SBF Note in nature:

| Aspect | SBF Note (today) | PDL / Salary Loan |
|---|---|---|
| External system | SBF core-banking (`/saveLoanFacilities`, `/auto-disburse`) | **Sambat LOS** (loan application APIs) |
| Counterparty | Merchant (goods supplier) | **None** — direct to customer |
| Documents | Purchase order + delivery note | Employment letter, **CBC consent**, **signed e-contract** |
| Approval | USER review → APPROVER approve | **LPO → BM → HQC** workplaces |
| Reject / rework | Reject only | **Reject vs Rework vs Revoke** (customer cancels), product-specific codes (see below) |
| Disbursement | After goods received | After **e-contract** accepted ("Pay Disbursement") |
| Customer | Existing (has CIF) | **New (CIF created on approval)** or existing |
| Repayment | Lump sum (`repaymentMethod="D"`, 1 installment, **0% interest** — fee only) | **Installment** schedule **with interest** — principal + interest per due date; LOS-driven |

**LOS reject/rework codes the app must handle** (from the CR):
- **PDL** — *rework = reject*: LPO "Rework" → send `R-LPO`; BM/HQC reject/rework → `R-AO`. App shows: `R-LPO` → "Insufficient Information or Documents"; `R-AO` → "Not eligible for the loan".
- **MSIL / PL**: LPO rework → `RW-LPO`; BM/HQC reject → `R-AO`; BM/HQC rework → `RW-AO`.

**e-contract accept/reject:** on customer accept → app sends e-contract with `Y` to LOS → LOS starts "Pay Disbursement". On reject or confirmation-time expiry → app sends `N` (no e-contract) → LOS rejects ("loan is rejected by customer").

**Backend tasks for PDL (Part B):**
1. `ProductType.PDL` (+ `MSIL`, `PL`).
2. `SalaryLoan extends LoanProductEntity` — employment, salary, `cbcConsentRef`, `eContractRef`, `losAppRefNo`, **`interestAmount`**, plus a **`PaymentSchedule`** child (rows of `dueDate / principalDue / interestDue`) — PDL is **installment-repaid with interest**, unlike the Note's lump-sum 0% fee model.
3. `SalaryLoanServiceImpl implements LoanProductService` — status model `Submitted → Processing → Approved / Rejected / Pending_Rework → (Accepted / Revoked) → Disbursed → Closed`. **`Pending_Rework`** = customer must resubmit a doc (e.g. latest bank statement) then **Rework** (resubmit) or **Revoke** (cancel); **`Revoked`** = an approved app is cancelled by the customer (or lapses). Map the LOS codes (`R-LPO`/`R-AO`/`RW-LPO`/`RW-AO`) to `statusCode`. No internal `review/approve` (LOS owns approval) — instead **ingest LOS status via webhook/callback** and persist the **payment schedule** LOS returns.
4. `SambatLosProvider implements CoreLendingProvider` — new-app create (with/without CIF), poll/callback for status, push e-contract (`Y`/`N`), trigger disbursement. New OAuth (`LosAuthorization`) + `los_*` config keys.
5. New doc types: `EMPLOYMENT_LETTER`, `CBC_CONSENT`, `SIGNED_CONTRACT` (reuse `/file/...`).
6. New roles only if internal staff use this backend for PDL (`ROLE_LPO/BM/HQC`) — but approval lives in LOS, so the backend mostly **relays LOS callbacks** and serves the app.

---

## Checklist (copy per product)

- [ ] `ProductType` value + product config registered
- [ ] Entity (+ child table) and status model — **incl. rework / revoked states** where the product has them
- [ ] **Repayment model** — lump-sum (Note/TFF) vs **installment + a `PaymentSchedule` child + `interestAmount`** (PDL)
- [ ] Repository
- [ ] Request/response/action payloads (contract shared with app team)
- [ ] Service implementation (`LoanProductService`) — only the steps the product has; **status callback/webhook ingest if the external system (LOS) owns approval**
- [ ] Core-system provider (`CoreLendingProvider`) — reuse SBF or add LOS client + OAuth + config keys
- [ ] Controller endpoints (per-product or via router) with `@PreAuthorize`
- [ ] Document types + attachment wiring (reuse `/file/...`)
- [ ] Tenor/rate model (reuse `NotePeriod` or new)
- [ ] Roles/permissions if new actors
- [ ] DB migration for prod
- [ ] Notifications for the product's status changes
- [ ] Swagger tag + README note
- [ ] Hand the endpoint contract to the app team → `kjeypapa_app/docs/ADD_LOAN_PRODUCT.md`
