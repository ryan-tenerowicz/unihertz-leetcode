package dev.titan.code.data.codeforces

import dev.titan.code.data.HtmlClean
import dev.titan.code.data.Platform
import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.ProblemSummary
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * codeforces.com (PLAN §6.3, rev 13). The official API gives the problem list and submission
 * verdicts; statements are web pages (behind Cloudflare, so they're loaded by a hidden WebView,
 * [CodeforcesWeb]) and parsed here. Pure functions, tested on fixtures.
 *
 * Checked 2026-11-02: `api/problemset.problems` and `api/user.status` answer a plain client;
 * problem pages answer it with a Cloudflare "Just a moment" page.
 */
object CodeforcesApi {
    const val ORIGIN = "https://codeforces.com"
    const val PROBLEMS_URL = "$ORIGIN/api/problemset.problems"
    const val LOGIN_URL = "$ORIGIN/enter"
    const val SUBMIT_URL = "$ORIGIN/problemset/submit"

    fun statusUrl(handle: String, count: Int = 10) = "$ORIGIN/api/user.status?handle=$handle&from=1&count=$count"
    fun problemUrl(contestId: Int, index: String) = "$ORIGIN/problemset/problem/$contestId/$index"
    fun submissionUrl(contestId: Int, id: Long) = "$ORIGIN/contest/$contestId/submission/$id"

    class ApiError(message: String) : Exception(message)

    /** `cf:1850A` → (1850, "A"). */
    fun split(key: String): Pair<Int, String> {
        val id = Platform.CODEFORCES.siteId(key)
        val m = Regex("^(\\d+)([A-Za-z][A-Za-z0-9]*)$").find(id) ?: throw ApiError("Not a Codeforces problem: $key")
        return m.groupValues[1].toInt() to m.groupValues[2]
    }

    // ---- problem list -----------------------------------------------------------------------

    /** Programming problems, newest contest first, with the rating as the difficulty ("" if unrated). */
    fun parseProblems(body: String): List<ProblemSummary> {
        val r = result(body) as JSONObject
        val stats = r.optJSONArray("problemStatistics")?.let { a ->
            (0 until a.length()).associate { a.getJSONObject(it).let { s -> "${s.optInt("contestId")}${s.optString("index")}" to s.optInt("solvedCount") } }
        } ?: emptyMap()
        val ps = r.getJSONArray("problems")
        return (0 until ps.length()).map { ps.getJSONObject(it) }
            .filter { it.optString("type", "PROGRAMMING") == "PROGRAMMING" && it.has("contestId") }
            .map { p ->
                val code = "${p.getInt("contestId")}${p.getString("index")}"
                ProblemSummary(
                    slug = Platform.CODEFORCES.key(code),
                    frontendId = p.getInt("contestId"),
                    title = p.getString("name"),
                    difficulty = if (p.has("rating")) p.getInt("rating").toString() else "",
                    paidOnly = false,
                    acRate = (stats[code] ?: 0).toDouble(),
                    tags = p.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it).replace(' ', '-') } } ?: emptyList(),
                    status = null,
                )
            }
            .sortedWith(compareByDescending<ProblemSummary> { it.frontendId }.thenBy { Platform.CODEFORCES.siteId(it.slug) })
    }

    // ---- a statement ------------------------------------------------------------------------

    /** The sample tests: (input, output) as plain text with newlines. */
    data class Statement(val detail: ProblemDetail, val samples: List<Pair<String, String>>)

    fun parseStatement(html: String, key: String): Statement {
        val doc = Jsoup.parse(html, ORIGIN)
        val st = doc.selectFirst("div.problem-statement") ?: throw ApiError(
            if (doc.title().contains("Just a moment")) "Codeforces asked for a browser check" else "No statement on that page"
        )
        val header = st.selectFirst("div.header")
        val title = header?.selectFirst(".title")?.text()?.replace(Regex("^[A-Z][0-9]*\\.\\s*"), "") ?: Platform.CODEFORCES.siteId(key)
        fun limit(cls: String) = header?.selectFirst(cls)?.let { e -> e.selectFirst(".property-title")?.remove(); e.text().trim() } ?: ""
        val time = limit(".time-limit")
        val memory = limit(".memory-limit")
        val samples = st.select("div.sample-test").flatMap { box ->
            val ins = box.select("div.input pre").map(::preText)
            val outs = box.select("div.output pre").map(::preText)
            ins.zip(outs)
        }
        header?.remove()
        // Absolute image URLs, so they can be saved for offline (PLAN §8.2).
        st.select("img[src]").forEach { it.attr("src", it.absUrl("src")) }
        st.select("div.section-title").forEach { it.tagName("h3") }
        val tags = doc.select("span.tag-box").map { it.text().trim() }
        val rating = tags.firstOrNull { it.startsWith("*") }?.removePrefix("*")?.toIntOrNull()
        val (contestId, index) = split(key)
        val meta = JSONObject()
            .put("stdin", true)
            .put("timeLimit", time)
            .put("memoryLimit", memory)
            .put("contestId", contestId)
            .put("index", index)
            .put("outputs", JSONArray(samples.map { it.second }))
        val body = buildString {
            append("<div class=\"meta\">⏱ ${HtmlClean.escape(time)} · ▦ ${HtmlClean.escape(memory)}</div>")
            append(tex(st.html()))
        }
        val detail = ProblemDetail(
            slug = key,
            questionId = "$contestId$index",
            frontendId = contestId,
            title = title,
            difficulty = rating?.toString() ?: "",
            paidOnly = false,
            contentHtml = HtmlClean.statement(body),
            starter = STARTER,
            exampleTestcases = JSONArray(samples.map { it.first }).toString(),
            metaData = meta.toString(),
            hints = emptyList(),
            tags = tags.filterNot { it.startsWith("*") },
            similarJson = "[]",
        )
        return Statement(detail, samples)
    }

    /** A `<pre>` sample: newer pages put each line in a `div.test-example-line`, older ones use `<br>`. */
    private fun preText(pre: Element): String {
        val sb = StringBuilder()
        for (n in pre.childNodes()) {
            when {
                n is TextNode -> sb.append(n.wholeText)
                n is Element && n.tagName() == "br" -> sb.append('\n')
                n is Element -> { sb.append(n.wholeText()); if (n.tagName() == "div") sb.append('\n') }
            }
        }
        return sb.toString().replace("\r\n", "\n").trim('\n').lines().joinToString("\n") { it.trimEnd() } + "\n"
    }

    /** A Python template for stdin/stdout problems (rev 13, *agent decision*). */
    const val STARTER = "import sys\ninput = sys.stdin.readline\n\n\ndef main():\n    \n\n\nmain()\n"

    /** Sample inputs saved with a statement. */
    fun sampleInputs(exampleTestcases: String): List<String> =
        runCatching { JSONArray(exampleTestcases).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    fun sampleOutputs(metaData: String): List<String> =
        runCatching { JSONObject(metaData).getJSONArray("outputs").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    // ---- TeX ($$$…$$$) to plain HTML ----------------------------------------------------------

    private val symbols = linkedMapOf(
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\ne" to "≠",
        "\\cdot" to "·", "\\times" to "×", "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…",
        "\\lfloor" to "⌊", "\\rfloor" to "⌋", "\\lceil" to "⌈", "\\rceil" to "⌉", "\\oplus" to "⊕",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\infty" to "∞", "\\sum" to "Σ", "\\prod" to "Π",
        "\\pm" to "±", "\\notin" to "∉", "\\in" to "∈", "\\subseteq" to "⊆", "\\subset" to "⊂", "\\cup" to "∪", "\\cap" to "∩",
        "\\land" to "∧", "\\lor" to "∨", "\\neg" to "¬", "\\forall" to "∀", "\\exists" to "∃", "\\mid" to "|",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\lambda" to "λ",
        "\\mu" to "μ", "\\pi" to "π", "\\sigma" to "σ", "\\phi" to "φ", "\\omega" to "ω", "\\Delta" to "Δ",
        "\\bmod" to " mod ", "\\mod" to " mod ", "\\gcd" to "gcd", "\\max" to "max", "\\min" to "min", "\\log" to "log",
        "\\lbrace" to "{", "\\rbrace" to "}", "\\{" to "{", "\\}" to "}", "\\%" to "%", "\\#" to "#", "\\&" to "&amp;",
        "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ", "\\!" to "", "\\ " to " ", "\\left" to "", "\\right" to "",
    )

    /** Replace `$$$…$$$` (inline) and `$$$$$$…$$$$$$` (display) maths with readable text; the page has JavaScript off. */
    fun tex(html: String): String {
        val display = Regex("\\$\\$\\$\\$\\$\\$(.+?)\\$\\$\\$\\$\\$\\$", RegexOption.DOT_MATCHES_ALL)
        val inline = Regex("\\$\\$\\$(.+?)\\$\\$\\$", RegexOption.DOT_MATCHES_ALL)
        return inline.replace(display.replace(html) { "<div class=\"math\">${texBody(it.groupValues[1])}</div>" }) {
            "<span class=\"math\">${texBody(it.groupValues[1])}</span>"
        }
    }

    fun texBody(src: String): String {
        var s = src
        // Commands with a braced argument whose content is kept.
        repeat(3) {
            s = Regex("\\\\(?:text|textbf|textit|mathrm|mathit|mathbf|mathtt|operatorname|texttt|bf|it|rm|mathcal|mathbb)\\{([^{}]*)}").replace(s) { it.groupValues[1] }
            s = Regex("\\\\frac\\{([^{}]*)}\\{([^{}]*)}").replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
            s = Regex("\\\\sqrt\\{([^{}]*)}").replace(s) { "√(${it.groupValues[1]})" }
            s = Regex("\\\\(?:overline|bar)\\{([^{}]*)}").replace(s) { "${it.groupValues[1]}̅" }
        }
        for ((k, v) in symbols) s = s.replace(Regex(Regex.escape(k) + "(?![a-zA-Z])"), Regex.escapeReplacement(v))
        s = Regex("\\^\\{([^{}]*)}").replace(s) { "<sup>${it.groupValues[1]}</sup>" }
        s = Regex("\\^(\\\\?\\w)").replace(s) { "<sup>${it.groupValues[1]}</sup>" }
        s = Regex("_\\{([^{}]*)}").replace(s) { "<sub>${it.groupValues[1]}</sub>" }
        s = Regex("_(\\w)").replace(s) { "<sub>${it.groupValues[1]}</sub>" }
        s = Regex("\\\\([a-zA-Z]+)").replace(s) { it.groupValues[1] }
        return s.replace("{", "").replace("}", "")
    }

    // ---- verdicts ---------------------------------------------------------------------------

    /** One submission from `user.status`. */
    data class Verdict(
        val id: Long,
        val contestId: Int,
        val index: String,
        /** Null or "TESTING" while it's judged. */
        val verdict: String?,
        val passedTests: Int,
        val ms: Int,
        val bytes: Long,
        val language: String,
        val createdSec: Long,
    ) {
        val done get() = verdict != null && verdict != "TESTING"
        val accepted get() = verdict == "OK"

        /** Codeforces' own wording: "Wrong answer on test 3". */
        val label: String get() {
            val test = passedTests + 1
            return when (verdict) {
                null, "TESTING" -> "Running on test $test"
                "OK" -> "Accepted"
                "WRONG_ANSWER" -> "Wrong answer on test $test"
                "TIME_LIMIT_EXCEEDED" -> "Time limit exceeded on test $test"
                "MEMORY_LIMIT_EXCEEDED" -> "Memory limit exceeded on test $test"
                "RUNTIME_ERROR" -> "Runtime error on test $test"
                "IDLENESS_LIMIT_EXCEEDED" -> "Idleness limit exceeded on test $test"
                "PRESENTATION_ERROR" -> "Presentation error on test $test"
                "COMPILATION_ERROR" -> "Compilation error"
                "CHALLENGED" -> "Hacked"
                "SKIPPED" -> "Skipped"
                "PARTIAL" -> "Partial result"
                else -> verdict.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }
        val time get() = "$ms ms"
        val memory get() = "${bytes / 1024} KB"
    }

    /** The newest submission to [contestId]/[index] made at or after [sinceSec], if any. */
    fun parseStatus(body: String, contestId: Int, index: String, sinceSec: Long): Verdict? {
        val a = result(body) as JSONArray
        return (0 until a.length()).map { a.getJSONObject(it) }.map { s ->
            val p = s.getJSONObject("problem")
            Verdict(
                id = s.getLong("id"),
                contestId = p.optInt("contestId"),
                index = p.optString("index"),
                verdict = s.optString("verdict").takeIf { it.isNotEmpty() && !s.isNull("verdict") },
                passedTests = s.optInt("passedTestCount"),
                ms = s.optInt("timeConsumedMillis"),
                bytes = s.optLong("memoryConsumedBytes"),
                language = s.optString("programmingLanguage"),
                createdSec = s.optLong("creationTimeSeconds"),
            )
        }.filter { it.contestId == contestId && it.index == index && it.createdSec >= sinceSec }.maxByOrNull { it.id }
    }

    /** The `result` of an API reply, or [ApiError] with its `comment`. */
    fun result(body: String): Any {
        val o = try { JSONObject(body) } catch (e: Exception) {
            throw ApiError(if (body.contains("Just a moment")) "Codeforces asked for a browser check" else "Unexpected reply from Codeforces")
        }
        if (o.optString("status") != "OK") throw ApiError(o.optString("comment", "Codeforces API error"))
        return o.get("result")
    }

    /** Languages offered on submit (rev 13): the form's option text is matched, since ids change over time. */
    enum class Language(val label: String, val js: String) {
        PYPY3_64("PyPy 3 (64-bit)", "t.indexOf('PyPy 3')>=0 && t.indexOf('64')>=0"),
        PYPY3("PyPy 3", "t.indexOf('PyPy 3')>=0 && t.indexOf('64')<0"),
        PYTHON3("Python 3", "t.indexOf('Python 3')==0");

        companion object {
            fun byName(name: String?) = entries.firstOrNull { it.name == name } ?: PYPY3_64
        }
    }
}
