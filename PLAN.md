# Titan Code: design and features

An Android app for practising coding problems in Python on the **Unihertz Titan 2 Elite**, built around its physical keyboard and touch-sensitive keyboard surface. You can browse, read, write, run and submit **LeetCode**, **NeetCode** (neetcode.io) and **Codeforces** problems, and almost all of it works offline. The launcher name is **Coding**; the package is `dev.titan.code`.

> This file describes **the app as it is built today**. History, decisions and the work log are in [`PROGRESS.md`](PROGRESS.md). What the phone's keyboard sends, measured in Phase 0, is in [`docs/device-input-report.md`](docs/device-input-report.md). Manual checks for the phone are in [`docs/test-checklist.md`](docs/test-checklist.md).

---

## 1. Goal and principles

**A full practice session (pick a problem, read it, write a solution, run it, submit) with all text editing on the physical keyboard.** The touchscreen handles navigation and the big actions: the pane tabs, Run and Submit, scrolling, and tapping rows, links and buttons.

1. **Every Python symbol is reachable.** Printed symbols come from the keyboard's Alt layer; `$ % ^ &` from Alt+Shift; the rest (`= [ ] { } < > | ; ~`) from a touch **symbol palette** opened with `fn` (§5.2). This palette is the one deliberate touch exception for typing.
2. **The keyboard surface is a trackpad for the cursor.** Swipes move the cursor; Shift + swipe selects (§6).
3. **No IME interference.** No autocorrect, auto-capitalisation, "double space → period" or candidate bar.
4. **One keyboard binding per action.** No alternate shortcuts, no sticky modifiers, no tap-versus-hold variants. Touch controls sit alongside and don't count.
5. **One full-screen pane at a time** (the 1080×1200 screen is nearly square). Switching panes never moves the cursor.
6. **Your work stays on the phone.** Drafts autosave per problem with a version history. There is no export or sync.
7. **Offline-first.** Every screen reads from the local store; the network only refreshes it. Only Submit and running on a site's judge need a connection.

---

## 2. The device

| Item | Value | What the app does with it |
|---|---|---|
| Display | 4.03" AMOLED, 1080×1200 (1076×1200 usable), 300 dpi, 120 Hz | Full-screen panes; 2- or 4-space indent |
| Camera cutout | 123×123 px square, top-left; 100 px corner radius | The status bar is hidden; the tab bar sits in the top band right of the camera; nothing interactive in a rounded corner |
| OS | Android 16 (`Titan 2 Elite_V02.00.04`) | minSdk 34, targetSdk 35, edge-to-edge with our own insets |
| Keyboard | QWERTY (`TitanKey`), Alt and `sym` in the third row, bottom row Shift · Home · Back · Space · Recents · `fn` · Shift. **No number row, no `,` `.` keys, no key repeat** | Digits and symbols from Alt (§5.2); `sym` is the leader key (§5.3); the app repeats keys itself |
| Keyboard surface | **Scroll Assistant** sends screen-coordinate touches from device `touchPad`; **Cursor Assistant** sends arrow keys | Scroll Assistant drives the cursor (§6); arrow keys are accepted too |
| Red side button | With *Settings → Shortcut keys → PTT key → PTT app = Coding* (this app), it reaches the app as **keycode 401** | Switches Problem ↔ Code |

**Phone settings the app needs** (the in-app Setup checklist walks through them): Scroll Assistant **on**, and the red button's PTT app set to this app. Cursor Assistant can be on or off.

The keyboard backlight can't be controlled by apps and is left to the system. Holding a modifier other than Shift stops the surface from sending anything, so there are no Alt+swipe or `sym`+swipe gestures.

---

## 3. Architecture

```
┌──────────────────────────── MainActivity (Compose, singleTask) ─────────────────────────────┐
│  Tab bar · title line · pane · message strip · palette/completion strip · status line        │
│  Panes: Problem (list ⇄ statement WebView) · Code (TitanEditor) · Results · Comments (WebView)│
│  Overlays: Settings · Setup checklist · cheat sheet · sign-in pages                          │
│                                                                                              │
│  Input:  TitanEditor / KeySinkView  ──pre-IME──►  InputRouter ──► EditorActions ─► PyEdit    │
│          dispatchTouchEvent (touchPad) ──────────► GestureEngine ─► cursor / scroll          │
│                                                                                              │
│  Data:   Repository ─► Room (StoreDb v5) + images on disk                                    │
│            ├─ LeetCodeApi + LeetCodeHttp (OkHttp, hidden-WebView fallback)                    │
│            ├─ NeetCodeApi + NeetCodeHttp (Firebase functions)                                 │
│            ├─ CodeforcesApi + CodeforcesWeb (public API + hidden WebView)                     │
│            └─ WorkManager: PackWorker (offline packs), RefreshWorker (weekly list)            │
│                                                                                              │
│  RunnerClient ──Messenger──► RunnerService in ":runner" (Chaquopy, Python 3.14)              │
│                              titan_runner · titan_drivers · titan_checkers · titan_limits ·  │
│                              titan_stress · sortedcontainers                                 │
│  CompletionClient ──Messenger──► CompletionService in ":complete" (Jedi 0.19.2, titan_complete)│
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 21; Jetpack Compose (Material 3); [Sora Editor](https://github.com/Rosemoe/sora-editor) with `language-textmate`; Room; WorkManager 2.10; OkHttp; jsoup; [Chaquopy](https://chaquo.com/chaquopy/) 17.2 with Python 3.14 (arm64 only). Versions are pinned in `gradle/libs.versions.toml`.

**Where input is caught:**

| Input | Where | Why |
|---|---|---|
| Keys on the Code pane | `TitanEditor.dispatchKeyEventPreIme()` → `InputRouter` | Before the IME, which otherwise eats `sym` |
| Keys on other panes | A focused, invisible `KeySinkView` → `InputRouter` | Without a focused view, `sym` opened the system input-method picker |
| Red button (401) | `MainActivity.dispatchKeyEvent()` | Works on every pane |
| Keyboard-surface touches (`touchPad`) | `MainActivity.dispatchTouchEvent()` → `GestureEngine`, always consumed | They're screen touches and must never reach a view |
| Keycode 404 (surface swipe marker) | Swallowed | No meaning |

**Editing has one path.** The editor reports `TYPE_NULL`, so no text arrives through the IME. The router produces each character from the key map and calls `EditorActions`; Sora's own auto-indent, pairing, key insertion and floating copy/paste bar are off. Python rules live in `PyEdit` (plain Kotlin over a `TextBuffer` interface, unit-tested), and each command is one Sora batch edit, which makes it one undo step.

**Problem keys.** Every problem has one string key: LeetCode's bare slug (`two-sum`), `nc:<neetcode id>` or `cf:<contest><index>`. The store's `provider` column holds the site, and `Platform` lists what each site can do (run on the site, comments, stdin programs).

**Bundled assets** (licences in `app/src/main/assets/LICENSES.md`): JetBrains Mono (OFL), the MagicPython TextMate grammar (MIT), KaTeX 0.16.11 (MIT, fonts OFL) for maths, Jedi 0.19.2 and parso 0.8.7 (MIT; typeshed's stdlib stubs, Apache 2.0) for completion, the project's own `titan-dark` theme, three sample problems (Two Sum, Group Anagrams, Valid Parentheses) so a new install works offline, the NeetCode catalog snapshot (939 problems) and the NeetCode 150 / Blind 75 list snapshot.

---

## 4. Screen and panes

```
┌──────────────────────────────────────────┐
│ ◉ │ Problem │[Code]│ Results │ Comments ⚙ │  tab bar beside the camera (◉)
│ LeetCode · 49. Group Anagrams · Medium   │  title line (+ "● save failed", "⦿ offline")
├──────────────────────────────────────────┤
│  1 class Solution:                       │
│  2     def groupAnagrams(self, strs):    │
│  …                                       │
├──────────────────────────────────────────┤
│ Ln 5 Col 31 · ⇧sel ⏱ 12:34 [sym] [▶ Run][Submit] │  status line
└──────────────────────────────────────────┘
```

- **Tab bar:** tap a pane; ⚙ opens Settings. The last result shows in the Results tab's label.
- **Title line:** site, number, title and difficulty. It only shows a draft state if a save **fails**, and `⦿ offline` only while offline.
- **Status line:** cursor position, `⇧sel` while selecting, the practice timer (§11), `[sym]` while `sym` is held, and the **▶ Run** and **Submit** buttons. ▶ Run runs locally (`sym+R`); a long press runs on the site (`sym+E`). Submit needs a second tap within 3 s ("Confirm?"), like pressing `sym+S` twice.
- **Strips above the status line:** messages, the `fn` symbol palette, and word completions.
- **The screen stays on** while the app is in front.

**Panes**

- **Problem** has two views. The **problem list** (§4.1), and the **statement**: a WebView with the app's dark CSS, local images, maths drawn by KaTeX, tags, limits, collapsible hints and similar problems. A "☰ Problems" bar above the statement (or `sym+P`) shows the list.
- **Code:** the editor (§5). Tapping places the cursor; dragging on the screen scrolls without moving it.
- **Results** (opens by itself after a run or submit; `sym+T` toggles it):
  - per case: pass/fail, input, your output, expected output, `print()` output, time, and tracebacks with **Go to line N**
  - verdicts from the site, or the Codeforces verdict with time, memory and a link
  - the problem's test cases with ✎ edit and ✕ delete, **＋ New test**, **＋ From clipboard**, and **⚡ Stress test** (§9.4)
  - submission history: verdict, tests passed, runtime and the practice time
- **Comments:**
  - LeetCode: **Solutions** (default) is a list of posts (title, author, votes, comment count, summary). Opening one shows its body and all its comments, 20 at a time with "Load more". **Discussion** shows the problem's threads with replies.
  - NeetCode: the **NeetCode solution** article (explanation and Python code per approach).
  - Codeforces: the **Editorial** (§7.3): the contest tutorial's part for this problem, loaded the first time Comments is shown. There's only ever one entry, so it opens straight away with no list; Back goes to Code.
  - Code blocks fold: a folded block shows 4 lines and "▸ N more lines". In comments, long blocks start folded; in posts, Python starts open and other languages folded. Unfenced code is detected. Images in posts aren't downloaded.
- **Settings** (⚙): see §12. **Setup checklist:** shown on first run and from Settings: Scroll Assistant, the red button's PTT app, optional LeetCode sign-in, and downloading the list.
- **Cheat sheet** (`sym+H`): generated from the key tables, so it always matches the real bindings.

**Switching panes and Back**

- The **red button** toggles Problem ↔ Code; from Results or Comments it goes to Code. Each pane keeps its scroll position.
- **Back** (key or screen-edge swipe) never leaves the app. In order: it closes an open overlay (cheat sheet, palette, completions, Settings); while editing a test case, it cancels the edit; in Code it does nothing; in Results or Comments it goes to Code, or from an open post back to the Solutions list; in Problem it switches between statement and list (on the list, back to the open problem).
- Leave the app with Home or Recents.

**Reading size.** The Problem and Comments panes have their own size, **70–200%** (steps 70, 80, 90, 100, 110, 120, 135, 150, 175, 200), saved between launches. `sym+I` / `sym+O` change it on those panes; Settings → Editor has a stepper. Statement, post and comment pages use WebView text zoom. Everything the panes draw themselves (site buttons, filter box, 🎲, ⟳, chips, list rows, the ☰ Problems bar, the Comments bar, the solution list) scales as a whole, keeping relative sizes. Chip rows wrap when they no longer fit. The tab bar, status line and Settings don't scale.

### 4.1 Problem list

- **Sites:** LeetCode / NeetCode / Codeforces buttons at the top; each keeps its own list.
  - **LeetCode:** the algorithms problems (3,682; 3,145 without Premium), 100 rows per request. The **Daily** problem sits above the list.
  - **NeetCode:** the bundled catalog of neetcode.io's 939 problems (788 without Pro).
  - **Codeforces:** the whole problemset from the public API in one request (~11,500), newest first; difficulty is the rating, bucketed for the chips (≤1200 Easy, 1300–1900 Medium, ≥2000 Hard).
- **Paid problems are hidden:** LeetCode Premium and NeetCode Pro never appear, and the count beside the filter is out of the problems you can open.
- **Type to filter:** words match the title in any order; a number matches problem numbers that start with it; `#word` matches a tag (`#hash` → Hash Table). Backspace edits the filter.
- **Chips:** Easy / Med / Hard, Todo / Tried / Solved, Saved, then lists: LeetCode has **All / NeetCode 150 / Blind 75** (mapped to LeetCode problems); NeetCode has **All / NeetCode 150 / Blind 75 / NeetCode 250 / By pattern**. A list shows in its own order with pattern headers, and the other filters still apply.
- **Rows:** number, title, marks (✎ draft, ⤓ saved offline, ✓ solved, ◐ attempted) and difficulty.
- **🎲 Random** opens a random problem from exactly what the list shows (site, chips and typed filter), preferring ones you haven't solved. **⟳** refreshes the list.
- **Moving:** a vertical swipe on the keyboard surface moves the highlight (finger down moves it down), or arrow keys; Enter opens. Tapping a row works too.
- **Refreshing:** the LeetCode list refreshes itself weekly and the daily problem daily when online; ⟳ or Settings refreshes on demand.

---

## 5. The editor

### 5.1 Presentation

- AMOLED-black theme with high-contrast syntax colours (TextMate Python grammar), JetBrains Mono, default **12 sp**, `sym+I` / `sym+O` in 1 sp steps from 8 to 24, saved.
- **Soft wrap on by default**; `sym+W` toggles it (saved). Continuation rows start at the left edge; line numbers appear on a line's first row only. With wrap on, up/down moves by screen row.
- **Indent width** 4 (default) or 2; LeetCode's 4-space starter is re-indented.
- **Indent guides:** a thin line at each indent level inside leading whitespace (first screen row only), continued across blank lines; the cursor's block is brighter.
- **Bracket pairs:** with the cursor beside a bracket, it and its partner are boxed. Current-line highlight.
- **Cursor trail:** when the cursor moves without an edit, a short streak runs from the old position to the new one and fades in about 260 ms.

### 5.2 Keys and symbols

| Layer | How | Gives |
|---|---|---|
| Base | Plain keys | Letters, Space, Enter, Backspace |
| Shift | Hold | Capitals; selection while swiping |
| Alt | Hold (no sticky Alt) | What's printed on the keycaps: `0–9` and `, . ? ! @ # ' " : _ - + * / ( )` |
| Alt+Shift | Hold both, any order | `$ % ^ &` on S / D / F / Z (Shift+4/5/6/7 on a normal keyboard) |
| Leader | Hold `sym`, press a letter | Commands (§5.3) |
| Palette | Tap `fn` | A strip of `= ( ) [ ] { } < > \| ; ~ %`; tap one to insert it (`^ &` are on Alt+Shift) |

- The palette inserts through the same path as typing, so pairing and type-over apply. `fn` or Back closes it; typing any other key closes it and types the key.
- **Shift+Backspace** deletes forward. With a selection, Backspace and Shift+Backspace delete it.
- **Key repeat** (the keyboard has none): Backspace, Shift+Backspace and characters repeat after a 400 ms hold, every 40 ms. `sym` chords never repeat.
- **Arrow keys** (Cursor Assistant or any source) move by character and line, keeping the column; Shift extends the selection.
- Alt+Space (a private-use character) and other Alt+Shift combinations insert nothing. Press `sym` before Shift: Shift then `sym` opens the system keyboard picker.

### 5.3 Leader commands (hold `sym`)

| `sym` + | Action | Panes |
|---|---|---|
| **R** | Run locally | All |
| **E** | Run on the site's judge (LeetCode, NeetCode) | All |
| **S** | Submit; press again to confirm. While editing a test case: save it | All |
| **T** | Open or close Results | All |
| **P** | Problem list; again for the statement | All |
| **H** | Cheat sheet | All |
| **I** / **O** | Text size up / down: editor font on Code, reading size on Problem and Comments | All |
| **U** / **Y** | Undo / redo | Code |
| **C** / **X** / **V** | Copy / cut / paste. With nothing selected, copy and cut take the line, which pastes as a whole line above the current one | Code |
| **L** | Select line (repeat to extend) | Code |
| **D** | Delete line | Code |
| **J** | Duplicate line | Code |
| **K** | Toggle comment | Code |
| **W** | Toggle soft wrap | Code |
| **N** | Reset to starter code; press again within 3 s to confirm (the old draft goes into the history) | Code |
| **Space** | Accept the first completion if the strip is open; otherwise indent to the next stop, or indent every selected line | Code |

On other panes, editing commands, characters and Enter are ignored and never reach the WebView. Clipboard text copied in another app pastes as plain text.

### 5.4 Python-aware editing

- **Auto-pairing** of `( [ { ' "` with type-over of closers, and Backspace on an empty pair deletes both.
  - Not inside a string or comment (judged by a scanner of the current line; multi-line triple-quoted strings aren't detected).
  - Openers pair only before whitespace, end of line or a closer; quotes don't pair after a letter or digit except a string prefix (`f'`, `rb'`).
  - Typing over a selection replaces it.
- **Smart Enter:** copies the indent; indents after `:` or an unclosed opener; between `()`, `[]` or `{}` opens a block; dedents after `return`, `break`, `continue`, `pass` or `raise`; clears a whitespace-only line.
- **Smart Backspace:** in leading whitespace, deletes back to the previous indent stop (the way to dedent).
- **Undo** groups a run of typing (broken by a cursor move, a command or a ~1 s pause); each command is one step.
- **Completion (type-aware, rev 38):** after an edit, when the word before the cursor (in code) has 2+ characters, or right after a `.`, a strip of suggestions appears. They come from **Jedi** (the analysis engine behind Python language servers), run in its own `:complete` process on the whole buffer with LeetCode's pre-imports in front (`titan_prelude.py`). Jedi infers types from hints, assignments and the standard library's stubs, so `nums: List[int]` then `nums.` offers `append`, `pop`, `sort`…; `deque`, `Counter`, `SortedList`, `str` and `ListNode`/`TreeNode` (`head.next.`) work the same way. Names defined in the solution come first, then the rest, shortest and A–Z (after a `.`, A–Z); private and dunder names only when the prefix starts with `_`; methods show a dim `()`. Accepting a function or class adds the brackets (rev 39): `append(|)` with the cursor inside, or `clear()|` after when the call takes no arguments (Jedi reports the count; if it's slow to say, inside). No brackets where a bare name is meant: a `(` already follows, decorators, `def`/`class`/`import` lines, `key=len`, `map(int, …)`/`filter(…)`, and classes in type hints (`: `, `->`, inside `[…]`), `except` and `isinstance`. A closing `)` types over the inserted one. For a plain name, buffer words Jedi didn't offer follow. While Jedi works, the last list narrows to the new prefix; if it hasn't answered in 0.7 s (starting, about 2 s after launch) the old word list shows (buffer words, then keywords, builtins and pre-imported names), and nothing after a `.`. A test case being edited (JSON) gets the word list only. `sym+Space` takes the first; tap takes any; the strip scrolls; moving off the word or Back closes it.
- **Starter code:** the site's Python snippet (Codeforces: a stdin template), with the cursor on the first method's empty body.

### 5.5 Drafts

- One draft per problem, saved in Room 1 s after typing stops and when the app goes to the background. Saving is silent.
- **Draft history:** a version is kept on every reset (`sym+N`), before a restore, and every 5 minutes of editing (the last 20 per problem). Settings → Draft history restores one as a single undoable edit.
- Drafts and their history are never removed by clearing storage or removing packs.

---

## 6. The keyboard surface

| Gesture | Code pane |
|---|---|
| Horizontal swipe | Move by character; stops at the start or end of the line (crosses wrapped rows within it) |
| Vertical swipe | Move by line (screen row when wrapping), keeping the column; the view follows |
| Shift + swipe | Extend the selection |
| Long fast slide (the system's backlight gesture) | Ignored: over 75% of the width at over 2000 px/s, and any movement it made is undone |

- **On other panes:** a vertical swipe scrolls the statement, comments or Results 1:1, or moves the highlight in the problem and solution lists.
- **Speeds** (Settings → Keyboard surface): **70 px per character** and **90 px per line** by default, both adjustable. Strokes lock to one axis after 24 px of travel.
- **Speed-up for fast swipes:** Off / Gentle (2×) / Normal (3.5×). Speed is measured over the last 80 ms; below 700 px/s there's none; the first 100 ms of a stroke is never sped up.
- **Vibration per step:** each character or line moved plays a short pulse, longer when moving slowly and shorter when fast, plus a longer one at the line edge. **Normal** 6–16 ms (edge 22), **Strong** 9–40 ms (edge 50), or **Off**. The system "touch feedback" switch also silences it.
- **Typing doesn't move the cursor:** a key pressed while a finger is on the surface ends that stroke and undoes its last 300 ms of movement; strokes starting within 150 ms of a key are ignored (Shift and arrow keys excepted). Short stray strokes from typing that end in a cancel are dropped.
- Cursor Assistant's arrow keys are accepted; with Scroll Assistant on, its slides arrive as swipes anyway.

---

## 7. Sites

### 7.1 LeetCode (leetcode.com only)

| Feature | Endpoint |
|---|---|
| List | GraphQL `problemsetQuestionList` (algorithms category, 100 per request) |
| Problem | GraphQL `question(titleSlug)`: content, snippets, examples, `metaData`, tags, hints, similar |
| Daily | GraphQL `activeDailyCodingChallengeQuestion` |
| Discussion and solutions | GraphQL discussion, comment, reply, solution-list and solution queries |
| Study plans | GraphQL study plan (for packs) |
| Run | `POST /problems/{slug}/interpret_solution/` → poll `/submissions/detail/{id}/check/` |
| Submit | `POST /problems/{slug}/submit/` → poll the same `check` |

- **Sign-in:** LeetCode's own username and password page in an in-app WebView (no social sign-in). The cookies are kept encrypted with an AES-256-GCM Keystore key; their values are never logged. Reading works without signing in; Run, Submit and solved marks need it.
- **Transport:** GraphQL goes over OkHttp with the WebView's user agent, cookies and CSRF header, spaced ≥250 ms apart (300 ms in background downloads). On a Cloudflare challenge it's re-sent from a hidden, signed-in WebView with `fetch()`, and after two challenges that path is used for the session. **Run, Submit and `check` always go through the hidden WebView** (Cloudflare challenges non-browser clients there). If the WebView is challenged too, the message points to signing in again.

### 7.2 NeetCode (neetcode.io)

- **List:** the bundled catalog snapshot (title, pattern, difficulty, LeetCode number, list membership, Pro flag).
- **Problem:** `getProblemMetadataFunctionHttp`: Markdown statement with images, starter, examples, hints, and the solution article, which becomes the Comments pane's post. NeetCode has no comment threads.
- **Run / Submit:** `runCodeFunctionHttp` / `executeCodeFunctionHttp` with a Firebase ID token.
- **Sign-in:** NeetCode's page with Google or GitHub. The Firebase refresh token is read from the page and kept encrypted; ID tokens are minted from it.
- **Local runs:** NeetCode has no `metaData`, so it's built from the starter's type hints (`List[int]` → `integer[]`). Inputs are stored one value per line and converted to NeetCode's `name=value` form when sent. Extra inputs the function doesn't take (`index` for Linked List Cycle) are kept by name. Saved problems from an older parser are re-downloaded when opened online.

### 7.3 Codeforces

- **List and verdicts:** the public API (`problemset.problems`, `user.status`).
- **Statements:** loaded in a hidden WebView (Cloudflare) and parsed with jsoup: limits, samples, tags, rating, and maths kept as TeX (rev 36). **Maths display:** each formula is saved as `<span class="math" data-tex="TeX">text</span>` (a `<div>` for display maths) and drawn by the bundled KaTeX (`assets/katex/`, `titan-math.js`) when the page is shown, offline. The text (`1≤t≤10⁴` with real sub- and superscripts, sizing commands like `\bigl` dropped, ~200 commands as symbols) shows if KaTeX can't draw a formula. The reading WebViews run JavaScript for this only: a content-security policy allows scripts from the app's own `file:` assets, with no inline scripts or handlers, and site HTML has its scripts removed. Statements with maths saved before rev 36 kept only the text; they're downloaded again the first time they're opened online in a session (`metaData.parser` 2), and the saved copy stays if that fails. The page's MathJax has usually drawn the maths by then; each drawn formula is turned back into its TeX source, or into text from its MathML copy. Statements saved before this was fixed are repaired when opened.
- **Sign-in:** Codeforces' own `/enter` form in a WebView; the handle is read from the page.
- **Submit:** the hidden WebView fills the site's own submit form (problem, language, source); the verdict is polled every 2 s for up to 3 minutes. Results show Codeforces' wording ("Wrong answer on test 3"), time, memory and a link; the API doesn't give the failing test.
- **Language** (Settings): PyPy 3 64-bit (default), PyPy 3 or Python 3.
- **Running:** locally only, as a stdin/stdout program on the samples, under the problem's own time and memory limits (§9). There's no run on Codeforces' judge, and no comment threads.
- **Editorial:** the tutorial blog entry linked from the contest's "Contest materials" (saved from the statement page, or read from `/contest/<id>` for statements saved earlier), loaded in the hidden WebView. The page's script fills each problem's "Tutorial is loading..." box; if it hasn't after 8 s the app asks for them the same way (`/data/problemTutorial`). The part for this problem runs from its heading (a link to the problem, its title, or a heading like `B. Title`) to the next problem's, so the same problem in the other division is found by title. Spoilers become sections that start **open** (a tap folds one), code is shown **in full** in every language, maths becomes text, images show as "[image]". If the part can't be found, the whole editorial is shown; if no tutorial is linked, "No editorial found" (⟳ looks again). One editorial page saves the part for every problem of that contest in the list. The part is saved as Codeforces' own HTML and formatted when it's shown, so a formatting fault shows it unformatted with a note instead of failing, and formatting fixes reach saved editorials without a download; each formatting step and each formula is handled on its own. A page that loads but can't be read tries the next tutorial link, then offers the browser. Every editorial ends with **Open the editorial in the browser**. Code: `CodeforcesEditorial`, `CodeforcesWeb.editorialPage`, `Repository.fetchEditorial`.

---

## 8. Running, submitting and test cases

- **`sym+R` / ▶ Run** runs the examples and saved cases **locally** (§9).
- **`sym+E` / long-press ▶ Run** runs them on the site's judge (LeetCode, NeetCode). Expected outputs that come back are saved with the cases (verified).
- **`sym+S` twice / Submit, Confirm?** submits to the site. Accepted marks the problem ✓ at once. Every submission is stored with its code, verdict, runtime and practice time.
- **Offline**, `sym+E` and Submit show *"Offline — your draft is saved. Submit when you're back online."* Nothing is queued.

**Test cases**

- **Examples** from the statement, with expected outputs from the statement's "Output:" lines until a site run supplies the official ones.
- **Failed submissions** save the failing input (and its expected output for Wrong Answer).
- **Your own cases:**
  - **＋ New test** or **✎** opens the case in the Code pane as one commented section per input (`# nums (integer[])`), then an optional expected output, prefilled from the first example. The palette, swipes, undo and completion all work. `sym+S` checks each value as strict JSON (unquoted strings are caught) and saves; an error puts the cursor on its line. Back cancels. Your draft is saved first and put back afterwards. Editing an example or a submission's case saves a copy as yours.
  - **＋ From clipboard** adds the copied input.
  - **＋ Save as a test case** from a stress-test difference (§9.4).
  - A case without an expected output still runs and shows its output; a checker (§9.3) judges it where one exists. ✕ deletes one of yours.
- Hidden tests are never available.

---

## 9. Local runner

Runs Python offline, usually in well under a second.

- **Process:** `RunnerService` in a separate **`:runner`** process (Chaquopy, Python 3.14), connected over Messenger. It's started and warmed (Python up, harness imported) when the app opens, and each run gets a fresh namespace. If it has to be killed, it restarts and the editor is never affected.
- **Environment:** a preamble of LeetCode's pre-imported names; `sortedcontainers` 2.4.0 bundled; solutions run on a thread with a **256 MB stack** and a recursion limit of 200,000.
- **The harness** (`titan_runner.py`) builds the call from `metaData` (function, parameter types, return type), converts `ListNode` and `TreeNode` to and from LeetCode's list notation, runs each case, and captures `print()` output and tracebacks trimmed to your code (with the source line of each frame).

| Checking mode | Used for |
|---|---|
| Exact | Most problems |
| Any order | Statements saying "in any order", plus 3Sum, 4Sum, Generate Parentheses, Combination Sum II, Word Search II, Majority Element II, Pacific Atlantic, Find All Duplicates / Disappeared Numbers |
| Float tolerance | Answers within 1e-5 |
| In place | `void` methods: the modified first argument (`sortColors`, `rotate`) |
| k first | Return k and check the first k elements (`removeDuplicates`, `removeElement`) |
| Design | Operation and argument lists (`LRUCache`); constructor and method arguments typed `TreeNode`, `ListNode` or `Iterator` are built from their types |
| Unchecked | Problems with random answers (380, 381, 382, 384, 398, 478, 497, 519, 528, 710): they run and show the output |
| stdin/stdout | Codeforces: the whole program per sample with input on stdin (`input`, `sys.stdin`, `open(0)`, `exit()` all work); output compared token by token, decimals within 1e-6, YES/NO in any case |

### 9.1 Special types

LeetCode judges these with its own code, and NeetCode's versions are the same problems, so `titan_drivers.py` picks a **driver by LeetCode number** (NeetCode's number is the LeetCode number). A driver builds the inputs, supplies the judge's classes (your own class of the same name wins), calls the solution, shows the answer as the site does, and can fail a case with a **Note** saying why.

| Kind | Problems | What's checked |
|---|---|---|
| List with a cycle | 141, 142 | The cycle is built from `pos`; 142 reports the returned node's index |
| Intersection | 160 | Must return **the shared node**, not an equal value |
| Graph `Node` | 133 | The copy's adjacency list; fails if a node is from the original |
| Random-pointer `Node` | 138 | The copy in `[[val, randomIndex]]` form; fails if it reuses original nodes |
| `next` pointers | 116, 117 | `[1,#,2,3,#,…]`, walking `next` along each level |
| N-ary tree | 429, 559, 589, 590 | The return value |
| `NestedInteger` | 341, 385 | 341 drives `NestedIterator`; 385 prints the nested result |
| Interactive APIs | 278, 374, 1095 | The answer and a Note with the number of API calls; 1095 fails past 100 `get` calls |
| Tree-node arguments | 235, 236, 863, 1379 | The returned node; 1379 must return the node from the cloned tree |
| Delete / flatten | 237, 430 | The list from the head; 430 also checks `prev` links and cleared `child` |
| Round trips | 297, 449, 271 | The decoded result; fails if the encoded form isn't a `str` |
| Other | 690 (`Employee`), 470 (`rand7`) | 470: each result in 1..10, unchecked |

### 9.2 Limits

- **Time:** **3 s per case** on LeetCode and NeetCode; a Codeforces problem uses **its own "time limit per test"** from the statement (1 s, 2.5 s…; 3 s if it can't be read). The phone runs CPython while Codeforces submissions default to PyPy, which is often several times faster, so a local Time Limit Exceeded near the limit may still pass on the site. A watchdog raises `TimeLimitExceeded` inside the running solution, so the case shows **Time Limit Exceeded** at the line it was on (Go to line works), keeps its output so far, and the other cases still run. The limit exceptions derive from `BaseException`, so `except Exception:` can't swallow them.
- **Memory:** a case that grows the runner by more than **512 MB** (a Codeforces problem: its own "memory limit per test") stops with **Memory Limit Exceeded** at its line.
- **Output:** a case keeps the first 20,000 characters of `print()` output; more than 1,000,000 stops it with **Output Limit Exceeded**.
- **Calls that can't be interrupted** (`time.sleep(60)`, one huge built-in operation): the runner gives up or is killed (the app waits each case's limit plus 5 s, between 10 and 120 s in all) and restarts. A progress file written after each case brings back the cases that had finished.
- A stopped case gives the run LeetCode's verdict (Time / Memory / Output Limit Exceeded).

### 9.3 Checkers

Where several answers are right, a **checker** (`titan_checkers.py`) validates your answer from the inputs instead of comparing it with the example, so it also judges your own cases. Built for: 5, 210, 269, 767, 1054, 451, 905, 922, 324, 1405, 984, 1968, 1980, 450, 701, 1382, 108, 109, 1092, and (for stress tests) 1, 167, 347, 973, 134.

### 9.4 Stress tests

**⚡ Stress test** in Results compares your code with a **reference solution** on generated inputs, offline (`titan_stress.py`).

- **References:** NeetCode's solution article, one per approach (a LeetCode problem NeetCode also has uses NeetCode's article), then saved LeetCode solution posts with Python code. References that fail the known examples are dropped. Open the problem's Comments once while online so one is saved.
- **Inputs** come from the parameter types and the statement's **Constraints**:
  - lengths and value ranges (`1 <= nums.length <= 10^4`, `n == grid[i].length`)
  - character sets ("lowercase English letters", `'0'` or `'1'`)
  - sorted, distinct, rotated
  - tree and list sizes, BSTs
  - pairs and edges (`a_i != b_i`, unique pairs)
  - squares, sums of lengths, no leading zeros

  Ten problems with structure the constraints can't express have their own generators (287, 105, 106, 684, 45, 57, 136, 137, 260, 150).
- **Search:** up to ~600 inputs in ~6 s, starting tiny so the first difference is easy to read, then **one full-size input** timing both solutions ("Yours: 2,883 ms · reference: 2 ms").
- **Result:** "Different answer" (input, your output or error, the reference's, a Note, and ＋ Save as a test case), "Too slow", or "No differences found", plus any warnings and the assumptions the generator made.
- **Coverage:** over the NeetCode 150, 126 problems run with no false differences. Design problems, the special types (§9.1) and Codeforces aren't stress-tested.

---

## 10. Offline

**What's saved for each problem**

| Data | Notes |
|---|---|
| Statement | Cleaned HTML with images downloaded and pointed at local files |
| Details | Title, difficulty, tags, hints, limits, `metaData`, ids, similar problems, starter code |
| Discussion | LeetCode, opened online: the top 30 threads with replies for the best 10, and 20 solution titles with the top 3 bodies; a post's comments are saved when you open it. NeetCode: the solution article. Codeforces: the editorial's part (§7.3), saved when Comments is shown online or by a pack |
| Test cases | §8 |
| Your work | Drafts and their history, submissions, practice timer |

**How it gets saved**

- **Save on open:** opening a problem online saves it, and the next *K* problems in the list (Settings, default 5, 0–20; not on Codeforces).
- **Offline packs** (Settings → Offline packs): a LeetCode **study plan** (Top Interview 150, LeetCode 75, Top 100 Liked, Binary Search, Dynamic Programming), a **curated list** (NeetCode 150, Blind 75…), or **the list as filtered now**. Each problem saves its statement, 20 threads with replies for 3, and 20 solution titles with 3 bodies.
- **Everything on LeetCode / NeetCode / Codeforces:** refreshes the list, then saves every problem that isn't Premium or Pro. Pressed again (**Update**), it adds problems published since and retries any that failed. Estimates are shown (LeetCode ~3,100 problems, a few hours; NeetCode ~790, minutes; Codeforces ~11,500 with editorials, about 16 hours).
- **How downloads run** (WorkManager):
  - Wi-Fi only unless Settings allows mobile data; they need storage not low.
  - Every statement first, then discussions.
  - Already-saved problems are skipped, so an interrupted download resumes where it stopped.
  - They run as a foreground job with a "Saving …" notification showing progress and a **Stop** button. Android allows about 6 hours a day of this; past that the download pauses and continues later.
  - One problem that won't load is skipped and counted. "Too many requests" waits 30 s, then 1, 2, 4 and 5 minutes. A browser check, a lost session or a run of network failures pauses the download, and it retries by itself.
  - The pack row shows progress: `statements 56/788, 2 skipped`, `paused; resumes by itself`, `waiting for Wi-Fi`.
  - Pacing: 300 ms between LeetCode requests, 0.4 s per NeetCode problem, 1.5 s per Codeforces page.
- **Removing a pack** deletes its saved statements and discussions unless another pack needs them; an Everything pack's **Stop** keeps what it saved.
- **Settings → Problems and offline storage** shows what's saved and its size, and **Clear** removes saved statements, discussions and images. Drafts and history are never removed.
- **Background refresh:** the LeetCode list weekly, through WorkManager.

---

## 11. Practice timer

"⏱ 12:34" in the status line, per problem. It starts on your first edit, counts only while the app is in front with that problem open, and stops with ✓ when a submission is accepted. Each submission records its reading (shown in Results' history). Settings → Practice turns it off, or resets the open problem's timer.

---

## 12. Settings

| Section | Contents |
|---|---|
| Accounts | Sign in or out of LeetCode, NeetCode and Codeforces; Codeforces submit language |
| Problems and offline storage | List size and age with Refresh; saved statements, discussions and size with Clear; save the next *K* problems on open |
| Offline packs | Everything on each site (Download / Update); packs with progress and Remove (Stop for Everything); study plans and curated lists to add; the filtered list to add; mobile data allowed or Wi-Fi only |
| Editor | Indent width 2 / 4; editor font size; Problem and Comments text size; soft wrap |
| Keyboard surface | px per character; speed-up Off / Gentle / Normal; vibration Off / Normal / Strong; px per line |
| Practice | Timer on/off; reset this problem's timer |
| Draft history | The open problem's saved versions, each with Restore |
| Setup | Reopen the phone settings checklist |

Back closes Settings.

---

## 13. Testing and debugging

- **Kotlin unit tests** (`./gradlew :app:testDebugUnitTest`) run on the desktop JVM, whose regex engine is more lenient than the phone's (ICU); `RegexCompatTest` scans every `Regex(…)` in the app for what ICU rejects (braces inside `[...]`, stray braces, unknown inline flags). They cover: input routing and the leader table (no key has two commands), the gesture engine against Phase 0 recordings, swipe haptics, `PyEdit` rules, starter-code placement against real snippets, completion, drafts, the problem filter and list, the API parsers for all three sites (with saved fixtures), the judge's response parsers, local-judge requests and results, the test-case buffer, the practice timer, and pack-download retries.
- **Harness tests** (`scripts/test-python.sh`, on the Mac's Python): drivers, limits, checkers, stress generation and stdin mode.
- **On the phone:** `scripts/debug-run.sh request.json` runs one harness request on the phone's Python without touching the editor or any draft (a receiver that exists only in debug builds).
- **Manual checks** on the phone: [`docs/test-checklist.md`](docs/test-checklist.md), grouped by feature.

---

## 14. Build and development

Everything builds locally on the owner's Mac; there's no remote CI.

- **Toolchain:** Temurin JDK 21 (`scripts/env.sh` picks a JDK 17–23; Android Studio's bundled JDK 25 is too new for Gradle 8.11), the Android SDK at `~/Library/Android/sdk`.
- **Scripts:** `scripts/doctor.sh` checks the setup; `scripts/install.sh [app|probe]` builds, installs (`adb install -r -t`, so drafts survive) and launches; `scripts/logcat.sh [app|probe]` follows the `TitanCode` or `TitanProbe` log; `scripts/test-python.sh` runs the harness tests; `scripts/debug-run.sh` runs a harness request on the phone; `scripts/adb-checks.sh` and `scripts/pull-report.sh` are the probe's Phase 0 tools.
- **Signing:** the Mac's standard debug keystore, so each build installs over the last.
- **Git:** work on `master`; commit and push only when the owner asks. `build-logs/` and `reports/` stay local.
- **The Key & Gesture Probe** (`probe/`, `dev.titan.probe`) is the Phase 0 diagnostic app that recorded what the keyboard sends; re-run it after Unihertz firmware updates (`docs/PROBE.md`).

**Working on the phone:** check it's unlocked first; screenshots with `adb exec-out screencap -p`; record logs in the background (`adb logcat -v time 'TitanCode:*' 'AndroidRuntime:E' '*:S'`, filter specs quoted in zsh); read Unihertz settings with `adb shell settings list …` but change them only with the owner's OK. Never type into the owner's drafts.

---

## 15. Known limitations

- **Unconfirmed parsers:** LeetCode's and NeetCode's Run/Submit response parsers and the Codeforces submit form follow the documented shapes but still need a signed-in run to confirm (owner test queue).
- **Codeforces:** no run on its judge, no comment threads, contests or gym problems. Editorials are cut out by heading, so an unusual layout shows the whole editorial; a tutorial that's only an external link or PDF isn't shown.
- **Local runner:** Quad Tree (427, 558) and concurrency problems aren't checked; the pre-imported names haven't been confirmed against LeetCode with a probe run.
- **Comments:** images in posts aren't downloaded (shown as "[image]").
- **Editing:** no word-wise movement, block dedent or move-line bindings; multi-line triple-quoted strings aren't recognised for pairing.
- **Fragility:** a firmware update could change the `touchPad` device name, keycodes 401/403/404 or the key map (the probe re-checks in minutes); the sites' private APIs and Cloudflare can change.
- **Drafts live only on the phone:** losing or resetting it loses unsubmitted work.

---

### Sources
- [Unihertz Titan 2 Elite](https://unihertz.com/products/titan-2-elite) · [Sora Editor](https://github.com/Rosemoe/sora-editor) · [Chaquopy](https://chaquo.com/chaquopy/) · [Codeforces API](https://codeforces.com/apiHelp) · [jsoup](https://jsoup.org/) · [NeetCode practice](https://neetcode.io/practice)
- Phase 0 evidence: `docs/device-input-report.md`
