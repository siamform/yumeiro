#!/usr/bin/env bash
# Send a one-time value (Telegram code or 2-step password) to a running *sealed* field probe without it ever being
# readable in the public repo: reads the value from stdin, encrypts it with PROBE_SEAL_KEY from the ignored
# secrets/yumeiro.env.local, and dispatches field-probe.yml with steps "code <blob>" (the run title carries the blob).
# Usage: printf '%s' 12345 | scripts/probe-send-secret.sh c      (p for the password). Never echo the value itself.
set -euo pipefail
k=${1:?kind c or p}; cd "$(dirname "$0")/.."
PROBE_SEAL_KEY=$(sed -n 's/^PROBE_SEAL_KEY=//p' secrets/yumeiro.env.local); export PROBE_SEAL_KEY
# PROBE-SEAL-3: an empty or missing key used to encrypt with an empty pass, which the runner can never open.
[ -n "$PROBE_SEAL_KEY" ] && [ "$(printf '%s\n' "$PROBE_SEAL_KEY" | wc -l)" = 1 ] || { echo "PROBE_SEAL_KEY missing or duplicated in secrets/yumeiro.env.local" >&2; exit 1; }
v=$(cat); b=$(printf '%s:%s' "$k" "$v" | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:PROBE_SEAL_KEY | base64 -w0)
gh workflow run field-probe.yml --ref "$(git rev-parse --abbrev-ref HEAD)" -R siamform/yumeiro -f steps="code $b" > /dev/null
echo "sent ($k)"
