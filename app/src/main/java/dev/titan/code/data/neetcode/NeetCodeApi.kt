package dev.titan.code.data.neetcode

import dev.titan.code.data.HtmlClean
import dev.titan.code.data.Markdown
import dev.titan.code.data.Platform
import dev.titan.code.data.ProblemCollection
import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.ProblemSummary
import dev.titan.code.data.leetcode.Judge
import org.json.JSONArray
import org.json.JSONObject

/**
 * neetcode.io (PLAN §6.3, rev 13). NeetCode runs its own judge, with its own problem ids
 * (`two-integer-sum`, not LeetCode's `two-sum`), behind Firebase callable functions:
 *
 * - `getProblemMetadataFunctionHttp {problemId}`: statement (Markdown), starter code, example
 *   inputs, the solution article. Works without signing in for free problems.
 * - `runCodeFunctionHttp {problemId, rawCode, lang, testCases}`: one result per test case.
 * - `executeCodeFunctionHttp {problemId, rawCode, lang}`: the submission.
 *
 * Run and Submit need a Firebase ID token (`Authorization: Bearer …`), minted from the refresh
 * token saved at sign-in ([NeetCodeHttp]). Request bodies are `{"data": …}`; replies are
 * `{"data": …}` or `{"error": {"message", "status"}}`. Checked on 2026-11-02 against the site's
 * own client code; the result shape (Judge0-style `status.description`, `stdout`, `stderr`,
 * `last_executed_test_case`) needs confirming with a signed-in run (owner test queue).
 *
 * The problem list isn't fetched: titles live in the site's bundle, so a dated snapshot ships in
 * `assets/neetcode/catalog.json` (*agent decision*), like the NeetCode lists in rev 12.
 *
 * Inputs are stored the LeetCode way (one JSON value per line, in parameter order) so the local
 * runner and the test-case UI are shared. They're converted to NeetCode's `name=value` lines
 * only when sent.
 */
object NeetCodeApi {
    const val ORIGIN = "https://neetcode.io"
    const val FUNCTIONS = "https://us-central1-neetcode-dd170.cloudfunctions.net"
    /** NeetCode's public Firebase web key (it's in the page source). */
    const val API_KEY = "AIzaSyD4emZpWF1MIsu6Z8O6yaMMcPxJ2Z38L8g"
    const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=$API_KEY"
    const val LOGIN_URL = "$ORIGIN/practice"
    const val META = "getProblemMetadataFunctionHttp"
    const val RUN = "runCodeFunctionHttp"
    const val SUBMIT = "executeCodeFunctionHttp"

    class ApiError(message: String, val status: String = "") : Exception(message)

    /**
     * Bumped when [parseProblem] changes what it stores; saved problems from an older parser are
     * downloaded again when opened online. 23: special-type inputs (rev 23).
     */
    const val PARSER = 23

    // ---- the bundled catalog ----------------------------------------------------------------

    data class Entry(
        val id: String, val title: String, val pattern: String, val difficulty: String,
        val lcSlug: String, val lcNumber: Int, val topics: List<String>, val pro: Boolean, val lists: List<String>,
    )

    fun parseCatalog(json: String): List<Entry> {
        val a = JSONObject(json).getJSONArray("problems")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Entry(
                o.getString("id"), o.getString("title"), o.getString("pattern"), o.getString("difficulty"),
                o.optString("lc"), o.optInt("lcNumber"), o.optJSONArray("topics").strings(), o.optBoolean("pro"),
                o.optJSONArray("lists").strings(),
            )
        }
    }

    fun summaries(entries: List<Entry>): List<ProblemSummary> = entries.map {
        ProblemSummary(Platform.NEETCODE.key(it.id), it.lcNumber, it.title, it.difficulty, it.pro, 0.0,
            it.topics.map { t -> t.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }, null)
    }

    /** NeetCode 150, Blind 75 and NeetCode 250 over NeetCode's own problems, with pattern headers; then every problem by pattern. */
    fun collections(entries: List<Entry>): List<ProblemCollection> {
        val lists = listOf("neetcode150" to "NeetCode 150", "blind75" to "Blind 75", "neetcode250" to "NeetCode 250")
        return lists.map { (id, title) ->
            val es = entries.filter { id in it.lists }
            ProblemCollection("nc-$id", title, es.map { Platform.NEETCODE.key(it.id) }, es.associate { Platform.NEETCODE.key(it.id) to it.pattern })
        } + ProblemCollection("nc-all", "By pattern", entries.map { Platform.NEETCODE.key(it.id) }, entries.associate { Platform.NEETCODE.key(it.id) to it.pattern })
    }

    // ---- requests ---------------------------------------------------------------------------

    fun metaRequest(id: String) = wrap(JSONObject().put("problemId", id))

    fun runRequest(id: String, code: String, testCases: List<String>) =
        wrap(JSONObject().put("problemId", id).put("rawCode", code).put("lang", "python").put("testCases", JSONArray(testCases)))

    fun submitRequest(id: String, code: String) =
        wrap(JSONObject().put("problemId", id).put("rawCode", code).put("lang", "python"))

    private fun wrap(o: JSONObject) = JSONObject().put("data", o).toString()

    /** The `data` of a reply, or [ApiError] from its `error`. */
    fun data(body: String): Any {
        val o = try { JSONObject(body) } catch (e: Exception) { throw ApiError("Unexpected reply from NeetCode: ${body.take(80)}") }
        o.optJSONObject("error")?.let { throw ApiError(it.optString("message", "NeetCode error"), it.optString("status")) }
        return o.opt("data") ?: o.opt("result") ?: throw ApiError("NeetCode sent no data")
    }

    // ---- a problem --------------------------------------------------------------------------

    /** A parsed problem: the detail to save, and the solution article (Markdown) for the Comments pane. */
    data class Problem(val detail: ProblemDetail, val article: String?, val video: String?)

    fun parseProblem(body: String, entry: Entry?): Problem {
        val d = data(body) as? JSONObject ?: throw ApiError("NeetCode sent no problem")
        val id = d.getString("id")
        val starter = d.optJSONObject("starterCode")?.optString("python")?.takeIf { it.isNotBlank() }
        val type = d.optString("test_case_type", "function")
        val meta = if (starter != null) signature(starter, type) else JSONObject()
        meta.put("neetcode", JSONObject().put("type", type))
        meta.put("parser", PARSER)
        val names = paramNames(meta)
        val (statement, hints) = splitDescription(d.optString("description"))
        val examples = examples(statement)
        val custom = d.optJSONArray("custom_test_cases").strings()
        val inputs = examples.mapNotNull { toLines(it.first, names, type) }
            .ifEmpty { custom.mapNotNull { fromNeetCode(it, names, type) } }
        (examples.map { it.first } + custom.map { it.lines().filter(String::isNotBlank).joinToString(", ") })
            .firstNotNullOfOrNull { namedValues(it).keys.toList().takeIf { k -> k.isNotEmpty() } }
            ?.let { orderedNames(it, names) }
            ?.takeIf { it != names }
            ?.let { meta.put("inputNames", JSONArray(it)) }
        meta.put("linesPerCase", inputs.firstOrNull()?.lines()?.size ?: if (type == "class") 2 else names.size.coerceAtLeast(1))
        val html = Markdown.toHtml(statement, Markdown.Fold.NONE, images = true)
        val detail = ProblemDetail(
            slug = Platform.NEETCODE.key(id),
            questionId = id,
            frontendId = entry?.lcNumber ?: 0,
            title = d.optString("name", entry?.title ?: id),
            difficulty = d.optString("difficulty", entry?.difficulty ?: ""),
            paidOnly = d.has("free") && !d.optBoolean("free", true),
            contentHtml = HtmlClean.statement(html),
            starter = starter,
            exampleTestcases = inputs.joinToString("\n"),
            metaData = meta.toString(),
            hints = hints,
            tags = d.optJSONArray("topics").strings(),
            similarJson = "[]",
        )
        return Problem(detail, d.optString("article_body").takeIf { it.isNotBlank() }?.let(::cleanArticle), video(d.optString("video")))
    }

    /** Expected outputs of the statement's examples, in order (for local runs). */
    fun expectedOutputs(statementMarkdown: String): List<String> = examples(statementMarkdown).map { it.second }

    /**
     * NeetCode's description is Markdown followed by HTML `<details>` blocks (topics, a
     * complexity target, hints, company tags). Returns the Markdown and the useful blocks as hints.
     */
    fun splitDescription(desc: String): Pair<String, List<String>> {
        val blocks = Regex("(?is)<details[^>]*>\\s*<summary>(.*?)</summary>(.*?)</details>").findAll(desc).toList()
        val hints = blocks.mapNotNull { m ->
            val title = m.groupValues[1].trim()
            if (title.equals("Topics", true) || title.contains("Company", true)) null
            else "<b>${HtmlClean.escape(title)}</b>" + m.groupValues[2].trim()
        }
        val md = desc.replace(Regex("(?is)<details[^>]*>.*?</details>"), "").replace(Regex("(?i)<br\\s*/?>"), "").trimEnd()
            // Example blocks are fenced as "java"; they're plain text.
            .replace(Regex("```[a-zA-Z]+\\n(?=\\s*Input:)"), "```\n")
        return md to hints
    }

    /** (input, output) text of each example: fenced blocks holding "Input:" and "Output:". */
    fun examples(md: String): List<Pair<String, String>> =
        Regex("(?s)```[a-zA-Z]*\\n(.*?)```").findAll(md).mapNotNull { m ->
            val block = m.groupValues[1]
            val i = block.indexOf("Input:")
            val o = block.indexOf("Output:")
            if (i < 0 || o < i) return@mapNotNull null
            val input = block.substring(i + 6, o).trim()
            val output = block.substring(o + 7).substringBefore("Explanation").trim().lines().filter { it.isNotBlank() }.joinToString("\n").trim()
            input to output
        }.toList()

    /**
     * An example's input as LeetCode-style lines in parameter order: `nums = [3,4,5,6], target = 7`
     * → `[3,4,5,6]\n7`. Class problems give the operations line and the arguments line.
     */
    fun toLines(input: String, names: List<String>, type: String): String? {
        val text = input.trim()
        // Class problems tested like functions (Codec, encode/decode: `root = […]`) fall through.
        if (type == "class" || text.startsWith("[")) classLines(text)?.let { return it }
        val byName = namedValues(text)
        if (byName.isEmpty()) return null
        return orderedNames(byName.keys.toList(), names).joinToString("\n") { byName.getValue(it) }
    }

    /**
     * The order to store a test's values in: the parameters first when the test names them all,
     * then any inputs the function doesn't take (Linked List Cycle's `index`); otherwise as written.
     */
    fun orderedNames(keys: List<String>, params: List<String>): List<String> =
        if (params.isNotEmpty() && params.all { it in keys }) params + keys.filter { it !in params } else keys

    /** `nums = [1,2], target = 3` → {nums: [1,2], target: 3}, in order; empty if nothing is named. */
    fun namedValues(input: String): LinkedHashMap<String, String> {
        val byName = LinkedHashMap<String, String>()
        var pending: String? = null
        for (p in splitTopLevel(input.trim().replace("\n", " "), ',')) {
            val eq = topLevelEquals(p)
            if (eq > 0) {
                pending = p.substring(0, eq).trim()
                byName[pending] = p.substring(eq + 1).trim()
            } else if (pending != null) {
                byName[pending] = byName[pending] + "," + p
            }
        }
        return byName
    }

    /** NeetCode's own test-case text (`nums=[1,2]\ntarget=3`, or a flat class list) as LeetCode-style lines. */
    fun fromNeetCode(text: String, names: List<String>, type: String): String? =
        if (type == "class" || text.trim().startsWith("[")) classLines(text.trim()) ?: toLines(text.lines().filter { it.isNotBlank() }.joinToString(", "), names, type)
        else toLines(text.lines().filter { it.isNotBlank() }.joinToString(", "), names, type)

    /** LeetCode-style lines back to NeetCode's test-case text. */
    fun toNeetCode(lines: String, meta: String): String {
        val m = runCatching { JSONObject(meta) }.getOrDefault(JSONObject())
        val type = m.optJSONObject("neetcode")?.optString("type") ?: "function"
        val ls = lines.split("\n")
        if (type == "class" && ls.size >= 2) {
            val ops = JSONArray(ls[0])
            val args = JSONArray(ls[1])
            val flat = JSONArray()
            for (i in 0 until ops.length()) { flat.put(ops.get(i)); flat.put(args.opt(i) ?: JSONArray()) }
            return flat.toString()
        }
        val names = inputNames(m)
        return ls.mapIndexed { i, v -> "${names.getOrElse(i) { "arg$i" }}=$v" }.joinToString("\n") + "\n"
    }

    /** `["LRUCache", [2], "put", [1, 10]]` → `["LRUCache","put"]\n[[2],[1,10]]`. */
    private fun classLines(flat: String): String? = runCatching {
        // Rev 23: some problems write the operations and the arguments as two lists instead,
        // `["BSTIterator","next"], [[[7,3]],[]]`, or on two lines.
        val two = runCatching { JSONArray("[" + flat.trim().replace(Regex("]\\s*\\n\\s*\\["), "],[") + "]") }.getOrNull()
        if (two != null && two.length() == 2) {
            val o = two.optJSONArray(0)
            val g = two.optJSONArray(1)
            if (o != null && g != null && o.length() == g.length() && (0 until o.length()).all { o.opt(it) is String }) return@runCatching "$o\n$g"
        }
        val a = JSONArray(flat)
        val ops = JSONArray()
        val args = JSONArray()
        var i = 0
        while (i < a.length()) {
            ops.put(a.getString(i))
            args.put(a.optJSONArray(i + 1) ?: JSONArray())
            i += 2
        }
        "$ops\n$args"
    }.getOrNull()

    // ---- the Python signature → LeetCode-style metaData -------------------------------------

    /**
     * Builds the `metaData` the local runner reads (method name, parameter types, return type)
     * from the starter's type hints, since NeetCode has no metaData. Class problems get `classname`.
     */
    fun signature(starter: String, type: String): JSONObject {
        val m = JSONObject()
        if (type == "class") {
            Regex("(?m)^class\\s+(\\w+)").find(starter)?.let { m.put("classname", it.groupValues[1]) }
            return m
        }
        val def = Regex("(?m)^\\s+def\\s+(\\w+)\\(self(?:,\\s*([^)]*))?\\)\\s*(?:->\\s*([^:]+))?:").findAll(starter)
            .firstOrNull { !it.groupValues[1].startsWith("_") } ?: return m
        m.put("name", def.groupValues[1])
        val params = JSONArray()
        splitTopLevel(def.groupValues[2], ',').filter { it.isNotBlank() }.forEach { p ->
            val name = p.substringBefore(':').substringBefore('=').trim()
            val hint = if (':' in p) p.substringAfter(':').substringBefore('=').trim() else ""
            params.put(JSONObject().put("name", name).put("type", leetType(hint)))
        }
        m.put("params", params)
        m.put("return", JSONObject().put("type", leetType(def.groupValues[3].trim())))
        return m
    }

    /**
     * The names NeetCode's tests use, in order. Usually the parameters, but some tests carry inputs
     * the function doesn't take (Linked List Cycle's `index`, Clone Graph's `adjList`), rev 23.
     */
    fun inputNames(meta: JSONObject): List<String> =
        meta.optJSONArray("inputNames")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: paramNames(meta)

    fun paramNames(meta: JSONObject): List<String> =
        meta.optJSONArray("params")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } } ?: emptyList()

    /** Python type hint → the type names LeetCode's metaData uses (the runner's vocabulary). */
    fun leetType(hint: String): String {
        val h = hint.replace(" ", "").removeSurrounding("'").removeSurrounding("\"")
        Regex("^Optional\\[(.*)]$").find(h)?.let { return leetType(it.groupValues[1]) }
        Regex("^(?:List|list)\\[(.*)]$").find(h)?.let {
            val inner = leetType(it.groupValues[1])
            return "$inner[]"
        }
        return when (h) {
            "int" -> "integer"
            "float" -> "double"
            "bool" -> "boolean"
            "str" -> "string"
            "None", "" -> "void"
            else -> h
        }
    }

    // ---- results ----------------------------------------------------------------------------

    /** Run: one Judge0-style result per test case. */
    fun parseRun(body: String, inputs: List<String>): Judge.RunResult {
        val arr = data(body) as? JSONArray ?: throw ApiError("NeetCode sent no results")
        val results = (0 until arr.length()).map { arr.getJSONObject(it) }
        val cases = inputs.mapIndexed { i, input ->
            val r = results.getOrNull(i)
            val t = r?.optJSONObject("last_executed_test_case")
            val desc = r?.let(::description)
            Judge.Case(
                index = i,
                input = input,
                output = t?.str("user_output") ?: r?.str("stdout")?.takeIf { t == null },
                expected = t?.str("expected_output"),
                stdout = t?.str("user_logs") ?: r?.str("user_logs") ?: "",
                ok = desc?.let { it == "Accepted" },
                error = r?.let(::errorText),
            )
        }
        val failing = results.map(::description).firstOrNull { it != "Accepted" && it != "Wrong Answer" }
        val status = failing?.let(::status) ?: Judge.Status.ACCEPTED
        return Judge.RunResult(
            source = "Run on NeetCode",
            status = status,
            statusMsg = failing ?: "Accepted",
            correct = results.isNotEmpty() && results.all { description(it) == "Accepted" },
            runtime = results.mapNotNull { it.str("time")?.toDoubleOrNull() }.maxOrNull()?.let(::seconds).orEmpty(),
            memory = results.mapNotNull { it.optLongOrNull("memory") }.maxOrNull()?.let(::kb).orEmpty(),
            cases = cases,
            error = results.firstNotNullOfOrNull(::errorText),
        )
    }

    /** Submit: one result for the whole test set, with the first failing case. */
    fun parseSubmit(body: String, meta: String): Judge.SubmitResult {
        val r = data(body) as? JSONObject ?: throw ApiError("NeetCode sent no result")
        val desc = description(r)
        val t = r.optJSONObject("last_executed_test_case")
        val names = paramNames(runCatching { JSONObject(meta) }.getOrDefault(JSONObject()))
        val type = runCatching { JSONObject(meta).optJSONObject("neetcode")?.optString("type") }.getOrNull() ?: "function"
        val rawInput = t?.str("input")
        return Judge.SubmitResult(
            status = status(desc),
            statusMsg = desc,
            totalCorrect = r.optInt("correct_test_case_count"),
            totalTestcases = r.optInt("test_case_count"),
            runtime = r.str("time")?.toDoubleOrNull()?.let(::seconds) ?: r.str("timeMs")?.let { "$it ms" } ?: "",
            runtimePercentile = null,
            memory = r.optLongOrNull("memory")?.let(::kb) ?: "",
            memoryPercentile = null,
            lastInput = rawInput?.let { fromNeetCode(it, names, type) ?: it },
            output = t?.str("user_output"),
            expected = t?.str("expected_output"),
            stdout = t?.str("user_logs") ?: "",
            error = errorText(r),
            source = "Submit to NeetCode",
        )
    }

    private fun description(r: JSONObject) =
        r.optJSONObject("status")?.optString("description")?.takeIf { it.isNotEmpty() } ?: r.optString("statusDescription", "Unknown")

    private fun errorText(r: JSONObject): String? =
        r.str("compile_output") ?: r.str("stderr")?.takeIf { description(r) != "Accepted" } ?: r.str("errorMessage")

    fun status(desc: String): Judge.Status = when {
        desc == "Accepted" -> Judge.Status.ACCEPTED
        desc.startsWith("Wrong") -> Judge.Status.WRONG_ANSWER
        desc.startsWith("Time Limit") -> Judge.Status.TIME_LIMIT
        desc.startsWith("Memory") -> Judge.Status.MEMORY_LIMIT
        desc.startsWith("Compil") -> Judge.Status.COMPILE_ERROR
        desc.startsWith("Runtime") -> Judge.Status.RUNTIME_ERROR
        else -> Judge.Status.UNKNOWN
    }

    private fun seconds(s: Double) = "%.0f ms".format(s * 1000)
    private fun kb(k: Long) = "%.1f MB".format(k / 1024.0)

    /** The solution article: Python code only (the other languages' tabs are dropped). */
    fun cleanArticle(md: String): String {
        val out = StringBuilder()
        val lines = md.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.trim().startsWith("::tabs")) { i++; continue }
            if (l.trimStart().startsWith("```")) {
                val lang = l.trim().removePrefix("```").trim().lowercase()
                val block = mutableListOf(l)
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { block += lines[i]; i++ }
                if (i < lines.size) block += lines[i]
                i++
                if (lang.isEmpty() || lang.startsWith("py")) block.forEach { out.append(it).append('\n') }
                continue
            }
            if (!l.trim().matches(Regex("(?i)<br\\s*/?>"))) out.append(l).append('\n')
            i++
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun video(html: String): String? = Regex("youtube\\.com/embed/([\\w-]+)").find(html)?.groupValues?.get(1)?.let { "https://www.youtube.com/watch?v=$it" }

    // ---- helpers ----------------------------------------------------------------------------

    /** Split on [sep] outside brackets and quotes. */
    fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var quote: Char? = null
        val cur = StringBuilder()
        for (c in s) {
            when {
                quote != null -> { if (c == quote) quote = null }
                c == '"' || c == '\'' -> quote = c
                c == '[' || c == '(' || c == '{' -> depth++
                c == ']' || c == ')' || c == '}' -> depth--
                c == sep && depth == 0 -> { out += cur.toString().trim(); cur.clear(); continue }
            }
            cur.append(c)
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }

    /** Index of a `name =` sign at the top level (not inside a value), or -1. */
    private fun topLevelEquals(p: String): Int {
        val m = Regex("^\\s*([A-Za-z_]\\w*)\\s*=").find(p) ?: return -1
        return m.range.last
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    private fun JSONObject.optLongOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else optString(k).toDoubleOrNull()?.toLong()
}
