package dev.titan.code

import dev.titan.code.data.ProblemFilter
import dev.titan.code.data.ProblemSummary
import dev.titan.code.ui.ProblemListModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rev 22: paid problems are never listed; 🎲 picks from the current view. */
class ProblemListModelTest {
    private fun p(id: Int, diff: String, paid: Boolean = false, status: String? = null) =
        ProblemSummary("p$id", id, "Problem $id", diff, paid, 50.0, emptyList(), status)

    private val model = ProblemListModel().apply {
        all = listOf(
            p(1, "Easy"), p(2, "Medium"), p(3, "Medium", paid = true),
            p(4, "Medium", status = "ac"), p(5, "Hard"), p(6, "Medium"),
        )
    }

    @Test fun paidProblemsAreHidden() {
        assertEquals(listOf(1, 2, 4, 5, 6), model.shown.map { it.frontendId })
        assertEquals(5, model.available.size)
    }

    @Test fun randomRespectsTheViewAndPrefersUnsolved() {
        model.updateFilter(ProblemFilter(difficulty = "Medium"))
        val picks = (0 until 200).map { model.random(Random(it))!!.frontendId }.toSet()
        assertEquals(setOf(2, 6), picks) // never the paid 3, the solved 4, or another difficulty
    }

    @Test fun randomFallsBackToSolvedWhenNothingElse() {
        model.updateFilter(ProblemFilter(status = "solved"))
        assertEquals(4, model.random(Random(1))!!.frontendId)
    }

    @Test fun randomOnAnEmptyViewIsNull() {
        model.updateFilter(ProblemFilter(text = "no such problem"))
        assertNull(model.random())
        assertTrue(model.shown.isEmpty())
    }
}
