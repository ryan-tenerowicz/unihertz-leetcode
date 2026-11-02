#!/usr/bin/env bash
# Live view of everything the probe receives (tag TitanProbe). Ctrl-C to stop.
source "$(dirname "$0")/env.sh"
require_device
adb logcat -v time -s TitanProbe:*
