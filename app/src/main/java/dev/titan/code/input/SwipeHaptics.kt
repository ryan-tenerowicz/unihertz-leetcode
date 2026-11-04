package dev.titan.code.input

import android.content.Context
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Haptic pulses for keyboard-surface cursor moves (owner request, rev 18; rev 19 fainter; rev 20
 * adaptive). Each cursor step (a character or a line) plays one pulse, and how long that pulse
 * is depends on how long ago the previous step was:
 * - **slow, deliberate steps** (≥ [SLOW_GAP_MS] apart, e.g. creeping one character at a time)
 *   get the level's longest pulse, so each character is distinct;
 * - **fast swipes** (steps ≤ [FAST_GAP_MS] apart) get the shortest, so a long swipe stays light;
 * - in between it scales linearly.
 * Reaching the start or end of the line plays a longer pulse.
 *
 * The Titan 2 Elite's vibrator has no amplitude control and long predefined effects (TICK 52–86 ms
 * in `dumpsys vibrator_manager`), so strength is pulse length. Rev 19's 3 ms pulse was too faint
 * and 6 ms "barely noticeable" (owner), so 6 ms is the floor.
 * Usage TOUCH, so the system's "touch feedback" switch turns them off too.
 */
class SwipeHaptics(context: Context) {
    /** Pulse lengths in ms: fast steps, slow steps, line edge. */
    enum class Level(val fastMs: Long, val slowMs: Long, val edgeMs: Long, val label: String) {
        OFF(0, 0, 0, "Off"),
        NORMAL(6, 16, 22, "Normal"),
        STRONG(9, 40, 50, "Strong");  // rev 21 (owner): slow steps were too weak at 24 ms
        companion object {
            /** Rev 19's FAINT and LIGHT map to NORMAL. */
            fun byName(n: String) = entries.firstOrNull { it.name == n } ?: NORMAL
        }
    }

    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    private var lastAt = 0L
    private var lastStepAt = 0L

    var level = Level.NORMAL

    fun character() = step()
    fun line() = step()
    fun lineEdge() = play(level.edgeMs, SystemClock.uptimeMillis(), force = true)

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val ms = pulseFor(now - lastStepAt, level)
        lastStepAt = now
        play(ms, now)
    }

    private fun play(ms: Long, now: Long, force: Boolean = false) {
        if (ms <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // Samples arrive every ~16 ms; one pulse per sample at most, and never closer than 12 ms.
        if (!force && now - lastAt < MIN_GAP_MS) return
        lastAt = now
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE), attrs)
    }

    companion object {
        const val MIN_GAP_MS = 12L
        /** Steps this close together are a fast swipe: shortest pulse. */
        const val FAST_GAP_MS = 40L
        /** Steps this far apart are deliberate single steps: longest pulse. */
        const val SLOW_GAP_MS = 150L

        /** Pulse length for a step [gapMs] after the previous one. */
        fun pulseFor(gapMs: Long, level: Level): Long {
            if (level == Level.OFF) return 0
            val f = ((gapMs - FAST_GAP_MS).toFloat() / (SLOW_GAP_MS - FAST_GAP_MS)).coerceIn(0f, 1f)
            return level.fastMs + Math.round(f * (level.slowMs - level.fastMs))
        }
    }
}
