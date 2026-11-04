# Titan Code

An Android app for practising coding problems in Python on the **Unihertz Titan 2 Elite**, built around its physical keyboard. It shows on the phone as **Coding** (`dev.titan.code`).

Browse, read, write, run and submit **LeetCode**, **NeetCode** and **Codeforces** problems, almost all of it offline.

## Features

- **Three sites, one editor.** Problem lists with type-to-filter, difficulty/status chips, NeetCode 150 / Blind 75 / NeetCode 250 lists, the LeetCode daily and a 🎲 random pick. Paid problems are hidden.
- **A keyboard-first Python editor.**
  - Every symbol is reachable: Alt, Alt+Shift, and an `fn` palette for `= [ ] { } < > | ; ~`.
  - `sym` + letter commands.
  - Auto-pairing, smart Enter and Backspace, word completion, indent guides and bracket highlighting.
  - Soft wrap and a saved font size.
  - Drafts autosave with a version history.
- **The keyboard surface as a trackpad.** Swipe to move the cursor, Shift + swipe to select, with adjustable speed and a vibration per step.
- **Run offline.** A local Python 3.14 runner checks your solution against the examples and your own test cases:
  - LeetCode's special types: linked-list cycles, graphs, interactive problems, design classes.
  - Per-case time, memory and output limits that show the line a stuck case was on.
  - Checkers for problems with several right answers.
  - **⚡ stress tests** against a reference solution on generated inputs.
- **Run and submit on the site** when online: LeetCode, NeetCode, and Codeforces submissions.
- **Read offline.**
  - Statements, discussions and solution posts are saved as you open them.
  - Packs save study plans and lists, or **everything** on a site, in the background.
- **Comfortable reading.** A separate text size for the Problem and Comments panes scales their text and controls together.
- **Practice timer** per problem.

The full description is in **[`PLAN.md`](PLAN.md)**. Status, history and decisions are in [`PROGRESS.md`](PROGRESS.md). Manual checks for the phone are in [`docs/test-checklist.md`](docs/test-checklist.md).

## Phone setup

1. Install the app (below), then follow the **Setup checklist** it shows on first run (also in ⚙ → Setup).
2. Turn on **Scroll Assistant** (keyboard touch settings). The app moves the cursor with it.
3. *Settings → Shortcut keys → PTT key → PTT app* = **Coding**, so the red side button switches between Problem and Code.
4. Optional: sign in to LeetCode, NeetCode and Codeforces in ⚙ → Accounts to run and submit on the sites. Reading and running locally need no account.

## Building (on the Mac)

| Step | Command |
|---|---|
| Check the Mac and phone are ready | `scripts/doctor.sh` |
| Build, install and launch the app | `scripts/install.sh` (or `scripts/install.sh probe`) |
| Run the unit tests | `./gradlew :app:testDebugUnitTest` and `scripts/test-python.sh` |
| Watch the app's log | `scripts/logcat.sh` |
| Run one local-runner request on the phone | `scripts/debug-run.sh request.json` |

Build logs go to `build-logs/` and reports to `reports/`; both stay on the Mac.

**One-time setup:**
1. Install **Android Studio** and run its setup wizard once. It puts the SDK in `~/Library/Android/sdk`, and Gradle fetches Android 15 (API 35) by itself.
2. Install **JDK 21** with `brew install --cask temurin@21`. Android Studio's bundled JDK 25 is too new for Gradle 8.11; the scripts pick a JDK 17–23 automatically.
3. On the phone, tap Settings → About phone → Build number 7 times, then turn on **USB debugging** in Developer options. Plug the phone in and accept the prompt.
4. Run `scripts/doctor.sh` until everything shows ✓.

## Layout

```
app/                 the app (Kotlin, Compose, Sora editor; Python runner in src/main/python)
probe/               Key & Gesture Probe: the Phase 0 keyboard diagnostic app
scripts/             doctor, install, logcat, test-python, debug-run, adb-checks, pull-report
PLAN.md              what the app does and how it's built
PROGRESS.md          status, work log, decisions and plan history
docs/device-input-report.md   what the Titan's keyboard and touch surface send
docs/test-checklist.md        manual checks on the phone, by feature
docs/PROBE.md        how to run the probe
```
