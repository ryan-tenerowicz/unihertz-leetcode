package dev.titan.code.data.leetcode

import org.json.JSONArray
import org.json.JSONObject

/**
 * Run (`interpret_solution`) and Submit on leetcode.com (PLAN §6.2, M3). Both start a job and
 * then poll `GET /submissions/detail/{id}/check/` until `state` is `SUCCESS`. The two `check`
 * responses have different shapes, so each has its own parser (PLAN §6.2).
 *
 * The shapes follow LeetCode's web client as of 2026. Unlike the GraphQL queries, these need a
 * signed-in session, so the test fixtures are written from the documented shape and must be
 * confirmed against a real response on the phone (owner test queue, M3).
 */
object Judge {

    fun runPath(slug: String) = "/problems/$slug/interpret_solution/"
    fun submitPath(slug: String) = "/problems/$slug/submit/"
    fun checkPath(id: String) = "/submissions/detail/$id/check/"

    fun runBody(questionId: String, code: String, dataInput: String): String =
        JSONObject().put("lang", "python3").put("question_id", questionId).put("typed_code", code).put("data_input", dataInput).toString()

    fun submitBody(questionId: String, code: String): String =
        JSONObject().put("lang", "python3").put("question_id", questionId).put("typed_code", code).toString()

    class JudgeError(message: String) : Exception(message)

    /** The id to poll after starting a Run. */
    fun parseRunStart(body: String): String {
        val o = obj(body)
        return o.optString("interpret_id").takeIf { it.isNotEmpty() } ?: throw JudgeError(startError(o))
    }

    /** The id to poll after starting a Submit. */
    fun parseSubmitStart(body: String): String {
        val o = obj(body)
        val id = o.opt("submission_id")?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
        return id ?: throw JudgeError(startError(o))
    }

    private fun startError(o: JSONObject) =
        o.optString("error").ifEmpty { o.optString("detail") }.ifEmpty { "LeetCode didn't start the job" }

    /** True once the job is finished; PENDING and STARTED mean poll again. */
    fun isDone(body: String): Boolean = obj(body).optString("state") == "SUCCESS"

    /** Status codes shared by both responses. */
    enum class Status(val code: Int, val label: String) {
        ACCEPTED(10, "Accepted"),
        WRONG_ANSWER(11, "Wrong Answer"),
        MEMORY_LIMIT(12, "Memory Limit Exceeded"),
        OUTPUT_LIMIT(13, "Output Limit Exceeded"),
        TIME_LIMIT(14, "Time Limit Exceeded"),
        RUNTIME_ERROR(15, "Runtime Error"),
        INTERNAL_ERROR(16, "Internal Error"),
        COMPILE_ERROR(20, "Compile Error"),
        TIMEOUT(30, "Timeout"),
        UNKNOWN(-1, "Unknown");

        companion object {
            fun of(code: Int) = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    data class Case(
        val index: Int,
        val input: String,
        val output: String?,
        val expected: String?,
        val stdout: String,
        /** The local runner's verdict (any-order, float tolerance…); null means compare text. */
        val ok: Boolean? = null,
        val error: String? = null,
        val ms: Double? = null,
        /** The local runner's remark on this case (API call counts, why a "copy" isn't one…). */
        val note: String? = null,
    ) {
        val passed get() = ok ?: (output != null && expected != null && output == expected)
    }

    /** The result of Run: one [Case] per test input. */
    data class RunResult(
        /** "Run on LeetCode" or "Run locally". */
        val source: String = "Run on LeetCode",
        val status: Status,
        val statusMsg: String,
        /** Run's `status_msg` says the code ran; correctness is `correct_answer`. */
        val correct: Boolean,
        val runtime: String,
        val memory: String,
        val cases: List<Case>,
        val error: String?,
    ) {
        val verdict get() = when {
            status != Status.ACCEPTED -> statusMsg
            correct -> "Accepted"
            else -> "Wrong Answer"
        }
        val passedCount get() = cases.count { it.passed }
    }

    /**
     * [inputs] are the cases that were sent, so each output lines up with its input. LeetCode's
     * lists can carry one extra trailing entry, which is ignored.
     */
    fun parseRun(body: String, inputs: List<String>): RunResult {
        val o = obj(body)
        val status = Status.of(o.optInt("status_code", -1))
        val answers = o.optJSONArray("code_answer").strings()
        val expected = o.optJSONArray("expected_code_answer").strings()
        val stdout = o.optJSONArray("std_output_list").strings()
        val cases = inputs.mapIndexed { i, input ->
            Case(i, input, answers.getOrNull(i), expected.getOrNull(i), stdout.getOrNull(i).orEmpty())
        }
        return RunResult(
            status = status,
            statusMsg = o.optString("status_msg", status.label),
            correct = o.optBoolean("correct_answer", false),
            runtime = o.optString("status_runtime"),
            memory = o.optString("status_memory"),
            cases = cases,
            error = error(o),
        )
    }

    /** The result of Submit: totals, and the first failing case when there is one. */
    data class SubmitResult(
        val status: Status,
        val statusMsg: String,
        val totalCorrect: Int,
        val totalTestcases: Int,
        val runtime: String,
        val runtimePercentile: Double?,
        val memory: String,
        val memoryPercentile: Double?,
        val lastInput: String?,
        val output: String?,
        val expected: String?,
        val stdout: String,
        val error: String?,
        /** "Submit to LeetCode" or "Submit to NeetCode" (rev 13). */
        val source: String = "Submit to LeetCode",
    ) {
        val accepted get() = status == Status.ACCEPTED
    }

    fun parseSubmit(body: String): SubmitResult {
        val o = obj(body)
        val status = Status.of(o.optInt("status_code", -1))
        return SubmitResult(
            status = status,
            statusMsg = o.optString("status_msg", status.label),
            totalCorrect = o.optInt("total_correct"),
            totalTestcases = o.optInt("total_testcases"),
            runtime = o.optString("status_runtime"),
            runtimePercentile = o.optDoubleOrNull("runtime_percentile"),
            memory = o.optString("status_memory"),
            memoryPercentile = o.optDoubleOrNull("memory_percentile"),
            lastInput = o.optStringOrNull("last_testcase") ?: o.optStringOrNull("input"),
            output = o.optStringOrNull("code_output"),
            expected = o.optStringOrNull("expected_output"),
            stdout = o.optString("std_output"),
            error = error(o),
        )
    }

    private fun error(o: JSONObject): String? =
        o.optStringOrNull("full_compile_error") ?: o.optStringOrNull("compile_error")
            ?: o.optStringOrNull("full_runtime_error") ?: o.optStringOrNull("runtime_error")

    // ---- test inputs ----------------------------------------------------------------------

    /**
     * How many lines one test input takes: the number of parameters in `metaData`, or 2 for
     * design problems (operations, arguments). Defaults to 1 if `metaData` can't be read.
     */
    fun linesPerCase(metaData: String): Int = try {
        val m = JSONObject(metaData)
        when {
            m.has("linesPerCase") -> m.getInt("linesPerCase")
            m.has("classname") || m.has("systemdesign") -> 2
            else -> m.optJSONArray("params")?.length()?.takeIf { it > 0 } ?: 1
        }
    } catch (e: Exception) {
        1
    }

    /** Split LeetCode's `exampleTestcases` (all inputs, one value per line) into cases. */
    fun splitCases(all: String, linesPerCase: Int): List<String> {
        val lines = all.replace("\r\n", "\n").trimEnd('\n').split("\n")
        if (lines.size == 1 && lines[0].isEmpty()) return emptyList()
        return lines.chunked(linesPerCase.coerceAtLeast(1)).map { it.joinToString("\n") }
    }

    // ---- helpers ------------------------------------------------------------------------

    private fun obj(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: Exception) {
        throw JudgeError("Unexpected response from LeetCode: ${body.take(80)}")
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
