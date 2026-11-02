#!/usr/bin/env bash
# Checks that the Mac is ready to build and install, and that the phone is connected.
# Usage: scripts/doctor.sh
source "$(dirname "$0")/env.sh"
cd "$REPO_ROOT"

ok=0
pass() { echo "  ✓ $*"; }
warn() { echo "  ! $*"; }
fail() { echo "  ✗ $*"; ok=1; }

echo "Build tools"
if [ -n "$JDK_FOUND" ]; then
  pass "JDK $(java_major "$JDK_FOUND") for Gradle: $JDK_FOUND"
else
  fail "No JDK 17–23 found (Gradle 8.11 can't start on JDK 24+)."
  [ -n "$JDK_REJECTED" ] && printf '%s' "$JDK_REJECTED" | sed 's/^/      unsuitable: /'
  echo "      Fix: brew install --cask temurin@21   (or Android Studio → Settings → Build Tools → Gradle → Gradle JDK → Download JDK 21)"
fi

if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
  pass "Android SDK: $ANDROID_HOME"
  if ls "$ANDROID_HOME/platforms" 2>/dev/null | grep -q "android-35"; then pass "SDK platform android-35 installed"
  else warn "SDK platform android-35 not installed yet (Gradle installs it if licences are accepted; or use SDK Manager)"; fi
  [ -d "$ANDROID_HOME/build-tools" ] && pass "build-tools: $(ls "$ANDROID_HOME/build-tools" | tr '\n' ' ')" || warn "no build-tools yet"
else
  fail "Android SDK not found. Install Android Studio and open it once (it downloads the SDK to ~/Library/Android/sdk)."
fi

[ -x ./gradlew ] && pass "gradlew is executable" || { chmod +x ./gradlew 2>/dev/null && pass "made gradlew executable" || fail "gradlew not executable"; }
[ -f local.properties ] && pass "local.properties: $(cat local.properties)" || warn "local.properties missing (written automatically once the SDK is found)"

echo "Phone"
if command -v adb >/dev/null 2>&1; then
  pass "adb: $(adb version | head -1)"
  devs="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  unauth="$(adb devices | awk 'NR>1 && $2=="unauthorized" {print $1}')"
  [ -n "$unauth" ] && fail "device $unauth is unauthorized — unlock the phone and accept the USB debugging prompt"
  if [ -n "$devs" ]; then
    for d in $devs; do
      model="$(adb -s "$d" shell getprop ro.product.model | tr -d '\r')"
      rel="$(adb -s "$d" shell getprop ro.build.version.release | tr -d '\r')"
      size="$(adb -s "$d" shell wm size | tr -d '\r' | awk -F': ' '{print $2}')"
      pass "device $d: $model, Android $rel, $size"
      echo "$model" | grep -qi titan || warn "this doesn't look like a Titan — is the right phone connected?"
      if adb -s "$d" shell pm list packages "$PROBE_ID" | grep -q "$PROBE_ID"; then
        pass "probe installed: $(adb -s "$d" shell dumpsys package $PROBE_ID | grep -m1 versionName | tr -d ' \r')"
      else
        warn "probe not installed yet (scripts/install.sh)"
      fi
    done
  else
    fail "no device connected (adb devices)"
  fi
else
  fail "adb not found (Android Studio → SDK Manager → SDK Tools → Platform-Tools)"
fi

echo
[ $ok -eq 0 ] && echo "All good. Next: scripts/install.sh" || echo "Fix the ✗ items above, then re-run scripts/doctor.sh"
exit $ok
