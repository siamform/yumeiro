#!/usr/bin/env bash
# G12 X10: upgrade from a published release (default v1.8.0) to this branch, on an emulator.
# Runs inside reactivecircus/android-emulator-runner (see .github/workflows/upgrade-check.yml).
#   1. install the published APK, let ITS code create data: first run (guide answered), a local clip
#      played part of the way (history + progress), the app sent home so it saves;
#   2. snapshot /data/data/<pkg>/{shared_prefs,files} as root (google_apis images allow adb root);
#   3. install this branch's release APK over it (same release key, higher versionCode);
#   4. start it, let it settle, snapshot again; compare (scripts/upgrade_compare.py) and check the
#      screen: no welcome guide again, the clip is still in history, no crash in our process.
# Output: upgrade/ (snapshots, ui dumps, screenshots, logcat) and UPGRADE_* lines in the log.
set -u
P=dev.asfakul.yumeiro
OLD=${OLD_APK:-old.apk}
NEW=${NEW_APK:-new.apk}
mkdir -p upgrade
adb logcat -c || true
adb logcat -v threadtime > upgrade/logcat.txt 2>&1 &
LOGPID=$!

note() { echo "UPGRADE $*"; }
dump() { # $1 name → upgrade/ui-$1.xml, prints the visible texts
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "upgrade/ui-$1.xml" > /dev/null 2>&1 || true
  adb exec-out screencap -p > "upgrade/shot-$1.png" 2>/dev/null || true
  python3 - "upgrade/ui-$1.xml" <<'PY' 2>/dev/null || true
import sys, re
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
texts = [x for x in re.findall(r'(?:text|content-desc)="([^"]+)"', t) if x.strip()]
print("  texts:", " | ".join(texts[:40]))
PY
}
tap_text() { # tap the first node whose text or description is exactly one of the arguments
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml upgrade/tap.xml > /dev/null 2>&1 || return 1
  XY=$(python3 - upgrade/tap.xml "$@" <<'PY'
import sys, re
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
want = set(sys.argv[2:])
for m in re.finditer(r'<node [^>]*>', t):
    n = m.group(0)
    tx = re.search(r' text="([^"]*)"', n); cd = re.search(r'content-desc="([^"]*)"', n)
    if (tx and tx.group(1) in want) or (cd and cd.group(1) in want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  [ -n "$XY" ] || return 1
  adb shell input tap $XY; note "tapped [$*] at $XY"; return 0
}
settle_first_run() { # answer the welcome guide / tour / permission or update prompts, up to $1 s
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    tap_text "Remind me later" "Skip" "Skip for now" "Not now" "Later" "Maybe later" "Maybe Later" "Close" "Got it" "Allow" "Done" "No thanks" || true
    sleep 3
  done
}
snapshot() { # $1 name → upgrade/$1.tar of prefs + files (no caches), and a listing
  adb shell "cd /data/data/$P && ls -laR shared_prefs files 2>/dev/null" > "upgrade/$1-ls.txt" 2>&1 || true
  adb exec-out "cd /data/data/$P && tar cf - shared_prefs files 2>/dev/null" > "upgrade/$1.tar" || true
  note "snapshot $1: $(tar tf "upgrade/$1.tar" 2>/dev/null | grep -vc '/$') files"
}

adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
note "root: $(adb shell id -u)"
adb shell settings put global package_verifier_enable 0 > /dev/null 2>&1 || true

# ---- 1. the published release creates its data -------------------------------------------------
note "install old: $(adb install "$OLD" 2>&1 | tail -1)"
note "old version: $(adb shell dumpsys package $P | grep -m1 versionName | tr -d ' ')"
for PERM in POST_NOTIFICATIONS READ_MEDIA_VIDEO READ_MEDIA_AUDIO; do adb shell pm grant $P android.permission.$PERM > /dev/null 2>&1 || true; done
adb push upgrade-clip.mp4 /sdcard/Movies/upgrade-clip.mp4 > /dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 12; dump old-start
settle_first_run 45
dump old-tabs
# Precondition (run 43869944264 lesson): the old app must be past its update dialog and welcome guide,
# otherwise "guide again" after the upgrade means nothing. Report a harness error, not a product result.
PRE_OK=1
# Run 43878778019: an extension pop-up ("Maybe Later") covered the tabs and still read as ready.
grep -qE 'text="(Skip|Skip for now|Remind me later|Maybe Later)"' upgrade/ui-old-tabs.xml 2>/dev/null && PRE_OK=0
note "old app past update dialog and welcome guide: $PRE_OK"
# A local clip through the app's own VIEW handler; the player records history and progress.
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Movies/upgrade-clip.mp4 -t video/mp4 -p $P > /dev/null 2>&1
sleep 12
# Run 43878778019: an extension's support pop-up ("Maybe Later") covered the player in v1.8.0
# (no Quiet mode then); answer pop-ups as a user would, then look again.
for i in 1 2 3; do tap_text "Maybe Later" "Maybe later" "Not now" "Close" "Got it" > /dev/null || true; sleep 3; done
sleep 4; dump old-player
PLAYED=0; grep -qi 'upgrade-clip\|upgrade clip' upgrade/ui-old-player.xml 2>/dev/null && PLAYED=1
grep -q 'YuPlayer\|ExoPlayerImpl.*Init' upgrade/logcat.txt 2>/dev/null && PLAYED=1
note "old app opened the clip: $PLAYED"
adb shell input keyevent 4; sleep 3; adb shell input keyevent 3; sleep 8
dump old-home-after
adb shell am force-stop $P; sleep 2
snapshot before

# ---- 2. upgrade in place ------------------------------------------------------------------------
R=$(adb install -r "$NEW" 2>&1 | tail -1); note "install new over old: $R"
case "$R" in *Success*) ;; *) echo "UPGRADE_RESULT FAIL install: $R"; kill $LOGPID 2>/dev/null; exit 1;; esac
note "new version: $(adb shell dumpsys package $P | grep -m1 versionName | tr -d ' ')"
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 20; dump new-start
GUIDE_AGAIN=0; grep -qE 'text="(Skip|Skip for now)"' upgrade/ui-new-start.xml 2>/dev/null && GUIDE_AGAIN=1
note "welcome guide shown again after upgrade: $GUIDE_AGAIN"
# History tab or Home "Continue": the clip name must still be listed.
# Run 43878778019: the new app's "Keep notifications on time" guide covered History; close prompts first.
for i in 1 2 3; do tap_text "Close" "Maybe Later" "Maybe later" "Not now" "Got it" > /dev/null || true; sleep 3; done
tap_text "History" > /dev/null || true; sleep 4; dump new-history
CLIP_SEEN=0; grep -rqi "upgrade-clip\|upgrade clip" upgrade/ui-new-start.xml upgrade/ui-new-history.xml 2>/dev/null && CLIP_SEEN=1
note "clip still listed after upgrade: $CLIP_SEEN"
adb shell input keyevent 3; sleep 6
adb shell am force-stop $P; sleep 2
snapshot after
kill $LOGPID 2>/dev/null || true
FATAL=$(grep -cE "FATAL EXCEPTION|ANR in $P" upgrade/logcat.txt || true)
note "FATAL/ANR lines in logcat: $FATAL"
python3 scripts/upgrade_compare.py upgrade/before.tar upgrade/after.tar | tee upgrade/compare.txt
CMP=${PIPESTATUS[0]}
V=PASS
[ "$CMP" = 0 ] || V=FAIL
[ "$PRE_OK" = 1 ] || V=ERROR_PRECONDITION
[ "$GUIDE_AGAIN" = 0 ] || V=FAIL
[ "${FATAL:-0}" = 0 ] || V=FAIL
# Without the clip played in the old app, history/resume parity was not exercised: not a PASS.
[ "$V" = PASS ] && [ "$PLAYED" = 0 ] && V=INCOMPLETE_NOT_PLAYED
[ "$V" = PASS ] && [ "$CLIP_SEEN" = 0 ] && V=FAIL_CLIP_NOT_LISTED
echo "UPGRADE_RESULT $V compare=$CMP pre_ok=$PRE_OK played=$PLAYED guide_again=$GUIDE_AGAIN clip_seen=$CLIP_SEEN fatal=$FATAL"
[ "$V" = PASS ]
