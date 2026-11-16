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
FILTER="${TEST_FILTER:-dev.asfakul.yumeiro.StabilityStressDeviceTest,dev.asfakul.yumeiro.PlayerSwitchDeviceTest,dev.asfakul.yumeiro.OnlinePlaybackDeviceTest,dev.asfakul.yumeiro.SurfaceModeDeviceTest,dev.asfakul.yumeiro.ForceDownloadDeviceTest,dev.asfakul.yumeiro.PlaybackSessionDeviceTest,dev.asfakul.yumeiro.DramaPlaybackDeviceTest,dev.asfakul.yumeiro.FeedPositionDeviceTest,dev.asfakul.yumeiro.QualityListDeviceTest,dev.asfakul.yumeiro.PinnedExtensionsDeviceTest,dev.asfakul.yumeiro.HomeNavigationDeviceTest,dev.asfakul.yumeiro.BackNavigationDeviceTest,dev.asfakul.yumeiro.ExtensionsDeviceTest,dev.asfakul.yumeiro.LocalPlayerDeviceTest,dev.asfakul.yumeiro.HeaderStreamDeviceTest,dev.asfakul.yumeiro.YumixDeviceTest,dev.asfakul.yumeiro.LibraryDeviceTest,dev.asfakul.yumeiro.DownloadResumeDeviceTest,dev.asfakul.yumeiro.BackupDeviceTest,dev.asfakul.yumeiro.AniSkipDeviceTest,dev.asfakul.yumeiro.AccessibilityDeviceTest,dev.asfakul.yumeiro.HeatGuardDeviceTest,dev.asfakul.yumeiro.ClosedAppAlertDeviceTest,dev.asfakul.yumeiro.CrashUiDeviceTest}" 
case "$FILTER" in *[!A-Za-z0-9._,#]*) echo "::error::Invalid test filter"; exit 1;; esac
R=6; H="${HPROF:-false}"; case "$H" in true|false) ;; *) H=false;; esac
# FLAVOR=Tv installs Yumeiro TV (minSdk 24) on the Android TV emulator; default is the phone app.
FLAVOR="${FLAVOR:-Mobile}"
./gradlew --no-daemon install${FLAVOR}Debug install${FLAVOR}DebugAndroidTest
# R15: one class at a time with its own time limit, so one hung test (run 43612391254
# spent 70 min in one) can't hide every other result; results go into the job log.
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r'); case "$SDK" in ''|*[!0-9]*) SDK=99;; esac
REPEAT="${REPEAT:-1}"; case "$REPEAT" in ''|*[!0-9]*) REPEAT=1;; esac
for N in $(seq 1 "$REPEAT"); do
for C in $(echo "$FILTER" | tr ',' ' '); do
  # R16: repeats start from cleared app data, like a fresh install.
  [ "$N" -gt 1 ] && adb shell pm clear dev.asfakul.yumeiro.debug > /dev/null 2>&1
  # G12 X9: Android 7–8 (TV app) asks for storage at start, before a test can grant it, and
  # UiAutomation.grantRuntimePermission only exists from API 28 (run 43759794246). Grant it first.
  if [ "$SDK" -lt 28 ]; then
    for PERM in READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE; do adb shell pm grant dev.asfakul.yumeiro.debug android.permission.$PERM > /dev/null 2>&1; done
  fi
  echo "::group::$C (run $N/$REPEAT)"
  L0=$(wc -l < logcat.txt)
  if [ "$C" = "dev.asfakul.yumeiro.HomeTraceDeviceTest" ]; then
    # ST-18: prepare (guide answered), then three traced cold starts to Home.
    P=dev.asfakul.yumeiro.debug
    timeout 300 adb shell am instrument -w -r -e class "$C#a_prepare" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
    mkdir -p stress/trace
    for T in 1 2 3; do
      adb shell am force-stop $P; sleep 3
      adb shell rm -f /data/misc/perfetto-traces/home-$T.pftrace > /dev/null 2>&1
      adb shell "perfetto --txt -c - -o /data/misc/perfetto-traces/home-$T.pftrace --background" < scripts/home-trace.pbtxt > stress/trace/perfetto-$T.txt 2>&1 || true
      sleep 2
      adb shell dumpsys gfxinfo $P reset > /dev/null 2>&1 || true
      adb shell am start -W -n $P/dev.asfakul.yumeiro.MainActivity > stress/trace/start-$T.txt 2>&1 || true
      echo "HOME_TRACE start $T $(grep -E 'TotalTime|WaitTime' stress/trace/start-$T.txt | tr -d '\r' | tr '\n' ' ')"
      sleep 19
      adb shell dumpsys gfxinfo $P > stress/trace/gfxinfo-$T.txt 2>&1 || true
      grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th|Number Slow UI|Number Frame deadline" stress/trace/gfxinfo-$T.txt | head -9 | sed "s/^/HOME_TRACE gfx $T /"
      adb pull /data/misc/perfetto-traces/home-$T.pftrace stress/trace/ > /dev/null 2>&1 || echo "HOME_TRACE no trace $T: $(cat stress/trace/perfetto-$T.txt | head -3)"
    done
    ls -la stress/trace | sed 's/^/HOME_TRACE file /'
  elif [ "$C" = "dev.asfakul.yumeiro.ClosedAppAlertDeviceTest" ]; then
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
  elif [ "$C" = "dev.asfakul.yumeiro.ResumeAcrossProcessDeviceTest" ]; then
    # BUG-1: phase a plays, pauses, seeks to 8 s and kills its own process (no ON_STOP, no dispose;
    # its own result is "Process crashed" by design); phase b, a new process, reopens from Home.
    P=dev.asfakul.yumeiro.debug
    timeout 300 adb shell am instrument -w -r -e class "$C#a_playSeekThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuResumeScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines"
    timeout 300 adb shell am instrument -w -r -e class "$C#b_reopenFromHomeJumpBackIn" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  elif [ "$C" = "dev.asfakul.yumeiro.DownloadKillDeviceTest" ]; then
    # G8: phase a downloads to 25 % and kills its own process ("Process crashed" by design);
    # phase b, a new process, checks the download finished by itself, then the delete case.
    P=dev.asfakul.yumeiro.debug
    timeout 400 adb shell am instrument -w -r -e class "$C#a_startThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuDlKillScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines"
    timeout 900 adb shell am instrument -w -r -e class "$C#b_continuesAfterProcessDeath,$C#c_deleteMidDownloadLeavesNoFiles" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  elif [ "$C" = "dev.asfakul.yumeiro.SettingsWalkDeviceTest" ]; then
    # G9: phase a flips every Settings switch and kills its own process ("Process crashed" by
    # design); phase b, a new process, checks each kept its new value and puts it back.
    P=dev.asfakul.yumeiro.debug
    timeout 900 adb shell am instrument -w -r -e class "$C#a_flipEverySwitchThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuSettingsWalkScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt")"
    timeout 900 adb shell am instrument -w -r -e class "$C#b_everySwitchKeptItsValue" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    # A failure in phase a (a switch that did not move) must fail the run too.
    if grep -q 'FAILURES' "inst-$C-a.txt"; then cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; else cp "inst-$C-b.txt" "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.PermissionFlowDeviceTest" ]; then
    # G10: phase a gets past first run; the script then takes the notification permission away
    # and clears "asked before" (revoking kills the app, so it cannot happen inside a test);
    # phase b, a new process, denies once, denies again, and follows the message to Android settings.
    P=dev.asfakul.yumeiro.debug
    timeout 300 adb shell am instrument -w -r -e class "$C#a_seedFirstRun" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    adb shell am force-stop $P; adb shell pm revoke $P android.permission.POST_NOTIFICATIONS 2>&1 | sed 's/^/YuPermScript revoke: /'
    adb shell pm clear-permission-flags $P android.permission.POST_NOTIFICATIONS user-set user-fixed 2>&1 | sed 's/^/YuPermScript flags: /'
    echo "YuPermScript after revoke: $(adb shell dumpsys package $P | grep -m1 'POST_NOTIFICATIONS: granted' | tr -d '\r')"
    timeout 400 adb shell am instrument -w -r -e class "$C#b_deniedOnceThenBlocked" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
    if grep -q '^OK (' "inst-$C-a.txt"; then cp "inst-$C-b.txt" "inst-$C.txt"; else cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.CrashUiDeviceTest" ]; then
    # G12 X6: phase a really crashes the app (its own result is "Process crashed" by design).
    P=dev.asfakul.yumeiro.debug
    timeout 120 adb shell am instrument -w -r -e class "$C#a_crashForReal" -e crash 1 $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    echo "YuCrashUiScript phase a: $(grep -cE 'Process crashed|IllegalStateException: YuCrashUi' "inst-$C-a.txt") crash lines · pidof='$(adb shell pidof $P | tr -d '\r')'"
    timeout 600 adb shell am instrument -w -r -e class "$C#b_readShareAndDelete" -e crashed 1 $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  else
  timeout 900 adb shell am instrument -w -r -e class "$C" -e rounds $R -e hprof $H dev.asfakul.yumeiro.debug.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  fi
  grep -E "INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run" "inst-$C.txt" | cut -c1-400 | tail -150
  if [ $RC -eq 124 ]; then echo "TIMEOUT in $C (last test started above)"; echo "TESTS_FAILED" > failed.txt; adb shell am force-stop dev.asfakul.yumeiro.debug || true; fi
  # Only this class's lines; crash blocks (AndroidRuntime) and foreground-service decisions included.
  tail -n +$((L0 + 1)) logcat.txt | grep -E "YuStress|YuResume|YuChannel|YuYtQuality|YuWidgetTest|YuDecoderFault|YuAnime4k|YuRtl|YuA11y|YuHeat|YuClosed|YuCrashUi|YuLibraryTest|YuDownloadTest|YuSettingsWalk|YuBackupTest|YuBackdropTest|YuPlayerFx|YuNotifyFx|YuPermFlow|YuPermScript|YuNetState|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|YuStartup|MTDeviceTest|ANR in|FATAL|OutOfMemory|AndroidRuntime|MediaSessionService|MediaNotification|ForegroundService|startForeground|ActiveServices" | cut -c1-300 | tail -260 || true
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
grep -E "YuStress|YuResume|YuChannel|YuYtQuality|YuWidgetTest|YuDecoderFault|YuAnime4k|YuRtl|YuA11y|YuHeat|YuClosed|YuCrashUi|YuLibraryTest|YuDownloadTest|YuBackupTest|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|MTDeviceTest|StrictMode|ANR in|FATAL|OutOfMemory|lowmemorykiller" logcat.txt > stress/logcat-key.txt || true
cp inst-*.txt repeat-results.txt stress/ 2>/dev/null || true
echo "::group::evidence"; tail -n 400 stress/logcat-key.txt; cat stress/exit-info.txt | head -80; cat stress/anr-list.txt; for f in $(find stress -type f -name '*report*.txt' | head -20); do echo "== $f"; head -c 6000 "$f"; done; echo "::endgroup::"
