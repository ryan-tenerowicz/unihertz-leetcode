package dev.titan.code.data.leetcode

import dev.titan.code.data.Comment
import dev.titan.code.data.Daily
import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.ProblemSummary
import dev.titan.code.data.Solution
import org.json.JSONArray
import org.json.JSONObject

/**
 * leetcode.com GraphQL requests and response parsers (PLAN §6.2). Pure functions over JSON
 * strings, so they're tested against real responses saved in `src/test/resources/leetcode/`.
 *
 * Checked on 2026-11-02: every query here works without signing in. Signed in, the list also
 * carries the user's `status`.
 */
object LeetCodeApi {

    const val ORIGIN = "https://leetcode.com"
    const val GRAPHQL = "$ORIGIN/graphql/"
    const val LOGIN_URL = "$ORIGIN/accounts/login/"

    /** The list endpoint returns at most 100 rows per request, whatever `limit` says. */
    const val LIST_PAGE = 100

    /** "algorithms" leaves out database, shell, concurrency and pandas problems, which have no python3 snippet (PLAN §4.3). */
    private const val CATEGORY = "algorithms"

    fun listRequest(skip: Int, limit: Int = LIST_PAGE) = request(
        """query problemsetQuestionList(${'$'}categorySlug: String, ${'$'}limit: Int, ${'$'}skip: Int, ${'$'}filters: QuestionListFilterInput) {
          problemsetQuestionList: questionList(categorySlug: ${'$'}categorySlug limit: ${'$'}limit skip: ${'$'}skip filters: ${'$'}filters) {
            total: totalNum
            questions: data { acRate difficulty frontendQuestionId: questionFrontendId paidOnly: isPaidOnly status title titleSlug topicTags { slug } }
          }
        }""",
        JSONObject().put("categorySlug", CATEGORY).put("skip", skip).put("limit", limit).put("filters", JSONObject()),
    )

    data class ListPage(val total: Int, val problems: List<ProblemSummary>)

    fun parseList(body: String): ListPage {
        val o = data(body).getJSONObject("problemsetQuestionList")
        val qs = o.getJSONArray("questions")
        return ListPage(
            total = o.getInt("total"),
            problems = (0 until qs.length()).map { i ->
                val q = qs.getJSONObject(i)
                ProblemSummary(
                    slug = q.getString("titleSlug"),
                    frontendId = q.getString("frontendQuestionId").toIntOrNull() ?: 0,
                    title = q.getString("title"),
                    difficulty = q.getString("difficulty"),
                    paidOnly = q.optBoolean("paidOnly"),
                    acRate = q.optDouble("acRate", 0.0),
                    tags = q.optJSONArray("topicTags").objects().map { it.getString("slug") },
                    status = q.optStringOrNull("status"),
                )
            },
        )
    }

    fun detailRequest(slug: String) = request(
        """query questionData(${'$'}titleSlug: String!) {
          question(titleSlug: ${'$'}titleSlug) {
            questionId questionFrontendId title titleSlug content difficulty isPaidOnly
            exampleTestcases metaData hints similarQuestions topicTags { slug }
            codeSnippets { langSlug code }
          }
        }""",
        JSONObject().put("titleSlug", slug),
    )

    /** Null if LeetCode has no such problem. */
    fun parseDetail(body: String): ProblemDetail? {
        val q = data(body).optJSONObject("question") ?: return null
        val starter = q.optJSONArray("codeSnippets").objects()
            .firstOrNull { it.optString("langSlug") == "python3" }?.getString("code")
        return ProblemDetail(
            slug = q.getString("titleSlug"),
            questionId = q.getString("questionId"),
            frontendId = q.getString("questionFrontendId").toIntOrNull() ?: 0,
            title = q.getString("title"),
            difficulty = q.getString("difficulty"),
            paidOnly = q.optBoolean("isPaidOnly"),
            contentHtml = q.optStringOrNull("content"),
            starter = starter,
            exampleTestcases = q.optString("exampleTestcases"),
            metaData = q.optString("metaData"),
            hints = q.optJSONArray("hints").strings(),
            tags = q.optJSONArray("topicTags").objects().map { it.getString("slug") },
            similarJson = q.optString("similarQuestions", "[]"),
        )
    }

    fun dailyRequest() = request(
        "query { activeDailyCodingChallengeQuestion { date question { titleSlug title questionFrontendId difficulty } } }",
        JSONObject(),
    )

    fun parseDaily(body: String): Daily? {
        val d = data(body).optJSONObject("activeDailyCodingChallengeQuestion") ?: return null
        val q = d.getJSONObject("question")
        return Daily(
            date = d.getString("date"),
            slug = q.getString("titleSlug"),
            title = q.getString("title"),
            frontendId = q.getString("questionFrontendId").toIntOrNull() ?: 0,
            difficulty = q.getString("difficulty"),
        )
    }

    /** Study plans offered as offline packs (PLAN §8.4); all readable without signing in (checked 2026-11-02). */
    val STUDY_PLANS = listOf(
        "top-interview-150" to "Top Interview 150",
        "leetcode-75" to "LeetCode 75",
        "top-100-liked" to "Top 100 Liked",
        "binary-search" to "Binary Search",
        "dynamic-programming" to "Dynamic Programming",
    )

    fun studyPlanRequest(slug: String) = request(
        "query d(${'$'}slug: String!) { studyPlanV2Detail(planSlug: ${'$'}slug) { name planSubGroups { questions { titleSlug } } } }",
        JSONObject().put("slug", slug),
    )

    /** The plan's name and its problems' slugs, in plan order. */
    fun parseStudyPlan(body: String): Pair<String, List<String>> {
        val p = data(body).optJSONObject("studyPlanV2Detail") ?: throw ApiError("No such study plan")
        val slugs = p.optJSONArray("planSubGroups").objects()
            .flatMap { g -> g.optJSONArray("questions").objects().map { it.getString("titleSlug") } }
            .distinct()
        return p.getString("name") to slugs
    }

    fun userStatusRequest() = request("query { userStatus { username isSignedIn } }", JSONObject())

    /** The signed-in username, or null if the cookies aren't signed in. */
    fun parseUserStatus(body: String): String? {
        val u = data(body).optJSONObject("userStatus") ?: return null
        return if (u.optBoolean("isSignedIn")) u.optStringOrNull("username") else null
    }

    // ---- discussion (PLAN §8.2) ---------------------------------------------------------

    fun discussionTopicRequest(slug: String) = request(
        "query t(${'$'}questionSlug: String!) { questionDiscussionTopic(questionSlug: ${'$'}questionSlug) { id topLevelCommentCount } }",
        JSONObject().put("questionSlug", slug),
    )

    /** The problem's discussion topic id, or null if it has none. */
    fun parseDiscussionTopic(body: String): Long? =
        data(body).optJSONObject("questionDiscussionTopic")?.optLong("id")?.takeIf { it > 0 }

    fun commentsRequest(topicId: Long, count: Int, page: Int = 1) = request(
        """query c(${'$'}topicId: Int!, ${'$'}orderBy: String, ${'$'}pageNo: Int, ${'$'}numPerPage: Int) {
          topicComments(topicId: ${'$'}topicId, orderBy: ${'$'}orderBy, pageNo: ${'$'}pageNo, numPerPage: ${'$'}numPerPage) {
            totalNum data { id post { content voteCount creationDate author { username } } numChildren }
          }
        }""",
        JSONObject().put("topicId", topicId).put("orderBy", "best").put("pageNo", page).put("numPerPage", count),
    )

    fun parseComments(body: String): List<Comment> =
        data(body).getJSONObject("topicComments").optJSONArray("data").objects().map(::comment)

    /** The topic's total number of top-level comments, from the same response. */
    fun parseCommentTotal(body: String): Int = data(body).getJSONObject("topicComments").optInt("totalNum")

    fun repliesRequest(commentId: Long) = request(
        """query r(${'$'}commentId: Int!) {
          commentReplies(commentId: ${'$'}commentId) { id post { content voteCount creationDate author { username } } numChildren }
        }""",
        JSONObject().put("commentId", commentId),
    )

    fun parseReplies(body: String): List<Comment> = data(body).optJSONArray("commentReplies").objects().map(::comment)

    private fun comment(o: JSONObject): Comment {
        val post = o.getJSONObject("post")
        return Comment(
            id = o.getLong("id"),
            author = post.optJSONObject("author")?.optStringOrNull("username") ?: "[deleted]",
            votes = post.optInt("voteCount"),
            createdSec = post.optLong("creationDate"),
            body = unescape(post.optString("content")),
            replyCount = o.optInt("numChildren"),
        )
    }

    /** Top community solutions tagged Python, most votes first (summaries only). */
    fun solutionsRequest(slug: String, count: Int) = request(
        """query s(${'$'}questionSlug: String!, ${'$'}orderBy: ArticleOrderByEnum, ${'$'}tagSlugs: [String!], ${'$'}skip: Int, ${'$'}first: Int) {
          ugcArticleSolutionArticles(questionSlug: ${'$'}questionSlug orderBy: ${'$'}orderBy tagSlugs: ${'$'}tagSlugs skip: ${'$'}skip first: ${'$'}first) {
            edges { node { title topicId summary author { userName } reactions { count reactionType } topic { topLevelCommentCount } } }
          }
        }""",
        JSONObject().put("questionSlug", slug).put("orderBy", "MOST_VOTES")
            .put("tagSlugs", JSONArray().put("python3")).put("skip", 0).put("first", count),
    )

    fun parseSolutions(body: String): List<Solution> =
        data(body).getJSONObject("ugcArticleSolutionArticles").optJSONArray("edges").objects().map { e ->
            val n = e.getJSONObject("node")
            Solution(
                topicId = n.getLong("topicId"),
                title = n.getString("title"),
                author = n.optJSONObject("author")?.optStringOrNull("userName") ?: "[deleted]",
                votes = upvotes(n.optJSONArray("reactions")),
                body = null,
                commentCount = n.optJSONObject("topic")?.optInt("topLevelCommentCount") ?: 0,
                summary = unescape(n.optString("summary")).replace(Regex("\\s+"), " ").trim(),
            )
        }

    fun solutionRequest(topicId: Long) = request(
        "query s(${'$'}topicId: ID!) { ugcArticleSolutionArticle(topicId: ${'$'}topicId) { content } }",
        JSONObject().put("topicId", topicId.toString()),
    )

    fun parseSolutionBody(body: String): String? =
        data(body).optJSONObject("ugcArticleSolutionArticle")?.optStringOrNull("content")?.let(::unescape)

    private fun upvotes(reactions: JSONArray?) =
        reactions.objects().firstOrNull { it.optString("reactionType") == "UPVOTE" }?.optInt("count") ?: 0

    // ---- helpers ------------------------------------------------------------------------

    private fun request(query: String, variables: JSONObject): String =
        JSONObject().put("query", query.trimIndent()).put("variables", variables).toString()

    class ApiError(message: String) : Exception(message)

    private fun data(body: String): JSONObject {
        val root = JSONObject(body)
        root.optJSONArray("errors")?.let { errs ->
            if (root.optJSONObject("data") == null) throw ApiError(errs.optJSONObject(0)?.optString("message") ?: "GraphQL error")
        }
        return root.optJSONObject("data") ?: throw ApiError("No data in response")
    }

    /**
     * Comment and solution bodies arrive escaped a second time: a newline is the two characters
     * `\` `n`. Undo that: `\n` `\t` `\"` `\'` `\\` `\/` and `\uXXXX`. Anything else stays as written.
     */
    fun unescape(s: String): String {
        if ('\\' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { out.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'r' -> { i += 2 }
                '"', '\'', '\\', '/' -> { out.append(n); i += 2 }
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                    if (code != null) { out.append(code.toChar()); i += 6 } else { out.append(c); i++ }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { has(key) }
}
