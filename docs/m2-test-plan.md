# M2 test plan: critical user journeys

Use this on the phone to check the M2 build (read LeetCode, offline-first). Each journey has steps and the expected result. Note anything that's off: the CUJ number and what happened is enough.

**Before you start**
- Build and install with `scripts/install.sh app`. Optionally record the log with `scripts/logcat.sh app`.
- **Wi-Fi on** to start. CUJ 8 switches to airplane mode.
- Your M1 drafts are migrated into the new store on the first launch; CUJ 1 checks that.

---

## CUJ 1: First launch and setup checklist

1. Launch the app.
   - Expected: the **Set up Titan Code** checklist appears over the Code pane.
   - Scroll Assistant shows `?`: the app can't read that setting, so check it yourself.
   - Red button shows ✓ if PTT app = Titan Code.
2. Tap **Download**.
   - Expected: "Downloading the problem list: 100 / 3682 … 3682 / 3682" takes about 15 s. Afterwards the step shows ✓.
3. Tap **Done**, or press Back.
   - Expected: the checklist closes and doesn't come back on the next launch. ⚙ → Setup → Open reopens it.
4. Open Code.
   - Expected: the problem you had open in M1 with your M1 draft text and cursor. Nothing is lost.
   - If that problem was a bundled sample, its statement is now LeetCode's real one (CUJ 3).

## CUJ 2: Find a problem with the keyboard

1. Hold `sym` and press **P**.
   - Expected: the Problem pane shows the **list**: a filter line, chips, the **Daily** problem, then every problem in number order.
2. Type `anagram`.
   - Expected: the list narrows as you type, and the count reads e.g. `9/3682`. Backspace widens it again.
3. Try other filters:
   - Alt+digits `4` `9`: problems whose number starts with 49 (49, 490, 491…).
   - `#hash`: problems tagged Hash Table.
   - `#string valid`: tag and title words together.
4. **Swipe down on the keys.**
   - Expected: the highlight moves down a row about every 70 px. Swiping up moves it up. Cursor Assistant up/down works too.
5. Press **Enter**.
   - Expected: the highlighted problem opens. You see its statement, the title line changes, and a message says "Opened …".
6. Tap a row on the touchscreen.
   - Expected: it opens too.
7. Tap the chips (**Easy / Med / Hard**, **Todo / Tried / Solved**, **Saved**).
   - Expected: each narrows the list; tap it again to clear it.
8. `sym+P` again on the list.
   - Expected: back to the statement. Back on the list also returns to the statement.

## CUJ 3: Read a statement

1. Open a problem with images, e.g. 2 (Add Two Numbers) or 104 (Maximum Depth of Binary Tree).
   - Expected: dark styling, difficulty badge, tag pills, the examples, any images, constraints, collapsed **Hint** boxes (tap to open) and a **Similar** list.
2. Swipe up and down on the keys.
   - Expected: the statement scrolls 1:1, the same as in M1.
3. Open 1 (Two Sum).
   - Expected: the real LeetCode statement. The bundled "written for Titan Code" note is gone once it has opened online.
4. Open a 🔒 Premium problem without a Premium account.
   - Expected: "Premium problem. Sign in with a LeetCode Premium account…" The editor shows a comment if there's no starter code.
5. Press Back on the statement.
   - Expected: the app goes to the background, as in M1.

## CUJ 4: Code a new problem

1. Open a problem you've never opened, then go to Code with the red button.
   - Expected: LeetCode's Python starter, with the cursor on the indented line under `def`.
2. Type a few lines.
   - Expected: nothing on the title line changes. **Saving is silent** now.
3. `sym+P`, open a different problem, type there, then reopen the first one.
   - Expected: each problem keeps its own code and cursor. In the list, problems with a draft show ✎.

## CUJ 5: Comments and solutions

1. Open a popular problem (e.g. 49), wait about 5 s, then tap the **Comments** tab.
   - Expected: "Saved 2 Nov 2026", then up to 30 threads with author, ▲ votes and date. Replies are indented under the top 10 threads, and code blocks appear in monospace.
2. Swipe on the keys.
   - Expected: the comments scroll.
3. Tap **Show solutions**.
   - Expected: up to 5 Python solutions with full text and code.
   - **Solutions are hidden until you tap.** Switching to another problem hides them again.
4. Tap **Discussion**.
   - Expected: back to the threads.
5. Tap **⟳**.
   - Expected: "Downloading discussion and solutions…", then a refreshed page.
6. Back on Comments.
   - Expected: goes to Code.

## CUJ 6: Sign in (optional)

1. ⚙ → **Sign in**.
   - Expected: LeetCode's own login page fills the screen.
2. Type your username and password with the keyboard. This is the one place where normal typing goes to a web page; the IME is active here.
   - Expected: after login the page closes by itself. You see "Signed in as … Updating solved marks…", and the list soon shows ✓ and ◐ marks.
3. Press Back while the login page is open.
   - Expected: it closes without signing in.
4. ⚙ → **Sign out**.
   - Expected: "Signed out". The marks stay until the next list refresh.

## CUJ 7: Settings

Open ⚙ in the tab bar. Back, ⚙ or **Close** closes it.

1. Look at the **storage** line.
   - Expected: it counts saved statements and discussions, and grows as you open problems. Each opened problem also saves the next 5 in the list.
2. **Clear** storage.
   - Expected: the statements and discussions are gone (⤓ marks disappear), but your drafts are kept.
3. **Indent width 2**, then reset a problem with `sym+N` twice.
   - Expected: the starter now uses 2-space indents, and Enter after `:` indents by 2. Set it back to 4.
4. **Font −/+** and **Soft wrap**.
   - Expected: the same as `sym+O/I` and `sym+W`.
5. **Swipe speeds**.
   - Expected: change them in steps of 10. A bigger number means a slower cursor. They take effect at once and survive a restart.
6. **Draft history**.
   - After `sym+N` twice, a "reset" version is listed.
   - **Restore** puts it back as one undoable step (`sym+U`) and switches to Code.
   - A "periodic" version appears after 5 minutes of editing.

## CUJ 8: Offline (the M2 exit check)

1. Open three problems online and read their Comments, then switch on **airplane mode**.
   - Expected: the title line shows `⦿ offline`.
2. `sym+P`, tap the **Saved** chip, and open each of the three.
   - Expected: the statement, images and comments all appear.
3. Open a problem that was never saved.
   - Expected: "Offline, and that problem isn't saved yet". The current problem stays open.
4. `sym+S`, `sym+E` and long-press Run.
   - Expected: "Offline — your draft is saved. Submit when you're back online."
5. Type, wait 2 s, then run `adb shell am force-stop dev.titan.code` and reopen.
   - Expected: the draft is intact.
6. Switch airplane mode off.
   - Expected: the badge disappears.

## CUJ 9: Still M1

Spot-check that M1 behaviour is unchanged:
- smart Enter and Backspace
- Alt and Alt+Shift symbols
- the `fn` palette
- swipes and Shift+swipe on Code
- the `sym` leader commands; `sym+H` now lists `sym+P`, and `sym+F` and `sym+G` do nothing
- holding `sym` never opens the input-method picker on any pane, including the list

---

## Known gaps (by design for now)

- **Run, Submit and Results are M3/M4.**
- **Cloudflare challenge:** the app says "LeetCode asked for a browser check". The automatic WebView fallback arrives in M3.
- **Images in discussions** show as "[image]".
- **The list refreshes itself weekly**, or now with ⟳ on the list. There is no background sync while the app is closed; that comes in M4.
- **Draft history** has no diff view. Restore it and undo if it's the wrong one.
