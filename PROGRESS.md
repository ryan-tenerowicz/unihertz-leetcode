# Titan Code: progress

This file tracks **where we are** against [`PLAN.md`](PLAN.md): what's done, what's next, problems we hit, and every change made to the plan. `PLAN.md` is the design; this is the log.

**How to update:**
- Keep the milestone table and **Next up** current.
- Add new entries to the top of **Work log** and **Plan change log**, with the date.
- When a problem forces a plan change, record the problem in the work log and the change in the plan change log, then edit PLAN.md.

---

## Status

| Milestone | Status | Notes |
|---|---|---|
| **M0**: skeleton, scripts, Key & Gesture Probe, device report | ✅ **Done** (2026-11-01) | K11 optional and unrun. Evidence: `docs/device-input-report.md`. |
| **M1**: editor and input core | ✅ **Done** (2026-11-02): owner walked the M1 test plan ("mostly works as expected"); Find/Go-to removed and saving made silent on request. The real Cursor Assistant fixture is still open (see Next up); the E01–E15 panel run was dropped with the panel (rev 26). | Scope and exit criteria: PLAN §12. |
| **M2**: read LeetCode, offline-first | 🔨 **Built, owner check pending** (2026-11-02): test with [`docs/m2-test-plan.md`](docs/m2-test-plan.md) | Agent decisions: PLAN rev 9, Appendix B |
| **M3**: run and submit on LeetCode | 🔨 **Built, owner check pending** (2026-11-02): see [`docs/owner-test-queue.md`](docs/owner-test-queue.md) | Needs a signed-in run to confirm the response parsers |
| **M4**: local runner and offline packs | 🔨 **Built, owner check pending** (2026-11-02): see the owner test queue | Special types rev 23; limits, checkers and stress tests rev 24; stdin rev 13; Everything packs rev 25 |
| **M5**: polish | 🔨 **Started** (2026-11-02): word completion built; snippets removed on request (rev 13) | Themes and tuning wait for the owner's use |
| **M6**: more platforms | 🔨 **Built, owner check pending** (2026-11-02, rev 13): NeetCode (neetcode.io) and Codeforces: browse, read offline, run locally, submit | Needs signed-in runs on both sites to confirm the result parsers and the Codeforces submit form |

**Last commit:** the owner's commit of M1 steps 1–2 and the gesture work (2026-11-01). Steps 3–11 are uncommitted (built 2026-11-02 while the owner was away). **Work on `master`; commit only when the owner asks.**

---

## Next up

**Running list of owner tests: [`docs/owner-test-queue.md`](docs/owner-test-queue.md)** (owner, rev 10: keep building, don't wait for confirmation).

**M2, with the owner:**
1. Walk [`docs/m2-test-plan.md`](docs/m2-test-plan.md): setup checklist, list download, filter, open/save offline, Comments, sign-in, Settings, draft history, airplane mode.
2. Review the rev 9 *agent decisions* (PLAN Appendix B).
3. Exit check (PLAN §12): in airplane mode, a problem saved earlier opens with statement and comments; drafts survive a force-stop.

**Left over from M1 (not blocking):** record the real Cursor Assistant fixture.

**Next to build (rev 17 roadmap, PLAN §14):**
1. Fixes from the owner's checks (owner test queue: revs 13–16).
2. **M7 stage 1 (rest):** block dedent; word/line jumps and move line once the owner agrees to the bindings (`sym+A/F` word, `sym+B/G` line start/end, `sym+Z` dedent, `sym+M` move line).
3. **M7 stage 2:** live syntax check, then pyflakes warnings, and gutter marks from local runs.
4. **M7 stage 3:** `sym+Q` peek at examples; highlighted solution code with "open in scratch editor"; compare with draft history, submissions or solutions.
5. ✅ **M7 stage 4** and ✅ **the offline judge** (rev 24). Left: stress tests for design problems and the special types; more checkers as problems come up.
7. Later and only if wanted: themes; Codeforces remote run, contests and editorials.

## M1 steps (done)

1. ✅ **Scripts:** add the `app` target to `install.sh`, `doctor.sh`, `logcat.sh` and `pull-report.sh` (PLAN §11).
2. ✅ **Skeleton, and an early risk check** (passed 2026-11-01, see the work log):
   - Create the `:app` module (`dev.titan.code`) and the Gradle version catalog.
   - Add Compose with the compiler plugin, and Sora Editor with `language-textmate`.
   - Pin versions that build with AGP 8.7.3 / Kotlin 2.1.0 / JDK 21, and record them below.
   - **Before building anything else:** put a `CodeEditor` subclass on screen with a TYPE_NULL `InputConnection` and pre-IME key handling. Check that typed characters, Backspace and arrows reach it, and that Sora's own input handling doesn't fight it. If it does, record the problem here and switch to the CodeMirror 6 WebView fallback (PLAN §3).
3. ✅ **Shell** (2026-11-01; red button and keys on other panes still to be checked by the owner):
   - single activity; status bar hidden; top band beside the camera (reuse the inset logic in `probe/.../ProbeActivity.kt`)
   - four tabs
   - bottom status line
   - Back handling (PLAN §5.1)
   - key routing on non-Code panes through `MainActivity.dispatchKeyEvent()` (PLAN §5.1)
4. ✅ **Bundled samples** (2026-11-02): Two Sum, Group Anagrams and Valid Parentheses in `app/src/main/assets/samples/`; the Problem-pane WebView with a sample picker; minimal draft autosave plus one backup file per sample.
5. ✅ **InputRouter** (2026-11-02):
   - TYPE_NULL, characters through `getUnicodeChar`
   - Alt layer; Alt+Shift+S/D/F/Z → `$ % ^ &`
   - Shift+Backspace forward delete
   - `sym` leader table, with "Coming in M3/M4" for R/E/S
   - arrow keys; app-side key repeat
   - keycode 403 (`fn`) and 404 handling
   - JVM unit tests for `KeymapEngine`
6. ✅ **`fn` symbol palette** (2026-11-02; owner check pending, adb can't send `fn`): typing a key closes it and types the key.
7. ✅ **GestureEngine** (synthetic Cursor Assistant fixture added 2026-11-02; real recording at step 12):
   - `touchPad` interception in `MainActivity.dispatchTouchEvent()`, with the starting values from PLAN §5.6
   - fixtures in `app/src/test/resources/phase0/`, trimmed from the guided report
   - the Cursor Assistant arrow fixture: synthetic for now; record the real one with the owner at step 12
8. ✅ **Red button** (owner confirmed the shell works, 2026-11-02): keycode 401 toggles Problem/Code; on Results or Comments it goes to Code. Built with the shell in step 3; owner check pending.
   - ✅ PTT app switched to Titan Code; keycode 401 reaches the app (2026-11-01).
   - ✅ The red button's "programmable key" is None (`func1_programmable_key_function=0`).
9. ✅ **Python editing** (PLAN §5.5; 2026-11-02):
   - auto-pairing, with no pairing inside strings or comments (a one-line scanner, not the TextMate tokens: PLAN rev 8)
   - smart Enter, smart Backspace
   - undo grouping for typing
   - Sora's own auto-indent and pair-closing switched off
10. ✅ **Find and Go-to-line bars, font size** (`sym+I/O`, saved), **soft-wrap toggle** (`sym+W`, saved; on by default), and the **cheat sheet** (`sym+H`, generated from the keymap).
11. ✅ **Guided acceptance panel** (removed in rev 26 at the owner's request) (debug builds only, PLAN §10): a collapsible panel inside `MainActivity` over the real shell, with its JSON/Markdown report and `pull-report.sh app`.
12. **Acceptance:**
    - Walk through [`docs/m1-test-plan.md`](docs/m1-test-plan.md) (critical user journeys), then run E01–E15 with the owner on the acceptance panel.
    - Record the Cursor Assistant fixture.
    - Tune the gesture values and record them below.

---

## Environment and phone state (2026-11-01)

**Mac:**
- Temurin JDK 21 at `/Library/Java/JavaVirtualMachines/temurin-21.jdk`. `scripts/env.sh` picks it, because Android Studio's JBR is JDK 25, which Gradle 8.11 can't run on.
- Android SDK at `~/Library/Android/sdk`, with build-tools 36.0.0 and android-35, which Gradle fetched itself.
- `adb` from Homebrew.

**Phone** (Titan 2 Elite, Android 16, build `Titan 2 Elite_V02.00.04`):

| Setting | Current value | What the app needs |
|---|---|---|
| Scroll Assistant | ON | **ON** (required) |
| Cursor Assistant | ON | Either. With Scroll Assistant on, its slides arrive as Scroll Assistant strokes and move the cursor the same way. |
| Red button → Shortcut keys → PTT app | **Titan Code** (`func1_smartkey_ptt_app_picker=dev.titan.code`) | Titan Code ✓ |
| Red button "programmable key" (`func1_programmable_key_function`) | 0 (None) | None ✓ |
| Keyboard backlight tile | Owner's choice | Not used by the app |
| Default IME | Unihertz keyboard (`com.iqqijni.bbkeyboard`) | Unchanged |

**Pinned versions** (M1 step 2, in `gradle/libs.versions.toml`):
- Compose BOM **2025.05.01** (Compose 1.8.2, Material 3 1.3.2). Later BOMs move to compileSdk 36.
- activity-compose **1.10.1** (1.11 needs compileSdk 36).
- Sora Editor BOM **0.24.4** (`editor`, `language-textmate`). Its `language-textmate` pulls kotlin-stdlib 2.2.21, the newest that Kotlin 2.1.0 can read. **0.24.5+ needs stdlib 2.3+, and 0.24.7 needs 2.4**, so upgrading Sora means upgrading Kotlin first. Fallback: 0.23.7 (stdlib 2.1.21).
- Compose compiler plugin: same version as Kotlin (2.1.0).
- JUnit 4.13.2; OkHttp 4.12.0 (probe only, now through the catalog).

**Gesture tuning** (fill in during M1 step 12):
- Starting values are in PLAN §5.6.
- 2026-11-01 (owner, step 2 try-out): vertical slowed from 60 to **90 px/line**; horizontal slowed from 40 to 60, then to **70 px/char** (acceleration unchanged, up to 4×). Owner: "for now this is good". Both become adjustable settings in M2 (PLAN §5.6). Horizontal swipes now stop at the line's start or end.

---

## Open questions for the owner

| Question | Raised | Blocks |
|---|---|---|
| Should word-wise movement and move-line-up/down get a leader letter? Their gestures proved impossible (G06/G07). | 2026-11-01 | Nothing |
| Does `=` need a keyboard binding after trying the `fn` palette? | 2026-11-01 | Nothing; decide after using M1 |
| Run K11 (per-letter Unihertz shortcuts)? It only matters if those shortcuts are enabled. | 2026-11-01 | Nothing |
| `sym+F` and `sym+G` are free since Find/Go-to were removed. Use one for block dedent, or keep them free? | 2026-11-02 | Nothing |
| Should **dedenting** a selected block get a leader letter (`sym+Z` is free)? `sym+Space` now indents a selected block (rev 8). Dedent at the start of a line is covered by smart Backspace. | 2026-11-01 (M1 review); narrowed 2026-11-02 | Nothing |
| Review the *agent decisions* of revision 8 (PLAN Appendix B), made while the owner was away | 2026-11-02 | Nothing |
| Problem pane surface-scroll direction: drag-style (finger up = content up). Flip it? | 2026-11-02 | Nothing |

---

## Work log

### 2026-11-03 (rev 28): the reading size scales the panes' controls too

**Owner asked:** the text size should also scale the menu elements in each pane (site selection, filters, buttons), keeping their relative sizes.

**Done:** `ReadingScale` now scales the Compose density (dp and sp together) instead of only the font scale, and wraps the whole `ProblemList` and `SolutionList` and the `StatementBar` / `CommentsBar`. The row-only scaling and widened columns from rev 27 are gone (they'd double up). The site row, difficulty/status/Saved chips and list chips are `FlowRow`s, so they wrap instead of running off the edge. On the phone at 150%: the site buttons, filter box, 🎲, ⟳, chips (Saved wraps to a second line), Daily row, list rows, ☰ Problems bar and the Comments bar all grew together. Set back to 100% afterwards. Tests pass; installed.

### 2026-11-03 (rev 27): review queue removed; reading text size

**Owner asked:** remove the review functionality (pick problems by hand or with the random selector only), and add a text size for the Problem and Comments panes.

**Done:**
- **Review removed:** `ReviewQueue`, `Repository.reviewDue`, `StoreDao.submissionBriefs`/`SubmissionBrief`, `ProblemFilter.reviewOnly`, the list's `due` state, Review chip and ↻, `Prefs.reviewDays`, the Settings row and its test. The practice timer and submission times stay. No database change (the review queue read the submissions table and stored nothing).
- **Reading size:** `Prefs.readingZoom` (70–200%, `READING_ZOOMS`); `MainActivity.textSize/changeReading/applyReadingZoom` set WebView `textZoom` on the Problem and Comments views; `ui/ReadingScale.kt` scales `sp` for the problem list rows (number and difficulty columns widen with it) and the solution list. `Leader.FONT_UP/DOWN` now work on every pane (`editsText = false`): Code → editor font, Problem/Comments → reading size, Results → a hint. Settings → Editor: "Editor font size" and "Problem and Comments text size".

**On the phone:** Settings showed the new stepper and no Review row; at 150% the Codeforces statement and the LeetCode list rows were larger and the chips unchanged; the Review chip is gone. Set back to 100% afterwards. Seen while testing, not changed: Codeforces statements show each math variable twice (e.g. "a a").

**Tests:** `LeaderTest` updated (I and O are shell commands now); `PracticeTest` keeps the timer tests. All pass; installed.

### 2026-11-03 (rev 26): acceptance panel removed

**Owner asked:** remove the tutorial for walking through all the tests (the "✓ E01 Type Two Sum" strip above the status line).

**Done:** deleted the panel (`src/debug/.../acceptance/Acceptance.kt`), its release no-op and the `AcceptanceHost` interface; `MainActivity` no longer implements it (event log hooks, sample picking, reference view, palette tap count removed). The test buffer flag stays: the test-case editor uses it. `DebugRunReceiver` stays in `src/debug`. Debug and release builds compile; Kotlin tests pass; installed, and the strip is gone on the phone. `scripts/pull-report.sh app` has nothing to pull now (the probe's reports still work).

### 2026-11-03 (rev 25): download everything

**Owner asked:** a button to download all available problems from each website, to save as many as possible.

**Built (PLAN §8.4):**
- `Repository.createEverythingPack` (list refresh, falling back to the saved list; pack source `all:<platform>`, updated in place when pressed again), `freeProblemCount`; `StoreDao.freeProblemCount`, `setPackSlugs`.
- `Repository.downloadPack` in two passes (statements, then discussions) with per-problem tolerance; `Repository.patiently` (in the companion, tested) for rate-limit waits, three network tries and fatal errors; per-site pacing; `PackProgress`/`PackOutcome`. Removing an Everything pack keeps its data.
- `PackWorker` as a foreground job: `Sync.foregroundInfo` (channel "Offline downloads", Stop via `createCancelPendingIntent`), phase and skipped count in progress, skipped count in the output; linear 30 s backoff; network-type errors always retry. Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, WorkManager's `SystemForegroundService` as `dataSync`. WorkManager 2.9.1 → 2.10.0.
- Settings: three "Everything on …" rows with counts, size and time estimates, Download/Update; packs show "statements 56/788, 2 skipped", "paused; resumes by itself", "done; N couldn't be saved"; Stop instead of Remove for Everything packs.

**On the phone:** the rows showed LeetCode 3,145 free problems, NeetCode 788, Codeforces 11,488. Download on NeetCode asked for notifications (allowed), ran as a foreground job with the notification, and saved about 1.5 problems a second; saved statements went 575 → 602 in the first 20 s. A reinstall mid-run stopped it; WorkManager resumed the same job when the app started, and it finished: 788/788, 408 new, 0 skipped, about 8 minutes in all.

**Tests:** `PackDownloadTest` (4: rate-limit waits, three network errors, skipped problems, fatal errors). All Kotlin tests pass; installed.

### 2026-11-03 (rev 24): offline judge, test-case editor, practice timer, review queue

**Owner asked:** implement M7 stage 4 (test-case editor, practice timer, review queue) and the proposed offline judge (per-case time limit with the line, memory and output limits, checkers, stress tests).

**Built:**
- **Limits** (`titan_limits.py`, PLAN §7.1): watchdog + `PyThreadState_SetAsyncExc`; `Limit` exceptions derive from `BaseException`; RSS check; capped stdout; progress file after each case; `restart` for calls that can't be interrupted. `titan_runner.py` reorganised around one `_cases` loop for both the LeetCode and stdin modes; `call_solution` shared with the stress tests; source lines in tracebacks. Kotlin: `LocalJudge.CASE_LIMIT_MS`, `overallLimitMs`, `parseKilled`, limit verdicts; `RunnerClient` restarts on `restart` and raises `RunnerCrashedException`.
- **Checkers** (`titan_checkers.py`, §7.2) and an any-order override set.
- **Stress tests** (`titan_stress.py`, §7.3): constraint parser and generator, ten custom generators, reference validation, small-to-large search, one full-size speed check. Kotlin: `Repository.references` (NeetCode article per approach, LeetCode posts; `codeBlocks`), `LocalJudge.stressRequest/parseStress/constraintsText` (`<sup>` → `^`, `<sub>` → `_`), `Outcome.Stressed`, the Results view with "Save as a test case" (`addCustomCase` now takes an expected output).
- **Test-case editor** (`data/CaseBuffer.kt`, *agent decision* to edit in the Code pane): render/parse with a strict JSON check; `MainActivity.editCase/saveCaseEdit/endCaseEdit`; `sym+S` saves and Back cancels while editing; `Repository.saveCase`, `StoreDao.setUnverifiedExpected`. Results: ＋ New test, ✎ per case, "Expected (yours…)".
- **Practice timer and review queue** (`data/Practice.kt`): `PracticeTimer`, `ReviewQueue`; DB **v5** (auto-migration: `timers` table, `submissions.elapsedMs`); status-line reading, submission times in history, Settings → Practice (timer on/off and reset, review after off/7/14/30 days), Review chip and ↻.
- **Debug tool:** `DebugRunReceiver` (debug builds only, `src/debug/AndroidManifest.xml`) and `scripts/debug-run.sh` run a harness request on the phone without the editor.

**Measured:** stress tests over the NeetCode 150 (LeetCode statements from the public API, NeetCode's free articles, its approaches checked against each other): first pass 110 clean and 17 false differences, all from inputs breaking a promise of the statement (one answer only, sorted, valid RPN, a tree plus one edge…). After the checkers, custom generators and generic fixes: **126 clean, 1 real time limit** (Swim in Rising Water's brute force), 16 not covered (10 design, 5 special types, 1 with one approach). The generator also handles all 128 non-design NC150 statements without error.

**On the phone** (Python 3.14.8, via `scripts/debug-run.sh`): a `while` loop stopped at 1.5 s at line 4 with the next case still passing; memory stopped at +300 MB at the `append` line; `print` in a loop stopped as Output Limit Exceeded with 20,037 characters kept; `time.sleep(60)` gave up after the grace period with `restart` and the runner came back for the next request; a stress test found a planted Two Sum bug on its 2nd input; BST Iterator (annotation path) passed. The app started on the new database (v5) and opened the owner's problem. The new buttons weren't seen on screen: the phone was locked by then.

**Tests:** 44 harness tests (`scripts/test-python.sh`), new Kotlin tests: `LocalJudgeTest.limitsAndKilledRuns`, `stressRequestAndResult`, `referenceCodeBlocks`, `CaseBufferTest` (5), `PracticeTest` (4). All pass; installed.

### 2026-11-03 (rev 23): special types in the local runner

**Owner asked:** after a discussion of what can't run offline (infinite loops, unsupported types), implement graph, linked-list and other custom-type parsing.

**Research:** pulled `metaData` and examples for ~30 such problems from LeetCode's public GraphQL and the free NeetCode ones from its metadata function. LeetCode marks them `"manual": true` and often lists inputs the function doesn't take (`pos`, `skipA`, `bad`, `pick`), or a different type than the function gets (`integer[][]` for Clone Graph's `Node`, `integer` for an n-ary root). NeetCode's starters use the same problems, keyed in our catalog by LeetCode number.

**Built:**
- `app/src/main/python/titan_drivers.py`: drivers by LeetCode number (PLAN §7 table), the judge's classes (`Node` variants, `NestedInteger`, `MountainArray`, `Employee`, `Iterator`, `isBadVersion`, `guess`, `rand7`), and design-argument conversion by declared types or annotations.
- `titan_runner.py`: the request carries `number`; a driver bypasses the type check; cases can carry a `note` and `unchecked`; cases are appended before running (so `continue` keeps them).
- `LocalJudge`: sends `number` (0 for Codeforces); reads `note`, and `unchecked` blanks the expected value. `Judge.Case.note`; the Results pane shows **Note**.
- `NeetCodeApi`: keeps extra named inputs after the parameters (`orderedNames`), saves `inputNames` for remote runs, parses `root = […]` tests on class problems, `[ops], [args]` class examples (BST Iterator showed `["BSTIterator","next",…]` with empty arguments), and sizes `linesPerCase` from the parsed tests. `PARSER = 23` in metaData; `Repository.isStale` makes `openProblem` download older NeetCode copies again when online.

**Tests:** 13 new harness tests (29 in all, `scripts/test-python.sh`) built on the real examples, including wrong answers each driver must catch (graph not copied, equal-but-not-shared intersection node, 101+ `MountainArray.get` calls, broken `prev` links, `rand10` out of range). `NeetCodeApiTest.specialInputs` uses saved NeetCode payloads (cycle, clone graph, Codec, BST Iterator). All Kotlin tests pass; installed.

**On the phone:** BST Iterator (NeetCode) re-downloaded on open with the fixed test case; Run used Python 3.14.8 and the harness (with the drivers module) loaded; the starter's empty methods gave the expected IndentationError. A passing run of a driver on the phone needs a written solution, so it's in the owner test queue. I opened BST Iterator to check this and then reopened the owner's Valid Palindrome II (cursor where it was); the list filter is cleared.

**Not run:** the harness under 3.14 on the Mac (downloading a standalone 3.14 was declined by the permission check); the on-device run above covers loading, and annotation reading falls back to plain values if 3.14 can't evaluate one.

### 2026-11-03 (rev 22): hide paid problems; random problem

**Owner asked:** why Valid Word Square appears under NeetCode (it's not on the site for them); hide Pro problems on NeetCode and LeetCode; add a random-problem helper for the current view.

**Why it appeared:** the NeetCode catalog is neetcode.io's own problem table, which includes 151 problems flagged `pro`. Valid Word Square is also `premiumAlgo100`, NeetCode's set of ~100 LeetCode-Premium problems for Pro subscribers. The site doesn't show those to non-Pro users; the app showed them with 🔒. (NeetCode's metadata function did return a Pro statement anonymously, Lonely Pixel I, but no starter code.)

**Built:** `ProblemListModel.available` drops `paidOnly` problems before any filter, on all sites; the count shows shown/available. NeetCode went from 939 to 788 and LeetCode to 3145. `ProblemListModel.random()` plus a 🎲 button beside ⟳ (`openRandomProblem`): it picks from `shown`, preferring not-solved. `ProblemListModelTest` (4 tests); all pass. Checked on the phone: Med + 🎲 on NeetCode opened Grumpy Bookstore Owner.

### 2026-11-03 (rev 21): stronger Strong

**Owner:** slow swipes on the strongest setting still too weak. **Changed:** `Level.STRONG` slow-step pulse 24 → 40 ms, line edge 30 → 50 ms; fast swipes unchanged at 9 ms. Normal unchanged. Test updated; all pass; installed.

### 2026-11-03 (rev 20): adaptive swipe vibration

**Owner:** the icon stays as it is. Vibration feels OK on long swipes but should be more distinct for fine, character-by-character moves; rev 19's Light (6 ms) was barely noticeable, so that should be the minimum and it should only get stronger.

**Built:** `SwipeHaptics.pulseFor(gap, level)`. The pulse length follows the time since the previous cursor step: 150 ms or more gives the level's longest pulse (Normal 16 ms, Strong 24 ms), 40 ms or less its shortest (6 or 9 ms), linear between. The line edge gives 22 or 30 ms. Levels Off / Normal (default) / Strong; rev 19's stored FAINT or LIGHT maps to Normal. `SwipeHapticsTest` (4 tests); all pass; installed. The feel is unverified (adb can't make keyboard-surface touches).

### 2026-11-03 (rev 19): fainter vibration; name and icon

**Owner asked:** make the vibration a very small jolt; give the app a proper name and image. Owner chose the name **Coding**.

- **Vibration:** `dumpsys vibrator_manager` showed our TICKs lasting 52–86 ms each, with every swipe tick cancelling the previous one, so it felt like a buzz. TEXTURE_TICK is ~56 ms too. A one-shot of 4 ms plays as 9 ms, the smallest jolt this motor (no amplitude control) makes. `SwipeHaptics` now uses one-shot pulses: **Faint** (default) 3 ms per step and 8 ms at the line edge, **Light** 6 ms and 14 ms, or **Off**. Settings has the three buttons, and choosing one plays a sample. The pref key changed (`swipeHapticsLevel`).
- **Name:** launcher label `Coding` (`strings.xml`); setup screen and bundled-statement note updated. The package stays `dev.titan.code`: renaming it would make a new app and lose drafts and sign-ins.
- **Icon:** adaptive vector icon (`mipmap-anydpi/ic_launcher*.xml`, `drawable/ic_launcher_{background,foreground,monochrome}.xml`): a near-black tile, white `< >` brackets, and the editor's yellow caret with a fading trail; a monochrome layer for themed icons. Checked on the phone's App info page.

### 2026-11-03 (rev 18): swipe vibration, indent guides, bracket highlight (M7 stage 1)

**Owner asked:** vibration while swiping, indent guides and bracket highlighting.

**Built:**
- **`input/SwipeHaptics`:** a TICK per step that actually moved the cursor (character or line), and a CLICK once when a sideways swipe bumps the line's edge. Steps that don't move the cursor (already at the edge) stay silent after that click. Uses `VibrationAttributes.USAGE_TOUCH` and the `VIBRATE` permission. The vibrator only has predefined effects (`dumpsys vibrator_manager`: CLICK, DOUBLE_CLICK, TICK, HEAVY_CLICK, TEXTURE_TICK; no primitives or amplitude), and TEXTURE_TICK's constant is hidden from apps. Setting `Prefs.swipeHaptics`, default on.
- **`editor/IndentGuides`:** Sora draws in-text block lines only with soft wrap off (the default here is on). Ours draw in leading whitespace on leading rows, with blank lines bridged and the cursor's block brighter. Sora's own are made transparent; its gutter block marker stays. Added `folding.offSide` to the Python language configuration so Sora computes blocks.
- **Bracket pairs:** `isHighlightBracketPair`, with colours set on the scheme.
- Checked on the phone by screenshot (guides at every level, active guide brighter, `[i]` pair boxed). **Not checked: how the vibration feels.** `adb` can't produce keyboard-surface touches. All JVM tests pass.

### 2026-11-03 (rev 16): smoother swipe speed

**Owner reported:** moving the cursor slowly over one or two characters sometimes jumps a whole word; speed changes feel exaggerated.

**Cause:** the old gain `1 + speed/1000` used each sample's own speed. Phase 0's "slow" swipe has samples 4–43 ms apart and per-sample speeds of 300–1800 px/s, so the gain flickered between 1.3× and 2.8× within one gentle movement, and a landing finger's 30–40 px lurch got the high gain. The phone's log only had per-stroke summaries, so the diagnosis is from the Phase 0 recordings plus the summaries.

**Fix (GestureEngine):** speed over an 80 ms window (floor 30 ms); 1× below 700 px/s, linear to 3.5× at 2800 px/s (max was 4×); no gain in a stroke's first 100 ms. Simulated on Phase 0: a slow fine move went from 3 characters to 2, a lurch-then-creep from 2 to 1; the slow full-width swipe now covers about 13–15 characters instead of 24, while a 4000 px/s flick still covers 25+. Settings has speed-up Off / Gentle / Normal (`Prefs.swipeAccel`). Strokes now log their raw samples for tuning. 4 new tests; all pass; installed.

### 2026-11-03 (rev 15): cursor trail

**Owner:** signed in to NeetCode and submitted a problem; it works. From now on the focus is the editing and viewing experience. Asked for a small animation when the cursor moves, like a quickly fading trail, to see where it came from.

**Built:** `editor/CursorTrail.kt`, drawn by `TitanEditor.onDraw` over the text. On a cursor move that isn't an edit (swipe, arrows, tap, undoing a stray stroke), a band in the caret colour runs from the old caret to the new one, brightest at the cursor, and fades out in 260 ms. Moves within 120 ms of each other join into one streak, so a swipe leaves a comet tail. Points are in content coordinates, so the trail stays on the text when the view scrolls. Typing, other edits, loading a draft and `sym+N` don't draw one. Checked on the phone with a screen recording (arrow-key moves). `TitanEditor.trailEnabled` is there for a Settings switch if wanted.

### 2026-11-02 (rev 14): typing moved the cursor

**Owner reported:** while typing, the cursor jumps without meaning to move it (a new word slides to the next line); a Space after a `fn`-palette symbol often lands earlier in the line.

**Cause, from the phone's log:** the keyboard surface reported strokes that were really fingers on the keys. One stroke lasted 8.8 s across ten keypresses, another 4.7 s across `sym+U` ×3, Space and `fn`, and moved the cursor just before the next Space. A press also makes a quick 30–60 px slide just before the key registers; with horizontal acceleration that's 1–2 characters left. The 24 px start threshold can't catch either, and a time gate would break real fast flicks (Phase 0 had a 49 ms swipe ending in `ACTION_CANCEL`).

**Fix:** `GestureEngine.onKey` (fed by `InputRouter` through `Shell.onTypingKey` for every key except Shift, Back, arrows and the swipe marker). A key down mid-stroke undoes movement from the last 300 ms and ends the stroke; strokes starting while a key is held or within 150 ms of a key are ignored; held keys are forgotten on focus loss. The undo mark is now taken at the first move after a 300 ms pause rather than at the stroke's start. 6 new tests (116 JVM tests pass); debug build installed.

### 2026-11-02 (rev 13): owner changes; NeetCode and Codeforces as platforms

**Owner asked:** remove snippets; Back must never leave the app; continue Codeforces and NeetCode so problems can be browsed and submitted on all three sites, with site-specific Results where needed and the same editor.

**Done**
- **Snippets removed** (`Completer`, cheat sheet, strip, tests). Word completion stays.
- **Back:** on the problem list it returns to the open problem; nothing calls `moveTaskToBack` any more.
- **Platform layer** (`data/Platform.kt`): prefixed keys (`nc:…`, `cf:…`; LeetCode unchanged), `Repository` dispatches per site; drafts, test cases, history, packs and solved marks all work per site with no DB migration (the `provider` column already existed).
- **NeetCode** (`data/neetcode/`): bundled 939-problem catalog (`assets/neetcode/catalog.json`) with NeetCode 150 / Blind 75 / NeetCode 250 / By pattern; statements, hints, images and the solution article from `getProblemMetadataFunctionHttp`; Run/Submit via Firebase functions with a refresh token from the sign-in page (encrypted); metaData built from type hints so the local runner works; inputs converted to/from `name=value`.
- **Codeforces** (`data/codeforces/`): problem list from the API (11,488 on 2026-11-02); statements through a hidden WebView + jsoup (limits, samples, tags, rating, TeX → text); Submit fills the site's own form in the hidden WebView, verdict from `user.status`; language setting (PyPy 3 64-bit default).
- **Runner:** stdin/stdout mode (`open(0)`, `sys.stdin.buffer`, `exit()`; token compare, float 1e-6, YES/NO any case). Python tests 12 → 15.
- **UI:** LeetCode / NeetCode / Codeforces switch on the list; Codeforces numbers like `1850A` and ratings; Settings → Accounts for all three plus Codeforces language; NeetCode/Codeforces sign-in page with popup support; Results: site names, Codeforces verdict view with "Open submission", stdin hints; Comments: NeetCode solution article, Codeforces explains there are none.
- **Tests:** 110 JVM tests (+`NeetCodeApiTest` 8, `CodeforcesApiTest` 6); fixtures in `src/test/resources/neetcode/` (free problems, solution code stripped) and `codeforces/` (API replies; the statement page is hand-written to the site's markup).
- **Checked on the phone:** NeetCode list and NeetCode 150 headers; NeetCode Two Sum statement, examples with expected outputs, local run; its solution article in Comments; Codeforces list download, `4a` filter, 4A statement through the hidden WebView (maths rendered), local stdin run **Accepted**; NeetCode sign-in page and its GitHub popup open; Codeforces login form loads.

**Problems hit**
- **neetcode.com isn't NeetCode:** it redirects to an unrelated site. Everything uses neetcode.io.
- **Codeforces statement pages are behind Cloudflare** for plain clients (HTTP 403 "Just a moment"); the API isn't. Hence the hidden WebView.
- **Desktop-site identity backfired:** the first Codeforces login load showed the mobile home page, so the WebViews were switched to a desktop user agent. Cloudflare then demanded an interactive human check, so it was reverted. The next load of `/enter` showed the login form normally. The signed-in check now needs a logout link, then takes the profile link next to it.
- **NeetCode sign-in is Google or GitHub only** (Firebase popup). Google may refuse to sign in inside a WebView; GitHub's popup opens fine. Untested end to end (needs the owner's account).


### 2026-11-02 (rev 12): M5 completion, M6 NeetCode lists

**Done**
- **Completion:**
  - `editor/Completer.kt`: snippets, buffer words, and keywords/builtins/LeetCode names.
  - `PyEdit.wordPrefix/complete/words`, where a snippet tab is one indent level and `$0` marks the cursor.
  - `InputRouter`: `sym+Space` asks the shell to accept first.
  - `CompletionStrip` UI, and the cheat sheet lists snippets.
  - Checked on the phone: "bf" offers ⧉ bfs; `sym+Space` expands it at the right indent with the cursor in `deque([|])`. My test edits were reset with `sym+N`.
- **NeetCode lists:**
  - Extracted 150 and 75 entries (LeetCode slug + pattern) from neetcode.io's bundle and bundled them as `assets/lists/neetcode.json`.
  - `ProblemFilter.collection` keeps list order; chips and pattern headers in the list; offered as packs.
  - Checked on the phone: NeetCode 150 shows 150/3682 with headers.
- **Tests:** 98 JVM tests (Completer, collections), plus 12 Python.

### 2026-11-02 (rev 11): M4

**Done**
- **Chaquopy 17.2.0, Python 3.14** (the Mac has only Python 3.9, so there's no pip and no build Python; `sortedcontainers` 2.4.0 is vendored; `.py` ships as source; arm64 only). APK about 51 MB.
- **The runner:**
  - `runner/RunnerService` lives in `:runner`; Python starts at bind (the warm spare).
  - `RunnerClient` uses Messenger, a 10 s limit, kills the process by pid on timeout, and rebinds automatically.
  - `runner/LocalJudge` builds the request, picks the mode, reads expected outputs from the statement, and maps results to `Judge.RunResult`.
- **The harness:** `app/src/main/python/titan_runner.py`, 12 tests in `app/src/test/python/` run by `scripts/test-python.sh`; all pass on the Mac.
- **Results:** "Run locally", per-case ms, case errors, "Go to line N", expected values marked "from the statement" or "LeetCode", and a compact tab badge. `sym+R` and ▶ Run run locally again; `sym+E` and long-press run on LeetCode.
- **Offline packs:**
  - Room v4 adds `packs`.
  - Study plans via `studyPlanV2Detail` (checked anonymously: Top Interview 150 = 150 problems, LeetCode 75 = 75 …).
  - "the list as filtered now" as a pack.
  - `PackWorker` and a weekly `RefreshWorker` (WorkManager 2.9.1), Wi-Fi only by default.
  - Settings: rows with progress, add, remove, and mobile data.
  - `StoreDb` is now one instance per process.
- **Tests:** 92 JVM tests, plus 12 Python tests.
- **On the phone:**
  - DB migrations v1→v4 kept the data.
  - The runner process starts, and a local run took **73 ms** (Python 3.14.8); a compile error with "Go to line 8" worked.
  - The weekly refresh worker succeeded.
  - The **Binary Search pack downloaded in the background** (10/32 after 25 s).

**Problems found and fixed**

| Problem | Fix |
|---|---|
| No Python 3.14 on the Mac for Chaquopy's pip | Vendor the pure-Python package; disable `.pyc` compilation of app sources |
| `TitanRunner` debug logs were hidden by the phone's global log level | `env.sh` sets `log.tag.TitanRunner V` |
| "in <strong>any order</strong>" wasn't detected | Strip tags before matching |
| The Results tab label wrapped onto two lines | One line, ✓/✗ badge only |

### 2026-11-02 (rev 10): Comments redesign, Back, M3

The owner, after trying M2: everything works well. Asked for:
- Comments opening on Solutions (first tab), shown like LeetCode as post titles that open to the body and all comments
- foldable code that starts folded in comments
- Back on a statement going to the problem list, with the screen back swipe captured

They also asked me to keep building without waiting, and to keep a running test list (`docs/owner-test-queue.md`).

**Done**
- **Comments:**
  - `SolutionList` is a native list of 20 posts with title, author, votes, comment count and summary; tap or Enter opens a post.
  - A post page shows the body and comments with replies, 20 per page, with "Load more" through a `titan://` link the WebView intercepts. Scroll is kept on reload.
  - A `posts` table (Room v2, auto-migration). The top 3 bodies are saved with the problem.
  - Code folding in `Markdown` with `<details>`, plus detection of unfenced code.
- **Back:**
  - On a statement, Back opens the list, with the current problem highlighted.
  - On a post, Back returns to the solution list.
  - An `OnBackPressedDispatcher` callback is the fallback route.
- **M3:**
  - `data/leetcode/Judge.kt`: run and submit bodies, start and check parsers, status codes, and splitting example cases by `metaData`.
  - `LeetCodeHttp` gains GET and POST, plus the hidden-WebView `fetch()` fallback (`WebFetch`). Run, Submit and check use it first.
  - Repository: `runRemote`, `submit` (polls each second up to 60 s), test cases (examples + saved + custom), submission history, and the solved mark. Room v3 adds `test_cases` and `submissions` by auto-migration.
  - `ResultsPane`: verdict, cases, errors, stdout, the failing case, test cases with delete, "＋ Add test from clipboard", submission history, and the tab badge.
  - `sym+E`, `sym+R` and ▶ Run run on LeetCode; `sym+S` needs two presses, and the Submit button shows "Confirm?".
- **Tests:** 83 JVM tests (new: `JudgeTest`, folding, unfenced code, solution fields).
- **On the phone:**
  - the v1→v3 database migrations kept the drafts
  - the Solutions list, opening a post, and comments with replies
  - the Results pane with the 3 example cases split correctly, and the signed-out message
  - **`WebFetch` returned HTTP 200 for a GraphQL call** (temporary check, removed)

**Problems found and fixed**

| Problem | Fix |
|---|---|
| Comments often paste code without ``` fences, so indentation was lost and nothing could fold | Paragraphs that are mostly code lines become one code block, joined across blank lines |
| Anonymous `interpret_solution` from a non-browser client gets Cloudflare's "Just a moment" (HTTP 403) | Run, Submit and check go through the WebView first (PLAN §6.2 rev 10) |
| "Load more" reloaded the page at the top | `ProblemView.show(keepScroll = true)` restores the scroll after load |

### 2026-11-02 (later): M1 feedback and M2 built

The owner walked the M1 test plan: mostly as expected. Asked for: remove Find and Go to line (solutions are short); stop the save indicator updating (distracting); then continue.

**Done**
- **M1 changes:** Find/Go-to removed from `Leader`, `InputRouter` (the bar mechanism), `EditorActions`, `PyEdit`, the overlays and the tests. The title line no longer shows `editing`/`saved`/`starter`; it shows `● save failed` (red) only if a write fails, and `● test buffer` during acceptance tests.
- **M2 data:**
  - `data/leetcode/LeetCodeApi.kt`: GraphQL requests and parsers for the list, detail, daily, user status, discussion topic, comments, replies, solutions and solution body. Tested against real responses saved in `src/test/resources/leetcode/` (fetched anonymously on 2026-11-02).
  - `data/Http.kt` (`LeetCodeHttp`): OkHttp with the WebView's user agent and cookies, `x-csrftoken`, Referer/Origin, ≥250 ms spacing, challenge detection.
  - `data/AuthStore.kt`: Keystore-encrypted cookie store.
  - `data/db/Db.kt`: Room (2.6.1 + KSP 2.1.0-1.0.29) with problems, details, discussions, drafts, draft versions and meta. Schema exported to `app/schemas/`.
  - `data/Repository.kt`: seeds the bundled samples, migrates M1 draft files, refreshes the list in pages of 100, saves statements with their images, prefetches, fetches discussions, writes drafts on one background thread (flushed on pause), keeps version history.
  - `data/Html.kt`: statement cleaning, image rewriting, a small Markdown renderer. `data/ProblemFilter.kt`: the list filter.
- **M2 UI:** the Problem pane's list view (`ui/ProblemList.kt`) with type-to-filter, chips, Daily, marks; statements with tags, hints and similar problems (`ui/Pages.kt`); the Comments pane with Discussion / Show solutions / refresh; Settings (⚙), the setup checklist and the sign-in WebView (`ui/Screens.kt`); `sym+P`; the offline badge; Settings for indent 2/4, font, wrap, swipe speeds, prefetch count, storage, draft history.
- **Tests:** 70 JVM tests pass (new: `LeetCodeApiTest`, `ProblemFilterTest`, text input in `InputRouterTest`, 2-space starter). Debug and release build.
- **Checked on the phone over adb:** draft migration; the bundled Two Sum replaced by the real statement; setup checklist (PTT setting read as ✓); list download with progress (3,682); `sym+P`, typing a filter, down + Enter opens a problem; statement rendering; Comments with replies. Then I stopped, because someone was using the phone (pane switches I didn't make).

**Problems found and fixed**

| Problem | Fix |
|---|---|
| The list endpoint ignores `limit` above 100 | Page through it, 37 requests |
| Comment and solution text is escaped twice (`\n`) | `LeetCodeApi.unescape()` |
| Two JVM signature clashes (`setOnline`, `setFilter` vs Compose state setters) | Renamed |
| The red button wasn't logged, so unexplained pane switches were hard to read | Logged as `key RED_BUTTON` |

**Not done in M2 (deferred, PLAN rev 9):** the `ProblemProvider` interface (M6), Cloudflare fallback steps 2–3 (M3), WorkManager (M4), images in discussions.

### 2026-11-02: M1 steps 4–11 (built while the owner was away)

The owner confirmed step 3 works on the phone, said the indentation plan (smart Enter/Backspace, `sym+Space`) was fine as planned, and asked me to build as much as possible, make and document decisions, and finish with a test plan. **Test plan: [`docs/m1-test-plan.md`](docs/m1-test-plan.md).**

**Done**
- **Step 4, samples and drafts:**
  - `assets/samples/`: `index.json`, three problems with `statement.html` (written in my own words), `starter.py` and `reference.py`, plus a dark `problem.css`.
  - `SampleLibrary` loads them. `Starter.prepare()` puts the cursor on the first method's indented empty body line, and re-indents it if a snippet lost its trailing spaces. Golden snippets in `src/test/resources/snippets/` include a commented `ListNode` definition and a design class.
  - `DraftStore`: one file per sample, written atomically, plus a `.pos` cursor file and a `.backup.py` for `sym+N`. The debounce is 1 s, and drafts are also saved on pause and on sample switch.
  - Problem pane: a `ProblemView` WebView with no JS, no network and no focus, a 3-button picker, and 1:1 keyboard-surface scrolling (`SurfaceScroller`, with a 24 px threshold).
  - The title line shows the problem and the draft state (`○ starter`, `● editing`, `● saved`).
- **Step 9, Python editing:** `editor/PyEdit.kt` over a `TextBuffer` interface. It covers pairing, type-over, empty-pair Backspace, smart Enter (indent after `:`, copy the indent, dedent after `return`/`break`/`continue`/`pass`/`raise`, open a block between brackets), smart Backspace to the previous indent stop, `sym+Space`, line select/delete/duplicate/comment, line-mode copy/cut/paste, and Find/Go-to. `SoraActions` adapts it, one batch edit per command. Undo merging is set to 1 s (`UndoManager.setMergeTimeLimit`).
- **Step 5, InputRouter:**
  - The routing logic works on a plain `Key` snapshot, so it's JVM-tested (`InputRouterTest`, the KeymapEngine tests of PLAN §10).
  - Alt+Shift+S/D/F/Z → `$ % ^ &`; other Alt+Shift combinations are dropped and logged.
  - All leader commands are wired.
  - App-side key repeat for Backspace, Shift+Backspace and characters: 400 ms, then every 40 ms. `sym` chords, Enter and arrows don't repeat.
  - The Find and Go-to bars take text keys while open.
- **Step 6:** the `fn` palette strip. Typing a key closes it and types the key; taps go through the same insert path (pairing included) and are counted for E01.
- **Step 10:**
  - Find (case-insensitive, wraps, searches as you type) and Go-to bars.
  - `sym+I/O` font size, saved, 8–24 sp.
  - The cheat sheet, generated from `Leader`, the Alt+Shift table and the palette.
  - `sym+N` with a 3 s confirm, a backup file, and one undoable edit.
- **Step 11:** the debug-only acceptance panel (`src/debug/.../Acceptance.kt`; `src/release` has a no-op).
  - It has cards for E01–E16 with Start/End: test buffers save and restore the real draft, and E01/E02 show the reference in the Problem pane.
  - Automatic checks: buffer vs reference with time and palette taps; cursor and buffer views; leader coverage; red-button count; repeat timing; and E14, which records the draft every 0.5 s and checks it after the restart.
  - Pass/Fail/Unclear rewrites the JSON and Markdown report in `Download/titan-code-acceptance/` and `files/reports/`.
- **Step 7 remainder:** a synthetic Cursor Assistant arrow fixture in `InputRouterTest`.
- **Tests:** 50 JVM tests pass. They include typing all three reference solutions through `PyEdit` and getting the exact reference text.
- **Checked on the phone over adb:**
  - starter cursor
  - typing with smart Enter, dedent, smart Backspace and type-over
  - `sym+K/J/H/F/N/U/I/O/T`
  - the Problem pane and picker
  - E14: draft intact after `am force-stop`
  - the acceptance panel's Start, test buffer and reference view

**Problems found and fixed**

| Problem | Cause | Fix |
|---|---|---|
| `sym` on the Problem pane opened Android's "Choose input method" dialog, and the app never saw the key | No view had focus on non-Code panes, so the keyboard app (now "Kika-Keyboard") got `sym` before `MainActivity.dispatchKeyEvent()` | A focused, invisible `KeySinkView` that takes keys at pre-IME on those panes (PLAN §3, rev 8) |
| After switching to Code, typed keys went nowhere | The editor asked for focus while it was still hidden, so the key sink kept focus | The key sink forwards to the editor on the Code pane, and the editor takes focus when it becomes visible |
| App debug logs missing from logcat | The phone's global `log.tag` is now `I` | `require_device()` in `scripts/env.sh` sets `log.tag.TitanCode` / `TitanProbe` to `V` |
| Sora's floating copy/paste bar appeared on keyboard selections | Sora's `EditorTextActionWindow` | Turned off; `sym+C/X/V` are the clipboard |

**Decisions made by me** (all in PLAN Appendix B as *agent decision*, revision 8; easy to change): see the plan change log row below.

**Not done / known gaps**
- Not testable over adb: the red button and `fn`.
- Dedent of a selected block (open question).
- Triple-quoted strings across lines aren't seen by the pairing scanner.
- Indent guides.
- No Notes field on the panel.
- Real Cursor Assistant recording (step 12).

### 2026-11-01: M1 step 3, the shell

**Done**
- `MainActivity` is the real shell:
  - four tabs in the band right of the camera (cutout read at runtime)
  - a title line (placeholder until step 4)
  - Code pane with the editor; placeholder Problem, Results and Comments panes
  - a message strip
  - the bottom status line: `Ln/Col`, `⇧sel`, a `sym` badge while `sym` is held, and **▶ Run** (long-press = remote) and **Submit**, which show "coming in M3/M4"
  - 32 dp clearance from the rounded corners
- The editor is created once and only hidden on other panes, so cursor, selection, scroll and undo survive pane switches.
- **Input routing:**
  - `InputRouter.onEditorKey` (Code, pre-IME) and `onShellKey` (other panes, from `dispatchKeyEvent`).
  - On other panes only `sym+R/E/S/T/H` and Back work; everything else is swallowed.
  - If the editor ever loses focus on the Code pane, the activity routes keys to it.
  - Keyboard-surface strokes move the cursor only on the Code pane and are swallowed elsewhere (Problem-pane scrolling comes in step 4).
- **Leader table** `input/Leader.kt` holds all of PLAN §5.4. Built so far: `sym+T` (Results, returns to the previous pane), `sym+U/Y` (undo/redo), `sym+W`, and `sym+R/E/S` messages. The rest say "not built yet". `LeaderTest` checks that no key has two commands.
- **Back** (PLAN §5.1): ignored in Code; Results/Comments → Code; Problem → `moveTaskToBack`. Checked over adb. A relaunch only brings the app forward (`onNewIntent`).
- **Red button** (keycode 401, on key-up): Problem ↔ Code; Results/Comments → Code. adb can't send keycode 401 (it arrives as `UNKNOWN`), so this needs the owner.
- **Look:**
  - `titan-dark` theme written for the project (AMOLED black)
  - MagicPython grammar (MIT, from VS Code)
  - JetBrains Mono NL 2.304 (OFL), with sources and licences in `app/src/main/assets/LICENSES.md`
  - a small gutter margin

**Problems hit**

| Problem | Cause | Resolution |
|---|---|---|
| Crash on launch: `Column 7 out of bounds` | The Write tool stripped trailing spaces from the sample's indented empty line, so the initial cursor position didn't exist | The sample is built from explicit lines; the initial cursor is clamped to the line length |
| JetBrains Mono not at the raw GitHub path | The repo doesn't keep the TTFs at `fonts/ttf/` on `master` | Took them from the v2.304 release zip |

### 2026-11-01: M1 steps 1–2

**Step 1, scripts.** `install.sh`, `logcat.sh` and `pull-report.sh` take `app` or `probe` (default `app` once `app/` exists). `logcat.sh` also shows `AndroidRuntime:E`. `pull-report.sh app` pulls `Download/titan-code-acceptance/` into `reports/acceptance-<time>/` and links `reports/acceptance-latest`. `doctor.sh` reports both apps.

**Step 2, skeleton and Sora risk check: passed. No CodeMirror fallback needed.**
- `:app` module, version catalog, root build switched to catalog plugin aliases (the probe still builds).
- `TitanEditor` (a Sora `CodeEditor`): `TYPE_NULL` with a dummy `BaseInputConnection`, keys taken in `dispatchKeyEventPreIme()`, Sora's `onKeyDown/Up` closed off (anything that leaks is logged), Sora's auto-indent and pair completion off. All edits go through `EditorActions` (`SoraActions`); any content change from elsewhere is logged as a "foreign edit".
- On the phone (owner + log `build-logs/step2-check.log`): letters, Shift, Alt digits and symbols, Enter, Backspace and Shift+Backspace all worked. Alt+Space inserted nothing. **0 foreign edits, 0 IME commits, no IME bar.** Back stayed in the app.
- The red button now arrives as keycode 401 (`mtk-pmic-keys`) with PTT app = Titan Code. In this build it falls through to the view, which is expected until step 8.

**Problems hit**

| Problem | Cause | Resolution |
|---|---|---|
| Sora 0.24.7 can't be used | Its `language-textmate` needs kotlin-stdlib 2.4.10; Kotlin 2.1.0 can read at most 2.2 metadata | Pinned Sora 0.24.4 (stdlib 2.2.21). See pinned versions. |
| Sourcing `scripts/env.sh` from zsh failed | It's a bash script (globs with no match abort zsh) | Run it through `bash -c` |
| Cursor Assistant slides moved nothing | Under investigation. The log shows no `DPAD` keys and one keycode 404 from `touchPad`; touch events weren't logged in this build. Phase 0 saw the same: with Scroll Assistant also on, slides arrive as touch strokes, which only the GestureEngine (step 7) turns into cursor moves. | Confirmed by logging touches: all slides (double-tap or not) arrive as Scroll Assistant touch strokes, no `DPAD` keys. Consuming them (as the plan requires) also stopped the view scrolling, which had been Sora treating them as screen drags. **GestureEngine (step 7) brought forward.** |

**Step 7 brought forward: GestureEngine.** Plain-Kotlin engine (`input/GestureEngine.kt`) fed from `MainActivity.dispatchTouchEvent()` (all historical samples), with the PLAN §5.6 starting values. Fixtures from the Phase 0 guided report are in `app/src/test/resources/phase0/` (horizontal, vertical, Shift, typing noise, long slide); 7 replay tests pass. The Cursor Assistant arrow fixture is still to come.

**Owner tried scroll-by-default and went back to cursor movement.** At the owner's request a build made plain swipes scroll the view (natural, 1:1) and kept cursor movement for Shift+swipe and for a slide starting just after a tap (to mimic Cursor Assistant). A stroke log of plain swipes, double-tap-then-slides, double-taps and a single tap showed **no taps at all**: only slides send `touchPad` events, so a Cursor Assistant slide can't be told apart from a plain swipe. After trying both, the owner chose: **swipes move the cursor; the touchscreen scrolls.** The scroll mode and tap detection were removed again. The per-stroke log line (`stroke UP CURSOR dur=… dx=… dy=…`) stays for tuning.

**Owner feedback:** horizontal scrolling to see code is unwanted. → Plan revision 7: soft wrap on by default, `sym+W` toggles it.

### 2026-11-01: M1 pre-start review

A second review before writing M1 code found four gaps that needed owner decisions. The owner approved all four recommendations:

| Gap | Decision |
|---|---|
| Auto-pairing inside strings would make the sample solutions untypeable (`'(['` would become `'()[]'`) | No auto-pairing inside strings or comments. Palette closers type over like typed ones. |
| Keys on Problem, Results and Comments never reach the router, because the editor doesn't have focus there | Route them from `MainActivity.dispatchKeyEvent()`. Non-editing leader commands work; text and editing keys are ignored and never reach the WebView. |
| A separate acceptance activity couldn't test the shell (E10, E14, E15) or receive touch interception | A debug-only collapsible panel inside `MainActivity`. The E01/E02 reference is shown in the Problem pane. |
| "Normal Android meaning" for Back is unclear when panes are tabs | Results/Comments → Code; Problem → app to background |

Defaults adopted along with them are listed in PLAN Appendix B ("M1 pre-start: defaults").

Checked:
- Maven Central and Google Maven are reachable (Sora BOM latest is 0.24.7).
- The Phase 0 guided report for the gesture fixtures is on disk.
- The Alt digits S/D/F/Z = 4/5/6/7 match `TitanKey.kcm`.

Stale text fixed: the PROGRESS gesture-tuning step number, and the device report's Cursor Assistant row ("left and right only").

### 2026-11-01: M1 readiness review

A review of the plan before starting M1 raised four questions. The owner decided:

| Question | Decision |
|---|---|
| Back key | Closes any open overlay. Otherwise it's ignored in Code, and normal in the other panes. |
| Forward delete | Shift+Backspace |
| Acceptance tests | A full guided acceptance screen, debug builds only |
| Git | Stay on `master`; commit only when asked |

The reviewer's proposed M1 defaults were adopted into PLAN.md:
- sample picker; WebView Problem pane
- `sym+N` backup file
- typing closes the palette *and types the key* (this changed the old §5.3 rule, which threw the key away)
- red button on Results/Comments → Code
- "Coming in M3/M4" for Run/Submit; `sym+Space` indents only
- Find and Go-to-line bars
- saved font size; fixed indent and wrap until M2
- bundled JetBrains Mono, with licences recorded

Gaps found and handled:
- The Cursor Assistant arrow recording was never saved. It's synthetic until it's re-recorded in M1.
- Dedent / block indent has no binding. Added to the open questions.
- An early Sora + TYPE_NULL check is now step 2.

Stale text fixed:
- PLAN §5.1 and Appendix B, and the device report summary, now say Cursor Assistant sends all four arrows.
- PLAN §5.7 refers to the red button's PTT setting, not launch-app.
- PROGRESS now gives the right last commit.

### 2026-11-01: Phase 0 completed

**Done**
- Built, installed and ran the probe for the first time.
- Guided run: 70 of 85 results.
- adb checks, then several Free log follow-ups with the owner.
- Wrote `docs/device-input-report.md`.
- Revised PLAN.md (revisions 3 and 4).

**Problems hit and how they were solved**

| Problem | Cause | Resolution |
|---|---|---|
| Gradle failed at startup | Android Studio's bundled JDK is 25. Gradle 8.11 needs 17–23. | Installed Temurin 21. `scripts/env.sh` picks a 17–23 JDK automatically. |
| "Android SDK not found" | Android Studio had never been opened, so no SDK was downloaded | The owner ran the setup wizard |
| 3 compile errors in the never-compiled probe | `IntArray` has no `mapNotNull`. `TOOL_TYPE_PALM` and `CLASSIFICATION_MULTI_FINGER_SWIPE` are hidden APIs. | `.toList()`, and the literal values 5 and 4 |
| Text hidden under the status bar and camera | targetSdk 35 forces edge-to-edge | First padded by the insets. Then, at the owner's request, hid the status bar and used the top band beside the camera. The first line is indented past the 123×123 px cutout and made at least as tall as it (PLAN §4.1). |
| Background `adb logcat` recorder died at once | zsh treated `TitanProbe:*` as a filename pattern | Quote filter specs (PLAN §11.1) |
| Gesture tests looked "unclear" to the owner | The probe's auto-checks look for DPAD, scroll or captured events. Scroll Assistant sends **touch** events from device `touchPad`. | Analysed the raw JSON instead. The probe check was left unfixed (throwaway). |
| "Mouse mode" tests impossible | The catalogue assumed a mode this device doesn't have | Marked not applicable |
| Cursor Assistant appeared to send nothing | It needs a **double-tap, then slide**. Later, arrows seemed missing in TYPE_NULL because the probe field wasn't focused (the Release button was pressed by mistake). | Re-tested. It's plain arrow keys in all four directions, also in TYPE_NULL. Up/down need more than one line of text. |
| K16 marked `*` and `@` missing | The owner overlooked them on the keycaps | Confirmed in Free log: Alt+A and Alt+P |
| Backlight couldn't be forced on | No API, setting or sysfs path is available to apps | **Requirement dropped** by the owner |
| The red button never reached the app | The system intercepts `FUNC1`. "Launch app" only reopens the home screen. | Unihertz **PTT app** setting → keycode 401 delivered to the chosen app |
| 15 Python symbols missing; no IME menu or key combo | The hardware key map has no keys for them | Alt+Shift+S/D/F/Z → `$ % ^ &`, plus the `fn` touchscreen palette for the rest. `` ` `` and `\` aren't needed. |

**Probe changes made** (no test IDs or report format changed):
- the compile fixes above
- the inset and top-band layout
- the Free log backlight button showing its state

**Known probe bugs left unfixed** (it's throwaway):
- Gesture auto-checks ignore `touchPad` touch events.
- Gesture tests run only in TYPE_NULL.
- B02 writes a setting with an empty name when its diff is empty, and diffs only the system table.

**Evidence on disk** (gitignored, on the owner's Mac only):
- `reports/probe-20261101-200140/` (guided run JSON and Markdown)
- `reports/adb-20261101-201016/` (adb checks, key maps, settings diffs)
- The follow-up Free log logcat captures were analysed in-session and not saved to the repo. Their results are in the device report.

---

## Plan change log

| Date | Revision | What changed | Why |
|---|---|---|---|
| 2026-11-03 | **28** | §4.2 reading size scales whole panes (controls with the text); chip rows wrap | Owner request |
| 2026-11-03 | **27** | §14.2 review queue removed; §4.2 reading text size for the Problem and Comments panes (`sym+I/O` there, Settings) | Owner request |
| 2026-11-03 | **26** | Guided acceptance panel removed (§3, §10 kept as history; §14 drops the E01–E15 run) | Owner request |
| 2026-11-03 | **25** | §8.4 Everything on a site: per-site packs of every free problem, two passes, skip-and-continue, rate-limit waits, foreground downloads with a notification; WorkManager 2.10 | Owner request |
| 2026-11-03 | **24** | §7.1 limits, §7.2 checkers, §7.3 stress tests; §14.2 stage 4 built (test-case editor in the Code pane, practice timer, Review chip); DB v5 | Owner request |
| 2026-11-03 | **23** | §7 special-type drivers by LeetCode number; design arguments by type; unchecked random answers; NeetCode extra inputs and re-download of stale copies | Owner request |
| 2026-11-03 | **22** | §4.3 paid problems hidden on all sites; 🎲 random problem | Owner request |
| 2026-11-03 | **21** | §5.6 Strong: slow 40 ms, edge 50 ms | Owner: still too weak |
| 2026-11-03 | **20** | §5.6 adaptive haptics (Off/Normal/Strong, 6 ms floor) | Owner: fine moves need more distinct pulses |
| 2026-11-03 | **19** | Name "Coding" and icon; haptics as short pulses with Off/Faint/Light (§5.6) | Owner request |
| 2026-11-03 | **18** | §4.2 indent guides and bracket pairs built; §5.6 swipe haptics; §14.2 marks them done | Owner request |
| 2026-11-03 | **17** | §14 rewritten as a roadmap (leftovers plus proposed M7 in four stages); §12 adds M7 | Owner asked what's left and what would improve the experience |
| 2026-11-03 | **16** | §5.6: smoothed swipe speed, precision zone, acceleration setting | Owner: slow moves jumped over words |
| 2026-11-03 | **15** | §4.2: cursor trail | Owner request |
| 2026-11-02 | **14** | §5.6: the gesture engine watches keys, so typing never moves the cursor | Owner: the cursor jumped while typing |
| 2026-11-02 | **13** | Owner: snippets removed (§5.4); Back never leaves the app (§4.3, §5.1); NeetCode and Codeforces as full platforms (§6.1 built note, §6.3 rewritten, §7 stdin built, §12 M6).<br>*Agent decisions* (Appendix B): prefixed keys instead of the full provider interface, NeetCode catalog snapshot and token handling, Codeforces hidden WebView + API, PyPy default, rating buckets, normal browser identity | Owner feedback on the test queue; M6 |
| 2026-11-02 | **11** | M4: Chaquopy/Python 3.14 runner design details, modes built vs not (§7); packs and WorkManager sync (§8.4); `sym+R` local again (Appendix B) | M4 build |
| 2026-11-02 | **10** | Owner: Comments Solutions-first with posts and folding code (§4.1); Back on statement → list, back swipe captured (§5.1).<br>M3 built: WebView-first transport for Run/Submit (§6.2), clipboard custom tests, submission history, `sym+R`/▶ Run → LeetCode until M4 (Appendix B) | Owner feedback on M2; M3 build |
| 2026-11-02 | **9** | Owner: Find/Go-to removed (§5.4); silent saving (§4.1, §12).<br>*Agent decisions* building M2 (Appendix B): problem list in the Problem pane with `sym+P` (§4.3, §5.4); Keystore cookie store; Cloudflare step 1 only until M3 (§6.2); offline data limits and no WorkManager until M4 (§8); quiet offline badge (§4.1); provider interface deferred to M6 (§12) | Owner feedback on M1; building M2 unattended |
| 2026-11-02 | **8** | *Agent decisions* while the owner was away (Appendix B):<br>• `KeySinkView` on non-Code panes (§3)<br>• Sora's copy/paste bar off; `PyEdit` over `TextBuffer` (§3)<br>• one-line string/comment scanner instead of TextMate tokens; pairing only before whitespace/closers; quote rules; Enter details (§5.5)<br>• `sym+Space` indents a selected block (§5.4)<br>• acceptance panel without Notes (§10)<br>• small defaults (Appendix B) | Building M1 steps 4–11 unattended; the input-method picker bug on non-Code panes |
| 2026-11-01 | 7.1 | The theme is written for the project instead of adapting Darcula/One Dark (§3) | Avoids a third-party theme licence; the plan already asked for an AMOLED-black adjustment |
| 2026-11-01 | **7** | Soft wrap on by default; `sym+W` toggles it and the choice is saved; up/down by screen row when wrapping; continuation rows not indented (Sora limit) (§4.2, §5.4, §5.6, E07, Appendix B).<br>Long-slide filter: >75% width **and** >2000 px/s, with the cursor restored; stroke travel counted from touch-down (§5.6).<br>Keyboard surface moves the cursor only; dragging on the touchscreen scrolls; Cursor Assistant's double-tap is invisible to the app (§4.1, §5.6, Appendix B) | Owner: no horizontal scrolling to read code. The old >85%-width rule would have dropped the Phase 0 Shift-select swipe and missed one backlight slide. |
| 2026-11-01 | 6 | M1 pre-start review:<br>• no auto-pairing inside strings and comments; palette closers type over (§5.3, §5.5)<br>• key routing on non-Code panes (§3, §5.1)<br>• acceptance panel inside `MainActivity`; E01/E02 reference in the Problem pane; E14 waits 2 s; E16 not applicable (§3, §10)<br>• Back on Results/Comments → Code, on Problem → background (§5.1)<br>• Sora's own auto-indent and pairing off; MagicPython grammar (§3)<br>• repeat scope, Alt+Shift fallback, selection replace, smart Enter for all bracket pairs, smart Backspace as dedent, undo grouping (§5.1, §5.5)<br>• line paste, Find and Go-to details (§5.4)<br>• font range, M1 status line, Problem pane scroll (§4, §5.6)<br>• sample content written by hand (§12)<br>• dedent question narrowed to block indent (§13) | Review before writing M1 code |
| 2026-11-01 | 5 | M1 review fixes:<br>• Back key rule (§5.1)<br>• Shift+Backspace forward delete (§5.1)<br>• guided acceptance screen (§3, §10, §12)<br>• Find and Go-to-line bars, `sym+N` backup, `sym+Space` indent-only, "Coming in M3/M4" (§5.4)<br>• palette: typing types the key (§5.3)<br>• red button on other panes → Code (§4.1)<br>• Problem pane WebView and sample picker (§4.1, §12)<br>• saved font size; fixed indent and wrap (§4.2)<br>• bundled-asset licences (§3)<br>• fixture gap (§10)<br>• dedent question (§13)<br>• stale Cursor Assistant and red-button text | Review before starting M1 |
| 2026-11-01 | 4 | Resolved inconsistencies and added M1 detail:<br>• Goal and E01/E02 allow `fn`-palette taps<br>• Cursor Assistant described as four directions<br>• E12 refers to the status line<br>• new "App structure" (§3): single activity, interception points, library list and version rule<br>• gesture starting values (§5.6)<br>• cheat sheet defined<br>• M1 ships bundled samples and minimal draft autosave; M2 migrates drafts to Room<br>• script extension for `app`<br>• §11.1 phone-testing workflow; git branch and commit rule<br>• open questions moved here<br>• status notes moved out of PLAN.md into this file<br>• `HANDOFF.md` retired by the owner | A review found a fresh agent couldn't start M1 from the plan without guessing |
| 2026-11-01 | 3.x | Editor input type briefly changed to a readable text field, then **reverted to TYPE_NULL** | A first test suggested Cursor Assistant's up/down needed a text field. A re-test with the field focused showed TYPE_NULL gets all four arrows. |
| 2026-11-01 | 3.x | Red button: **PTT app = Titan Code → keycode 401** toggles panes; `sym+P` removed from the leader table | Follow-up tests of the Unihertz Shortcut keys settings |
| 2026-11-01 | 3.x | Cursor Assistant accepted as arrow keys; selection only via Shift + Scroll Assistant swipe | Cursor mode ends on any key press, so it can't select |
| 2026-11-01 | 3 | Phase 0 results folded in:<br>• §5.1–§5.3 and §5.6 finalised<br>• symbol fix (Alt+Shift + `fn` palette)<br>• backlight requirement dropped<br>• top band beside the camera<br>• app-side key repeat<br>• Alt/`sym`+swipe gestures removed<br>• Appendix B added | Probe results and owner decisions |
| 2026-11-01 | 2 | One way per action, `sym` hold-to-use leader, CODE layer deferred, red button switches panes, native login only, offline Submit blocked, drafts on-device only (Appendix A) | Owner feedback in `PLAN-FEEDBACK.md` |
