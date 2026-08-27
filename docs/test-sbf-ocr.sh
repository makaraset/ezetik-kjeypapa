#!/bin/bash
# Ad-hoc probe for SBF's NID OCR endpoint (tricube UAT).
# Reads credentials from src/main/resources/application*.properties — no secrets inline.
# Usage:  ./docs/test-sbf-ocr.sh /path/to/NID.jpg
set -euo pipefail
IMG="${1:?usage: test-sbf-ocr.sh <nid-image>}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -h "^$1=" "$DIR"/src/main/resources/application.properties \
                        "$DIR"/src/main/resources/application-local.properties 2>/dev/null | tail -1 | cut -d= -f2-; }
TOKEN_EP="$(prop token_endpoint)"; BODY="$(prop urlencoded_token)"
AUTH="$(prop authorization)";      API="$(prop url_api)"

echo "1) token  <- $TOKEN_EP"
AT=$(curl -sk -m 20 -X POST "$TOKEN_EP" -H 'Content-Type: application/x-www-form-urlencoded' \
      -H "Authorization: $AUTH" -d "$BODY" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
echo "   ok (${#AT} chars)"

echo "2) sanity <- GET /customer-information/by-idno   (proves the token works)"
curl -sk -m 20 -o /dev/null -w "   HTTP %{http_code}\n" \
     "$API/customer-information/by-idno?idNo=110553867&page=0&size=1" -H "Authorization: Bearer $AT"

echo "3) OCR    <- POST /ocr-id-card   ($(basename "$IMG"))"
python3 - "$IMG" "$API" "$AT" <<'PY'
import sys, json, base64, ssl, time, urllib.request
img_path, api, at = sys.argv[1], sys.argv[2], sys.argv[3]
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
body = json.dumps({"idImage": base64.b64encode(open(img_path, "rb").read()).decode()}).encode()
req = urllib.request.Request(api + "/ocr-id-card", data=body, method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + at})
t0 = time.time()
try:
    r = urllib.request.urlopen(req, context=ctx, timeout=90)
    print(f"   HTTP {r.status} ({time.time()-t0:.2f}s)")
    print("  ", json.dumps(json.load(r), ensure_ascii=False, indent=2)[:1200])
except urllib.error.HTTPError as e:
    print(f"   HTTP {e.code} ({time.time()-t0:.2f}s)  <-- still broken on SBF's side")
    print("  ", e.read()[:300].decode(errors="replace"))
PY
