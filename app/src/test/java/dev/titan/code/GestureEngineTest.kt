package dev.titan.code

import dev.titan.code.editor.EditorActions.Direction
import dev.titan.code.input.GestureEngine
import dev.titan.code.input.GestureEngine.Action
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays Phase 0 recordings (app/src/test/resources/phase0/) through the GestureEngine. */
class GestureEngineTest {

    private class Recorder : GestureEngine.Sink {
        val moves = mutableListOf<Triple<Direction, Int, Boolean>>()
        var begun = 0
        var aborted = 0
        override fun beginStroke() { begun++ }
        override fun move(direction: Direction, steps: Int, extend: Boolean) { moves += Triple(direction, steps, extend) }
        override fun abortStroke() { aborted++ }

        fun total(d: Direction) = moves.filter { it.first == d }.sumOf { it.second }
    }

    private fun replay(name: String): Recorder {
        val rec = Recorder()
        val engine = GestureEngine(rec)
        val text = javaClass.getResource("/phase0/$name.csv")!!.readText()
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val (a, x, y, t, meta) = line.split(",")
            engine.onSample(Action.valueOf(a), x.toFloat(), y.toFloat(), t.toLong(), (meta.toInt() and 1) != 0)
        }
        return rec
    }

    @Test fun horizontalSwipeMovesRightThenLeftOnly() {
        val r = replay("horizontal-swipe")
        assertEquals(2, r.begun)
        assertEquals(0, r.total(Direction.UP) + r.total(Direction.DOWN))
        // Two ~810 px swipes at about 950 px/s (Phase 0's "slow" swipe): mostly unaccelerated
        // since rev 16, about 12 characters at 1x, 15–20 with the gentle speed-up they get.
        assertTrue("right ${r.total(Direction.RIGHT)}", r.total(Direction.RIGHT) in 12..30)
        assertTrue("left ${r.total(Direction.LEFT)}", r.total(Direction.LEFT) in 12..30)
        assertTrue(r.moves.none { it.third })
    }

    @Test fun verticalSwipesMoveByLineOnly() {
        val r = replay("vertical-swipe")
        assertEquals(0, r.total(Direction.LEFT) + r.total(Direction.RIGHT))
        // Strokes of about 370, 360, 370, 216 and 284 px at 90 px per line: 2–4 lines each.
        assertTrue("up ${r.total(Direction.UP)}", r.total(Direction.UP) in 6..10)
        assertTrue("down ${r.total(Direction.DOWN)}", r.total(Direction.DOWN) in 7..12)
    }

    @Test fun shiftSwipeExtendsSelectionAndIsNotALongSlide() {
        val r = replay("shift-swipe")
        assertEquals(0, r.aborted)
        assertTrue(r.moves.isNotEmpty())
        assertTrue(r.moves.all { it.third })
    }

    @Test fun typingNoiseDoesNothing() {
        val r = replay("typing-noise")
        assertEquals(0, r.begun)
        assertTrue(r.moves.isEmpty())
    }

    @Test fun longSlidesAreAborted() {
        val r = replay("long-slide")
        assertEquals(3, r.aborted)
    }

    @Test fun belowThresholdDoesNothing() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onSample(Action.DOWN, 500f, 600f, 0, false)
        e.onSample(Action.MOVE, 515f, 610f, 10, false)
        e.onSample(Action.UP, 520f, 612f, 20, false)
        assertEquals(0, rec.begun)
        assertTrue(rec.moves.isEmpty())
    }

    @Test fun diagonalLocksToTheLargerAxis() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onSample(Action.DOWN, 500f, 600f, 0, false)
        var t = 0L
        for (i in 1..20) { t += 10; e.onSample(Action.MOVE, 500f + i * 10, 600f + i * 6, t, false) }
        e.onSample(Action.UP, 700f, 720f, t + 10, false)
        assertEquals(0, rec.total(Direction.UP) + rec.total(Direction.DOWN))
        assertTrue(rec.total(Direction.RIGHT) > 0)
    }

    // ---- typing (owner report, rev 14) ----------------------------------------------------

    /** A quick 160 px slide to the left: at least 2 characters even without acceleration. */
    private fun pressSlide(e: GestureEngine, t0: Long) {
        e.onSample(Action.DOWN, 500f, 600f, t0, false)
        for (i in 1..6) e.onSample(Action.MOVE, 500f - i * 27, 600f, t0 + i * 5, false)
    }

    @Test fun keyPressUndoesTheSlideJustBeforeIt() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        pressSlide(e, 1000)
        assertTrue(rec.total(Direction.LEFT) > 0)
        e.onKey(KeyEvent.KEYCODE_SPACE, true, 1060)
        assertEquals(1, rec.aborted)
        // The finger stays down and wanders while typing: nothing more moves.
        val before = rec.moves.size
        for (i in 1..30) e.onSample(Action.MOVE, 338f + i * 10, 600f + i * 5, 1060L + i * 10, false)
        e.onSample(Action.CANCEL, 740f, 750f, 1400, false)
        assertEquals(before, rec.moves.size)
    }

    @Test fun restingFingerMovesNothingOnceTypingStarts() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onSample(Action.DOWN, 500f, 600f, 0, false)
        e.onKey(KeyEvent.KEYCODE_N, true, 40)
        e.onKey(KeyEvent.KEYCODE_N, false, 120)
        for (i in 1..50) e.onSample(Action.MOVE, 500f, 600f - i * 6, 200L + i * 20, false)
        e.onSample(Action.UP, 500f, 300f, 1300, false)
        assertTrue(rec.moves.isEmpty())
    }

    @Test fun deliberateSwipeBeforeTypingIsKept() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        pressSlide(e, 1000)
        // Finger rests after the swipe; the key comes well after the last move.
        e.onKey(KeyEvent.KEYCODE_A, true, 1600)
        assertEquals(0, rec.aborted)
        assertTrue(rec.total(Direction.LEFT) > 0)
    }

    @Test fun strokesWhileAKeyIsHeldOrJustAfterAreIgnored() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onKey(KeyEvent.KEYCODE_SPACE, true, 900)
        pressSlide(e, 1000)
        e.onSample(Action.UP, 338f, 600f, 1040, false)
        e.onKey(KeyEvent.KEYCODE_SPACE, false, 1100)
        pressSlide(e, 1200) // 100 ms after the key-up
        e.onSample(Action.UP, 338f, 600f, 1240, false)
        assertTrue(rec.moves.isEmpty())
        pressSlide(e, 1500) // a real swipe later still works
        assertTrue(rec.total(Direction.LEFT) > 0)
    }

    @Test fun releasedKeysDontBlockSwipes() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onKey(KeyEvent.KEYCODE_SYM, true, 0) // its key-up is lost to a focus change
        e.releaseKeys()
        pressSlide(e, 1000)
        assertTrue(rec.total(Direction.LEFT) > 0)
    }

    // ---- smooth speed (owner report, rev 16) ----------------------------------------------

    /** Slow moves with uneven sample spacing (4–34 ms, as the surface sends) never speed up. */
    @Test fun slowMoveIsOneCharacterPerStepDespiteJitter() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        val gaps = longArrayOf(16, 4, 25, 17, 34, 5, 16, 17, 25, 4, 16, 17, 34, 16, 17, 25, 4, 16, 17, 25)
        var t = 0L; var x = 500f
        e.onSample(Action.DOWN, x, 600f, t, false)
        for (g in gaps) { t += g; x += g * 0.5f; e.onSample(Action.MOVE, x, 600f, t, false) } // 500 px/s
        e.onSample(Action.UP, x, 600f, t + 10, false)
        // About 192 px at 70 px per character, no acceleration: 2 characters.
        assertEquals(2, rec.total(Direction.RIGHT))
    }

    @Test fun landingLurchIsNotAccelerated() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onSample(Action.DOWN, 500f, 600f, 0, false)
        e.onSample(Action.MOVE, 540f, 600f, 10, false) // 40 px in 10 ms as the finger lands
        for (i in 1..10) e.onSample(Action.MOVE, 540f + i * 3, 600f, 10L + i * 16, false)
        e.onSample(Action.UP, 570f, 600f, 200, false)
        assertEquals(1, rec.total(Direction.RIGHT))
    }

    @Test fun fastFlickStillTravelsFar() {
        val rec = Recorder()
        val e = GestureEngine(rec)
        e.onSample(Action.DOWN, 100f, 600f, 0, false)
        for (i in 1..20) e.onSample(Action.MOVE, 100f + i * 40, 600f, i * 10L, false) // 4000 px/s, 800 px
        e.onSample(Action.UP, 900f, 600f, 210, false)
        assertTrue("right ${rec.total(Direction.RIGHT)}", rec.total(Direction.RIGHT) >= 25)
    }

    @Test fun accelerationOffIsLinear() {
        val rec = Recorder()
        val e = GestureEngine(rec, GestureEngine.Config(maxAcceleration = 1f))
        e.onSample(Action.DOWN, 100f, 600f, 0, false)
        for (i in 1..20) e.onSample(Action.MOVE, 100f + i * 40, 600f, i * 10L, false)
        e.onSample(Action.UP, 900f, 600f, 210, false)
        assertEquals(11, rec.total(Direction.RIGHT)) // 800 / 70
    }
}
