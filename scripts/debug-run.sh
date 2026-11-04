#!/bin/bash
# Run one local-runner request (titan_runner.py JSON) on the phone's Python, without touching the
# editor or any draft (debug builds; DebugRunReceiver, rev 24). Prints the result JSON.
#   scripts/debug-run.sh request.json [limit-ms]
set -euo pipefail
source "$(dirname "$0")/env.sh" >/dev/null 2>&1
adb push "$1" /data/local/tmp/debug-run.json >/dev/null
adb shell "run-as dev.titan.code sh -c 'cat /data/local/tmp/debug-run.json > cache/debug-run.json; rm -f cache/debug-run.out.json'"
adb shell am broadcast -n dev.titan.code/.debug.DebugRunReceiver --el limit "${2:-40000}" >/dev/null
for _ in $(seq 1 90); do
  out=$(adb shell "run-as dev.titan.code cat cache/debug-run.out.json 2>/dev/null" || true)
  if [ -n "$out" ]; then echo "$out"; exit 0; fi
  sleep 1
done
echo "no result after 90 s" >&2
exit 1
