package dev.titan.code

import dev.titan.code.input.SwipeHaptics
import dev.titan.code.input.SwipeHaptics.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rev 20: pulses scale with how slowly the cursor is stepping, and never go below 6 ms. */
class SwipeHapticsTest {
    @Test fun slowStepsGetTheLongestPulse() {
        assertEquals(16, SwipeHaptics.pulseFor(400, Level.NORMAL))
        assertEquals(16, SwipeHaptics.pulseFor(Long.MAX_VALUE / 2, Level.NORMAL)) // first step of a stroke
        assertEquals(40, SwipeHaptics.pulseFor(200, Level.STRONG))
    }

    @Test fun fastSwipesGetTheShortest() {
        assertEquals(6, SwipeHaptics.pulseFor(16, Level.NORMAL))
        assertEquals(9, SwipeHaptics.pulseFor(30, Level.STRONG))
    }

    @Test fun scalesInBetweenAndNeverBelowTheFloor() {
        val mid = SwipeHaptics.pulseFor(95, Level.NORMAL)
        assertTrue("$mid", mid in 10..12)
        for (gap in 0L..300L) assertTrue(SwipeHaptics.pulseFor(gap, Level.NORMAL) >= 6)
        assertEquals(0, SwipeHaptics.pulseFor(400, Level.OFF))
    }

    @Test fun oldLevelNamesMapToNormal() {
        assertEquals(Level.NORMAL, Level.byName("FAINT"))
        assertEquals(Level.NORMAL, Level.byName("LIGHT"))
    }
}
