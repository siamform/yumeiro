#!/usr/bin/env bash
# Usage: data-changed.sh <dir>
# Exit 0 when <dir> (a worktree of a data branch) has real changes, 1 when nothing but
# timestamps changed. Used to skip pointless force-pushes and keep Actions usage low.
set -euo pipefail
dir=$1
[ -e "$dir/.git" ] || { echo "first publish"; exit 0; }
cd "$dir"
git add -A
norm='walk(if type=="object" then del(.generated, .checked) else . end)'
for f in $(git diff --cached --name-only); do
  [ "$f" = "README.md" ] && continue
  if [[ "$f" == *.json ]] && [ -f "$f" ] && git cat-file -e "HEAD:$f" 2>/dev/null; then
    [ "$(git show "HEAD:$f" | jq -S "$norm")" = "$(jq -S "$norm" "$f")" ] && continue
  fi
  echo "changed: $f"; exit 0
done
echo "no changes"; exit 1
