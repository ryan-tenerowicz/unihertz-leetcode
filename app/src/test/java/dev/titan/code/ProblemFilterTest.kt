package dev.titan.code

import dev.titan.code.data.ProblemFilter
import dev.titan.code.data.ProblemSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemFilterTest {
    private fun p(id: Int, title: String, diff: String = "Easy", tags: List<String> = emptyList(), status: String? = null) =
        ProblemSummary(title.lowercase().replace(' ', '-'), id, title, diff, false, 50.0, tags, status)

    private val all = listOf(
        p(1, "Two Sum", tags = listOf("array", "hash-table"), status = "ac"),
        p(15, "3Sum", "Medium", listOf("array", "two-pointers"), "notac"),
        p(49, "Group Anagrams", "Medium", listOf("hash-table", "string")),
        p(490, "The Maze", "Medium", listOf("graph")),
        p(20, "Valid Parentheses", tags = listOf("stack", "string")),
    )

    private fun ids(f: ProblemFilter, saved: Set<String> = emptySet()) = f.apply(all, saved).map { it.frontendId }

    @Test fun numberPrefix() = assertEquals(listOf(49, 490), ids(ProblemFilter("49")))
    @Test fun titleWordsAnyOrder() = assertEquals(listOf(1), ids(ProblemFilter("sum two")))
    @Test fun titleIgnoresCase() = assertEquals(listOf(49), ids(ProblemFilter("ANAGR")))
    @Test fun tagFilter() = assertEquals(listOf(1, 49), ids(ProblemFilter("#hash")))
    @Test fun tagPlusTitle() = assertEquals(listOf(20), ids(ProblemFilter("#string valid")))
    @Test fun difficulty() = assertEquals(listOf(15, 49, 490), ids(ProblemFilter(difficulty = "Medium")))
    @Test fun status() {
        assertEquals(listOf(1), ids(ProblemFilter(status = "solved")))
        assertEquals(listOf(15), ids(ProblemFilter(status = "tried")))
        assertEquals(listOf(49, 490, 20), ids(ProblemFilter(status = "todo")))
    }
    @Test fun savedOnly() = assertEquals(listOf(20), ids(ProblemFilter(savedOnly = true), setOf("valid-parentheses")))
    @Test fun collectionKeepsListOrder() {
        val c = dev.titan.code.data.ProblemCollection("x", "X", listOf("valid-parentheses", "two-sum", "missing"), emptyMap())
        assertEquals(listOf(20, 1), ids(ProblemFilter(collection = c)))
        assertEquals(listOf(20), ids(ProblemFilter("valid", collection = c)))
    }

    @Test fun bundledNeetCodeLists() {
        val json = java.io.File("src/main/assets/lists/neetcode.json").readText()
        val lists = dev.titan.code.data.Collections.parse(json)
        assertEquals(listOf(150, 75), lists.map { it.slugs.size })
        assertEquals("Arrays & Hashing", lists[0].patterns["two-sum"])
        assertEquals(lists[1].slugs.toSet() - lists[0].slugs.toSet(), emptySet<String>())
    }

    @Test fun emptyMatchesAll() = assertEquals(5, ids(ProblemFilter("  ")).size)
}
