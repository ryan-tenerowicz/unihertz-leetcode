package dev.titan.code.input

import android.view.KeyEvent

/**
 * The leader table (PLAN §5.4): hold `sym` and press one key. Each command has exactly one key,
 * matched by keycode, and no key has two commands (checked by a unit test). The cheat sheet
 * (`sym+H`) is generated from this table.
 *
 * [editsText] commands only work in the Code pane. The others also work on the Problem, Results
 * and Comments panes (PLAN §5.1).
 */
enum class Leader(val keyCode: Int, val keyLabel: String, val label: String, val editsText: Boolean) {
    RUN_LOCAL(KeyEvent.KEYCODE_R, "R", "Run locally", false),
    RUN_REMOTE(KeyEvent.KEYCODE_E, "E", "Run on the site (LeetCode, NeetCode)", false),
    SUBMIT(KeyEvent.KEYCODE_S, "S", "Submit (press twice)", false),
    RESULTS(KeyEvent.KEYCODE_T, "T", "Open or close Results", false),
    PROBLEMS(KeyEvent.KEYCODE_P, "P", "Problem list", false),
    UNDO(KeyEvent.KEYCODE_U, "U", "Undo", true),
    REDO(KeyEvent.KEYCODE_Y, "Y", "Redo", true),
    COPY(KeyEvent.KEYCODE_C, "C", "Copy (line if nothing selected)", true),
    CUT(KeyEvent.KEYCODE_X, "X", "Cut (line if nothing selected)", true),
    PASTE(KeyEvent.KEYCODE_V, "V", "Paste", true),
    SELECT_LINE(KeyEvent.KEYCODE_L, "L", "Select line (repeat to extend)", true),
    DELETE_LINE(KeyEvent.KEYCODE_D, "D", "Delete line", true),
    DUPLICATE_LINE(KeyEvent.KEYCODE_J, "J", "Duplicate line", true),
    TOGGLE_COMMENT(KeyEvent.KEYCODE_K, "K", "Toggle comment", true),
    FONT_UP(KeyEvent.KEYCODE_I, "I", "Text size up (the editor, or the Problem and Comments panes)", false),
    FONT_DOWN(KeyEvent.KEYCODE_O, "O", "Text size down (the editor, or the Problem and Comments panes)", false),
    SOFT_WRAP(KeyEvent.KEYCODE_W, "W", "Toggle soft wrap", true),
    CHEAT_SHEET(KeyEvent.KEYCODE_H, "H", "Cheat sheet", false),
    RESET(KeyEvent.KEYCODE_N, "N", "Reset to starter code (press twice)", true),
    INDENT(KeyEvent.KEYCODE_SPACE, "Space", "Accept the completion, or indent", true);

    companion object {
        private val byKey = entries.associateBy { it.keyCode }
        fun forKey(keyCode: Int): Leader? = byKey[keyCode]
    }
}
