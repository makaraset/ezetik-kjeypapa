#!/bin/sh
# Markdown -> .docx for the documents we send Sambat.
#
# Two things here are not obvious and both were learned the hard way:
#
#  1. KHMER. Khmer is a COMPLEX SCRIPT, so Word takes its font from w:cs, not
#     from the ascii font. Pandoc's stock reference.docx leaves that on the
#     theme, which resolves to a Latin face with no Khmer glyphs — customer
#     names then render as boxes. This pins w:cs to Khmer OS Content, the same
#     family Sambat's own consent form uses.
#
#  2. NO --toc. Pandoc writes the table of contents as a field the reader has
#     to refresh, so the recipient opens the file and sees an empty heading.
#     Our documents are short and their sections are numbered; a contents page
#     is not worth looking broken for.
#
# Usage: docs/make-docx.sh docs/CBC_Consent_Record_Spec.md "Document title"
set -e
SRC="$1"
TITLE="$2"
OUT="${SRC%.md}.docx"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pandoc --print-default-data-file reference.docx > "$WORK/ref.docx"
(cd "$WORK" && unzip -qo ref.docx -d unpacked)
python3 - "$WORK" <<'PY'
import io, sys
p = sys.argv[1] + '/unpacked/word/styles.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('w:cstheme="minorBidi"', 'w:cs="Khmer OS Content"')
s = s.replace('w:cstheme="majorBidi"', 'w:cs="Khmer OS Content"')
io.open(p, 'w', encoding='utf-8').write(s)
PY
(cd "$WORK/unpacked" && zip -qr ../ref-km.docx . -x '.*')

# Drop the H1: --metadata title already renders it, and both together print
# the title twice.
tail -n +2 "$SRC" > "$WORK/body.md"

pandoc "$WORK/body.md" -o "$OUT" \
  --reference-doc="$WORK/ref-km.docx" \
  --metadata title="$TITLE" \
  --metadata author="Kjey PAPA (Ezetik) · for Sambat Finance / Tricube LOS" \
  --metadata date="$(date +%Y-%m-%d)"

echo "wrote $OUT"
