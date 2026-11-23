#!/usr/bin/env bash
# R19 field probe: run a published (or freshly built) APK exactly as a user gets it, on an emulator with
# real internet, and record what happens in the first minutes: every network connection (emulator
# -tcpdump, read by scripts/field_probe_report.py), logcat, UI text dumps and screenshots on a timeline.
# No test harness, no debug build: this is the user's path. Runs inside reactivecircus/android-emulator-runner.
#
# Env: NEW_APK (required), OLD_APK (optional: install + first run of the old one first = update scenario),
#      STEPS (scenario, ';'-separated: skip | tour | wait N | tap TEXT | find TEXT | top | dismiss | rec NAME | recpull NAME | back | home | shot NAME | gesturenav | buttonnav | swipeback | edgeback),
#      NET (none | edge | umts | lte), OUT (default probe).
set -u
P=dev.asfakul.yumeiro
OUT=${OUT:-probe}
mkdir -p "$OUT"
T0=$(date +%s%3N)
echo "PROBE epoch_ms=$T0" > "$OUT/timeline.txt"
ms() { echo $(( $(date +%s%3N) - T0 )); }
note() { echo "PROBE t=$(ms) $*" | tee -a "$OUT/timeline.txt"; }

dump() { # $1 label: UI texts + screenshot
  local n; n=$(printf '%06d' "$(ms)")
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$OUT/ui-$n-$1.xml" > /dev/null 2>&1 || true
  adb exec-out screencap -p > "$OUT/shot-$n-$1.png" 2>/dev/null || true
  python3 - "$OUT/ui-$n-$1.xml" "$1" "$(ms)" <<'PY' 2>/dev/null | tee -a "$OUT/timeline.txt" || true
import sys, re
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
texts = [x for x in re.findall(r'(?:text|content-desc)="([^"]+)"', t) if x.strip()]
print(f"PROBE t={sys.argv[3]} UI[{sys.argv[2]}] n={len(texts)} :: " + " | ".join(texts[:60]))
PY
}

tap_text() { # tap the first node whose text or content-desc equals (or, with ~ prefix, contains) an argument
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml "$OUT/tap.xml" > /dev/null 2>&1 || return 1
  XY=$(python3 - "$OUT/tap.xml" "$@" <<'PY'
import sys, re, html
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
want = sys.argv[2:]
# Exact text first, in the order asked; "~x" (contains) only when nothing matched exactly, so "~Skip" never
# picks the sentence "You can skip and start using the app." over the "Skip for now" button.
nodes = []
for m in re.finditer(r'<node [^>]*>', t):
    n = m.group(0)
    tx = re.search(r' text="([^"]*)"', n); cd = re.search(r'content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if b: nodes.append((html.unescape(tx.group(1)) if tx else "", html.unescape(cd.group(1)) if cd else "", tuple(map(int, b.groups()))))
def find():
    for w in [w for w in want if not w.startswith("~")]:
        for tx, cd, b in nodes:
            if tx == w or cd == w: return b
    for w in [w[1:].lower() for w in want if w.startswith("~")]:
        for tx, cd, b in nodes:
            if (w in tx.lower() and len(tx) <= len(w) + 12) or (w in cd.lower() and len(cd) <= len(w) + 12): return b
    return None
b = find()
if b:
    x1, y1, x2, y2 = b; print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  [ -n "$XY" ] || return 1
  adb shell input tap $XY; note "tapped [$*] at $XY"; return 0
}

answer_system() { # permission dialogs only (not the app's own guide)
  tap_text "Allow" "While using the app" "Allow all" "Allow access to media only" > /dev/null 2>&1 || true
}

launch() {
  echo "PROBE launch_epoch_ms=$(date +%s%3N) $1" >> "$OUT/timeline.txt"
  adb shell monkey -p "$P" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  note "launched $1"
}

run_steps() {
  IFS=';' read -ra S <<< "$STEPS"
  for s in "${S[@]}"; do
    s=$(echo "$s" | sed 's/^ *//;s/ *$//'); [ -n "$s" ] || continue
    case "$s" in
      skip) # answer the welcome guide by skipping everything, like an impatient user
        for i in $(seq 1 12); do answer_system; tap_text "Skip" "Skip for now" "Skip tour" "~Skip" "Not now" "Maybe later" "Later" "Remind me later" "No thanks" "Close" "All set" "✕" || true; sleep 2; done ;;
      tour) # follow the guide and tour with Next / Continue like a careful new user
        for i in $(seq 1 30); do answer_system; tap_text "Next" "Continue" "Get started" "Start tour" "~Take the tour" "Finish" "Done" "Got it" || true; sleep 3; done ;;
      wait*) n=${s#wait }; end=$(( $(date +%s) + n ))
        while [ "$(date +%s)" -lt "$end" ]; do answer_system; dump "wait"; sleep 10; done ;;
      tap*) t=${s#tap }; tap_text "$t" "~$t" || note "tap miss [$t]"; sleep 1; dump "after-tap" ;;
      find*) # scroll the page up (up to 6 swipes) until TEXT is on screen, then tap it
        t=${s#find }; ok=0
        for i in 1 2 3 4 5 6 7; do tap_text "$t" "~$t" && { ok=1; break; }; adb shell input swipe 540 1500 540 700 300; sleep 1; done
        # not below: the page may have opened scrolled down (Settings keeps its place), so look above too
        [ $ok = 1 ] || for i in 1 2 3 4 5 6 7 8 9 10 11 12; do tap_text "$t" "~$t" && { ok=1; break; }; adb shell input swipe 540 700 540 1500 300; sleep 1; done
        [ $ok = 1 ] || note "find miss [$t]"; sleep 1; dump "after-find" ;;
      recpull*) r=${s#recpull }; sleep 5; adb pull "/sdcard/rec-$r.mp4" "$OUT/rec-$r.mp4" > /dev/null 2>&1 && note "pulled rec-$r.mp4" || note "rec miss [$r]" ;;
      rec*) # rec NAME: record the screen for 5 s from now (runs while the next steps happen)
        r=${s#rec }; adb shell "screenrecord --time-limit 5 --bit-rate 12000000 /sdcard/rec-$r.mp4" > /dev/null 2>&1 &
        sleep 0.6; note "recording [$r]" ;;
      dismiss) tap_text "Close" "All set" "✕" "Not now" "Later" > /dev/null 2>&1 && note "dismissed a popup" || true; sleep 1 ;;
      top) for i in 1 2 3 4; do adb shell input swipe 540 600 540 1600 200; done; sleep 1 ;;
      back) adb shell input keyevent 4; note "key BACK"; sleep 1; dump "after-back" ;;
      home) adb shell input keyevent 3; note "key HOME" ;;
      shot*) dump "${s#shot }" ;;
      gfxreset) adb shell dumpsys gfxinfo "$P" reset > /dev/null 2>&1; note "gfxinfo reset" ;;
      gfx*) g=${s#gfx }; adb shell dumpsys gfxinfo "$P" > "$OUT/gfx-$g.txt" 2>&1; note "gfx [$g] $(grep -m1 'Total frames rendered' "$OUT/gfx-$g.txt")" ;;
      gesturenav) adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural > /dev/null 2>&1; sleep 3
        note "navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')" ;;
      buttonnav) adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton > /dev/null 2>&1; sleep 3
        note "navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')" ;;
      swipeback) # a quick real Back swipe from the left edge (no hold), like most people do it
        W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); WX=${W%x*}; WY=${W#*x}
        adb shell input swipe 2 $(( WY / 2 )) $(( WX * 2 / 3 )) $(( WY / 2 )) 180; note "swipe back"; sleep 1 ;;
      edgeback) # a real Back gesture from the left edge, held a third of the way across, screenshot, released
        W=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); WX=${W%x*}; WY=${W#*x}; Y=$(( WY / 2 )); X=$(( WX / 3 ))
        adb shell input motionevent DOWN 2 $Y
        for x in 20 60 120 $(( X / 2 )) $(( X * 3 / 4 )) $X; do adb shell input motionevent MOVE $x $Y; done
        sleep 0.4; adb exec-out screencap -p > "$OUT/shot-$(ms)-edgeback-held.png"; note "edge back held at x=$X"
        adb shell input motionevent UP $X $Y; sleep 1.2; dump "after-edgeback" ;;
      *) note "unknown step [$s]" ;;
    esac
  done
}

# Network shape (the emulator's own throttle: delay + bandwidth), applied before anything starts.
case "${NET:-none}" in
  none) ;;
  *) adb emu network delay "${NET}" > /dev/null 2>&1; adb emu network speed "${NET}" > /dev/null 2>&1; note "network shaped: $NET" ;;
esac
# The emulator's -tcpdump sees the first NIC only; with Wi-Fi on, app traffic went around it (run 45112282639:
# 97 packets in 150 s, none from the app). Mobile data only, so every connection is in the capture.
[ "${WIFI:-off}" = "off" ] && { adb shell svc wifi disable > /dev/null 2>&1; adb shell svc data enable > /dev/null 2>&1; sleep 3; note "wifi off, mobile data on"; }
adb logcat -c
adb logcat -v threadtime > "$OUT/logcat.txt" 2>&1 &
LOGCAT=$!

if [ -n "${OLD_APK:-}" ]; then
  note "install old"
  adb install -r -g "$OLD_APK" > "$OUT/install-old.txt" 2>&1; cat "$OUT/install-old.txt"
  launch old
  STEPS_OLD=${STEPS_OLD:-"skip;wait 40"}
  SAVE=$STEPS; STEPS=$STEPS_OLD; run_steps; STEPS=$SAVE
  adb shell input keyevent 3; sleep 3
  adb shell am force-stop "$P"
  note "old run done; installing new over it (update)"
fi

note "install new"
adb install -r "$NEW_APK" > "$OUT/install-new.txt" 2>&1; cat "$OUT/install-new.txt"
adb shell dumpsys package "$P" | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" | tee -a "$OUT/timeline.txt"
launch new
sleep 2; dump "start"
run_steps
dump "end"

adb shell dumpsys meminfo "$P" > "$OUT/meminfo.txt" 2>&1 || true
adb shell dumpsys gfxinfo "$P" > "$OUT/gfxinfo.txt" 2>&1 || true
adb shell pidof "$P" | tee -a "$OUT/timeline.txt"
kill $LOGCAT 2>/dev/null || true
grep -E "FATAL EXCEPTION|ANR in $P" "$OUT/logcat.txt" | head -20 | tee -a "$OUT/timeline.txt" || true
note "done"
