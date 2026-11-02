package dev.titan.probe

import android.text.InputType

/** What the probe's input field reports to the IME (EditorInfo.inputType). */
enum class InputMode(val key: String, val label: String, val inputType: Int) {
    NULL("null", "TYPE_NULL", InputType.TYPE_NULL),
    RAW(
        "raw", "TEXT|NO_SUGGESTIONS|VISIBLE_PASSWORD",
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    ),
    TEXT(
        "text", "normal text (caps + autocorrect)",
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
    );

    companion object {
        fun of(key: String): InputMode? = entries.firstOrNull { it.key == key }
    }
}
