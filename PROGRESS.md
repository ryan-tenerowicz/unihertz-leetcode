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
| **M1**: editor and input core | 🔨 **In progress** (steps 1–2 done) | Scope and exit criteria: PLAN §12. Structure: PLAN §3 "App structure". |
| M2: read LeetCode, offline-first | Not started | |
| M3: run and submit on LeetCode | Not started | |
| M4: local runner and offline packs | Not started | |
| M5: polish | Not started | |
| M6: more platforms | Not started | |

**Last commit:** `be269b4` "cleanup md files" on `master` (plan revisions 5–6). M1 work since then is uncommitted. **Work on `master`; commit only when the owner asks.**

---

## Next up (M1, in order)

1. ✅ **Scripts:** add the `app` target to `install.sh`, `doctor.sh`, `logcat.sh` and `pull-report.sh` (PLAN §11).
2. ✅ **Skeleton, and an early risk check** (passed 2026-11-01, see the work log):
   - Create the `:app` module (`dev.titan.code`) and the Gradle version catalog.
   - Add Compose with the compiler plugin, and Sora Editor with `language-textmate`.
   - Pin versions that build with AGP 8.7.3 / Kotlin 2.1.0 / JDK 21, and record them below.
   - **Before building anything else:** put a `CodeEditor` subclass on screen with a TYPE_NULL `InputConnection` and pre-IME key handling. Check that typed characters, Backspace and arrows reach it, and that Sora's own input handling doesn't fight it. If it does, record the problem here and switch to the CodeMirror 6 WebView fallback (PLAN §3).
3. **Shell:**
   - single activity; status bar hidden; top band beside the camera (reuse the inset logic in `probe/.../ProbeActivity.kt`)
   - four tabs
   - bottom status line
   - Back handling (PLAN §5.1)
   - key routing on non-Code panes through `MainActivity.dispatchKeyEvent()` (PLAN §5.1)
4. **Bundled samples:** Two Sum, Group Anagrams and Valid Parentheses in `app/src/main/assets/samples/`; the Problem-pane WebView with a sample picker; minimal draft autosave plus one backup file per sample.
5. **InputRouter:**
   - TYPE_NULL, characters through `getUnicodeChar`
   - Alt layer; Alt+Shift+S/D/F/Z → `$ % ^ &`
   - Shift+Backspace forward delete
   - `sym` leader table, with "Coming in M3/M4" for R/E/S
   - arrow keys; app-side key repeat
   - keycode 403 (`fn`) and 404 handling
   - JVM unit tests for `KeymapEngine`
6. **`fn` symbol palette:** typing a key closes it and types the key.
7. 🔨 **GestureEngine** (engine, interception and Phase 0 fixtures done; on-device check pending):
   - `touchPad` interception in `MainActivity.dispatchTouchEvent()`, with the starting values from PLAN §5.6
   - fixtures in `app/src/test/resources/phase0/`, trimmed from the guided report
   - the Cursor Assistant arrow fixture: synthetic for now; record the real one with the owner at step 12
8. **Red button:** keycode 401 toggles Problem/Code; on Results or Comments it goes to Code.
   - ✅ PTT app switched to Titan Code; keycode 401 reaches the app (2026-11-01).
   - ✅ The red button's "programmable key" is None (`func1_programmable_key_function=0`).
9. **Python editing** (PLAN §5.5):
   - auto-pairing, with no pairing inside strings or comments (from the TextMate tokens)
   - smart Enter, smart Backspace
   - undo grouping for typing
   - Sora's own auto-indent and pair-closing switched off
10. **Find and Go-to-line bars, font size** (`sym+I/O`, saved), **soft-wrap toggle** (`sym+W`, saved; on by default), and the **cheat sheet** (`sym+H`, generated from the keymap).
11. **Guided acceptance panel** (debug builds only, PLAN §10): a collapsible panel inside `MainActivity` over the real shell, with its JSON/Markdown report and `pull-report.sh app`.
12. **Acceptance:**
    - Run E01–E15 with the owner on the acceptance panel.
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
| Should indenting or dedenting a selected block get a leader letter? There's no Tab key; `sym+Space` only indents at the cursor. (Dedent at the start of a line is covered by smart Backspace.) | 2026-11-01 (M1 review) | Nothing |

---

## Work log

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
| 2026-11-01 | **7** | Soft wrap on by default; `sym+W` toggles it and the choice is saved; up/down by screen row when wrapping; continuation rows not indented (Sora limit) (§4.2, §5.4, §5.6, E07, Appendix B).<br>Long-slide filter: >75% width **and** >2000 px/s, with the cursor restored; stroke travel counted from touch-down (§5.6).<br>Keyboard surface moves the cursor only; dragging on the touchscreen scrolls; Cursor Assistant's double-tap is invisible to the app (§4.1, §5.6, Appendix B) | Owner: no horizontal scrolling to read code. The old >85%-width rule would have dropped the Phase 0 Shift-select swipe and missed one backlight slide. |
| 2026-11-01 | 6 | M1 pre-start review:<br>• no auto-pairing inside strings and comments; palette closers type over (§5.3, §5.5)<br>• key routing on non-Code panes (§3, §5.1)<br>• acceptance panel inside `MainActivity`; E01/E02 reference in the Problem pane; E14 waits 2 s; E16 not applicable (§3, §10)<br>• Back on Results/Comments → Code, on Problem → background (§5.1)<br>• Sora's own auto-indent and pairing off; MagicPython grammar (§3)<br>• repeat scope, Alt+Shift fallback, selection replace, smart Enter for all bracket pairs, smart Backspace as dedent, undo grouping (§5.1, §5.5)<br>• line paste, Find and Go-to details (§5.4)<br>• font range, M1 status line, Problem pane scroll (§4, §5.6)<br>• sample content written by hand (§12)<br>• dedent question narrowed to block indent (§13) | Review before writing M1 code |
| 2026-11-01 | 5 | M1 review fixes:<br>• Back key rule (§5.1)<br>• Shift+Backspace forward delete (§5.1)<br>• guided acceptance screen (§3, §10, §12)<br>• Find and Go-to-line bars, `sym+N` backup, `sym+Space` indent-only, "Coming in M3/M4" (§5.4)<br>• palette: typing types the key (§5.3)<br>• red button on other panes → Code (§4.1)<br>• Problem pane WebView and sample picker (§4.1, §12)<br>• saved font size; fixed indent and wrap (§4.2)<br>• bundled-asset licences (§3)<br>• fixture gap (§10)<br>• dedent question (§13)<br>• stale Cursor Assistant and red-button text | Review before starting M1 |
| 2026-11-01 | 4 | Resolved inconsistencies and added M1 detail:<br>• Goal and E01/E02 allow `fn`-palette taps<br>• Cursor Assistant described as four directions<br>• E12 refers to the status line<br>• new "App structure" (§3): single activity, interception points, library list and version rule<br>• gesture starting values (§5.6)<br>• cheat sheet defined<br>• M1 ships bundled samples and minimal draft autosave; M2 migrates drafts to Room<br>• script extension for `app`<br>• §11.1 phone-testing workflow; git branch and commit rule<br>• open questions moved here<br>• status notes moved out of PLAN.md into this file<br>• `HANDOFF.md` retired by the owner | A review found a fresh agent couldn't start M1 from the plan without guessing |
| 2026-11-01 | 3.x | Editor input type briefly changed to a readable text field, then **reverted to TYPE_NULL** | A first test suggested Cursor Assistant's up/down needed a text field. A re-test with the field focused showed TYPE_NULL gets all four arrows. |
| 2026-11-01 | 3.x | Red button: **PTT app = Titan Code → keycode 401** toggles panes; `sym+P` removed from the leader table | Follow-up tests of the Unihertz Shortcut keys settings |
| 2026-11-01 | 3.x | Cursor Assistant accepted as arrow keys; selection only via Shift + Scroll Assistant swipe | Cursor mode ends on any key press, so it can't select |
| 2026-11-01 | 3 | Phase 0 results folded in:<br>• §5.1–§5.3 and §5.6 finalised<br>• symbol fix (Alt+Shift + `fn` palette)<br>• backlight requirement dropped<br>• top band beside the camera<br>• app-side key repeat<br>• Alt/`sym`+swipe gestures removed<br>• Appendix B added | Probe results and owner decisions |
| 2026-11-01 | 2 | One way per action, `sym` hold-to-use leader, CODE layer deferred, red button switches panes, native login only, offline Submit blocked, drafts on-device only (Appendix A) | Owner feedback in `PLAN-FEEDBACK.md` |
