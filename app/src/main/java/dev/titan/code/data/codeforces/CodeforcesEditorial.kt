package dev.titan.code.data.codeforces

import dev.titan.code.data.HtmlClean
import dev.titan.code.data.Markdown
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Codeforces editorials (rev 32), shown in the Comments pane. A contest's tutorial is a blog
 * entry linked from the "Contest materials" box beside its problems; it covers every problem in
 * the contest, so the part for one problem is cut out by the problem's heading. Pure functions,
 * tested on fixtures; [CodeforcesWeb.editorialPage] loads the pages.
 *
 * Checked 2026-11-04 on blog 132373 (Codeforces Round 964): each problem's part starts with a
 * paragraph linking `/contest/1999/problem/A` ("1999A - A+B Again?"), and the tutorial text is a
 * `div.problemTutorial[problemcode=1999A]` that says "Tutorial is loading..." until the page's
 * script fills it, which is why the page is read from the hidden browser and not the API.
 */
object CodeforcesEditorial {

    fun blogUrl(id: Long) = "${CodeforcesApi.ORIGIN}/blog/entry/$id?locale=en"
    fun contestUrl(contestId: Int) = "${CodeforcesApi.ORIGIN}/contest/$contestId"

    private val blogLink = Regex("/blog/entry/(\\d+)")
    /** Matched against lowercased text: ICU on Android has no `(?u)` flag (rev 33). */
    private val tutorialWords = Regex("tutorial|editorial|разбор|analysis|solutions?\\b")

    /**
     * The tutorial blog entries a problem or contest page links to, English first. Announcements
     * and video links are left out.
     */
    fun tutorialIds(html: String): List<Long> {
        val doc = Jsoup.parse(html, CodeforcesApi.ORIGIN)
        val boxes = doc.select("div.sidebox").filter { it.text().contains("materials", ignoreCase = true) }
        val scope: List<Element> = boxes.ifEmpty { listOfNotNull(doc.selectFirst("#sidebar")) }
        val links = scope.flatMap { it.select("a[href]") }.mapNotNull { a ->
            val id = blogLink.find(a.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val label = (a.attr("title") + " " + a.text()).lowercase()
            if (!tutorialWords.containsMatchIn(label) || label.contains("announcement", ignoreCase = true)) null else id to label
        }
        val english = links.filter { (_, l) -> l.contains("(en)") || !l.contains("(ru)") }
        return (english + links).map { it.first }.distinct()
    }

    /** One editorial blog entry, read from its page. */
    data class Entry(val id: Long, val title: String, val author: String, val content: Element)

    fun parseEntry(html: String, id: Long): Entry {
        val doc = Jsoup.parse(html, CodeforcesApi.ORIGIN)
        if (doc.title().contains("Just a moment")) throw CodeforcesApi.ApiError("Codeforces asked for a browser check")
        val topic = doc.selectFirst("div.topic") ?: doc
        val content = topic.selectFirst("div.content div.ttypography") ?: topic.selectFirst("div.ttypography")
            ?: throw CodeforcesApi.ApiError("No editorial on that page")
        val title = topic.selectFirst("div.title")?.text()?.trim().orEmpty().ifEmpty { doc.title().substringBefore(" - Codeforces").trim() }
        val author = topic.selectFirst("div.info a[href^=/profile/], a.rated-user")?.text()?.trim().orEmpty()
        content.select("script:not([type^=math/tex])").remove()
        // Rev 33: maths that can't be turned back into TeX stays as the page drew it.
        runCatching { CodeforcesApi.unMathJax(content) }.onFailure { dev.titan.code.TLog.w("editorial $id maths: $it") }
        return Entry(id, title, author, content)
    }

    /** A heading in the editorial that starts one problem's part. */
    private data class Marker(val at: Int, val contest: Int?, val index: String?, val text: String)

    private val problemLink = Regex("/(?:contest|problemset/problem|gym)/(\\d+)/(?:problem/)?([A-Za-z][0-9]?)(?:$|[/?#])")
    private val headingText = Regex("^\\s*(?:problem\\s+)?(?:\\d{1,4})?([A-Z][0-9]?)\\s*(?:[.:)\\-–—]|$)", RegexOption.IGNORE_CASE)

    /**
     * Problem [contestId][index] ("[title]")'s part of the editorial: from its heading up to the
     * next problem's, as the page's own HTML (rev 33: saved like that and turned into the pane's
     * form by [show] when it's opened). Null when it can't be told apart.
     */
    fun section(e: Entry, contestId: Int, index: String, title: String): String? {
        val root = unwrap(e.content)
        val kids = root.children()
        val markers = kids.mapIndexedNotNull { i, el -> marker(i, el) }
        val t = title.trim().lowercase()
        fun has(m: Marker) = t.length >= 3 && m.text.lowercase().contains(t)
        val ours = markers.firstOrNull { it.contest == contestId && it.index.equals(index, true) }
            ?: markers.firstOrNull { it.index.equals(index, true) && has(it) }
            ?: markers.firstOrNull(::has)
            ?: markers.firstOrNull { it.contest == null && it.index.equals(index, true) }
        val part: List<Element> = if (ours != null) {
            val next = markers.firstOrNull { it.at > ours.at && !(it.index.equals(ours.index, true) && (it.contest == null || it.contest == ours.contest)) }
            kids.subList(ours.at, next?.at ?: kids.size)
        } else {
            // No headings: the block holding this problem's tutorial, if the editorial uses them.
            val tut = root.selectFirst("div.problemTutorial[problemcode=$contestId$index]") ?: return null
            listOf(kids.firstOrNull { it == tut || tut.parents().contains(it) } ?: return null)
        }
        val box = Element("div")
        part.forEach { box.appendChild(it.clone()) }
        return raw(box)
    }

    /** The whole editorial, for when one problem's part can't be found. */
    fun whole(e: Entry): String = raw(e.content.clone())

    /** Marks a saved body as the page's HTML, for [show]; editorials saved by rev 32 were already rendered. */
    private const val RAW = "<!--cf-editorial-->"

    private fun raw(box: Element): String {
        box.remove()
        org.jsoup.nodes.Document("").apply { outputSettings().prettyPrint(false) }.appendChild(box)
        box.select("script:not([type^=math/tex]), style").remove()
        return RAW + box.html()
    }

    /**
     * A saved editorial body as the Comments pane shows it (rev 33). Never throws: if turning it
     * into the pane's form fails, the editorial is shown as Codeforces wrote it, with a note, so a
     * rendering bug can't hide an editorial, and a fixed renderer applies to saved ones without
     * downloading them again.
     */
    fun show(body: String): String {
        if (!body.startsWith(RAW)) return HtmlClean.statement(body)
        val html = body.removePrefix(RAW)
        return runCatching { render(Jsoup.parseBodyFragment(html).body()) }.getOrElse { e ->
            dev.titan.code.TLog.w("editorial render: $e")
            "<p class=\"note\">Shown as Codeforces wrote it: the app couldn't format it (${HtmlClean.escape(e.toString().take(200))}).</p>" +
                HtmlClean.statement(html)
        }
    }

    /** Editorials wrapped in one big `<div>` are read from inside it. */
    private fun unwrap(root: Element): Element {
        var r = root
        while (r.children().size == 1 && r.ownText().isBlank() && r.child(0).tagName() == "div" && !r.child(0).hasClass("spoiler")) r = r.child(0)
        return r
    }

    private fun marker(i: Int, el: Element): Marker? {
        val text = el.text().trim()
        if (text.isEmpty() || text.length > 150 || el.hasClass("spoiler")) return null
        el.select("a[href]").firstNotNullOfOrNull { problemLink.find(it.attr("href")) }?.let { m ->
            return Marker(i, m.groupValues[1].toInt(), m.groupValues[2].uppercase(), text)
        }
        val heading = el.tagName().matches(Regex("h[1-6]")) ||
            (el.tagName() == "p" && el.children().size == 1 && el.child(0).tagName() in setOf("b", "strong") && el.child(0).text().trim() == text)
        if (!heading) return null
        val m = headingText.find(text) ?: return null
        return Marker(i, null, m.groupValues[1].uppercase(), text)
    }

    /**
     * Editorial HTML as the Comments pane shows it, with JavaScript off: spoilers become open
     * `<details>` (rev 34), code is shown in full with its language label, maths becomes text, images become a marker (not downloaded, as in posts).
     */
    fun render(box: Element): String {
        // Printed as it is: no pretty-printing whitespace in the page.
        box.remove()
        org.jsoup.nodes.Document("").apply { outputSettings().prettyPrint(false) }.appendChild(box)
        // Rev 33: each step on its own, so one that fails leaves the others' work (and the text) in place.
        fun step(name: String, f: () -> Unit) = runCatching(f).onFailure { dev.titan.code.TLog.w("editorial $name: $it") }
        step("code") { codeBlocks(box) }
        step("spoilers") { spoilers(box) }
        step("tutorials") { unloaded(box) }
        step("cleanup") {
            box.select("img").forEach { it.replaceWith(Element("span").addClass("dim").text("[image]")) }
            box.select("a[href]").forEach { it.unwrap() }
            box.select("[style]").forEach { it.removeAttr("style") }
        }
        val html = box.html()
        return HtmlClean.statement(runCatching { CodeforcesApi.tex(html) }.getOrDefault(html))
    }

    private fun codeBlocks(box: Element) {
        for (pre in box.select("pre")) {
            val items = pre.select("li")
            val code = (if (items.isNotEmpty()) items.joinToString("\n") { it.wholeText() } else pre.wholeText())
                .replace("\r\n", "\n").trimEnd('\n', ' ').trimStart('\n')
            // Owner, rev 34: editorial code is shown in full, in any language.
            pre.after(Markdown.codeBlock(code, language(code), Markdown.Fold.NONE))
            pre.remove()
        }
    }

    private fun spoilers(box: Element) {
        for (sp in box.select("div.spoiler")) {
            val title = sp.selectFirst("> .spoiler-title")?.text()?.trim().orEmpty().ifEmpty { "Spoiler" }
            val content = sp.selectFirst("> .spoiler-content") ?: sp
            // Owner, rev 34: open, so the tutorial and code read straight through; a tap still folds one.
            val d = Element("details").addClass("spoiler").attr("open", true)
            d.appendElement("summary").text(title)
            content.childNodes().toList().forEach { d.appendChild(it) }
            sp.replaceWith(d)
        }
    }

    private fun unloaded(box: Element) {
        for (t in box.select("div.problemTutorial")) {
            if (t.text().contains("Tutorial is loading", ignoreCase = true)) {
                t.html("<p class=\"note\">This tutorial didn't load. ⟳ tries again, or open the editorial in the browser below.</p>")
            }
        }
    }

    /** A label for a code block: Python ones stay open (Markdown.Fold.NON_PYTHON). */
    fun language(code: String): String = when {
        Regex("#include|using namespace|\\bint main\\s*\\(|\\bstd::").containsMatchIn(code) -> "cpp"
        Regex("public static void main|import java\\.|System\\.out").containsMatchIn(code) -> "java"
        Regex("\\bfn main\\s*\\(|let mut ").containsMatchIn(code) -> "rust"
        Regex("^\\s*(def |import |from \\w+ import|for _ in range)|\\binput\\(\\)|print\\(", RegexOption.MULTILINE).containsMatchIn(code) -> "python"
        else -> "code"
    }
}
