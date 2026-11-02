package dev.titan.code.input

import dev.titan.code.editor.EditorActions.Direction
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.truncate

/**
 * Turns keyboard-surface strokes (Scroll Assistant touch events from device `touchPad`) into
 * cursor moves (PLAN §5.6). Plain Kotlin with no Android types, so the Phase 0 recordings can be
 * replayed in JVM unit tests. Scrolling the view is left to the touchscreen (owner decision,
 * revision 7).
 *
 * - Horizontal: 1 character per [Config.pxPerChar], times `1 + speed/1000 px·s⁻¹`, capped. It
 *   stops at the start or end of the line (the sink passes `withinLine`); only vertical strokes
 *   change lines.
 * - Vertical: 1 line per [Config.pxPerLine] (screen rows when soft wrap is on).
 * - Shift held (from the event's meta state, G05) extends the selection.
 * - Nothing happens until [Config.startThresholdPx] of travel; then travel counts from the
 *   touch-down point. A stroke that never gets that far is ignored (typing noise, G08).
 * - The axis locks to the larger travel when the threshold is crossed.
 * - The backlight long slide (G13) — more than [Config.longSlideWidthFraction] of the width at
 *   over [Config.longSlideMinSpeed] — is aborted, and [Sink.abortStroke] undoes it.
 */
class GestureEngine(private val sink: Sink, private val config: Config = Config()) {

    data class Config(
        val surfaceWidthPx: Float = 1080f,
        val startThresholdPx: Float = 24f,
        /** Owner: 40, then 60 px felt too fast (M1 step 2). Adjustable in Settings from M2. */
        val pxPerChar: Float = 70f,
        val maxAcceleration: Float = 4f,
        /** Owner: 60 px felt too fast (M1 step 2). Adjustable in Settings from M2. */
        val pxPerLine: Float = 90f,
        val longSlideWidthFraction: Float = 0.75f,
        /** px per second, averaged from the stroke's start. */
        val longSlideMinSpeed: Float = 2000f,
    )

    interface Sink {
        /** A stroke has started moving the cursor; remember the cursor and scroll position. */
        fun beginStroke()
        fun move(direction: Direction, steps: Int, extend: Boolean)
        /** The stroke was a long slide: put things back where [beginStroke] found them. */
        fun abortStroke()
        /** One line per finished stroke, for the log while gestures are being tuned. */
        fun strokeSummary(summary: String) {}
    }

    enum class Action { DOWN, MOVE, UP, CANCEL }

    private enum class State { IDLE, PENDING, ACTIVE, DROPPED }
    private enum class Axis { H, V }

    private var state = State.IDLE
    private var axis = Axis.H
    private var startX = 0f
    private var startY = 0f
    private var startT = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var minX = 0f
    private var maxX = 0f
    private var carry = 0f
    private var lastStrokeEnd = Long.MIN_VALUE / 2
    private var gapBefore = 0L

    fun onSample(action: Action, x: Float, y: Float, timeMs: Long, shift: Boolean) {
        when (action) {
            Action.DOWN -> {
                state = State.PENDING
                startX = x; startY = y; startT = timeMs
                lastX = x; lastY = y; lastT = timeMs
                minX = x; maxX = x
                carry = 0f
                gapBefore = timeMs - lastStrokeEnd
            }
            Action.MOVE -> move(x, y, timeMs, shift)
            Action.UP, Action.CANCEL -> {
                if (state == State.IDLE) return
                val wasState = state
                if (state == State.ACTIVE || state == State.PENDING) move(x, y, timeMs, shift)
                val endState = if (state == State.DROPPED) State.DROPPED else wasState
                val duration = timeMs - startT
                sink.strokeSummary(
                    "stroke ${action.name} " +
                        when (endState) { State.ACTIVE -> "CURSOR"; State.DROPPED -> "LONG-SLIDE"; else -> "ignored" } +
                        " dur=${duration}ms dx=${(x - startX).roundToInt()} dy=${(y - startY).roundToInt()}" +
                        " gapBefore=${if (gapBefore > 100_000) "-" else "${gapBefore}ms"}"
                )
                lastStrokeEnd = timeMs
                state = State.IDLE
            }
        }
    }

    private fun move(x: Float, y: Float, t: Long, shift: Boolean) {
        if (state == State.IDLE || state == State.DROPPED) return
        minX = min(minX, x); maxX = maxOf(maxX, x)

        if (isLongSlide(t)) {
            if (state == State.ACTIVE) sink.abortStroke()
            state = State.DROPPED
            return
        }

        if (state == State.PENDING) {
            val dx = x - startX
            val dy = y - startY
            if (hypot(dx, dy) < config.startThresholdPx) return
            axis = if (abs(dx) >= abs(dy)) Axis.H else Axis.V
            state = State.ACTIVE
            sink.beginStroke()
            // Fall through: travel counts from the touch-down point (lastX/lastY are still
            // there). A fast stroke's first sample is often far past the threshold.
        }

        val dxStep = x - lastX
        val dyStep = y - lastY
        val dt = (t - lastT).coerceAtLeast(1L)
        lastX = x; lastY = y; lastT = t

        when (axis) {
            Axis.H -> {
                val speed = abs(dxStep) / dt * 1000f
                val accel = min(1f + speed / 1000f, config.maxAcceleration)
                carry += dxStep * accel / config.pxPerChar
            }
            Axis.V -> carry += dyStep / config.pxPerLine
        }
        val steps = truncate(carry).toInt()
        if (steps != 0) {
            carry -= steps
            val dir = when (axis) {
                Axis.H -> if (steps > 0) Direction.RIGHT else Direction.LEFT
                Axis.V -> if (steps > 0) Direction.DOWN else Direction.UP
            }
            sink.move(dir, abs(steps), shift)
        }
    }

    private fun isLongSlide(t: Long): Boolean {
        val span = maxX - minX
        if (span < config.surfaceWidthPx * config.longSlideWidthFraction) return false
        val seconds = (t - startT).coerceAtLeast(1L) / 1000f
        return span / seconds > config.longSlideMinSpeed
    }
}
