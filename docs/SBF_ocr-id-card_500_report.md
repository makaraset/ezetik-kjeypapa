> **RESOLVED 2026-08-27.** Sambat fixed a certificate issue on their side.
> `POST /ocr-id-card` now returns HTTP 200 with full card data in ~3s, verified
> against a real NID; we run it live (`ocr.mock.enabled=false`). Kept for the record.

# `POST /api/ocr-id-card` returns HTTP 500 on every request (tricube UAT)

**Environment:** `https://tricube-uat.sambatfinance.com:6443/api`
**Reported by:** Kjey PAPA app team · 2026-08-27
**Status:** blocking the sign-up NID auto-fill (we have the feature built and flag-gated)

## Summary
Every call to `/ocr-id-card` fails with a generic HTTP 500 in ~80–130 ms, for **any**
payload. We can rule out the request itself: image size, image format, base64 encoding
style and JSON field name make no difference, and the failure is far too fast for any
image decoding or OCR to have been attempted.

## Exact request
```
POST https://tricube-uat.sambatfinance.com:6443/api/ocr-id-card
Authorization: Bearer <token from :4443/oauth/token, scopes: app accountinfo read write register repayment>
Content-Type: application/json

{"idImage":"<base64 of the NID front>"}
```
Response (always):
```
HTTP 500  {"timestamp":1787795639608,"status":500,"error":"Internal Server Error","path":"/api/ocr-id-card"}
```

## Variants tried — all identical 500
| Variant | Result |
|---|---|
| original JPEG (229 KB base64) | 500 |
| JPEG downscaled to 1200 px / 640 px | 500 |
| `data:image/jpeg;base64,` prefix | 500 |
| PNG instead of JPEG | 500 |
| tiny dummy payload `{"idImage":"aGk="}` | 500 |
| field names `image` / `imageBase64` / `idimage` / `idImageBase64` / `file` | 500 |

## The failure is inside the handler, not around it
| Probe | Result | Interpretation |
|---|---|---|
| malformed JSON | **400** | request body parsing works |
| empty body | **400** | validation works |
| form-encoded body | **415** | content-type negotiation works |
| `GET` on the route | **405** | routing works |
| no `Authorization` header | **401** | security works |
| non-existent path | **404** | global error handling works |
| `GET /saving-info-by-cid?cifNo=62581` (control) | **200** | our token and connectivity are fine |
| **valid JSON body** | **500** | **throws once it reaches the controller method** |

Conclusion: a valid, authenticated, well-formed request reaches the controller and an
unhandled exception is thrown immediately — consistent with an unconfigured or null
CamDigi dependency (missing credentials / endpoint property) rather than bad input.

## Same symptom across the whole CamDigi group (18)
`/verified-info` and `/gernerate-token` also return identical immediate 500s on an empty
body, which suggests the module as a whole is unprovisioned in this environment rather
than one broken endpoint.

## What would unblock us
1. The **stack trace** from your server log — the 500 body hides it. Timestamps to grep:
   `1787795639608`, `1787795611691`, `1787795612286`.
2. Confirmation of **which host/environment** your successful base64 test was run against —
   if it was not `tricube-uat...:6443`, please share the correct base URL.
3. Whether `/ocr-id-card` requires any **precondition we are missing** (e.g. a CamDigi
   token obtained from `/gernerate-token`, or an extra header).

## Unrelated bug found in the same environment
`GET /group-facilities/by-cid?custKeyNum=70` → **HTTP 500**. CIF 70 exists
(`/customer-information/by-idno?idNo=110553867` returns it) but has no credit facility;
the handler appears not to tolerate that case. This is the endpoint the TFF facility
screen depends on.
