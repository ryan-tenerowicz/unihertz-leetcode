package dev.titan.code.input

import android.view.KeyEvent
import dev.titan.code.TLog
import dev.titan.code.editor.EditorActions
import dev.titan.code.editor.EditorActions.Direction

/**
 * Turns hardware keys into editor actions (PLAN §5.1–§5.4).
 *
 * **M1 step 2 version:** only what the Sora + TYPE_NULL risk check needs: characters from the key
 * map (plain, Shift, Alt), Enter, Backspace, Shift+Backspace and arrow keys. The keymap engine,
 * Alt+Shift symbols, leader commands, `fn` palette and key repeat arrive in step 5.
 */
class InputRouter(private val actions: EditorActions) {

    /** Called for every key, for the debug status line. */
    var onEvent: ((String) -> Unit)? = null
    /** Called after `sym+W` with the new soft-wrap state, so it can be saved. */
    var onSoftWrapChanged: ((Boolean) -> Unit)? = null

    fun onKey(e: KeyEvent): Boolean {
        val name = KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")
        if (e.action == KeyEvent.ACTION_DOWN) {
            val msg = "$name meta=0x${Integer.toHexString(e.metaState)} uc=0x${Integer.toHexString(e.getUnicodeChar(e.metaState))} dev=${e.device?.name}"
            TLog.d("key $msg")
            onEvent?.invoke(msg)
        }

        when (e.keyCode) {
            // Owned by the system (PLAN §5.1).
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return false
            // Red button (401) is handled by MainActivity, on every pane.
            KEYCODE_RED_BUTTON -> return false
        }

        // From here on every key is consumed, DOWN and UP, so neither the IME nor Sora sees it.
        if (e.action != KeyEvent.ACTION_DOWN) return true

        val shift = e.isShiftPressed
        when (e.keyCode) {
            KeyEvent.KEYCODE_BACK -> Unit // Ignored in the Code pane (PLAN §5.1).
            KEYCODE_SWIPE_MARKER, KEYCODE_FN -> Unit // 404 swallowed; fn palette in step 6.
            KeyEvent.KEYCODE_DEL -> if (shift) actions.deleteForward() else actions.backspace()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> actions.newline()
            KeyEvent.KEYCODE_DPAD_LEFT -> actions.move(Direction.LEFT, shift)
            KeyEvent.KEYCODE_DPAD_RIGHT -> actions.move(Direction.RIGHT, shift)
            KeyEvent.KEYCODE_DPAD_UP -> actions.move(Direction.UP, shift)
            KeyEvent.KEYCODE_DPAD_DOWN -> actions.move(Direction.DOWN, shift)
            else -> {
                if (e.isSymPressed) {
                    // Leader chords: the full table arrives in step 5.
                    if (e.keyCode == KeyEvent.KEYCODE_W) onSoftWrapChanged?.invoke(actions.toggleSoftWrap())
                    return true
                }
                val uc = e.getUnicodeChar(e.metaState)
                if (uc > 0 && uc != ALT_SPACE_PRIVATE_USE && (e.metaState and KeyEvent.META_SYM_ON) == 0) {
                    actions.insert(String(Character.toChars(uc)))
                }
            }
        }
        return true
    }

    companion object {
        /** Red side button with "PTT app = Titan Code" (PLAN §4.1). */
        const val KEYCODE_RED_BUTTON = 401
        /** `fn` key, FUNC3 (PLAN §5.1). */
        const val KEYCODE_FN = 403
        /** Sent after every keyboard-surface swipe; meaningless to us (PLAN §5.1). */
        const val KEYCODE_SWIPE_MARKER = 404
        /** Alt+Space produces this private-use character; dropped (K03). */
        const val ALT_SPACE_PRIVATE_USE = 0xEF01
    }
}
