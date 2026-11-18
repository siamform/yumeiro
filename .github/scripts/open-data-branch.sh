#!/usr/bin/env bash
# Usage: open-data-branch.sh <branch> <dir>
# Opens the data branch <branch> as a full worktree in <dir> (or an empty <dir> when the branch does
# not exist yet: first publish). NBUG-16 NB16-2: the workflows check out only .github (sparse), and a
# plain `git worktree add` inherits that, so none of the previous data files were on disk and the
# builds' "keep the last good copy" (build_market.py, build_extension_market.py, sync_streams.py)
# never found anything. Sparse checkout is disabled for the data worktree only.
set -euo pipefail
branch=${1:?branch}; dir=${2:?dir}
if git ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1; then
  git fetch --depth=1 origin "+refs/heads/$branch:refs/remotes/origin/$branch"
  git worktree add --no-checkout "$dir" "origin/$branch"
  git -C "$dir" sparse-checkout disable
  git -C "$dir" checkout -q --detach "origin/$branch"
  [ -z "$(git -C "$dir" ls-files --deleted)" ] || { echo "::error::$branch worktree is missing files"; exit 1; }
else
  mkdir -p "$dir"
fi
