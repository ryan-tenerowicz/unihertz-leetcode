# M1 test plan: critical user journeys

> **Since M2:** the 3-button sample picker is replaced by the problem list (`sym+P`), and Back on the list returns to the statement. See [`m2-test-plan.md`](m2-test-plan.md).

Use this on the phone to check the M1 build. Every journey has steps and the expected result. Note anything that's off: the CUJ number and what happened is enough.

**Before you start**
- Build and install: `scripts/install.sh app`. Optionally, record the log with `scripts/logcat.sh app`.
- Phone settings: Scroll Assistant **on**; red button → PTT app = **Titan Code**.
- Sample drafts are kept between installs. To start a sample fresh, use `sym+N` twice (CUJ 6).

The **acceptance panel** is the dark strip above the status line (debug builds only). Tap it to expand it. It walks through E01–E16 with automatic checks, and **Pass / Fail / Unclear** write `Download/titan-code-acceptance/`. Afterwards, `scripts/pull-report.sh app` copies the report to the Mac. The CUJs below cover the same ground in the order you'd use the app. The panel is the formal run.

---

## CUJ 1: Open a problem and read it

1. Launch the app. It opens on **Code** with Two Sum, or with the last sample you used.
2. Press the **red button**.
   - Expected: the **Problem** pane, with a 3-button sample picker on top and the statement below in dark styling.
3. **Swipe up and down on the keyboard keys.**
   - Expected: the statement scrolls 1:1 with your finger, with no inertia. Sideways swipes do nothing.
   - The direction is drag-style (finger up moves the content up). Tell me if it should be the other way.
4. Drag the statement on the touchscreen.
   - Expected: it scrolls normally.
5. Type some letters, press Backspace and Enter, and hold `sym` and press U.
   - Expected: nothing happens, and switching to Code shows the code unchanged.
6. Press the red button again.
   - Expected: back to Code, with the cursor and scroll where you left them. Press it a few more times: the Problem scroll position is kept too.

## CUJ 2: Switch between sample problems

1. On Problem, tap **49. Group Anagrams**.
   - Expected: the statement changes, the title line reads "49. Group Anagrams · Medium", and a message says "Opened …".
2. Go to Code.
   - Expected: that sample's draft, or its starter code with the cursor on the indented empty line under `def`.
3. Type something, switch back to Two Sum, then back to Group Anagrams.
   - Expected: each sample keeps its own text and cursor position.

## CUJ 3: Write a solution with smart indentation (no space-bar indenting)

Type Two Sum from the starter. The reference is in `app/src/main/assets/samples/two-sum/reference.py`, or start E01 on the panel to show it in the Problem pane.

1. On the empty body line (already at 8 spaces), type `seen = {` and then `}`.
   - Expected: `{` inserts `{}`, and typing `}` steps over the closer instead of adding a second one.
2. Press **Enter**.
   - Expected: the new line starts at 8 spaces (indent copied).
3. Type `for i, n in enumerate(nums):` and press **Enter**.
   - Expected: 12 spaces (one level deeper after `:`).
4. Type `if target - n in seen:` and press **Enter**.
   - Expected: 16 spaces.
5. Type `return [seen[target - n], i]` and press **Enter**.
   - Expected: back to 12 (dedent after `return`).
6. Type `seen[n] = i` and press **Enter** (12), then press **Backspace once**.
   - Expected: it jumps back to 8 (a whole indent level).
7. Type `return []`.
   - Expected: the buffer matches the reference exactly. You never pressed space to indent.
8. More checks:
   - Typing `''.join` gives exactly `''.join`.
   - Inside a string, `'(['` stays as typed, with no extra closers.
   - In a `# comment`, `(` doesn't pair.
   - `(` typed directly before a word doesn't pair.
   - Backspace between an empty `()` or `''` deletes both.
   - Typing `x = [`, then Enter between the brackets, opens a block: the `]` moves to its own line and the cursor is indented between.
   - `sym+Space` adds spaces to the next indent stop.

## CUJ 4: Symbols

1. **Alt + key:** `( ) : ' " # + - * / ! @ , . ? _` and digits 0–9.
   - Expected: each takes exactly 2 keystrokes.
2. **Alt+Shift+S / D / F / Z:**
   - Expected: `$` `%` `^` `&`.
   - Alt+Shift with any other letter types nothing.
3. **`fn`:** opens a symbol strip above the status line: `= [ ] { } < > | ; ~ % ^ &`.
   - Tap `=`: it's inserted and the strip closes.
   - Tap `[`: you get `[]`, the same auto-pairing as typing.
4. Open `fn` again and type a letter.
   - Expected: the strip closes and the letter is typed.
   - `fn` again, or Back, closes it without typing anything.
5. Alt+Space types nothing.

## CUJ 5: Move the cursor and select

1. **Swipe left and right on the keys.**
   - Expected: the cursor moves one character per about 70 px, and stops at the start or end of the line.
2. **Swipe up and down.**
   - Expected: line by line, about 90 px per line, keeping the column. With wrap on, it moves by screen row.
3. **Hold Shift and swipe.**
   - Expected: the selection grows, and the status line shows `⇧sel`.
4. **Cursor Assistant:** double-tap the keys, then slide in all four directions.
   - Expected: the cursor moves; up and down keep the column.
5. A **long, fast slide** across the whole keyboard (like the backlight gesture).
   - Expected: it's ignored and the cursor returns to where it was.
6. **Touchscreen:** drag to scroll the code; tap to place the cursor.
   - Expected: no copy/paste popup bar appears; that's intentional, `sym+C/X/V` are the clipboard.

## CUJ 6: Edit with leader commands (hold `sym`, press a letter)

Check that each of these does exactly one thing:

| Keys | Expected |
|---|---|
| `sym+U` / `sym+Y` | Undo / redo. A run of typing undoes as one step; a pause of about 1 s starts a new step. Smart Enter is one step. |
| `sym+C`, then `sym+V` on another line, with nothing selected | The current line is pasted **above** the cursor's line ("Copied line"). |
| `sym+C` / `sym+X` with a selection, then `sym+V` | Pastes at the cursor, replacing any selection. |
| `sym+L`, then `sym+L` again | Selects the line, then extends the selection by one more line. |
| `sym+D` | Deletes the line, or every selected line. |
| `sym+J` | Duplicates the line below; the cursor follows the copy. |
| `sym+K` | Toggles `# ` on the line, or on the selected lines. |
| `sym+Space` with several lines selected | Indents every selected line. |
| `sym+W` | Soft wrap off/on. The choice survives a restart. |
| `sym+I` / `sym+O` | Font size up/down (8–24 sp). It survives a restart. |
| `sym+N`, then `sym+N` within 3 s | Resets to the starter code. The old draft goes to the backup file, and `sym+U` brings it back. A single `sym+N` only shows the "press again" message. |
| `sym+R` / `sym+E` / `sym+S` | "Coming in M4/M3" messages. |

## CUJ 7: (removed)

Find and Go to line were removed in PLAN revision 9.

## CUJ 8: Panes, Back and the red button

1. `sym+T` on Code, then `sym+T` again.
   - Expected: Results opens, then you're back on Code.
2. Repeat from Problem.
   - Expected: you return to Problem.
3. **Hold `sym` on every pane.**
   - Expected: the yellow `sym` badge shows. The **Android "Choose input method" dialog must never appear**: I found and fixed this on the Problem pane.
4. **Back:**
   - On Code: nothing happens.
   - On Results or Comments: goes to Code.
   - On Problem: the app goes to the background. Reopening it keeps everything.
   - With the palette or cheat sheet open, Back only closes that.
5. **Red button** from Results or Comments goes to Code.
6. **Tap ▶ Run, long-press Run, and tap Submit.**
   - Expected: each shows a short "coming in M3/M4" message.
7. `sym+H`.
   - Expected: the cheat sheet, generated from the real key table. Back, `sym+H`, a tap or any key closes it.

## CUJ 9: Never lose code

1. Type something, then watch the title line.
   - Expected: nothing changes. Saving is silent (1 s after you stop, and on pause); the title line only shows a red `● save failed` if a write fails.
2. Type, **wait 2 s**, then run `adb shell am force-stop dev.titan.code` on the Mac and reopen the app.
   - Expected: the text and cursor position are intact. Panel test E14 checks this automatically.
3. Press Home in the middle of typing, then reopen.
   - Expected: everything is still there (saved on pause).
4. Reinstall with `scripts/install.sh app`.
   - Expected: the drafts are still there.

## CUJ 10: Free typing (5 minutes)

1. Type freely, including sentences after full stops and common English words.
   - Expected: no autocorrect, no suggestion bar, no auto-capitals, and no on-screen keyboard ever.
2. Hold a letter, then hold Backspace.
   - Expected: repeat starts after about 400 ms, then about 25 characters per second. Shift+Backspace held deletes forward. `sym` chords and Enter never repeat.

## CUJ 11: Layout

1. Look at every pane at font 8 and at 24.
   - Expected: the tab bar sits right of the camera; nothing is hidden by the camera; no button sits in a rounded corner.

---

## Known gaps (not bugs)

- Results and Comments are placeholders (M2–M4). Run and Submit do nothing yet.
- Dedenting a **selected block** has no key. Smart Backspace dedents one line at a time.
- Auto-pairing doesn't know about triple-quoted strings that span lines.
- Indent guides aren't shown yet. Matching-bracket and current-line highlights are.
- adb can't send the red button (401) or `fn` (403), so those were never tested by me. They need your hands.
