package dev.titan.code.input

/**
 * Keyboard-surface scrolling for the Problem pane (PLAN §5.6 item 6): vertical strokes scroll the
 * content 1:1, no inertia; horizontal strokes do nothing. Uses the same 24 px start threshold as
 * the cursor engine, so typing noise doesn't nudge the page.
 */
class SurfaceScroller(private val scrollBy: (Int) -> Unit, private val thresholdPx: Float = 24f) {
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var active = false
    private var down = false

    fun onSample(action: GestureEngine.Action, x: Float, y: Float) {
        when (action) {
            GestureEngine.Action.DOWN -> { downX = x; downY = y; lastY = y; active = false; down = true }
            GestureEngine.Action.MOVE -> {
                if (!down) return
                if (!active) {
                    val dx = kotlin.math.abs(x - downX)
                    val dy = kotlin.math.abs(y - downY)
                    if (dy < thresholdPx || dy < dx) return
                    active = true
                    lastY = downY
                }
                val delta = (lastY - y).toInt()
                if (delta != 0) {
                    scrollBy(delta)
                    lastY -= delta
                }
            }
            GestureEngine.Action.UP, GestureEngine.Action.CANCEL -> { down = false; active = false }
        }
    }
}
