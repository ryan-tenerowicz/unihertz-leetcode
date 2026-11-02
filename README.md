# Titan Code

An Android app for doing LeetCode-style practice in Python on the **Unihertz Titan 2 Elite**, built around its physical keyboard. The design is in [`PLAN.md`](PLAN.md).

**Current phase: Phase 0 (M0).** The repo contains only the **Key & Gesture Probe** (`probe/`), a throwaway app. It records exactly what the phone's keyboard, `sym` key, red button and touch-sensitive keyboard surface send to an app. The editor design (PLAN.md §5) gets finalised from its report.

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

1. Install **Android Studio**. It bundles a JDK and downloads the SDK to `~/Library/Android/sdk`. Open it once, and in SDK Manager install **Android 15 (API 35)** and **Platform-Tools**.
2. On the phone, go to Settings → About phone and tap Build number 7 times. Then in Developer options turn on **USB debugging**. Plug in the phone and accept the prompt.
3. Run `scripts/doctor.sh` until everything shows ✓.

The scripts find Android Studio's JDK and SDK on their own. You can also open the folder in Android Studio and run the `probe` configuration.

## Layout

```
PLAN.md              design plan (source of truth)
probe/               Key & Gesture Probe (Phase 0)
scripts/             doctor / install / logcat / adb-checks / pull-report
docs/PROBE.md        how to run the guided keyboard tests
```
