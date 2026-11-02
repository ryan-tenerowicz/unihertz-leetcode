#!/usr/bin/env bash
# Copy the probe's report + logcat from the phone into reports/probe-<time>/ (and reports/latest).
# Tap "Save report" in the probe first (it also saves automatically when you exit a guided run).
# Usage: scripts/pull-report.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"
require_device

OUT="reports/probe-$(timestamp)"
mkdir -p "$OUT"

if adb pull /sdcard/Download/titan-probe "$OUT/" >/dev/null 2>&1 && [ -d "$OUT/titan-probe" ]; then
  mv "$OUT/titan-probe/"* "$OUT/" && rmdir "$OUT/titan-probe"
  echo "✓ pulled Download/titan-probe/"
else
  echo "! Download/titan-probe/ not found; trying app storage (run-as)…"
  for f in titan-probe-report.json titan-probe-report.md; do
    adb exec-out run-as "$PROBE_ID" cat "files/reports/$f" > "$OUT/$f" 2>/dev/null && [ -s "$OUT/$f" ] \
      && echo "✓ $f" || { rm -f "$OUT/$f"; echo "✗ $f not found — open the probe and tap Save report"; }
  done
fi

adb logcat -d -v time -s TitanProbe:* > "$OUT/logcat.txt" 2>/dev/null || true
echo "✓ logcat.txt ($(wc -l < "$OUT/logcat.txt" | tr -d ' ') lines)"

ln -sfn "$(basename "$OUT")" reports/latest
echo "✓ Saved to $OUT  (also reports/latest)"
[ -f "$OUT/titan-probe-report.md" ] && head -40 "$OUT/titan-probe-report.md" | sed -n '/## Results/,$p' | head -8
