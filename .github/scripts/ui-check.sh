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

shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }

# Centre of a view by resource id, from a uiautomator dump ("" if absent).
center() {
  local out="" try
  for try in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
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

hide_keyboard() { adb shell cmd input_method hide-soft-input >/dev/null 2>&1 || true; sleep 0.5; }

tap_id() {
  local c; c=$(center "$1")
  if [ -z "$c" ]; then log "MISSING view: $1"; return 1; fi
  log "tap $1 at $c"; adb shell input tap $c
}

read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
log "screen ${W}x${H}"

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
    adb shell input swipe "$hx2" "$hy2" "$hx2" $((hy2 + 200)) 900
    sleep 1.5; shot 06-after-slow-small-drag-down
    expect shown homeSearchInput "slow short drag down springs back open"
    adb shell input swipe "$hx2" "$hy2" "$hx2" $((hy2 + 300)) 90
    sleep 1.5; shot 07-after-flick-down
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
tap_id navSheetHandle && sleep 1 && shot 17-drive-bar-expanded

# Dark mode.
adb shell cmd uimode night yes; sleep 4; shot 18-dark-driving
tap_id navSheetHandle; sleep 1; shot 19-dark-drive-bar

sleep 2
adb shell pkill -INT screenrecord 2>/dev/null; wait "$REC" 2>/dev/null; sleep 2
adb pull /sdcard/run.mp4 "$OUT/run.mp4" >/dev/null 2>&1 || log "no recording"

adb logcat -d > "$OUT/logcat.txt"
# Only NavOS's own crashes count - the emulator image's Google apps crash
# on their own sometimes.
if grep -A1 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG"; then
  grep -B1 -A40 "Process: $PKG" "$OUT/logcat.txt" > "$OUT/CRASH.txt"
  log "APP CRASHED - see CRASH.txt"
  exit 1
fi
adb shell pidof "$PKG" >/dev/null || { log "app not running at end"; exit 1; }
if grep -q "^FAIL" "$LOG"; then log "checks failed:"; grep "^FAIL" "$LOG"; exit 1; fi
log "done - no crash, all checks passed"
