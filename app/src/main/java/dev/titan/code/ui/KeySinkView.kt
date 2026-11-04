package dev.titan.code.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Holds focus on the Problem, Results and Comments panes so keys reach the app at pre-IME, the
 * same as in the Code pane. Without a focused view, the keyboard app sees `sym` first and opens
 * the system input-method picker (found in M1 step 4). Not a text editor, so no IME binds to it.
 */
@SuppressLint("ViewConstructor")
class KeySinkView(context: Context, private val onKey: (KeyEvent) -> Boolean) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean = onKey(event) || super.dispatchKeyEventPreIme(event)
    override fun onCheckIsTextEditor() = false
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? = null
}
