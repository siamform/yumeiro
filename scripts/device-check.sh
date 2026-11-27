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
# GATE-1-AB: GATE1_BASELINE=true runs the gate walk with the GATE-1 app fixes off (GateOneVariant).
GB="${GATE1_BASELINE:-false}"; case "$GB" in true|false) ;; *) GB=false;; esac; echo "YuGateEnv gate1Baseline=$GB"
# FLAVOR=Tv installs Yumeiro TV (minSdk 24) on the Android TV emulator; default is the phone app.
FLAVOR="${FLAVOR:-Mobile}"
./gradlew --no-daemon install${FLAVOR}Debug install${FLAVOR}DebugAndroidTest
# R15: one class at a time with its own time limit, so one hung test (run 43612391254
# spent 70 min in one) can't hide every other result; results go into the job log.
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r'); case "$SDK" in ''|*[!0-9]*) SDK=99;; esac
# GATE-1-ANR-T8: emulators scale the 5 s input-ANR timeout by ro.hw_timeout_multiplier; log it.
# TV-API24-JIT diagnosis: ART 7.0's JIT dies in UsePosition::Dup while compiling one of our methods.
# Only when the filter names TvJitProbeDeviceTest on API 24: log every method the compiler starts
# ("Building ..."), so the last one before the SIGSEGV is the culprit. Needs a rootable image.
case "$TEST_FILTER" in *TvJitProbeDeviceTest*)
  if [ "$SDK" = "24" ]; then
    adb root > /dev/null 2>&1 || true; sleep 3; adb wait-for-device
    adb shell setprop dalvik.vm.extra-opts -verbose:compiler || true
    echo "JITPROBE extra-opts='$(adb shell getprop dalvik.vm.extra-opts | tr -d '\r')' uid=$(adb shell id -u | tr -d '\r')"
    adb shell stop; sleep 2; adb shell start
    for i in $(seq 1 60); do sleep 3; [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && adb shell pm path android > /dev/null 2>&1 && break; done
    sleep 20; adb logcat -G 16M || true
    # adb root restarted adbd, which ended the background logcat above: start it again.
    (adb logcat -v time >> logcat.txt &) ; sleep 1
  fi;;
esac
echo "YuGateEnv hw_timeout_multiplier='$(adb shell getprop ro.hw_timeout_multiplier | tr -d '\r')' sdk=$SDK"
REPEAT="${REPEAT:-1}"; case "$REPEAT" in ''|*[!0-9]*) REPEAT=1;; esac
# Final test (MASTER-CHECKLIST-FINAL): P1 = every class from cleared data and a cold start; P2 = the
# same classes again with P1's data kept, the app killed and reopened (warm start, populated data).
PASSES="${PASSES:-false}"; [ "$PASSES" = "true" ] && REPEAT=2
# Owner 2026-11-20: final passes P1 only (fresh install, cold start) to save time.
[ "$PASSES" = "true" ] && [ "${PASSES_ONCE:-false}" = "true" ] && REPEAT=1
# FINAL-12 (api33-lowram, run 44617327290): the system launcher hung during boot ("ANR in
# com.google.android.apps.nexuslauncher" before the first test) and kept window focus, so every
# focus check saw the launcher, not Yumeiro. hide_error_dialogs hides the dialog but not the hang.
# Before each class: if a system app (launcher / System UI) went ANR since the last check, restart it
# and go Home. Logged as HARNESS lines; the ANR itself stays in logcat and the evidence.
ANR_SEEN=0
recover_system_anr() {
  local n; n=$(grep -cE "ANR in (com\.google\.android\.apps\.nexuslauncher|com\.google\.android\.tvlauncher|com\.google\.android\.apps\.tv\.launcherx|com\.android\.systemui|com\.android\.launcher3)" logcat.txt 2>/dev/null)
  [ "${n:-0}" -le "$ANR_SEEN" ] && return 0
  ANR_SEEN=$n
  local pkg; pkg=$(grep -oE "ANR in (com\.google\.android\.apps\.nexuslauncher|com\.google\.android\.tvlauncher|com\.google\.android\.apps\.tv\.launcherx|com\.android\.systemui|com\.android\.launcher3)" logcat.txt | tail -1 | sed 's/ANR in //')
  echo "HARNESS system ANR seen ($pkg, $n so far): restarting it before the next class"
  if [ "$pkg" = "com.android.systemui" ]; then adb shell pkill -f com.android.systemui > /dev/null 2>&1 || true
  else adb shell am force-stop "$pkg" > /dev/null 2>&1 || true; fi
  sleep 3; adb shell input keyevent 3 > /dev/null 2>&1 || true; sleep 3
  echo "HARNESS focus after restart: $(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r' | sed 's/^ *//')"
}
# FINAL-15 (tv-api24 run 44620669094): after DownloadThroughputDeviceTest (screen off + forced Doze) the
# next adb call never returned and the job sat 100 min until the 110-min limit, so P1/P2 of the rest of the
# chunk never ran and no artifacts but the logcat were kept. Every class now starts with a bounded adb
# check; a dead link gets one reconnect and a screen wake, and a link still dead ends the loop with a
# HARNESS line so the remaining classes are reported as not run and the artifacts upload.
ADB_LOST=0
adb_alive() {
  timeout 20 adb shell true > /dev/null 2>&1 && return 0
  echo "HARNESS adb did not answer in 20 s before $C (run $N): reconnecting"
  timeout 30 adb reconnect > /dev/null 2>&1 || true
  timeout 120 adb wait-for-device > /dev/null 2>&1 || true
  timeout 20 adb shell input keyevent 224 > /dev/null 2>&1 || true
  timeout 20 adb shell dumpsys deviceidle unforce > /dev/null 2>&1 || true
  if timeout 20 adb shell true > /dev/null 2>&1; then echo "HARNESS adb back after reconnect"; return 0; fi
  echo "HARNESS adb lost before $C (run $N): stopping; the remaining classes are not run"
  ADB_LOST=1; return 1
}
# GATE-1-QUIET (gate 44982381315, api33-lowram): during the walk Google Maps'
# server_recovery_process crash-looped (about 25 restarts in 20 s), Google apps were killed for package
# changes (background updates) and Play Services ran 154k getResources calls (1.8k on api36). The main
# thread then waited 997 ms of a 1520 ms stall in the run queue. The gate measures Yumeiro, so before
# the walk: stop a crash-looping Maps, then wait (max 240 s) until the CPU is below 40 % busy for 3
# samples in a row. Everything is logged as GATE_QUIET lines; the 700 ms limit is unchanged.
cpu_busy() {
  local a b; a=$(timeout 10 adb shell head -1 /proc/stat < /dev/null | tr -d '\r'); sleep 5
  b=$(timeout 10 adb shell head -1 /proc/stat < /dev/null | tr -d '\r')
  echo "$a|$b" | awk -F'|' '{ split($1,x," "); split($2,y," "); ti=0; tb=0;
    for (i=2;i<=NF+20;i++) if (x[i]!="") { d=y[i]-x[i]; ti+=d; if (i!=5 && i!=6) tb+=d }
    if (ti>0) printf "%d", 100*tb/ti; else print 100 }'
}
gate_quiet() {
  local deaths; deaths=$(grep -cE "server_recovery_process \(pid [0-9]+\) has died" logcat.txt 2>/dev/null)
  if [ "${deaths:-0}" -ge 5 ]; then
    echo "GATE_QUIET maps server_recovery_process died $deaths times: force-stop com.google.android.apps.maps"
    timeout 20 adb shell am force-stop com.google.android.apps.maps < /dev/null > /dev/null 2>&1 || true
  fi
  local t0 calm busy; t0=$(date +%s); calm=0
  while [ $(( $(date +%s) - t0 )) -lt 240 ]; do
    busy=$(cpu_busy); echo "GATE_QUIET cpu busy ${busy}% at $(( $(date +%s) - t0 )) s"
    if [ "${busy:-100}" -lt 40 ]; then calm=$((calm + 1)); else calm=0; fi
    [ "$calm" -ge 3 ] && break
  done
  echo "GATE_QUIET settled=$([ "$calm" -ge 3 ] && echo yes || echo no) after $(( $(date +%s) - t0 )) s"
  timeout 20 adb shell top -b -n 1 -m 8 < /dev/null 2>/dev/null | tr -d '\r' | head -16 | sed 's/^/GATE_QUIET top /'
}
for N in $(seq 1 "$REPEAT"); do
for C in $(echo "$FILTER" | tr ',' ' '); do
  adb_alive || break 2
  # R16: repeats start from cleared app data, like a fresh install.
  if [ "$PASSES" = "true" ]; then
    adb shell am force-stop dev.asfakul.yumeiro.debug > /dev/null 2>&1
    [ "$N" -eq 1 ] && adb shell pm clear dev.asfakul.yumeiro.debug > /dev/null 2>&1
  else
    [ "$N" -gt 1 ] && adb shell pm clear dev.asfakul.yumeiro.debug > /dev/null 2>&1
  fi
  # G12 X9: Android 7–8 (TV app) asks for storage at start, before a test can grant it, and
  # UiAutomation.grantRuntimePermission only exists from API 28 (run 43759794246). Grant it first.
  if [ "$SDK" -lt 28 ]; then
    for PERM in READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE; do adb shell pm grant dev.asfakul.yumeiro.debug android.permission.$PERM > /dev/null 2>&1; done
  fi
  recover_system_anr
  echo "::group::$C (run $N/$REPEAT)"
  # GATE-1-PREPARE-COLD: the first start after install is measured on a settled CPU too, so a stall there
  # (4-6 s while Play Services ran in 44984838999 / 44985820433) can be told apart from CPU starvation.
  [ "$C" = "dev.asfakul.yumeiro.GateOnePrepareDeviceTest" ] && gate_quiet
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
  elif [ "$C" = "dev.asfakul.yumeiro.MainThreadStallRealClockDeviceTest" ]; then
    # GATE-1: the walk runs under a Perfetto trace (main + RenderThread + HWUI) so a long frame can be read.
    P=dev.asfakul.yumeiro.debug
    mkdir -p stress/trace
    gate_quiet
    adb shell rm -f /data/misc/perfetto-traces/gate.pftrace > /dev/null 2>&1
    adb shell "perfetto --txt -c - -o /data/misc/perfetto-traces/gate.pftrace --background" < scripts/gate-trace.pbtxt > stress/trace/perfetto-gate.txt 2>&1 || true
    sleep 2
    # GATE-1-QUIET: who else used the CPU during the walk, every 5 s (the trace ring can lose the start).
    ( while :; do echo "== $(date -u +%H:%M:%S)"; timeout 10 adb shell top -b -n 1 -m 8 < /dev/null 2>/dev/null | tr -d '\r' | head -16; sleep 5; done ) > stress/trace/top.txt 2>&1 &
    TOP_PID=$!
    timeout 600 adb shell am instrument -w -r -e class "$C" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
    kill $TOP_PID > /dev/null 2>&1 || true
    adb shell killall -INT perfetto > /dev/null 2>&1 || true
    sleep 4
    adb pull /data/misc/perfetto-traces/gate.pftrace stress/trace/ > /dev/null 2>&1 || echo "GATE_TRACE no trace: $(head -3 stress/trace/perfetto-gate.txt)"
  elif [ "$C" = "dev.asfakul.yumeiro.UpdateFeedDeviceTest" ]; then
    # FE-Q1 / FE-Q2: a installs extensions and saves homes; b = relaunch; c = after an in-place update
    # (`adb install -r` of the same debug APK: new code path, data kept, like a store update).
    P=dev.asfakul.yumeiro.debug
    timeout 600 adb shell am instrument -w -r -e class "$C#a_installAndSaveHomes" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    adb shell am force-stop $P; sleep 2
    timeout 400 adb shell am instrument -w -r -e class "$C#b_relaunchBeforeUpdate" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1
    adb shell am force-stop $P; sleep 2
    mkdir -p stress/updfeed
    adb shell run-as $P ls -laR files/extensions > stress/updfeed/ext-before.txt 2>&1 || true
    adb shell dumpsys package dexopt > stress/updfeed/dexopt-before.txt 2>&1 || true
    APK=$(ls app/build/outputs/apk/${FLAVOR,,}/debug/*.apk 2>/dev/null | grep -v -- '-androidTest' | head -1)
    echo "UPDFEED_SCRIPT update: $(adb install -r "$APK" 2>&1 | tail -1) ($APK)"
    sleep 3
    adb shell run-as $P ls -laR files/extensions > stress/updfeed/ext-after-install.txt 2>&1 || true
    adb shell dumpsys package dexopt > stress/updfeed/dexopt-after-install.txt 2>&1 || true
    timeout 400 adb shell am instrument -w -r -e class "$C#c_afterUpdate" $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-c.txt" 2>&1; RC=$?
    adb shell run-as $P ls -laR files/extensions > stress/updfeed/ext-after.txt 2>&1 || true
    adb shell dumpsys package dexopt > stress/updfeed/dexopt-after.txt 2>&1 || true
    grep -a "UPDFEED" logcat.txt | sed 's/^.*UPDFEED/UPDFEED/' | tee stress/updfeed/numbers.txt
    cat "inst-$C-a.txt" "inst-$C-b.txt" "inst-$C-c.txt" > "inst-$C.txt"
    # All three phases must report OK.
    [ "$(grep -c '^OK (' "inst-$C.txt")" -eq 3 ] || sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"
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
  elif [ "$C" = "dev.asfakul.yumeiro.Bug1YumixRestoreDeviceTest" ]; then
    # BUG-1 follow-through: phase a records Yumix progress and kills its own process ("Process
    # crashed" by design); phase b, a new process, checks Home Jump back in, Yumix Continue and the banner.
    P=dev.asfakul.yumeiro.debug
    timeout 400 adb shell am instrument -w -r -e class "$C#a_recordThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuBug1YumixScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt")"
    timeout 400 adb shell am instrument -w -r -e class "$C#b_newProcessShowsItEverywhere" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  elif [ "$C" = "dev.asfakul.yumeiro.LibraryPersistDeviceTest" ]; then
    # G5 (LB-8, LB-3): phase a saves likes and playlists, damages one entry, kills its own process
    # ("Process crashed" by design); phase b, a new process, checks what came back.
    P=dev.asfakul.yumeiro.debug
    timeout 300 adb shell am instrument -w -r -e class "$C#a_saveThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuLibPersistScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt")"
    timeout 300 adb shell am instrument -w -r -e class "$C#b_newProcessKeepsOrderAndGoodPlaylists" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    if grep -q 'FAILURES' "inst-$C-a.txt"; then cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; else cp "inst-$C-b.txt" "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.LibraryEmptyProfileDeviceTest" ]; then
    # G5 from an empty profile: pm clear, then phase a likes, plays a queue (pause, seek, screen
    # destroyed mid-queue) and kills its own process ("Process crashed" by design); phase b, a new
    # process, checks counts, order, play time and Jump back in.
    P=dev.asfakul.yumeiro.debug
    adb shell pm clear $P > /dev/null 2>&1
    for PERM in READ_MEDIA_AUDIO READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1; done
    timeout 400 adb shell am instrument -w -r -e class "$C#a_emptyProfileLikeAddPlayThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuEmptyProfileScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt")"
    timeout 300 adb shell am instrument -w -r -e class "$C#b_newProcessShowsHistoryRightAway" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    if grep -q 'FAILURES' "inst-$C-a.txt"; then cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; else cp "inst-$C-b.txt" "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.QueuePersistenceDeviceTest" ]; then
    # G6-QP: pm clear, then phase a queues five songs, skips to song 2 and kills its own process
    # ("Process crashed" by design); phase b, a new process, plays Home Continue and checks all five.
    P=dev.asfakul.yumeiro.debug
    adb shell pm clear $P > /dev/null 2>&1
    for PERM in READ_MEDIA_AUDIO READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1; done
    timeout 300 adb shell am instrument -w -r -e class "$C#a_queueFiveThenDie" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    sleep 2
    echo "YuQueuePersistScript after phase a: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-a.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt")"
    timeout 300 adb shell am instrument -w -r -e class "$C#b_reopenContinueBringsAllFive" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1; RC=$?
    if grep -q 'FAILURES' "inst-$C-a.txt"; then cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; else cp "inst-$C-b.txt" "inst-$C.txt"; fi
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
    # G10 "revoked while running": Android kills the process on revoke; the app must come back
    # clean (no crash on relaunch) and read the new state. One pass per runtime permission.
    for PERM in POST_NOTIFICATIONS READ_MEDIA_VIDEO; do
      WAS=$(adb shell dumpsys package $P | grep -m1 "android.permission.$PERM: granted=true" | wc -l | tr -d ' \r')
      adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 8
      B=$(adb shell pidof $P | tr -d '\r'); adb shell pm revoke $P android.permission.$PERM 2>&1 | sed 's/^/YuPermScript revoke: /'; sleep 3
      A=$(adb shell pidof $P | tr -d '\r'); adb logcat -b crash -c 2>/dev/null || true
      adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 10
      R2=$(adb shell pidof $P | tr -d '\r'); FX=$(adb logcat -d -b crash 2>/dev/null | grep -c "$P" | tr -d '\r')
      echo "YuPermScript PERM_RUNNING perm=$PERM wasGranted=$WAS pidBefore=$B pidAfterRevoke='${A}' relaunchPid='${R2}' crashLines=$FX"
      [ "$WAS" = "1" ] && adb shell pm grant $P android.permission.$PERM || true
      adb shell am force-stop $P
    done
    # FINAL-23 (run 44659067541): the next class in the chunk (PlaybackSessionDeviceTest) found the
    # notification permission off and Android's permission dialog over MainActivity ("No compose
    # hierarchies found"). Hand the next class the state every class starts from: first run done,
    # notifications and media allowed, no "asked before" flags.
    for PERM in POST_NOTIFICATIONS READ_MEDIA_AUDIO READ_MEDIA_VIDEO; do
      adb shell pm clear-permission-flags $P android.permission.$PERM user-set user-fixed > /dev/null 2>&1
      adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1 || true
    done
    echo "YuPermScript restored for the next class: $(adb shell dumpsys package $P | grep -m1 'POST_NOTIFICATIONS: granted' | tr -d '\r')"
    if grep -q '^OK (' "inst-$C-a.txt"; then cp "inst-$C-b.txt" "inst-$C.txt"; else cat "inst-$C-a.txt" "inst-$C-b.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.MusicAccessDeviceTest" ]; then
    # G6-SP1 (Android 13+): a song pushed by the shell (not owned by the app); phase a gets past
    # first run with everything allowed; the script takes music access away (videos stay) and
    # phase b, a new process, expects the "Music access is off" card and Android's dialog from it;
    # the script allows music and phase c expects no card and the song listed.
    P=dev.asfakul.yumeiro.debug
    SONG=/sdcard/Music/YuAccess/yu_access_song.m4a
    adb shell mkdir -p /sdcard/Music/YuAccess
    adb push app/src/androidTest/assets/yt_audio_only.m4a $SONG > /dev/null 2>&1
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$SONG 2>&1 | tail -1 | sed 's/^/YuMusicAccessScript scan: /'
    timeout 300 adb shell am instrument -w -r -e class "$C#a_prepare" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    adb shell am force-stop $P
    adb shell pm grant $P android.permission.READ_MEDIA_VIDEO 2>&1 | sed 's/^/YuMusicAccessScript grant video: /'
    adb shell pm revoke $P android.permission.READ_MEDIA_AUDIO 2>&1 | sed 's/^/YuMusicAccessScript revoke audio: /'
    adb shell pm clear-permission-flags $P android.permission.READ_MEDIA_AUDIO user-set user-fixed 2>&1 | sed 's/^/YuMusicAccessScript flags: /'
    echo "YuMusicAccessScript before b: $(adb shell dumpsys package $P | grep -m1 'READ_MEDIA_AUDIO: granted' | tr -d '\r')"
    timeout 400 adb shell am instrument -w -r -e class "$C#b_videosOnly" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1
    adb shell am force-stop $P
    adb shell pm grant $P android.permission.READ_MEDIA_AUDIO 2>&1 | sed 's/^/YuMusicAccessScript grant audio: /'
    timeout 400 adb shell am instrument -w -r -e class "$C#c_audioAllowed" -e phase c $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-c.txt" 2>&1; RC=$?
    adb shell rm -rf /sdcard/Music/YuAccess
    if grep -q '^OK (' "inst-$C-a.txt" && grep -q '^OK (' "inst-$C-b.txt"; then cp "inst-$C-c.txt" "inst-$C.txt"; else cat "inst-$C-a.txt" "inst-$C-b.txt" "inst-$C-c.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.RestoreAfterClearDeviceTest" ]; then
    # G9 (NBUG-7): pm clear; phase a installs CloudStream extensions and a JS source from a local
    # repo and makes a default backup (no extension files) in files/; the script copies it out, runs
    # the real "Clear all data" (pm clear) and puts the backup back; phase b stages the restore and
    # kills its own process ("Process crashed" by design); phase c, a new process, expects every
    # source back without restarting the app.
    P=dev.asfakul.yumeiro.debug
    adb shell pm clear $P > /dev/null 2>&1
    for PERM in READ_MEDIA_AUDIO READ_MEDIA_VIDEO READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1; done
    timeout 400 adb shell am instrument -w -r -e class "$C#a_installThenBackUp" -e phase a $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    adb shell am force-stop $P
    adb exec-out run-as $P cat files/nbug7.zip > nbug7.zip 2>/dev/null
    echo "YuRestoreClearScript backup copied out: $(wc -c < nbug7.zip) bytes"
    adb shell pm clear $P 2>&1 | sed 's/^/YuRestoreClearScript pm clear: /'
    for PERM in READ_MEDIA_AUDIO READ_MEDIA_VIDEO READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1; done
    adb push nbug7.zip /data/local/tmp/nbug7.zip > /dev/null 2>&1
    adb shell "run-as $P mkdir -p files; cat /data/local/tmp/nbug7.zip | run-as $P sh -c 'cat > files/nbug7.zip'"
    echo "YuRestoreClearScript backup put back: $(adb shell run-as $P ls -l files/nbug7.zip 2>&1 | tr -d '\r')"
    adb shell rm -f /data/local/tmp/nbug7.zip
    timeout 300 adb shell am instrument -w -r -e class "$C#b_stageRestoreThenDie" -e phase b $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-b.txt" 2>&1
    sleep 2
    echo "YuRestoreClearScript after phase b: pidof='$(adb shell pidof $P | tr -d '\r')' $(grep -c 'Process crashed' "inst-$C-b.txt") crash lines; failures in a: $(grep -c 'FAILURES' "inst-$C-a.txt"), in b: $(grep -c 'FAILURES' "inst-$C-b.txt")"
    timeout 600 adb shell am instrument -w -r -e class "$C#c_everySourceComesBack" -e phase c $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-c.txt" 2>&1; RC=$?
    if grep -q '^OK (' "inst-$C-a.txt" && ! grep -q 'FAILURES' "inst-$C-b.txt"; then cp "inst-$C-c.txt" "inst-$C.txt"; else cat "inst-$C-a.txt" "inst-$C-b.txt" "inst-$C-c.txt" > "inst-$C.txt"; sed -i 's/^OK (/PHASE_OK (/' "inst-$C.txt"; fi
  elif [ "$C" = "dev.asfakul.yumeiro.CrashUiDeviceTest" ]; then
    # G12 X6: phase a really crashes the app (its own result is "Process crashed" by design).
    P=dev.asfakul.yumeiro.debug
    timeout 120 adb shell am instrument -w -r -e class "$C#a_crashForReal" -e crash 1 $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C-a.txt" 2>&1
    echo "YuCrashUiScript phase a: $(grep -cE 'Process crashed|IllegalStateException: YuCrashUi' "inst-$C-a.txt") crash lines · pidof='$(adb shell pidof $P | tr -d '\r')'"
    timeout 600 adb shell am instrument -w -r -e class "$C#b_readShareAndDelete" -e crashed 1 $P.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  else
  timeout 900 adb shell am instrument -w -r -e class "$C" -e rounds $R -e hprof $H -e gate1Baseline "$GB" dev.asfakul.yumeiro.debug.test/androidx.test.runner.AndroidJUnitRunner > "inst-$C.txt" 2>&1; RC=$?
  fi
  grep -E "INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run" "inst-$C.txt" | cut -c1-400 | tail -150
  if [ $RC -eq 124 ]; then echo "TIMEOUT in $C (last test started above)"; echo "TESTS_FAILED" > failed.txt; adb shell am force-stop dev.asfakul.yumeiro.debug || true; fi
  # Only this class's lines; crash blocks (AndroidRuntime) and foreground-service decisions included.
  tail -n +$((L0 + 1)) logcat.txt | grep -E "YuStress|YuResume|YuChannel|YuYtQuality|YuWidgetTest|YuDecoderFault|YuAnime4k|YuRtl|YuA11y|YuHeat|YuClosed|YuCrashUi|YuLibraryTest|YuEmptyProfile|YuDownloadTest|YuSettingsWalk|YuBackupTest|YuRestoreClear|YuSharedFile|YuNbug9|YuBackdropTest|YuPlayerFx|YuNotifyFx|YuPermFlow|YuPermScript|YuNetState|YuDialogs|YuLowStorage|YuMessages|YuBgFx|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|YuStartup|MTDeviceTest|ANR in|FATAL|OutOfMemory|AndroidRuntime|MediaSessionService|MediaNotification|ForegroundService|startForeground|ActiveServices" | cut -c1-300 | tail -260 || true
  # Full stack of every failing test (the line filter above keeps only its first line).
  awk '/^INSTRUMENTATION_STATUS: stack=/{p=1} /^INSTRUMENTATION_STATUS: (test|class|current|id|numtests|stream)=|^INSTRUMENTATION_STATUS_CODE/{p=0} p' "inst-$C.txt" | grep -vE "^\s+at (org\.junit|java\.lang\.reflect|jdk\.internal|androidx\.test\.internal)" | head -80
  grep -qE "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|Process crashed" "inst-$C.txt" && echo "TESTS_FAILED" > failed.txt
  grep -qE "^OK \(" "inst-$C.txt" || echo "TESTS_FAILED" > failed.txt
  # GATE-1-ANR-T8: an input ANR under instrumentation never reaches ApplicationExitInfo or
  # processesInErrorState; AMS ends the run with shortMsg=keyDispatchingTimedOut (AMS.inputDispatchingTimedOut).
  grep -qE "shortMsg=(keyDispatchingTimedOut|Process crashed)|longMsg=Input dispatching timed out" "inst-$C.txt" && { echo "GATE_ANR_OR_CRASH in $C: $(grep -hE 'shortMsg=|longMsg=' "inst-$C.txt" | head -2 | tr '\n' ' ')"; echo "TESTS_FAILED" > failed.txt; }
  if grep -qE "^OK \(" "inst-$C.txt" && [ $RC -ne 124 ]; then V=PASS; else V=FAIL; fi
  PL=""; [ "$PASSES" = "true" ] && PL=" pass=P$N"
  # Ignored tests (status -3) and assumption failures (status -4, e.g. "needs API 26") are skips; the final
  # test reports them, never hides them (FINAL-9: -4 was not counted, so a skipped test read as a pass).
  SK=$(grep -cE "INSTRUMENTATION_STATUS_CODE: -(3|4).?$" "inst-$C.txt" 2>/dev/null | tr -d ' '); OKN=$(sed -n 's/^OK (\([0-9]*\) test.*/\1/p' "inst-$C.txt" | tail -1)
  echo "REPEAT_RESULT $C run=$N/$REPEAT$PL $V tests=${OKN:-?} skipped=${SK:-0}" | tee -a repeat-results.txt
  cp "inst-$C.txt" "inst-$C-run$N.txt" 2>/dev/null || true
  # Final test §10: keep each class's screenshots (FirstRun.shot) before the next class clears the
  # app data, so the images in docs/assets/final come from the final passes themselves.
  SHOTS=/sdcard/Android/data/dev.asfakul.yumeiro.debug/files/shots
  if timeout 30 adb shell "[ -d $SHOTS ]" > /dev/null 2>&1; then
    mkdir -p "stress/shots-P$N/${C##*.}"
    timeout 120 adb pull "$SHOTS/." "stress/shots-P$N/${C##*.}/" > /dev/null 2>&1 || true
    timeout 30 adb shell rm -rf "$SHOTS" > /dev/null 2>&1 || true
  fi
  echo "::endgroup::"
done
done
mkdir -p stress && timeout 120 adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/stress stress/ || true
timeout 120 adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/shots stress/ || true
timeout 120 adb pull /sdcard/Android/data/dev.asfakul.yumeiro.debug/files/feedpos stress/ || true
timeout 60 adb shell dumpsys meminfo dev.asfakul.yumeiro.debug > stress/meminfo.txt || true
timeout 60 adb shell dumpsys activity exit-info dev.asfakul.yumeiro.debug > stress/exit-info.txt || true
timeout 60 adb shell ls /data/anr > stress/anr-list.txt 2>&1 || true
grep -E "YuStress|YuResume|YuChannel|YuYtQuality|YuWidgetTest|YuDecoderFault|YuAnime4k|YuRtl|YuA11y|YuHeat|YuClosed|YuCrashUi|YuLibraryTest|YuEmptyProfile|YuDownloadTest|YuBackupTest|YuFeedPos|YuQualityTest|YuPinTest|YuExtTest|YuLocalPlayer|YuStart|YuGate|YuStall|YuMemory|YuPlayerTest|YuSurfaceTest|YuHang|YuSession|MTDeviceTest|StrictMode|ANR in|FATAL|OutOfMemory|lowmemorykiller" logcat.txt > stress/logcat-key.txt || true
cp inst-*.txt repeat-results.txt stress/ 2>/dev/null || true
echo "::group::evidence"; tail -n 400 stress/logcat-key.txt; cat stress/exit-info.txt | head -80; cat stress/anr-list.txt; for f in $(find stress -type f -name '*report*.txt' | head -20); do echo "== $f"; head -c 6000 "$f"; done; echo "::endgroup::"
# FINAL-15: a lost adb link is a failed run, never a quiet partial pass.
if [ "$ADB_LOST" = 1 ]; then echo "HARNESS adb lost: the run is incomplete"; exit 1; fi
