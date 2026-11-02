package dev.titan.code

import android.content.Context

/** Small per-device settings that survive restarts (PLAN §4.2). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("titan-code", Context.MODE_PRIVATE)

    /** Soft wrap: on by default (PLAN §4.2, revision 7). Toggled with `sym+W`. */
    var softWrap: Boolean
        get() = sp.getBoolean(KEY_SOFT_WRAP, true)
        set(value) { sp.edit().putBoolean(KEY_SOFT_WRAP, value).apply() }

    private companion object {
        const val KEY_SOFT_WRAP = "softWrap"
    }
}
