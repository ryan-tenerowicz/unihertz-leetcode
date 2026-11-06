package dev.titan.code.editor

/**
 * Every change the keyboard makes to the buffer goes through this interface (PLAN §3). It knows
 * nothing about keys, and the InputRouter knows nothing about Sora, so the editor can be swapped
 * (CodeMirror fallback) without touching the router.
 */
interface EditorActions {
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /** Type text at the cursor with the Python rules (pairing, type-over), replacing any selection. */
    fun insert(text: String)
    /** Insert text as-is (paste-like): no pairing. */
    fun insertPlain(text: String)
    /** Delete the selection, an empty pair, back to the previous indent stop, or one character. */
    fun backspace()
    /** Delete the selection, or the character after the cursor. */
    fun deleteForward()
    /** Smart Enter (PLAN §5.5). */
    fun newline()
    /** `sym+Space`: one indent level at the cursor, or indent the selected lines. */
    fun indent()
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

    fun undo()
    fun redo()

    fun selectLine()
    fun deleteLine()
    fun duplicateLine()
    fun toggleComment()
    fun copy(): Clip
    fun cut(): Clip
    fun paste(clip: Clip)

    /** Spaces per indent level: 4 (default) or 2, from Settings (PLAN §4.2). */
    var indentWidth: Int

    /** Completion (M5): the identifier before the cursor, replacing it, and the buffer's words. */
    fun wordPrefix(): String = ""
    fun complete(prefixLen: Int, text: String) {}
    fun words(): Set<String> = emptySet()
    /** Rev 38: the spot for type-aware completion, and the whole buffer to analyse. */
    fun completionSite(): CompletionSite? = null
    fun bufferText(): String = ""

    /** Turn soft wrap on or off; returns the new state. */
    fun toggleSoftWrap(): Boolean
}
