#!/usr/bin/env bash
# PLAN.md §9.3 A: device checks over adb (no app needed). Saves everything under reports/adb-<time>/.
# Usage: scripts/adb-checks.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"
require_device

OUT="reports/adb-$(timestamp)"
mkdir -p "$OUT/keymaps"
echo "▸ Writing to $OUT"

run() { # run <file> <adb shell command...>
  local f="$1"; shift
  adb shell "$@" > "$OUT/$f" 2>&1 || true
  echo "  ✓ $f"
}

{
  echo "model: $(adb shell getprop ro.product.model)"
  echo "manufacturer: $(adb shell getprop ro.product.manufacturer)"
  echo "android: $(adb shell getprop ro.build.version.release) (sdk $(adb shell getprop ro.build.version.sdk))"
  echo "build: $(adb shell getprop ro.build.display.id)"
  echo "wm size: $(adb shell wm size)"
  echo "wm density: $(adb shell wm density)"
} | tr -d '\r' > "$OUT/device.txt"
echo "  ✓ device.txt"

run input.txt dumpsys input
run getevent-il.txt getevent -il
run keylayout-dirs.txt ls -la /system/usr/keylayout /vendor/usr/keylayout /system/usr/keychars /vendor/usr/keychars /odm/usr/keylayout /odm/usr/keychars
run ime.txt ime list -a
run leds.txt ls -la /sys/class/leds/
run settings-secure.txt settings list secure
run settings-global.txt settings list global
run packages-unihertz.txt "pm list packages | grep -iE 'unihertz|kika|keyboard|input'"

# Pull the key layout / character map files the keyboard actually uses (listed in dumpsys input).
grep -oE '(KeyLayoutFile|KeyCharacterMapFile): [^ ,]+' "$OUT/input.txt" | awk '{print $2}' | tr -d '\r' | sort -u | while read -r f; do
  [ -n "$f" ] || continue
  adb pull "$f" "$OUT/keymaps/" >/dev/null 2>&1 && echo "  ✓ pulled $f" || echo "  ! could not pull $f"
done

echo
echo "▸ Backlight settings diff: the script snapshots 'settings list system', you toggle the"
echo "  keyboard backlight with the long slide, then it snapshots again."
adb shell settings list system | tr -d '\r' > "$OUT/settings-system-before.txt"
read -r -p "  Toggle the keyboard backlight now (long slide), then press Enter… " _
adb shell settings list system | tr -d '\r' > "$OUT/settings-system-after.txt"
diff "$OUT/settings-system-before.txt" "$OUT/settings-system-after.txt" > "$OUT/settings-system-diff.txt"
if [ -s "$OUT/settings-system-diff.txt" ]; then
  echo "  ✓ changed keys:"; sed 's/^/    /' "$OUT/settings-system-diff.txt"
else
  echo "  ! no system setting changed when toggling the backlight"
fi

echo
echo "▸ Raw kernel events for 25 s: press sym, Alt, Shift, the red side button, and swipe the keyboard surface."
read -r -p "  Press Enter to start… " _
adb shell timeout 25 getevent -lt > "$OUT/getevent-lt.txt" 2>&1 || true
echo "  ✓ getevent-lt.txt ($(wc -l < "$OUT/getevent-lt.txt" | tr -d ' ') lines)"

ln -sfn "$(basename "$OUT")" reports/adb-latest
echo
echo "✓ Done: $OUT  (also reports/adb-latest). Also take a photo of the keycaps and drop it in that folder."
