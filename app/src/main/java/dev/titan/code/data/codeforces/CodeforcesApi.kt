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
        unMathJax(st)
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
            .put("parser", PARSER)
            // Rev 32: the contest's tutorial blog entries, from the page's "Contest materials".
            .put("editorials", JSONArray(CodeforcesEditorial.tutorialIds(html)))
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

    /** Rev 36: statements keep each formula's TeX (`data-tex`) for KaTeX. */
    const val PARSER = 2

    /** A saved statement from before [PARSER] 2 that has maths, and so lost its TeX. */
    fun needsReparse(metaData: String, contentHtml: String?): Boolean {
        val parser = runCatching { JSONObject(metaData).optInt("parser") }.getOrDefault(0)
        return parser < PARSER && contentHtml != null && contentHtml.contains("class=\"math\"")
    }

    /** Tutorial blog ids saved with a statement (rev 32); null for statements saved before. */
    fun editorialIds(metaData: String): List<Long>? =
        runCatching { JSONObject(metaData).optJSONArray("editorials")?.let { a -> (0 until a.length()).map { a.getLong(it) } } }.getOrNull()

    fun sampleOutputs(metaData: String): List<String> =
        runCatching { JSONObject(metaData).getJSONArray("outputs").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    // ---- MathJax output back to text (rev 30) -------------------------------------------------

    private val D3 = "\$\$\$"

    /**
     * The hidden browser runs the page's MathJax before the statement is read, so each formula
     * arrives drawn twice: MathJax's HTML-CSS glyphs (in a font the app doesn't have) and a MathML
     * copy for screen readers that the site hides with its own CSS. Both showed, as "ɑ a". Each
     * drawn formula is replaced by its TeX source as `$$$…$$$` (from the `math/tex` script MathJax
     * keeps after it) for [tex], or, without one, by readable text from its MathML ([mathText]).
     */
    fun unMathJax(root: Element) {
        root.select(".MathJax_Preview").remove()
        for (frame in root.select("div.MathJax_Display, span.MathJax, span.MathJax_SVG, span.MathJax_CHTML")) {
            if (frame.root() !== root.root()) continue // inside a display block already replaced
            val display = frame.tagName() == "div"
            val script = frame.nextElementSibling()?.takeIf { it.tagName() == "script" && it.attr("type").startsWith("math/tex") }
            if (script != null) {
                script.remove()
                frame.replaceWith(TextNode(if (script.attr("type").contains("mode=display")) "$D3$D3${script.data()}$D3$D3" else "$D3${script.data()}$D3"))
                continue
            }
            val math = frame.selectFirst("math") ?: frame.attr("data-mathml").takeIf(String::isNotEmpty)?.let { Jsoup.parseBodyFragment(it).selectFirst("math") }
            if (math == null) { frame.remove(); continue }
            val out = Element(if (display) "div" else "span").addClass("math")
            out.append(mathText(math))
            frame.replaceWith(out)
        }
        // Not drawn yet: MathJax's preprocessor has already moved the TeX into scripts.
        for (script in root.select("script[type^=math/tex]")) {
            val d = script.attr("type").contains("mode=display")
            script.replaceWith(TextNode(if (d) "$D3$D3${script.data()}$D3$D3" else "$D3${script.data()}$D3"))
        }
    }

    /** MathML as the same readable HTML [texBody] makes: sub/superscripts, (a)/(b), √(x). */
    fun mathText(e: Element): String {
        fun kids() = e.children().joinToString("") { mathText(it) }
        fun kid(i: Int) = e.children().getOrNull(i)?.let(::mathText) ?: ""
        return when (e.tagName().lowercase()) {
            "mi", "mn", "mo", "mtext", "ms" -> HtmlClean.escape(e.text())
            "mspace" -> " "
            "msup" -> kid(0) + "<sup>${kid(1)}</sup>"
            "msub" -> kid(0) + "<sub>${kid(1)}</sub>"
            "msubsup" -> kid(0) + "<sub>${kid(1)}</sub><sup>${kid(2)}</sup>"
            "mfrac" -> "(${kid(0)})/(${kid(1)})"
            "msqrt" -> "√(${kids()})"
            "mroot" -> "√(${kid(0)})"
            "mover" -> kid(0) + if (kid(1) == "¯" || kid(1) == "‾") "̅" else "<sup>${kid(1)}</sup>"
            "munder" -> kid(0) + "<sub>${kid(1)}</sub>"
            "annotation", "annotation-xml" -> ""
            else -> kids()
        }
    }

    /**
     * Repairs a statement saved before rev 30, which still holds MathJax's drawing and MathML
     * (the TeX scripts were cleaned away). Returns null when there's nothing to repair.
     */
    fun repairSavedMath(html: String): String? {
        if (!html.contains("MathJax")) return null
        val body = Jsoup.parseBodyFragment(html).body()
        unMathJax(body)
        return body.html()
    }

    // ---- TeX ($$$…$$$) to plain HTML ----------------------------------------------------------

    /**
     * TeX commands as text, for when KaTeX can't draw a formula (rev 36: the text is only the
     * fallback; `titan-math.js` draws the `data-tex` source). Commands ending in a letter only
     * match before a non-letter, so `\le` never eats `\left`.
     */
    private val symbols = linkedMapOf(
        // relations
        "\\leq" to "≤", "\\le" to "≤", "\\leqslant" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\geqslant" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\gt" to "&gt;", "\\lt" to "&lt;", "\\ll" to "≪", "\\gg" to "≫",
        "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼", "\\simeq" to "≃", "\\cong" to "≅", "\\propto" to "∝",
        "\\prec" to "≺", "\\succ" to "≻", "\\preceq" to "⪯", "\\succeq" to "⪰", "\\perp" to "⊥", "\\parallel" to "∥",
        "\\mid" to "|", "\\nmid" to "∤", "\\divides" to "|",
        // sets and logic
        "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋", "\\subseteq" to "⊆", "\\subset" to "⊂", "\\supseteq" to "⊇", "\\supset" to "⊃",
        "\\cup" to "∪", "\\cap" to "∩", "\\bigcup" to "⋃", "\\bigcap" to "⋂", "\\setminus" to "∖", "\\emptyset" to "∅", "\\varnothing" to "∅",
        "\\land" to "∧", "\\wedge" to "∧", "\\lor" to "∨", "\\vee" to "∨", "\\neg" to "¬", "\\lnot" to "¬",
        "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄",
        // arrows
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\gets" to "←", "\\leftrightarrow" to "↔",
        "\\Rightarrow" to "⇒", "\\implies" to "⇒", "\\Leftarrow" to "⇐", "\\Leftrightarrow" to "⇔", "\\iff" to "⇔",
        "\\longrightarrow" to "⟶", "\\longleftarrow" to "⟵", "\\mapsto" to "↦", "\\uparrow" to "↑", "\\downarrow" to "↓",
        // operators
        "\\cdot" to "·", "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓", "\\ast" to "∗", "\\star" to "⋆",
        "\\circ" to "∘", "\\bullet" to "•", "\\oplus" to "⊕", "\\bigoplus" to "⊕", "\\otimes" to "⊗", "\\odot" to "⊙",
        "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫", "\\partial" to "∂", "\\nabla" to "∇", "\\infty" to "∞",
        "\\bmod" to " mod ", "\\mod" to " mod ", "\\oplus" to "⊕", "\\angle" to "∠", "\\triangle" to "△", "\\prime" to "′",
        // named functions
        "\\gcd" to "gcd", "\\lcm" to "lcm", "\\max" to "max", "\\min" to "min", "\\log" to "log", "\\ln" to "ln", "\\lg" to "lg",
        "\\exp" to "exp", "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\arg" to "arg", "\\deg" to "deg",
        "\\det" to "det", "\\dim" to "dim", "\\sup" to "sup", "\\inf" to "inf", "\\lim" to "lim", "\\limsup" to "lim sup", "\\liminf" to "lim inf",
        // dots
        "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…", "\\dotsc" to "…", "\\dotsb" to "⋯", "\\vdots" to "⋮", "\\ddots" to "⋱",
        // delimiters
        "\\lfloor" to "⌊", "\\rfloor" to "⌋", "\\lceil" to "⌈", "\\rceil" to "⌉", "\\langle" to "⟨", "\\rangle" to "⟩",
        "\\lvert" to "|", "\\rvert" to "|", "\\vert" to "|", "\\lVert" to "‖", "\\rVert" to "‖", "\\Vert" to "‖", "\\|" to "‖",
        "\\lbrace" to "{", "\\rbrace" to "}", "\\lbrack" to "[", "\\rbrack" to "]", "\\backslash" to "\\",
        // Greek
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
        "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ",
        "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Xi" to "Ξ", "\\Pi" to "Π",
        "\\Sigma" to "Σ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
        // escapes and spacing (braces are protected separately)
        "\\%" to "%", "\\#" to "#", "\\&" to "&amp;", "\\$" to "$", "\\_" to "_",
        "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\:" to " ", "\\;" to " ", "\\!" to "", "\\ " to " ",
    )

    // Rev 33: Android's regex engine is ICU, where `{` inside `[...]` starts a string (`[^{}]` is a
    // syntax error that the desktop JVM accepts), so every brace here is escaped. RegexCompatTest
    // checks the sources for this. Compiled once.
    private val texDisplay = Regex("\\$\\$\\$\\$\\$\\$(.+?)\\$\\$\\$\\$\\$\\$", RegexOption.DOT_MATCHES_ALL)
    private val texInline = Regex("\\$\\$\\$(.+?)\\$\\$\\$", RegexOption.DOT_MATCHES_ALL)
    private const val ARG = "\\{([^\\{\\}]*)\\}"
    private val texKeep = Regex("\\\\(?:text|textbf|textit|textrm|textsf|mathrm|mathit|mathbf|mathsf|mathtt|mathcal|mathbb|mathfrak|mathscr|boldsymbol|operatorname\\*?|texttt|emph|bf|it|rm|underline|hat|widehat|tilde|widetilde|vec|dot|ddot|check|breve|acute|grave|mathring|boxed|mbox|hbox)\\s*$ARG")
    private val texFrac = Regex("\\\\[dt]?frac\\s*$ARG\\s*$ARG")
    private val texBinom = Regex("\\\\[dt]?binom\\s*$ARG\\s*$ARG")
    private val texSqrt = Regex("\\\\sqrt\\s*(?:\\[([^\\]]*)\\])?\\s*$ARG")
    private val texBar = Regex("\\\\(?:overline|bar)\\s*$ARG")
    private val texPmod = Regex("\\\\pmod\\s*$ARG")
    private val texDrop = Regex("\\\\(?:hspace|vspace|phantom|hphantom|vphantom|label|tag)\\*?\\s*$ARG")
    private val texEnv = Regex("\\\\(?:begin|end)\\s*\\{[a-zA-Z*]*\\}")
    /** Sizing and style commands with nothing to show (`\bigl(` is just a bigger "("). */
    private val texInvisible = Regex("\\\\(?:[bB]igg?[lrm]?|middle|left|right|displaystyle|textstyle|scriptstyle|scriptscriptstyle|limits|nolimits|nonumber|notag|mathop|mathbin|mathrel|mathord|mathopen|mathclose|mathpunct|stackrel)(?![a-zA-Z])")
    private val texSupGroup = Regex("\\^\\s*$ARG")
    private val texSup = Regex("\\^\\s*(\\\\?\\w|['*])")
    private val texSubGroup = Regex("_\\s*$ARG")
    private val texSub = Regex("_\\s*(\\\\?\\w)")
    private val texCommand = Regex("\\\\([a-zA-Z]+)")
    private val multiSpace = Regex(" {2,}")
    // `\le` must not match inside `\left`; `\ ` and `\,` end in punctuation and need no such guard (rev 35).
    private val texSymbols by lazy {
        symbols.entries.sortedByDescending { it.key.length }
            .map { (k, v) -> Regex(Regex.escape(k) + if (k.last().isLetter()) "(?![a-zA-Z])" else "") to Regex.escapeReplacement(v) }
    }

    /**
     * Replace `$$$…$$$` (inline) and `$$$$$$…$$$$$$` (display) maths with `<span class="math"
     * data-tex="…">text</span>` (a `<div>` for display): rev 36 keeps the TeX for KaTeX to draw
     * when the page is shown, and the text is what shows if it can't. A formula that can't be
     * converted shows its TeX (rev 33), never failing the page.
     */
    fun tex(html: String): String {
        fun body(src: String) = runCatching { texBody(src) }.getOrElse { src.replace("<", "&lt;") }
        // The source is already HTML text (`&lt;`); only quotes and stray tags need escaping for the attribute.
        fun attr(src: String) = src.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        return texInline.replace(texDisplay.replace(html) {
            "<div class=\"math\" data-tex=\"${attr(it.groupValues[1])}\">${body(it.groupValues[1])}</div>"
        }) { "<span class=\"math\" data-tex=\"${attr(it.groupValues[1])}\">${body(it.groupValues[1])}</span>" }
    }

    /** A formula's TeX as readable HTML text: the fallback when KaTeX can't draw it. */
    fun texBody(src: String): String {
        // Escaped braces and line breaks survive the brace removal at the end.
        var s = src.replace("\\\\", " ").replace("\\{", "\u0001").replace("\\}", "\u0002").replace("\\&amp;", "\u0003")
        s = texEnv.replace(s, " ")
        s = texInvisible.replace(s, "")
        // Commands with braced arguments, innermost first.
        repeat(4) {
            s = texDrop.replace(s, "")
            s = texKeep.replace(s) { it.groupValues[1] }
            s = texFrac.replace(s) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
            s = texBinom.replace(s) { "C(${it.groupValues[1]}, ${it.groupValues[2]})" }
            s = texSqrt.replace(s) { (if (it.groupValues[1].isNotEmpty()) "<sup>${it.groupValues[1]}</sup>" else "") + "√(${it.groupValues[2]})" }
            s = texBar.replace(s) { "${it.groupValues[1]}̅" }
            s = texPmod.replace(s) { " (mod ${it.groupValues[1]})" }
        }
        for ((k, v) in texSymbols) s = s.replace(k, v)
        repeat(2) {
            s = texSupGroup.replace(s) { "<sup>${it.groupValues[1]}</sup>" }
            s = texSubGroup.replace(s) { "<sub>${it.groupValues[1]}</sub>" }
        }
        s = texSup.replace(s) { "<sup>${it.groupValues[1]}</sup>" }
        s = texSub.replace(s) { "<sub>${it.groupValues[1]}</sub>" }
        s = texCommand.replace(s) { it.groupValues[1] }
        s = s.replace("{", "").replace("}", "").replace("~", " ").replace("&amp;", " ")
        return s.replace("\u0001", "{").replace("\u0002", "}").replace("\u0003", "&amp;").replace(multiSpace, " ").trim()
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
