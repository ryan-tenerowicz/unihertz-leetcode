package dev.titan.code.runner

import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.leetcode.Judge
import org.json.JSONArray
import org.json.JSONObject

/**
 * The pure parts of local runs (PLAN §7): the request for the Python harness, the result-checking
 * mode for a problem, expected outputs read from the statement, and mapping the harness result to
 * the same [Judge.RunResult] a LeetCode run produces, so the Results pane shows both alike.
 */
object LocalJudge {

    /** Problems whose output is "k, then the first k elements" (PLAN §7 table). */
    private val K_FIRST = setOf(
        "remove-duplicates-from-sorted-array", "remove-element", "remove-duplicates-from-sorted-array-ii",
    )

    /** Checking mode (PLAN §7): overrides first, then "any order" in the statement. */
    fun mode(d: ProblemDetail): String = when {
        d.platform == dev.titan.code.data.Platform.CODEFORCES -> "stdin"
        dev.titan.code.data.Platform.LEETCODE.siteId(d.slug) in K_FIRST -> "k_first"
        d.contentHtml?.replace(Regex("<[^>]+>"), "")?.contains(Regex("in\\s+any\\s+order", RegexOption.IGNORE_CASE)) == true -> "any_order"
        else -> "exact"
    }

    /** Per-case time limit on the phone (rev 24); the harness stops a case that runs longer. */
    const val CASE_LIMIT_MS = 3000L

    /**
     * The per-case time limit for [d] (rev 31): a Codeforces problem's own "time limit per test"
     * ("1 second", "2.5 seconds"), saved in its `metaData`; [CASE_LIMIT_MS] otherwise or when it
     * can't be read.
     */
    fun caseLimitMs(d: ProblemDetail): Long =
        codeforcesLimit(d, "timeLimit", Regex("""(\d+(?:\.\d+)?)\s*seconds?"""))
            ?.let { (it * 1000).toLong().coerceIn(100L, 30_000L) } ?: CASE_LIMIT_MS

    /**
     * The per-case memory limit in MB for [d] (rev 31): a Codeforces problem's "memory limit per
     * test" ("256 megabytes"); null to keep the harness default (512 MB of growth).
     */
    fun memLimitMb(d: ProblemDetail): Int? =
        codeforcesLimit(d, "memoryLimit", Regex("""(\d+(?:\.\d+)?)\s*megabytes?"""))
            ?.let { it.toInt().coerceIn(16, 2048) }

    private fun codeforcesLimit(d: ProblemDetail, key: String, pattern: Regex): Double? {
        if (d.platform != dev.titan.code.data.Platform.CODEFORCES) return null
        val text = runCatching { JSONObject(d.metaData).optString(key) }.getOrNull() ?: return null
        return pattern.find(text.lowercase())?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** How long the app waits for a whole run before killing the runner: every case at its limit, plus slack. */
    fun overallLimitMs(cases: Int, caseLimitMs: Long = CASE_LIMIT_MS): Long =
        (caseLimitMs * cases + 5000L).coerceIn(10_000L, 120_000L)

    fun request(code: String, d: ProblemDetail, inputs: List<String>, expected: List<String?>, progressPath: String? = null): String =
        JSONObject()
            .put("code", code)
            .put("metaData", d.metaData)
            .put("inputs", JSONArray(inputs))
            .put("expected", JSONArray(expected.map { it ?: JSONObject.NULL }))
            .put("mode", mode(d))
            // Picks the harness driver for problems with special types (titan_drivers.py, rev 23).
            // NeetCode's frontendId is the LeetCode number of the same problem.
            .put("number", if (d.platform == dev.titan.code.data.Platform.CODEFORCES) 0 else d.frontendId)
            .put("caseLimitMs", caseLimitMs(d))
            .apply { memLimitMb(d)?.let { put("memLimitMb", it) } }
            .apply { if (progressPath != null) put("progressPath", progressPath) }
            .toString()

    /**
     * The runner had to be killed ([why]): the cases it finished, from the progress file, with
     * the one it was on marked [why] and the rest not run.
     */
    fun parseKilled(progressJson: String?, inputs: List<String>, expected: List<String?>, why: String, limit: String?): Judge.RunResult {
        val o = progressJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject().put("status", "ok").put("cases", JSONArray())
        val cases = o.optJSONArray("cases") ?: JSONArray().also { o.put("cases", it) }
        // The last case in the file is the one that was running if it has no result yet.
        val last = if (cases.length() > 0) cases.getJSONObject(cases.length() - 1) else null
        val running = if (last != null && last.isNull("output") && last.isNull("error")) last
            else JSONObject().put("stdout", "").also { if (cases.length() < inputs.size) cases.put(it) }
        running.put("error", why).put("ok", false)
        if (limit != null) running.put("limit", limit)
        while (cases.length() < inputs.size) cases.put(JSONObject().put("error", "Not run: an earlier case had to be stopped.").put("stdout", ""))
        if (!o.has("status")) o.put("status", "ok")
        return parse(o.toString(), inputs, expected)
    }

    fun parse(json: String, inputs: List<String>, expected: List<String?>): Judge.RunResult {
        val o = JSONObject(json)
        val status = o.optString("status")
        val arr = o.optJSONArray("cases") ?: JSONArray()
        val cases = inputs.mapIndexed { i, input ->
            val c = arr.optJSONObject(i)
            // Random answers can't be compared with the example: shown as unchecked.
            val unchecked = c?.optBoolean("unchecked") == true
            Judge.Case(
                index = i,
                input = input,
                output = c?.takeUnless { it.isNull("output") }?.optString("output"),
                expected = if (unchecked) null else expected.getOrNull(i),
                stdout = c?.optString("stdout").orEmpty(),
                ok = c?.takeUnless { it.isNull("ok") }?.optBoolean("ok"),
                error = c?.takeUnless { it.isNull("error") }?.optString("error"),
                ms = c?.optDouble("ms"),
                note = c?.takeUnless { it.isNull("note") }?.optString("note")?.takeIf { it.isNotEmpty() },
            )
        }
        val caseError = cases.firstOrNull { it.error != null }?.error
        // Rev 24: a case stopped by a limit gives the run that verdict, as on LeetCode.
        val limit = (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it)?.optString("limit")?.takeIf(String::isNotEmpty) }
        val st = when {
            status == "compile_error" -> Judge.Status.COMPILE_ERROR
            status != "ok" -> Judge.Status.INTERNAL_ERROR
            limit == "time" -> Judge.Status.TIME_LIMIT
            limit == "memory" -> Judge.Status.MEMORY_LIMIT
            limit == "output" -> Judge.Status.OUTPUT_LIMIT
            caseError != null -> Judge.Status.RUNTIME_ERROR
            else -> Judge.Status.ACCEPTED
        }
        val unchecked = cases.any { it.expected == null }
        val total = cases.sumOf { it.ms ?: 0.0 }
        return Judge.RunResult(
            source = "Run locally",
            status = st,
            statusMsg = if (status == "unsupported") "Not supported locally" else st.label,
            correct = st == Judge.Status.ACCEPTED && !unchecked && cases.all { it.passed },
            runtime = "%.0f ms".format(total),
            memory = o.optString("python").takeIf(String::isNotEmpty)?.let { "Python $it" } ?: "",
            cases = cases,
            error = o.takeUnless { it.isNull("error") }?.optString("error")?.takeIf { it.isNotEmpty() } ?: caseError,
        )
    }

    /**
     * Expected outputs from the statement's examples ("Output: …"), in order. Handles both of
     * LeetCode's formats: `<pre>` blocks and `example-block` divs.
     */
    fun statementOutputs(html: String?): List<String> {
        if (html == null) return emptyList()
        val text = html
            .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</pre>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
            .replace("&nbsp;", " ").replace("&amp;", "&")
        return Regex("Output:\\s*([^\\n]+)").findAll(text).map { it.groupValues[1].trim() }.toList()
    }

    // ---- stress tests (rev 24) ------------------------------------------------------------

    /** What a stress test found (titan_stress.py). */
    data class Stress(
        val tried: Int,
        val skipped: Int,
        val seconds: Double,
        val reference: String,
        val mismatch: Mismatch?,
        val big: Big?,
        val assumptions: List<String>,
        val warnings: List<String>,
        /** Set when the stress test couldn't run (no reference, unknown types…). */
        val error: String? = null,
    )

    /** The first input where your answer differs from the reference's; [input] is ready to save as a test case. */
    data class Mismatch(val input: String, val output: String?, val expected: String?, val error: String?, val note: String?)

    /** The speed check on one input at the constraints' full size. */
    data class Big(val size: String, val ms: Double?, val refMs: Double?, val error: String?, val limit: String?)

    const val STRESS_SECONDS = 8
    /** The app's wait for a stress test: its budget, the big input for both solutions, and slack. */
    val STRESS_LIMIT_MS = (STRESS_SECONDS + 2 * CASE_LIMIT_MS / 1000 + 8) * 1000L

    fun stressRequest(code: String, d: ProblemDetail, inputs: List<String>, expected: List<String?>, references: List<Pair<String, String>>): String =
        JSONObject(request(code, d, inputs, expected))
            .put("mode", "stress")
            .put("checkMode", mode(d))
            .put("references", JSONArray(references.map { (label, c) -> JSONObject().put("label", label).put("code", c) }))
            .put("constraints", constraintsText(d.contentHtml))
            .put("seconds", STRESS_SECONDS)
            .toString()

    /** The statement as plain text for the constraint parser: `10<sup>4</sup>` → `10^4`, `a<sub>i</sub>` → `a_i`. */
    fun constraintsText(html: String?): String = (html ?: "")
        .replace(Regex("(?is)<sup>(.*?)</sup>"), "^$1")
        .replace(Regex("(?is)<sub>(.*?)</sub>"), "_$1")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</div>|</pre>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&le;", "<=").replace("&ge;", ">=").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

    fun parseStress(json: String): Stress {
        val o = JSONObject(json)
        if (o.optString("status") != "ok") {
            return Stress(0, 0, 0.0, "", null, null, emptyList(), emptyList(),
                error = o.optString("error").takeIf(String::isNotEmpty) ?: "The stress test couldn't run (${o.optString("status")}).")
        }
        val st = o.getJSONObject("stress")
        fun str(x: JSONObject, k: String) = x.takeUnless { it.isNull(k) }?.optString(k)
        fun list(k: String) = st.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val m = st.optJSONObject("mismatch")
        val b = st.optJSONObject("big")
        return Stress(
            tried = st.optInt("tried"), skipped = st.optInt("skipped"), seconds = st.optDouble("seconds", 0.0),
            reference = st.optString("reference"),
            mismatch = m?.let { Mismatch(it.getString("input"), str(it, "output"), str(it, "expected"), str(it, "error"), str(it, "note")) },
            big = b?.let { Big(it.optString("size"), it.takeUnless { x -> x.isNull("ms") }?.optDouble("ms"),
                it.takeUnless { x -> x.isNull("refMs") }?.optDouble("refMs"), str(it, "error"), str(it, "limit")) },
            assumptions = list("assumptions"), warnings = list("warnings"),
        )
    }

    /** The line number of the deepest user-code frame in a traceback, for "go to line". */
    fun errorLine(traceback: String?): Int? =
        traceback?.let { Regex("File \"solution\\.py\", line (\\d+)").findAll(it).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() }
}
