package dev.titan.probe

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

/** Human-readable names for input constants. */
object Names {
    private val SOURCES = listOf(
        InputDevice.SOURCE_KEYBOARD to "KEYBOARD",
        InputDevice.SOURCE_DPAD to "DPAD",
        InputDevice.SOURCE_GAMEPAD to "GAMEPAD",
        InputDevice.SOURCE_TOUCHSCREEN to "TOUCHSCREEN",
        InputDevice.SOURCE_MOUSE to "MOUSE",
        InputDevice.SOURCE_STYLUS to "STYLUS",
        InputDevice.SOURCE_TRACKBALL to "TRACKBALL",
        InputDevice.SOURCE_MOUSE_RELATIVE to "MOUSE_RELATIVE",
        InputDevice.SOURCE_TOUCHPAD to "TOUCHPAD",
        InputDevice.SOURCE_TOUCH_NAVIGATION to "TOUCH_NAVIGATION",
        InputDevice.SOURCE_ROTARY_ENCODER to "ROTARY_ENCODER",
        InputDevice.SOURCE_JOYSTICK to "JOYSTICK",
        InputDevice.SOURCE_HDMI to "HDMI",
        InputDevice.SOURCE_SENSOR to "SENSOR",
    )

    private val METAS = listOf(
        KeyEvent.META_SHIFT_ON to "SHIFT",
        KeyEvent.META_ALT_ON to "ALT",
        KeyEvent.META_SYM_ON to "SYM",
        KeyEvent.META_CTRL_ON to "CTRL",
        KeyEvent.META_META_ON to "META",
        KeyEvent.META_FUNCTION_ON to "FN",
        KeyEvent.META_CAPS_LOCK_ON to "CAPS",
        KeyEvent.META_NUM_LOCK_ON to "NUM",
        KeyEvent.META_SCROLL_LOCK_ON to "SCROLL",
    )

    fun source(s: Int): String {
        val names = SOURCES.filter { (bit, _) -> bit != 0 && (s and bit) == bit }.map { it.second }
        return if (names.isEmpty()) "0x" + Integer.toHexString(s) else names.joinToString("|")
    }

    fun meta(m: Int): String = METAS.filter { (m and it.first) != 0 }.joinToString("+") { it.second }

    fun keyAction(a: Int): String = when (a) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        else -> "MULTIPLE"
    }

    fun key(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    fun tool(t: Int): String = when (t) {
        MotionEvent.TOOL_TYPE_FINGER -> "finger"
        MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
        MotionEvent.TOOL_TYPE_STYLUS -> "stylus"
        MotionEvent.TOOL_TYPE_ERASER -> "eraser"
        5 -> "palm" // MotionEvent.TOOL_TYPE_PALM (hidden API)
        else -> "unknown"
    }

    fun classification(c: Int): String = when (c) {
        MotionEvent.CLASSIFICATION_NONE -> "none"
        MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE -> "ambiguous"
        MotionEvent.CLASSIFICATION_DEEP_PRESS -> "deep_press"
        MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE -> "two_finger_swipe"
        4 -> "multi_finger_swipe" // MotionEvent.CLASSIFICATION_MULTI_FINGER_SWIPE (hidden API)
        MotionEvent.CLASSIFICATION_PINCH -> "pinch"
        else -> "c$c"
    }

    fun device(id: Int): String = InputDevice.getDevice(id)?.name ?: "virtual($id)"

    /** A unicode value from KeyCharacterMap / getUnicodeChar as a printable string ("" for none). */
    fun uni(c: Int): String {
        if (c == 0) return ""
        return if ((c and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            val base = c and KeyCharacterMap.COMBINING_ACCENT_MASK
            if (base > 0) "◌" + String(Character.toChars(base)) else ""
        } else if (c > 0) {
            String(Character.toChars(c))
        } else ""
    }

    fun printable(s: String): String =
        s.replace("\n", "⏎").replace(" ", "␣").replace("\t", "⇥")
}
