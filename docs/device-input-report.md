# Device input report: Titan 2 Elite (Phase 0 / M0)

Source data: `reports/probe-20261101-200140/` (guided run, 70 of 85 results), `reports/adb-20261101-201016/` (adb checks plus a later all-tables settings diff), and Free log sessions recorded on 2026-11-01. Device: Titan 2 Elite, Android 16 (SDK 36), build `Titan 2 Elite_V02.00.04`, 1076×1200 at 300 dpi, default IME `com.iqqijni.bbkeyboard` (the Unihertz keyboard).

**Summary**

- The physical keyboard's Alt characters come from the key map (`TitanKey.kcm`), so with `TYPE_NULL` the app gets every printed symbol from `getUnicodeChar()` with no IME involved.
- `sym` reaches the app as `KEYCODE_SYM` with `META_SYM_ON`, and all 26 `sym`+letter chords arrive. Hold-to-use leader works.
- Hardware keys **never auto-repeat** (repeatCount stays 0, even on Backspace). The editor has to implement its own key repeat.
- **Keyboard surface:**
  - **Scroll Assistant** gives touch events with coordinates from a device named `touchPad`. Shift+swipe works. Alt- and sym-held swipes produce nothing.
  - **Cursor Assistant**, used properly (double-tap the keys, then slide), gives standard arrow-key events in **all four directions** (`DPAD_UP/DOWN/LEFT/RIGHT`). Up/down need more than one line of text, and the field must have focus. It works in TYPE_NULL.
  - Pointer capture gives nothing, and there is no Mouse mode on this device.
- The **red button** is taken by the system out of the box. Set as the **PTT key for our app**, it arrives as **keycode 401** at every stage.
- The **keyboard backlight** can't be forced on by any method found. The owner dropped the requirement.
- **LeetCode:** OkHttp and WebView `fetch()` both work, signed in, with no Cloudflare challenge.
- **Symbols:** 15 of the 28 Python symbols on the K16 list are missing from the keyboard, and **no key combination or IME menu provides them**. The other 13 take 2 keystrokes.
- **Screen:** a 123×123 px camera cutout sits in the top-left corner and the corners are rounded (100 px radius). The probe now hides the status bar and uses the top band beside the camera.

---

## 1. Key map and where characters come from

Keyboard device: **`TitanKey`** (`/dev/input/event5`), using `TitanKey.kl` and `TitanKey.kcm` (copies in `reports/adb-latest/keymaps/`). Left Shift oddly arrives from `mtk-pmic-keys`, but behaves normally.

| Physical key | Plain | Alt (from `.kcm`) | Notes |
|---|---|---|---|
| Q W E R T Y U I O P | letters | `0 1 2 3 ( ) _ - + @` | |
| A S D F G H J K L | letters | `* 4 5 6 / : # ' "` | |
| Z X C V B N M | letters | `7 8 9 ? ! , .` | |
| Backspace (DEL), Enter | | | Enter is Enter with Alt too |
| Space | space | `U+EF01` (private-use) | Alt+Space produces a private-use character. The router must ignore it. |
| Alt (left) | modifier | | Works only **while held**. No one-shot or locking Alt at system level (K04). |
| `sym` | `KEYCODE_SYM` (63), scancode 253 | | Modifier `META_SYM_ON` |
| Shift L / Shift R | modifier | | Capitals while held (K07: sym+Shift+letter → `H B`) |
| BACK, HOME, APP_SWITCH | system keys on the keyboard's bottom row | | BACK reaches pre-IME. HOME and APP_SWITCH are taken by the system. |
| **`fn`** (`FUNC3`, scancode 251, bottom row next to Shift R) | arrives as keycode **403** | | Reaches pre-IME in every mode (K09 and Free log). It does nothing by itself, opens no IME menu, and `fn`+letter gives the plain letter. |

- **No dedicated `,` `.` or digit keys.** Comma, period and every digit are Alt+letter (2 keystrokes).
- **Source of Alt characters:** the key map. K03@null delivered 26 of 26 Alt characters with **0** IME commits. The §5.1 fallback mapping table is **not needed**.
- `Virtual.kcm` / `Generic.kcm` define `[ ] { } < > = …` for keys this keyboard doesn't have, so those keys are not reachable.

## 2. `sym`, Alt, Shift and the red button

| Test | Result |
|---|---|
| K05 tap `sym` | Reaches pre-IME, activity and view in TYPE_NULL. In text mode the IME consumes it after pre-IME. |
| K06 hold `sym` + each letter | 26 of 26 letters carry `META_SYM_ON`. In TYPE_NULL they reach the view unconsumed. In text mode they're visible only at pre-IME. |
| K07 `sym`+Shift+letter | Works when `sym` is pressed first. **Shift first, then `sym`, opens the system keyboard picker.** Avoid Shift→`sym` ordering. |
| K04 Alt alone / double Alt | Alt applies only while held. **Recorded as PASS**: that matches "no sticky modifiers". |
| K10 hold a letter | **No auto-repeat** (repeatCount 0, no `FLAG_LONG_PRESS`). Holding a key inserts one character. |
| K08 Space/Enter/Backspace with modifiers | All combinations arrive (Shift and Alt variants). |
| K12 red button | Out of the box: kernel scancode 249 (`FUNC1`) on `mtk-pmic-keys`, the system intercepts it, and **the app gets no key event**. Set to "launch app", it just reopens the app at its home screen. **Remapped** in Settings → Shortcut keys (the `AguiShortcutKey` app): **PTT app = probe** → keycode **401**, scancode 249, from `mtk-pmic-keys`, at pre-IME, activity and view, for short and long presses. **Programmable key = Sym key** (`func1_programmable_key_function=2`; options 0 None, 1 Ctrl, 2 Sym, 3 Magic, 4 Media) → `KEYCODE_SYM` + `META_SYM_ON` with scancode 249 (real `sym` is 253). |
| K11 per-letter system shortcuts | **Not run.** |

## 3. Pre-IME ordering (K15) and IME interference (K14)

- **K15@null:** all 17 presses were seen first at `preIme`, and none were consumed. Order: `preIme` → `activity` → `view`.
- **Text mode:** the Unihertz IME eats `sym` and `sym`+letter after pre-IME, and turns typing into composing text. `dispatchKeyEventPreIme()` still sees every key first.
- **K14:** `TYPE_NULL` and the "raw" fallback (`TEXT|NO_SUGGESTIONS|VISIBLE_PASSWORD`) both give text exactly as typed with **0** composing or correction calls. Normal text mode made 6 composing calls.
- **K13 rollover:** 5 of 5 lines exact in TYPE_NULL. The text-mode variant wasn't run.

**Conclusion:** `TYPE_NULL` plus pre-IME interception works as PLAN §5.1 assumes.

## 4. Touch keyboard surface

There are three Unihertz touch settings: **Scroll Assistant**, **Cursor Assistant** and flick-to-pick. **There is no Mouse mode.** Scroll Assistant also has a Quick Settings tile, `touchpad_scroll_assistant`.

| Mode | What an app receives from a swipe |
|---|---|
| All off | A `KEYCODE 404` DOWN+UP pair (scancode 404) from `touchPad` when the finger lifts. No direction or distance. |
| **Cursor Assistant** | A plain swipe gives only the 404 pair; that's all the guided run captured. **The feature needs a double-tap on the keys first, then a slide.** That sends `DPAD_LEFT` / `DPAD_RIGHT` KeyEvents from `touchPad`, one per step, about 25–85 ms apart. In the first runs (one line of text) it sent left and right only, because there was nowhere to go vertically; see the multi-line follow-up below. In TYPE_NULL every step reaches pre-IME, activity and view; in normal text mode the IME swallows some (29 at pre-IME, 23 at the view). It works in other apps; the probe's own field just doesn't act on arrow keys. **Multi-line follow-up:** with three lines of text, Cursor Assistant sends **all four directions**.
- **TYPE_NULL, field focused:** up ×7, down ×9, left ×21, right ×10, reaching pre-IME, activity and view.
- **Normal text mode:** all four directions, but the IME consumes them after pre-IME.
- Earlier runs that showed no arrows in TYPE_NULL had the probe field unfocused (the Release button was pressed by mistake). A single line gives only left/right.
- Cursor mode stays active across input-mode switches.

**Follow-up:** any key press, Shift included, ends cursor mode (owner, in other apps), so it can't select. With **both assistants on**, each works in other apps. In the probe, all five slides (plain and double-tap) arrived as Scroll Assistant touch strokes with no arrow keys mixed in, and a Shift-held swipe carried `META_SHIFT_ON`. |
| **Scroll Assistant** | **Touch MotionEvents** from device `touchPad`, source `TOUCHSCREEN`, tool finger, delivered through `Activity.dispatchTouchEvent`. Absolute screen-space coordinates (x ≈ 0–1080, y ≈ 275–970), about 10 ms between samples. The 404 pair still follows each swipe. |
| Pointer capture (G10, L01) | Granted, but **0 captured events** in every mode. Capture is dropped when the app loses focus (expected). **Unusable.** |

Scroll Assistant details:

- **G05, Shift held + swipe:** works. All 38 events carry `META_SHIFT_ON`, so Shift+swipe selection is possible.
- **G06/G07, Alt or `sym` held + swipe:** **no touch events at all** (no 404 either). The firmware appears to switch off the surface while Alt or `sym` is held. Worth one confirmation re-run.
- **G08, typing:** brief stray `touchPad` sequences (DOWN, MOVE, CANCEL) during normal typing. These have to be filtered out.
- **G09/G12, flicks and quick lifts:** often end in `ACTION_CANCEL` rather than `ACTION_UP`.
- **G11, light tap:** no `touchPad` events.
- **G13, long slide (backlight gesture):** arrives as an ordinary near-full-width horizontal swipe (x ≈ 60→1080). The system also uses it for the backlight.
- **Risk:** these are screen-coordinate touch events. If the app doesn't intercept them, they act as real taps and drags on whatever view is under those coordinates.
- Matching: identify the device by **name `touchPad`** (or `SOURCE_TOUCHPAD` in its device sources), not by id. Its id was 6 on this boot.

## 5. Keyboard backlight (B01–B03)

| Approach | Result |
|---|---|
| `buttonBrightness = BRIGHTNESS_OVERRIDE_FULL` (B01, B03, Free log) | **No effect.** The override is applied (logged), but the light stays off until a key is pressed. |
| Writable setting (B02 + adb diffs) | The long slide and the QS `keyboard_led` tile change **no** key in system, secure or global settings. Global `agui_keyboard_background_light=1` exists, but setting it to 0 and back over adb made no visible difference. The tile works as a master allow/never switch. |
| Kernel / sysfs | `/sys/class/leds` is not readable. `dumpsys input` reports "0 keyboard backlights", so Android's own keyboard backlight controller isn't used. |
| Behaviour | The backlight lights on a keypress and turns off a few seconds after the last one. |

**Conclusion:** no app-controllable way to force the backlight on was found. **The owner dropped the requirement** (PLAN Appendix B).

## 5a. Screen geometry

| Item | Value |
|---|---|
| Logical size | 1076×1200 px (physical 1080×1200), density 1.875 (300 dpi) |
| Camera cutout | Bounding rect `(0,0)–(123,123)`: a square in the **top-left** corner |
| Status bar | 123 px tall, the same height as the cutout |
| Rounded corners | All four, radius 100 px |

- **Layout before the fix:** with targetSdk 35 the app draws edge-to-edge, so before inset handling text was hidden under the status bar and camera. Padding by the full status bar wasted the whole top line.
- **Current probe layout:**
  - The status bar is hidden (a swipe down shows it briefly), and the app draws into the cutout (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`).
  - Each screen's first line is indented past the camera and made at least 123 px tall, so the rest of the screen starts below the camera band.
  - Verified with screenshots on the main menu and Free log.

## 6. Lifecycle (L01–L03)

- **L01:** pointer capture is lost on switching apps and not restored. **Not applicable**, since pointer capture isn't used (§4).
- **L02:** the screen off/on cycle is clean.
- **L03:** changing a keyboard setting causes focus loss and pause/resume, with no recreation, thanks to `configChanges`. Marked Unclear without notes; no problem is visible in the log.

## 7. LeetCode / Cloudflare (C01)

Signed in with LeetCode's own login page in the WebView. Cookies present: `LEETCODE_SESSION`, `csrftoken`, `cf_clearance`. `userStatus` GraphQL returned **HTTP 200, signed in, no challenge** over all three paths: OkHttp with the default User-Agent, OkHttp with the WebView User-Agent, and WebView `fetch()`. **OkHttp is fine as the primary transport**; keep the `fetch()` fallback as planned.

## 8. K16 Python symbols (TYPE_NULL, the editor's mode)

A keystroke is one key press: Alt held + key = 2.

| Symbol | Keystrokes | How |
|---|---|---|
| `_` | 2 | Alt+U |
| `(` `)` | 2 | Alt+T, Alt+Y |
| `:` | 2 | Alt+H |
| `'` `"` | 2 | Alt+K, Alt+L |
| `#` | 2 | Alt+J |
| `+` `-` | 2 | Alt+O, Alt+I |
| `*` | 2 | Alt+A (confirmed in Free log; skipped by mistake in the guided run) |
| `/` | 2 | Alt+G |
| `!` | 2 | Alt+B |
| `@` | 2 | Alt+P (confirmed in Free log) |
| `=` `[` `]` `{` `}` `<` `>` `%` `\` `\|` `&` `^` `;` `~` `` ` `` | **missing** | not on the keyboard in any layer |

Not on the K16 list but common in code, and also 2 keystrokes each: `,` (Alt+N), `.` (Alt+M), `?` (Alt+V) and every digit `0–9`.

**Nothing takes more than 2 keystrokes. 15 of 28 symbols are missing.**

**Is there a built-in way to the missing symbols?** Tested on 2026-11-01 in Free log, in both normal text mode (with the Unihertz IME) and TYPE_NULL:

| Tried | Text mode (IME) | TYPE_NULL |
|---|---|---|
| Tap `sym` | No popup, no character | No popup, no character |
| Tap `fn`; `fn`+F | No popup; `fn`+F types `f` | No popup |
| Alt+Space | Types a space | `U+EF01` (private-use) |
| Alt+Shift+F (hoping for `^`) | Types `6` | Arrives with `SHIFT+ALT` meta, **no character** |
| Hold F; hold Alt+F | Single `f` / `6`; no popup, no repeat | Same |

**No key combination or IME menu provides a missing symbol.** The app has to supply them. The owner's choice (PLAN §5.3):
- the router maps Alt+Shift+S/D/F/Z to `$ % ^ &`
- the other symbols go through a touchscreen palette opened with `fn`
- `` ` `` and `\` are not needed

## 9. Results that need a re-run or are not applicable

| Test | Status | Action |
|---|---|---|
| G01–G13 @mouse | Not applicable | There is no Mouse mode on this device. |
| L01 | Not applicable | Pointer capture isn't used. |
| K04 | FAIL → PASS | Hold-only Alt is the expected behaviour. |
| K12 | Done | Works through PTT app = Titan Code (keycode 401, §2). |
| K11 | Not run | Run once, if per-letter Unihertz shortcuts are in use. |
| K02@null | No data recorded | Shift in TYPE_NULL is already shown by K07 and Free log. Optional re-run. |
| G06/G07 @scroll | No events | Confirm once that a swipe really was made while holding Alt or `sym`. |
| K13@text | Not run | Optional (the editor uses TYPE_NULL). |
| G-tests @off / @cursor marked Unclear | Explained | Plain swipes only send keycode 404 (§4). Cursor Assistant was re-tested in Free log with the double-tap. |
| Cursor Assistant + Shift; both assistants on | Done | No selection through Cursor Assistant. Both assistants can be on together without double events (§4). |
| B01–B03 | Closed | The owner dropped the backlight requirement. |

Probe bugs found (left unfixed, since the probe is throwaway):

- The gesture auto-checks don't recognise `touchPad` touch events as keyboard-surface input.
- The gesture tests run only in TYPE_NULL.
- B02 tries to write a setting with an empty name when the diff finds nothing.
- B02's diff covers only the system table.
