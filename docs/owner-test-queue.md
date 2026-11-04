# Owner test queue

A running list of what needs your hands on the phone, in the order it was built. Each item says what to do and what should happen. Tick items off (`[x]`) or note problems next to them. The longer walkthroughs are in `m1-test-plan.md` and `m2-test-plan.md`.

## Rev 10: Comments redesign, Back on Problem

- [ ] **Solutions first.** Open a problem online, then tap **Comments**.
  - Expected: the **Solutions** tab is selected, and listed first, before Discussion.
  - Rows show title, author, ▲ votes, 💬 comment count and a two-line summary (⤓ marks posts saved for offline).
  - A problem saved before this build shows its old 5 solutions; tap ⟳ to get the new 20-post list with counts.
- [ ] **Open a post.** Tap a row, or swipe on the keys to move the highlight and press Enter.
  - Expected: the title, author line and full body, then **Comments (N)** with replies indented.
  - Comments come 20 at a time, with **Load more comments** at the end.
- [ ] **Load more comments.** Tap it.
  - Expected: the next 20 are appended, and the page keeps its scroll position.
- [ ] **Code folding.**
  - In a post body, **Python** code starts open; tap "▾ fold code" to fold it.
  - Other languages (C++, Java…) start folded.
  - In comments and the Discussion tab, every code block longer than 4 lines starts folded: the first 4 lines, then "▸ N more lines". Tap to open, tap "▾ fold code" to close.
  - Code pasted into comments **without** ``` fences is detected and folds too. Report any prose that wrongly turns into code.
- [ ] **Back in Comments.** Back on an open post returns to the Solutions list. Back on the list goes to Code. "← Solutions · Back" does the same as Back.
- [ ] **Offline.** In airplane mode, open a post you opened before.
  - Expected: body and comments are there.
  - A post never opened shows only its body if it was one of the top 3 (prefetched); otherwise "not saved".
- [ ] **Back on Problem.**
  - On a statement, Back (key, **or the screen-edge back swipe**) shows the **problem list**, with the current problem highlighted.
  - **Rev 13:** Back on the list returns to the open problem. Back never closes or backgrounds the app, on any pane.
- [ ] **The back swipe never closes the app's state.** Swipe back from the screen edge on every pane.
  - Expected: it follows the same rules as the Back key, and Code ignores it.

## M3: Run and Submit on LeetCode (needs sign-in)

The parsers were written from LeetCode's documented response shapes. Your first real run confirms them. If anything looks wrong, run `scripts/logcat.sh app` while reproducing; the log line `http: … via browser` shows each call.

- [ ] **Sign in** (⚙ → Sign in) if you haven't.
- [ ] **Run.** Open Two Sum with a correct solution, then `sym+E` (or tap ▶ Run).
  - Expected: the Results tab opens at once ("Sending to LeetCode…", then "Waiting in LeetCode's queue…" or "Running…").
  - Then **Accepted**, "3/3 passed", runtime and memory, and three green cases with Input, Output and Expected.
  - The tab reads `Results ✓ 3/3`.
- [ ] **Wrong answer on Run.** Break the code, for example `return [0, 0]`, and run again.
  - Expected: **Wrong Answer**, and the failing cases are outlined red, showing Output and Expected.
- [ ] **Errors.**
  - A typo such as `retrun x` gives **Compile Error** (or Runtime Error) with the message in a red box.
  - `print("hi")` inside the function shows under **Stdout**.
- [ ] **Submit.** `sym+S` once.
  - Expected: "Press sym+S again…", and the Submit button reads **Confirm?** for 3 s.
  - Press `sym+S` again (or tap Confirm?): "Submitting…", then **Accepted**, "N/N test cases", runtime and memory with "beats X%".
  - The list shows ✓ for the problem, and **Submissions** at the bottom of Results lists it.
- [ ] **Failed submission.** Submit something that fails a hidden case.
  - Expected: the "Failing case" box shows Input, Output and Expected, and the case appears under **Test cases** as "from a failed submission".
  - The next `sym+E` runs it too.
- [ ] **Custom test.**
  - Type an input on blank lines in the editor (for Two Sum: `[1,5,9]` then `14`) and select the lines with `sym+L`.
  - `sym+C`, open Results, then tap **＋ Add test from clipboard**.
  - Expected: "Test added"; it's listed as "yours". Delete those lines from your code.
  - After the next Run, it shows "expected ✓".
- [ ] **Delete a saved test** with ✕. Examples can't be deleted.
- [ ] **Offline.** In airplane mode, `sym+E` and `sym+S` show "Offline — your draft is saved…" and nothing is sent.
- [ ] **Signed out.** After ⚙ → Sign out, `sym+E` says "Sign in to LeetCode first".
- [ ] **Scrolling.** Swipe on the keys in Results to scroll; Back and `sym+T` return to where you were.
- [ ] **A slow judge.** While "Waiting…" shows, switch problems and come back.
  - Expected: the result lands on the right problem, and pressing `sym+E` again in the meantime says "Still waiting…".

## M4: Local runner and offline packs

- [ ] **Local run.** Write a correct Two Sum, then `sym+R` (or tap ▶ Run).
  - Expected: Results opens and shows **Accepted**, "Run locally · 3/3 passed · N ms · Python 3.14.x". Each case shows its time.
  - It works in **airplane mode** too.
  - Expected values come "from the statement" until a LeetCode run has verified them.
- [ ] **Speed.** The first run after launching the app should take well under a second. Python warms up at launch; if the very first run is slow, note how slow.
- [ ] **Errors.** `return nums[99]` gives **Runtime Error** with a traceback limited to your code.
  - Tap **Go to line N**: the Code pane opens with the cursor on that line.
  - A syntax error gives **Compile Error** and no case cards.
- [ ] **Infinite loop.** Add `while True: pass` and press `sym+R`.
  - Expected: after 10 s, "Time Limit Exceeded … runner was stopped and restarted".
  - Remove the loop; the next `sym+R` works normally (the runner restarted).
- [ ] **Modes:**
  - Group Anagrams, groups in any order: Accepted.
  - A tree problem (226 Invert Binary Tree).
  - A linked list problem (206 Reverse Linked List).
  - A design problem (146 LRU Cache).
  - 27 Remove Element (k-first).
  - Any problem returning a float (e.g. 643 Maximum Average Subarray I).
  - 133 Clone Graph should say "Not supported locally; use sym+E".
- [ ] **Deep recursion.** A recursive DFS on a big input doesn't crash. Paste a large case via "＋ Add test from clipboard".
- [ ] **Python version on LeetCode.** Signed in, run this once with `sym+E` on any problem:
  - **Code:** `import sys; print(sys.version, sorted(k for k in globals() if not k.startswith('_')))` inside the method.
  - **Report:** send me the Stdout. It confirms the Python version and LeetCode's pre-imported names (PLAN §7).
- [ ] **Offline pack.** ⚙ → Offline packs → **＋ Add** on Binary Search (or any plan).
  - Expected: the row shows "downloading n/32", then "32/32 saved". It keeps going with the app in the background.
  - Problems in it show ⤓ in the list.
- [ ] **Pack from the list.** Filter the list (e.g. Hard + `#graph`), then ⚙ → "The problem list as filtered now … ＋ Add".
- [ ] **Wi-Fi only.** Switch Wi-Fi off and add a pack.
  - Expected: "waiting for Wi-Fi". Set **Download packs on mobile data: Allowed** and it starts.
- [ ] **Remove a pack.**
  - Expected: its ⤓ marks disappear, except problems another pack also holds.
  - A draft in one of its problems is still there.
- [ ] **E16 (the M4 exit check).** After a study-plan pack finishes, switch on **airplane mode**:
  - open one of its problems
  - read its Comments (Solutions list, a saved post, Discussion)
  - run locally with `sym+R`
  - press `sym+S`

  Expected: everything works except Submit, which shows the offline message.

## M5: Completion (snippets removed in rev 13)

- [ ] **Words.** In Code, type `de` inside a function.
  - Expected: a strip above the status line shows suggestions: words from your code first, then `def`, `del`, `deque`, `defaultdict`…
  - The first item has a yellow border. `sym+Space` inserts it; tapping any item inserts that one.
- [ ] **No snippets.** Type `bfs`: no snippet is offered (only words), and the cheat sheet (`sym+H`) has no snippet list.
- [ ] **Doesn't get in the way.**
  - No strip appears inside strings or comments, or for 1-letter words.
  - Moving the cursor, typing a space or symbol, or Back closes it.
  - Enter always makes a new line.
  - With no strip open, `sym+Space` still indents.

## M6: NeetCode lists

- [ ] **Lists.** `sym+P`, then tap **NeetCode 150**.
  - Expected: 150 problems in NeetCode's order under pattern headers (Arrays & Hashing, Two Pointers…).
  - **Blind 75** shows 75. Filters and typing still narrow the list. **All** returns to everything.
- [ ] **Pack.** ⚙ → Offline packs → **NeetCode 150 ＋ Add**. It downloads like a study plan.

## Rev 13: NeetCode and Codeforces

The list has **LeetCode / NeetCode / Codeforces** at the top. The editor, keys and panes are the same on all three. Note: **neetcode.com isn't NeetCode's site** (it redirects elsewhere); the app uses neetcode.io.

**Browsing (no sign-in needed):**
- [ ] **NeetCode list.** `sym+P`, tap **NeetCode**.
  - Expected: 939 problems in NeetCode's order, with chips **NeetCode 150 / Blind 75 / NeetCode 250 / By pattern** (pattern headers). Pro problems show 🔒.
  - Numbers are the LeetCode numbers; typing `1` or `two sum` filters as before.
- [ ] **NeetCode problem.** Open Two Sum from NeetCode 150.
  - Expected: the title line reads "NeetCode · 1. Two Sum · Easy". The statement has examples, plus hints at the end (complexity target, Hint 1–3).
  - **Comments** shows one post, "NeetCode solution: Two Sum" (Python code only). There's no Discussion tab.
  - Opening it again in airplane mode works (statement and article saved).
- [ ] **Codeforces list.** Tap **Codeforces**. The first time it downloads the list (one request, about 2 MB).
  - Expected: ~11,500 problems, newest first, numbers like `2278A`, ratings on the right (— if unrated). Easy/Med/Hard chips mean ≤1200 / 1300–1900 / ≥2000.
  - Typing `4a` finds Watermelon.
- [ ] **Codeforces problem.** Open 4A (or any recent problem).
  - Expected: within a few seconds, the statement with time/memory limits, tags and readable maths (`1 ≤ w ≤ 100`, `10⁹`-style powers, subscripts). Report anything that still shows `$$$` or a backslash command.
  - Images (if the problem has any) show, and the problem opens offline later.
  - **Comments** says Codeforces problems have no comments here.

**Running locally (offline):**
- [ ] **NeetCode local run.** Solve Two Sum (NeetCode), `sym+R`.
  - Expected: "Run locally · 3/3 passed", Expected values "from the statement". Try a linked-list (Reverse Linked List), a tree (Invert Binary Tree) and LRU Cache.
- [ ] **Codeforces local run.** On 4A write:
  `w = int(input())` and `print('YES' if w > 2 and w % 2 == 0 else 'NO')` inside `main()`, then `sym+R`.
  - Expected: **Accepted**, each sample shows Input, Output and Expected. (Already checked once on the phone.)
  - Try `open(0).read().split()` and `sys.stdin.buffer` styles: both work.
  - A wrong answer shows ✗ and the note that answers are compared word by word.
  - Add your own test: type the whole input in the editor, `sym+L`, `sym+C`, Results → ＋. Multi-line input is kept as typed.
- [ ] **`sym+E` on Codeforces** says there's no remote run (use `sym+R` / `sym+S`).

**Signing in (⚙ → Accounts):**
- [ ] **NeetCode sign-in.** Tap Sign in next to NeetCode, then **Sign In → GitHub** (or Google) on the page.
  - A popup opens for GitHub/Google. After signing in, the app should close the page and say "Signed in to NeetCode as <email>".
  - **Tell me** if Google refuses ("This browser or app may not be secure"). GitHub is the fallback, if your NeetCode account has GitHub linked.
  - Sign out, then sign in again, to check both work.
- [ ] **Codeforces sign-in.** Tap Sign in next to Codeforces. The login form fits the screen width (pinch to zoom works).
  - After logging in, the app should say "Signed in to Codeforces as <handle>".
  - If a "Verify you are human" box appears, tick it: that's Cloudflare, and it's meant for you.
  - **Tell me** if it signs you in but the app doesn't notice (stays on the Codeforces page).
- [ ] **Codeforces language.** ⚙ → "Codeforces submit language" cycles PyPy 3 (64-bit) → PyPy 3 → Python 3.

**Run and Submit (signed in). The response parsing for both sites needs these to confirm it:**
- [ ] **NeetCode run.** On NeetCode Two Sum, `sym+E`.
  - Expected: Results shows "Run on NeetCode", one card per case with Output, Expected and Stdout (`print`). Expected values become "Expected (NeetCode)".
  - If it shows an error or empty cases, run `scripts/logcat.sh app` while repeating it and send me the log.
- [ ] **NeetCode submit.** `sym+S` twice.
  - Expected: "Submit to NeetCode" with "N/N test cases" and time/memory. A wrong solution shows the failing case (saved to your tests). The list shows ✓ once accepted.
  - A Pro problem without Pro should show NeetCode's own error message.
- [ ] **Codeforces submit.** On 4A with the solution above, `sym+S` twice.
  - Expected: "Sending to Codeforces (PyPy 3 (64-bit))…", then "In Codeforces' queue…" / "Running on test N…", then **Accepted** with time and memory, and "Open submission #… on Codeforces" (opens your browser).
  - Submitting the same code twice: Codeforces refuses duplicates. The app should show Codeforces' message ("You have submitted exactly the same code before").
  - A wrong solution shows "Wrong answer on test N" and how many tests passed.
  - The list shows ✓ for 4A, and Results → Submissions lists both attempts.
- [ ] **Signed out.** After ⚙ → Sign out (each site), `sym+E` / `sym+S` say "Sign in to <site> first".
- [ ] **Drafts per site.** NeetCode Two Sum and LeetCode Two Sum keep separate drafts. Switching between them never mixes code.

## Rev 14: typing no longer moves the cursor

- [ ] **Type normally** in the editor for a minute, resting fingers on the keys as you usually do, with words, spaces and Enter.
  - Expected: the cursor stays where you're typing; no sliding to another line.
- [ ] **`fn` palette, then Space.** Tap `fn`, tap a symbol such as `=`, then press Space several times.
  - Expected: each space goes right after the symbol. (`[` and `{` still auto-close, so a space after `[` lands between `[` and `]`; that's intended.)
- [ ] **Swiping still works.** Swipe left/right and up/down on the keys, then type straight away; also Shift + swipe to select.
  - Expected: the swipe moves the cursor as before, and typing right after it types where the swipe left the cursor.
  - If a quick swipe followed at once by a key now gets undone, tell me; the 300 ms window can shrink.

## Rev 15: cursor trail

- [ ] **Watch the trail.** Swipe on the keys, use the arrow keys, tap somewhere in the code.
  - Expected: a short yellow streak from where the cursor was to where it is, gone in about a quarter of a second. Typing doesn't make one.
  - Tell me if it's too faint, too bright, too long-lived or too thick; each is one number to change.

## Rev 16: smoother cursor swipes

- [ ] **Slow, small moves.** Move the cursor slowly by one or two characters, several times, on a long line.
  - Expected: it moves one character at a time, in proportion to your finger; no jumps over a word.
- [ ] **Longer moves.** A quick flick still crosses a long line; a slow full-width swipe now moves about 13–15 characters (less than before).
  - If long moves now feel too slow, try Settings → Keyboard surface → px per character 60 or 50 (slow moves stay predictable now), or tell me.
- [ ] **Speed-up setting.** Settings → Keyboard surface → speed-up **Off / Gentle / Normal**. Off makes every swipe exactly proportional.
  - Tell me which you settle on; the log now records each swipe's raw samples, so I can tune from your real movements.

## Rev 18: swipe vibration, indent guides, bracket highlight

- [ ] **Vibration while swiping.** Swipe slowly sideways on the keys: one light tick per character. Swipe up/down: a tick per line. Swipe into the start or end of a line: one firmer click, then silence.
  - Settings → Keyboard surface → "Vibrate on each step while swiping" turns it off. If you feel nothing, check that Android's own touch feedback (Settings → Sound & vibration) is on.
  - Tell me if the ticks feel too strong or too buzzy on fast swipes (I can tick only every 2nd or 3rd character at speed).
- [ ] **Indent guides.** Open code with nested blocks: thin grey lines at each indent level, the one for the block you're in brighter. With soft wrap on (`sym+W`), wrapped rows have no guides; that's intended.
- [ ] **Bracket highlight.** Put the cursor next to `(`, `[` or `{` (or its closer): both brackets of the pair are boxed.

## Rev 19: fainter vibration; name and icon

- [ ] **Vibration strength.** Swipe on the keys: each step should be a barely-there jolt. Settings → Keyboard surface → Off / **Faint** / Light (tapping one plays a sample).
  - If Faint is too faint to notice, use Light; if both feel wrong, tell me and I'll adjust the pulse lengths.
- [ ] **Name and icon.** The launcher shows **Coding** with a black icon: white `< >` and a yellow cursor. If your launcher uses themed icons, it shows a one-colour version.
  - The red-button setting (PTT app) now lists the app as **Coding**; it should still be selected.

## Rev 20: adaptive vibration

- [ ] **Fine moves.** Creep the cursor one character at a time: each step should be a clear, distinct pulse.
- [ ] **Long swipes.** Swipe quickly across a line: lighter, quick pulses (as strong as rev 19's Light at the least), not a buzz.
- [ ] **Levels.** Settings → Keyboard surface → Off / **Normal** / Strong (tapping one plays a sample). Tell me if slow steps need to be even firmer, or fast swipes lighter or heavier.

## Rev 21: stronger Strong

- [ ] **Strong, slow steps.** Settings → Keyboard surface → Strong, then creep one character at a time: each step should now be a clearly firmer jolt (40 ms; it was 24 ms). Fast swipes are unchanged.
  - If it's still too weak, tell me. Longer pulses start to feel like a buzz rather than a jolt; past this point, the phone's built-in "heavy click" effect is the next thing to try.

## Rev 22: no paid problems; random problem

- [ ] **No Pro or Premium problems.** NeetCode shows 788 problems (no Valid Word Square, Lonely Pixel…); LeetCode shows no 🔒 rows.
- [ ] **🎲 Random.** Pick a site and chips (e.g. LeetCode + Med), tap 🎲: it opens a random medium problem you haven't solved. Typed filters and lists (NeetCode 150…) are respected too.

## Rev 23: special types in the local runner

Write a solution and press ▶ Run (sym+R). Each should pass the examples offline; the **Note** line shows extra detail.
- [ ] **Linked List Cycle** (LeetCode 141 or NeetCode): Floyd's two pointers passes both examples. `return False` fails the cycle example.
- [ ] **Clone Graph** (133): a DFS copy passes. `return node` fails with the Note "Not a copy: node 1 is from the original graph".
- [ ] **Copy List with Random Pointer** (138): passes; returning `head` fails with "Not a copy".
- [ ] **Intersection of Two Linked Lists** (160): the two-pointer solution passes on both sites.
- [ ] **First Bad Version** (278) / **Guess Number** (374): binary search passes; the Note shows how many API calls you made.
- [ ] **Find in Mountain Array** (1095, NeetCode): a linear scan fails past 100 `get` calls; binary search passes.
- [ ] **Lowest Common Ancestor** (235/236), **All Nodes Distance K** (863): `p`, `q` and `target` arrive as nodes.
- [ ] **Serialize and Deserialize Binary Tree** (297) and **Encode and Decode Strings** (NeetCode 271): round trips pass.
- [ ] **BST Iterator** (173, NeetCode): the test case shows `["BSTIterator","next","next","hasNext",…]` with `[[[7,3,15,null,null,9,20]],[],…]` (it was cut short before), and a stack solution passes. This also checks that Python 3.14 reads the `Optional[TreeNode]` annotation.
- [ ] **N-ary trees** (559, 589, 590), **Flatten Nested List Iterator** (341), **Populating Next Right Pointers** (116/117): pass with a correct solution.
- [ ] **Implement Rand10** (470): the case shows ✓ with "All in 1..10" and rand7 calls per result; expected is blank because the answer is random.
- [ ] **Saved NeetCode problems refresh:** a NeetCode problem saved earlier re-downloads once when opened online (its test cases may change). Your drafts aren't touched.

## Rev 24: offline judge, test-case editor, timer, review

**Limits** (any problem; ▶ Run):
- [ ] **Infinite loop.** Add `while True: pass` to a solution: after about 3 s that case shows **Time Limit Exceeded** at its line (Go to line works), the other cases still show their results, and the next Run starts straight away (no restart wait).
- [ ] **Swallowed stop.** Wrap the loop in `try: … except Exception: pass`: it's still stopped.
- [ ] **Memory.** `a = []` then `while True: a.append([0] * 10000)`: **Memory Limit Exceeded** at the `append` line.
- [ ] **Too much printing.** `print` in an endless loop: **Output Limit Exceeded**; Stdout shows the first part and how much more there was.

**Checkers:**
- [ ] **Longest Palindromic Substring** (5): a solution returning `"aba"` for `"babad"` passes, although the example says `"bab"`.
- [ ] **Course Schedule II** (210): any valid order passes, also on a test case of yours with no expected output.

**Stress tests** (⚡ in Results; NeetCode problems, or LeetCode problems NeetCode also has, need its solution saved: open Comments once while online):
- [ ] **A bug is found.** Two Sum with a bug (e.g. allowing the same index twice): ⚡ shows **Different answer** with a small input, your output, the reference's, and ＋ Save as a test case adds it to the list.
- [ ] **A correct solution.** It shows **No differences found**, how many inputs, the speed on a full-size input next to the reference's, and the assumptions it made.
- [ ] **Slow solution.** A brute-force O(n²) Two Sum: the speed line shows yours far slower than the reference's, or Too slow.
- [ ] Tell me about any **Different answer** you think is wrong (an input the problem doesn't allow): the Inputs generated list shows what it assumed.

**Test-case editor:**
- [ ] **＋ New test** in Results opens the Code pane with `# nums (integer[])`-style sections, prefilled from the first example. Edit a value (the palette and swipes work), add an expected output if you like, press **sym+S**: back in Results with the new case. Your draft is back in the editor, unchanged.
- [ ] **Errors.** Type `[a,b]` or leave a bracket open, then sym+S: the message says what's wrong and the cursor goes to that line.
- [ ] **✎** on one of your cases edits it in place; on an example it saves a copy as yours. **Back** while editing cancels ("Test case not saved").

**Practice timer:**
- [ ] On a problem you haven't edited, the status line shows nothing; type one character and **⏱ 0:01** appears and counts. It pauses when you leave the app, and continues on another day.
- [ ] Submit: the history row in Results shows ⏱ with the time; an accepted submission stops it with ✓.
- [ ] Settings → Practice: Off hides it; **Reset** clears this problem's timer.

**Review:**
- ~~The list has a **Review** chip~~ (removed in rev 27 at your request).

## Rev 25: download everything

Settings → Offline packs, the three **Everything on …** rows (on Wi-Fi, or allow mobile data below them):
- [x] **NeetCode** (I ran it on 2026-11-03: all 788 saved, 408 new, none skipped, about 8 minutes): its pack row reads `statements N/788`, then `discussions`, then `788/788 saved` (a few may show as skipped). The notification "Saving Everything on NeetCode" shows the same progress.
- [ ] **LeetCode** (about 3,100 problems, a few hours): Download refreshes the list first, then saves. Problems you open meanwhile still load at once. Premium problems aren't saved.
- [ ] **Codeforces** (about 11,500 problems, about 10 hours, spread over two days by Android's limit): statements open offline afterwards, images included.
- [ ] **Stop** in the notification, or Stop on the pack row: the download ends and what it saved stays (Saved for offline doesn't drop). Download again resumes, skipping what's saved.
- [ ] **Airplane mode mid-download:** the row says `paused; resumes by itself` or `waiting for Wi-Fi`, and it carries on when you're back online.
- [ ] Tell me if LeetCode or Codeforces starts showing "too many requests" or a browser check while browsing during a download; I can slow it down.

## Rev 27: no review queue; reading text size

- [ ] The problem list has no **Review** chip and no ↻ marks; Settings → Practice has only the timer.
- [ ] **Problem pane:** `sym+I` makes the statement larger ("Problem and Comments text 110%", then 120, 135, 150…), `sym+O` smaller. The problem list rows grow too; the chips above them don't.
- [ ] **Comments pane:** the same size applies to the solution list, an open solution post and the comments.
- [ ] **Code pane:** `sym+I` / `sym+O` still change only the editor font.
- [ ] Settings → Editor has both steppers, and the size survives a restart.
- [ ] **Rev 28:** at a larger size (e.g. 150%) the Problem pane's site buttons, filter box, 🎲, ⟳ and chips grow with the list, and chips that no longer fit move to a second line. The ☰ Problems bar above a statement and the Comments bar grow too. Smaller sizes shrink them all.
