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
 * - Horizontal: 1 character per [Config.pxPerChar]. Rev 16 (owner: slow moves jumped over
 *   words): speed is measured over the last [Config.speedWindowMs], not from one ~16 ms sample
 *   (Phase 0 samples arrive 4–43 ms apart, so per-sample speed swung 300–1800 px/s in one gentle
 *   swipe); below [Config.precisionSpeed] there's no acceleration at all, then it ramps to
 *   [Config.maxAcceleration] at [Config.fullSpeed]; and the first [Config.startRampMs] of a stroke
 *   (where a landing finger lurches) is never accelerated. It stops at the start or end of the
 *   line (the sink passes `withinLine`); only vertical strokes change lines.
 * - Vertical: 1 line per [Config.pxPerLine] (screen rows when soft wrap is on).
 * - Shift held (from the event's meta state, G05) extends the selection.
 * - Nothing happens until [Config.startThresholdPx] of travel; then travel counts from the
 *   touch-down point. A stroke that never gets that far is ignored (typing noise, G08).
 * - The axis locks to the larger travel when the threshold is crossed.
 * - The backlight long slide (G13) — more than [Config.longSlideWidthFraction] of the width at
 *   over [Config.longSlideMinSpeed] — is aborted, and [Sink.abortStroke] undoes it.
 * - Typing (owner report, rev 14): a finger resting on the keys, or the little slide a key press
 *   makes just before the key registers, looks like a stroke. So [onKey] is told about every key:
 *   a key going down mid-stroke ends the stroke, undoing any movement from the last
 *   [Config.typingUndoMs]; a stroke that starts while a key is held, or within
 *   [Config.afterKeyMs] of a key, is ignored. Shift doesn't count (Shift + swipe selects).
 */
class GestureEngine(private val sink: Sink, var config: Config = Config()) {

    data class Config(
        val surfaceWidthPx: Float = 1080f,
        val startThresholdPx: Float = 24f,
        /** Owner: 40, then 60 px felt too fast (M1 step 2). Adjustable in Settings from M2. */
        val pxPerChar: Float = 70f,
        /** Rev 16: was 4×, reached from any speed (gain `1 + speed/1000`). */
        val maxAcceleration: Float = 3.5f,
        /** Below this (px/s) a character is always [pxPerChar]: the precision zone. */
        val precisionSpeed: Float = 700f,
        /** At or above this (px/s) the gain is [maxAcceleration]. */
        val fullSpeed: Float = 2800f,
        /** Speed is the travel over this much of the most recent stroke. */
        val speedWindowMs: Long = 80,
        /** No acceleration this soon after the stroke starts moving the cursor. */
        val startRampMs: Long = 100,
        /** Owner: 60 px felt too fast (M1 step 2). Adjustable in Settings from M2. */
        val pxPerLine: Float = 90f,
        val longSlideWidthFraction: Float = 0.75f,
        /** px per second, averaged from the stroke's start. */
        val longSlideMinSpeed: Float = 2000f,
        /** Movement this close before a key press was the press itself; undone. */
        val typingUndoMs: Long = 300,
        /** A stroke starting this soon after a key event is the finger leaving the key. */
        val afterKeyMs: Long = 150,
    )

    interface Sink {
        /**
         * The cursor is about to move (first move of a stroke, or after a pause of
         * [Config.typingUndoMs]); remember the cursor and scroll position.
         */
        fun beginStroke()
        fun move(direction: Direction, steps: Int, extend: Boolean)
        /** The movement was a long slide or typing: put things back where [beginStroke] found them. */
        fun abortStroke()
        /** One line per finished stroke, for the log while gestures are being tuned. */
        fun strokeSummary(summary: String) {}
    }

    enum class Action { DOWN, MOVE, UP, CANCEL }

    private enum class State { IDLE, PENDING, ACTIVE, DROPPED }
    private enum class Drop { LONG_SLIDE, TYPING }
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
    private var activeT = 0L
    /** Recent samples along the locked axis: time and position, for the speed window. */
    private val recentT = LongArray(RING)
    private val recentP = FloatArray(RING)
    private var recentN = 0
    private var recentHead = 0
    /** Raw samples of the current stroke, for the tuning log (dt/dx/dy, capped). */
    private val trace = StringBuilder()
    private var lastStrokeEnd = Long.MIN_VALUE / 2
    private var gapBefore = 0L
    private var drop = Drop.LONG_SLIDE
    /** Time of the last cursor move in this stroke, or never. */
    private var lastMoveT = NEVER
    private val heldKeys = mutableSetOf<Int>()
    private var lastKeyT = NEVER

    /**
     * A key went down or up ([timeMs] on the same clock as the touch samples). Shift is left out
     * by the caller's choice of [counts]; modifiers that silence the surface (Alt, `sym`) count.
     */
    fun onKey(code: Int, down: Boolean, timeMs: Long) {
        if (down) heldKeys += code else heldKeys -= code
        lastKeyT = timeMs
        if (!down) return
        if (state == State.ACTIVE && timeMs - lastMoveT <= config.typingUndoMs) sink.abortStroke()
        if (state == State.ACTIVE || state == State.PENDING) {
            state = State.DROPPED
            drop = Drop.TYPING
        }
    }

    /** Forget held keys (the window lost focus, so their key-ups may never come). */
    fun releaseKeys() = heldKeys.clear()

    fun onSample(action: Action, x: Float, y: Float, timeMs: Long, shift: Boolean) {
        when (action) {
            Action.DOWN -> {
                state = State.PENDING
                startX = x; startY = y; startT = timeMs
                lastX = x; lastY = y; lastT = timeMs
                minX = x; maxX = x
                carry = 0f
                gapBefore = timeMs - lastStrokeEnd
                lastMoveT = NEVER
                recentN = 0
                trace.setLength(0)
                if (heldKeys.isNotEmpty() || timeMs - lastKeyT < config.afterKeyMs) {
                    state = State.DROPPED
                    drop = Drop.TYPING
                }
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
                        when (endState) { State.ACTIVE -> "CURSOR"; State.DROPPED -> drop.name.replace('_', '-'); else -> "ignored" } +
                        " dur=${duration}ms dx=${(x - startX).roundToInt()} dy=${(y - startY).roundToInt()}" +
                        " gapBefore=${if (gapBefore > 100_000) "-" else "${gapBefore}ms"}" +
                        if (endState == State.ACTIVE) " samples[dt,dx,dy]=$trace" else ""
                )
                lastStrokeEnd = timeMs
                state = State.IDLE
            }
        }
    }

    private fun move(x: Float, y: Float, t: Long, shift: Boolean) {
        if (state == State.IDLE || state == State.DROPPED) return
        if (trace.length < TRACE_MAX) trace.append("${t - lastT},${(x - lastX).roundToInt()},${(y - lastY).roundToInt()} ")
        minX = min(minX, x); maxX = maxOf(maxX, x)

        if (isLongSlide(t)) {
            if (lastMoveT != NEVER) sink.abortStroke()
            state = State.DROPPED
            drop = Drop.LONG_SLIDE
            return
        }

        if (state == State.PENDING) {
            val dx = x - startX
            val dy = y - startY
            if (hypot(dx, dy) < config.startThresholdPx) return
            axis = if (abs(dx) >= abs(dy)) Axis.H else Axis.V
            state = State.ACTIVE
            activeT = t
            record(startT, if (axis == Axis.H) startX else startY)
            // Fall through: travel counts from the touch-down point (lastX/lastY are still
            // there). A fast stroke's first sample is often far past the threshold.
        }

        val dxStep = x - lastX
        val dyStep = y - lastY
        lastX = x; lastY = y; lastT = t

        when (axis) {
            Axis.H -> {
                record(t, x)
                carry += dxStep * gain(t) / config.pxPerChar
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
            if (t - lastMoveT > config.typingUndoMs) sink.beginStroke()
            lastMoveT = t
            sink.move(dir, abs(steps), shift)
        }
    }

    private fun record(t: Long, p: Float) {
        recentT[recentHead] = t
        recentP[recentHead] = p
        recentHead = (recentHead + 1) % RING
        if (recentN < RING) recentN++
    }

    /** Horizontal gain at time [t]: 1 in the precision zone, rising to the maximum at full speed. */
    internal fun gain(t: Long): Float {
        if (t - activeT < config.startRampMs) return 1f
        val speed = windowSpeed(t)
        if (speed <= config.precisionSpeed) return 1f
        val f = ((speed - config.precisionSpeed) / (config.fullSpeed - config.precisionSpeed)).coerceAtMost(1f)
        return 1f + f * (config.maxAcceleration - 1f)
    }

    /** px/s along the axis: total travel over the samples in the last [Config.speedWindowMs]. */
    private fun windowSpeed(t: Long): Float {
        var travel = 0f
        var oldest = t
        var i = (recentHead - 1 + RING) % RING
        var newerP = recentP[i]
        for (k in 1 until recentN) {
            val j = (i - 1 + RING) % RING
            if (t - recentT[j] > config.speedWindowMs) break
            travel += abs(newerP - recentP[j])
            newerP = recentP[j]
            oldest = recentT[j]
            i = j
        }
        val span = (t - oldest).coerceAtLeast(MIN_SPAN_MS)
        return travel / span * 1000f
    }

    private fun isLongSlide(t: Long): Boolean {
        val span = maxX - minX
        if (span < config.surfaceWidthPx * config.longSlideWidthFraction) return false
        val seconds = (t - startT).coerceAtLeast(1L) / 1000f
        return span / seconds > config.longSlideMinSpeed
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
        const val RING = 32
        /** Floor for the speed window, so one sample a few ms after another can't read as a flick. */
        const val MIN_SPAN_MS = 30L
        const val TRACE_MAX = 3000
    }
}
