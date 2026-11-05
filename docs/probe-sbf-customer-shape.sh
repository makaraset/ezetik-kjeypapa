#!/bin/bash
# Shows the FORM of Sambat's customer record for one ID number, never its
# values: digits print as 9, Latin letters as a, Khmer letters as ក. Only
# regisType (an ID-type code, not personal data) prints as it is.
#
# The CIF check (SbfCustomerMatch, backlog F27) must read the forms Sambat use
# for dob, sex and the names. This shows them without copying anyone's data.
#
# Reads credentials from src/main/resources/application*.properties — no
# secrets inline, none printed. One token request: never loop it (lockout).
# Usage, on the SBF VPN:  ./docs/probe-sbf-customer-shape.sh <your own ID number>
set -euo pipefail
IDNO="${1:?usage: probe-sbf-customer-shape.sh <id-number>}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -h "^$1=" "$DIR"/src/main/resources/application.properties \
                        "$DIR"/src/main/resources/application-local.properties 2>/dev/null | tail -1 | cut -d= -f2-; }
TOKEN_EP="$(prop token_endpoint)"; BODY="$(prop urlencoded_token)"
AUTH="$(prop authorization)";      API="$(prop url_api)"

AT=$(curl -sk -m 20 -X POST "$TOKEN_EP" -H 'Content-Type: application/x-www-form-urlencoded' \
      -H "Authorization: $AUTH" -d "$BODY" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

curl -sk -m 20 -G "$API/customer-information/by-idno" --data-urlencode "idNo=$IDNO" \
     --data "page=0&size=1" -H "Authorization: Bearer $AT" | python3 -c '
import sys, json, re
page = json.load(sys.stdin)
rows = page.get("content") or []
print("records:", len(rows), "  totalElements:", page.get("totalElements"))
def form(v):
    if v is None:
        return "null"
    if isinstance(v, str):
        s = re.sub(r"[0-9]", "9", v)
        s = re.sub(r"[A-Za-z]", "a", s)
        return "text  " + repr(re.sub(r"[ក-៿᧠-᧿]", "ក", s))
    return type(v).__name__ + "  " + re.sub(r"[0-9]", "9", json.dumps(v))
for k in ("custKeyNum", "idNo", "regisType", "dob", "sex",
          "name", "fname", "lname", "khName", "familyNameKh", "firstNameKh"):
    if rows:
        v = rows[0].get(k)
        shown = json.dumps(v) if k == "regisType" else form(v)
        print(f"{k:13} {shown}")
'
