#!/usr/bin/env bash
# GATE-1-FTL body: runs GateOneFtlSuite (or $TARGETS) on Test Lab phones, current and/or baseline (GATE-1-AB), then pulls
# each device's logcat and prints the YuGate1 / YuStall lines into the job log. Never prints credentials.
set -u
mkdir -p ftl
APP=$(ls app/build/outputs/apk/mobile/debug/*.apk | grep -v androidTest | head -1)
TEST=$(ls app/build/outputs/apk/androidTest/mobile/debug/*.apk | head -1)
echo "FTL app=$APP test=$TEST"
gcloud firebase test android models list --filter="form=PHYSICAL" --format="value(codename,brand,name,supportedVersionIds.list())" > ftl/models.txt 2>&1 || true
sed 's/^/FTL_MODEL /' ftl/models.txt | head -80
MODELS="${MODELS:-auto}"
if [ "$MODELS" = "auto" ]; then
  # One budget phone (2-4 GB class) and one current Pixel, each at its newest version on Test Lab.
  pick() { for c in "$@"; do l=$(awk -v c="$c" '$1==c' ftl/models.txt | head -1); [ -n "$l" ] && { v=$(echo "$l" | awk '{print $NF}' | tr ',;' '\n\n' | sort -n | tail -1); echo "model=$c,version=$v"; return; }; done; }
  LOW=$(pick a10 a11q a12 a13 a14 a15 a03s gta8 sargo blueline)
  HIGH=$(pick oriole panther shiba husky tokay caiman komodo)
  MODELS="$LOW;$HIGH"
fi
echo "FTL devices: $MODELS"
case "${VARIANTS:-both}" in both) VS="current baseline";; *) VS="$VARIANTS";; esac
RC=0
for V in $VS; do
  B=false; [ "$V" = "baseline" ] && B=true
  DEV=(); IFS=';' read -ra M <<< "$MODELS"; for m in "${M[@]}"; do [ -n "$m" ] && DEV+=(--device "$m"); done
  [ ${#DEV[@]} -gt 0 ] || { echo "::error::no Test Lab device found"; exit 1; }
  echo "::group::Test Lab $V"
  gcloud firebase test android run --type instrumentation --app "$APP" --test "$TEST" "${DEV[@]}" \
    --test-targets "${TARGETS:-class dev.asfakul.yumeiro.GateOneFtlSuite}" --use-orchestrator \
    --environment-variables "clearPackageData=false,gate1Baseline=$B" \
    --timeout 15m --num-flaky-test-attempts 0 --no-record-video --no-performance-metrics \
    --results-dir "gate-$GITHUB_RUN_ID-$V" --format=json > "ftl/result-$V.json" 2> "ftl/run-$V.txt" || RC=1
  grep -E "Test results|Passed|Failed|Inconclusive|Skipped|gs://|https://console" "ftl/run-$V.txt" | head -20
  BUCKET=$(grep -oE "gs://[^ ]+" "ftl/run-$V.txt" | head -1 | sed 's:/*$::')
  if [ -n "$BUCKET" ]; then
    mkdir -p "ftl/$V"
    gcloud storage cp -r "$BUCKET/*" "ftl/$V/" > /dev/null 2>&1 || gsutil -m cp -r "$BUCKET/*" "ftl/$V/" > /dev/null 2>&1 || true
    for L in $(find "ftl/$V" -name "logcat*" 2>/dev/null); do
      echo "--- $L"; grep -hE "YuGate1|YuStall|STALL_SCHED|GATE1_|YuGroup|_RESULT" "$L" | head -120
    done
  fi
  echo "::endgroup::"
done
exit $RC
