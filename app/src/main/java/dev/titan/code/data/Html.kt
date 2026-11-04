package dev.titan.code.data

/**
 * Statement HTML cleaning and image rewriting (PLAN §8.2). The WebView that shows it already has
 * JavaScript and network loads off; this removes active content anyway, so a saved statement is
 * inert on disk too.
 */
object HtmlClean {
    private val dropBlocks = Regex("(?is)<(script|style|iframe|object|embed)\\b.*?</\\1\\s*>")
    private val dropTags = Regex("(?i)</?(script|style|iframe|object|embed|link|meta|base)\\b[^>]*>")
    private val handlers = Regex("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")
    private val jsUrls = Regex("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2")
    private val imgSrc = Regex("(?i)(<img\\b[^>]*?\\bsrc\\s*=\\s*)([\"'])([^\"']+)\\2")

    fun statement(html: String): String = html
        .replace(dropBlocks, "")
        .replace(dropTags, "")
        .replace(handlers, "")
        .replace(jsUrls, "$1=$2#$2")

    /** Remote image URLs in [html] (http/https), in order, without duplicates. */
    fun imageUrls(html: String): List<String> =
        imgSrc.findAll(html).map { it.groupValues[3].trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct().toList()

    /** Replace each `<img src>` found in [local] with its local URL. Others are left alone. */
    fun rewriteImages(html: String, local: Map<String, String>): String =
        imgSrc.replace(html) { m ->
            val url = m.groupValues[3].trim()
            val to = local[url] ?: return@replace m.value
            "${m.groupValues[1]}${m.groupValues[2]}$to${m.groupValues[2]}"
        }

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * A small Markdown-to-HTML renderer for comments and solutions (PLAN §8.2). It covers what
 * LeetCode posts use: fenced code, inline code, headings, lists, quotes, bold, italic and links.
 * Raw HTML is escaped, not rendered. Single newlines become line breaks, as on LeetCode.
 * Images become an "[image]" marker: they aren't downloaded for discussions (*agent decision*, rev 9).
 *
 * Code blocks longer than [PEEK_LINES] lines are foldable (owner, rev 10): a `<details>` element,
 * so it works with JavaScript off. Folded, it shows the first [PEEK_LINES] lines and "N more
 * lines"; tapping toggles it. [Fold] picks which blocks start folded.
 */
object Markdown {
    enum class Fold {
        /** Nothing folds (statements). */
        NONE,
        /** Every long block starts folded (comments). */
        ALL,
        /** Python (or untagged) blocks start open, other languages start folded (solution posts). */
        NON_PYTHON,
    }

    const val PEEK_LINES = 4

    private val fence = Regex("^\\s*(```|~~~)")
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^\\s*[-*+]\\s+(.*)$")
    private val numbered = Regex("^\\s*\\d+[.)]\\s+(.*)$")
    private val quote = Regex("^\\s*>\\s?(.*)$")
    private val rule = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    /** [images]: render `![](url)` as an image (statements, which save their images) instead of a marker. */
    fun toHtml(md: String, fold: Fold = Fold.NONE, images: Boolean = false): String {
        val inl = { t: String -> inline(t, images) }
        val out = StringBuilder()
        val para = mutableListOf<String>()
        var list: String? = null
        val lines = md.replace("\r\n", "\n").split("\n")

        // Code pasted without a fence: runs of code-like paragraphs become one code block (rev 10).
        val looseCode = mutableListOf<String>()
        fun flushCode() {
            if (looseCode.isNotEmpty()) out.append(codeBlock(looseCode.joinToString("\n").trimEnd('\n'), "", fold)).append('\n')
            looseCode.clear()
        }
        fun flushPara() {
            if (para.isEmpty()) return
            if (looksLikeCode(para, continuing = looseCode.isNotEmpty())) {
                if (looseCode.isNotEmpty()) looseCode += ""
                looseCode += para
            } else {
                flushCode()
                out.append("<p>").append(para.joinToString("<br>") { inl(it) }).append("</p>\n")
            }
            para.clear()
        }
        fun closeList() {
            list?.let { out.append("</$it>\n") }
            list = null
        }
        fun openList(tag: String) {
            if (list != tag) { closeList(); out.append("<$tag>"); list = tag }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val f = fence.find(line)
            if (f != null) {
                flushPara(); flushCode(); closeList()
                val marker = f.groupValues[1]
                val lang = line.trim().removePrefix(marker).trim().substringBefore(' ').substringBefore('[').lowercase()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                out.append(codeBlock(code.toString().trimEnd('\n'), lang, fold)).append('\n')
                i++
                continue
            }
            val h = heading.find(line)
            val b = bullet.find(line)
            val n = numbered.find(line)
            val q = quote.find(line)
            when {
                line.isBlank() -> { flushPara(); closeList() }
                line.trim().matches(rule) -> { flushPara(); flushCode(); closeList(); out.append("<hr>\n") }
                h != null -> {
                    flushPara(); flushCode(); closeList()
                    val level = (h.groupValues[1].length + 2).coerceAtMost(6)
                    out.append("<h$level>").append(inl(h.groupValues[2])).append("</h$level>\n")
                }
                b != null -> { flushPara(); flushCode(); openList("ul"); out.append("<li>").append(inl(b.groupValues[1])).append("</li>") }
                n != null -> { flushPara(); flushCode(); openList("ol"); out.append("<li>").append(inl(n.groupValues[1])).append("</li>") }
                q != null -> { flushPara(); flushCode(); closeList(); out.append("<blockquote>").append(inl(q.groupValues[1])).append("</blockquote>\n") }
                else -> { closeList(); para += line }
            }
            i++
        }
        flushPara(); flushCode(); closeList()
        return out.toString()
    }

    private fun codeBlock(code: String, lang: String, fold: Fold): String {
        val lines = code.split('\n')
        val label = if (lang.isEmpty()) "" else "<span class=\"lang\">${HtmlClean.escape(lang)}</span>"
        if (fold == Fold.NONE || lines.size <= PEEK_LINES) {
            return "<pre>$label<code>${HtmlClean.escape(code)}</code></pre>"
        }
        val python = lang.isEmpty() || lang.startsWith("py")
        val open = fold == Fold.NON_PYTHON && python
        val peek = HtmlClean.escape(lines.take(PEEK_LINES).joinToString("\n"))
        return "<details class=\"code\"${if (open) " open" else ""}><summary>" +
            "<pre class=\"peek\">$label<code>$peek</code></pre>" +
            "<span class=\"more\">▸ ${lines.size - PEEK_LINES} more lines${if (lang.isEmpty()) "" else " · $lang"}</span></summary>" +
            "<pre>$label<code>${HtmlClean.escape(code)}</code></pre></details>"
    }

    private val codeLine = Regex(
        "^(\\s{2,}\\S|\\s*(def|class|for|while|if|elif|else|return|import|from|try|except|with|lambda|print|public|private|int|vector|var|let|const|#include)\\b)" +
            "|[:;{}]\\s*$|\\s(=|==|\\+=|-=|!=|<=|>=)\\s|\\w\\(.*\\)\\s*$"
    )

    /** A paragraph is code if most of its lines look like code (2+ lines, or 1 line continuing a code run). */
    private fun looksLikeCode(lines: List<String>, continuing: Boolean): Boolean {
        val hits = lines.count { codeLine.containsMatchIn(it) }
        return if (lines.size == 1) continuing && hits == 1 else hits * 10 >= lines.size * 6
    }

    private val code = Regex("`([^`]+)`")
    private val image = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)[^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)")
    private val bold = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val italic = Regex("(?<![\\w*])\\*(?=\\S)([^*]+?)(?<=\\S)\\*(?![\\w*])")

    /** Inline spans. Code spans are cut out first so nothing inside them is formatted. */
    fun inline(text: String, images: Boolean = false): String {
        val spans = mutableListOf<String>()
        val protectedText = code.replace(text) { m ->
            spans += "<code>${HtmlClean.escape(m.groupValues[1])}</code>"
            "\u0000${spans.size - 1}\u0000"
        }
        var s = HtmlClean.escape(protectedText)
        s = image.replace(s) {
            val url = it.groupValues[2]
            if (images && (url.startsWith("https://") || url.startsWith("http://"))) "<img src=\"$url\" alt=\"${it.groupValues[1]}\">"
            else "<span class=\"dim\">[image]</span>"
        }
        s = link.replace(s) { "<a>${it.groupValues[1]}</a>" }
        s = bold.replace(s) { "<b>${it.groupValues[2]}</b>" }
        s = italic.replace(s) { "<i>${it.groupValues[1]}</i>" }
        return Regex("\u0000(\\d+)\u0000").replace(s) { spans[it.groupValues[1].toInt()] }
    }
}
