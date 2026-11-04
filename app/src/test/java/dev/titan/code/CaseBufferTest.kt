package dev.titan.code

import dev.titan.code.data.CaseBuffer
import dev.titan.code.data.ProblemDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The test-case editor's buffer (rev 24). */
class CaseBufferTest {
    private fun detail(slug: String, meta: String) = ProblemDetail(
        slug, "1", 1, "Two Sum", "Easy", false, null, "class Solution: pass", "", meta, emptyList(), emptyList(), "[]",
    )

    private val twoSum = detail("two-sum",
        """{"name":"twoSum","params":[{"name":"nums","type":"integer[]"},{"name":"target","type":"integer"}],"return":{"type":"integer[]"}}""")

    @Test fun roundTrip() {
        val text = CaseBuffer.render(twoSum, "[2,7,11,15]\n9", "[0,1]")
        assertTrue(text, text.contains("# nums (integer[])\n[2,7,11,15]\n# target (integer)\n9\n# expected output (optional)\n[0,1]"))
        assertEquals(CaseBuffer.Parsed.Case("[2,7,11,15]\n9", "[0,1]"), CaseBuffer.parse(twoSum, text))
    }

    @Test fun valuesOverSeveralLinesAndNoExpected() {
        val text = "# header\n# nums (integer[])\n[1,\n 2,\n 3]\n# target (integer)\n  4  \n# expected output (optional)\n\n"
        assertEquals(CaseBuffer.Parsed.Case("[1, 2, 3]\n4", null), CaseBuffer.parse(twoSum, text))
    }

    @Test fun errorsPointAtTheLine() {
        val missing = CaseBuffer.parse(twoSum, "# x\n# nums (integer[])\n[1]\n# target (integer)\n\n# expected output (optional)\n")
        assertEquals(CaseBuffer.Parsed.Error(4, "No value for target."), missing)
        val bad = CaseBuffer.parse(twoSum, "# x\n# nums (integer[])\n[1,2\n# target (integer)\n3\n# expected output (optional)\n") as CaseBuffer.Parsed.Error
        assertEquals(2, bad.line)
        assertTrue(bad.message, bad.message.startsWith("nums isn't valid JSON: ']' missing"))
    }

    @Test fun strictJson() {
        assertNull(CaseBuffer.Json.check("""[[1,2],["a","b\"c"],true,null,-1.5e3,{"k":[]}]"""))
        assertEquals("strings need double quotes at character 2 ('a')", CaseBuffer.Json.check("[a,b]"))
        assertTrue(CaseBuffer.Json.check("[1,2]]")!!.startsWith("unexpected ']'"))
        assertTrue(CaseBuffer.Json.check("\"abc")!!.contains("closing quote"))
    }

    @Test fun designAndCodeforces() {
        val lru = detail("lru-cache", """{"classname":"LRUCache"}""")
        val text = CaseBuffer.render(lru, "[\"LRUCache\",\"get\"]\n[[2],[1]]", null)
        assertTrue(text.contains("# operations (string[])\n[\"LRUCache\",\"get\"]\n# arguments (list of argument lists)\n[[2],[1]]"))
        assertEquals(CaseBuffer.Parsed.Case("[\"LRUCache\",\"get\"]\n[[2],[1]]", null), CaseBuffer.parse(lru, text))

        val cf = detail("cf:1850A", "{}")
        val t = CaseBuffer.render(cf, "3\n1 2 3\n", "6\n")
        assertEquals(CaseBuffer.Parsed.Case("3\n1 2 3\n", "6"), CaseBuffer.parse(cf, t))
    }
}
