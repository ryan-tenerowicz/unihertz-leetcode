package dev.titan.code

import dev.titan.code.data.leetcode.Judge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run/Submit parsers (PLAN §10). Fixtures follow LeetCode's documented `check` shapes; replace
 * them with real responses once the owner has run and submitted on the phone.
 */
class JudgeTest {
    private val twoSumInputs = listOf("[2,7,11,15]\n9", "[3,2,4]\n6", "[3,3]\n6")

    @Test fun runAcceptedAllPass() {
        val body = """{"status_code":10,"status_msg":"Accepted","state":"SUCCESS","run_success":true,"correct_answer":true,
            "code_answer":["[0,1]","[1,2]","[0,1]",""],"expected_code_answer":["[0,1]","[1,2]","[0,1]",""],
            "std_output_list":["","hi\n","",""],"status_runtime":"0 ms","status_memory":"17.4 MB"}"""
        val r = Judge.parseRun(body, twoSumInputs)
        assertEquals("Accepted", r.verdict)
        assertEquals(3, r.passedCount)
        assertEquals("hi\n", r.cases[1].stdout)
        assertEquals("[3,2,4]\n6", r.cases[1].input)
        assertNull(r.error)
    }

    @Test fun runWrongAnswerIsNotAccepted() {
        val body = """{"status_code":10,"status_msg":"Accepted","state":"SUCCESS","correct_answer":false,
            "code_answer":["[0,1]","[0,0]","[0,1]"],"expected_code_answer":["[0,1]","[1,2]","[0,1]"],"std_output_list":["","",""]}"""
        val r = Judge.parseRun(body, twoSumInputs)
        assertEquals("Wrong Answer", r.verdict)
        assertEquals(2, r.passedCount)
        assertFalse(r.cases[1].passed)
    }

    @Test fun runRuntimeError() {
        val body = """{"status_code":15,"status_msg":"Runtime Error","state":"SUCCESS","runtime_error":"Line 3: NameError",
            "full_runtime_error":"NameError: name 'x' is not defined\n    x\nLine 3 in twoSum (Solution.py)","code_answer":[],"expected_code_answer":["[0,1]"]}"""
        val r = Judge.parseRun(body, twoSumInputs.take(1))
        assertEquals("Runtime Error", r.verdict)
        assertTrue(r.error!!.startsWith("NameError"))
        assertNull(r.cases[0].output)
    }

    @Test fun submitAcceptedAndWrong() {
        val ok = Judge.parseSubmit("""{"status_code":10,"status_msg":"Accepted","state":"SUCCESS","total_correct":63,"total_testcases":63,
            "status_runtime":"52 ms","runtime_percentile":75.31,"status_memory":"17.9 MB","memory_percentile":null}""")
        assertTrue(ok.accepted)
        assertEquals(75.31, ok.runtimePercentile!!, 0.001)
        assertNull(ok.memoryPercentile)

        val wa = Judge.parseSubmit("""{"status_code":11,"status_msg":"Wrong Answer","state":"SUCCESS","total_correct":3,"total_testcases":63,
            "last_testcase":"[3,3]\n6","code_output":"[0,0]","expected_output":"[0,1]","std_output":""}""")
        assertFalse(wa.accepted)
        assertEquals("[3,3]\n6", wa.lastInput)
        assertEquals("[0,1]", wa.expected)
    }

    @Test fun startIdsAndPending() {
        assertEquals("runcode_1_abc", Judge.parseRunStart("""{"interpret_id":"runcode_1_abc","test_case":"x"}"""))
        assertEquals("123", Judge.parseSubmitStart("""{"submission_id":123}"""))
        assertFalse(Judge.isDone("""{"state":"PENDING"}"""))
        assertTrue(Judge.isDone("""{"state":"SUCCESS"}"""))
        try {
            Judge.parseSubmitStart("""{"error":"You have attempted to run code too soon"}""")
            throw AssertionError()
        } catch (e: Judge.JudgeError) {
            assertEquals("You have attempted to run code too soon", e.message)
        }
    }

    @Test fun splitsExampleCases() {
        val meta = """{"name":"twoSum","params":[{"name":"nums","type":"integer[]"},{"name":"target","type":"integer"}],"return":{"type":"integer[]"}}"""
        assertEquals(2, Judge.linesPerCase(meta))
        assertEquals(twoSumInputs, Judge.splitCases("[2,7,11,15]\n9\n[3,2,4]\n6\n[3,3]\n6\n", 2))
        assertEquals(2, Judge.linesPerCase("""{"classname":"LRUCache","constructor":{}}"""))
        assertEquals(1, Judge.linesPerCase("not json"))
        assertTrue(Judge.splitCases("", 2).isEmpty())
    }

    @Test fun bodies() {
        val o = JSONObject(Judge.runBody("1", "code", "a\nb"))
        assertEquals("python3", o.getString("lang"))
        assertEquals("a\nb", o.getString("data_input"))
    }
}
