package dev.titan.code.editor

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.titan.code.TLog
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.subscribeAlways

/**
 * Sora's CodeEditor with all of its own text input switched off (PLAN §3, §5.1):
 * - The IME gets `TYPE_NULL` and a dummy connection, so it never composes or corrects (K14).
 * - Every hardware key goes to [keyHandler] from `dispatchKeyEventPreIme()`, before the IME (K15).
 * - Sora's own key handler (`onKeyDown`/`onKeyUp`) is never reached. If a key does leak through,
 *   it is logged and swallowed, so there is exactly one path that inserts text.
 * - Sora's auto-indent and symbol-pair completion are off; [SoraActions] does that work itself.
 */
class TitanEditor(context: Context) : CodeEditor(context) {

    /** Sees every key first. Returns true if it consumed the key. Set by the InputRouter. */
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    /** True while [SoraActions] is editing; any edit outside that is "foreign" and gets logged. */
    internal var ownEdit = false
    var foreignEdits = 0
        private set

    val actions: EditorActions = SoraActions(this)

    init {
        typefaceText = Typeface.MONOSPACE
        typefaceLineNumber = Typeface.MONOSPACE
        setTextSize(12f)
        props.symbolPairAutoCompletion = false
        props.autoIndent = false
        props.deleteEmptyLineFast = false
        props.deleteMultiSpaces = 1
        props.formatPastedText = false
        props.disallowSuggestions = true
        setSoftKeyboardEnabled(false)
        inputType = InputType.TYPE_NULL
        isFocusable = true
        isFocusableInTouchMode = true

        subscribeAlways<ContentChangeEvent> { e ->
            if (!ownEdit && e.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && !e.isCausedByUndoManager) {
                foreignEdits++
                TLog.w("FOREIGN EDIT #$foreignEdits action=${e.action} text='${e.changedText}' at ${e.changeStart.line}:${e.changeStart.column}")
            }
        }
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (keyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.initialSelStart = cursor.left
        outAttrs.initialSelEnd = cursor.right
        // Dummy mode: anything an IME commits arrives as key events, which go back through
        // dispatchKeyEventPreIme. With TYPE_NULL nothing was committed in Phase 0 (K14).
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                TLog.w("IME commitText '$text' (unexpected in TYPE_NULL)")
                return super.commitText(text, newCursorPosition)
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    /** The end of the selection that moves when it's extended (the other end is the anchor). */
    internal fun movingEnd(): CharPosition = selectingTarget

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = leaked(event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = leaked(event)
    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = leaked(event)

    private fun leaked(event: KeyEvent): Boolean {
        if (isSystemKey(event.keyCode)) return false
        TLog.w("LEAKED to view: ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action}")
        return true
    }

    private fun isSystemKey(code: Int) = code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_APP_SWITCH ||
        code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
        code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_POWER
}

/** [EditorActions] on top of Sora's text model. Bypasses Sora's auto-indent and pairing. */
private class SoraActions(private val ed: TitanEditor) : EditorActions {

    private inline fun edit(block: () -> Unit) {
        ed.ownEdit = true
        try { block() } finally { ed.ownEdit = false }
        ed.ensureSelectionVisible()
    }

    override fun insert(text: String) = edit {
        ed.commitText(text, /* applyAutoIndent = */ false, /* applySymbolCompletion = */ false)
    }

    override fun backspace() = edit { ed.deleteText() }

    override fun deleteForward() = edit {
        val c = ed.cursor
        val t = ed.text
        if (c.isSelected) {
            t.delete(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn)
        } else if (c.leftColumn < t.getColumnCount(c.leftLine)) {
            t.delete(c.leftLine, c.leftColumn, c.leftLine, c.leftColumn + 1)
        } else if (c.leftLine + 1 < t.lineCount) {
            t.delete(c.leftLine, c.leftColumn, c.leftLine + 1, 0)
        }
    }

    override fun newline() = insert("\n")

    override fun move(direction: EditorActions.Direction, extend: Boolean, withinLine: Boolean) {
        if (withinLine && atLineEdge(direction, extend)) return
        val m = when (direction) {
            EditorActions.Direction.LEFT -> SelectionMovement.LEFT
            EditorActions.Direction.RIGHT -> SelectionMovement.RIGHT
            EditorActions.Direction.UP -> SelectionMovement.UP
            EditorActions.Direction.DOWN -> SelectionMovement.DOWN
        }
        if (extend) ed.extendSelection(m) else ed.moveSelection(m)
        ed.ensureSelectionVisible()
    }

    private fun scrollTo(x: Int, y: Int) {
        val s = ed.scroller
        s.startScroll(s.currX, s.currY, x - s.currX, y - s.currY, 0)
        ed.invalidate()
    }

    /** True if a horizontal move would leave the current line. */
    private fun atLineEdge(direction: EditorActions.Direction, extend: Boolean): Boolean {
        val c = ed.cursor
        // Without extend, a selection just collapses to one of its ends: never crosses a line.
        if (!extend && c.isSelected) return false
        val pos = if (extend) ed.movingEnd() else c.left()
        return when (direction) {
            EditorActions.Direction.LEFT -> pos.column == 0
            EditorActions.Direction.RIGHT -> pos.column >= ed.text.getColumnCount(pos.line)
            else -> false
        }
    }

    override fun saveSelection(): EditorActions.Mark {
        val c = ed.cursor
        return EditorActions.Mark(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn, ed.scroller.currX, ed.scroller.currY)
    }

    override fun restoreSelection(mark: EditorActions.Mark) {
        if (mark.startLine == mark.endLine && mark.startColumn == mark.endColumn) {
            ed.setSelection(mark.startLine, mark.startColumn, false)
        } else {
            ed.setSelectionRegion(mark.startLine, mark.startColumn, mark.endLine, mark.endColumn, false)
        }
        scrollTo(mark.scrollX, mark.scrollY)
    }

    override fun toggleSoftWrap(): Boolean {
        ed.setWordwrap(!ed.isWordwrap, /* antiWordBreaking = */ true)
        ed.ensureSelectionVisible()
        return ed.isWordwrap
    }
}
