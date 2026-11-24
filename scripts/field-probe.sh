#!/usr/bin/env bash
# R19 field probe: run a published (or freshly built) APK exactly as a user gets it, on an emulator with
# real internet, and record what happens in the first minutes: every network connection (emulator
# -tcpdump, read by scripts/field_probe_report.py), logcat, UI text dumps and screenshots on a timeline.
# No test harness, no debug build: this is the user's path. Runs inside reactivecircus/android-emulator-runner.
#
# Env: NEW_APK (required), OLD_APK (optional: install + first run of the old one first = update scenario),
#      STEPS (scenario, ';'-separated: skip | tour | wait N | tap TEXT | xy X Y | find TEXT | field [N] | type TEXT | top | dismiss | rec NAME | recpull NAME | back | home | shot NAME | gesturenav | buttonnav | swipeback | edgeback | anim N | quick X Y | rec NAME SECS | seal | tgphone | tgcode [MIN] | tgpass [MIN] | secret K [MIN] (sealed only) | key N | paste TEXT | sh CMD),
#      NET (none | edge | umts | lte), OUT (default probe).
set -u
P=dev.asfakul.yumeiro
OUT=${OUT:-probe}
mkdir -p "$OUT"
T0=$(date +%s%3N)
echo "PROBE epoch_ms=$T0" > "$OUT/timeline.txt"
ms() { echo $(( $(date +%s%3N) - T0 )); }
# SEALED=1 (a scenario that starts with "seal"): nothing from the device reaches the public job log; the timeline,
# shots and logcat are encrypted before upload (workflow "Seal results"). Used for signed-in accounts.
tl() { if [ "${SEALED:-0}" = 1 ]; then cat >> "$OUT/timeline.txt"; else tee -a "$OUT/timeline.txt"; fi; }
# Public, content-free progress marks for the agent (a commit status), e.g. "waiting for the code".
signal() { [ -n "${GH_TOKEN:-}" ] && [ -n "${GITHUB_SHA:-}" ] && gh api -X POST "repos/$GITHUB_REPOSITORY/statuses/$GITHUB_SHA" \
  -f state="$1" -f context="field-probe/$GITHUB_RUN_ID" -f description="$2" > /dev/null 2>&1; echo "PROBE signal $1 $2"; }
# A secret the agent sends during the run: a later dispatch of this workflow with steps "code <blob>", where blob is
# base64 of openssl aes-256-cbc -pbkdf2 -iter 200000 with PROBE_SEAL_KEY over "<kind>:<value>" (kind c = code, p = password).
secret_in() { # $1 kind, $2 minutes; prints the value
  local end=$(( $(date +%s) + $2 * 60 )) since; since=$(date -u -d "@$(( T0 / 1000 ))" +%Y-%m-%dT%H:%M:%SZ)
  while [ "$(date +%s)" -lt "$end" ]; do
    for b in $(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/field-probe.yml/runs?event=workflow_dispatch&per_page=20" \
        --jq ".workflow_runs[] | select(.created_at > \"$since\") | .display_title" 2>/dev/null | sed -n 's/^code //p'); do
      v=$(printf '%s' "$b" | base64 -d 2>/dev/null | openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:PROBE_SEAL_KEY 2>/dev/null)
      case "$v" in "$1:"*) printf '%s' "${v#*:}"; return 0 ;; esac
    done; sleep 10
  done; return 1
}
note() { echo "PROBE t=$(ms) $*" | tl; }

dump() { # $1 label: UI texts + screenshot
  local n; n=$(printf '%06d' "$(ms)")
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$OUT/ui-$n-$1.xml" > /dev/null 2>&1 || true
  adb exec-out screencap -p > "$OUT/shot-$n-$1.png" 2>/dev/null || true
  python3 - "$OUT/ui-$n-$1.xml" "$1" "$(ms)" <<'PY' 2>/dev/null | tl || true
import sys, re
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
texts = [x for x in re.findall(r'(?:text|content-desc)="([^"]+)"', t) if x.strip()]
print(f"PROBE t={sys.argv[3]} UI[{sys.argv[2]}] n={len(texts)} :: " + " | ".join(texts[:60]))
PY
}

on_screen() { # on_screen TEXT: true when TEXT is in the current UI (nothing is logged)
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml "$OUT/os.xml" > /dev/null 2>&1 || return 1
  grep -qF -- "$1" "$OUT/os.xml"
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
  # the emulator's own launcher sometimes shows "Pixel Launcher isn't responding" over everything (45139112809):
  # answer Wait so the rest of the run sees the app, and note it so the run is not read as an app fault
  if grep -q "isn&apos;t responding\|isn't responding" "$OUT/tap.xml" 2>/dev/null; then
    tap_text "Wait" > /dev/null 2>&1 && note "answered a system ANR dialog (not the app): Wait"
  fi
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
      xy\ *) # tap a screen point (e.g. the video surface, which has no text): xy X Y
        read -r px py <<< "${s#xy }"; adb shell input tap "$px" "$py"; note "tapped point $px $py"; sleep 1; dump "after-xy" ;;
      tap*) t=${s#tap }; answer_system; tap_text "$t" "~$t" || note "tap miss [$t]"; sleep 1; dump "after-tap" ;;
      find*) # scroll the page up (up to 15 swipes) until TEXT is on screen, then tap it
        t=${s#find }; ok=0
        for i in $(seq 1 15); do tap_text "$t" "~$t" && { ok=1; break; }; adb shell input swipe 540 1500 540 700 300; sleep 1; done
        # not below: the page may have opened scrolled down (Settings keeps its place), so look above too
        [ $ok = 1 ] || for i in 1 2 3 4 5 6 7 8 9 10 11 12; do tap_text "$t" "~$t" && { ok=1; break; }; adb shell input swipe 540 700 540 1500 300; sleep 1; done
        [ $ok = 1 ] || note "find miss [$t]"; sleep 1; dump "after-find" ;;
      recpull*) r=${s#recpull }; for i in $(seq 1 40); do adb shell pidof screenrecord > /dev/null 2>&1 || break; sleep 1; done; sleep 1; adb pull "/sdcard/rec-$r.mp4" "$OUT/rec-$r.mp4" > /dev/null 2>&1 && note "pulled rec-$r.mp4" || note "rec miss [$r]" ;;
      rec*) # rec NAME [SECS]: record the screen for SECS (default 5) s from now (runs while the next steps happen)
        r=${s#rec }; rs=5; case "$r" in *" "*) rs=${r##* }; r=${r% *} ;; esac
        adb shell "screenrecord --time-limit $rs --bit-rate 12000000 /sdcard/rec-$r.mp4" > /dev/null 2>&1 &
        sleep 0.6; note "recording [$r]" ;;
      field*) # field [N]: tap the Nth (default 1st) text field on screen; hints are not in the UI dump
        n=${s#field}; n=${n# }; [ -n "$n" ] || n=1
        adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/tap.xml" > /dev/null 2>&1
        XY=$(python3 - "$OUT/tap.xml" "$n" <<'PY'
import sys, re
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
f = [m.group(0) for m in re.finditer(r'<node [^>]*>', t) if 'class="android.widget.EditText"' in m.group(0)]
n = int(sys.argv[2])
if len(f) >= n:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', f[n - 1])
    if b: x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
        if [ -n "$XY" ]; then adb shell input tap $XY; note "tapped text field $n at $XY"; else note "field miss [$n]"; fi; sleep 1 ;;
      type*) # type TEXT into the focused field, as the on-screen keyboard would (LIVE-1 search probes)
        t=${s#type }; adb shell input text "'${t// /%s}'"; note "typed [$t]"; sleep 1; dump "after-type" ;;
      send*) # send TEXT: share a link to Yumeiro like another app's Share sheet would (text/plain)
        t=${s#send }; adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'$t'" -p "$P" > /dev/null 2>&1
        note "shared [$t] to the app"; sleep 1 ;;
      paste*) # paste TEXT: put TEXT on the clipboard ("\n" = line break) and paste it into the focused field, as a
        # person pasting copied text would (SEARCH-ML-1). The helper dex is built once from scripts/probe-clip.
        t=${s#paste }
        if [ ! -f "$OUT/clip.dex" ]; then
          BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1); PJ=$(ls -d "$ANDROID_HOME"/platforms/android-* | sort -V | tail -1)/android.jar
          mkdir -p /tmp/clipc && javac -source 8 -target 8 -cp "$PJ" -d /tmp/clipc scripts/probe-clip/Clip.java 2>/dev/null \
            && "$BT/d8" --output /tmp/clipc /tmp/clipc/Clip.class 2>/dev/null && cp /tmp/clipc/classes.dex "$OUT/clip.dex"
          adb push "$OUT/clip.dex" /data/local/tmp/clip.dex > /dev/null 2>&1
        fi
        r=$(adb shell "CLASSPATH=/data/local/tmp/clip.dex app_process / Clip '$t'" 2>&1 | tr -d '\r' | tail -1)
        adb shell input keyevent 279; note "pasted [$t] ($r)"; sleep 1; dump "after-paste" ;;
      key\ *) k=${s#key }; adb shell input keyevent "$k"; note "key $k"; sleep 1; dump "after-key" ;;
      sh\ *) # sh CMD: a shell command on the device (controls, e.g. am start a URL in another browser); not in sealed runs
        if [ "${SEALED:-0}" = 1 ]; then note "sh refused in a sealed run"; continue; fi
        c=${s#sh }; o=$(adb shell "$c" 2>&1 | head -c 400); note "sh [$c] -> $o"; sleep 1 ;;
      dismiss) tap_text "Close" "All set" "✕" "Not now" "Later" > /dev/null 2>&1 && note "dismissed a popup" || true; sleep 1 ;;
      top) for i in 1 2 3 4; do adb shell input swipe 540 600 540 1600 200; done; sleep 1 ;;
      back) adb shell input keyevent 4; note "key BACK"; sleep 1; dump "after-back" ;;
      anim*) # anim N: Android animation duration scale (1 = normal, 10 = ten times slower; Compose follows it)
        a=${s#anim }; adb shell settings put global animator_duration_scale "$a" > /dev/null 2>&1; note "animator_duration_scale=$a" ;;
      seal) note "sealed run" ;;
      tgphone) adb shell input text "$TG_TEST_PHONE"; note "typed the test phone (hidden)" ;;
      tgcode*|tgpass*) # tgcode [MIN] / tgpass [MIN]: wait for the code / 2-step password the agent sends, then type it
        k=c; w="Code sent"; [ "${s%% *}" = tgpass ] && { k=p; w="Two-step verification"; }; m=30; case "$s" in *" "*) m=${s#* } ;; esac
        sleep 3; if ! on_screen "$w"; then note "no [$w] screen, $k skipped"; signal success "no $k asked"; continue; fi
        signal pending "waiting for $k"; v=$(secret_in "$k" "$m") && { adb shell input text "$(printf '%q' "$v")"; note "typed $k (hidden)"; signal success "got $k"; } || { note "no $k in $m min"; signal failure "no $k"; } ;;
      secret\ *) # secret K [MIN]: sealed runs only; wait for a value of kind K the agent sends (probe-send-secret.sh K), type it
        k=${s#secret }; m=30; case "$k" in *" "*) m=${k#* }; k=${k%% *} ;; esac
        if [ "${SEALED:-0}" != 1 ]; then note "secret step refused: run is not sealed"; continue; fi
        signal pending "waiting for $k"; v=$(secret_in "$k" "$m") && { adb shell input text "$(printf '%q' "$v")"; note "typed $k (hidden)"; signal success "got $k"; } || { note "no $k in $m min"; signal failure "no $k"; } ;;
      quick*) # quick X Y: tap a point with no UI read after it (for recordings of what the tap starts)
        q=${s#quick }; adb shell input tap $q; note "quick tap $q" ;;
      home) adb shell input keyevent 3; note "key HOME" ;;
      night*) # night yes|no: system dark theme on / off (SPLASH-2 light and dark cold starts)
        v=${s#night }; adb shell cmd uimode night "$v" > /dev/null 2>&1; sleep 2; note "night mode $v: $(adb shell cmd uimode night | tr -d '\r')" ;;
      pic) # put one picture in the gallery (a screenshot of the screen now), for photo-picker probes (PROFILE-PIC-1)
        adb shell mkdir -p /sdcard/Pictures; adb shell screencap -p /sdcard/Pictures/yumeiro-probe.png
        adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
        adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/yumeiro-probe.png > /dev/null 2>&1 || true
        sleep 2; note "picture added to the gallery" ;;
      relaunch) # the app is closed (as after a swipe away) and opened again, to see what it shows from its saved state
        adb shell am force-stop "$P"; sleep 2; launch again; sleep 2 ;;
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
# PREWAIT N: a new emulator's Google Play services download several MB at first boot (45148263297: ~5 MB in
# 240 s on umts) and starve a shaped link; wait N s first so the app's own traffic is measured, not that.
if [ -n "${PREWAIT:-}" ]; then note "prewait ${PREWAIT} s (system first-boot downloads)"; sleep "$PREWAIT"; fi
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
adb shell dumpsys package "$P" | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" | tl
launch new
sleep 2; dump "start"
run_steps
dump "end"

adb shell dumpsys meminfo "$P" > "$OUT/meminfo.txt" 2>&1 || true
adb shell dumpsys gfxinfo "$P" > "$OUT/gfxinfo.txt" 2>&1 || true
adb shell pidof "$P" | tl
kill $LOGCAT 2>/dev/null || true
grep -E "FATAL EXCEPTION|ANR in $P" "$OUT/logcat.txt" | head -20 | tl || true
note "done"
