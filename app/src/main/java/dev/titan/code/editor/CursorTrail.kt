package dev.titan.code.editor

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.core.graphics.ColorUtils
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * A short fading trail behind the cursor when it moves without an edit (owner request, rev 15):
 * a band from where the cursor was to where it is, brightest at the cursor, gone after
 * [DURATION_MS]. Successive small moves (a swipe) join into one comet-like streak.
 *
 * Points are kept in content coordinates, so the trail stays on the text when the view scrolls.
 */
internal class CursorTrail(private val ed: CodeEditor, private val colorOf: () -> Int) {

    private class Point(val x: Float, val y: Float, val t: Long)

    private val points = ArrayList<Point>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** The cursor moved from ([oldLine], [oldCol]) to ([line], [col]); the layout is unchanged. */
    fun moved(oldLine: Int, oldCol: Int, line: Int, col: Int) {
        if (oldLine == line && oldCol == col) return
        val now = SystemClock.uptimeMillis()
        val from = pointAt(oldLine, oldCol, now) ?: return
        val to = pointAt(line, col, now) ?: return
        val last = points.lastOrNull()
        // Continue the streak only if it ends where this move starts and is still fresh.
        if (last == null || now - last.t > JOIN_MS || last.x != from.x || last.y != from.y) {
            points.clear()
            points += from
        }
        points += to
        ed.postInvalidateOnAnimation()
    }

    fun clear() = points.clear()

    fun draw(canvas: Canvas) {
        if (points.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        // Drop points that have fully faded, but keep the one before the first live point as the tail.
        while (points.size > 1 && now - points[1].t > DURATION_MS) points.removeAt(0)
        if (points.size < 2 || now - points.last().t > DURATION_MS) { points.clear(); return }

        val dx = ed.offsetX.toFloat()
        val dy = ed.offsetY.toFloat()
        paint.strokeWidth = ed.rowHeight * WIDTH_OF_ROW
        val color = colorOf()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            // The very first point is the tail: fainter, so the band tapers towards the start.
            val alphaA = alphaAt(a, now) * if (i == 1) TAIL else 1f
            val alphaB = alphaAt(b, now)
            if (alphaA <= 0f && alphaB <= 0f) continue
            val x0 = a.x - dx; val y0 = a.y - dy
            val x1 = b.x - dx; val y1 = b.y - dy
            paint.shader = LinearGradient(
                x0, y0, x1 + 0.01f, y1,
                ColorUtils.setAlphaComponent(color, (alphaA * 255).toInt()),
                ColorUtils.setAlphaComponent(color, (alphaB * 255).toInt()),
                Shader.TileMode.CLAMP,
            )
            canvas.drawLine(x0, y0, x1, y1, paint)
        }
        paint.shader = null
        ed.postInvalidateOnAnimation()
    }

    private fun alphaAt(p: Point, now: Long): Float {
        val life = 1f - (now - p.t).toFloat() / DURATION_MS
        return if (life <= 0f) 0f else MAX_ALPHA * life * life
    }

    /** The middle of the caret at ([line], [col]), in content coordinates. */
    private fun pointAt(line: Int, col: Int, t: Long): Point? = runCatching {
        val x = ed.getCharOffsetX(line, col) + ed.offsetX
        val y = ed.getCharOffsetY(line, col) + ed.offsetY - ed.rowHeight / 2f
        Point(x, y, t)
    }.getOrNull()

    private companion object {
        const val DURATION_MS = 260L
        /** A move within this long of the last one extends the same streak. */
        const val JOIN_MS = 120L
        const val MAX_ALPHA = 0.55f
        const val TAIL = 0.35f
        const val WIDTH_OF_ROW = 0.3f
    }
}
