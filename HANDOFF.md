# Handoff: Titan Code, Phase 0

You're picking up an Android project in this repo (`~/unihertz-leetcode`, on the owner's Mac). The owner's Unihertz Titan 2 Elite (Android 16, physical QWERTY keyboard with a touch-sensitive surface) is connected over USB. Check with `adb devices`.

## Long-term goal
An Android app for LeetCode practice in Python on this phone, built around the physical keyboard. NeetCode and Codeforces may be added later. **`PLAN.md` is the source of truth; read it first.** The key owner decisions:

- **Text editing:** all text editing is done on the keyboard. Touch is used for pane tabs, the Run/Submit buttons and scrolling.
- **Shortcuts:** each action has at most one keyboard shortcut, to keep the list short.
- **`sym` key:** hold `sym` + a letter runs a leader command. There's no tap behaviour, no sticky keys and no custom symbol layer yet; symbols come from the native Alt layer.
- **Red side button:** switches between the Problem and Code panes (Unihertz PTT app = Titan Code → keycode 401).
- **Keyboard backlight:** left to the system (the requirement was dropped after Phase 0).
- **Symbols:** `$ % ^ &` via Alt+Shift+S/D/F/Z; other missing symbols via a touchscreen palette opened with `fn` (PLAN §5.3).
- **Screen:** status bar hidden; the top band beside the top-left camera cutout holds the tab bar (PLAN §4.1).
- **LeetCode access:** leetcode.com only, with LeetCode's own username/password login (no OAuth). There's a WebView `fetch()` fallback if Cloudflare blocks OkHttp.
- **Offline:** offline-first. Problems, tests and comments are saved locally. Submitting while offline is blocked, not queued. Drafts stay on the device only.
- **Builds:** local builds on this Mac only. No GitHub Actions or other CI.
- **Git:** commit only when the owner asks. The repo has no commits yet.

The milestones are in PLAN.md §12: M0 is the probe (now), M1 the editor and input core, M2 reading LeetCode offline-first, M3 run/submit, M4 the local runner and offline packs, M5 polish, M6 more platforms.

## Current state (end of Phase 0 / M0)
- **The probe builds, installs and runs** with Temurin JDK 21 and the SDK at `~/Library/Android/sdk`. Use `scripts/doctor.sh`, then `scripts/install.sh`.
- **Results:**
  - The guided run is in `reports/probe-20261101-200140/` and the adb checks are in `reports/adb-20261101-201016/`.
  - The analysis is in **`docs/device-input-report.md`**.
  - PLAN.md is at revision 3, with the owner's decisions in Appendix B.
- **Re-tests:** all done except K11, which is optional. The red button works as PTT key → keycode 401. Cursor Assistant = arrow keys in all four directions, which work in TYPE_NULL when the editor has focus (PLAN §5.6).

## Next steps
1. **M1:** add the `:app` module (Kotlin + Compose + Sora Editor) and follow PLAN.md §5 and the acceptance tests E01–E15 in §10.

## Working agreements
- Run builds and installs through the scripts, so the logs land in `build-logs/` and `reports/`.
- Keep the probe a throwaway app: fix bugs, but don't polish it.
- Don't add CI, OAuth, alternate shortcuts or a custom symbol layer unless the owner asks.
