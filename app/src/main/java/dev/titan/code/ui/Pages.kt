package dev.titan.code.ui

import dev.titan.code.data.Comment
import dev.titan.code.data.HtmlClean
import dev.titan.code.data.Markdown
import dev.titan.code.data.Post
import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.Repository
import dev.titan.code.data.Solution
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** HTML for the Problem and Comments panes (PLAN §4.1, §8.2). Shown by [ProblemView] with `problem.css`. */
object Pages {

    fun statement(d: ProblemDetail, placeholder: Boolean): String = buildString {
        val cf = d.platform == dev.titan.code.data.Platform.CODEFORCES
        val diff = if (cf) (if (d.difficulty.isEmpty()) "unrated" else "rating ${d.difficulty}") else d.difficulty
        val cls = if (cf) dev.titan.code.data.ProblemFilter.bucket(dev.titan.code.data.ProblemSummary(d.slug, 0, "", d.difficulty, false, 0.0, emptyList(), null)).lowercase() else d.difficulty.lowercase()
        append("<h1>${HtmlClean.escape(d.label)} <span class=\"$cls\">$diff</span></h1>")
        if (d.platform != dev.titan.code.data.Platform.LEETCODE) append("<div class=\"meta\">${d.platform.title}</div>")
        if (d.tags.isNotEmpty()) {
            append("<div class=\"meta\">")
            d.tags.forEach { append("<span class=\"tag\">${HtmlClean.escape(it)}</span>") }
            append("</div>")
        }
        if (placeholder) {
            append("<p class=\"note\">Bundled copy written for Coding. The original statement replaces it the next time this problem opens online.</p>")
        }
        when {
            d.contentHtml != null -> append(d.contentHtml)
            d.paidOnly && d.platform == dev.titan.code.data.Platform.NEETCODE -> append("<p class=\"note\">NeetCode Pro problem. Sign in to NeetCode with a Pro account (Settings) and refresh it to read it.</p>")
            d.paidOnly -> append("<p class=\"note\">Premium problem. Sign in with a LeetCode Premium account (Settings) and open it again to read it.</p>")
            else -> append("<p class=\"note\">No statement saved.</p>")
        }
        if (d.starter == null) append("<p class=\"note\">This problem has no Python 3 starter code.</p>")
        d.hints.forEachIndexed { i, h ->
            // NeetCode's hints carry their own title ("Recommended Time & Space Complexity", "Hint 1").
            val title = Regex("^<b>(.*?)</b>").find(h)?.groupValues?.get(1)
            append("<details><summary>${title ?: "Hint ${i + 1}"}</summary>").append(HtmlClean.statement(if (title != null) h.substringAfter("</b>") else h)).append("</details>")
        }
        val similar = runCatching { JSONArray(d.similarJson) }.getOrNull()
        if (similar != null && similar.length() > 0) {
            append("<h2>Similar</h2><ul>")
            for (i in 0 until similar.length()) {
                val o = similar.getJSONObject(i)
                append("<li>${HtmlClean.escape(o.optString("title"))} <span class=\"dim\">${o.optString("difficulty")}</span></li>")
            }
            append("</ul>")
        }
    }

    fun loading(text: String) = "<p class=\"dim\">${HtmlClean.escape(text)}</p>"

    /** The Discussion tab: threads and replies, every long code block folded (rev 10). */
    fun discussion(title: String, disc: Repository.Discussion?, status: String?): String = buildString {
        append("<h1>${HtmlClean.escape(title)}</h1>")
        if (status != null) append("<p class=\"note\">${HtmlClean.escape(status)}</p>")
        if (disc == null) {
            if (status == null) append("<p class=\"dim\">Nothing saved yet. It downloads when you open the problem online.</p>")
            return@buildString
        }
        append("<div class=\"meta\">Saved ${date(disc.fetchedAt)}</div>")
        if (disc.comments.isEmpty()) append("<p class=\"dim\">No discussion yet.</p>")
        disc.comments.forEach { append(comment(it)) }
    }

    /**
     * An opened solution post (rev 10): title, body (Python code open, other languages folded),
     * then its comments with replies (code folded), and a "Load more" link while there are more.
     */
    fun post(s: Solution, post: Post?, status: String?): String = buildString {
        append("<h1>${HtmlClean.escape(s.title)}</h1>")
        append("<div class=\"post-meta\">${HtmlClean.escape(s.author)}" + (if (s.topicId == 0L || s.html) "" else " · ▲ ${s.votes} · 💬 ${s.commentCount}") + "</div>")
        if (status != null) append("<p class=\"note\">${HtmlClean.escape(status)}</p>")
        val body = post?.body ?: s.body
        if (body != null) append(if (s.html) dev.titan.code.data.codeforces.CodeforcesEditorial.show(body) else Markdown.toHtml(body, Markdown.Fold.NON_PYTHON))
        else if (status == null) append("<p class=\"dim\">Not saved. Open it online to read it.</p>")
        // Rev 33: a Codeforces editorial can always be opened on the site.
        if (s.html && s.topicId > 0) {
            val url = dev.titan.code.data.codeforces.CodeforcesEditorial.blogUrl(s.topicId)
            append("<a class=\"more-link\" href=\"$OPEN_LINK${HtmlClean.escape(url)}\">Open the editorial in the browser</a>")
        }
        if (post == null || s.topicId == 0L || s.html) return@buildString
        append("<h2>Comments (${post.totalComments})</h2>")
        if (post.comments.isEmpty()) append("<p class=\"dim\">No comments.</p>")
        post.comments.forEach { append(comment(it)) }
        if (post.hasMore) append("<a class=\"more-link\" href=\"$MORE_LINK\">Load more comments (${post.comments.size} of ${post.totalComments})</a>")
    }

    /** Intercepted by the WebView client; never navigates. */
    const val MORE_LINK = "titan://more-comments"

    /** Followed by a URL to open in the phone's browser (rev 33). */
    const val OPEN_LINK = "titan://open?"

    private fun comment(c: Comment): String = buildString {
        append("<div class=\"c\"><div class=\"who\"><b>${HtmlClean.escape(c.author)}</b> · ▲ ${c.votes} · ${date(c.createdSec * 1000)}</div>")
        append(Markdown.toHtml(c.body, Markdown.Fold.ALL))
        if (c.replies.isNotEmpty()) {
            append("<div class=\"replies\">")
            c.replies.forEach { append(comment(it)) }
            append("</div>")
        } else if (c.replyCount > 0) {
            append("<div class=\"dim\">${c.replyCount} replies not saved</div>")
        }
        append("</div>")
    }

    private fun date(ms: Long) = if (ms <= 0) "" else SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))
}
