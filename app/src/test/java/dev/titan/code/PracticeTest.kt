package dev.titan.code

import dev.titan.code.data.PracticeTimer
import dev.titan.code.data.db.TimerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The practice timer (rev 24). */
class PracticeTest {
    private var clock = 0L
    private val timer = PracticeTimer { clock }

    @Test fun startsOnFirstEditAndCountsOnlyInFront() {
        timer.load("two-sum", null)
        clock = 5_000
        assertFalse(timer.started)
        assertEquals(0, timer.elapsedMs())
        timer.edited(wallMs = 1000)
        clock = 65_000
        assertEquals(60_000, timer.elapsedMs())
        timer.foreground(false)
        clock = 1_000_000 // in the background: not counted
        timer.foreground(true)
        clock = 1_010_000
        assertEquals(70_000, timer.elapsedMs())
        assertEquals("1:10", PracticeTimer.format(timer.elapsedMs()))
        assertEquals(TimerRow("leetcode", "two-sum", 70_000, 1000, null), timer.row("leetcode"))
    }

    @Test fun acceptedStopsItAndSavedStateComesBack() {
        timer.load("a", TimerRow("leetcode", "a", 30_000, 1, null))
        assertTrue(timer.running)
        clock = 10_000
        timer.finish(wallMs = 99)
        clock = 50_000
        assertEquals(40_000, timer.elapsedMs())
        assertTrue(timer.done)
        timer.edited(wallMs = 100) // editing after solving doesn't restart it
        assertFalse(timer.running)
        timer.load("a", timer.row("leetcode"))
        assertTrue(timer.done)
        assertFalse(timer.running)
        timer.reset()
        assertNull(timer.row("leetcode"))
        assertEquals("1:02:03", PracticeTimer.format(3_723_000))
    }
}
