package dev.titan.code

import android.content.Context

/** Small per-device settings that survive restarts (PLAN §4.2). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("titan-code", Context.MODE_PRIVATE)

    /** Soft wrap: on by default (PLAN §4.2, revision 7). Toggled with `sym+W`. */
    var softWrap: Boolean
        get() = sp.getBoolean(KEY_SOFT_WRAP, true)
        set(value) { sp.edit().putBoolean(KEY_SOFT_WRAP, value).apply() }

    /** Editor font size in sp, 8–24, changed with `sym+I` / `sym+O` (PLAN §4.2). */
    var fontSize: Int
        get() = sp.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        set(value) { sp.edit().putInt(KEY_FONT_SIZE, value).apply() }

    /**
     * Text size of the Problem and Comments panes (rev 27), as a percentage: the statement and
     * comment pages, the problem list rows and the solution list. `sym+I` / `sym+O` on those panes.
     */
    var readingZoom: Int
        get() = sp.getInt("readingZoom", 100).coerceIn(READING_ZOOMS.first(), READING_ZOOMS.last())
        set(value) { sp.edit().putInt("readingZoom", value).apply() }

    /** The sample problem that's open (M1 picker, PLAN §12). */
    var activeSample: String?
        get() = sp.getString(KEY_ACTIVE_SAMPLE, null)
        set(value) { sp.edit().putString(KEY_ACTIVE_SAMPLE, value).apply() }

    /** The open problem's slug (M2; falls back to the M1 sample). */
    var activeProblem: String?
        get() = sp.getString(KEY_ACTIVE_PROBLEM, null) ?: activeSample
        set(value) { sp.edit().putString(KEY_ACTIVE_PROBLEM, value).apply() }

    /** Spaces per indent level, 4 or 2 (PLAN §4.2, Settings). */
    var indentWidth: Int
        get() = if (sp.getInt(KEY_INDENT, 4) == 2) 2 else 4
        set(value) { sp.edit().putInt(KEY_INDENT, value).apply() }

    /** Keyboard-surface swipe speeds (PLAN §5.6, Settings). Owner's M1 values are the defaults. */
    var pxPerChar: Int
        get() = sp.getInt(KEY_PX_CHAR, 70).coerceIn(MIN_SWIPE_PX, MAX_SWIPE_PX)
        set(value) { sp.edit().putInt(KEY_PX_CHAR, value).apply() }

    var pxPerLine: Int
        get() = sp.getInt(KEY_PX_LINE, 90).coerceIn(MIN_SWIPE_PX, MAX_SWIPE_PX)
        set(value) { sp.edit().putInt(KEY_PX_LINE, value).apply() }

    /** How many of the next problems in the list to save when one is opened (PLAN §8.4). */
    var prefetchCount: Int
        get() = sp.getInt(KEY_PREFETCH, 5).coerceIn(0, 20)
        set(value) { sp.edit().putInt(KEY_PREFETCH, value).apply() }

    /** Offline packs and the weekly refresh may use mobile data (default: Wi-Fi only, PLAN §8.4). */
    var packsOnMobile: Boolean
        get() = sp.getBoolean(KEY_PACKS_MOBILE, false)
        set(value) { sp.edit().putBoolean(KEY_PACKS_MOBILE, value).apply() }

    /** The platform whose list the Problem pane shows (rev 13): "leetcode", "neetcode" or "codeforces". */
    var listPlatform: String
        get() = sp.getString("listPlatform", "leetcode") ?: "leetcode"
        set(value) { sp.edit().putString("listPlatform", value).apply() }

    /** Codeforces submit language (rev 13, *agent decision*: PyPy 3 64-bit by default, the fastest Python there). */
    /** Sideways swipe acceleration (rev 16): "off", "gentle" or "normal". */
    var swipeAccel: String
        get() = sp.getString("swipeAccel", "normal") ?: "normal"
        set(value) { sp.edit().putString("swipeAccel", value).apply() }

    /** Haptic pulse per swiped character or line (rev 18): a [dev.titan.code.input.SwipeHaptics.Level] name. */
    var swipeHaptics: String
        get() = sp.getString("swipeHapticsLevel", "NORMAL") ?: "NORMAL"
        set(value) { sp.edit().putString("swipeHapticsLevel", value).apply() }

    var cfLanguage: String
        get() = sp.getString("cfLanguage", "PYPY3_64") ?: "PYPY3_64"
        set(value) { sp.edit().putString("cfLanguage", value).apply() }

    /** The practice timer in the status line (rev 24). */
    var practiceTimer: Boolean
        get() = sp.getBoolean("practiceTimer", true)
        set(value) { sp.edit().putBoolean("practiceTimer", value).apply() }


    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) { sp.edit().putBoolean(KEY_ONBOARDING, value).apply() }

    companion object {
        const val MIN_SWIPE_PX = 30
        const val MAX_SWIPE_PX = 200
        const val DEFAULT_FONT_SIZE = 12
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 24
        /** Reading text size steps, in percent (rev 27). */
        val READING_ZOOMS = listOf(70, 80, 90, 100, 110, 120, 135, 150, 175, 200)
        private const val KEY_SOFT_WRAP = "softWrap"
        private const val KEY_FONT_SIZE = "fontSize"
        private const val KEY_ACTIVE_SAMPLE = "activeSample"
        private const val KEY_ACTIVE_PROBLEM = "activeProblem"
        private const val KEY_INDENT = "indentWidth"
        private const val KEY_PX_CHAR = "pxPerChar"
        private const val KEY_PX_LINE = "pxPerLine"
        private const val KEY_PREFETCH = "prefetchCount"
        private const val KEY_ONBOARDING = "onboardingDone"
        private const val KEY_PACKS_MOBILE = "packsOnMobile"
    }
}
