#!/usr/bin/env bash
# Usage: data-changed.sh <dir>
# Exit 0 when <dir> (a worktree of a data branch) has real changes, 1 when nothing but
# timestamps changed, 2 on any error. Used to skip pointless force-pushes and keep Actions usage
# low. NBUG-16 NB16-1: an error used to exit 1 under `set -e` (a new file outside a sparse
# checkout made `git add -A` fail), which the workflows read as "no changes" and never published.
set -uo pipefail
fail() { echo "data-changed: $*" >&2; exit 2; }
dir=${1:-}
[ -n "$dir" ] || fail "no directory given"
[ -e "$dir/.git" ] || { echo "first publish"; exit 0; }
cd "$dir" || fail "cannot enter $dir"
git rev-parse --verify -q HEAD >/dev/null || fail "$dir is not a usable git worktree"
# --sparse: also stage paths outside a sparse-checkout definition (the worktree is opened with
# sparse checkout disabled, see open-data-branch.sh; this keeps the check correct either way).
git add -A --sparse || fail "git add failed"
names=$(git diff --cached --name-only) || fail "git diff failed"
norm='walk(if type=="object" then del(.generated, .checked) else . end)'
while IFS= read -r f; do
  [ -z "$f" ] && continue
  [ "$f" = "README.md" ] && continue
  if [[ "$f" == *.json ]] && [ -f "$f" ] && git cat-file -e "HEAD:$f" 2>/dev/null; then
    old=$(git show "HEAD:$f" | jq -S "$norm") || fail "jq failed on HEAD:$f"
    new=$(jq -S "$norm" "$f") || fail "jq failed on $f"
    [ "$old" = "$new" ] && continue
  fi
  echo "changed: $f"; exit 0
done <<< "$names"
echo "no changes"; exit 1
