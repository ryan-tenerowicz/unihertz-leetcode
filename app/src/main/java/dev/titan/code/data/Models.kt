package dev.titan.code.data

/**
 * Platform-neutral models (PLAN §6.1). Each keeps what the UI and later milestones need; the
 * platform's own identifiers stay alongside (for LeetCode, the numeric `questionId` and the slug).
 */

/** One row of the problem list (PLAN §4.3). */
data class ProblemSummary(
    val slug: String,
    val frontendId: Int,
    val title: String,
    val difficulty: String,
    val paidOnly: Boolean,
    val acRate: Double,
    val tags: List<String>,
    /** LeetCode's per-user status when signed in: "ac" (solved), "notac" (attempted) or null. */
    val status: String?,
) {
    val platform get() = Platform.of(slug)
    /** The number shown in the list: LeetCode's id, the LeetCode number of a NeetCode problem, or a Codeforces code like `1850A`. */
    val number: String get() = if (platform == Platform.CODEFORCES) Platform.CODEFORCES.siteId(slug) else if (frontendId > 0) frontendId.toString() else ""
    val label get() = if (number.isEmpty()) title else "$number. $title"
}

/** Everything saved for a problem so it opens offline (PLAN §8.2). */
data class ProblemDetail(
    val slug: String,
    val questionId: String,
    val frontendId: Int,
    val title: String,
    val difficulty: String,
    val paidOnly: Boolean,
    /** Statement HTML, already cleaned (and with images rewritten to local files once saved). Null for locked premium problems. */
    val contentHtml: String?,
    /** The `python3` snippet; null when the problem has none (SQL, shell…). */
    val starter: String?,
    val exampleTestcases: String,
    val metaData: String,
    val hints: List<String>,
    val tags: List<String>,
    /** LeetCode's `similarQuestions` JSON, kept as-is. */
    val similarJson: String,
) {
    val platform get() = Platform.of(slug)
    val label get() = when {
        platform == Platform.CODEFORCES -> "${Platform.CODEFORCES.siteId(slug)}. $title"
        frontendId > 0 -> "$frontendId. $title"
        else -> title
    }
}

/** A discussion comment and its direct replies (PLAN §8.2). The body is Markdown. */
data class Comment(
    val id: Long,
    val author: String,
    val votes: Int,
    val createdSec: Long,
    val body: String,
    val replyCount: Int,
    val replies: List<Comment> = emptyList(),
)

/** A community solution post (PLAN §4.1). The body is Markdown; null until the post is opened (or prefetched). */
data class Solution(
    val topicId: Long,
    val title: String,
    val author: String,
    val votes: Int,
    val body: String?,
    val commentCount: Int = 0,
    val summary: String = "",
)

/** An opened solution post: its body and the comments loaded so far (pages of [Repository.POST_PAGE]). */
data class Post(
    val topicId: Long,
    val body: String,
    val comments: List<Comment>,
    val totalComments: Int,
    val pagesLoaded: Int,
    val fetchedAt: Long,
) {
    val hasMore get() = comments.size < totalComments
}

/**
 * A test input for Run (PLAN §8.3). Examples come from the statement; others are saved from
 * failed submissions. [expected] is LeetCode's official output once known ([verified]).
 */
data class TestCase(
    /** Null for examples (they're derived from the statement, not stored). */
    val id: Long?,
    val input: String,
    val expected: String?,
    /** "example" or "submission". */
    val source: String,
    val verified: Boolean,
)

/** One past submission (PLAN §8.2 "submission history"). */
data class SubmissionRecord(val at: Long, val status: String, val runtime: String, val memory: String, val passed: String, val elapsedMs: Long? = null)

/** The day's problem (PLAN §4.3: "Daily"). */
data class Daily(val date: String, val slug: String, val title: String, val frontendId: Int, val difficulty: String)
