#!/usr/bin/env bash
# §9 checks on the review APK artifact of a CI run (FINAL-SEC).
# Usage: scripts/final_apk_check.sh <run-id> [out-file]
# Reads secret VALUES from the git-ignored .env / secrets/ at runtime and only
# prints "found"/"not found" — never the values themselves.
set -euo pipefail
run=$1; out=${2:-docs/evidence/final/apk-check-$run.txt}
root=$(cd "$(dirname "$0")/.." && pwd)
dir=/tmp/apk-$run; rm -rf "$dir"; mkdir -p "$dir"
gh run download "$run" -R siamform/yumeiro -n yumeiro-review-apk -D "$dir/art" >/dev/null
{
  echo "# APK checks for run $run ($(date -u +%FT%TZ))"
  (cd "$dir/art" && ls -1 && echo "-- sha256sum -c SHA256SUMS.txt" && sha256sum -c SHA256SUMS.txt)
  for apk in "$dir"/art/*.apk; do
    name=$(basename "$apk"); x="$dir/x-$name"; mkdir -p "$x"; unzip -qo "$apk" -d "$x"
    echo "== $name"
    check() { # label value
      local label=$1 val=$2
      if [ -z "$val" ]; then echo "  $label: empty in .env (not shipped)"; return; fi
      if grep -rqaF -- "$val" "$x" || grep -qaF -- "$val" "$apk"; then echo "  $label: FOUND"; else echo "  $label: not found"; fi
    }
    getenv() { grep -E "^$1=" "$root/.env" | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
    check LASTFM_SHARED_SECRET "$(getenv LASTFM_SHARED_SECRET)"
    check TRAKT_CLIENT_SECRET "$(getenv TRAKT_CLIENT_SECRET)"
    check CLOUDFLARE_API_TOKEN "$(getenv CLOUDFLARE_API_TOKEN)"
    fb="$root/secrets/firebase-admin.json"
    if [ -f "$fb" ]; then
      check firebase_private_key_id "$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["private_key_id"])' "$fb")"
      check firebase_private_key_body "$(python3 -c 'import json,sys;k=json.load(open(sys.argv[1]))["private_key"].splitlines();print(k[2] if len(k)>2 else "")' "$fb")"
    fi
    if grep -rqa -- "BEGIN PRIVATE KEY" "$x"; then echo "  PEM private key: FOUND"; else echo "  PEM private key: not found"; fi
  done
  echo "-- clamscan $(clamscan --version)"
  clamscan -r --scan-archive=yes --infected --no-summary "$dir/art" && echo "  clamscan: 0 infected" || echo "  clamscan: exit $? (see above)"
} 2>&1 | tee "$out"
