package dev.titan.probe

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

/**
 * The text field under test. It is a TextView that acts as its own text editor, so we control
 * exactly what the IME is told (inputType) and log every stage a key passes through:
 *   preIme   – dispatchKeyEventPreIme (before the IME sees the key)
 *   activity – Activity.dispatchKeyEvent (logged by ProbeActivity)
 *   view     – onKeyDown / onKeyUp (key reached the view; the IME did not consume it)
 * Text arriving through the InputConnection (IME commits) is logged as kind "ic".
 */
class ProbeInputView(context: Context) : TextView(context) {

    val editable: Editable = SpannableStringBuilder()
    var consumeBack = true
    /** Called with each piece of text inserted (from key events or IME commits). */
    var onInsert: ((String) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    var mode: InputMode = InputMode.NULL
        set(value) {
            field = value
            imm().restartInput(this)
            EventLog.note("mode", "input mode -> ${value.label}")
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        typeface = Typeface.MONOSPACE
        textSize = 16f
        minLines = 3
        maxLines = 6
        val p = (12 * resources.displayMetrics.density).toInt()
        setPadding(p, p, p, p)
        setBackgroundColor(0xFF1E1E1E.toInt())
        setTextColor(0xFFE8E8E8.toInt())
        Selection.setSelection(editable, 0)
        render()
    }

    private fun imm(): InputMethodManager = context.getSystemService(InputMethodManager::class.java)

    private fun cursor(): Int {
        val c = Selection.getSelectionEnd(editable)
        return if (c < 0 || c > editable.length) editable.length else c
    }

    fun render() {
        val s = editable.toString()
        val c = cursor()
        text = s.substring(0, c) + "▌" + s.substring(c)
        if (mode != InputMode.NULL) {
            imm().updateSelection(
                this, c, c,
                BaseInputConnection.getComposingSpanStart(editable),
                BaseInputConnection.getComposingSpanEnd(editable),
            )
        }
        onChanged?.invoke()
    }

    fun insert(t: CharSequence) {
        val c = cursor()
        editable.insert(c, t)
        Selection.setSelection(editable, c + t.length)
        render()
        notifyInsert(t.toString())
    }

    fun notifyInsert(s: String) {
        onInsert?.invoke(s)
    }

    private fun backspace() {
        val c = cursor()
        if (c > 0) {
            editable.delete(c - 1, c)
            Selection.setSelection(editable, c - 1)
        }
        render()
    }

    fun clearText() {
        editable.clear()
        Selection.setSelection(editable, 0)
        imm().restartInput(this)
        render()
    }

    // ---- key stages -------------------------------------------------------------------------

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        EventLog.key("preIme", event)
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        EventLog.key("view", event)
        if (keyCode == KeyEvent.KEYCODE_BACK && !consumeBack) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> backspace()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> insert("\n")
            else -> {
                val uc = event.getUnicodeChar(event.metaState)
                if (uc > 0) insert(String(Character.toChars(uc)))
            }
        }
        return true // consume everything (incl. DPAD) so focus never moves away from the field
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        EventLog.key("view", event)
        if (keyCode == KeyEvent.KEYCODE_BACK && !consumeBack) return super.onKeyUp(keyCode, event)
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        EventLog.key("view", event)
        @Suppress("DEPRECATION")
        val chars = event.characters
        if (chars != null) insert(chars)
        return true
    }

    // ---- pointer capture --------------------------------------------------------------------

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        EventLog.note("capture", if (hasCapture) "pointer capture GRANTED" else "pointer capture LOST/RELEASED")
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        EventLog.motion("captured", event)
        return true
    }

    // ---- IME connection ---------------------------------------------------------------------

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = mode.inputType
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        val c = cursor()
        outAttrs.initialSelStart = c
        outAttrs.initialSelEnd = c
        EventLog.note("ic", "onCreateInputConnection inputType=0x${Integer.toHexString(mode.inputType)} (${mode.label})")
        return ProbeConnection(this, mode != InputMode.NULL)
    }

    /**
     * full=false (TYPE_NULL): BaseInputConnection's "dummy" mode turns any committed text into key
     * events, so text still reaches onKeyDown. full=true: commits edit our buffer directly.
     */
    private class ProbeConnection(private val v: ProbeInputView, private val full: Boolean) :
        BaseInputConnection(v, full) {

        override fun getEditable(): Editable? = if (full) v.editable else super.getEditable()

        private fun after() { if (full) v.render() }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            EventLog.ic("commitText", "'${Names.printable(text.toString())}' pos=$newCursorPosition")
            val r = super.commitText(text, newCursorPosition)
            if (full) {
                v.render()
                if (text.isNotEmpty()) v.notifyInsert(text.toString())
            }
            return r
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            EventLog.ic("setComposingText", "'${Names.printable(text.toString())}'")
            val r = super.setComposingText(text, newCursorPosition); after(); return r
        }

        override fun finishComposingText(): Boolean {
            EventLog.ic("finishComposingText", "")
            val r = super.finishComposingText(); after(); return r
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            EventLog.ic("deleteSurroundingText", "before=$beforeLength after=$afterLength")
            val r = super.deleteSurroundingText(beforeLength, afterLength); after(); return r
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            EventLog.ic("sendKeyEvent", "${Names.keyAction(event.action)} ${Names.key(event.keyCode)}")
            return super.sendKeyEvent(event)
        }

        override fun commitCorrection(correctionInfo: CorrectionInfo): Boolean {
            EventLog.ic("commitCorrection", correctionInfo.toString())
            return super.commitCorrection(correctionInfo)
        }

        override fun commitCompletion(text: CompletionInfo): Boolean {
            EventLog.ic("commitCompletion", text.toString())
            return super.commitCompletion(text)
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            EventLog.ic("performEditorAction", "action=$actionCode")
            return super.performEditorAction(actionCode)
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            EventLog.ic("setComposingRegion", "$start..$end")
            val r = super.setComposingRegion(start, end); after(); return r
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            EventLog.ic("setSelection", "$start..$end")
            val r = super.setSelection(start, end); after(); return r
        }

        override fun clearMetaKeyStates(states: Int): Boolean {
            EventLog.ic("clearMetaKeyStates", "0x${Integer.toHexString(states)}")
            return super.clearMetaKeyStates(states)
        }
    }
}
