package dev.titan.code

import dev.titan.code.samples.Starter
import org.junit.Assert.assertEquals
import org.junit.Test

class StarterTest {
    private fun snippet(name: String) =
        javaClass.classLoader!!.getResource("snippets/$name")!!.readText()

    @Test fun twoSumCursorOnBody() {
        val p = Starter.prepare(snippet("two-sum.py"))
        assertEquals(2, p.line)
        assertEquals(8, p.column)
        assertEquals(snippet("two-sum.py"), p.text)
    }

    @Test fun commentedDefinitionIsSkipped() {
        val p = Starter.prepare(snippet("add-two-numbers.py"))
        assertEquals(7, p.line)
        assertEquals(8, p.column)
    }

    @Test fun designClassUsesFirstMethod() {
        val p = Starter.prepare(snippet("lru-cache.py"))
        assertEquals(3, p.line)
        assertEquals(8, p.column)
        assertEquals(snippet("lru-cache.py"), p.text)
    }

    @Test fun strippedBodyIsReindented() {
        val p = Starter.prepare(snippet("stripped.py"))
        assertEquals(2, p.line)
        assertEquals(8, p.column)
        assertEquals(" ".repeat(8), p.text.lines()[2])
    }

    @Test fun noDefPutsCursorAtEnd() {
        val p = Starter.prepare("x = 1\ny = 2")
        assertEquals(1, p.line)
        assertEquals(5, p.column)
    }

    @Test fun twoSpaceIndentReindentsSnippet() {
        val p = Starter.prepare("class Solution:\n    def f(self, x: int) -> int:\n        ", indentWidth = 2)
        assertEquals("class Solution:\n  def f(self, x: int) -> int:\n    ", p.text)
        assertEquals(2 to 4, p.line to p.column)
    }
}
