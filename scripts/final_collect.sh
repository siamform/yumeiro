#!/usr/bin/env bash
# Final test: copy the REPEAT_RESULT lines of a device-check run (passes: true) into docs/evidence/final/<run>.txt
# as "<run> <device> <class> <P1|P2> <PASS|FAIL|SKIPPED> tests=N skipped=M". Usage: final_collect.sh <run-id>
set -euo pipefail
R="$1"; OUT="docs/evidence/final/$R.txt"; mkdir -p docs/evidence/final; : > "$OUT"
for J in $(gh api "repos/siamform/yumeiro/actions/runs/$R/jobs" -q '.jobs[]|select(.name|startswith("emulator"))|"\(.id)"'); do
  NAME=$(gh api "repos/siamform/yumeiro/actions/jobs/$J" -q .name)
  DEV=$(echo "$NAME" | sed -E 's/^emulator \([0-9]+, ([^,]+),.*/\1/')
  gh api "repos/siamform/yumeiro/actions/jobs/$J/logs" 2>/dev/null | sed -n 's/.*REPEAT_RESULT dev\.asfakul\.yumeiro\.\([A-Za-z0-9]*\) run=[0-9]*\/[0-9]* pass=\(P[12]\) \(PASS\|FAIL\) tests=\([0-9?]*\) skipped=\([0-9]*\).*/\1 \2 \3 \4 \5/p' | sort -u |
  while read -r C P V T S; do
    if [ "$V" = PASS ] && [ "$T" != "?" ] && [ "$S" -gt 0 ] && [ "$S" -ge "$T" ]; then V=SKIPPED; fi
    echo "$R $DEV $C $P $V tests=$T skipped=$S" >> "$OUT"
  done
done
wc -l "$OUT"
