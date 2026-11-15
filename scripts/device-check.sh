#!/usr/bin/env bash
# R15: device-check body (android-emulator-runner runs `script:` line by line, so loops live here).
# Runs each instrumented test class with its own time limit and prints the evidence into the job log.
set -u
adb logcat -c || true
(adb logcat -v time > logcat.txt &) ; sleep 1
# R16: other processes' crash/ANR dialogs (run 43676915487: "System UI isn't responding" on api33)
# sit above the app and swallow test input. Hide them; every ANR is still in logcat ("ANR in")
# and /data/anr, which the evidence step below collects.
adb shell settings put global hide_error_dialogs 1 || true
FILTER="${TEST_FILTER:-dev.asfakul.yumeiro.StabilityStressDeviceTest,dev.asfakul.yumeiro.PlayerSwitchDeviceTest,dev.asfakul.yumeiro.OnlinePlaybackDeviceTest,dev.asfakul.yumeiro.SurfaceModeDeviceTest,dev.asfakul.yumeiro.ForceDownloadDeviceTest,dev.asfakul.yumeiro.PlaybackSessionDeviceTest,dev.asfakul.yumeiro.DramaPlaybackDeviceTest,dev.asfakul.yumeiro.FeedPositionDeviceTest,dev.asfakul.yumeiro.QualityListDeviceTest,dev.asfakul.yumeiro.PinnedExtensionsDeviceTest,dev.asfakul.yumeiro.HomeNavigationDeviceTest,dev.asfakul.yumeiro.BackNavigationDeviceTest,dev.asfakul.yumeiro.ExtensionsDeviceTest,dev.asfakul.yumeiro.LocalPlayerDeviceTest,dev.asfakul.yumeiro.HeaderStreamDeviceTest,dev.asfakul.yumeiro.YumixDeviceTest,dev.asfakul.yumeiro.LibraryDeviceTest,dev.asfakul.yumeiro.DownloadResumeDeviceTest,dev.asfakul.yumeiro.BackupDeviceTest,dev.asfakul.yumeiro.AniSkipDeviceTest,dev.asfakul.yumeiro.AccessibilityDeviceTest,dev.asfakul.yumeiro.HeatGuardDeviceTest,dev.asfakul.yumeiro.ClosedAppAlertDeviceTest}" 
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
  if [ "$C" = "dev.asfakul.yumeiro.ClosedAppAlertDeviceTest" ]; then
    # G12 X5: queue (phase a), then Home, kill, deep Doze, wait for the alarm to post it, then check (phase b).
    P=dev.asfakul.yumeiro.debug
    timeout 300 adb shell am instrument -w -r -e class "$C#a_queueAndArm" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    TITLE=$(sed -n 's/.*CLOSED_A title=\(.*\) pid=.*/\1/p' logcat.txt | tail -1)
    adb shell input keyevent 3; adb shell am kill $P; sleep 2
    echo "YuClosedScript after kill: pidof='$(adb shell pidof $P | tr -d '\r')' title='$TITLE'"
    adb shell dumpsys battery unplug; adb shell dumpsys deviceidle force-idle | tr -d '\r' | sed 's/^/YuClosedScript /'
    T0=$(date +%s); SEEN=no
    while [ $(( $(date +%s) - T0 )) -lt 300 ]; do
      if [ -n "$TITLE" ] && adb shell dumpsys notification --noredact | grep -qF "$TITLE"; then SEEN=yes; break; fi
      sleep 5
    done
    echo "YuClosedScript seen=$SEEN after $(( $(date +%s) - T0 ))s · idle=$(adb shell dumpsys deviceidle get deep | tr -d '\r') · pid now='$(adb shell pidof $P | tr -d '\r')'"
    adb shell dumpsys deviceidle unforce; adb shell dumpsys battery reset
    timeout 300 adb shell am instrument -w -r -e class "$C#b_deliveredWhileClosed" -e closed 1 $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"
    # Both phases must report OK.
    [ "$(grep -c '^OK (' "inst-$C.txt")" -eq 2 ] || sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"
  else
  timeout 900 adb shell am instrument -w -r -e class "$C" -e rounds $R -e hprof $H dev.asfakul.yumeiro.debug.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  fi
  grep -E "INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run" "inst-$C.txt" | cut -c1-400 | tail -150
  if [ $RC -eq 124 ]; then echo "TIMEOUT in $C (last test started above)"; echo "TESTS_FAILED" > failed.txt; adb shell am force-stop dev.asfakul.yumeiro.debug || true; fi
  # Only this class's lines; crash blocks (AndroidRuntime) and foreground-service decisions included.
  tail -n +$((L0 + 1)) logcat.txt | grep -E "YuStress|YuA11y|YuHeat|YuClosed|YuLibraryTest|YuDownloadTest|YuBackupTest|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|YuStartup|MTDeviceTest|ANR in|FATAL|OutOfMemory|AndroidRuntime|MediaSessionService|MediaNotification|ForegroundService|startForeground|ActiveServices" | cut -c1-300 | tail -260 || true
  # Full stack of every failing test (the line filter above keeps only its first line).
  awk '/^INSTRUMENTATION_STATUS: stack=/{p=1} /^INSTRUMENTATION_STATUS: (test|class|current|id|numtests|stream)=|^INSTRUMENTATION_STATUS_CODE/{p=0} p' "inst-$C.txt" | grep -vE "^\s+at (org\.junit|java\.lang\.reflect|jdk\.internal|androidx\.test\.internal)" | head -80
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
grep -E "YuStress|YuA11y|YuHeat|YuClosed|YuLibraryTest|YuDownloadTest|YuBackupTest|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|MTDeviceTest|StrictMode|ANR in|FATAL|OutOfMemory|lowmemorykiller" logcat.txt > stress/logcat-key.txt || true
cp inst-*.txt repeat-results.txt stress/ 2>/dev/null || true
echo "::group::evidence"; tail -n 400 stress/logcat-key.txt; cat stress/exit-info.txt | head -80; cat stress/anr-list.txt; for f in $(find stress -type f -name '*report*.txt' | head -20); do echo "== $f"; head -c 6000 "$f"; done; echo "::endgroup::"
