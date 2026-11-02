# Titan Code

An Android app for doing LeetCode-style practice in Python on the **Unihertz Titan 2 Elite**, built around its physical keyboard. The design is in [`PLAN.md`](PLAN.md).

**Status:** Phase 0 (M0) is done; M1 (the editor) is next. **Read [`PROGRESS.md`](PROGRESS.md) first**: it has the current status, the ordered next steps, the phone's settings and the history of plan changes. The design is in [`PLAN.md`](PLAN.md); what the phone's keyboard actually sends is in [`docs/device-input-report.md`](docs/device-input-report.md).

The repo contains the **Key & Gesture Probe** (`probe/`), a throwaway diagnostic app that records exactly what the keyboard, `sym`, `fn`, the red button and the touch-sensitive keyboard surface send to an app. The real app (`:app`) is added in M1.

## Everyday loop (on the Mac)

| Step | Command |
|---|---|
| Check the Mac and phone are ready | `scripts/doctor.sh` |
| Build, install and launch the probe | `scripts/install.sh` |
| Watch events live while testing | `scripts/logcat.sh` |
| Device checks over adb (§9.3 A) | `scripts/adb-checks.sh` |
| Copy the probe report back | `scripts/pull-report.sh` → `reports/latest/` |

Build logs go to `build-logs/` (newest: `build-logs/latest.log`). Reports go to `reports/`. Both folders stay on the Mac and aren't committed.

## One-time setup

1. Install **Android Studio** and run its setup wizard once; it downloads the SDK to `~/Library/Android/sdk`. Gradle fetches Android 15 (API 35) by itself.
2. Install **JDK 21**: `brew install --cask temurin@21`. Android Studio's bundled JDK is version 25, which Gradle 8.11 can't run on; the scripts pick a JDK 17–23 automatically.
3. On the phone, go to Settings → About phone and tap Build number 7 times. Then in Developer options turn on **USB debugging**. Plug in the phone and accept the prompt.
4. Run `scripts/doctor.sh` until everything shows ✓.

The scripts find the JDK and SDK on their own. You can also open the folder in Android Studio and run the `probe` configuration.

## Layout

```
PLAN.md              design plan (source of truth)
PROGRESS.md          status, next steps, problems hit, plan change log
docs/device-input-report.md   what the Titan's keyboard and touch surface send (Phase 0 results)
probe/               Key & Gesture Probe (Phase 0)
scripts/             doctor / install / logcat / adb-checks / pull-report
docs/PROBE.md        how to run the guided keyboard tests
```
