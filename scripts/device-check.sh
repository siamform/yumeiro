#!/usr/bin/env bash
# R15: device-check body (android-emulator-runner runs `script:` line by line, so loops live here).
# Runs each instrumented test class with its own time limit and prints the evidence into the job log.
set -u
adb logcat -c || true
(adb logcat -v time > logcat.txt &) ; sleep 1
FILTER="${TEST_FILTER:-dev.asfakul.yumeiro.StabilityStressDeviceTest,dev.asfakul.yumeiro.PlayerSwitchDeviceTest,dev.asfakul.yumeiro.OnlinePlaybackDeviceTest,dev.asfakul.yumeiro.SurfaceModeDeviceTest,dev.asfakul.yumeiro.ForceDownloadDeviceTest,dev.asfakul.yumeiro.PlaybackSessionDeviceTest,dev.asfakul.yumeiro.DramaPlaybackDeviceTest,dev.asfakul.yumeiro.FeedPositionDeviceTest}" 
case "$FILTER" in *[!A-Za-z0-9._,#]*) echo "::error::Invalid test filter"; exit 1;; esac
R=6; H=false
./gradlew --no-daemon installMobileDebug installMobileDebugAndroidTest
# R15: one class at a time with its own time limit, so one hung test (run 43612391254
# spent 70 min in one) can't hide every other result; results go into the job log.
REPEAT="${REPEAT:-1}"; case "$REPEAT" in ''|*[!0-9]*) REPEAT=1;; esac
for N in $(seq 1 "$REPEAT"); do
for C in $(echo "$FILTER" | tr ',' ' '); do
  # R16: repeats start from cleared app data, like a fresh install.
  [ "$N" -gt 1 ] && adb shell pm clear dev.asfakul.yumeiro.debug > /dev/null 2>&1
  echo "::group::$C (run $N/$REPEAT)"
  L0=$(wc -l < logcat.txt)
  timeout 900 adb shell am instrument -w -r -e class "$C" -e rounds $R -e hprof $H dev.asfakul.yumeiro.debug.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  grep -E "INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run" "inst-$C.txt" | cut -c1-400 | tail -150
  if [ $RC -eq 124 ]; then echo "TIMEOUT in $C (last test started above)"; echo "TESTS_FAILED" > failed.txt; adb shell am force-stop dev.asfakul.yumeiro.debug || true; fi
  # Only this class's lines; crash blocks (AndroidRuntime) and foreground-service decisions included.
  tail -n +$((L0 + 1)) logcat.txt | grep -E "YuStress|YuFeedPos|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|YuStartup|MTDeviceTest|ANR in|FATAL|OutOfMemory|AndroidRuntime|MediaSessionService|MediaNotification|ForegroundService|startForeground|ActiveServices" | cut -c1-300 | tail -260 || true
  grep -qE "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|Process crashed" "inst-$C.txt" && echo "TESTS_FAILED" > failed.txt
  grep -qE "^OK \(" "inst-$C.txt" || echo "TESTS_FAILED" > failed.txt
  if grep -qE "^OK \(" "inst-$C.txt" && [ $RC -ne 124 ]; then V=PASS; else V=FAIL; fi
  echo "REPEAT_RESULT $C run=$N/$REPEAT $V" | tee -a repeat-results.txt
  cp "inst-$C.txt" "inst-$C-run$N.txt" 2>/dev/null || true
  echo "::endgroup::"
done
done
mkdir -p stress && adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/stress stress/ || true
adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/shots stress/ || true
adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/feedpos stress/ || true
adb shell dumpsys meminfo dev.asfakul.yumeiro.debug > stress/meminfo.txt || true
adb shell dumpsys activity exit-info dev.asfakul.yumeiro.debug > stress/exit-info.txt || true
adb shell ls /data/anr > stress/anr-list.txt 2>&1 || true
grep -E "YuStress|YuFeedPos|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|MTDeviceTest|StrictMode|ANR in|FATAL|OutOfMemory|lowmemorykiller" logcat.txt > stress/logcat-key.txt || true
cp inst-*.txt repeat-results.txt stress/ 2>/dev/null || true
echo "::group::evidence"; tail -n 400 stress/logcat-key.txt; cat stress/exit-info.txt | head -80; cat stress/anr-list.txt; for f in $(find stress -type f -name '*report*.txt' | head -20); do echo "== $f"; head -c 6000 "$f"; done; echo "::endgroup::"
