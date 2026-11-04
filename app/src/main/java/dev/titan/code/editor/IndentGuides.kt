package dev.titan.code.editor

import android.graphics.Canvas
import android.graphics.Paint
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Indent guides (owner request, rev 18): a thin vertical line at each indent level inside a
 * line's leading whitespace, with the guide of the block holding the cursor brighter. Sora draws
 * its own block lines only with soft wrap off, so this draws them in both modes.
 *
 * - Drawn only on a line's first screen row, inside its indentation, so they never cross text
 *   (continuation rows start at the left edge, §4.2).
 * - A blank line takes the smaller indent of its nearest non-blank neighbours, so guides run
 *   through the blank lines inside a block.
 */
internal class IndentGuides(
    private val ed: CodeEditor,
    private val indentWidth: () -> Int,
    private val dim: Int,
    private val bright: Int,
) {
    private val paint = Paint().apply { strokeWidth = 1f * ed.resources.displayMetrics.density }

    fun draw(canvas: Canvas) {
        val text = ed.text
        val layout = ed.layout ?: return
        val rowH = ed.rowHeight
        if (rowH <= 0 || text.lineCount == 0) return
        val w = indentWidth().coerceAtLeast(1)
        val firstRow = (ed.offsetY / rowH).coerceAtLeast(0)
        val lastRow = ((ed.offsetY + ed.height) / rowH).coerceAtMost(layout.rowCount - 1)

        val cursorLine = ed.cursor.leftLine
        val active = activeBlock(cursorLine, w)
        val cache = HashMap<Int, Int>()
        fun indent(line: Int) = cache.getOrPut(line) { effectiveIndent(line) }

        for (row in firstRow..lastRow) {
            val r = runCatching { layout.getRowAt(row) }.getOrNull() ?: continue
            if (!r.isLeadingRow) continue
            val ind = indent(r.lineIndex)
            if (ind < w) continue
            val top = (row * rowH - ed.offsetY).toFloat()
            val x0 = ed.getCharOffsetX(r.lineIndex, 0)
            val sw = spaceWidth()
            var col = 0
            while (col < ind) {
                val x = x0 + col * sw + paint.strokeWidth
                val isActive = active != null && col == active.first && r.lineIndex in active.second
                paint.color = if (isActive) bright else dim
                canvas.drawLine(x, top, x, top + rowH, paint)
                col += w
            }
        }
    }

    /** One column's width (monospace), from the editor's own text paint. */
    private fun spaceWidth(): Float = ed.textPaint.measureText(" ")

    /** Leading spaces of [line] (a tab counts as one indent), or for a blank line its neighbours'. */
    private fun effectiveIndent(line: Int): Int {
        rawIndent(line)?.let { return it }
        val above = (line - 1 downTo maxOf(0, line - SCAN)).firstNotNullOfOrNull { rawIndent(it) } ?: 0
        val below = (line + 1..minOf(ed.text.lineCount - 1, line + SCAN)).firstNotNullOfOrNull { rawIndent(it) } ?: 0
        return minOf(above, below)
    }

    /** Indent of [line] in columns, or null if it's blank. */
    private fun rawIndent(line: Int): Int? {
        val s = ed.text.getLine(line)
        var n = 0
        for (i in 0 until s.length) {
            when (s[i]) {
                ' ' -> n++
                '\t' -> n += indentWidth()
                else -> return n
            }
        }
        return null
    }

    /**
     * The guide to brighten: the column of the innermost block holding [cursorLine], and the
     * lines it spans. A header line (`for …:`) counts as inside the block it opens.
     */
    private fun activeBlock(cursorLine: Int, w: Int): Pair<Int, IntRange>? {
        val count = ed.text.lineCount
        var here = effectiveIndent(cursorLine)
        val next = (cursorLine + 1 until minOf(count, cursorLine + SCAN)).firstNotNullOfOrNull { rawIndent(it) }
        if (next != null && next > here && rawIndent(cursorLine) != null) here = next
        if (here < w) return null
        val col = ((here - 1) / w) * w
        var start = cursorLine
        while (start > 0 && effectiveIndent(start - 1) > col) start--
        var end = cursorLine
        while (end < count - 1 && effectiveIndent(end + 1) > col) end++
        return col to (start..end)
    }

    private companion object {
        /** How far to look for a non-blank neighbour. */
        const val SCAN = 60
    }
}
