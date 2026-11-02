package dev.titan.probe

import org.json.JSONObject

enum class Special { NONE, SYMBOLS, CAPTURE, BACKLIGHT_WINDOW, BACKLIGHT_SETTING, NETWORK }
enum class Kind { KEY, GESTURE, OTHER }

class Auto(val status: String, val text: String)  // status: PASS | FAIL | INFO

class RunCtx(val events: List<EventLog.Entry>, val buffer: String, val extra: JSONObject)

class TestDef(
    val id: String,
    val title: String,
    val instruction: String,
    val learn: String,
    val kind: Kind,
    val variants: List<String>,
    val special: Special = Special.NONE,
    val check: ((RunCtx) -> Auto)? = null,
)

sealed class Step {
    abstract val key: String
}

class SetupStep(override val key: String, val title: String, val body: String) : Step()

class TestStep(val def: TestDef, val variant: String) : Step() {
    override val key: String get() = "${def.id}@$variant"
    val inputMode: InputMode
        get() = if (def.kind == Kind.KEY) (InputMode.of(variant) ?: InputMode.NULL) else InputMode.NULL
    val variantLabel: String
        get() = when (def.kind) {
            Kind.KEY -> "Input mode: ${inputMode.label}"
            Kind.GESTURE -> "Touch keyboard: ${TouchModes.label(variant)}"
            Kind.OTHER -> ""
        }
}

object TouchModes {
    val all = listOf("off", "scroll", "cursor", "mouse")
    fun label(m: String): String = when (m) {
        "off" -> "all touch features OFF"
        "scroll" -> "Scroll Assistant ON (others off)"
        "cursor" -> "Cursor Assistant ON (others off)"
        "mouse" -> "Mouse mode ON (others off)"
        else -> m
    }
}

/** The Phase 0 test plan (PLAN.md §9.3). IDs are stable and appear in the report. */
object Catalogue {
    val SYMBOLS = listOf(
        "=", "_", "[", "]", "(", ")", "{", "}", ":", "<", ">", "'", "\"", "#",
        "+", "-", "*", "/", "%", "\\", "|", "&", "^", ";", "~", "!", "@", "`",
    )

    private val BOTH = listOf("null", "text")
    private val NULL_ONLY = listOf("null")
    private val ALL_MODES = listOf("null", "raw", "text")
    private val ONCE = listOf("-")

    val keyTests = listOf(
        TestDef("K01", "Letters a–z", "Type every letter a to z once (lowercase).",
            "Every letter arrives; keyCodes and characters.", Kind.KEY, BOTH, check = Checks::k01),
        TestDef("K02", "Shift + letters", "Type A to Z in capitals using Shift. Then tap Shift alone a few times, and try double-tapping it.",
            "Capitals; whether Shift is held, one-shot or locking.", Kind.KEY, BOTH, check = Checks::k02),
        TestDef("K03", "Alt + every key", "Hold Alt and press every key on the keyboard, row by row (letters, comma, period, space, enter). Compare with the keycap legends.",
            "Each Alt character, and whether it comes from the key map or only as text committed by the IME.", Kind.KEY, BOTH, check = Checks::k03),
        TestDef("K04", "Alt alone / double Alt", "Tap Alt alone, then press a letter. Then double-tap Alt and press a few letters. Then tap Alt again.",
            "Whether the system has its own one-shot or locking Alt.", Kind.KEY, NULL_ONLY, check = Checks::k04),
        TestDef("K05", "Tap sym alone", "Tap the sym key on its own a few times. If a symbol picker pops up, close it and add a note.",
            "Does sym reach the app? Does a system symbol picker open?", Kind.KEY, BOTH, check = Checks::k05),
        TestDef("K06", "Hold sym + each letter", "Hold sym and press every letter a to z (keep sym held, or re-press it, whichever is comfortable).",
            "Does the app get the chord (META_SYM_ON)? Does the system consume any of these?", Kind.KEY, BOTH, check = Checks::k06),
        TestDef("K07", "sym + Shift + letter", "Hold sym and Shift together and press a few letters.",
            "Whether three-key chords work.", Kind.KEY, NULL_ONLY, check = Checks::k07),
        TestDef("K08", "Space / Enter / Backspace with modifiers", "Press Space, Enter and Backspace. Then each of them with Shift held, with Alt held, and with sym held.",
            "Special-key events, including sym+Space.", Kind.KEY, NULL_ONLY, check = Checks::k08),
        TestDef("K09", "Extra keys", "Press the native comma and period keys, any Fn/Ctrl key the keyboard has, and Back. (Home and Recents will leave the app — just come back.)",
            "Which extra keys exist and reach the app.", Kind.KEY, NULL_ONLY, check = Checks::k09),
        TestDef("K10", "Hold a letter", "Hold the J key down for about 1.5 seconds, release. Repeat with K.",
            "Key-repeat events; whether a Unihertz long-press shortcut fires and the app loses focus.", Kind.KEY, NULL_ONLY, check = Checks::k10),
        TestDef("K11", "System shortcuts vs. the field", "In Unihertz settings, set a long-press shortcut on a letter (e.g. J) and enable a Universal shortcut. Come back, then hold that letter for 1.5 s and try the Universal shortcut here.",
            "Whether system shortcuts take keys before the app.", Kind.KEY, NULL_ONLY, check = Checks::k10),
        TestDef("K12", "Red side button", "Press the red side button: short press, long press, double press. If something launches, come back to this app.",
            "Does it reach the app? Which keyCode? Any system action?", Kind.KEY, NULL_ONLY, check = Checks::k12),
        TestDef("K13", "Fast typing (rollover)", "Type this line quickly, then Enter. Do it 5 times:\nfor i in range(len(nums)):",
            "Dropped or reordered keys.", Kind.KEY, BOTH, check = Checks::k13),
        TestDef("K14", "Autocorrect suppression", "Type exactly (from an empty field):\nt e h space i space space o k\n(\"teh i  ok\" — note the double space)",
            "Whether autocorrect, auto-capitalisation and \"double space → period\" are fully suppressed.", Kind.KEY, ALL_MODES, check = Checks::k14),
        TestDef("K15", "Handler order", "Press about 10 different keys, including a letter with Alt held.",
            "Confirms pre-IME interception sees each key first.", Kind.KEY, BOTH, check = Checks::k15),
        TestDef("K16", "Python symbols", "Type the symbol shown in the big box using the keyboard's native layers (Alt etc.). It advances automatically. If you can't find a symbol, tap \"Can't find it\".",
            "Keystrokes per symbol, and which symbols are missing (feeds the plan's §5.3 decision).", Kind.KEY, BOTH, Special.SYMBOLS, Checks::k16),
    )

    val gestureTests = listOf(
        TestDef("G01", "Slow horizontal swipe", "With the field focused, slowly swipe right across the keyboard surface, then left. Don't press keys.",
            "Event type (DPAD / scroll / pointer) and size.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G02", "Fast horizontal swipe", "Swipe quickly right, then quickly left, a few times.",
            "Whether speed shows up in the events (for acceleration).", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G03", "Vertical swipe", "Swipe up across the keyboard, then down. Repeat twice.",
            "Event type for vertical movement.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G04", "Diagonal swipe", "Swipe diagonally (bottom-left to top-right, then top-left to bottom-right).",
            "How the axes split; whether we need axis locking.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G05", "Shift held + swipe", "Hold Shift with one hand and swipe horizontally with the other. Keep Shift held during the swipe.",
            "Do swipes register while a key is held? (selection depends on this)", Kind.GESTURE, TouchModes.all, check = Checks::g05),
        TestDef("G06", "Alt held + swipe", "Hold Alt and swipe horizontally, then vertically.",
            "Same question, for word and block movement.", Kind.GESTURE, TouchModes.all, check = Checks::g06),
        TestDef("G07", "sym held + vertical swipe", "Hold sym and swipe vertically.",
            "Same question, for sym + swipe (move line).", Kind.GESTURE, TouchModes.all, check = Checks::g07),
        TestDef("G08", "Normal typing (stray motion)", "Type normally for about 30 seconds (anything).",
            "Stray motion events while typing (palm rejection).", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G09", "Flick up", "Flick upwards on the keyboard surface a few times.",
            "Whether the system's flick gesture reaches the app (so we can ignore it).", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G10", "Pointer capture", "The probe requests pointer capture now (see the log). Then repeat: slow horizontal, fast horizontal, vertical swipes.",
            "Whether capture works and what raw deltas look like.", Kind.GESTURE, TouchModes.all, Special.CAPTURE, Checks::gesture),
        TestDef("G11", "Light tap", "Lightly tap the keyboard surface a few times without pressing a key down.",
            "Whether a tap creates any event.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G12", "Swipe and lift quickly", "Do a few short, fast swipes, lifting your finger quickly at the end.",
            "Whether the system adds inertia after lifting.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
        TestDef("G13", "Long slide (backlight gesture)", "Do the long slide that toggles the keyboard backlight. Do it twice.",
            "Whether the system consumes it; make sure we don't react to it.", Kind.GESTURE, TouchModes.all, check = Checks::gesture),
    )

    val otherTests = listOf(
        TestDef("B01", "Backlight: window override", "The probe has set buttonBrightness = FULL on this window. Watch the keyboard backlight for 60 seconds without touching anything (timer below). Use the buttons to switch the override off/on and compare.",
            "Whether the standard per-window hint keeps the keyboard lit.", Kind.OTHER, ONCE, Special.BACKLIGHT_WINDOW, Checks::notes),
        TestDef("B02", "Backlight: settings key", "1) Tap \"Snapshot\". 2) Toggle the backlight with the long slide. 3) Tap \"Diff\" — changed setting keys appear. 4) Grant write permission, then try writing the key (value 1 / 0) and watch the light.",
            "Whether a writable setting controls the backlight.", Kind.OTHER, ONCE, Special.BACKLIGHT_SETTING, Checks::notes),
        TestDef("B03", "Backlight: leave and return", "The override is on again. Switch to another app for ~10 seconds, then come back. Then lock and unlock the phone. Note whether the backlight goes back to normal while you're away, and is forced on again when you return.",
            "Whether the backlight returns to system control and the override comes back.", Kind.OTHER, ONCE, Special.BACKLIGHT_WINDOW, Checks::notes),
        TestDef("L01", "Switch away and back", "Pointer capture is requested on this screen. Switch to another app, then come back. Then swipe the keyboard once.",
            "Whether pointer capture and focus are restored.", Kind.OTHER, ONCE, Special.CAPTURE, Checks::notes),
        TestDef("L02", "Screen off and on", "Turn the screen off with the power button, turn it on and unlock, then type a letter and swipe once.",
            "Whether focus and capture survive.", Kind.OTHER, ONCE, Special.CAPTURE, Checks::notes),
        TestDef("L03", "Change a keyboard setting", "With this screen open, pull down quick settings or open Unihertz keyboard settings, change one keyboard mode, come back and type a letter.",
            "Whether the app needs to re-detect modes (device-changed notes).", Kind.OTHER, ONCE, check = Checks::notes),
        TestDef("C01", "LeetCode login + Cloudflare", "Tap \"Open LeetCode\", sign in with your LeetCode username and password, then tap \"Run checks\" on that screen and come back.",
            "Whether OkHttp requests pass Cloudflare, or the WebView fetch() fallback is needed from day one.", Kind.OTHER, ONCE, Special.NETWORK, Checks::notes),
    )

    fun steps(): List<Step> {
        val s = mutableListOf<Step>()
        s += SetupStep(
            "intro", "Welcome",
            "This walks through every keyboard test in PLAN.md §9.3. Each screen has one test.\n\n" +
                "• Do the action described, watching the live log.\n" +
                "• Tap Pass / Fail / Unclear (and Notes if anything is odd). That saves and moves on.\n" +
                "• The AUTO line is the probe's own check where it can tell.\n" +
                "• Progress is saved; you can exit and resume any time.\n\n" +
                "Before starting: on the main menu, tick which Unihertz keyboard features are ON (\"Keyboard modes\").",
        )
        s += SetupStep(
            "setup-keys", "Setup: key tests",
            "Turn OFF Scroll Assistant, Cursor Assistant and Mouse mode for the key tests, so swipes don't interfere. " +
                "Leave everything else as you normally use it.\n\nEach key test runs in two input modes (the probe switches automatically).",
        )
        for (d in keyTests) for (v in d.variants) s += TestStep(d, v)
        for (m in TouchModes.all) {
            s += SetupStep(
                "setup-touch-$m", "Setup: ${TouchModes.label(m)}",
                "Open Unihertz settings → keyboard / touch features and set: ${TouchModes.label(m)}.\n\n" +
                    "Then come back and tap Continue. The next ${gestureTests.size} tests use this setting.",
            )
            for (d in gestureTests) s += TestStep(d, m)
        }
        s += SetupStep(
            "setup-restore", "Setup: restore",
            "Set the touch keyboard back to how you normally use it. The remaining tests cover the backlight, app lifecycle and the LeetCode network check.",
        )
        for (d in otherTests) for (v in d.variants) s += TestStep(d, v)
        s += SetupStep(
            "done", "All done",
            "Thanks! Go back to the main menu and tap \"Save report\". Then on the Mac run:\n\n  scripts/pull-report.sh\n\n" +
                "(Or tap \"Share report\" and send the two files.) Any test can be re-run from here with Back.",
        )
        return s
    }
}
