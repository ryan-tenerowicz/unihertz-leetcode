#!/usr/bin/env bash
# Copy the Key & Gesture Probe's report + logcat from the phone into reports/.
#   Download/titan-probe/ → reports/probe-<time>/ (+ reports/latest)
# The probe saves its report when a guided run ends. The app writes no report; use scripts/logcat.sh.
# Usage: scripts/pull-report.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"
if [ "${1:-probe}" != probe ]; then echo "Only the probe writes a report (scripts/pull-report.sh). For the app, use scripts/logcat.sh." >&2; exit 1; fi
resolve_module probe
require_device

PREFIX=probe; LINK=reports/latest; FILES="titan-probe-report.json titan-probe-report.md"

OUT="reports/$PREFIX-$(timestamp)"
mkdir -p "$OUT"

if adb pull "/sdcard/Download/$REPORT_DIR" "$OUT/" >/dev/null 2>&1 && [ -d "$OUT/$REPORT_DIR" ]; then
  mv "$OUT/$REPORT_DIR/"* "$OUT/" && rmdir "$OUT/$REPORT_DIR"
  echo "✓ pulled Download/$REPORT_DIR/"
else
  echo "! Download/$REPORT_DIR/ not found; trying app storage (run-as)…"
  for f in $FILES; do
    adb exec-out run-as "$APP_ID" cat "files/reports/$f" > "$OUT/$f" 2>/dev/null && [ -s "$OUT/$f" ] \
      && echo "✓ $f" || { rm -f "$OUT/$f"; echo "✗ $f not found — open the app and save the report"; }
  done
fi

adb logcat -d -v time "$LOG_TAG:*" 'AndroidRuntime:E' '*:S' > "$OUT/logcat.txt" 2>/dev/null || true
echo "✓ logcat.txt ($(wc -l < "$OUT/logcat.txt" | tr -d ' ') lines)"

ln -sfn "$(basename "$OUT")" "$LINK"
echo "✓ Saved to $OUT  (also $LINK)"
for md in "$OUT"/*.md; do [ -f "$md" ] && sed -n '/## Results/,$p' "$md" | head -8; done
