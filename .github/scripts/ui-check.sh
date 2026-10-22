#!/usr/bin/env bash
# Drives NavOS on the CI emulator and records what each screen looks like.
# Every step is best-effort: a missing view is logged and the run carries
# on, so one broken step still leaves screenshots of everything else. The
# job only fails if the app crashes.
set -u
OUT=ui-check
PKG=com.excellentmindblow.navos
mkdir -p "$OUT"
LOG="$OUT/steps.log"
log() { echo "$*" | tee -a "$LOG"; }

shot() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  dismiss_system_dialogs >/dev/null || true
  adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"
}

# Centre of a view by resource id, from a uiautomator dump ("" if absent).
# The CI emulator is slow enough that Android's own processes sometimes
# raise "isn't responding" dialogs over the app. Press Wait on any that
# show up (and note whether it was NavOS).
dismiss_system_dialogs() {
  local xml c
  xml=$(adb shell cat /sdcard/ui.xml 2>/dev/null)
  echo "$xml" | grep -q 'android:id/aerr_wait' || return 1
  echo "$xml" | grep -o "text=\"[^\"]*isn't responding\"" | head -1 | sed 's/^/dismissed dialog: /' | tee -a "$LOG" >&2
  c=$(echo "$xml" | tr '>' '\n' | grep 'android:id/aerr_wait' | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF==4 {printf "%d %d", ($1+$3)/2, ($2+$4)/2}')
  [ -n "$c" ] && adb shell input tap $c
  sleep 1
  return 0
}

center() {
  local out="" try
  for try in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    dismiss_system_dialogs && adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    out=$(adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' |
      grep "resource-id=\"$PKG:id/$1\"" | head -1 |
      sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
      awk 'NF==4 {printf "%d %d", ($1+$3)/2, ($2+$4)/2}')
    [ -n "$out" ] && break
    sleep 1
  done
  echo "$out"
}

# PASS/FAIL line for a view that should (or should not) be on screen.
expect() {
  local want=$1 id=$2 what=$3 c
  c=$(center "$id")
  if { [ "$want" = shown ] && [ -n "$c" ]; } || { [ "$want" = hidden ] && [ -z "$c" ]; }; then
    log "PASS $what"
  else
    log "FAIL $what"
  fi
}

foreground() {
  adb shell dumpsys activity activities 2>/dev/null | grep -q "topResumedActivity.*$PKG" ||
    { log "NavOS no longer in foreground"; adb shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 4; }
}

ime_shown() { adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; }

# Back hides the keyboard, but only press it while the keyboard is up -
# otherwise Back collapses the sheet or opens NavOS's exit dialog. Then
# give the emulator's input stack a moment: it reconfigures its touch
# devices while the keyboard animates and drops injected swipes meanwhile.
hide_keyboard() {
  local i
  for i in 1 2 3; do ime_shown || break; adb shell input keyevent KEYCODE_BACK; sleep 1; done
  sleep 2
}

tap_id() {
  local c; c=$(center "$1")
  if [ -z "$c" ]; then log "MISSING view: $1"; return 1; fi
  log "tap $1 at $c"; adb shell input tap $c
}

read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
log "screen ${W}x${H}"

adb root >/dev/null 2>&1; adb wait-for-device   # lets us pull ANR / stack traces later
# Let the freshly booted emulator finish its first-boot work (Play
# services, launcher) before measuring anything.
sleep 60
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure location_mode 3
adb emu geo fix 101.6869 3.1390   # Kuala Lumpur (lon lat)
adb logcat -c
adb shell screenrecord --time-limit 180 --bit-rate 4000000 /sdcard/run.mp4 &
REC=$!

adb shell am start -W -n "$PKG/.MainActivity"
sleep 15
adb emu geo fix 101.6869 3.1390
sleep 3
shot 01-home-collapsed

# Rubber-band: hold the collapsed sheet's handle and drag it further down.
if c=$(center homeSheetDragHandle) && [ -n "$c" ]; then
  read -r hx hy <<<"$c"
  adb shell input motionevent DOWN "$hx" "$hy"
  for dy in 40 80 140 220 300; do adb shell input motionevent MOVE "$hx" $((hy + dy)); done
  sleep 0.3; shot 02-rubberband-past-bottom
  adb shell input motionevent UP "$hx" $((hy + 300))
  sleep 1.5; shot 03-after-rubberband-release
  expect shown homeSearchPill "sheet springs back to collapsed after rubber-band"

  # Short quick flick up: should open fully thanks to momentum projection.
  adb shell input swipe "$hx" "$hy" "$hx" $((hy - 250)) 80
  sleep 1.5; shot 04-after-short-flick-up
  expect shown homeSearchInput "short quick flick up opens the sheet"
  hide_keyboard; shot 05-search-open-no-keyboard

  # Slow small drag down then release: should spring back open.
  if c2=$(center homeSheetDragHandle) && [ -n "$c2" ]; then
    read -r hx2 hy2 <<<"$c2"
    hide_keyboard
    adb shell input swipe "$hx2" "$hy2" "$hx2" $((hy2 + 200)) 900
    sleep 1.5; shot 06-after-slow-small-drag-down
    expect shown homeSearchInput "slow short drag down springs back open"
    hide_keyboard
    adb shell input swipe "$hx2" "$hy2" "$hx2" $((hy2 + 600)) 100
    sleep 1.5; shot 07-after-flick-down
    if [ -z "$(center homeSearchPill)" ]; then
      log "RETRY flick down (emulator may have dropped the swipe)"
      hide_keyboard
      adb shell input swipe "$hx2" "$hy2" "$hx2" $((hy2 + 600)) 100
      sleep 1.5; shot 07b-after-flick-down-retry
    fi
    expect shown homeSearchPill "short quick flick down closes the sheet"
  fi
else
  log "MISSING view: homeSheetDragHandle"
fi

# Tap the pill (tap path, no drag) and type a query.
tap_id homeSearchPill && sleep 1.5 && shot 08-search-tap-open
adb shell input text "KLCC" ; sleep 6; shot 09-search-results
hide_keyboard
tap_id homeSheetBackButton; sleep 1.5; shot 10-back-collapsed
foreground

# Settings.
tap_id homeSettingsButton && sleep 1.5 && shot 11-settings
tap_id homeSheetBackButton; sleep 1.5
foreground

# Dropped pin card (long-press the map above the sheet).
adb shell input swipe $((W / 2)) $((H * 2 / 5)) $((W / 2)) $((H * 2 / 5)) 1200
sleep 0.15; shot 12-pin-card-mid-reveal
sleep 3; shot 13-pin-card
expect shown poiGoButton "long-press on the map shows the dropped-pin card"

# Route preview, then start driving.
tap_id poiGoButton && sleep 12 && shot 14-route-preview
expect shown startNavigationButton "route preview with Go now"
tap_id startNavigationButton && sleep 6 && shot 15-driving
expect shown etaClockText "drive bar shows ETA"
for lat in 3.1395 3.1400 3.1405; do adb emu geo fix 101.6875 "$lat" >/dev/null 2>&1; sleep 1; done
shot 16-driving-after-moves

# Main-thread stack samples during the drive (SIGQUIT writes all thread
# stacks to /data/anr), to see what the UI thread is busy with.
NAVPID=$(adb shell pidof "$PKG" | tr -d '\r')
for i in 1 2 3 4; do adb shell kill -3 "$NAVPID"; sleep 4; done
adb shell top -b -n 1 -H -p "$NAVPID" 2>/dev/null | head -25 > "$OUT/threads-top.txt"

# Second recording segment for the drive (screenrecord caps at 180 s).
adb shell pkill -INT screenrecord 2>/dev/null; wait "$REC" 2>/dev/null; sleep 1
adb pull /sdcard/run.mp4 "$OUT/run-1.mp4" >/dev/null 2>&1
adb shell screenrecord --time-limit 120 --bit-rate 4000000 /sdcard/run.mp4 &
REC=$!
sleep 1

# Drive bar handle: a tap should reveal the Stop pill.
tap_id navSheetHandle; sleep 0.25; shot 17a-drive-bar-tap-quarter-second
sleep 1; shot 17b-drive-bar-after-tap
expect shown stopNavigationButton "tap on drive bar handle reveals Stop"
if [ -z "$(center stopNavigationButton)" ]; then
  # Fall back to a swipe up on the handle, the other documented gesture.
  if c=$(center navSheetHandle) && [ -n "$c" ]; then
    read -r nx ny <<<"$c"
    adb shell input swipe "$nx" "$ny" "$nx" $((ny - 200)) 200
    sleep 1.2; shot 17c-drive-bar-after-swipe-up
    expect shown stopNavigationButton "swipe up on drive bar handle reveals Stop"
  fi
fi

# End the drive, then check dark mode while idle (mid-drive it is
# deliberately deferred until the drive ends).
tap_id stopNavigationButton; sleep 3; shot 18-after-stop
foreground
adb shell cmd uimode night yes; sleep 6; shot 19-dark-idle
tap_id homeSearchPill && sleep 1.5 && hide_keyboard && shot 20-dark-search
tap_id homeSheetBackButton; sleep 1.5
tap_id homeSettingsButton && sleep 1.5 && shot 21-dark-settings
adb shell cmd uimode night no
sleep 6

# 3D road signs: Jalan Gombak -> Genting Highlands. 1.6 km in, the route
# takes the on-ramp to the DUKE (E33) - the map data signposts it
# "E33, 2: Kuantan, Seremban" with lane arrows, so a lane gantry and a
# direction gantry stand ahead; traffic lights along Jalan Gombak too.
adb emu geo fix 101.71296 3.22910; sleep 2
adb shell am start -a android.intent.action.VIEW -d "geo:3.4236,101.7932?q=3.4236,101.7932" -p "$PKG" >/dev/null 2>&1
sleep 6; shot 22-signs-place
tap_id poiGoButton && sleep 25 && shot 23-signs-route-preview
tap_id startNavigationButton && sleep 10 && shot 24-signs-start
for fix in "101.71189 3.23137" "101.71060 3.23318" "101.70905 3.23411"; do
  adb emu geo fix $fix >/dev/null 2>&1; sleep 2
done
sleep 4; shot 25-signs-800m
for fix in "101.70725 3.23412" "101.70585 3.23430"; do
  adb emu geo fix $fix >/dev/null 2>&1; sleep 2
done
sleep 4; shot 26-signs-1150m
adb emu geo fix 101.70460 3.23489 >/dev/null 2>&1; sleep 2
adb emu geo fix 101.70391 3.23524 >/dev/null 2>&1; sleep 4; shot 27-signs-1400m
adb logcat -d -s NavOSSigns > "$OUT/signs.txt" 2>/dev/null
tap_id navSheetHandle; sleep 1; tap_id stopNavigationButton; sleep 3

sleep 2
adb shell pkill -INT screenrecord 2>/dev/null; wait "$REC" 2>/dev/null; sleep 2
adb pull /sdcard/run.mp4 "$OUT/run-2.mp4" >/dev/null 2>&1 || log "no recording"

adb logcat -d > "$OUT/logcat.txt"
mkdir -p "$OUT/anr"; adb pull /data/anr "$OUT/anr" >/dev/null 2>&1 || log "could not pull /data/anr"
# Only NavOS's own crashes count - the emulator image's Google apps crash
# on their own sometimes.
if grep -A1 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG"; then
  grep -B1 -A40 "Process: $PKG" "$OUT/logcat.txt" > "$OUT/CRASH.txt"
  log "APP CRASHED - see CRASH.txt"
  exit 1
fi
adb shell pidof "$PKG" >/dev/null || { log "app not running at end"; exit 1; }
grep -q "dismissed dialog.*NavOS" "$LOG" && log "FAIL NavOS raised an isn't-responding dialog"
if grep -q "^FAIL" "$LOG"; then log "checks failed:"; grep "^FAIL" "$LOG"; exit 1; fi
log "done - no crash, all checks passed"
