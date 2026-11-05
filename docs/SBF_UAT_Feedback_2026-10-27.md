# SBF UAT feedback, 2026-10-27 — what we found, what changed, what we need

| | |
|---|---|
| **For** | Sambat Finance — UAT testers and the CamDigi / LOS integration team |
| **From** | Kjey PAPA (Ezetik) |
| **Date** | 2026-10-27 |
| **Re** | Your three items: CamDigi liveness, address country, work status |

Thank you for testing. Each of your three items turned out to be slightly
different from how it first looked, so this note explains what we found as
well as what we changed.

Everything below ships together in the next UAT release: the backend, plus app
build **1.2.6 (18)** on TestFlight and the Play internal track. Until that is
installed, testers will still see the old behaviour.

---

## 1. "Customer addresses are missing country"

**What we found.** The loan application you receive was never short of it:
every country field you get — correspondence, permanent, place of birth and
work — already carries `KHM`, and so does nationality. What was missing was in
our app. The sign-up screen saved the country for place of birth and work, but
not for the two home addresses, so My Profile showed "Country: -" for both,
right beside a work address that said "Cambodia". The sign-up address pickers
also had no Country level, although your V8 design starts each address with one.

**What we changed.**
- Every address picker now starts with a fixed **Country: Cambodia** (កម្ពុជា).
  It is shown, not selectable: your gazetteer is Cambodia-only, so no other
  country could resolve to a province code.
- The server fills the country on every save, so no app version can leave it
  blank again. Existing accounts are repaired by a one-time data fix.
- My Profile shows the country in Khmer when the app is in Khmer.
- The LPO review screen now shows the applicant's address at all — before this,
  it showed none.
- Every address level (province, district, commune, village) is now required
  on the correspondence, permanent and work addresses, since your Appendix 2
  marks all four codes Required.

## 2. "Work status is required"

**What we found.** In Khmer, ស្ថានភាពការងារ is our **Employment Status** field
(Permanent / Probation / Contract / Part-time / Other). It had a bug: it
*displayed* "Permanent / Confirmed" without saving it unless the customer
changed it, so every account your testers created went in with a blank status.
It was also not shown when confirming details before a loan request, nor on the
LPO review screen.

**What we changed.**
- The field starts with no choice made, and must be chosen before continuing.
- The server refuses a blank or unknown status.
- It now appears when confirming details before a loan request, and on the LPO
  review screen.
- Customers whose status is blank today are asked to complete it the next time
  they request a loan.

Two related fixes, on the same screen and from the same Appendix 2 rule:
- **Employment start date** is now required (Appendix 2 #45).
- **Employment type is fixed to Employee**, as on V8 screen 7. Choosing
  Self-employed used to produce a loan application with status `1` beside type
  `E` and income type `S`, which contradict each other.

## 3. CamDigi NID image + liveness verification

**What we built.** The complete flow, switched off until the questions below
are answered:
- After the customer verifies their phone (OTP), the app asks them to do a
  short face check on the front camera.
- Our server sends CamDigi the **NID image and identity details the customer
  already submitted**, with the capture. So CamDigi checks exactly what the LPO
  will approve, and the check cannot be used to test someone else's identity.
- A customer who closes the app can finish later by signing in.
- The LPO review screen shows the result, and approval can be made to wait for
  a pass.
- Each attempt is recorded. The capture itself is not stored.

**Why it is off.** Your swagger describes `liveness` and `actions` only as
"string", and we would rather ask than guess. A wrong guess would fail every
customer. We also have no record that the CamDigi endpoints work on UAT: on
2026-08-27, `/verified-info` and `/gernerate-token` answered HTTP 500 to an
empty request, and only `/ocr-id-card` was confirmed fixed afterwards.

---

## What we need from you

### CamDigi

1. **`liveness`** — what should it contain? A base64 video (which format, how
   long, what maximum size)? A single photo? Several photos? Or a package from
   a vendor SDK that we must embed in the app?
2. **`actions`** — what values are allowed (for example `BLINK`, `TURN_LEFT`),
   in what order, and do we choose them, or does an SDK?
3. **`userInfo`** — should it carry the values your OCR read from the card, or
   the values the customer confirmed? Which date format (`dd/MM/yyyy`?), which
   gender format (`M`/`F`?), and which fields are required?
4. **The reply** — what does `data` contain, and what do the `error` codes mean?
   In particular, can we tell "the details do not match the registry" apart
   from "the face does not match" or "not a live person"? A customer can fix
   the first by correcting their details, but not by trying again.
5. Is `doneBy = "KjeyPapa"` correct for CamDigi, as for the loan application?
6. Typical response time, size limit, whether each call is charged, and how
   often a customer may retry.
7. Should the capture be kept? If so, by whom and for how long? It is biometric
   data.
8. When a customer fails, or runs out of attempts, should they be blocked, or
   reach the LPO marked as not verified?
9. Is `/verified-info-idimage-liveness` working on UAT today?

### Loan application

10. **`CustP_CBEmploymentStatus`** — on 2026-08-31 you told us it carries the
    employment *type* (1 self-employed, 2 employee, 3 other), and that is what
    we send. Appendix 2 lists it as "Employment Status code" and requires it
    for Employee with contract type UDC. Should it instead carry the customer's
    Permanent / Probation / Contract answer? If so, what are the codes?
11. **Nationality** — can a non-Cambodian apply for a payday loan? Today the
    app offers "Other", but every application is filed as `KHM`. If only
    Cambodians can apply, we will remove the option.

### To confirm our reading

12. Where did your testers see "missing country" and "work status is
    required": on which screen of our app, or on which application (AppId) in
    your LOS? We believe it was My Profile and the employment screen. If it was
    your LOS screen, please send the AppId and we will compare it with what we
    sent.
