package dev.titan.code.editor

import android.content.Context
import android.graphics.Canvas
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.titan.code.TLog
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.UndoManager
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
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

    /** Fading trail behind cursor moves (rev 15). Off while loading text or editing. */
    private val trail = CursorTrail(this) { colorScheme.getColor(EditorColorScheme.SELECTION_INSERT) }
    var trailEnabled = true
        set(value) { field = value; if (!value) trail.clear() }
    private var quiet = false

    /** Indent guides in both wrap modes (rev 18); Sora's own in-text block lines are made invisible. */
    private val guides = IndentGuides(this, { actions.indentWidth }, 0xFF2A2A2A.toInt(), 0xFF5A5A5A.toInt())

    init {
        CodeTheme.apply(this)
        setTextSize(12f)
        props.symbolPairAutoCompletion = false
        props.autoIndent = false
        props.deleteEmptyLineFast = false
        props.deleteMultiSpaces = 1
        props.formatPastedText = false
        props.disallowSuggestions = true
        // Undo groups a run of typing; a pause of about 1 s starts a new step (PLAN §5.5).
        UndoManager.setMergeTimeLimit(1000)
        setSoftKeyboardEnabled(false)
        // Sora's floating copy/paste bar would edit outside EditorActions; sym+C/X/V replace it.
        getComponent(EditorTextActionWindow::class.java).isEnabled = false
        inputType = InputType.TYPE_NULL
        isFocusable = true
        isFocusableInTouchMode = true

        subscribeAlways<SelectionChangeEvent> { e ->
            if (!trailEnabled || quiet || ownEdit || e.cause == SelectionChangeEvent.CAUSE_TEXT_MODIFICATION) return@subscribeAlways
            // Follow the end that moved (with no selection, both ends are the cursor).
            val oldLeft = e.oldLeft ?: return@subscribeAlways
            val oldRight = e.oldRight ?: return@subscribeAlways
            val leftMoved = e.left.index != oldLeft.index
            val old = if (leftMoved) oldLeft else oldRight
            val new = if (leftMoved) e.left else e.right
            trail.moved(old.line, old.column, new.line, new.column)
        }
        subscribeAlways<ContentChangeEvent> { e ->
            if (!ownEdit && e.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && !e.isCausedByUndoManager) {
                foreignEdits++
                TLog.w("FOREIGN EDIT #$foreignEdits action=${e.action} text='${e.changedText}' at ${e.changeStart.line}:${e.changeStart.column}")
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        guides.draw(canvas)
        trail.draw(canvas)
    }

    private inline fun quietly(block: () -> Unit) {
        quiet = true
        trail.clear()
        try { block() } finally { quiet = false }
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

    /** Replace the whole buffer without undo history (opening a draft or sample). */
    fun load(text: String, line: Int, column: Int) = quietly {
        setText(text)
        val l = line.coerceIn(0, this.text.lineCount - 1)
        setSelection(l, column.coerceIn(0, this.text.getColumnCount(l)))
    }

    /** Replace the whole buffer as one undoable edit (`sym+N`, reset to starter). */
    fun replaceAll(text: String, line: Int, column: Int) = quietly {
        ownEdit = true
        try {
            val t = this.text
            val last = t.lineCount - 1
            t.replace(0, 0, last, t.getColumnCount(last), text)
        } finally { ownEdit = false }
        val l = line.coerceIn(0, this.text.lineCount - 1)
        setSelection(l, column.coerceIn(0, this.text.getColumnCount(l)))
    }

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

/**
 * [EditorActions] on top of Sora's text model. The Python rules live in [PyEdit]; this class is
 * its [TextBuffer] plus cursor movement, undo and wrap. Bypasses Sora's auto-indent and pairing.
 */
private class SoraActions(private val ed: TitanEditor) : EditorActions, TextBuffer {

    private val py = PyEdit(this)

    override var indentWidth: Int
        get() = py.indentWidth
        set(value) { py.indentWidth = value }

    private inline fun <T> edit(block: () -> T): T {
        ed.ownEdit = true
        try { return block() } finally {
            ed.ownEdit = false
            ed.ensureSelectionVisible()
        }
    }

    // ---- TextBuffer -------------------------------------------------------------------

    override val lineCount get() = ed.text.lineCount
    override fun line(i: Int): String = ed.text.getLineString(i)
    override val selStart get() = ed.cursor.let { Pos(it.leftLine, it.leftColumn) }
    override val selEnd get() = ed.cursor.let { Pos(it.rightLine, it.rightColumn) }

    override fun replace(start: Pos, end: Pos, text: String) {
        if (start == end && text.isEmpty()) return
        if (start == end) ed.text.insert(start.line, start.col, text)
        else ed.text.replace(start.line, start.col, end.line, end.col, text)
    }

    override fun select(start: Pos, end: Pos) {
        if (start == end) ed.setSelection(start.line, start.col, false)
        else ed.setSelectionRegion(start.line, start.col, end.line, end.col, false)
    }

    override fun group(block: () -> Unit) {
        val t = ed.text
        t.beginBatchEdit()
        try { block() } finally { t.endBatchEdit() }
    }

    // ---- EditorActions ----------------------------------------------------------------

    override fun insert(text: String) = edit { py.type(text) }
    override fun insertPlain(text: String) = edit { py.insertPlain(text) }
    override fun backspace() = edit { py.backspace() }
    override fun deleteForward() = edit { py.deleteForward() }
    override fun newline() = edit { py.newline() }
    override fun indent() = edit { py.indent() }
    override fun wordPrefix(): String = py.wordPrefix()
    override fun complete(prefixLen: Int, text: String) = edit { py.complete(prefixLen, text) }
    override fun words(): Set<String> = py.words()
    override fun completionSite(): CompletionSite? = py.completionSite()
    override fun bufferText(): String = py.allText()
    override fun selectLine() = edit { py.selectLine() }
    override fun deleteLine() = edit { py.deleteLine() }
    override fun duplicateLine() = edit { py.duplicateLine() }
    override fun toggleComment() = edit { py.toggleComment() }
    override fun copy(): Clip = py.copy()
    override fun cut(): Clip = edit { py.cut() }
    override fun paste(clip: Clip) = edit { py.paste(clip) }

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
        select(Pos(mark.startLine, mark.startColumn), Pos(mark.endLine, mark.endColumn))
        scrollTo(mark.scrollX, mark.scrollY)
    }

    override fun undo() = edit { ed.undo() }
    override fun redo() = edit { ed.redo() }

    override fun toggleSoftWrap(): Boolean {
        ed.setWordwrap(!ed.isWordwrap, /* antiWordBreaking = */ true)
        ed.ensureSelectionVisible()
        return ed.isWordwrap
    }
}
