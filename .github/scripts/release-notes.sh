#!/usr/bin/env bash
# Generates Markdown release notes from the commits since the previous tag, grouped by
# Conventional Commit type. Usage: release-notes.sh <version_name> <version_code> <previous_tag> <apk_name> <sha256>
set -euo pipefail

version="$1"; code="$2"; prev="${3:-}"; apk="$4"; sha="$5"
repo="${GITHUB_REPOSITORY:-siamform/yumeiro}"
server="${GITHUB_SERVER_URL:-https://github.com}"

range="HEAD"; [ -n "$prev" ] && range="$prev..HEAD"

declare -A title=(
  [feat]="New features" [fix]="Bug fixes" [perf]="Performance" [refactor]="Improvements"
  [style]="Improvements" [ui]="Improvements" [docs]="Documentation" [test]="Tests"
  [build]="Build & CI" [ci]="Build & CI" [chore]="Maintenance" [other]="Other changes"
)
order=("New features" "Bug fixes" "Performance" "Improvements" "Documentation" "Tests" "Build & CI" "Maintenance" "Other changes")
declare -A bucket

while IFS=$'\x1f' read -r subject short author; do
  [ -z "$subject" ] && continue
  case "$subject" in "chore(release):"*|*"[skip ci]"*) continue ;; esac
  type="$(echo "$subject" | sed -nE 's/^([a-z]+)(\([^)]*\))?!?:.*/\1/p')"
  msg="$(echo "$subject" | sed -E 's/^[a-z]+(\(([^)]*)\))?!?:[[:space:]]*/\2: /; s/^: //')"
  [ -z "$type" ] && { type=other; msg="$subject"; }
  section="${title[$type]:-Other changes}"
  bucket[$section]+="- ${msg^} ([\`$short\`]($server/$repo/commit/$short)) — $author"$'\n'
done < <(git log "$range" --no-merges --format="%s%x1f%h%x1f%an")

echo "## Yumeiro v$version"
echo
for s in "${order[@]}"; do
  if [ -n "${bucket[$s]:-}" ]; then
    echo "### $s"
    printf '%s\n' "${bucket[$s]}"
  fi
done
[ ${#bucket[@]} -eq 0 ] && { echo "- Maintenance release"; echo; }

echo "---"
echo "**Install:** \`$apk\` works on every device (phone, tablet, Android TV, Fire TV). Smaller downloads: \`*-arm64-v8a.apk\` for most phones and TVs from 2017 on, \`*-armeabi-v7a.apk\` for older 32-bit devices and many TV sticks. Android 7.0 (API 24) or newer is required. In-app updates pick the right one automatically."
echo
echo "**SHA-256 (universal):** \`$sha\` · all files: \`SHA256SUMS.txt\`"
echo
if [ -n "$prev" ]; then
  echo "**Full Changelog**: $server/$repo/compare/$prev...v$version"
fi
echo
echo "<!-- versionCode: $code -->"
