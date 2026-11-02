# Running the Key & Gesture Probe

The probe works through every test in PLAN.md §9.3, one per screen. You do the action, watch the live log, and tap **Pass / Fail / Unclear**. The probe records the raw events behind each result.

## 1. Install

```bash
scripts/doctor.sh      # everything ✓?
scripts/install.sh     # builds, installs, launches "Titan Probe"
```

If the build fails, send me `build-logs/latest.log` (or just tell me, since I can read the folder).

## 2. adb checks (about 2 minutes)

```bash
scripts/adb-checks.sh
```

This saves `dumpsys input`, the keyboard's `.kl`/`.kcm` files, settings and so on to `reports/adb-latest/`. It also asks you to:

- toggle the backlight once, so it can find the setting that controls it
- press keys and swipe for 25 seconds, to capture raw kernel events

Put a photo of the keycaps in that folder too.

## 3. Quick look before the full run

In the probe:

- **Show key character map** lists the character each key produces with Shift, Alt and sym. These are the characters an app gets from the key map.
- **Free log** lets you type and swipe anything. Every event appears live, and the *Mode* button switches the field between the three input modes.

## 4. Guided run (about 45–60 minutes, resumable)

1. Main menu → **Keyboard modes currently ON…**: tick what's enabled.
2. **▶ Start guided run.** The run has these parts:
   - Setup screens tell you when to change a Unihertz setting. The key tests need the touch features off, and the gesture tests run four times, once for each touch mode.
   - **AUTO** is the probe's own verdict where it can tell. You decide the result: tap Pass / Fail / Unclear, and **Notes** for anything odd (a popup, the app losing focus, the backlight going off).
   - **Back / Skip / Reset** move between tests or redo the current one. **Exit** saves progress, and so does closing the app.
3. Tests that need your attention:
   - **K11:** set up a Unihertz per-letter shortcut first.
   - **K12:** the red button may launch something. Just come back to the probe.
   - **K16:** type each symbol shown in the big box. It advances by itself, and **Can't find it** skips a symbol.
   - **B02:** Snapshot → long-slide the backlight → Diff → Grant write → Write.
   - **C01:** sign in on LeetCode's own login page (username and password). Then tap **Run checks**. Cookie values are never logged, only their names.

You don't have to finish in one sitting. The menu shows where you'll resume.

## 5. Send the results back

Main menu → **Save report** (the probe also saves when you exit a run), then:

```bash
scripts/pull-report.sh     # → reports/latest/{titan-probe-report.md, .json, logcat.txt}
```

Tell me it's there, since I can read `reports/` directly. You can also use **Share report** to send the two files.

## What's in the report

- **Header:** device, Android version, screen, default and enabled IMEs, the keyboard modes you ticked.
- **Input devices:** every device's name, sources and axes, plus the full key character map (plain / Shift / Alt / sym) for each keyboard.
- **Results:** a table of every test (your verdict, AUTO, notes). It's followed by the K16 symbol table and a summary of each test.
- **JSON only:** every raw event (`KeyEvent`, `MotionEvent`, IME calls, focus and lifecycle notes), tagged with the stage that saw it (`preIme` / `activity` / `view` / `touch` / `generic` / `captured`).
