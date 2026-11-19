#!/usr/bin/env bash
# Yumeiro — Copyright (C) 2026 siamform
# Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
#
# F-AIR Worker deploy (ADR-043). Dry run by default: it only reads production and prints the plan.
# Nothing is changed in production unless `--go` is given, and `--go` is only used after the
# owner's explicit go for 1.9.0 (merge + publish + Worker deploy are one decision).
#
#   scripts/deploy-worker.sh          # read-only: shows which migrations production still needs
#   scripts/deploy-worker.sh --go     # applies the missing migrations in order, then deploys
#
# Credentials come only from the git-ignored .env (CLOUDFLARE_API_TOKEN). Never pass them on the
# command line, never echo them. Needs Node >= 22.12 (server/package.json engines).
set -euo pipefail
cd "$(dirname "$0")/.."
GO=0; [[ "${1:-}" == "--go" ]] && GO=1

[[ -f .env ]] || { echo "No .env (CLOUDFLARE_API_TOKEN). Stop." >&2; exit 2; }
set -a; . ./.env; set +a
[[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || { echo "CLOUDFLARE_API_TOKEN is empty. Stop." >&2; exit 2; }
DB_ID=$(sed -n 's/^database_id = "\([^"]*\)".*/\1/p' server/wrangler.toml)
API=https://api.cloudflare.com/client/v4
AUTH="Authorization: Bearer $CLOUDFLARE_API_TOKEN"
ACCOUNT=$(curl -sf "$API/accounts" -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["result"][0]["id"])')

d1() { # read-only helper: prints the result rows as JSON
  curl -sf -X POST "$API/accounts/$ACCOUNT/d1/database/$DB_ID/query" -H "$AUTH" -H 'content-type: application/json' \
    -d "$(python3 -c 'import json,sys;print(json.dumps({"sql":sys.argv[1]}))' "$1")" |
    python3 -c 'import sys,json;print(json.dumps(json.load(sys.stdin)["result"][0]["results"]))'
}
has_table()  { d1 "SELECT name FROM sqlite_master WHERE type='table' AND name='$1'" | grep -q "\"$1\""; }
has_column() { d1 "PRAGMA table_info($1)" | python3 -c "import sys,json;sys.exit(0 if any(r['name']=='$2' for r in json.load(sys.stdin)) else 1)"; }

# Each migration is detected by what it creates, so a re-run never applies an ALTER twice.
need=()
has_table airing_pending   || need+=(0003_abuse_guards.sql)
has_column fcm_devices want || need+=(0004_fcm_want.sql)
has_column fcm_devices follows_v || need+=(0005_fcm_follows.sql)

echo "Production D1: $(d1 'SELECT (SELECT count(*) FROM fcm_devices) fcm, (SELECT count(*) FROM devices) up, (SELECT count(*) FROM follows) follows, (SELECT count(*) FROM airing) airing')"
echo "Migrations still needed (in order): ${need[*]:-none}"
echo "Worker secrets present: $(curl -sf "$API/accounts/$ACCOUNT/workers/scripts/yumeiro-cloud/secrets" -H "$AUTH" | python3 -c 'import sys,json;print(sorted(s["name"] for s in json.load(sys.stdin)["result"]))')"

if [[ $GO -eq 0 ]]; then
  echo "Dry run only. Re-run with --go after the owner's go. Plan: migrations above, then 'wrangler deploy'."
  exit 0
fi

export CLOUDFLARE_ACCOUNT_ID="$ACCOUNT"
cd server
npm ci --silent
NODE_OPTIONS=--experimental-sqlite npm test    # the deploy stops if the Worker tests fail
npm run -s typecheck
for m in "${need[@]}"; do
  echo "Applying $m"
  npx wrangler d1 execute yumeiro --remote --file="migrations/$m"
done
npx wrangler deploy
echo "Deployed. Watch: npx wrangler tail yumeiro-cloud --format pretty (look for 'wake' queue sends after the next */10 cron)."
