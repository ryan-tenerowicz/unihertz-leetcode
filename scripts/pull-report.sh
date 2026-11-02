#!/usr/bin/env bash
# Copy a report + logcat from the phone into reports/.
#   probe: Download/titan-probe/            → reports/probe-<time>/       (+ reports/latest)
#   app:   Download/titan-code-acceptance/  → reports/acceptance-<time>/  (+ reports/acceptance-latest)
# Save the report in the app first (the probe and the acceptance panel also save when a run ends).
# Usage: scripts/pull-report.sh [app|probe]     (default: app once it exists)
set -uo pipefail
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"
MODULE="${1:-$(default_module)}"
resolve_module "$MODULE"
require_device

if [ "$MODULE" = app ]; then PREFIX=acceptance; LINK=reports/acceptance-latest; FILES="acceptance-report.json acceptance-report.md"
else PREFIX=probe; LINK=reports/latest; FILES="titan-probe-report.json titan-probe-report.md"; fi

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
