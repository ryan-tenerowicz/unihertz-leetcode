# Titan Code: coding practice on the Unihertz Titan 2 Elite

A plan for an Android app that makes it comfortable to browse coding problems, write Python, run tests and submit, using the Titan 2 Elite's physical keyboard and its touch-sensitive keyboard surface. **LeetCode (leetcode.com) is the first platform.** NeetCode and Codeforces can be added later without reworking the editor or UI.

> **This file is the design (the source of truth for *what* we build).** Progress, problems we hit, and the history of changes to this plan are in **[`PROGRESS.md`](PROGRESS.md)**. Device evidence is in [`docs/device-input-report.md`](docs/device-input-report.md). Owner decisions are recorded in Appendix A (plan feedback) and Appendix B (Phase 0 results).
>
> **Revision 7 (2026-11-01).** Soft wrap on by default, toggled with `sym+W` (owner feedback after the M1 step 2 check). Long-slide filter based on speed as well as width (§5.6). Keyboard-surface swipes move the cursor only; the touchscreen scrolls (§4.1, §5.6). History: PROGRESS.md → Plan change log.

---

## 1. Goal

**A full practice session (pick a problem, read it, write a solution, run the examples, submit) where text editing happens on the keyboard.** The touchscreen handles navigation and the big actions: tapping the pane tabs, the **Run** and **Submit** buttons, scrolling, and tapping links or list rows. Typing, moving the cursor, selecting, deleting and clipboard work use the keyboard.

**The one exception (owner decision, Appendix B):** the keyboard has no keys at all for `= [ ] { } < > | ; ~`. Those are inserted from a small **touchscreen symbol palette opened with `fn`** (§5.3). Every other character and every editing action stays on the keyboard.

Design principles:

1. **Every Python symbol is reachable, and most are fast.** The printed symbols come from the keyboard's native Alt layer (2 keystrokes each). Phase 0 found 15 symbols missing (K16). The owner's fix (§5.3):
   - `%` `^` `&` (and `$`) come from **Alt+Shift** on the keys whose printed digit carries them on a standard keyboard (3 keystrokes).
   - The rest come from a **touchscreen symbol palette opened with `fn`**.
   - `` ` `` and `\` aren't needed.
2. **The keyboard surface is a trackpad for the code.** Swiping moves the cursor (horizontally by character, vertically by line), and holding Shift selects text.
3. **No IME interference.** The editor never autocorrects, auto-capitalises, turns a double space into a period or shows a candidate bar.
4. **Few keyboard shortcuts to remember.** Every action has **at most one keyboard binding**: there are no alternate shortcuts, no sticky modifiers and no tap-versus-hold variants. Touch controls (pane tabs, Run and Submit buttons) sit alongside the keyboard and don't count against this rule. The goal is a small set of shortcuts that's easy to remember, not removing touch.
5. **One focused pane at a time.** The 1080×1200 screen is nearly square, so each pane (Problem, Code, Results, Comments) takes the full screen. The **red side button switches between Problem and Code**. Switching panes never moves the editor's cursor or changes its selection.
6. **Keep everything you type, on the device.** Drafts autosave per problem and a run never loses code. Drafts stay on the phone: there is no export and no sync.
7. **Offline-first.** Problems, test cases and comments are saved locally, so reading, writing and running code locally all work without a connection. Submitting and running on the server need the network (§8).
8. **Platform-agnostic core.** The editor, input system, panes and runner know nothing about LeetCode. Each platform is a plug-in **problem provider** (§6).

---

## 2. What we know about the device

| Item | Value | Implication |
|---|---|---|
| Display | 4.03" AMOLED, 1080×1200 physical (**1076×1200** usable), 300 dpi logical, 120 Hz | Roughly 55–60 monospace columns at 12 sp. Method bodies inside `class Solution` start at column 8, so there's a 2-space indent option. |
| Camera cutout | **123×123 px square in the top-left corner** (about 66 dp). The status bar is the same 123 px tall. All four screen corners are rounded with a **100 px radius**. | The app hides the status bar and uses the top band to the right of the camera (§4.1). Nothing interactive goes in a rounded corner. |
| OS | Android 16 (SDK 36), build `Titan 2 Elite_V02.00.04` | We target a modern SDK (minSdk 34, targetSdk 35). targetSdk 35 forces edge-to-edge drawing, so we handle insets ourselves. |
| SoC / RAM | Dimensity 7400, 12 GB | Plenty for a separate Python runner process and a WebView. |
| Keyboard | Physical QWERTY (`TitanKey`), 3 rows of letters plus a bottom row of Shift · Home · Back · Space · Recents · `fn` · Shift. Alt and `sym` sit in the third row. **No number row and no dedicated `,` `.` keys.** | Digits and printed symbols come from the native Alt layer (§5.3). `sym` is our leader key (§5.4). **Keys never auto-repeat** (§5.1). |
| Touch keyboard | **Scroll Assistant** (touch events with coordinates), **Cursor Assistant** (double-tap the keys, then slide → arrow keys in all four directions), flick-to-pick candidate. **No Mouse mode.** All are off by default. | Scroll Assistant drives cursor movement and selection. Arrow keys from Cursor Assistant are also accepted (§5.6). |
| Programmable keys | Per-letter short and long-press shortcuts, "Universal shortcuts", **red side button** (`FUNC1`, scancode 249). In *Settings → Shortcut keys* it can launch an app, act as a key (None / Ctrl / Sym / Magic / Media) or be a **PTT key for a chosen app**. | Out of the box the system takes it. **With "PTT app = Titan Code" it reaches the app as keycode 401** on every input stage, so it switches panes (§4.1). |

**Phase 0 resolved the unknowns.** The details are in `docs/device-input-report.md`:

- Alt characters come from the KeyCharacterMap.
- `sym` reaches the app as a modifier.
- The touch keyboard behaves as described above.
- Swipes register with Shift held, but not with Alt or `sym` held.
- The keyboard backlight can't be controlled by apps; the owner dropped that requirement.

---

## 3. Architecture

```
┌──────────────────────────── Android app (Kotlin) ─────────────────────────────┐
│                                                                               │
│  UI (Jetpack Compose)                                                         │
│   ├─ OnboardingScreen       required Unihertz settings, login, first download │
│   ├─ ProblemListScreen      type-to-filter, difficulty/status/tag filters     │
│   ├─ ProblemScreen          WebView, bundled CSS/KaTeX, local images          │
│   ├─ EditorScreen           Sora Editor (CodeEditor) + status line            │
│   ├─ ResultsScreen          run/submit output, test case list                 │
│   ├─ CommentsScreen         saved discussion + solutions (spoiler-gated)      │
│   └─ SettingsScreen         indent, wrap, swipe speeds, storage, account      │
│                                                                               │
│  InputRouter  ◄── editor's dispatchKeyEventPreIme / onKeyPreIme + MotionEvents│
│   ├─ KeymapEngine          base / native Alt / Alt+Shift / leader (sym held)  │
│   ├─ GestureEngine         keyboard-surface swipes → cursor/selection         │
│   ├─ SymbolPalette         touchscreen symbol picker, opened with fn (§5.3)   │
│   └─ EditorActions         insert, pair, indent, move, select, undo, …       │
│                                                                               │
│  Data (platform-neutral)                                                      │
│   ├─ ProviderRegistry      ProblemProvider plug-ins (§6.1)                    │
│   │   ├─ LeetCodeProvider  leetcode.com GraphQL + REST          ← v1          │
│   │   ├─ NeetCodeProvider  (later)                                            │
│   │   └─ CodeforcesProvider (later)                                           │
│   ├─ AuthManager           native-login WebView, encrypted cookies            │
│   ├─ HttpTransport         OkHttp w/ WebView UA + cookies → WebView fetch     │
│   │                        fallback for Cloudflare (§6.2)                     │
│   ├─ Room DB + files       offline-first store (§8), keyed by                 │
│   │                        (providerId, problemId)                            │
│   ├─ SyncWorker            WorkManager: offline packs, scheduled refresh      │
│   └─ RunnerClient ──IPC──► :runner process (Chaquopy CPython), killable (§7) │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Key technology choices

- **Kotlin + Jetpack Compose** for the app shell.
- **Editor: [Sora Editor](https://github.com/Rosemoe/sora-editor).** It's a native code editor with Python highlighting, undo/redo and line numbers. We subclass `CodeEditor` and route all key handling through the `InputRouter` (§5.1).
  - *Fallback:* CodeMirror 6 in a WebView, if Sora causes problems with hardware keys. The `InputRouter` and `EditorActions` interfaces don't depend on the editor, so a swap stays contained.
- **Problem rendering: WebView** with our own dark, compact CSS. Everything it needs is bundled, so it works offline.
- **Local execution: [Chaquopy](https://chaquo.com/chaquopy/)** runs in a **separate `:runner` process** (§7).

### App structure (decided for M1)

- **Module `:app`**, package and application ID **`dev.titan.code`**, app name "Titan Code".
  - compileSdk 35, targetSdk 35, minSdk 34, JVM target 17: the same as the probe.
  - logcat tag **`TitanCode`**.
- **One activity** (`MainActivity`, `launchMode="singleTask"`) hosts the Compose UI. Its manifest `configChanges` list is the same as the probe's (keyboard, keyboardHidden, navigation, orientation, screenSize, screenLayout, smallestScreenSize, uiMode, density, fontScale, touchscreen), so a hardware-keyboard or touch-mode change never recreates it mid-edit.
- **The editor** is a `CodeEditor` subclass (`TitanEditor`) embedded with Compose `AndroidView`. It overrides `dispatchKeyEventPreIme()` and `onCreateInputConnection()` (§5.1).
- **Where each input is intercepted:**

  | Input | Where | Why |
  |---|---|---|
  | Physical keys, including `sym` chords, Alt+Shift symbols, `fn` and arrow keys | `TitanEditor.dispatchKeyEventPreIme()` → `InputRouter` | Sees keys before the IME (K15) |
  | Physical keys while the Problem, Results or Comments pane is showing | `MainActivity.dispatchKeyEvent()` → `InputRouter` (non-Code mode, §5.1) | The editor doesn't have focus there, so its pre-IME hook never runs. Keys must not reach the WebView. |
  | Red button, keycode 401 | `MainActivity.dispatchKeyEvent()` | Must work on every pane, not only while the editor has focus |
  | Keyboard-surface touches (device `touchPad`) | `MainActivity.dispatchTouchEvent()` → `GestureEngine` | They're screen-coordinate touches and must never reach a view (§5.6) |
  | Keycode 404 (`touchPad` swipe marker) | Both places above swallow it | It has no meaning for us |

- **Libraries.** Exact versions are pinned at the start of M1 in a Gradle version catalog (`gradle/libs.versions.toml`) and recorded in PROGRESS.md. They must build with the toolchain in §11: AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, JDK 21. Upgrading those is a separate, deliberate step. What's needed:
  - **Compose:** the Compose BOM, `activity-compose`, Material 3, and the Kotlin **Compose compiler Gradle plugin** (`org.jetbrains.kotlin.plugin.compose`, the same version as Kotlin). Kotlin 2.x requires that plugin.
  - **Sora Editor** (`io.github.rosemoe:editor` via its BOM) plus **`language-textmate`**. Sora has no built-in Python highlighting, so a **TextMate Python grammar** and a dark theme JSON are bundled in `app/src/main/assets/textmate/`, with their licences noted.
    - **Sora's own editing behaviour is switched off:** its auto-indent, symbol-pair auto-close and key-driven text insertion. The `InputRouter` and `EditorActions` are the only things that insert text (§5.1). Sora still provides highlighting, rendering, the text model, touch handling and undo.
  - **Bundled assets, licences recorded in `app/src/main/assets/LICENSES.md`:**
    - JetBrains Mono (OFL), in `app/src/main/res/font/`
    - the **MagicPython** TextMate grammar (MIT)
    - a dark TextMate theme (Darcula or One Dark, whichever has a clear licence), adjusted to an AMOLED-black background
  - **Unit tests:** JUnit 4 (`./gradlew :app:testDebugUnitTest`).
- **Debug-only acceptance panel** (§10) lives in the `src/debug/` source set, so release builds never contain it. It is a collapsible panel **inside `MainActivity`**, over the real shell, not a separate activity: the shell's touch interception, red button and panes are what it tests. The main source set exposes a no-op hook that the debug source set fills in.
  - **Later milestones only:** Room, WorkManager, OkHttp, security-crypto (M2); Chaquopy (M4).

---

## 4. UI design

### 4.1 Layout: four full-screen panes

```
┌──────────────────────────────────────────┐
│ ◉ │ Problem │[Code]│ Results │ Comments  │  ← top band beside the camera (◉): tab bar
│ 49. Group Anagrams · Med  ● saved  ⦿ off │  ← status line: problem, draft state, online/offline
├──────────────────────────────────────────┤
│  1 class Solution:                       │
│  2   def groupAnagrams(self, strs):      │
│  3     groups = defaultdict(list)        │
│  4     for s in strs:                    │
│  5       groups[''.join(sorted(s))]▌     │
│  …                                       │
├──────────────────────────────────────────┤
│ Ln 5 Col 31 · ⇧sel · [sym]  [▶ Run][Submit]│  ← position, selection, leader indicator; tappable buttons
└──────────────────────────────────────────┘
```

- **Using the top of the screen (Phase 0).** The app hides the system status bar (`WindowInsetsController.hide(statusBars())`; a swipe down shows it briefly) and draws into the cutout area (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`).
  - The **top band** is the height of the camera cutout, 123 px (about 66 dp). It holds the tab bar, starting just right of the camera (x ≥ 123 px), so that whole line is used except the camera square.
  - Everything below the band uses the full width. The cutout rectangle is read from `WindowInsets.displayCutout` at runtime, not hard-coded.
  - Interactive controls stay at least 30 px clear of the rounded corners (100 px radius). In particular, Run and Submit in the bottom-right corner are inset.
  - The navigation bar and IME insets still pad the bottom.
  - The probe already uses this layout. It works.
- **Touch buttons:**
  - **▶ Run** runs locally, the same as `sym+R`. Long-pressing Run runs on LeetCode, the same as `sym+E`.
  - **Submit** asks for confirmation: the first tap turns it into **"Confirm?"** for 3 seconds, and a second tap submits. This matches `sym+S` pressed twice.
  - The last result (e.g. ✓ 3/3) shows in the Results tab's label.
  - While offline, the Submit button is greyed out and shows the offline message (§8.1).
- **Status line in M1:** problem title, draft state, position, selection and `[sym]`. The online/offline badge arrives with the network in M2.

- **Problem**: the rendered statement, examples and constraints, in a WebView with bundled CSS. The keyboard surface scrolls it (Scroll Assistant, §5.6). In M1, a 3-item sample picker sits at the top of this pane (§12).
- **Code**: the editor.
- **Results**: output of Run or Submit (verdict, runtime, failing case, stdout) and the problem's saved test cases. **It opens automatically after Run or Submit.** `sym+T` opens and closes it.
- **Comments**: saved discussion threads and community solutions. Solutions stay hidden until you tap "show solutions". This pane is reached by tapping its tab.
- **Switching panes:**
  - The **red side button** toggles between Problem and Code. Each pane keeps its scroll position, and the editor keeps its cursor.
  - **How the button reaches the app (Phase 0, K12 + follow-up).**
    - Out of the box the system takes it, and "launch app" just reopens the app at its home screen.
    - With the Unihertz setting **Shortcut keys → PTT key → PTT app = Titan Code**, every press reaches the app as **keycode 401** (`FUNC1`, scancode 249, device `mtk-pmic-keys`): a DOWN/UP pair at pre-IME, the activity and the view, for both short presses and holds.
    - The router toggles Problem/Code on key-up of keycode 401. Holds and double presses do nothing extra (one action, one binding). **On Results or Comments, the red button goes to Code.**
    - Unihertz notes that enabling PTT turns off the button's other "smart key" shortcuts, which is fine.
    - Onboarding walks the owner through this setting (§5.7).
    - **Not yet tested:** what PTT does while Titan Code is in the background (it may launch the app). The app handles both: a keycode 401 in `dispatchKeyEvent()`, and a relaunch intent through `onNewIntent()` (`singleTask`), which just brings it forward without toggling.
  - The other remap route, "programmable key = Sym key", also reaches the app (as `KEYCODE_SYM` with scancode 249). It isn't used, because it would look like the leader key.
- **The tab bar** is the touch method for reaching any pane, including Problem and Code, alongside the red button.
- **Touch in the Code pane:** standard touch behaviour keeps working: tapping places the cursor, and **dragging on the screen scrolls the code, vertically and (with wrap off) horizontally**. This is the only way to scroll without moving the cursor (owner decision, revision 7: the keyboard surface is for the cursor only, §5.6). We don't design any extra touch features.

### 4.2 Editor presentation

- **Theme and font:**
  - Dark theme with an AMOLED-black background and high-contrast syntax colours.
  - Monospace font (JetBrains Mono, bundled, ligatures off), default 12 sp. Font size changes only with `sym+I` / `sym+O`, in 1 sp steps from 8 to 24 sp, and **is saved between launches** (SharedPreferences).
- **Soft wrap (owner decision, revision 7): on by default**, so the code never has to be scrolled sideways to be read.
  - **`sym+W` toggles it** (§5.4), and the choice **is saved between launches**. With wrap off, long lines run past the screen edge and the view scrolls horizontally to follow the cursor.
  - Words aren't split across rows (Sora's anti-word-breaking).
  - **Continuation rows start at the left edge**: Sora 0.24 can't indent them to match the line. Line numbers appear only on a line's first row, which marks where each line starts.
  - With wrap on, up/down movement (vertical swipe or arrow keys) goes **by screen row**, keeping the horizontal position (§5.6).
- **Indent width (Settings screen, M2):** 4 (the default) or 2. Until the Settings screen exists it's fixed at 4.
- **Swipe speeds (Settings screen, M2):** horizontal and vertical cursor speed for keyboard-surface swipes (§5.6).
- **Visual aids:** indent guides, current-line highlight and matching-bracket highlight.

### 4.3 Problem list

- **Searching:** start typing to filter by title or number.
- **Filters:** difficulty, status (solved, attempted, todo), tag, LeetCode study plans and favourites, and Daily. NeetCode lists arrive in M6.
- **Hidden problems:** problems with no `python3` snippet (SQL, shell, concurrency) don't appear.
- **Opening a problem:** swipe vertically on the keyboard surface to move through the list, then press Enter to open. Tapping a row also works.
- **Offline status:** each row shows whether that problem is saved for offline use.

---

## 5. Text input design

### 5.1 Owning the input stream

*Finalised after Phase 0. Evidence is in `docs/device-input-report.md`.*

- **Event order (confirmed, K15).** Every hardware key is seen first by **`dispatchKeyEventPreIme()`**, then by `Activity.dispatchKeyEvent()`, then by the view. The `InputRouter` handles keys in the editor view's `dispatchKeyEventPreIme()` / `onKeyPreIme()`. In text input modes the Unihertz IME (`com.iqqijni.bbkeyboard`) consumes `sym` and `sym`+letter after pre-IME, so pre-IME is the only reliable place to catch them.
- **No IME text path (confirmed, K14).** The editor's `onCreateInputConnection()` reports `inputType = TYPE_NULL`. In TYPE_NULL, text arrived exactly as typed with 0 composing or correction calls.
  - Nothing arrives through the `InputConnection`, so the router produces every character itself with `KeyEvent.getUnicodeChar(metaState)` and passes it to `EditorActions.insert()`.
  - Sora's own `onKeyDown` text path is either delegated to or bypassed, whichever M1 shows is more reliable. Either way, there's exactly one path that inserts text.
  - **Cursor Assistant works fully in TYPE_NULL** (follow-up, 2026-11-01). With the field focused, its up/down/left/right arrows reached pre-IME, the activity and the view. In normal text mode the IME swallowed them after pre-IME, which is another reason to prefer TYPE_NULL. Earlier "no arrows in TYPE_NULL" runs were the probe field not having focus.
  - The fallback `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` was also clean in K14. It stays as a fallback only.
- **Alt characters come from the key map (K03).** `TitanKey.kcm` defines every printed Alt character, and `getUnicodeChar()` returns them with no IME involved. **No mapping table is needed.** The router must drop Alt+Space, which produces the private-use character `U+EF01`.
- **No hardware key repeat (K10).** The keyboard never sends repeat events: repeatCount stays 0 and `FLAG_LONG_PRESS` never appears. The router implements its own repeat for **Backspace, forward delete (Shift+Backspace) and characters (plain, Shift, Alt and Alt+Shift)**: start after a 400 ms hold, then repeat every 40 ms until key-up. **`sym` chords never repeat.** These values get tuned in M1. This adds no shortcut; it makes holding a key behave as users expect.
- **Keyboard-surface events must be intercepted (§5.6).**
  - With Scroll Assistant on, swipes on the keys arrive as screen-coordinate touch events from the `touchPad` device. `Activity.dispatchTouchEvent()` consumes every `touchPad` event.
  - Each swipe also sends a `KEYCODE 404` (scancode 404) DOWN/UP pair, in every touch mode. The router swallows it.
  - Cursor Assistant sends `DPAD_UP/DOWN/LEFT/RIGHT` from `touchPad`. The router handles these as cursor moves (§5.6).
- **Forward delete = Shift+Backspace.** The keyboard has no Delete key. Shift+Backspace deletes the character after the cursor (K08 showed Shift+DEL reaches the app), and repeats when held. With a selection, Backspace and Shift+Backspace both delete the selection.
- **Other keys.**
  - **`fn`** (keycode 403, `FUNC3`, scancode 251) reaches pre-IME in every mode and opens the symbol palette (§5.3).
  - Alt+Space produces the private-use character `U+EF01` and is dropped.
  - HOME and APP_SWITCH on the bottom row belong to the system.
- **Back key (the keyboard has no Esc).** Back reaches pre-IME. The router handles it in this order:
  1. If an overlay is open (Find bar, Go-to-line bar, cheat sheet or `fn` palette), Back closes it and does nothing else.
  2. Otherwise, **in the Code pane Back is ignored**, so a stray press can't leave the app mid-edit.
  3. **On Results or Comments**, Back goes to Code.
  4. **On Problem**, Back sends the app to the background (`moveTaskToBack(true)`) rather than finishing the activity, so nothing is torn down. From M2, when the problem list exists, Back on Problem returns to the list instead.
  - Leaving the app from Code is done with Home, Recents, or the red button to Problem and then Back.
- **Keys on the Problem, Results and Comments panes.** The editor doesn't have focus there, so `MainActivity.dispatchKeyEvent()` passes keys to the `InputRouter` in **non-Code mode**:
  - Leader commands that don't edit text work: `sym+R/E/S` (Run, Run remote, Submit), `sym+T` (Results), `sym+H` (cheat sheet). The `[sym]` indicator works too.
  - Editing commands (`sym+U/Y/C/X/V/L/D/J/K/G/F/I/O/N/Space`), characters, Backspace, Enter and `fn` are **ignored**. They never reach the WebView, and they don't switch to Code.
  - Back follows the rule above. The red button and keycode 404 are handled as on every pane.
- **Alt+Shift on keys other than S/D/F/Z** inserts nothing and is logged (§5.3).

### 5.2 Modifier model

*Finalised after Phase 0.*

| Layer | How to reach it | Purpose |
|---|---|---|
| **Base** | Plain keys | Letters, space, Enter, Backspace. There are **no dedicated `,` `.` or digit keys** on this keyboard. |
| **Shift** | Hold Shift | Capitals, and selection while swiping (§5.6, confirmed by G05) |
| **Alt** | **Hold** Alt (K04: no one-shot or locking Alt at system level) | **The characters printed on the keycaps**: digits `0–9`, `, . ? ! @ # ' " : _ - + * / ( )` |
| **Alt+Shift** | Hold Alt and Shift, in either order | Four symbols the key map lacks, produced by the router (§5.3): `$` `%` `^` `&` |
| **Leader** | **Hold `sym`** and press a letter | Editor commands (§5.4) |
| **Symbol palette** | Tap **`fn`** | Touchscreen picker for the other missing symbols (§5.3) |

There's **no tap behaviour on `sym` and no sticky modifiers.** A `[sym]` indicator appears in the status line while `sym` is held.

**`sym` works as the leader (confirmed).** It arrives as `KEYCODE_SYM` (63) with `META_SYM_ON`, and all 26 `sym`+letter chords reach pre-IME (K05, K06).

**One ordering to avoid (K07).** Pressing **Shift first and then `sym`** opens the system keyboard picker. `sym` then Shift then letter works. No leader binding uses Shift (§5.4), so this only matters if someone presses Shift by accident. The router does not try to block the system picker.

### 5.3 Symbols

*Finalised after Phase 0. The owner decided the fix (Appendix B).*

**Phase 0 result (K16, TYPE_NULL).** The full table with key positions is in `docs/device-input-report.md` §8.

| Keystrokes | Symbols |
|---|---|
| 2 (Alt + key, native) | `_` `(` `)` `:` `'` `"` `#` `+` `-` `*` `/` `!` `@`, plus `,` `.` `?` and `0–9` |
| More than 2 | none |
| Missing | `=` `[` `]` `{` `}` `<` `>` `%` `\` `\|` `&` `^` `;` `~` `` ` `` |

**There's no built-in way to reach the missing symbols.** On 2026-11-01 these were tested in both TYPE_NULL and the Unihertz IME's text mode: tapping `sym`, tapping `fn`, `fn`+letter, Alt+Space, Alt+Shift+key, and holding a key or Alt+key.
- None of them opens a symbol menu or produces a missing symbol.
- In text mode, Alt+Space types a space and Alt+Shift+F types `6`.
- Keys never long-press or repeat.

**The fix (owner decision).** `` ` `` and `\` are not needed. The other 13 are covered like this:

1. **Alt+Shift on the digit key (3 keystrokes).**
   - On a standard keyboard, `$ % ^ &` are Shift+4, 5, 6, 7. Here the digits are Alt+S, Alt+D, Alt+F and Alt+Z, so the router maps Alt+Shift+S/D/F/Z to `$` `%` `^` `&`.
   - The keys arrive at pre-IME with `META_SHIFT_ON | META_ALT_ON` and no character from the key map, so nothing else claims them.
   - No other Alt+Shift combination is defined, so the printed symbols keep exactly one way each.
2. **Symbol palette (`fn`, then a tap).**
   - Tapping `fn` opens a touchscreen grid of `=` `[` `]` `{` `}` `<` `>` `|` `;` `~` `%` `^` `&`.
   - Tapping a symbol inserts it at the cursor and closes the palette. It goes through the same `EditorActions.insert()` as a typed key, so auto-pairing (`[` and `{`) and **type-over of closers (`]` and `}`)** apply exactly as for typed characters, including the no-pairing rule inside strings and comments (§5.5).
   - `fn` again or Back closes it without inserting. **Typing any other key closes it and the key is typed as normal**, so nothing you type is lost.
   - It shows as a compact strip just above the bottom status line, so the code stays visible.
   - As a touch control it doesn't count against the one-binding rule (§1 principle 4), so `% ^ &` also appear there.
3. **Later, if the owner wants:** a keyboard binding for `=`, the most common missing symbol. Not planned now; we'll judge by how the palette feels in use.

### 5.4 Leader commands (hold `sym` and press a letter)

The bindings use **letter keys only, matched by physical key**, so no binding needs a second modifier. Each action has exactly one binding.

| `sym` + | Action |
|---|---|
| **R** | Run the examples and saved test cases **locally** |
| **E** | Run on LeetCode (remote) |
| **S** | Submit. Press `sym+S` a second time to confirm. |
| **T** | Open or close the Results pane |
| **U** | Undo |
| **Y** | Redo |
| **C** / **X** / **V** | Copy / cut / paste. Copy and cut act on the current line when nothing is selected; text copied that way is pasted as a whole line **above the current line** (as in VS Code). Other text pastes at the cursor, replacing any selection. |
| **L** | Select line (repeat to extend) |
| **D** | Delete line |
| **J** | Duplicate line |
| **K** | Toggle comment |
| **G** | Go to line: a one-line bar above the status line. Type the number (digits are Alt+key, as everywhere else), then Enter jumps; Back closes. |
| **F** | Find: a one-line bar above the status line. Matching is **case-insensitive** and **wraps around** the end of the buffer. Enter jumps to the next match; Back closes. No replace. Both bars take keys through the same `InputRouter`, so Alt, Alt+Shift and the `fn` palette work in them. |
| **I** / **O** | Font size up / down |
| **W** | Toggle soft wrap (§4.2); saved |
| **H** | Cheat sheet: an overlay generated from the keymap (this table, the Alt+Shift symbols and the `fn` palette), so it can't drift from the real bindings |
| **N** | Reset to starter code. Press `sym+N` a second time to confirm. The previous draft is kept: in M1 as one backup file per problem (overwritten on each reset), and from M2 in the draft version history. |
| **Space** | Complete or indent (see below) |

Move line up or down had a `sym`+vertical-swipe gesture, but **the keyboard surface sends nothing while `sym` is held** (G07). So that action has no binding for now; the owner can assign a letter later.

**`sym+Space` has one meaning in each context:**

- **When the completion popup is open,** it accepts the highlighted item. Snippets (`bfs`, `memo`, `dd`, …) are listed as items in the same popup, so accepting a snippet item expands it. Because snippets live in the popup, a completion and a snippet with the same name can't conflict: whichever item is highlighted is the one that's used.
- **When no popup is open,** it inserts one indent level at the cursor.
- **In M1 there's no popup yet** (autocomplete is M5), so `sym+Space` always indents.

**Not available yet in M1:** `sym+R`, `sym+E` and `sym+S` (and the Run/Submit buttons) show a brief "Coming in M3/M4" message and do nothing else.

Run on the red button is removed, and so is the long-press for Results. The red button only switches panes (§4.1).

### 5.5 Python-aware editing

- **Auto-pairing with type-over.** `(`, `[`, `{`, `'` and `"` insert their closers. Typing a closer that's already there skips over it, and Backspace on an empty pair deletes both.
  - **No auto-pairing inside a string literal or a comment**, judged from the TextMate tokens at the cursor. There, `(` `[` `{` insert only themselves, so `'(['` types as written. Typing the string's own closing quote still skips over it, so `''.join` works.
  - The same rules apply to symbols from the `fn` palette (§5.3).
  - **With a selection, typing replaces it**, opening brackets and quotes included (no wrapping).
- **Smart Enter:**
  - The new line copies the current indentation.
  - After a line ending in `:` it indents one level.
  - Between a bracket pair (`(|)`, `[|]` or `{|}`) it opens a block: the closer moves to its own line at the outer indent, and the cursor sits on an indented line between.
  - After `return`, `break`, `continue`, `pass` or `raise` it dedents.
- **Smart colon** (a setting, off by default). Pressing Enter after a block header that has no colon adds one first.
- **Smart Backspace.** In leading whitespace, Backspace deletes back to the previous indent stop. **This is how you dedent at the start of a line** (e.g. after the last line of a loop body).
- **Undo** groups a run of consecutive typing into one step, broken by a cursor move, a command or a pause of about 1 s. Each command (`sym+D`, paste, smart Enter, …) is one step.
- **Autocomplete popup (M5).** Suggestions include identifiers from the buffer, Python keywords and builtins, LeetCode vocabulary (`defaultdict`, `Counter`, `deque`, `heapq`, `bisect_left`, `SortedList`, `ListNode`, `TreeNode`, …) and snippets. Accept an item with `sym+Space`.
- **Starter code.** We pre-fill LeetCode's `python3` snippet. These snippets end with an **empty method body (no `pass`)**, so the cursor goes on the indented empty line of the first method body. This needs checking against real snippets: unit tests include several real snippets as golden files.
- **Pre-imported names.** LeetCode already imports common modules, so we don't add imports. The local runner uses the same preamble, verified against LeetCode (§7).

### 5.6 Cursor movement and selection with the touch keyboard

*Finalised after Phase 0. Evidence is in `docs/device-input-report.md` §4.*

| Gesture on keyboard surface | Action |
|---|---|
| Horizontal swipe | Move by character, accelerated. **Stops at the start or end of the line**; it never crosses to the next line (owner, M1 step 2). With wrap on it still crosses screen rows within the same line. Arrow keys still cross lines as usual. |
| Vertical swipe | Move by **line**, keeping the column. With soft wrap on (the default), "line" means **screen row**, so a wrapped line takes several steps. The view follows the cursor, and there's no separate scroll mode. |
| **Shift held** + swipe | Extend the selection *(confirmed by G05: events carry `META_SHIFT_ON`)* |
| Long slide (the system's backlight gesture) | Ignored. A stroke that covers **more than 75% of the surface width at an average speed above 2000 px/s** is dropped, and any cursor movement it already made is undone (G13). Width alone can't tell it apart: the Phase 0 Shift-select swipe covered 893 px at about 1140 px/s, while the long slides covered 874–1015 px at 2900–6300 px/s. |

**Removed (G06, G07):** Alt+swipe (word move and block jump) and `sym`+vertical swipe (move line). While Alt or `sym` is held, the surface sends **no events at all**. These actions have no gesture; the owner can give them key bindings later (§5.4).

**What the touch modes deliver (Phase 0):**

| Mode | Events | Use |
|---|---|---|
| **Scroll Assistant** | Touch MotionEvents from `touchPad`: both axes, about 10 ms samples, Shift flag kept | **Primary.** It's the only source of vertical movement and selection. |
| **Cursor Assistant** (double-tap the keys, then slide) | Standard `DPAD_UP/DOWN/LEFT/RIGHT` key events from `touchPad`, one per step, about 25–85 ms apart. In TYPE_NULL they reach pre-IME, the activity and the view. The editor must have focus. Cursor mode survives input-mode changes. | Accepted as normal arrow keys. Not required: Scroll Assistant covers the same movement. |
| Pointer capture (G10) | 0 events | Not used |
| Mouse mode | Doesn't exist on this device | — |

**Cursor Assistant follow-up (2026-11-01):**
- **It's plain arrow keys, in all four directions.** Cursor Assistant sends standard `KEYCODE_DPAD_*` events, the same as a hardware keyboard's arrow keys, which is why it "just works" in other apps.
  - In the probe, with a focused TYPE_NULL field holding three lines, it sent up ×7, down ×9, left ×21 and right ×10, all reaching the view.
  - A single line of text gave only left/right, because there was nowhere to go vertically.
  - So the editor just handles `DPAD_*` as cursor moves (`EditorActions.moveLeft/Right/Up/Down`, with up/down keeping the column). Nothing else is needed.
- **No selection through Cursor Assistant.** Pressing any key, Shift included, ends cursor mode (owner, in other apps). Selection always comes from **Shift held + a Scroll Assistant swipe**, which carries `META_SHIFT_ON` (G05, and again with both assistants on).
- **Both assistants can be on at once.** In other apps each does its own job. In the probe with both on, every slide arrived as Scroll Assistant touch strokes, including the double-tap slides, and no arrow keys were mixed in. The editor therefore never sees the same slide as both a swipe and arrow keys, and won't move twice.
- **The app can't see Cursor Assistant's double-tap** (M1 step 2, 2026-11-01). With Scroll Assistant on, a stroke log of plain swipes, double-tap-then-slides, double-taps and a single tap recorded only slides: taps send no `touchPad` events (as G11 found). So the app can't tell a Cursor Assistant slide from a plain swipe, and both get the same treatment: they move the cursor.
- **Recommendation:** keep Scroll Assistant ON, which is required. Cursor Assistant adds nothing in Titan Code while Scroll Assistant is on (its slides arrive as Scroll Assistant strokes), so it can be on or off as other apps need. Its arrows are still accepted if they ever arrive.

How the gesture engine works:

1. **Requirement:** Scroll Assistant must be ON. It has a Quick Settings tile (`touchpad_scroll_assistant`), and no readable settings key was found. Onboarding asks the user to enable it, and the editor shows a one-line hint if a swipe produces only keycode 404, the signature of Scroll Assistant being off.
2. **Interception:** `Activity.dispatchTouchEvent()` routes every MotionEvent whose device is named **`touchPad`** to the gesture engine, matching by name rather than device id, and consumes it. These are screen-coordinate touch events: if they weren't consumed they would tap and drag on whatever view is underneath. Real screen touches (`fts_ts`) pass through unchanged. The router swallows the trailing keycode 404 pair (§5.1).
3. **Deltas:** the engine works from the difference between successive x/y samples (about 10 ms apart), with acceleration and axis locking. Modifiers come from `event.metaState` together with the router's own key state.
4. **Noise filtering:** typing produces short stray `touchPad` sequences ending in `ACTION_CANCEL` (G08). Movement starts only after the finger has travelled 24 px; once it starts, travel is counted from the touch-down point (a fast stroke's first sample is often already far past 24 px). A stroke ending in `ACTION_CANCEL` before that point is discarded, while one that has already started moving is treated as a normal end (G09, G12).
5. **Starting values, tuned with the owner in M1.** Surface coordinates span about 1080 px across the keyboard (one key is about 108 px).
   - **Axis lock:** a stroke locks to the axis with the larger travel after the 24 px start threshold. Diagonal strokes don't move both ways.
   - **Horizontal:** 1 character per **70 px** (40 and then 60 px felt too fast to the owner in M1 step 2), multiplied by an acceleration factor of `1 + speed/1000 px·s⁻¹`, capped at 4×.
   - **Vertical:** 1 line per **90 px** (60 px felt too fast to the owner in M1 step 2), with no acceleration.
   - Tuned values are recorded in PROGRESS.md.
   - **Adjustable in the Settings screen (M2)** (owner request): horizontal speed (px per character) and vertical speed (px per line), each with a slider that takes effect immediately, so they can be tuned while trying the editor. Until then the tuned values above are fixed.
6. **Problem pane:** a vertical stroke scrolls the WebView **1:1** (surface pixels to content pixels, with no inertia), so the keyboard surface scrolls it as §4.1 describes. Horizontal strokes do nothing there. **On Results and Comments** (placeholders in M1), surface events are consumed and ignored.
7. **Arrow keys:** `DPAD_LEFT/RIGHT` move one character and `DPAD_UP/DOWN` move one line, keeping the column. They work the same from Cursor Assistant or any other source, and extend the selection while Shift is held.

The screen stays on (`FLAG_KEEP_SCREEN_ON`) while editing. **The keyboard backlight is left to the system** (owner decision, Appendix B).

### 5.7 Conflicts with system shortcuts

- **Per-letter shortcuts.** Holding a letter for 1.5 s fired nothing and the app kept focus (K10). Whether an *enabled* Unihertz per-letter or Universal shortcut takes keys is still untested (**K11 not run**). If one does, onboarding shows how to turn it off.
- **Shift then `sym`** opens the system keyboard picker (K07). No binding needs that order.
- **Red button** is taken by the system unless its **PTT app** is set to Titan Code. Then it arrives as keycode 401 (§4.1).
- **Required settings.** Onboarding checks these, deep-linking where possible:
  - Scroll Assistant on (§5.6)
  - the red button's PTT app set to Titan Code (Settings → Shortcut keys → PTT key, §4.1)
  - per-letter shortcuts off, if K11 shows they interfere

---

## 6. Platform integration

### 6.1 Provider interface

```kotlin
interface ProblemProvider {
    val id: String                       // "leetcode", "neetcode", "codeforces"
    val capabilities: Set<Capability>    // RUN_REMOTE, SUBMIT, COMMENTS, LISTS, DAILY, CONTESTS, …
    val ioStyle: IoStyle                 // FUNCTION_CALL (LeetCode/NeetCode) or STDIN_STDOUT (Codeforces)

    suspend fun listProblems(filter: ProblemFilter, page: Page): List<ProblemSummary>
    suspend fun getProblem(id: ProblemId): Problem   // statement, snippets, examples, metaData, limits, tags
    suspend fun runRemote(problem: Problem, code: String, input: String): RunResult
    suspend fun submit(problem: Problem, code: String): SubmissionResult
    suspend fun comments(problem: Problem, page: Page): List<Comment>
    fun loginUrl(): String                           // the platform's own login page
}
```

- **Neutral models.** `Problem`, `TestCase`, `Verdict`, `RunResult` and `SubmissionResult` are shared across platforms. Each one also keeps the platform's original fields.
- **IDs.** `ProblemId` carries every identifier a platform needs. For LeetCode that's the **numeric `questionId` and the `titleSlug`**, because `interpret_solution` and `submit` need the numeric ID.
- **The UI adapts to capabilities.** Actions a platform doesn't support are hidden.

### 6.2 LeetCode (v1): leetcode.com only

Only **leetcode.com** is supported. **leetcode.cn is out of scope**: it uses different hosts, accounts and API details.

| Feature | Endpoint (unofficial, subject to change) |
|---|---|
| Problem list | `POST /graphql` → `problemsetQuestionList` |
| Problem detail | `POST /graphql` → `question(titleSlug)` → `questionId`, `content`, `codeSnippets`, `exampleTestcases`, `metaData`, `topicTags`, `hints` |
| Daily problem | `POST /graphql` → `activeDailyCodingChallengeQuestion` |
| Run code | `POST /problems/{slug}/interpret_solution/` → poll `GET /submissions/detail/{id}/check/` |
| Submit | `POST /problems/{slug}/submit/` → poll `GET /submissions/detail/{id}/check/` |
| Submissions | `POST /graphql` → `questionSubmissionList` |
| Comments / solutions | GraphQL discussion and solution queries (exact queries to be confirmed) |

The `check` responses for **Run and Submit have different shapes**. Run returns `code_answer` and `expected_code_answer` lists, while Submit returns totals and a last failing test case. Each gets its own parser and its own tests.

**Login: LeetCode's own username and password only.** There's no Google, GitHub or other social or OAuth sign-in. The login page loads in an in-app WebView so that CAPTCHA and Cloudflare checks run in a real browser. After login, the app copies the cookies (`LEETCODE_SESSION`, `csrftoken`, `cf_clearance`) from `CookieManager` into EncryptedSharedPreferences.

**Cloudflare risk and fallback.** Cloudflare may reject OkHttp requests even when the cookies are valid, because OkHttp's TLS fingerprint and user agent differ from the browser's. `HttpTransport` handles this in three steps:

1. OkHttp sends **the WebView's exact user agent and all of its cookies**, including `cf_clearance`, along with the `x-csrftoken` and `Referer` headers.
2. If the response is a 403 or a challenge page, the request is **re-sent from a hidden, logged-in WebView** with JavaScript `fetch()`, which uses the browser's own TLS stack and cookies. The result is returned through a JS bridge. After repeated failures, the app uses this path for the rest of the session.
3. If the WebView also gets a challenge, the app shows it so you can solve it, then retries.

**Phase 0 result (C01):** signed in through the WebView, `userStatus` returned HTTP 200 with no challenge over OkHttp (both user agents) and over WebView `fetch()`. Step 1 works today. Steps 2–3 stay in as protection against future Cloudflare changes.

### 6.3 Later platforms (designed for, not built in v1)

- **NeetCode (M6).** Starts as lists only (NeetCode 150, Blind 75), with each entry mapped to its LeetCode problem.
- **Codeforces (M6).**
  - **Login:** Codeforces' own login form only.
  - **Data:** the official API for problems, contests and status, plus the statement's HTML.
  - **Submitting:** through the logged-in web form.
  - **Running:** uses the runner's stdin/stdout mode and offers a PyPy 3 or Python 3 choice on submit.

---

## 7. Local runner

The goal is to run the examples and saved test cases in **under a second, offline**.

- **A separate process.** Chaquopy normally runs inside the app's process. Python threads can't be killed, and deep recursion can crash the whole process. So user code runs in **`android:process=":runner"`**, connected over a bound service (Messenger/AIDL).
  - **Timeouts:** on timeout or a crash, the app kills the process and starts a new one. The editor is never affected.
  - **Startup:** a **warm spare** runner process is kept ready, so the time spent starting Python and importing modules doesn't count against the one-second target.
  - **Recursion:** user code runs on a thread with a **large stack** (e.g. 256 MB via `threading.stack_size`) and a **raised recursion limit**, so recursive DFS solutions behave as they do on LeetCode.
- **Match the judge:**
  - **Python version:** the same minor version LeetCode uses, checked against LeetCode's language help page and re-checked when LeetCode changes it.
  - **Bundled packages:** `sortedcontainers`, which LeetCode provides.
  - **Preamble:** the **exact list of LeetCode's pre-imported names**, confirmed by running a probe solution on LeetCode that prints `sorted(globals())` and module names. We use that list, not a guess.
- **The harness** is generated from `metaData` (function name, parameter types, return type). It parses inputs, calls `Solution().method(...)`, and converts `ListNode` and `TreeNode` to and from LeetCode's list notation.
- **Result checking modes** are selected per problem from `metaData` and a small table of overrides kept with the code:

| Mode | Examples |
|---|---|
| Exact match | Most problems |
| Any order | "Return the answer in any order" (e.g. Group Anagrams, permutations) |
| Float tolerance | Answers within 1e-5 count as correct |
| In-place (`void`) | Check the modified argument instead (`sortColors`, `rotate`) |
| Return k and the first k elements | `removeDuplicates`, `removeElement` |
| Linked list with a cycle | Build the cycle from `pos`, which isn't a function argument |
| Special node types | Graph `Node` (`cloneGraph`), random-pointer nodes |
| Interactive | Provided-API stubs (`isBadVersion`, `guess`) driven by hidden parameters in the test input |
| Design | Operation and argument lists (`LRUCache`) |
| stdin/stdout | Codeforces (M6) |

- **Output:** each case shows pass or fail, the actual versus expected values, any `print()` output, and tracebacks. Tapping a traceback line jumps the editor to it.
- **Submit always goes to the platform.**

---

## 8. Offline practice

You can practise with no connection: browse problems, read statements and comments, write code and run it locally. **Run on server and Submit need the network.**

### 8.1 Offline-first data flow

- **One source of truth.** Every screen reads from the local store (Room plus files). Providers only refresh that store.
- **Status badge.** The status bar shows online, offline or syncing, and how old the open problem's saved data is.
- **Offline Submit is blocked, not queued.** Pressing `sym+S` or `sym+E`, or tapping Submit or long-pressing Run, while offline shows: *"Offline — your draft is saved. Submit when you're back online."* There's no queue and nothing is sent automatically later. This fits the one-way rule and avoids surprise submissions.

### 8.2 What gets saved per problem

| Data | Stored as | Notes |
|---|---|---|
| Statement | Sanitised HTML in Room, with images downloaded and `<img>` tags rewritten to local paths | Rendered with bundled CSS, fonts and KaTeX |
| Metadata | Title, difficulty, tags, hints, limits, `metaData`, `questionId`, slug, similar problems | Hints are collapsed |
| Starter code | The `python3` snippet | Problems without one are hidden |
| Test cases | `TestCase(input, expectedOutput, source)` | See §8.3 |
| Comments and solutions | The top *N* threads (configurable, default 30) with replies, and the top solutions | Solutions stay behind the reveal |
| Your work | Drafts with a short version history, notes, run history, submission history | **On the device only. No export or sync.** Never deleted when storage is cleared. |

### 8.3 Test cases that work offline

- **Examples** from the statement work offline as soon as the problem is saved.
- **Custom tests run on LeetCode** (`sym+E`) are saved together with LeetCode's official expected output (`expected_code_answer`).
- **Failed submissions** save the failing input, plus its expected output for a Wrong Answer.
- **Custom tests written offline** run locally and are marked *unverified* until they're run on LeetCode.
- Hidden tests are never available.

### 8.4 Downloading for offline use

- **Offline packs:** by LeetCode study plan, favourites list, tag or difficulty, or everything. A size estimate is shown first. NeetCode lists come in M6.
- **Save on open:** opening a problem saves it, and also saves the next *K* problems in the current list.
- **Background sync** runs through WorkManager. It's Wi-Fi only by default, resumable, rate-limited to a few requests per second with backoff, and refreshes on a schedule.
- **Storage:** the Settings screen shows a breakdown, and each pack can be cleared on its own. Clearing never removes drafts, notes or history.

---

## 9. Phase 0: Device probe and keyboard test plan

**The probe is built before any other app code.** It lives in the real project skeleton, so it also counts as M0.

> **Phase 0 is complete** (see PROGRESS.md). These sections are kept as the record of what the probe is and what was tested. The probe stays in the repo as a throwaway diagnostic tool: re-run it after Unihertz firmware updates.

### 9.1 How we work together

1. **Build and install.** The agent runs `scripts/install.sh probe` on the Mac (§11).
2. **Guided run.** The owner opens the probe, chooses **Guided run**, and does each prompted action.
3. **Report.** The probe saves a JSON report with the raw events and a Markdown summary to `Download/titan-probe/` (written through MediaStore on Android 16). It also offers the share sheet. The owner sends the files back, or they're pulled with `adb pull /sdcard/Download/titan-probe/`.
4. **Finalise.** The agent writes `docs/device-input-report.md` and finalises §5.1, §5.2, §5.3 and §5.6.
5. **Retest.** Any test marked Fail or Unclear is run again.

### 9.2 Probe app requirements

- **Guided mode.** One test per screen, with a stable ID (e.g. `K05`). Each screen shows:
  - the instruction and the expected behaviour
  - a live event log
  - touchscreen buttons for **Pass / Fail / Unclear / Notes**, plus **Next / Back**. The keyboard is the thing under test, so it isn't used to mark results.
- **Automatic checks** where the expected events are known (e.g. K01 expects `KEYCODE_A`…`KEYCODE_Z`). The probe marks Pass or Fail itself and lists anything missing.
- **Free-log mode:** a full-screen live log of everything received, for exploring.
- **What it logs:**
  - every `KeyEvent`: action, keyCode, scanCode, metaState, repeatCount, `getUnicodeChar(meta)`, source, deviceId, timestamp, and **which stage saw it** (pre-IME view, Activity dispatch, view `onKeyDown`)
  - every `MotionEvent`: source, tool type, action, `X/Y/RELATIVE_X/RELATIVE_Y/VSCROLL/HSCROLL`, timestamp
  - text committed through the `InputConnection`, to tell IME-produced characters apart from key-map characters
  - pointer-capture state: requested, granted or lost
- **Input-mode switch** for the test field: `TYPE_NULL`, or `TEXT | NO_SUGGESTIONS | VISIBLE_PASSWORD`, or normal text. Each result records which mode was active.
- **Report header:** model, Android version, screen size and density, default IME, which Unihertz keyboard modes the owner says are on, and the app version.
- **Logcat mirror** under the tag `TitanProbe`.
- **A Cloudflare check screen** (C01). After the owner logs in, it makes one GraphQL request with OkHttp and one through WebView `fetch()`, and records both status codes (§6.2).
- It's a throwaway app, so no polish is needed.

### 9.3 Test cases

**A. adb checks** (no app needed). Run these from the Mac with the phone connected:

```bash
adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell wm size; adb shell wm density
adb shell dumpsys input > input.txt                 # keyboard/touchpad devices, .kl/.kcm files, backlight controller
adb shell getevent -il                              # device capabilities
adb shell ls /system/usr/keylayout /vendor/usr/keylayout /system/usr/keychars /vendor/usr/keychars
adb pull /vendor/usr/keychars/<keyboard>.kcm; adb pull /vendor/usr/keylayout/<keyboard>.kl
adb shell ime list -s
adb shell settings list system > sys-before.txt     # toggle the backlight with a long slide, then:
adb shell settings list system > sys-after.txt      # diff to find the backlight key
adb shell settings list secure | grep -iE 'unihertz|keyboard|cursor|scroll|mouse|shortcut'
adb shell ls /sys/class/leds/
```

Also take a photo of the keycaps.

**B. Keys.** Run each test in `TYPE_NULL` mode and in normal-text mode unless noted.

| ID | Action | What we learn |
|---|---|---|
| K01 | Type a–z | Every letter arrives; keyCodes and characters |
| K02 | Shift + each letter; Shift alone | Capitals; whether Shift is held, one-shot or locks |
| K03 | Alt + every key (compare with the keycap photo) | Each Alt character, and whether it comes from the key map or only as committed IME text |
| K04 | Alt alone; double-tap Alt | Whether the system has its own sticky or locking Alt |
| K05 | Tap `sym` alone | Does it reach the app? Does a system symbol picker open? |
| K06 | Hold `sym` + each letter | Does the app get the chord (META_SYM_ON)? Does the system consume any of these? |
| K07 | Hold `sym` + Shift + a letter | Whether three-key chords work |
| K08 | Space, Enter, Backspace, plus each of them with Shift, Alt and `sym` held | Special-key events, including `sym+Space` |
| K09 | The native `,` and `.` keys; any Fn/Ctrl key; Back; Home; Recents | Which extra keys exist and reach the app |
| K10 | Hold a letter for 1.5 s | Key-repeat events; whether a long-press shortcut fires and the app loses focus |
| K11 | Turn on a per-letter shortcut and a Universal shortcut, then repeat K10 with the editor focused | Whether system shortcuts take keys before the app |
| K12 | Red side button: short press, long press, double press | Does it reach the app? keyCode? Any system action? |
| K13 | Type `for i in range(len(nums)):` fast, 5 times | Dropped or reordered keys (rollover) |
| K14 | Type `teh `, `i `, a double space, and a sentence start, in each input mode | Whether autocorrect, auto-capitalisation and "double space → period" are fully suppressed |
| K15 | Any key, while recording which handler sees it first | Confirms pre-IME interception works |
| K16 | Type ``= _ [ ] ( ) { } : < > ' " # + - * / % \ | & ^ ; ~ ! @ ` `` using the native layers | Keystrokes per symbol; which symbols are missing (feeds §5.3) |

**C. Touch keyboard surface.** Run each test four times: with the touch features off, Scroll Assistant on, Cursor Assistant on, and Mouse mode on. *(Phase 0: there's no Mouse mode, and Cursor Assistant needs a double-tap before sliding, which the guided run missed; it was re-tested in Free log.)*

| ID | Action | What we learn |
|---|---|---|
| G01 | Slow horizontal swipe, right then left | Event type (DPAD / scroll / pointer) and size |
| G02 | Fast horizontal swipe | Whether speed shows up in the events (for acceleration) |
| G03 | Vertical swipe, up then down | Event type for vertical movement |
| G04 | Diagonal swipe | How the axes split; whether we need axis locking |
| G05 | Hold Shift with one hand and swipe with the other | **Do swipes register while a key is held?** (selection depends on this) |
| G06 | Hold Alt and swipe | Same question, for word and block movement |
| G07 | Hold `sym` and swipe vertically | Same question, for leader + swipe (move line) |
| G08 | Type normally for 30 s | Stray motion events from typing (palm rejection) |
| G09 | Flick up on the surface | Whether the system's flick gesture reaches the app (so we can ignore it) |
| G10 | Request pointer capture, then repeat G01–G03 | Whether capture works and what raw deltas look like |
| G11 | Light tap on the surface without pressing a key | Whether a tap creates any event |
| G12 | Swipe and lift quickly | Whether the system adds inertia after lifting |
| G13 | Long slide (backlight gesture) | Whether the system consumes it; make sure we don't react to it |

**D. Backlight.** B01–B03 were run. No app-controllable method exists, and the owner dropped the requirement (Appendix B). The results are in the device report.

**E. Lifecycle and network**

| ID | Action | What we learn |
|---|---|---|
| L01 | Switch away from the app and back during a test | Whether pointer capture and focus are restored |
| L02 | Turn the screen off and on | Same |
| L03 | Change a Unihertz keyboard setting while the app is open | Whether the app needs to re-detect modes |
| C01 | Log in to LeetCode, then make one GraphQL request via OkHttp and one via WebView `fetch()` | Whether the Cloudflare fallback is needed from day one |

---

## 10. Testing strategy

- **Unit tests (JVM):**
  - `KeymapEngine`: every binding in §5.4, and that **no key maps to two actions**.
  - The gesture classifier, replaying Phase 0 event recordings. `reports/` is gitignored, so M1 copies a few trimmed recordings into **`app/src/test/resources/phase0/`** (committed): a horizontal swipe, a vertical swipe, a Shift-held swipe, typing noise ending in `ACTION_CANCEL`, a long slide, and a Cursor Assistant arrow run.
    - The first five come from `reports/probe-20261101-200140/titan-probe-report.json`, which is on the owner's Mac.
    - **The Cursor Assistant arrow run was never saved** (it was only seen in Free log). It's recorded once with the owner during M1: a 10-second double-tap-and-slide session in all four directions. Until then a synthetic fixture stands in, built from the documented shape (`DPAD_*` DOWN/UP pairs from device `touchPad`, about 25–85 ms apart).
  - Harness input parsing and each result-checking mode in §7.
  - Parsing the Run and Submit `check` responses, and mapping verdicts.
  - HTML sanitising and extracting examples.
  - Placing the cursor in the starter code, against real snippets saved as golden files.
- **Runner tests (instrumented):** timeouts, killing and restarting the process, deep recursion, and matching the pre-imported names.
- **On-device acceptance tests** run on a **guided acceptance panel**. It's built in M1, debug builds only (`src/debug/`), and modelled on the probe's guided run:
  - **It's a collapsible panel inside `MainActivity`, over the real shell** (§3), not a separate activity. Collapsed, it's a thin strip showing the test ID and Pass/Fail; expanded, it shows the full test card. So every test exercises the real shell, panes, red button, `InputRouter`, `GestureEngine` and `EditorActions`.
  - One test per card: the instruction, the pass condition, a live event log, and touch buttons for **Pass / Fail / Unclear / Notes** and **Back / Next / Reset**. Progress is saved, so a run can be resumed.
  - Editor tests load a test buffer into the real Code pane. Starting a test saves the current sample's draft first, and finishing restores it, so acceptance runs never overwrite drafts.
  - **For E01/E02 the reference solution is shown in the Problem pane** in place of the statement, so the owner can peek at it with the red button while typing. The expanded panel also shows it.
  - **E16 is shown as "not applicable until M4"** and skipped.
  - **Automatic checks** where the outcome is measurable. The owner still gives the final verdict.

    | Test | What's checked automatically |
    |---|---|
    | E01/E02 | Time from the first key to a buffer match; count of palette taps; buffer compared with the reference solution, ignoring trailing whitespace |
    | E03–E05 | Buffer and cursor after each scripted step |
    | E06 | Keystrokes per symbol |
    | E07/E08 | Cursor line, column and selection after each stroke |
    | E09 | Every leader binding fires exactly one action |
    | E12 | Status line shows `[sym]` while it's held |
    | E13 | Repeat start delay and rate, from event timestamps |
    | E14 | After force-stop and reopen, the draft matches what was typed |

  - **Report:** saved as JSON plus Markdown to `Download/titan-code-acceptance/`. `scripts/pull-report.sh app` copies it to `reports/acceptance-latest/`.

  The tests:

| ID | Test | Pass when |
|---|---|---|
| E01 | Type a full Two Sum solution, starting from the starter code; timed | Buffer matches the reference. The only screen touches are `fn`-palette symbol taps, which are counted and reported. |
| E02 | Same for Group Anagrams | Same |
| E03 | Auto-pairing: `(`, `[`, `{`, `'`, `"`; type the closer; Backspace on an empty pair | The closer is inserted; typing it skips over; Backspace deletes both |
| E04 | Enter after a line ending in `:`; Enter on a normal line; Enter after `return x`; Enter inside `[]` | Indents; copies the indent; dedents; opens a block |
| E05 | Backspace in leading whitespace | Deletes back to the previous indent stop |
| E06 | Each symbol from K16 (except `` ` `` and `\`) in the editor | Native Alt symbols take 2 keystrokes; `$ % ^ &` take Alt+Shift+S/D/F/Z; the rest go through the `fn` palette; nothing unexpected is inserted |
| E07 | Horizontal swipe; vertical swipe from a long line through a short line to a long line, once with wrap off and once through a wrapped line with wrap on; `sym+W` | Moves by character; moves by line (by screen row when wrapping) and keeps the column; `sym+W` switches wrap and it survives a restart |
| E08 | Shift + swipe; Cursor Assistant slides (double-tap, then slide) up, down, left and right in a 10-line buffer | Shift + swipe selects; Cursor Assistant moves the cursor in all four directions, with up/down keeping the column |
| E09 | Each leader binding in §5.4 | Each does exactly its one action; nothing else triggers |
| E10 | Red button repeatedly (PTT app = Titan Code) | Switches between Problem and Code (in M1 the Problem pane shows a bundled sample statement); scroll position and cursor are kept |
| E11 | Five minutes of free typing | No autocorrect, candidate bar or auto-capitalisation at any point |
| E12 | Hold `sym` | The bottom status line shows `[sym]` while it's held |
| E13 | Hold a letter; hold Backspace | No system shortcut fires; the app's own key repeat starts after about 400 ms |
| E14 | Type, **wait at least 2 s**, then run `adb shell am force-stop`, then reopen | The draft is intact. (Force-stop skips `onPause`, so only the 1 s debounced save protects the draft; the wait makes sure it has run.) |
| E15 | Look at every pane's top band and corners | The tab bar sits right of the camera and nothing is hidden by it; no control sits in a rounded corner |
| E16 | With airplane mode on: open a saved problem, read its comments, run locally, then press `sym+S` | Everything works except Submit, which shows the offline message |

---

## 11. Build and install (local, on the Mac)

All building and development happens **locally on the owner's Mac**. There's **no remote CI** (no GitHub Actions or other hosted builds).

- **Toolchain on the Mac (verified in Phase 0):**
  - **Temurin JDK 21** (`brew install --cask temurin@21`). Gradle 8.11 can't start on JDK 24 or newer, and Android Studio's bundled JBR is JDK 25, so `scripts/env.sh` picks a JDK between 17 and 23 automatically.
  - The **Android SDK** at `~/Library/Android/sdk`, from Android Studio's setup wizard. Gradle fetches `platforms;android-35` by itself.
  - The project uses AGP 8.7.3, Kotlin 2.1.0 and Gradle wrapper 8.11.1.
- **Builds go through the scripts.** `scripts/doctor.sh` checks the setup, and `scripts/install.sh [probe]` builds, installs and launches, logging to `build-logs/`. The agent runs these on the Mac itself.
  - **First M1 task:** extend the scripts for the app. `install.sh app` maps to `dev.titan.code/.MainActivity`; `doctor.sh` reports both apps; `logcat.sh [probe|app]` filters `TitanProbe` or `TitanCode`; `pull-report.sh [probe|app]` pulls the probe or acceptance report. `app` becomes the default once it exists.
- **Signing.** Builds are debug-signed with the Mac's standard **`~/.android/debug.keystore`**. Every build uses the same key, so a new APK installs over the old one without wiping drafts.
- **Install** with `scripts/install.sh app` (or `probe`). It runs `adb install -r -t` on the debug APK, so drafts survive reinstalls.
- **App IDs.** The probe and the real app are separate application IDs (`dev.titan.probe`, `dev.titan.code`), so they can both be installed at once.
- **Git.** The repo is on branch `master` with a remote `origin`. **Work on `master`. Commit and push only when the owner asks** (confirmed again for M1). `build-logs/` and `reports/` are local only (gitignored).

### 11.1 Working on the phone with the owner

The owner performs physical actions on the phone; the agent records and analyses. What worked in Phase 0:

- **Record in the background, analyse afterwards.** Start `adb logcat -c`, then `adb logcat -v time 'TitanCode:*' 'AndroidRuntime:E' '*:S' > <file>` as a background job. Ask the owner to do one short, specific sequence, then parse the file. Stop the recorder when done.
  - In zsh, **quote logcat filter specs** (`'TitanCode:*'`), or the shell treats `*` as a filename pattern and the command fails at once.
- **Before blaming the device, check the setup:**
  - Is the phone unlocked? A lock screen blocks adb UI work.
  - Does the text field have focus? Unfocused fields get no arrows or keys.
  - Which input mode is active, and which Unihertz touch modes are on?
  - Several Phase 0 "failures" were these.
- **Ask for exact, numbered steps** and one observation at a time. The owner answers what they *see*; the log provides what the app *received*.
- **Settings over adb.** `adb shell settings list global|system|secure` shows Unihertz keys (`agui_*`, `func1_*`, `fn_*`, `sym_*`). Read them freely. Change them only with the owner's OK, and restore the previous value afterwards.
- **Photos from the owner** arrive as HEIC in the repo root. Convert them with `sips -s format jpeg -Z 1600 <file> --out <scratch>.jpg` to view them, and don't commit them.
- **Screenshots:** `adb exec-out screencap -p > shot.png`, useful for checking layout without asking the owner.

---

## 12. Milestones

| # | Milestone | Done when |
|---|---|---|
| **M0** | Project skeleton, build and install script (§11), **Key & Gesture Probe** (§9), and the device input report | **Done** (K11 optional): the tests are recorded, §5 is finalised and the owner chose the symbol fix |
| **M1** | **Editor and input core** (no network, no Python runner): `:app` module and scripts (§3, §11); single-activity Compose shell with the four tabs in the top band; Sora editor with TextMate Python highlighting; pre-IME `InputRouter` (Alt, Alt+Shift symbols, `fn` palette, `sym` leader, arrow keys, app-side key repeat); auto-pairing and smart indent; keyboard-surface cursor and selection; red-button pane switch; Back handling; Find and Go-to-line bars; cheat sheet; **debug-only guided acceptance panel** inside `MainActivity` (§10).
<br>**M1 content is bundled, not downloaded:** 3 sample problems (Two Sum, Group Anagrams, Valid Parentheses) with statement HTML, `python3` starter snippet and reference solution, in `app/src/main/assets/samples/`. The agent writes these by hand (no network in M1). Each reference solution is written so that typing it with smart Enter, smart Backspace and auto-pairing produces exactly the reference text.
<br>• A **3-item picker** at the top of the Problem pane (a WebView with bundled CSS) chooses the active sample.
<br>• **Minimal draft autosave** writes one file per sample in app storage (debounced 1 s, and on pause), plus one backup file per sample for `sym+N`. The Room store replaces it in M2.
<br>• Results and Comments panes are placeholders. Run, Submit and `sym+R/E/S` show "Coming in M3/M4". | E01–E15 pass (E01–E02 against the bundled samples) |
| **M2** | **Read LeetCode, offline-first**: provider interface, Room store, native login plus Cloudflare fallback, problem list, problem pane, Comments pane, starter code, drafts moved into Room (migrating M1's draft files), save-on-open, Settings (including swipe speeds) and Onboarding | In airplane mode, a problem saved earlier opens with its statement and comments; drafts survive a force-stop |
| **M3** | **Run and submit on LeetCode**: Results pane (opens automatically), both `check` parsers, saved test cases from custom runs and failed submissions, offline Submit blocked | The full loop works on the device |
| **M4** | **Local runner and offline packs**: the `:runner` process with a warm spare, judge-matched environment, all result-checking modes, offline packs and sync | E16 passes after downloading a LeetCode study-plan pack; local runs finish in under 1 s |
| **M5** | **Polish**: autocomplete and snippets (`sym+Space`), themes, tuning the symbol palette and gestures | Daily-driver quality |
| **M6** | **More platforms**: NeetCode lists, then Codeforces with the stdin/stdout runner | Codeforces problems can be read, run and submitted |

---

## 13. Risks and open questions

- **Scroll Assistant events are screen touches.** If interception ever misses one, it taps or drags whatever is underneath. Mitigation: one interception point in `Activity.dispatchTouchEvent()`, covered by unit tests replaying Phase 0 recordings.
- **Unihertz firmware updates** could change the `touchPad` device name, keycodes 403 and 404, or the key map. The router logs unknown devices and keys, and the probe can be re-run in minutes.
- **The red button depends on a Unihertz setting** (PTT app). If a firmware update removes it, the fallback is to give "switch pane" one leader letter (`sym+P`).
- **Sora Editor and hardware keys.** Sora is built mostly for soft keyboards. If its own key or `InputConnection` handling fights the router in M1, the fallback is CodeMirror 6 in a WebView (§3). The router and `EditorActions` interfaces keep that swap contained.
- **Dependency versions.** The Compose BOM, Sora and the TextMate module must work with AGP 8.7.3 and Kotlin 2.1.0. If they don't, toolchain upgrades are done together and recorded in §11 and PROGRESS.md.
- **The symbol palette is touch-based.** Reaching for `=`, brackets and braces on the screen may feel slow. We'll judge it in M1 use; the owner can later give `=` a key binding (§5.3).
- **Cloudflare and private-API changes.** Mitigations are the WebView `fetch()` fallback, an isolated provider layer, caching and modest request rates. Phase 0 saw no challenge today.
- **Drafts are on the device only.** Losing or resetting the phone loses unsubmitted drafts. Submitted code is still kept on LeetCode. This is a deliberate decision by the owner.
- **Bulk downloads may trip rate limits.** Packs download slowly, for personal use only, and comments are capped per problem.
- **The environment may not match the judge exactly.** The Python version and pre-imported names are checked against LeetCode and re-checked periodically.
- **APK size.** Chaquopy plus `sortedcontainers` add roughly 20–40 MB, which is acceptable for a personal app.

**Open questions for the owner** (none block M1; the current list is in PROGRESS.md):
- Word-wise cursor movement and move-line-up/down have no binding since Alt/`sym`+swipe proved impossible (G06/G07). Do they need a leader letter?
- **Indent or dedent a selected block** has no binding: there's no Tab key and `sym+Space` only indents at the cursor. (Dedenting at the start of a line is covered by smart Backspace, §5.5, so E01/E02 don't depend on this.) Does it need a leader letter?
- After using the palette in M1: does `=` deserve a keyboard binding?
- K11 (per-letter Unihertz shortcuts) is unrun. It only matters if those shortcuts are enabled.

---

## 14. Next steps

The current status and the ordered task list are in **PROGRESS.md → Next up**. In short: start M1 (§12), beginning with the scripts and the `:app` module skeleton, then the `InputRouter`.

---

## Appendix A: How each feedback item was handled (`PLAN-FEEDBACK.md`)

| Feedback | Decision | Notes |
|---|---|---|
| 1.1 Native login only; keep the WebView; Cloudflare fallback | **Accepted** | §6.2. The plan never had OAuth; it's now stated explicitly. The fallback has three steps, and C01 tests it in Phase 0. |
| 1.2 One binding per action | **Accepted for keyboard shortcuts** | The owner clarified that the rule is meant to **limit the number of keyboard shortcuts to remember**, not to remove touch. Each action has at most one keyboard binding, and the touch tab bar plus tappable Run and Submit buttons stay as well. The Comments pane has no keyboard binding: it's reached by its tab. |
| 1.2 Red button = Problem↔Code; remove Peek; Results opens automatically with one leader key | **Accepted** | §4.1, §5.4. The fallback if the button can't be captured is `sym+P`. |
| 1.2 Remove the duplicates and extras | **Accepted** | All removed. Also removed: the scroll-mode/cursor-mode toggle (vertical swipe always moves by line), the heat-map log (K16 and E06 measure symbols instead), and the Settings keymap editor. |
| 1.3 `sym` = hold-to-use leader only; CODE layer deferred; native Alt | **Accepted** | §5.2–§5.4. |
| 1.3 Leader table | **Accepted, with additions** | Added `sym+N` (reset to starter, from §4 of the feedback), `sym+Space` (complete or indent, see 2.3) and the conditional `sym+P`. |
| 1.3 Remove the HJKL fallback | **Accepted** | §13. |
| 2.1 Rename "Automatic import header" | **Accepted** | Now "Pre-imported names". |
| 2.2 NeetCode lists in M2 | **Accepted** | Moved to M6. Offline packs and the M4 test now use a LeetCode study plan. |
| 2.3 `sym+Space` had three meanings | **Accepted** | It now means accept the highlighted popup item when a popup is open, and indent otherwise. Snippets are popup items, so completions and snippets can't conflict. The CODE-layer bindings `sym+Backspace` and `sym+Enter` are removed. |
| 2.4 Principle 1 depends on the CODE layer | **Accepted** | Restated as a goal measured in Phase 0. |
| 3 Pre-IME event order; `TYPE_NULL` means the router inserts characters | **Accepted** | §5.1. One refinement: Sora's own `onKeyDown` path may be reused instead of re-implemented, chosen in M1, as long as only one insertion path exists. |
| 3 Starter code has no `pass` | **Accepted** | §5.5, with golden-file tests. |
| 3 Separate runner process, warm spare, big stack | **Accepted** | §7. |
| 3 Match the judge (Python version, `sortedcontainers`, pre-imported names) | **Accepted** | §7. The names are confirmed by running a probe solution on LeetCode. |
| 3 Per-problem result-checking modes | **Accepted** | §7 table. Interactive problems need hidden test parameters, which still have to be confirmed for each problem. |
| 3 Hide problems without `python3`; store `questionId`; different `check` shapes | **Accepted** | §4.3, §6.1, §6.2. |
| 4 Testing strategy | **Accepted, extended** | §10. Added E15 (backlight) and E16 (offline). |
| 4 Settings and Onboarding in the diagram | **Accepted** | Comments was also missing from the diagram and is now added. |
| 4 Offline Submit: block or queue | **Block** | §8.1. The queue is removed from the plan and from `SyncWorker`. |
| 4 Reset to starter code | **Accepted** | `sym+N`, pressed twice. The old draft is kept in version history. |
| 4 leetcode.com only | **Accepted** | §6.2. |
| 4 Build and install | **Accepted: local only** | §11. The owner decided all builds and development happen locally on the Mac, with no GitHub Actions or remote CI. |
| 4 Drafts on the device only ("§1.4") | **Accepted** | Pack and draft export/import are removed. The feedback's "§1.4" reference doesn't exist in either plan version; it was read as this owner decision. |
| 5 Probe app and test plan | **Accepted, extended** | §9. Added the B01–B03 backlight tests and the C01 Cloudflare check. Reports are written through MediaStore on Android 16. |
| Header: "build the probe before other app code" | **Accepted as sequencing** | M0 / §14. It isn't started as part of this plan update. |

---

## Appendix B: Owner decisions on the Phase 0 results (revision 3)

| Finding | Decision | Where |
|---|---|---|
| 15 symbols missing from the keyboard (K16); no key combo or IME menu provides them | `` ` `` and `\` aren't needed. `$ % ^ &` come from Alt+Shift on their digit keys. The rest come from a touchscreen palette opened with `fn`. This replaces "no custom symbol layer yet" for these 13 symbols only. | §5.3 |
| The keyboard backlight can't be forced on (B01–B03) | **Dropped**: "more trouble than it's worth". The backlight is left to the system, and E15 now tests the screen layout instead. | §5.6 |
| Cursor Assistant needs a double-tap, then sends arrow keys in all four directions | Accepted as an extra input. Scroll Assistant stays primary because it's the only source of vertical movement and selection. | §5.6 |
| The status bar and camera cutout block the top line | Hide the status bar and use the top band beside the camera for the tab bar. | §4.1 |
| K04: Alt only works while held | Counted as PASS; it matches "no sticky modifiers". | §5.2 |
| L01: pointer capture is lost on app switch | Not applicable; pointer capture isn't used. | §5.6 |
| The red button can be set as a PTT key for a chosen app | Use **PTT app = Titan Code**. The app gets keycode 401 and toggles Problem/Code. `sym+P` is dropped from the leader table. | §4.1 |
| Cursor Assistant = arrow keys in all four directions (also in TYPE_NULL, when the editor has focus); any key press ends cursor mode | Accept arrow keys; the editor stays TYPE_NULL. Selection comes only from Shift + Scroll Assistant swipe. | §5.1, §5.6 |
| M1 review: Back key | Back closes any open overlay. Otherwise it's ignored in Code, and normal in the other panes. | §5.1 |
| M1 review: forward delete | Shift+Backspace, with repeat | §5.1 |
| M1 review: acceptance tests | A full guided acceptance screen, debug builds only, with automatic checks where measurable | §10 |
| M1 review: git | Stay on `master`; commit only when the owner asks | §11 |
| M1 review: defaults | Sample picker, WebView Problem pane, `sym+N` backup file, typing closes the palette and types the key, red button on Results/Comments → Code, "Coming in M3/M4" for Run/Submit, `sym+Space` indents only, Find and Go-to-line bars, saved font size, fixed indent and wrap until M2 | §4, §5, §12 |
| M1 step 2: swipe speed | Tuned to 70 px per character and 90 px per line; both become adjustable settings in M2 | §4.2, §5.6 |
| M1 step 2: keyboard surface | Swipes on the keys **move the cursor only**; scrolling is done by dragging on the touchscreen. (Tried both: a scroll-by-default mode with a tap-then-slide cursor mode was built and rejected, and the app can't see taps anyway.) | §4.1, §5.6 |
| M1 step 2: horizontal scrolling | Soft wrap **on by default**; `sym+W` toggles it and the choice is saved. Up/down moves by screen row when wrapping. (Supersedes "fixed wrap off until M2" from the M1 review defaults.) | §4.2, §5.4, §5.6 |
| M1 pre-start: auto-pairing in strings | No auto-pairing inside strings or comments; palette closers type over like typed ones | §5.3, §5.5 |
| M1 pre-start: keys on non-Code panes | Routed from `MainActivity.dispatchKeyEvent()`. Non-editing leader commands work; text and editing keys are ignored and never reach the WebView; no auto-switch to Code | §3, §5.1 |
| M1 pre-start: acceptance screen | A debug-only collapsible panel inside `MainActivity` over the real shell; E01/E02 reference shown in the Problem pane | §3, §10 |
| M1 pre-start: Back on non-Code panes | Results/Comments → Code; Problem → app to background (`moveTaskToBack`) | §5.1 |
| M1 pre-start: defaults | Repeat scope (no `sym` repeat); other Alt+Shift dropped and logged; typing replaces a selection; smart Enter for `()` `[]` `{}`; smart Backspace as line-start dedent; line paste above; Find case-insensitive with wrap; Go-to digits via Alt; typing undo grouping; Sora's own auto-indent and pairing off; font 8–24 sp; Problem pane 1:1 scroll; no online badge in M1; MagicPython grammar; E14 waits 2 s; E16 not applicable in M1 | §3, §4, §5, §10 |

---

### Sources
- [Unihertz: Titan 2 Elite product page](https://unihertz.com/products/titan-2-elite)
- [TechRadar: Titan 2 Elite review](https://techradar.com/computing/unihertz-titan-2-elite-business-phone-review)
- [Sora Editor](https://github.com/Rosemoe/sora-editor) · [Chaquopy](https://chaquo.com/chaquopy/) · [Codeforces API](https://codeforces.com/apiHelp)
- Android: [`View.dispatchKeyEventPreIme`](https://developer.android.com/reference/android/view/View#dispatchKeyEventPreIme(android.view.KeyEvent)) · [`WindowInsets.displayCutout`](https://developer.android.com/reference/android/view/WindowInsets#getDisplayCutout())
- Phase 0 evidence: `docs/device-input-report.md`, `reports/probe-20261101-200140/`, `reports/adb-20261101-201016/`
