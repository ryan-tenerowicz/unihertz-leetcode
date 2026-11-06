package dev.titan.code

import dev.titan.code.data.leetcode.LeetCodeApi
import dev.titan.code.data.leetcode.Judge
import dev.titan.code.runner.LocalJudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalJudgeTest {
    private val detail = LeetCodeApi.parseDetail(javaClass.getResource("/leetcode/detail-group-anagrams.json")!!.readText())!!

    @Test fun statementOutputsFromRealStatement() {
        val outs = LocalJudge.statementOutputs(detail.contentHtml)
        assertEquals(3, outs.size)
        assertEquals("""[["bat"],["nat","tan"],["ate","eat","tea"]]""", outs[0])
        assertEquals("""[[""]]""", outs[1])
    }

    @Test fun statementOutputsFromPreFormat() {
        val html = "<pre><strong>Input:</strong> nums = [2,7,11,15], target = 9\n<strong>Output:</strong> [0,1]\n<strong>Explanation:</strong> x</pre>"
        assertEquals(listOf("[0,1]"), LocalJudge.statementOutputs(html))
    }

    @Test fun modes() {
        assertEquals("any_order", LocalJudge.mode(detail))
        assertEquals("k_first", LocalJudge.mode(detail.copy(slug = "remove-element")))
        assertEquals("exact", LocalJudge.mode(detail.copy(contentHtml = "<p>Return the sum.</p>")))
    }

    @Test fun parsesHarnessResult() {
        val json = """{"status":"ok","python":"3.14.0","cases":[{"output":"[0,1]","ok":true,"stdout":"","error":null,"ms":0.5},
            {"output":null,"ok":null,"stdout":"x\n","error":"Traceback (most recent call last):\n  File \"solution.py\", line 7, in f\n    y\nNameError: y","ms":0.1}]}"""
        val r = LocalJudge.parse(json, listOf("a", "b"), listOf("[0,1]", "[1,2]"))
        assertEquals(Judge.Status.RUNTIME_ERROR, r.status)
        assertEquals("Run locally", r.source)
        assertTrue(r.cases[0].passed)
        assertFalse(r.cases[1].passed)
        assertEquals(7, LocalJudge.errorLine(r.error))
    }

    @Test fun compileErrorAndUnsupported() {
        val ce = LocalJudge.parse("""{"status":"compile_error","error":"SyntaxError","cases":[]}""", listOf("a"), listOf(null))
        assertEquals("Compile Error", ce.verdict)
        val un = LocalJudge.parse("""{"status":"unsupported","error":"Input type Node isn't supported","cases":[]}""", listOf("a"), listOf(null))
        assertEquals("Not supported locally", un.verdict)
    }

    /** Rev 24: limits give the run their verdict; a killed run keeps the finished cases. */
    @Test fun limitsAndKilledRuns() {
        val tle = """{"status":"ok","python":"3.14.8","cases":[{"output":"[0,1]","ok":true,"stdout":"","error":null,"ms":1},
            {"output":null,"ok":false,"stdout":"","error":"Traceback (most recent call last):\n  File \"solution.py\", line 4, in f\n    while True: pass\nTime Limit Exceeded: still running after 3 s","ms":3000,"limit":"time"}]}"""
        val r = LocalJudge.parse(tle, listOf("a", "b"), listOf("[0,1]", null))
        assertEquals(Judge.Status.TIME_LIMIT, r.status)
        assertEquals("Time Limit Exceeded", r.verdict)
        assertTrue(r.cases[0].passed)
        assertEquals(4, LocalJudge.errorLine(r.error))

        val progress = """{"python":"3.14.8","status":"ok","cases":[{"output":"[0,1]","ok":true,"stdout":"","error":null,"ms":1},
            {"output":null,"ok":null,"stdout":"","error":null,"ms":0}]}"""
        val k = LocalJudge.parseKilled(progress, listOf("a", "b", "c"), listOf("[0,1]", null, null), "Time Limit Exceeded: stuck", "time")
        assertEquals(Judge.Status.TIME_LIMIT, k.status)
        assertTrue(k.cases[0].passed)
        assertEquals("Time Limit Exceeded: stuck", k.cases[1].error)
        assertTrue(k.cases[2].error!!.startsWith("Not run"))
        val none = LocalJudge.parseKilled(null, listOf("a"), listOf(null), "Memory Limit Exceeded: killed", "memory")
        assertEquals(Judge.Status.MEMORY_LIMIT, none.status)
        assertEquals("Memory Limit Exceeded: killed", none.cases[0].error)
    }

    /** Rev 31: a Codeforces problem runs under its own time and memory limits. */
    @Test fun codeforcesLimits() {
        val cf = dev.titan.code.data.codeforces.CodeforcesApi.parseStatement(
            javaClass.getResource("/codeforces/4A.html")!!.readText(), "cf:4A").detail
        assertEquals(1000L, LocalJudge.caseLimitMs(cf))
        assertEquals(64, LocalJudge.memLimitMb(cf))
        val req = org.json.JSONObject(LocalJudge.request("print(1)", cf, listOf("8\n"), listOf("YES")))
        assertEquals(1000L, req.getLong("caseLimitMs"))
        assertEquals(64, req.getInt("memLimitMb"))
        assertEquals(15_000L, LocalJudge.overallLimitMs(10, 1000L))

        fun with(time: String, memory: String) = cf.copy(metaData = org.json.JSONObject(cf.metaData)
            .put("timeLimit", time).put("memoryLimit", memory).toString())
        assertEquals(2500L, LocalJudge.caseLimitMs(with("2.5 seconds", "256 megabytes")))
        assertEquals(256, LocalJudge.memLimitMb(with("2.5 seconds", "256 megabytes")))
        // Unreadable or missing: the defaults.
        assertEquals(LocalJudge.CASE_LIMIT_MS, LocalJudge.caseLimitMs(with("", "")))
        assertEquals(null, LocalJudge.memLimitMb(with("", "")))
        assertEquals(LocalJudge.CASE_LIMIT_MS, LocalJudge.caseLimitMs(cf.copy(metaData = "")))

        // LeetCode keeps the fixed limit and the harness's memory default.
        assertEquals(LocalJudge.CASE_LIMIT_MS, LocalJudge.caseLimitMs(detail))
        assertEquals(null, LocalJudge.memLimitMb(detail))
        assertFalse(org.json.JSONObject(LocalJudge.request("", detail, emptyList(), emptyList())).has("memLimitMb"))
    }

    /** Rev 24: the constraint text the stress generator reads, and its result. */
    @Test fun stressRequestAndResult() {
        val text = LocalJudge.constraintsText(detail.contentHtml)
        assertTrue(text, text.contains("1 <= strs.length <= 10^4"))
        assertTrue(text.contains("strs[i] consists of lowercase English letters."))
        assertEquals("0 <= a_i < n", LocalJudge.constraintsText("<code>0 &lt;= a<sub>i</sub> &lt; n</code>"))
        val req = org.json.JSONObject(LocalJudge.stressRequest("code", detail, listOf("[\"a\"]"), listOf(null), listOf("NeetCode: 1. Sorting" to "class Solution: pass")))
        assertEquals("stress", req.getString("mode"))
        assertEquals("any_order", req.getString("checkMode"))
        assertEquals(49, req.getInt("number"))
        assertEquals("NeetCode: 1. Sorting", req.getJSONArray("references").getJSONObject(0).getString("label"))

        val s = LocalJudge.parseStress("""{"status":"ok","cases":[],"stress":{"tried":40,"skipped":2,"seconds":3.5,"reference":"NeetCode: 1. Sorting",
            "mismatch":{"input":"[\"ab\",\"ba\"]","output":"[[\"ab\"],[\"ba\"]]","expected":"[[\"ab\",\"ba\"]]","error":null,"note":null},
            "big":null,"assumptions":["strs: string[], length 1…10^4"],"warnings":[]}}""")
        assertEquals(40, s.tried)
        assertEquals("[\"ab\",\"ba\"]", s.mismatch!!.input)
        assertEquals(null, s.mismatch!!.error)
        assertEquals(listOf("strs: string[], length 1…10^4"), s.assumptions)
        val none = LocalJudge.parseStress("""{"status":"unsupported","error":"No reference solution","cases":[]}""")
        assertEquals("No reference solution", none.error)
    }

    @Test fun referenceCodeBlocks() {
        val md = "## Prerequisites\ntext\n## 1. Brute Force\n### Intuition\n```python\nclass Solution:\n    pass\n```\n```java\nclass X {}\n```\n" +
            "## 2. Hash Map\n```python\nclass Solution:\n    def f(self): return 1\n```\n"
        val blocks = dev.titan.code.data.Repository.codeBlocks(md)
        assertEquals(listOf("1. Brute Force", "2. Hash Map"), blocks.map { it.first })
        assertTrue(blocks[1].second.contains("return 1"))
    }
}
