# e-CBC consent — PDF + record

**For:** Sambat Finance / Tricube LOS
**From:** Kjey PAPA (Ezetik)
**Date:** 2026-09-04
**Subject:** the structured consent record to add alongside `Doc_ECBCConsentForm`

You asked for the CBC consent as **both a PDF and a record**, and said you will
add fields on your side to match ours. This is that specification.

---

## 1. Why a record as well as the PDF

The PDF is what a person reads; the record is what a system can check. A PDF
alone cannot answer "was consent given, when, to exactly which wording?"
without someone opening it and reading. If a customer later disputes a CBC
enquiry, the record answers it in one query, and the hash proves the wording
was not edited afterwards.

Both describe the same event. Neither replaces the other.

## 2. What we already send

| Field | Value |
|---|---|
| `Doc_ECBCConsentForm` | base64 PDF, A4, one page, **Khmer** (per your 2026-09-03 request) |
| `Doc_ECBCConsentForm_FileName` | `ECBCConsentForm-<loanId>.pdf` |

## 3. The record — proposed fields

Named in your existing flat PascalCase style, prefixed `ECBCConsent_` to sit
beside `Doc_ECBCConsentForm`. **Rename freely** — the important part is that
every row below exists somewhere, since each answers a question the others
cannot.

| # | Field | Type | Example | Why it is needed |
|---|---|---|---|---|
| 1 | `ECBCConsent_Ref` | string(64) | `CBC-24-v1-2026-08` | Our immutable reference. Ties record ↔ PDF ↔ our database in support calls. Unique per application. |
| 2 | `ECBCConsent_Given` | boolean | `true` | States consent explicitly. A record whose presence *implies* consent is not evidence; an auditor asks to see the flag. |
| 3 | `ECBCConsent_DateTime` | string, ISO-8601 **with offset** | `2026-09-04T08:41:12+07:00` | When. The offset matters — a bare timestamp read as UTC moves a Cambodian consent seven hours and can land it on the wrong day. |
| 4 | `ECBCConsent_TextVersion` | string(32) | `v1-2026-08` | *Which* wording. Lets us reissue text without invalidating old consents. |
| 5 | `ECBCConsent_TextHash` | string(64), SHA-256 lower-case hex | `9f2c…` | *Proves* the wording. A version label can be reused or its stored text edited; the hash cannot. This is the field that makes the record stand up in a dispute. |
| 6 | `ECBCConsent_Language` | string(8) | `km` | Which language the customer actually read. You asked for Khmer; the record should say so rather than assume. |
| 7 | `ECBCConsent_Channel` | string(32) | `MOBILE_APP` | How it was captured — an in-app tick box, not a wet signature. Distinguishes it from a branch-signed form. |
| 8 | `ECBCConsent_IdNo` | string(32) | `110553867` | Identifies the subject. Makes the record readable standalone, without joining back to the application. |
| 9 | `ECBCConsent_CustomerName` | string(255) | `សែត មករា` | The name as printed on the form the customer saw. |

### Optional, if you want a fuller audit trail

| Field | Type | Can we supply it? |
|---|---|---|
| `ECBCConsent_AppVersion` | string(32) | Yes — the app build that displayed the consent. |
| `ECBCConsent_DeviceId` | string(64) | Possible, but it is personal data with no lending purpose. We would rather not send it unless you need it. |
| `ECBCConsent_IpAddress` | string(45) | **Not today.** We do not record an IP at consent time. Say the word and we will add it. |

## 4. How we produce each value

- `Ref`, `DateTime`, `TextVersion`, `TextHash`, `Language`, `Channel` are
  stamped **once, at the moment the application is filed** — never earlier and
  never on a retry. An earlier version of ours stamped consent *before* the
  call, which left a recorded consent behind for submissions that never
  happened; that is fixed and the stamp is now proof the application went.
- `TextHash` is `SHA-256(UTF-8 bytes of the exact Khmer wording shown)`,
  lower-case hex. The same string that is rendered into the PDF is the string
  that is hashed, so the two can never disagree.
- `IdNo` and `CustomerName` come from the verified KYC record, not from
  anything the app re-sends at submit time.

## 5. Worked example

```json
{
  "ECBCConsent_Ref":          "CBC-24-v1-2026-08",
  "ECBCConsent_Given":        true,
  "ECBCConsent_DateTime":     "2026-09-04T08:41:12+07:00",
  "ECBCConsent_TextVersion":  "v1-2026-08",
  "ECBCConsent_TextHash":     "3b7c1f…64 hex chars…9ade",
  "ECBCConsent_Language":     "km",
  "ECBCConsent_Channel":      "MOBILE_APP",
  "ECBCConsent_IdNo":         "110553867",
  "ECBCConsent_CustomerName": "សែត មករា"
}
```

## 6. What we need back from you

1. **Confirm the field names and types**, or send yours and we will map to them.
2. **Where do they go** — extra fields on `newAppRequest` (simplest for us,
   consistent with the rest of the application), or a separate endpoint?
3. **`ECBCConsent_Given`** — do you want the field at all, given we only ever
   file an application when consent was given? We recommend keeping it: a
   record that relies on absence to mean "no" is hard to audit.
4. **The final Khmer + English legal text.** Ours is an interim translation
   pending your wording. When you send it we will replace the text and bump
   `TextVersion`, so consents stay attributable to the exact wording each
   customer saw — previously filed consents keep pointing at the old version
   and hash, which is the point of having both.

Until then nothing is blocked: we send the PDF today, and the record fields are
already produced on our side and served on our own consent endpoint, so wiring
them into your payload is a one-line change once the field names are fixed.
