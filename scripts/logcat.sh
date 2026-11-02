#!/usr/bin/env bash
# Live log for one app (tag TitanCode or TitanProbe), plus crashes. Ctrl-C to stop.
# Usage: scripts/logcat.sh [app|probe]     (default: app once it exists)
source "$(dirname "$0")/env.sh"
MODULE="${1:-$(default_module)}"
resolve_module "$MODULE"
require_device
adb logcat -v time "$LOG_TAG:*" 'AndroidRuntime:E' '*:S'
