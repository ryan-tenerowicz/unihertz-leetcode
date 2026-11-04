package dev.titan.code

import dev.titan.code.data.HtmlClean
import dev.titan.code.data.Markdown
import dev.titan.code.data.leetcode.LeetCodeApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers against real leetcode.com responses saved on 2026-11-02 (PLAN §10). */
class LeetCodeApiTest {
    private fun res(name: String) = javaClass.getResource("/leetcode/$name")!!.readText()

    @Test fun parsesList() {
        val page = LeetCodeApi.parseList(res("list.json"))
        assertEquals(3682, page.total)
        assertEquals(5, page.problems.size)
        val p = page.problems[0]
        assertEquals("two-sum", p.slug)
        assertEquals(1, p.frontendId)
        assertEquals("Easy", p.difficulty)
        assertEquals(listOf("array", "hash-table"), p.tags)
        assertNull(p.status)
        assertFalse(p.paidOnly)
    }

    @Test fun parsesDetail() {
        val d = LeetCodeApi.parseDetail(res("detail-group-anagrams.json"))!!
        assertEquals("49", d.questionId)
        assertEquals("Medium", d.difficulty)
        assertTrue(d.starter!!.startsWith("class Solution:\n    def groupAnagrams(self, strs: list[str])"))
        assertTrue(d.contentHtml!!.contains("<code>strs</code>"))
        assertTrue(d.metaData.contains("groupAnagrams"))
        assertEquals(listOf("array", "hash-table", "string", "sorting"), d.tags)
    }

    @Test fun parsesCommentsAndUnescapes() {
        val cs = LeetCodeApi.parseComments(res("comments.json"))
        assertEquals(3, cs.size)
        assertEquals(954, cs[0].votes)
        assertEquals(21, cs[0].replyCount)
        assertTrue(cs[1].body.contains("months.\nThis journey"))
        assertTrue(cs[1].body.endsWith("🚀"))
    }

    @Test fun parsesSolutionBody() {
        val body = LeetCodeApi.parseSolutionBody(res("solution.json"))!!
        assertTrue(body.startsWith("**Appreciate if you could upvote this solution**\n\n"))
        assertTrue(body.contains("```\nclass Solution:\n    def groupAnagrams"))
    }

    @Test fun requestsAreValidJson() {
        val o = JSONObject(LeetCodeApi.listRequest(200))
        assertEquals(200, o.getJSONObject("variables").getInt("skip"))
        assertTrue(o.getString("query").contains("questionList("))
        JSONObject(LeetCodeApi.solutionRequest(42)).getJSONObject("variables").getString("topicId")
    }

    @Test fun graphqlErrorsThrow() {
        try {
            LeetCodeApi.parseDetail("""{"errors":[{"message":"boom"}]}""")
            throw AssertionError("expected ApiError")
        } catch (e: LeetCodeApi.ApiError) {
            assertEquals("boom", e.message)
        }
        assertNull(LeetCodeApi.parseDetail("""{"data":{"question":null}}"""))
    }

    @Test fun unescapeHandlesSequences() {
        assertEquals("a\nb\"c'd\\e/fé", LeetCodeApi.unescape("""a\nb\"c\'d\\e\/fé"""))
        assertEquals("plain", LeetCodeApi.unescape("plain"))
        assertEquals("""\x""", LeetCodeApi.unescape("""\x"""))
    }

    @Test fun cleansStatement() {
        val html = """<p onclick="x()">Hi</p><script>alert(1)</script><a href="javascript:evil()">l</a><img src="https://a/b.png" onerror='x'>"""
        val clean = HtmlClean.statement(html)
        assertEquals("""<p>Hi</p><a href="#">l</a><img src="https://a/b.png">""", clean)
        assertEquals(listOf("https://a/b.png"), HtmlClean.imageUrls(clean))
        assertEquals("""<p>Hi</p><a href="#">l</a><img src="file:///x.png">""",
            HtmlClean.rewriteImages(clean, mapOf("https://a/b.png" to "file:///x.png")))
    }

    @Test fun markdownBasics() {
        val html = Markdown.toHtml("**Bold** and `a<b` and *it*\nnext line\n\n```python\ndef f(*args):\n    return a<b\n```\n- one\n- two\n# Title\n![img](http://x/y.png) [link](http://z)")
        assertEquals(
            "<p><b>Bold</b> and <code>a&lt;b</code> and <i>it</i><br>next line</p>\n" +
                "<pre><span class=\"lang\">python</span><code>def f(*args):\n    return a&lt;b</code></pre>\n" +
                "<ul><li>one</li><li>two</li></ul>\n" +
                "<h3>Title</h3>\n" +
                "<p><span class=\"dim\">[image]</span> <a>link</a></p>\n",
            html,
        )
    }

    private val longPy = "```python\n1\n2\n3\n4\n5\n6\n```"
    private val longCpp = "```cpp\n1\n2\n3\n4\n5\n```"

    @Test fun commentsFoldLongCode() {
        val html = Markdown.toHtml(longPy, Markdown.Fold.ALL)
        assertTrue(html.startsWith("<details class=\"code\"><summary>"))
        assertTrue(html.contains("<code>1\n2\n3\n4</code></pre><span class=\"more\">▸ 2 more lines · python</span>"))
        assertTrue(html.contains("<code>1\n2\n3\n4\n5\n6</code>"))
    }

    @Test fun postsOpenPythonFoldOthers() {
        assertTrue(Markdown.toHtml(longPy, Markdown.Fold.NON_PYTHON).startsWith("<details class=\"code\" open>"))
        assertTrue(Markdown.toHtml(longCpp, Markdown.Fold.NON_PYTHON).startsWith("<details class=\"code\"><summary>"))
        assertTrue(Markdown.toHtml("```Python3 []\n1\n2\n3\n4\n5\n```", Markdown.Fold.NON_PYTHON).startsWith("<details class=\"code\" open>"))
    }

    @Test fun shortCodeNeverFolds() {
        assertEquals("<pre><code>a\nb</code></pre>\n", Markdown.toHtml("```\na\nb\n```", Markdown.Fold.ALL))
    }

    @Test fun parsesSolutionListFields() {
        val body = """{"data":{"ugcArticleSolutionArticles":{"edges":[{"node":{"title":"T","topicId":5,"summary":"a\\nb  c","author":{"userName":"u"},"reactions":[{"count":7,"reactionType":"UPVOTE"}],"topic":{"topLevelCommentCount":12}}}]}}}"""
        val s = LeetCodeApi.parseSolutions(body).single()
        assertEquals(12, s.commentCount)
        assertEquals(7, s.votes)
        assertEquals("a b c", s.summary)
    }

    @Test fun unfencedCodeBecomesOneBlock() {
        val md = "ChatGPT solution:\n\nclass A:\n    def f(self):\n        x = 1\n\n        # note\n        return x\n\nThanks for reading the post."
        val html = Markdown.toHtml(md, Markdown.Fold.ALL)
        assertTrue(html, html.startsWith("<p>ChatGPT solution:</p>\n<details class=\"code\">"))
        assertTrue(html, html.contains("<code>class A:\n    def f(self):\n        x = 1\n\n        # note\n        return x</code>"))
        assertTrue(html, html.endsWith("<p>Thanks for reading the post.</p>\n"))
    }

    @Test fun proseStaysProse() {
        assertEquals("<p>I think this is easy.<br>Great answer, thanks!</p>\n", Markdown.toHtml("I think this is easy.\nGreat answer, thanks!", Markdown.Fold.ALL))
    }

    @Test fun markdownLeavesPythonStarsAlone() {
        assertEquals("f(*args, **kwargs) and a*b*c", Markdown.inline("f(*args, **kwargs) and a*b*c"))
    }
}
