package dev.titan.code.editor

/**
 * Every change the keyboard makes to the buffer goes through this interface (PLAN §3). It knows
 * nothing about keys, and the InputRouter knows nothing about Sora, so the editor can be swapped
 * (CodeMirror fallback) without touching the router.
 */
interface EditorActions {
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /** Insert text at the cursor, replacing any selection. */
    fun insert(text: String)
    /** Delete the selection, or the character before the cursor. */
    fun backspace()
    /** Delete the selection, or the character after the cursor. */
    fun deleteForward()
    /** Split the line at the cursor. */
    fun newline()
    /**
     * Move the cursor one step; with [extend], grow the selection instead. With [withinLine],
     * LEFT/RIGHT stop at the start or end of the line instead of crossing to the next one
     * (keyboard-surface swipes, PLAN §5.6).
     */
    fun move(direction: Direction, extend: Boolean, withinLine: Boolean = false)
    /** Where the cursor, selection and scroll are now, to put them back with [restoreSelection]. */
    fun saveSelection(): Mark
    fun restoreSelection(mark: Mark)
    data class Mark(
        val startLine: Int, val startColumn: Int, val endLine: Int, val endColumn: Int,
        val scrollX: Int, val scrollY: Int,
    )

    /** Turn soft wrap on or off; returns the new state. */
    fun toggleSoftWrap(): Boolean
}
