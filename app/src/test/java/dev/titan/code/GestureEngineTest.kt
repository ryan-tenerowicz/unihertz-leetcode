package dev.titan.code

import dev.titan.code.editor.EditorActions.Direction
import dev.titan.code.input.GestureEngine
import dev.titan.code.input.GestureEngine.Action
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
        assertTrue("right ${r.total(Direction.RIGHT)}", r.total(Direction.RIGHT) in 20..80)
        assertTrue("left ${r.total(Direction.LEFT)}", r.total(Direction.LEFT) in 20..80)
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
}
