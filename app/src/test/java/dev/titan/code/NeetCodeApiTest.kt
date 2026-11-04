package dev.titan.code

import dev.titan.code.data.Platform
import dev.titan.code.data.leetcode.Judge
import dev.titan.code.data.neetcode.NeetCodeApi
import dev.titan.code.runner.LocalJudge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeetCodeApiTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("neetcode/$name")!!.readText()
    private val catalog by lazy { NeetCodeApi.parseCatalog(java.io.File("src/main/assets/neetcode/catalog.json").readText()) }

    @Test fun bundledCatalog() {
        assertTrue(catalog.size > 900)
        val two = catalog.first { it.id == "two-integer-sum" }
        assertEquals("two-sum", two.lcSlug)
        assertEquals(1, two.lcNumber)
        val s = NeetCodeApi.summaries(catalog).first { it.slug == "nc:two-integer-sum" }
        assertEquals("1. Two Sum", s.label)
        assertEquals(Platform.NEETCODE, s.platform)
        val lists = NeetCodeApi.collections(catalog).associateBy { it.id }
        assertEquals(150, lists.getValue("nc-neetcode150").slugs.size)
        assertEquals(75, lists.getValue("nc-blind75").slugs.size)
        assertEquals(250, lists.getValue("nc-neetcode250").slugs.size)
        assertEquals("Arrays & Hashing", lists.getValue("nc-neetcode150").patterns["nc:two-integer-sum"])
    }

    @Test fun functionProblem() {
        val p = NeetCodeApi.parseProblem(res("two-integer-sum.json"), catalog.first { it.id == "two-integer-sum" })
        val d = p.detail
        assertEquals("nc:two-integer-sum", d.slug)
        assertEquals("two-integer-sum", d.questionId)
        assertEquals("1. Two Sum", d.label)
        assertFalse(d.paidOnly)
        val meta = JSONObject(d.metaData)
        assertEquals("twoSum", meta.getString("name"))
        assertEquals("integer[]", meta.getJSONArray("params").getJSONObject(0).getString("type"))
        assertEquals("integer", meta.getJSONArray("params").getJSONObject(1).getString("type"))
        assertEquals("integer[]", meta.getJSONObject("return").getString("type"))
        assertEquals(2, Judge.linesPerCase(d.metaData))
        assertEquals("[3,4,5,6]\n7\n[4,5,6]\n10\n[5,5]\n10", d.exampleTestcases)
        assertEquals(listOf("[0,1]", "[0,2]", "[0,1]"), LocalJudge.statementOutputs(d.contentHtml))
        val html = d.contentHtml!!
        assertTrue(html.contains("Example 1"))
        assertFalse(html.contains("<details"))
        assertTrue(d.hints.any { it.contains("Hint 1") })
        assertTrue(d.hints.any { it.contains("Recommended Time") })
        assertFalse(d.hints.any { it.contains("Topics") || it.contains("Company") })
        val article = p.article!!
        assertTrue(article.contains("```python"))
        assertFalse(article.contains("```java") || article.contains("::tabs"))
        assertEquals("https://www.youtube.com/watch?v=KLlXCFG5TnA", p.video)
    }

    @Test fun linkedListTreeAndImages() {
        val ll = NeetCodeApi.parseProblem(res("reverse-a-linked-list.json"), null).detail
        assertEquals("ListNode", JSONObject(ll.metaData).getJSONArray("params").getJSONObject(0).getString("type"))
        assertEquals("[0,1,2,3]\n[]", ll.exampleTestcases)
        assertEquals("Reverse Linked List", ll.label)
        val tree = NeetCodeApi.parseProblem(res("invert-a-binary-tree.json"), null).detail
        assertEquals("TreeNode", JSONObject(tree.metaData).getJSONObject("return").getString("type"))
        assertTrue(tree.contentHtml!!.contains("<img src=\"https://imagedelivery.net/"))
    }

    @Test fun classProblem() {
        val d = NeetCodeApi.parseProblem(res("lru-cache.json"), null).detail
        val meta = JSONObject(d.metaData)
        assertEquals("LRUCache", meta.getString("classname"))
        assertEquals(2, Judge.linesPerCase(d.metaData))
        assertEquals("[\"LRUCache\",\"put\",\"get\",\"put\",\"put\",\"get\",\"get\"]\n[[2],[1,10],[1],[2,20],[3,30],[2],[1]]", d.exampleTestcases)
        assertEquals("[null, null, 10, null, null, 20, -1]", LocalJudge.statementOutputs(d.contentHtml).first())
        assertEquals("[\"LRUCache\",[2],\"put\",[1,10]]", NeetCodeApi.toNeetCode("[\"LRUCache\",\"put\"]\n[[2],[1,10]]", d.metaData))
    }

    @Test fun inputConversions() {
        val meta = "{\"name\":\"f\",\"params\":[{\"name\":\"nums\",\"type\":\"integer[]\"},{\"name\":\"s\",\"type\":\"string\"}],\"neetcode\":{\"type\":\"function\"}}"
        assertEquals("nums=[1,2]\ns=\"a,b\"\n", NeetCodeApi.toNeetCode("[1,2]\n\"a,b\"", meta))
        assertEquals("[1,2]\n\"a,b\"", NeetCodeApi.toLines("s = \"a,b\", nums = [1,2]", listOf("nums", "s"), "function"))
        assertEquals("[1,2]\n\"a,b\"", NeetCodeApi.fromNeetCode("nums=[1,2]\ns=\"a,b\"", listOf("nums", "s"), "function"))
        assertEquals("[[1,2],[3]]\n5", NeetCodeApi.toLines("grid = [[1,2],[3]],\nk = 5", emptyList(), "function"))
    }

    /** Rev 23: tests with inputs the function doesn't take, and class problems tested like functions. */
    @Test fun specialInputs() {
        val cycle = NeetCodeApi.parseProblem(res("linked-list-cycle-detection.json"), null).detail
        assertEquals("[1,2,3,4]\n1", cycle.exampleTestcases.lines().take(2).joinToString("\n"))
        assertEquals("head=[1,2]\nindex=-1\n", NeetCodeApi.toNeetCode("[1,2]\n-1", cycle.metaData))

        val graph = NeetCodeApi.parseProblem(res("clone-graph.json"), null).detail
        assertEquals("cloneGraph", JSONObject(graph.metaData).getString("name"))
        assertEquals("[[2],[1,3],[2]]", graph.exampleTestcases.lines().first())
        assertEquals("adjList=[[]]\n", NeetCodeApi.toNeetCode("[[]]", graph.metaData))

        val codec = NeetCodeApi.parseProblem(res("serialize-and-deserialize-binary-tree.json"), null).detail
        assertEquals("Codec", JSONObject(codec.metaData).getString("classname"))
        assertEquals("[1,2,3,null,null,4,5]", codec.exampleTestcases.lines().first())
        assertEquals("root=[]\n", NeetCodeApi.toNeetCode("[]", codec.metaData))

        // Operations and arguments written as two lists.
        val it = NeetCodeApi.parseProblem(res("binary-search-tree-iterator.json"), null).detail
        assertEquals(
            "[\"BSTIterator\",\"next\",\"next\",\"hasNext\",\"next\",\"hasNext\",\"next\",\"hasNext\",\"next\",\"hasNext\"]\n" +
                "[[[7,3,15,null,null,9,20]],[],[],[],[],[],[],[],[],[]]",
            it.exampleTestcases.lines().take(2).joinToString("\n"),
        )
        assertEquals(2, Judge.linesPerCase(it.metaData))
    }

    @Test fun typeHints() {
        assertEquals("integer[][]", NeetCodeApi.leetType("List[List[int]]"))
        assertEquals("ListNode[]", NeetCodeApi.leetType("List[Optional[ListNode]]"))
        assertEquals("string", NeetCodeApi.leetType("str"))
        assertEquals("void", NeetCodeApi.leetType("None"))
        assertEquals("Node", NeetCodeApi.leetType("Optional['Node']"))
    }

    @Test fun runResults() {
        val body = """{"data":[
            {"status":{"id":3,"description":"Accepted"},"stdout":"","time":"0.012","memory":9000,
             "last_executed_test_case":{"input":"nums=[3,4]\ntarget=7","expected_output":"[0,1]","user_output":"[0,1]","user_logs":"hi\n"}},
            {"status":{"id":4,"description":"Wrong Answer"},"time":"0.02","memory":9100,
             "last_executed_test_case":{"input":"nums=[5,5]\ntarget=10","expected_output":"[0,1]","user_output":"[1,0]","user_logs":""}}]}"""
        val r = NeetCodeApi.parseRun(body, listOf("[3,4]\n7", "[5,5]\n10"))
        assertEquals("Run on NeetCode", r.source)
        assertEquals(Judge.Status.ACCEPTED, r.status)
        assertFalse(r.correct)
        assertEquals("Wrong Answer", r.verdict)
        assertEquals(listOf(true, false), r.cases.map { it.passed })
        assertEquals("[1,0]", r.cases[1].output)
        assertEquals("hi\n", r.cases[0].stdout)
        assertEquals("20 ms", r.runtime)
        val err = NeetCodeApi.parseRun("""{"data":[{"status":{"description":"Runtime Error"},"stderr":"Traceback...\nIndexError"}]}""", listOf("[1]\n1"))
        assertEquals(Judge.Status.RUNTIME_ERROR, err.status)
        assertEquals("Traceback...\nIndexError", err.error)
    }

    @Test fun submitResults() {
        val meta = "{\"name\":\"twoSum\",\"params\":[{\"name\":\"nums\",\"type\":\"integer[]\"},{\"name\":\"target\",\"type\":\"integer\"}],\"neetcode\":{\"type\":\"function\"}}"
        val ok = NeetCodeApi.parseSubmit("""{"data":{"status":{"description":"Accepted"},"test_case_count":33,"correct_test_case_count":33,"time":"0.05","memory":17000}}""", meta)
        assertTrue(ok.accepted)
        assertEquals("33/33", "${ok.totalCorrect}/${ok.totalTestcases}")
        assertEquals("50 ms", ok.runtime)
        assertEquals("Submit to NeetCode", ok.source)
        assertNull(ok.lastInput)
        val wa = NeetCodeApi.parseSubmit("""{"data":{"status":{"description":"Wrong Answer"},"test_case_count":33,"correct_test_case_count":12,
            "last_executed_test_case":{"input":"nums=[1,3]\ntarget=4","expected_output":"[0,1]","user_output":"[]"}}}""", meta)
        assertEquals(Judge.Status.WRONG_ANSWER, wa.status)
        assertEquals("[1,3]\n4", wa.lastInput)
        assertEquals("[]", wa.output)
        val e = runCatching { NeetCodeApi.data("""{"error":{"message":"Authentication required","status":"UNAUTHENTICATED"}}""") }.exceptionOrNull()
        assertEquals("Authentication required", e?.message)
    }
}
