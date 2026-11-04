#!/usr/bin/env bash
# Shared environment for the scripts in this folder. Sourced, not run.
# Finds a JDK and the Android SDK on macOS, writes local.properties, and puts adb on PATH.
# Works with the bash 3.2 that ships with macOS.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- JDK ---------------------------------------------------------------------------------
# Gradle 8.11 + AGP 8.7 need a JDK between 17 and 23 to *run* (JDK 24/25 fail at startup with
# just the version number as the error). Pick the first suitable JDK from: JAVA_HOME, Android
# Studio's bundled JBR, JDKs Android Studio downloaded, /usr/libexec/java_home, Homebrew.
JDK_MIN=17
JDK_MAX=23
java_major() { "$1/bin/java" -version 2>&1 | grep -m1 -i version | sed -E 's/.*version "([0-9]+).*/\1/'; }
jdk_ok() { [ -x "$1/bin/java" ] || return 1; local m; m="$(java_major "$1")"; [ -n "$m" ] && [ "$m" -ge $JDK_MIN ] 2>/dev/null && [ "$m" -le $JDK_MAX ] 2>/dev/null; }

JDK_FOUND=""
JDK_REJECTED=""
for c in \
  "${JAVA_HOME:-}" \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home" \
  $( [ -x /usr/libexec/java_home ] && for v in 21 17 23 22 20 19 18; do /usr/libexec/java_home -v "$v" 2>/dev/null; done ) \
  "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
  /Library/Java/JavaVirtualMachines/*/Contents/Home \
  /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
  [ -n "$c" ] && [ -x "$c/bin/java" ] || continue
  if jdk_ok "$c"; then JDK_FOUND="$c"; break; fi
  case " $JDK_REJECTED " in *" $c "*) ;; *) JDK_REJECTED="$JDK_REJECTED $c (JDK $(java_major "$c"))
";; esac
done
if [ -n "$JDK_FOUND" ]; then
  export JAVA_HOME="$JDK_FOUND"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Exits with instructions if no JDK 17–23 was found.
require_jdk() {
  if [ -z "$JDK_FOUND" ]; then
    echo "✗ No JDK between $JDK_MIN and $JDK_MAX found (Gradle 8.11 can't start on newer JDKs)." >&2
    [ -n "$JDK_REJECTED" ] && { echo "  Found but unsuitable:" >&2; printf '%s' "$JDK_REJECTED" | sed 's/^/   /' >&2; }
    echo "  Fix (either one):" >&2
    echo "    brew install --cask temurin@21" >&2
    echo "    or Android Studio → Settings → Build Tools → Gradle → Gradle JDK → Download JDK → version 21" >&2
    echo "  Then re-run this script; it finds the new JDK automatically." >&2
    exit 1
  fi
}

# --- Android SDK ---
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [ -d "$HOME/Library/Android/sdk" ]; then export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi
if [ -n "${ANDROID_HOME:-}" ] && [ ! -f "$REPO_ROOT/local.properties" ]; then
  echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
fi

# --- adb ---
if ! command -v adb >/dev/null 2>&1 && [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

PROBE_ID="dev.titan.probe"
APP_ID_CODE="dev.titan.code"

# Default module for install and logcat: the app (the probe is chosen with "probe").
default_module() { if [ -d "$REPO_ROOT/app" ]; then echo app; else echo probe; fi; }

# Sets APP_ID, LOG_TAG and REPORT_DIR (the probe's report folder under /sdcard/Download) for a module name, or exits.
resolve_module() {
  case "$1" in
    app)   APP_ID="$APP_ID_CODE"; LOG_TAG="TitanCode";  REPORT_DIR="" ;;
    probe) APP_ID="$PROBE_ID";    LOG_TAG="TitanProbe"; REPORT_DIR="titan-probe" ;;
    *) echo "Unknown module '$1' (available: app, probe)" >&2; exit 1 ;;
  esac
}

# Fails with a clear message unless exactly one device is connected.
require_device() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "✗ adb not found. Install Android platform-tools (Android Studio → SDK Manager) or 'brew install android-platform-tools'." >&2
    exit 1
  fi
  local n
  n="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  if [ "$n" = "0" ]; then
    echo "✗ No device. Plug in the Titan 2 Elite, enable USB debugging, accept the prompt on the phone, then: adb devices" >&2
    adb devices >&2
    exit 1
  fi
  if [ "$n" != "1" ]; then
    echo "✗ $n devices connected; set ANDROID_SERIAL to pick one." >&2
    adb devices -l >&2
    [ -z "${ANDROID_SERIAL:-}" ] && exit 1
  fi
  # The phone's global log level can be INFO (log.tag=I), which hides our debug lines.
  adb shell setprop log.tag.TitanCode V >/dev/null 2>&1 || true
  adb shell setprop log.tag.TitanProbe V >/dev/null 2>&1 || true
  adb shell setprop log.tag.TitanRunner V >/dev/null 2>&1 || true
}

timestamp() { date +%Y%m%d-%H%M%S; }
