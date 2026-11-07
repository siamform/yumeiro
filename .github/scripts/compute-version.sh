#!/usr/bin/env bash
# Computes the next app version for a release and writes it to version.properties.
#
# Rules
#   * If the developer already bumped VERSION_NAME above the latest tag, that version is used.
#   * Otherwise the version is bumped automatically from the latest tag using Conventional Commits
#     found since that tag:  BREAKING CHANGE / "type!:" -> major,  "feat:" -> minor,  else -> patch.
#     A manual bump type (major|minor|patch) can be forced with $BUMP_OVERRIDE.
#   * VERSION_CODE always strictly increases:  max(file code, last released code + 1).
#
# Outputs (also appended to $GITHUB_OUTPUT when available):
#   version_name, version_code, tag, previous_tag, changed (true when version.properties changed)
set -euo pipefail

PROPS="version.properties"
BUMP_OVERRIDE="${BUMP_OVERRIDE:-auto}"

prop() { grep -E "^$1=" "$2" | head -1 | cut -d= -f2 | tr -d '[:space:]'; }
semver_gt() { [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ]; }

file_name="$(prop VERSION_NAME "$PROPS")"
file_code="$(prop VERSION_CODE "$PROPS")"

git fetch --tags --force --quiet 2>/dev/null || true
prev_tag="$(git tag --list 'v[0-9]*' --sort=-v:refname | head -1 || true)"

if [ -z "$prev_tag" ]; then
  new_name="$file_name"
  new_code="$file_code"
else
  tag_name="${prev_tag#v}"
  tag_code="$(git show "$prev_tag:$PROPS" 2>/dev/null | grep -E '^VERSION_CODE=' | cut -d= -f2 | tr -d '[:space:]' || true)"
  tag_code="${tag_code:-0}"

  if semver_gt "$file_name" "$tag_name"; then
    new_name="$file_name"            # developer bumped it manually
  else
    bump="$BUMP_OVERRIDE"
    if [ "$bump" = "auto" ]; then
      log="$(git log "$prev_tag"..HEAD --no-merges --format='%s%n%b' || true)"
      if echo "$log" | grep -qE '^[a-z]+(\([^)]*\))?!:|BREAKING[ -]CHANGE'; then bump=major
      elif echo "$log" | grep -qE '^feat(\([^)]*\))?:'; then bump=minor
      else bump=patch; fi
    fi
    IFS=. read -r ma mi pa <<<"$tag_name"
    ma=${ma:-0}; mi=${mi:-0}; pa=${pa:-0}
    case "$bump" in
      major) new_name="$((ma + 1)).0.0" ;;
      minor) new_name="$ma.$((mi + 1)).0" ;;
      *)     new_name="$ma.$mi.$((pa + 1))" ;;
    esac
  fi

  new_code=$(( file_code > tag_code ? file_code : tag_code + 1 ))
  [ "$new_code" -le "$tag_code" ] && new_code=$((tag_code + 1))
fi

changed=false
if [ "$new_name" != "$file_name" ] || [ "$new_code" != "$file_code" ]; then
  sed -i -E "s/^VERSION_NAME=.*/VERSION_NAME=$new_name/; s/^VERSION_CODE=.*/VERSION_CODE=$new_code/" "$PROPS"
  changed=true
fi

echo "Previous tag : ${prev_tag:-<none>}"
echo "Version      : $new_name ($new_code)  changed=$changed"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "version_name=$new_name"
    echo "version_code=$new_code"
    echo "tag=v$new_name"
    echo "previous_tag=$prev_tag"
    echo "changed=$changed"
  } >> "$GITHUB_OUTPUT"
fi
