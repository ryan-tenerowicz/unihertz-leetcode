package dev.titan.code

import dev.titan.code.data.ChallengeException
import dev.titan.code.data.RateLimitedException
import dev.titan.code.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Rev 25: how one problem in a long pack download is retried, skipped or fatal. */
class PackDownloadTest {
    private val waits = mutableListOf<Long>()
    private fun attempt(vararg outcomes: () -> Boolean): Boolean? = runBlocking {
        var n = 0
        Repository.patiently("two-sum", { waits += it }) { outcomes[n++]() }
    }

    @Test fun rateLimitWaitsLongerEachTimeThenRetries() {
        val r = attempt({ throw RateLimitedException() }, { throw RateLimitedException() }, { true })
        assertEquals(true, r)
        assertEquals(listOf(30_000L, 60_000L), waits)
    }

    @Test fun networkErrorsThreeTimesGiveUpOnThisProblem() {
        assertNull(attempt({ throw IOException("HTTP 500") }, { throw IOException("x") }, { throw IOException("y") }))
        assertEquals(listOf(5_000L, 10_000L), waits)
    }

    @Test fun aBrokenProblemIsSkipped() {
        assertEquals(false, attempt({ throw IllegalStateException("no such problem") }))
        assertEquals(false, attempt({ false }))
        assertTrue(waits.isEmpty())
    }

    @Test fun browserChecksAndEndlessRateLimitsStopTheRun() {
        assertTrue(runCatching { attempt({ throw ChallengeException() }) }.exceptionOrNull() is ChallengeException)
        val always = Array<() -> Boolean>(10) { { throw RateLimitedException() } }
        assertTrue(runCatching { attempt(*always) }.exceptionOrNull() is RateLimitedException)
        assertEquals(300_000L, waits.last())
        assertFalse(waits.size > 6)
    }
}
