#!/usr/bin/env bash
# Build the debug APK, install it on the connected phone, and launch it.
# Usage: scripts/install.sh [probe]      (only the probe exists in Phase 0)
#        scripts/install.sh probe --no-build   (reinstall the last build)
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"

MODULE="${1:-probe}"
case "$MODULE" in
  probe) APP_ID="$PROBE_ID" ;;
  *) echo "Unknown module '$MODULE' (available: probe)"; exit 1 ;;
esac

require_device

if [ "${2:-}" != "--no-build" ]; then
  require_jdk
  echo "▸ Using JDK $(java_major "$JAVA_HOME"): $JAVA_HOME"
  mkdir -p build-logs
  LOG="build-logs/build-$MODULE-$(timestamp).log"
  echo "▸ Building :$MODULE  (log: $LOG)"
  if ! ./gradlew ":$MODULE:assembleDebug" --console=plain 2>&1 | tee "$LOG"; then
    cp "$LOG" build-logs/latest.log
    echo
    echo "✗ Build failed. Full log: $LOG (also build-logs/latest.log)"
    echo "  Errors:"; grep -E "^e: |error:|FAILURE|What went wrong" -A2 "$LOG" | head -40 || true
    exit 1
  fi
  cp "$LOG" build-logs/latest.log
fi

APK="$MODULE/build/outputs/apk/debug/$MODULE-debug.apk"
[ -f "$APK" ] || { echo "✗ APK not found at $APK"; exit 1; }

echo "▸ Installing $APK"
if ! out="$(adb install -r -t "$APK" 2>&1)"; then
  echo "$out"
  if echo "$out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    echo "✗ The installed copy was signed with a different key."
    echo "  Pull any results first (scripts/pull-report.sh), then: adb uninstall $APP_ID && scripts/install.sh $MODULE --no-build"
  fi
  exit 1
fi
echo "$out" | tail -1

echo "▸ Launching $APP_ID"
adb shell am start -n "$APP_ID/.MainActivity" >/dev/null
echo "✓ Installed and launched. Live log: scripts/logcat.sh"
