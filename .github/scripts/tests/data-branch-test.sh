#!/usr/bin/env bash
# NBUG-16 (NB16-1, NB16-2) regression test for the data-branch workflows.
# Runs the real "Checkout <data> branch" step of each data workflow inside a clone that is sparse
# (.github only) exactly like actions/checkout with `sparse-checkout: .github`, then checks:
#   NB16-2  the previous data files are on disk while the build runs (keep-the-last-good-copy);
#   NB16-1  data-changed.sh reports a NEW file as changed (exit 0), nothing as unchanged (exit 1),
#           and a git error as an error (exit 2), never as "unchanged".
# Needs only git and jq. Usage: bash .github/scripts/tests/data-branch-test.sh
set -uo pipefail
root=$(cd "$(dirname "$0")/../../.." && pwd)
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
fails=0
ok()   { echo "PASS $*"; }
bad()  { echo "FAIL $*"; fails=$((fails + 1)); }
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t GIT_CONFIG_NOSYSTEM=1
git config --global init.defaultBranch main >/dev/null 2>&1 || true

# step <workflow> <branch>: the run block of "Checkout <branch> branch" from the workflow file.
step() {
  awk -v want="- name: Checkout $2 branch" '
    index($0, want) { grab = 1; next }
    grab && /^      - name:/ { exit }
    grab && /run: \|/ { body = 1; next }
    grab && body { sub(/^          /, ""); print }
  ' "$root/.github/workflows/$1.yml"
}

for pair in playlist-market:market extension-market:extensions stream-sync:streams; do
  wf=${pair%%:*}; br=${pair##*:}
  script=$(step "$wf" "$br")
  [ -n "$script" ] || { bad "$wf: no 'Checkout $br branch' step found"; continue; }
  d="$work/$wf"; mkdir -p "$d"
  # Remote: main with .github + app code; the data branch with an index and a data file in a folder.
  git init -q --bare "$d/remote.git"
  git clone -q "$d/remote.git" "$d/seed" 2>/dev/null
  ( cd "$d/seed"
    mkdir -p .github/scripts app; cp "$root/.github/scripts/"*.sh .github/scripts/; echo code > app/x.txt
    git add -A; git commit -qm main; git push -q origin HEAD:main
    git checkout -q --orphan "$br"; git rm -rqf . >/dev/null
    mkdir -p playlists; echo '{"generated":"1","items":["a"]}' > index.json; echo "#EXTM3U" > playlists/a.m3u
    git add -A; git commit -qm data; git push -q origin "HEAD:$br" )
  # Like actions/checkout: init + `remote add origin` (default refspec), sparse (cone) on .github,
  # fetch depth 1, checkout.
  git init -q "$d/ws"; git -C "$d/ws" remote add origin "file://$d/remote.git"
  git -C "$d/ws" sparse-checkout set --cone .github
  git -C "$d/ws" fetch -q --depth=1 origin +refs/heads/main:refs/remotes/origin/main
  git -C "$d/ws" checkout -q -B main refs/remotes/origin/main
  ( cd "$d/ws" && bash -c "$script" ) >"$d/step.log" 2>&1 || { bad "$wf: checkout step failed: $(tail -3 "$d/step.log")"; continue; }
  out="$d/ws/out"
  # NB16-2: the previous data file must be present.
  if [ -f "$out/playlists/a.m3u" ]; then ok "$wf: previous data on disk"; else bad "$wf: NB16-2 previous playlists/a.m3u missing in out/ (sparse worktree)"; fi
  cd "$d/ws"
  # Unchanged: only the timestamp moved.
  echo '{"generated":"2","items":["a"]}' > out/index.json
  bash .github/scripts/data-changed.sh out >/dev/null 2>&1; rc=$?
  [ $rc -eq 1 ] && ok "$wf: unchanged -> 1" || bad "$wf: unchanged data gave exit $rc, expected 1"
  # NB16-1: a new file is a change.
  mkdir -p out/playlists; echo "#EXTM3U" > out/playlists/new.m3u
  bash .github/scripts/data-changed.sh out >"$d/dc.log" 2>&1; rc=$?
  [ $rc -eq 0 ] && ok "$wf: new file -> changed" || bad "$wf: NB16-1 a new file gave exit $rc ($(tail -2 "$d/dc.log" | tr '\n' ' '))"
  # NB16-1: a git error must not read as "unchanged".
  mkdir -p "$d/broken"; echo "gitdir: $d/nowhere" > "$d/broken/.git"
  bash .github/scripts/data-changed.sh "$d/broken" >/dev/null 2>&1; rc=$?
  [ $rc -eq 2 ] && ok "$wf: git error -> 2" || bad "$wf: NB16-1 a git error gave exit $rc, expected 2 (1 means 'skip publish')"
  cd "$root"
done
echo "data-branch-test: $fails failure(s)"
[ $fails -eq 0 ]
