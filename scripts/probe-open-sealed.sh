#!/usr/bin/env bash
# Decrypt a sealed field-probe artifact: scripts/probe-open-sealed.sh <run id> <dir>. Keep the output out of the repo.
set -euo pipefail
cd "$(dirname "$0")/.."; r=${1:?run id}; d=${2:?out dir}
PROBE_SEAL_KEY=$(sed -n 's/^PROBE_SEAL_KEY=//p' secrets/yumeiro.env.local); export PROBE_SEAL_KEY
mkdir -p "$d"; gh run download "$r" -R siamform/yumeiro -n field-probe -D "$d/enc"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:PROBE_SEAL_KEY -in "$d/enc/probe.tgz.enc" | tar xzf - -C "$d"; rm -rf "$d/enc"; ls "$d/probe" | head
