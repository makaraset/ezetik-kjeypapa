# e-CBC consent — PDF, record, and how both are protected

| | |
|---|---|
| **For** | Sambat Finance / Tricube LOS |
| **From** | Kjey PAPA (Ezetik) |
| **Date** | 2026-09-07, rev. 2 *(supersedes the 2026-09-04 version)* |
| **Subject** | The consent record as built, and the fields we propose sending you |
| **Rev. 2** | Answers Chanthy Lay's three review comments: the timestamp format — now settled as UTC `Z` (§3) — who "customer" means, and what a request can do (§6) |

You asked for the CBC consent as **both a PDF and a record**. The 2026-09-04
version of this document proposed nine fields. Since then we have built the
record, and it is considerably more than was proposed — so this replaces that
document rather than amending it.

---

## What changed since 2026-09-04

| | Then | Now |
|---|---|---|
| Where the record lives | six columns on the loan row, rewritten by every webhook | its own append-only table |
| Tamper evidence | none | hash-chained; a database trigger refuses `UPDATE`, `DELETE`, `TRUNCATE` |
| The filed PDF | discarded after sending | archived, hash recorded, served back verbatim |
| Capture context | not recorded | app build, device model, IP address |
| The wording | interim translation | **your final Khmer text**, version `v3-2026-09` |
| Customer rights | nowhere to exercise them | the borrower can *request* access, correction or deletion in the app — see §6 |

Your rulings of 2026-09-05 are implemented: consent is **not withdrawable**,
consent records are **never destroyed**, and protecting against a duplicate
submission is **ours** — all three are described in section 6.

---

## 1. Why a record as well as the PDF

The PDF is what a person reads; the record is what a system can check. A PDF
alone cannot answer "was consent given, when, to exactly which wording?" without
someone opening it and reading. If a customer disputes a CBC enquiry, the record
answers it in one query and the hash proves the wording was not edited
afterwards.

Both describe the same event. Neither replaces the other — and since 2026-09-05
we keep the exact bytes of the PDF we sent you, so the two can be compared
rather than assumed to match.

## 2. What we send you today

| Field | Value |
|---|---|
| `Doc_ECBCConsentForm` | base64 PDF, A4, one page, **Khmer**, your final wording |
| `Doc_ECBCConsentForm_FileName` | `ECBCConsentForm-<loanId>.pdf` |

The bytes you receive are the bytes we archive. The document is rendered from
the consent record itself, so a customer editing their name later cannot change
the document belonging to an application already filed.

## 3. The record we hold

Twenty-nine fields. Grouped by the question each answers.

### The consent

| Field | Type | Example | Why |
|---|---|---|---|
| `consentRef` | string(64) | `CBC-35-v3-2026-09` | Our immutable reference. Ties record ↔ PDF ↔ application in support calls. |
| `consentGiven` | boolean | `true` | Stated, not implied. A record that relies on its own existence to mean "yes" is hard to audit. |
| `consentDate` | ISO-8601 instant, UTC | `2026-09-05T07:45:37.122303Z` | When. Stored and sent as UTC — the trailing `Z` **is** the offset (zero), so the instant is exact and unambiguous. This example is 14:45:37 on 05-Sep in Phnom Penh. |
| `losAppId` | int64 | `8306` | Your application id. Ties the consent to the enquiry it authorised. |

### What they agreed to

| Field | Type | Example | Why |
|---|---|---|---|
| `textVersion` | string(32) | `v3-2026-09` | *Which* wording. Lets wording be reissued without invalidating consents already given. |
| `textHash` | SHA-256 hex | `035f3ea0…dabc` | *Proves* the wording. A version label can be reused or its file edited; the hash cannot. This is the field that makes the record stand up in a dispute. |
| `textLanguage` | string(8) | `km` | Which language was actually read. |
| `channel` | string(32) | `MOBILE_APP` | An in-app tick box, not a wet signature. Distinguishes it from a branch-signed form. |
| `formReference` | string(128) | `Form _ CBC 01 _ … _ V2 _ 09122025` | Your form reference, as printed on the document. |

> **On time zones — settled 2026-09-07.** There is no risk of a wrong day, and
> nothing here is approximate. We store an instant, not a wall-clock reading,
> and always serialise it with an explicit zone. A day only shifts when a
> timestamp carries *no* zone and a reader guesses one; ours never omits it.
>
> **Agreed format: UTC with the `Z` designator.** The reason is specific to this
> field rather than a matter of style. `consentDate` is one of the values covered
> by the record's SHA-256 hash, and the hash is computed over the UTC rendering —
> so the timestamp you hold being byte-identical to ours is what lets either side
> verify a consent independently, years later, with no conversion rule anyone has
> to remember. It also matches every other timestamp in the payload, and the
> format your own webhooks already send us.
>
> Rendering it as local time for an operator to read is a display concern and
> belongs in the console. We would rather not send both forms: two timestamps
> that can drift apart leave nobody sure which is the record.

### Who the subject was, at the time

| Field | Type | Why |
|---|---|---|
| `idNo` | string(30) | Identifies the subject; the record reads standalone. |
| `customerNameKm`, `customerNameLatin` | string(255) | The name **as printed on the form the customer saw**, frozen. A later profile edit cannot rewrite history. |

### The document

| Field | Type | Why |
|---|---|---|
| `documentRef` | string(128) | The archived PDF. Retrievable by the customer and by us. |
| `documentHash` | SHA-256 hex | Proves the archived file is the one filed. Checked whenever it is served and by a nightly job. |

### How it was captured

| Field | Type | Example | Why |
|---|---|---|---|
| `appVersion` | string(32) | `2.0.1+45` | The build that displayed the wording. |
| `deviceModel` | string(64) | `iPhone14,3 / iOS 26.6.1` | The hardware identifier, **not** the user-assigned device name — that is usually a person's own name and has no place here. |
| `ipAddress` | string(45) | | Where the request came from. Honoured from a forwarding header only behind a proxy we operate; otherwise the socket address. |
| `captureSource` | string(32) | `SUBMIT` | Whether the record was captured as it happened or reconstructed later. See section 5. |
| `recordedBy` | string(64) | `010849001` | Who recorded it. |

### Tamper evidence

| Field | Type | Why |
|---|---|---|
| `canonVersion` | string(8) | Which canonical form the hash was computed under, so the scheme can change without lying about older rows. |
| `prevHash`, `rowHash` | SHA-256 hex | The chain. See section 4. |

## 4. How the record is protected

**Append-only.** Every column is non-updatable, and a database trigger rejects
`UPDATE`, `DELETE` and `TRUNCATE` outright. A trigger rather than a privilege
grant, because our application currently connects as a superuser and a grant
would look like protection while enforcing nothing.

**Chained.** Each record carries the hash of the one before it, over a canonical
form that names every field. Altering a record breaks its own hash; altering it
*and* recomputing breaks the link from the record after it. A test pins the
exact canonical string, because changing it would silently invalidate every hash
already on file.

**Witnessed off-box.** A chain cannot defend itself against someone who can
disable the trigger and recompute it. So a nightly job writes the head hash to
the application log, which ships off the database host. A chain whose head
disagrees with last night's line is evidence no in-database edit can erase.

**Verified.** Two endpoints walk it: one checks the chain, one re-hashes every
archived PDF against the hash its record claims. They are separate because the
documents live in an ordinary table with no trigger — a document can be swapped
while the chain still verifies perfectly.

**Never destroyed.** Per your 2026-09-05 ruling, no retention period is
configured and none is expected. The disposal job ships disabled and, even
enabled, only reports.

## 5. Honesty about what each record can prove

`captureSource` exists so an auditor can tell records apart rather than being
told they are equivalent. Of the 23 records we hold today:

| Source | Count | What it can prove |
|---|---|---|
| `SUBMIT` | 3 | Everything above, including the archived document. |
| `MIGRATION_003` | 19 | Consent given, when, and for which loan. Twelve of these predate the wording-hash, language and channel columns and are stored **empty rather than guessed** — a record that invents is worth less than one that says it does not know. |
| `RECONCILED` | 1 | A consent reconstructed after a submission whose reply was lost (section 6). Wording proved by hash; no archived document, because the filed PDF cannot be reproduced byte-for-byte and an approximation of evidence is not evidence. |

Our verification reports these as `incomplete`, never as `broken` — a failure
count nobody can ever clear would train everyone to ignore the alert.

## 6. Your rulings of 2026-09-05, as implemented

**Consent is not withdrawable.** The enquiry it authorises is made
automatically while the loan is assessed, so by the time anyone could withdraw
it there is nothing left to stop. Withdrawal is not offered in the app and the
API refuses the value.

**Customer** throughout this document means **the borrower** — the person using
the Kjey PAPA app — never an SBF or Kjey PAPA staff user.

A borrower may still **request** access to their data, a correction to it, or
its deletion. Three things are worth being precise about, because "request" is
doing real work in that sentence:

- **They cannot change or delete anything themselves.** The entire customer-facing
  surface is read and request-only: fifteen `GET` endpoints and nine `POST`, and
  no `PUT`, `PATCH` or `DELETE` anywhere on it. A request creates a record for a
  person to answer; it changes nothing on its own.
- **A request is decided by an operator**, and the decision is recorded with who
  made it, when, and why. A refusal must carry a reason — the API rejects one
  without.
- **A consent record is never edited or deleted by anyone**, borrower or staff,
  including us. It is append-only and the database refuses `UPDATE`, `DELETE`
  and `TRUNCATE` outright. "Deletion" concerns a borrower's personal data
  generally; per your own ruling of 2026-09-05 the consent evidence itself is
  never destroyed.

**Consent records are never destroyed.** No retention period, no disposal.

**Duplicate submissions are ours to prevent.** Implemented: if a submission is
sent and no reply arrives — a read timeout, a dropped response, or an HTTP 5xx —
the application is marked outcome-unknown and **will not be sent again** until
an operator has checked your console and recorded which way it went. A connect
failure is treated as safe to retry, because the request never left us.

> **One request.** Because you expose no lookup for "does an application already
> exist for this customer", that reconciliation is manual. If you can offer such
> a lookup, or de-duplicate on an idempotency key we supply, we would replace the
> manual step immediately.

## 7. Worked example

A real record, taken from our system:

```json
{
  "consentRef":        "CBC-35-v3-2026-09",
  "consentGiven":      true,
  "consentDate":       "2026-09-05T07:45:37.122303Z",
  "textVersion":       "v3-2026-09",
  "textHash":          "035f3ea038f727a7245253e9a2fb6167278ce5e4a1624f96b0a61293fcb4dabc",
  "textLanguage":      "km",
  "channel":           "MOBILE_APP",
  "formReference":     "Form _ CBC 01 _ CBC Consent Form For Borrowers _ V2 _ 09122025",
  "idNo":              "110553867",
  "customerNameKm":    "សែត មករា",
  "documentHash":      "82e41a7f15c8549bf30555ffede0458e51bad13de48f7804574d4ae1e788f452",
  "appVersion":        "2.0.1+45",
  "deviceModel":       "iPhone14,3 / iOS 26.6.1",
  "captureSource":     "SUBMIT"
}
```

## 8. What we propose sending you

We are not asking you to store all twenty-nine. The nine below are the ones that
make your copy independently auditable; the rest are ours to keep.

`ECBCConsent_Ref`, `_Given`, `_DateTime`, `_TextVersion`, `_TextHash`,
`_Language`, `_Channel`, `_IdNo`, `_CustomerName` — named in your flat
PascalCase style to sit beside `Doc_ECBCConsentForm`. **Rename freely**; what
matters is that each exists somewhere, since each answers a question the others
cannot.

`_DateTime` is sent as **UTC with `Z`**, agreed 2026-09-07 — see the note in §3
for why that particular field is not a free choice.

`_TextHash` is the one to keep if you keep only one. It is what turns "they
consented to version 3" into something provable.

## 9. Still open with you

1. **Confirm the field names and types**, or send yours and we will map to them.
2. **Where do they go** — extra fields on `newAppRequest`, or a separate call?
3. **`ECBCConsent_Given`** — worth keeping even though we only ever file when
   consent was given; a record that relies on absence to mean "no" is hard to
   audit.
4. **A duplicate-submission lookup or idempotency key**, per section 6.

Nothing is blocked meanwhile: we send the PDF today, every field above is
already produced and stored on our side, and wiring the subset into your payload
is a small change once the names are fixed.
