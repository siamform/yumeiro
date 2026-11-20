#!/bin/bash
# usage: worker.sh <name> [adopt_run kind chunk]  — pulls "kind:chunk" lines from /tmp/queue.txt
cd "${REPO:-$(git rev-parse --show-toplevel)}"
name=$1; want=$2; adopt=$3; akind=$4; achunk=$5
finish() { # run kind chunk
  local run=$1 kind=$2 c=$3
  echo "$run $kind $c" > /tmp/cur.$name
  while [ "$(gh run view $run -R siamform/yumeiro --json status -q .status 2>/dev/null)" != completed ]; do sleep 60; done
  local concl=$(gh run view $run -R siamform/yumeiro --json conclusion -q .conclusion)
  bash scripts/final_collect.sh $run > /tmp/collect-$run.log 2>&1
  local bad=$(grep -vc " PASS .*skipped=0" docs/evidence/final/$run.txt 2>/dev/null)
  echo "$run $kind $c done $concl bad=$bad $(date -u +%T)" >> /tmp/lanes.log
  rm -f /tmp/cur.$name
}
[ -n "$adopt" ] && finish $adopt $akind $achunk
while true; do
  spec=$(flock /tmp/queue.lock bash -c "l=\$(grep -m1 '^$want:' /tmp/queue.txt); [ -n \"\$l\" ] && sed -i \"0,/^\$l\$/{//d}\" /tmp/queue.txt; echo \"\$l\"")
  [ -z "$spec" ] && break
  kind=${spec%%:*}; c=${spec##*:}
  list=$(git show origin/r15-stability:docs/evidence/final/PLAN.md 2>/dev/null | sed -n "s/^- chunk_$c ([0-9]*): //p")
  [ -z "$list" ] && list=$(sed -n "s/^- chunk_$c ([0-9]*): //p" docs/evidence/final/PLAN.md)
  case $kind in phone) extra="-f phone=both";; tv34) extra="-f tv=true -f tv_api=34";; tv24) extra="-f tv=true -f tv_api=24";; esac
  run=""; rm -f /tmp/lastrun.$name
  flock /tmp/dispatch.lock bash -c "
    before=\$(gh run list -R siamform/yumeiro -w device-check.yml -L 1 --json databaseId -q '.[0].databaseId')
    gh workflow run device-check.yml -R siamform/yumeiro --ref r15-stability -f test='$list' -f passes=true -f once=true -f lane=$name $extra >/dev/null
    for i in \$(seq 1 30); do sleep 10
      id=\$(gh run list -R siamform/yumeiro -w device-check.yml -L 1 --json databaseId -q '.[0].databaseId')
      [ \"\$id\" -gt \"\$before\" ] && { echo \$id > /tmp/lastrun.$name; break; }
    done"
  run=$(cat /tmp/lastrun.$name)
  echo "$run $kind $c dispatched $(date -u +%T)" >> /tmp/lanes.log
  finish $run $kind $c
done
echo "$name finished" >> /tmp/lanes.log
