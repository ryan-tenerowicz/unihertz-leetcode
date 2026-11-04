package dev.titan.code.data

import android.content.Context
import dev.titan.code.TLog
import dev.titan.code.data.db.DetailRow
import dev.titan.code.data.db.TimerRow
import dev.titan.code.data.db.DiscussionRow
import dev.titan.code.data.db.DraftRow
import dev.titan.code.data.db.DraftVersionRow
import dev.titan.code.data.db.MetaRow
import dev.titan.code.data.db.PostRow
import dev.titan.code.data.db.PackRow
import dev.titan.code.data.db.SubmissionRow
import dev.titan.code.data.db.TestCaseRow
import dev.titan.code.data.leetcode.Judge
import kotlinx.coroutines.delay
import dev.titan.code.data.db.ProblemRow
import dev.titan.code.data.db.StoreDb
import dev.titan.code.data.leetcode.LeetCodeApi
import dev.titan.code.data.codeforces.CodeforcesApi
import dev.titan.code.data.codeforces.CodeforcesWeb
import dev.titan.code.data.neetcode.NeetCodeApi
import dev.titan.code.data.neetcode.NeetCodeHttp
import dev.titan.code.samples.DraftStore
import dev.titan.code.samples.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The offline-first repository (PLAN §8.1). Screens read from the Room store; the network only
 * refreshes it. Rev 13: three platforms. Each problem key says which one it belongs to
 * ([Platform.of]); the store's `provider` column holds the platform id. LeetCode keeps its bare
 * slugs, so data saved before rev 13 is unchanged.
 */
class Repository(
    context: Context,
    private val http: LeetCodeHttp,
    private val nc: NeetCodeHttp,
    private val cf: CodeforcesWeb,
) {
    private val app = context.applicationContext
    private val db = StoreDb.open(app)
    private val dao = db.dao()
    private val imagesDir = File(app.filesDir, "images").apply { mkdirs() }
    /** Drafts are written on one background thread, in order; [flushDrafts] waits for it (E14). */
    private val draftWriter = Executors.newSingleThreadExecutor { Thread(it, "draft-writer") }

    // ---- first run ------------------------------------------------------------------------

    /**
     * Seed the bundled samples (so a fresh install works offline) and move M1's draft files into
     * Room (PLAN §12, M2). Both run once.
     */
    suspend fun setUp(samples: List<Sample>) = withContext(Dispatchers.IO) {
        if (dao.meta(META_SEEDED) == null) {
            dao.upsertProblems(samples.map {
                ProblemRow(P, it.slug, it.number, it.title, it.difficulty, false, 0.0, "", null)
            })
            samples.forEach {
                dao.upsertDetail(
                    DetailRow(P, it.slug, it.number.toString(), it.number, it.title, it.difficulty, false,
                        it.statementHtml, it.starter, "", "", "[]", "", "[]", fetchedAt = 0)
                )
            }
            dao.putMeta(MetaRow(META_SEEDED, "1"))
        }
        val old = File(app.filesDir, "drafts")
        if (old.isDirectory) {
            val files = DraftStore(old)
            samples.forEach { s ->
                val d = files.load(s.slug) ?: return@forEach
                if (dao.draft(P, s.slug) == null) {
                    dao.upsertDraft(DraftRow(P, s.slug, d.text, d.line, d.column, System.currentTimeMillis()))
                }
                files.loadBackup(s.slug)?.let { dao.addVersion(DraftVersionRow(provider = P, slug = s.slug, text = it, savedAt = 0, reason = "migrated"), VERSIONS_KEPT) }
            }
            if (old.renameTo(File(app.filesDir, "drafts-m1-migrated"))) TLog.d("drafts: M1 files migrated to Room")
        }
    }

    // ---- problem list (PLAN §4.3) ---------------------------------------------------------

    /**
     * A platform's list. NeetCode's is the bundled catalog in NeetCode's order (seeded into the
     * store on first use, so solved marks have somewhere to live); Codeforces' is newest first.
     */
    suspend fun problems(platform: Platform = Platform.LEETCODE): List<ProblemSummary> = withContext(Dispatchers.IO) {
        if (platform == Platform.NEETCODE) seedNeetCode()
        val rows = dao.problems(platform.id).map {
            ProblemSummary(it.slug, it.frontendId, it.title, it.difficulty, it.paidOnly, it.acRate,
                it.tags.split(',').filter(String::isNotEmpty), it.status)
        }
        when (platform) {
            Platform.NEETCODE -> {
                val order = ncCatalog.withIndex().associate { Platform.NEETCODE.key(it.value.id) to it.index }
                rows.sortedBy { order[it.slug] ?: Int.MAX_VALUE }
            }
            Platform.CODEFORCES -> rows.sortedWith(compareByDescending<ProblemSummary> { it.frontendId }.thenBy { it.slug })
            Platform.LEETCODE -> rows
        }
    }

    suspend fun savedSlugs(): Set<String> = withContext(Dispatchers.IO) { Platform.entries.flatMap { dao.savedSlugs(it.id) }.toSet() }
    suspend fun draftSlugs(): Set<String> = withContext(Dispatchers.IO) { Platform.entries.flatMap { dao.draftSlugs(it.id) }.toSet() }

    suspend fun listUpdatedAt(platform: Platform = Platform.LEETCODE): Long = withContext(Dispatchers.IO) {
        dao.meta(listKey(platform))?.toLongOrNull() ?: 0
    }

    private fun listKey(p: Platform) = if (p == Platform.LEETCODE) META_LIST_AT else "list.fetchedAt.${p.id}"

    // ---- NeetCode's bundled catalog (rev 13) ---------------------------------------------------

    val ncCatalog: List<NeetCodeApi.Entry> by lazy {
        runCatching { NeetCodeApi.parseCatalog(app.assets.open("neetcode/catalog.json").bufferedReader().use { it.readText() }) }
            .onFailure { TLog.w("neetcode catalog: $it") }.getOrDefault(emptyList())
    }

    private suspend fun seedNeetCode() {
        if (dao.meta(META_NC_SEEDED) == NC_SNAPSHOT) return
        val have = dao.problems(Platform.NEETCODE.id).associateBy { it.slug }
        dao.upsertProblems(NeetCodeApi.summaries(ncCatalog).map {
            ProblemRow(Platform.NEETCODE.id, it.slug, it.frontendId, it.title, it.difficulty, it.paidOnly, 0.0,
                it.tags.joinToString(","), have[it.slug]?.status)
        })
        dao.putMeta(MetaRow(META_NC_SEEDED, NC_SNAPSHOT))
        dao.putMeta(MetaRow(listKey(Platform.NEETCODE), System.currentTimeMillis().toString()))
    }

    /** Download a platform's list. LeetCode: 100 rows per request; Codeforces: one request; NeetCode: bundled. */
    suspend fun refreshList(platform: Platform, progress: (Int, Int) -> Unit = { _, _ -> }) {
        when (platform) {
            Platform.LEETCODE -> refreshList(progress)
            Platform.NEETCODE -> withContext(Dispatchers.IO) { seedNeetCode() }
            Platform.CODEFORCES -> {
                progress(0, 1)
                val list = CodeforcesApi.parseProblems(http.fetchText(CodeforcesApi.PROBLEMS_URL))
                withContext(Dispatchers.IO) {
                    val status = dao.problems(Platform.CODEFORCES.id).associate { it.slug to it.status }
                    list.chunked(1000).forEach { chunk ->
                        dao.upsertProblems(chunk.map {
                            ProblemRow(Platform.CODEFORCES.id, it.slug, it.frontendId, it.title, it.difficulty, false, it.acRate,
                                it.tags.joinToString(","), status[it.slug])
                        })
                    }
                    dao.putMeta(MetaRow(listKey(Platform.CODEFORCES), System.currentTimeMillis().toString()))
                }
                progress(1, 1)
            }
        }
    }

    /** Download the whole LeetCode list, 100 rows per request. [progress] gets (done, total). */
    suspend fun refreshList(progress: (Int, Int) -> Unit = { _, _ -> }) {
        var skip = 0
        var total = Int.MAX_VALUE
        while (skip < total) {
            val page = LeetCodeApi.parseList(http.graphql(LeetCodeApi.listRequest(skip)))
            total = page.total
            withContext(Dispatchers.IO) {
                dao.upsertProblems(page.problems.map {
                    ProblemRow(P, it.slug, it.frontendId, it.title, it.difficulty, it.paidOnly, it.acRate, it.tags.joinToString(","), it.status)
                })
            }
            skip += LeetCodeApi.LIST_PAGE
            progress(minOf(skip, total), total)
            if (page.problems.isEmpty()) break
        }
        withContext(Dispatchers.IO) { dao.putMeta(MetaRow(META_LIST_AT, System.currentTimeMillis().toString())) }
        runCatching { refreshDaily() }
    }

    suspend fun refreshDaily(): Daily? {
        val d = LeetCodeApi.parseDaily(http.graphql(LeetCodeApi.dailyRequest())) ?: return null
        withContext(Dispatchers.IO) {
            dao.putMeta(MetaRow(META_DAILY, JSONObject().put("date", d.date).put("slug", d.slug).put("title", d.title)
                .put("id", d.frontendId).put("difficulty", d.difficulty).toString()))
        }
        return d
    }

    suspend fun daily(): Daily? = withContext(Dispatchers.IO) {
        val o = dao.meta(META_DAILY)?.let(::JSONObject) ?: return@withContext null
        Daily(o.getString("date"), o.getString("slug"), o.getString("title"), o.getInt("id"), o.getString("difficulty"))
    }

    suspend fun username(): String? = LeetCodeApi.parseUserStatus(http.graphql(LeetCodeApi.userStatusRequest()))

    // ---- problems (PLAN §8.2) -------------------------------------------------------------

    /** The saved detail, or null if it was never saved. */
    suspend fun savedDetail(slug: String): ProblemDetail? = withContext(Dispatchers.IO) {
        var row = dao.detail(pv(slug), slug) ?: return@withContext null
        // Rev 30: Codeforces statements saved with MathJax's double drawing are repaired once, offline.
        if (row.provider == Platform.CODEFORCES.id) {
            row.contentHtml?.let(CodeforcesApi::repairSavedMath)?.let { fixed ->
                row = row.copy(contentHtml = fixed)
                dao.upsertDetail(row)
            }
        }
        row.toModel()
    }

    /** True if the saved copy is a bundled sample that the real statement should replace. */
    suspend fun isPlaceholder(slug: String): Boolean = withContext(Dispatchers.IO) { dao.detail(pv(slug), slug)?.fetchedAt == 0L }

    /** True if the saved copy came from an older NeetCode parser and should be downloaded again (rev 23). */
    suspend fun isStale(slug: String): Boolean = Platform.of(slug) == Platform.NEETCODE && withContext(Dispatchers.IO) {
        val meta = dao.detail(pv(slug), slug)?.metaData ?: return@withContext false
        runCatching { org.json.JSONObject(meta).optInt("parser") }.getOrDefault(0) < NeetCodeApi.PARSER
    }

    /** Download a problem, its statement images, and save it (save-on-open, PLAN §8.4). */
    suspend fun fetchDetail(slug: String): ProblemDetail? = when (Platform.of(slug)) {
        Platform.LEETCODE -> fetchLeetCode(slug)
        Platform.NEETCODE -> fetchNeetCode(slug)
        Platform.CODEFORCES -> fetchCodeforces(slug)
    }

    private suspend fun fetchLeetCode(slug: String): ProblemDetail? {
        val d = LeetCodeApi.parseDetail(http.graphql(LeetCodeApi.detailRequest(slug), "${LeetCodeApi.ORIGIN}/problems/$slug/"))
            ?: return null
        return save(d.copy(contentHtml = d.contentHtml?.let(HtmlClean::statement)))
    }

    /**
     * NeetCode (rev 13): the statement, starter and examples, and the solution article, which
     * becomes the Comments pane's one "solution post". Signed in, Pro problems come back too.
     */
    private suspend fun fetchNeetCode(slug: String): ProblemDetail {
        val id = Platform.NEETCODE.siteId(slug)
        val entry = ncCatalog.firstOrNull { it.id == id }
        val p = NeetCodeApi.parseProblem(nc.call(NeetCodeApi.META, NeetCodeApi.metaRequest(id), signedIn = nc.signedIn), entry)
        val saved = save(p.detail)
        val sol = listOfNotNull(p.article?.let {
            Solution(0, "NeetCode solution: ${saved.title}", "NeetCode", 0, it, 0, p.video?.let { v -> "Video: $v" } ?: "Explanation, code and complexity")
        })
        withContext(Dispatchers.IO) { dao.upsertDiscussion(DiscussionRow(Platform.NEETCODE.id, slug, "[]", solutionsJson(sol), System.currentTimeMillis())) }
        return saved
    }

    /** Codeforces (rev 13): the statement page, through the hidden browser. */
    private suspend fun fetchCodeforces(slug: String): ProblemDetail {
        val (contestId, index) = CodeforcesApi.split(slug)
        val st = CodeforcesApi.parseStatement(cf.page(CodeforcesApi.problemUrl(contestId, index)), slug)
        val saved = save(st.detail)
        withContext(Dispatchers.IO) { dao.upsertDiscussion(DiscussionRow(Platform.CODEFORCES.id, slug, "[]", "[]", System.currentTimeMillis())) }
        return saved
    }

    /** Download a statement's images, point it at the local copies, and save it. */
    private suspend fun save(d: ProblemDetail): ProblemDetail {
        var html = d.contentHtml
        if (html != null) {
            val local = mutableMapOf<String, String>()
            for (url in HtmlClean.imageUrls(html)) {
                runCatching {
                    val f = File(imagesDir, sha1(url) + "." + url.substringAfterLast('.', "img").take(4).filter(Char::isLetterOrDigit))
                    if (!f.exists()) f.writeBytes(http.download(url))
                    local[url] = "file://" + f.absolutePath
                }.onFailure { TLog.w("image $url: $it") }
            }
            html = HtmlClean.rewriteImages(html, local)
        }
        val saved = d.copy(contentHtml = html)
        withContext(Dispatchers.IO) {
            dao.upsertDetail(
                DetailRow(pv(saved.slug), saved.slug, saved.questionId, saved.frontendId, saved.title, saved.difficulty, saved.paidOnly,
                    saved.contentHtml, saved.starter, saved.exampleTestcases, saved.metaData,
                    JSONArray(saved.hints).toString(), saved.tags.joinToString(","), saved.similarJson, System.currentTimeMillis())
            )
        }
        return saved
    }

    /** Save the next problems in the list for offline use, skipping ones already saved (PLAN §8.4). */
    suspend fun prefetch(slugs: List<String>) {
        val have = savedSlugs()
        for (s in slugs) {
            if (s in have) continue
            runCatching { fetchDetail(s) }.onFailure {
                TLog.w("prefetch $s: $it")
                return
            }
        }
    }

    private fun DetailRow.toModel() = ProblemDetail(
        slug, questionId, frontendId, title, difficulty, paidOnly, contentHtml, starter, exampleTestcases, metaData,
        JSONArray(hintsJson).let { a -> (0 until a.length()).map { a.getString(it) } },
        tags.split(',').filter(String::isNotEmpty), similarJson,
    )

    // ---- comments and solutions (PLAN §8.2) -----------------------------------------------

    data class Discussion(val comments: List<Comment>, val solutions: List<Solution>, val fetchedAt: Long)

    suspend fun savedDiscussion(slug: String): Discussion? = withContext(Dispatchers.IO) {
        dao.discussion(pv(slug), slug)?.let { Discussion(commentsFrom(it.commentsJson), solutionsFrom(it.solutionsJson), it.fetchedAt) }
    }

    /**
     * Top [threads] comments with their direct replies (for the [withReplies] best threads), and
     * the top [solutionCount] Python solutions with full text. About 20 requests, rate-limited.
     */
    suspend fun fetchDiscussion(slug: String, threads: Int = 30, withReplies: Int = 10, solutionCount: Int = 20, bodies: Int = 3): Discussion {
        if (Platform.of(slug) != Platform.LEETCODE) {
            // NeetCode's article and Codeforces' (empty) entry are saved with the statement.
            savedDiscussion(slug)?.let { return it }
            fetchDetail(slug)
            return savedDiscussion(slug) ?: Discussion(emptyList(), emptyList(), System.currentTimeMillis())
        }
        val ref = "${LeetCodeApi.ORIGIN}/problems/$slug/"
        val topic = LeetCodeApi.parseDiscussionTopic(http.graphql(LeetCodeApi.discussionTopicRequest(slug), ref))
        var comments = if (topic == null) emptyList() else LeetCodeApi.parseComments(http.graphql(LeetCodeApi.commentsRequest(topic, threads), ref))
        comments = comments.mapIndexed { i, c ->
            if (i < withReplies && c.replyCount > 0) {
                c.copy(replies = runCatching { LeetCodeApi.parseReplies(http.graphql(LeetCodeApi.repliesRequest(c.id), ref)) }.getOrDefault(emptyList()))
            } else c
        }
        // Titles for the list; the top few bodies too, so they read offline (rev 10).
        val solutions = LeetCodeApi.parseSolutions(http.graphql(LeetCodeApi.solutionsRequest(slug, solutionCount), ref)).mapIndexed { i, s ->
            if (i >= bodies) s
            else s.copy(body = runCatching { LeetCodeApi.parseSolutionBody(http.graphql(LeetCodeApi.solutionRequest(s.topicId), ref)) }.getOrNull())
        }
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) { dao.upsertDiscussion(DiscussionRow(P, slug, commentsJson(comments), solutionsJson(solutions), now)) }
        return Discussion(comments, solutions, now)
    }

    // ---- solution posts (rev 10) ---------------------------------------------------------

    suspend fun savedPost(topicId: Long): Post? = withContext(Dispatchers.IO) {
        dao.post(P, topicId)?.let { Post(it.topicId, it.body, commentsFrom(it.commentsJson), it.totalComments, it.pagesLoaded, it.fetchedAt) }
    }

    /**
     * Download a post's body (unless [knownBody] is given) and its first page of comments, each
     * with its replies, and save it. With [more], append the next page to the saved post.
     */
    suspend fun fetchPost(slug: String, topicId: Long, knownBody: String?, more: Post? = null): Post {
        val ref = "${LeetCodeApi.ORIGIN}/problems/$slug/solutions/$topicId/"
        val body = more?.body ?: knownBody
            ?: LeetCodeApi.parseSolutionBody(http.graphql(LeetCodeApi.solutionRequest(topicId), ref)) ?: ""
        val page = (more?.pagesLoaded ?: 0) + 1
        val raw = http.graphql(LeetCodeApi.commentsRequest(topicId, POST_PAGE, page), ref)
        val total = LeetCodeApi.parseCommentTotal(raw)
        val fresh = LeetCodeApi.parseComments(raw).map { c ->
            if (c.replyCount == 0) c
            else c.copy(replies = runCatching { LeetCodeApi.parseReplies(http.graphql(LeetCodeApi.repliesRequest(c.id), ref)) }.getOrDefault(emptyList()))
        }
        val post = Post(topicId, body, (more?.comments ?: emptyList()) + fresh, total, page, System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            dao.upsertPost(PostRow(P, topicId, slug, post.body, commentsJson(post.comments), total, page, post.fetchedAt))
        }
        return post
    }

    // ---- test cases, Run and Submit (PLAN §6.2, §8.3, M3) -----------------------------------

    /**
     * The statement's examples, then saved cases, without duplicates. An example's expected
     * output is LeetCode's (once a Run returned it) or else the statement's "Output:" (PLAN §8.3).
     */
    suspend fun testCases(d: ProblemDetail): List<TestCase> {
        val stdin = d.platform == Platform.CODEFORCES
        val examples = if (stdin) CodeforcesApi.sampleInputs(d.exampleTestcases) else Judge.splitCases(d.exampleTestcases, Judge.linesPerCase(d.metaData))
        val fromStatement = if (stdin) CodeforcesApi.sampleOutputs(d.metaData) else dev.titan.code.runner.LocalJudge.statementOutputs(d.contentHtml)
        val saved = withContext(Dispatchers.IO) { dao.testCases(pv(d.slug), d.slug) }
        val byInput = saved.associateBy { it.input }
        val ex = examples.mapIndexed { i, input ->
            val s = byInput[input]
            // Codeforces' sample outputs are the judge's own, so they count as verified.
            TestCase(null, input, s?.expected ?: fromStatement.getOrNull(i), "example", s?.verified == true || stdin)
        }
        return ex + saved.filter { it.input !in examples }.map { TestCase(it.id, it.input, it.expected, it.source, it.verified) }
    }

    /**
     * A test you wrote (PLAN §8.3), or one a stress test found (rev 24). [expected] is yours or
     * the reference's: unverified until a Run on the site returns the judge's.
     */
    suspend fun addCustomCase(slug: String, input: String, expected: String? = null): Boolean = withContext(Dispatchers.IO) {
        dao.insertTestCase(TestCaseRow(provider = pv(slug), slug = slug, input = input, expected = expected, source = "custom",
            verified = false, createdAt = System.currentTimeMillis())) > 0
    }

    /**
     * Known-good solutions for stress tests (rev 24), best first: NeetCode's article for this
     * problem (one per approach), then saved LeetCode solution posts with Python code. A LeetCode
     * problem that NeetCode also has uses NeetCode's article, downloaded now if [online].
     */
    suspend fun references(d: ProblemDetail, online: Boolean): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val ncSlug = when (d.platform) {
            Platform.NEETCODE -> d.slug
            Platform.LEETCODE -> ncCatalog.firstOrNull { (it.lcNumber == d.frontendId && d.frontendId > 0) || it.lcSlug == d.slug }
                ?.takeUnless { it.pro }?.let { Platform.NEETCODE.key(it.id) }
            Platform.CODEFORCES -> null
        }
        if (ncSlug != null) {
            var disc = savedDiscussion(ncSlug)
            if (disc == null && online && d.platform == Platform.LEETCODE) {
                runCatching { fetchDetail(ncSlug) }.onFailure { TLog.w("reference $ncSlug: $it") }
                disc = savedDiscussion(ncSlug)
            }
            disc?.solutions?.firstOrNull()?.body?.let { md -> out += codeBlocks(md).map { (h, c) -> "NeetCode: ${h ?: "solution"}" to c } }
        }
        if (d.platform == Platform.LEETCODE) {
            savedDiscussion(d.slug)?.solutions?.sortedByDescending { it.votes }?.forEach { s ->
                s.body?.let { md -> codeBlocks(md).forEach { (_, c) -> out += "LeetCode post: ${s.title.take(60)} (${s.author})" to c } }
            }
        }
        return out.filter { (_, c) -> c.contains("class Solution") }.distinctBy { it.second }
    }

    suspend fun deleteTestCase(id: Long) = withContext(Dispatchers.IO) { dao.deleteTestCase(id) }

    /**
     * Save a case from the test-case editor (rev 24), replacing [replacing] (one of yours) if
     * given. An existing case with the same input keeps its judge-verified expected output.
     */
    suspend fun saveCase(slug: String, replacing: Long?, input: String, expected: String?) = withContext(Dispatchers.IO) {
        if (replacing != null) dao.deleteTestCase(replacing)
        val added = dao.insertTestCase(TestCaseRow(provider = pv(slug), slug = slug, input = input, expected = expected, source = "custom",
            verified = false, createdAt = System.currentTimeMillis())) > 0
        if (!added) dao.setUnverifiedExpected(pv(slug), slug, input, expected)
    }

    /** What's happening, for the Results pane while it waits. */
    fun interface Progress { fun update(text: String) }

    /**
     * Run [code] on LeetCode against [cases] (PLAN §6.2). Every expected output that comes back
     * is saved, so those cases are verified for offline runs later (PLAN §8.3).
     */
    suspend fun runRemote(d: ProblemDetail, code: String, cases: List<String>, progress: Progress): Judge.RunResult {
        if (d.platform == Platform.NEETCODE) return runNeetCode(d, code, cases, progress)
        val ref = "${LeetCodeApi.ORIGIN}/problems/${d.slug}/"
        progress.update("Sending to LeetCode…")
        val id = Judge.parseRunStart(http.post(Judge.runPath(d.slug), Judge.runBody(d.questionId, code, cases.joinToString("\n")), ref))
        return saveExpected(d, Judge.parseRun(poll(id, ref, progress), cases))
    }

    /** NeetCode runs every case in one call and answers when done (rev 13). */
    private suspend fun runNeetCode(d: ProblemDetail, code: String, cases: List<String>, progress: Progress): Judge.RunResult {
        progress.update("Running on NeetCode…")
        val tests = cases.map { NeetCodeApi.toNeetCode(it, d.metaData) }
        val body = nc.call(NeetCodeApi.RUN, NeetCodeApi.runRequest(d.questionId, code, tests), signedIn = true)
        return saveExpected(d, NeetCodeApi.parseRun(body, cases))
    }

    /** Every expected output a judge returned is saved, so those cases are verified for offline runs (PLAN §8.3). */
    private suspend fun saveExpected(d: ProblemDetail, result: Judge.RunResult): Judge.RunResult {
        withContext(Dispatchers.IO) {
            result.cases.forEach { c ->
                val exp = c.expected ?: return@forEach
                dao.insertTestCase(TestCaseRow(provider = pv(d.slug), slug = d.slug, input = c.input, expected = exp, source = "example", verified = true, createdAt = System.currentTimeMillis()))
                dao.verifyTestCase(pv(d.slug), d.slug, c.input, exp)
            }
        }
        return result
    }

    /**
     * Submit [code] (PLAN §6.2). A failing case is saved as a test case (with LeetCode's expected
     * output for Wrong Answer); every submission goes in the history; Accepted marks the problem solved.
     */
    suspend fun submit(d: ProblemDetail, code: String, progress: Progress, elapsedMs: Long? = null): Judge.SubmitResult {
        val id: String
        val r: Judge.SubmitResult
        if (d.platform == Platform.NEETCODE) {
            progress.update("Submitting to NeetCode… (it runs every test before answering)")
            r = NeetCodeApi.parseSubmit(nc.call(NeetCodeApi.SUBMIT, NeetCodeApi.submitRequest(d.questionId, code), signedIn = true), d.metaData)
            id = ""
        } else {
            val ref = "${LeetCodeApi.ORIGIN}/problems/${d.slug}/"
            progress.update("Submitting…")
            id = Judge.parseSubmitStart(http.post(Judge.submitPath(d.slug), Judge.submitBody(d.questionId, code), ref))
            r = Judge.parseSubmit(poll(id, ref, progress))
        }
        withContext(Dispatchers.IO) {
            val pv = pv(d.slug)
            dao.insertSubmission(SubmissionRow(provider = pv, slug = d.slug, submissionId = id, status = r.statusMsg, runtime = r.runtime,
                memory = r.memory, passed = "${r.totalCorrect}/${r.totalTestcases}", code = code, at = System.currentTimeMillis(), elapsedMs = elapsedMs))
            val input = r.lastInput
            if (!r.accepted && input != null) {
                dao.insertTestCase(TestCaseRow(provider = pv, slug = d.slug, input = input, expected = r.expected, source = "submission",
                    verified = r.expected != null, createdAt = System.currentTimeMillis()))
                r.expected?.let { dao.verifyTestCase(pv, d.slug, input, it) }
            }
            markStatus(d.slug, r.accepted)
        }
        return r
    }

    /** Solved marks only move forward: an accepted problem stays accepted after a later failure. */
    private suspend fun markStatus(slug: String, accepted: Boolean) {
        val now = dao.problems(pv(slug)).firstOrNull { it.slug == slug }?.status
        if (accepted || now != "ac") dao.setStatus(pv(slug), slug, if (accepted) "ac" else "notac")
    }

    /**
     * Codeforces (rev 13): send the form from the hidden browser, then follow the verdict through
     * the public API (`user.status`) every 2 s for up to 3 minutes.
     */
    suspend fun submitCodeforces(d: ProblemDetail, code: String, handle: String, language: CodeforcesApi.Language, progress: Progress,
                                 elapsedMs: Long? = null): CodeforcesApi.Verdict {
        val (contestId, index) = CodeforcesApi.split(d.slug)
        progress.update("Sending to Codeforces (${language.label})…")
        val since = System.currentTimeMillis() / 1000 - 30
        cf.submit(contestId, index, code, language)
        var last: CodeforcesApi.Verdict? = null
        repeat(CF_POLL_LIMIT) { i ->
            delay(if (i == 0) 1500 else 2000)
            val v = runCatching { CodeforcesApi.parseStatus(http.fetchText(CodeforcesApi.statusUrl(handle)), contestId, index, since) }
                .onFailure { TLog.w("cf status: $it") }.getOrNull()
            if (v != null) {
                last = v
                if (v.done) {
                    withContext(Dispatchers.IO) {
                        dao.insertSubmission(SubmissionRow(provider = pv(d.slug), slug = d.slug, submissionId = v.id.toString(), status = v.label,
                            runtime = v.time, memory = v.memory, passed = "${v.passedTests} tests", code = code, at = System.currentTimeMillis(), elapsedMs = elapsedMs))
                        markStatus(d.slug, v.accepted)
                    }
                    return v
                }
                progress.update("${v.label}… (${i * 2 + 2} s)")
            } else {
                progress.update("In Codeforces' queue… (${i * 2 + 2} s)")
            }
        }
        throw Judge.JudgeError(last?.let { "Still ${it.label.lowercase()} after ${CF_POLL_LIMIT * 2} s; check Codeforces later" }
            ?: "Codeforces took the form, but the submission didn't show up in your status. Check codeforces.com.")
    }

    suspend fun submissions(slug: String): List<SubmissionRecord> = withContext(Dispatchers.IO) {
        dao.submissions(pv(slug), slug).map { SubmissionRecord(it.at, it.status, it.runtime, it.memory, it.passed, it.elapsedMs) }
    }

    // ---- practice (rev 24) -------------------------------------------------------------------

    suspend fun timer(slug: String): TimerRow? = withContext(Dispatchers.IO) { dao.timer(pv(slug), slug) }

    /** Save [row], or forget the problem's timer when it's null (reset). */
    suspend fun saveTimer(slug: String, row: TimerRow?) = withContext(Dispatchers.IO) {
        if (row == null) dao.deleteTimer(pv(slug), slug) else dao.putTimer(row)
    }

    fun timerProvider(slug: String) = pv(slug)


    /** Poll `check` every second, up to [POLL_LIMIT] tries. */
    private suspend fun poll(id: String, ref: String, progress: Progress): String {
        repeat(POLL_LIMIT) { i ->
            delay(if (i == 0) 600 else 1000)
            val body = http.get(Judge.checkPath(id), ref)
            if (Judge.isDone(body)) return body
            progress.update(if (body.contains("STARTED")) "Running… (${i + 1} s)" else "Waiting in LeetCode's queue… (${i + 1} s)")
        }
        throw Judge.JudgeError("LeetCode didn't finish within ${POLL_LIMIT} s")
    }

    // ---- drafts (PLAN §8.2) ---------------------------------------------------------------

    data class Draft(val text: String, val line: Int, val column: Int)

    fun loadDraft(slug: String): Draft? =
        draftWriter.submit<DraftRow?> { dao.draft(pv(slug), slug) }.get(5, TimeUnit.SECONDS)?.let { Draft(it.text, it.line, it.col) }

    /** Queue a draft write. [onError] runs on the writer thread if it fails. */
    fun saveDraft(slug: String, draft: Draft, onError: (Exception) -> Unit) {
        draftWriter.execute {
            try {
                dao.upsertDraft(DraftRow(pv(slug), slug, draft.text, draft.line, draft.column, System.currentTimeMillis()))
            } catch (e: Exception) {
                TLog.w("draft save failed: $e")
                onError(e)
            }
        }
    }

    /** Keep a copy in the version history (reset, periodic). Skipped if identical to the newest one. */
    fun addVersion(slug: String, text: String, reason: String) {
        draftWriter.execute {
            runCatching {
                if (dao.versions(pv(slug), slug).firstOrNull()?.text == text) return@runCatching
                dao.addVersion(DraftVersionRow(provider = pv(slug), slug = slug, text = text, savedAt = System.currentTimeMillis(), reason = reason), VERSIONS_KEPT)
            }.onFailure { TLog.w("version save failed: $it") }
        }
    }

    fun versions(slug: String): List<DraftVersionRow> = draftWriter.submit<List<DraftVersionRow>> { dao.versions(pv(slug), slug) }.get(5, TimeUnit.SECONDS)

    /** Block until queued draft writes finish (onPause). */
    fun flushDrafts() {
        runCatching { draftWriter.submit {}.get(3, TimeUnit.SECONDS) }
    }

    // ---- offline packs (PLAN §8.4, M4) ------------------------------------------------------

    data class Pack(val id: Long, val title: String, val slugs: List<String>, val saved: Int, val source: String = "") {
        /** "Everything on …" (rev 25): removing it keeps what it saved. */
        val everything get() = source.startsWith(ALL_PREFIX)
    }

    suspend fun packs(): List<Pack> = withContext(Dispatchers.IO) {
        val saved = Platform.entries.flatMap { dao.savedSlugs(it.id) }.toSet()
        val disc = Platform.entries.flatMap { dao.discussionSlugs(it.id) }.toSet()
        dao.packs().map { r ->
            val slugs = slugsOf(r)
            Pack(r.id, r.title, slugs, slugs.count { it in saved && it in disc }, r.source)
        }
    }

    private fun slugsOf(r: PackRow) = JSONArray(r.slugsJson).let { a -> (0 until a.length()).map { a.getString(it) } }

    /** A study plan as a pack: its problem list comes from LeetCode. */
    suspend fun createPlanPack(planSlug: String): Long {
        val (name, slugs) = LeetCodeApi.parseStudyPlan(http.graphql(LeetCodeApi.studyPlanRequest(planSlug), "${LeetCodeApi.ORIGIN}/studyplan/$planSlug/"))
        return createPack(name, "plan:$planSlug", slugs)
    }

    suspend fun createPack(title: String, source: String, slugs: List<String>): Long = withContext(Dispatchers.IO) {
        dao.insertPack(PackRow(title = title, source = source, slugsJson = JSONArray(slugs).toString(), createdAt = System.currentTimeMillis()))
    }

    /** Problems on [platform] that "Everything" would save: not Premium (LeetCode) or Pro (NeetCode). */
    suspend fun freeProblemCount(platform: Platform): Int = withContext(Dispatchers.IO) {
        if (platform == Platform.NEETCODE) seedNeetCode()
        dao.freeProblemCount(platform.id)
    }

    /**
     * "Everything on [platform]" (rev 25): bring the list up to date, then one pack holding every
     * problem that isn't Premium or Pro, in the list's order. Pressed again, the same pack picks
     * up problems added since. If the list can't be refreshed, the saved one is used. Returns
     * the pack and its size.
     */
    suspend fun createEverythingPack(platform: Platform, progress: (Int, Int) -> Unit = { _, _ -> }): Pair<Long, Int> {
        runCatching { refreshList(platform, progress) }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            TLog.w("everything ${platform.id}: list refresh failed: $it")
            if (withContext(Dispatchers.IO) { dao.freeProblemCount(platform.id) } == 0) throw it
        }
        val slugs = problems(platform).filter { !it.paidOnly }.map { it.slug }
        val json = JSONArray(slugs).toString()
        val source = ALL_PREFIX + platform.id
        return withContext(Dispatchers.IO) {
            val existing = dao.packs().firstOrNull { it.source == source }
            val id = if (existing != null) existing.id.also { dao.setPackSlugs(it, json) }
            else dao.insertPack(PackRow(title = "Everything on ${platform.title}", source = source, slugsJson = json, createdAt = System.currentTimeMillis()))
            id to slugs.size
        }
    }

    /** A pack download's progress: [phase] is "statements" or "discussions". */
    data class PackProgress(val phase: String, val done: Int, val total: Int, val failed: Int)

    /** How a pack download went: problems newly saved, and ones that couldn't be. */
    data class PackOutcome(val saved: Int, val failed: Int)

    /**
     * Save every problem in the pack in two passes: every statement first (so as many problems
     * as possible open offline early), then discussions and solution lists (lighter than an
     * interactive open: 20 threads, replies for 3, top 3 solution bodies; NeetCode's article
     * and Codeforces' empty entry come with the statement). Already-saved problems are skipped,
     * so an interrupted download resumes where it stopped.
     *
     * Rev 25, for downloads of thousands: one problem that won't load (gone, unparseable, a
     * server error three times) is skipped and counted, not fatal. "Too many requests" waits
     * and retries the same problem, longer each time. A browser check, a lost session, or three
     * problems in a row failing on the network ends the run with that exception, and the worker
     * resumes later.
     */
    suspend fun downloadPack(id: Long, progress: suspend (PackProgress) -> Unit = {}): PackOutcome {
        val pack = withContext(Dispatchers.IO) { dao.pack(id) } ?: return PackOutcome(0, 0)
        val slugs = slugsOf(pack)
        val failed = mutableSetOf<String>()
        var saved = 0
        var lastReport = 0L
        var netFailsInARow = 0
        suspend fun report(phase: String, done: Int, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastReport < 1000) return
            lastReport = now
            progress(PackProgress(phase, done, slugs.size, failed.size))
        }
        suspend fun attempt(slug: String, what: suspend () -> Boolean) {
            when (patiently(slug, what = what)) {
                true -> netFailsInARow = 0
                false -> { failed += slug; netFailsInARow = 0 }
                null -> {
                    failed += slug
                    if (++netFailsInARow >= 3) throw IOException("three problems in a row failed on the network")
                }
            }
            delay(PACE_MS[Platform.of(slug)] ?: 0)
        }

        val haveDetail = withContext(Dispatchers.IO) { slugs.map(::pv).distinct().flatMap { dao.savedSlugs(it) }.toSet() }
        slugs.forEachIndexed { i, slug ->
            if (slug !in haveDetail) attempt(slug) { fetchDetail(slug) != null }.also { if (slug !in failed) saved++ }
            report("statements", i + 1, force = i == slugs.size - 1)
        }
        val haveDisc = withContext(Dispatchers.IO) { slugs.map(::pv).distinct().flatMap { dao.discussionSlugs(it) }.toSet() }
        slugs.forEachIndexed { i, slug ->
            if (slug !in haveDisc && slug !in failed) {
                attempt(slug) { fetchDiscussion(slug, threads = 20, withReplies = 3, solutionCount = 20, bodies = 3); true }
            }
            report("discussions", i + 1, force = i == slugs.size - 1)
        }
        if (failed.isNotEmpty()) TLog.w("pack $id: ${failed.size} problems not saved, e.g. ${failed.take(5)}")
        return PackOutcome(saved, failed.size)
    }

    /**
     * Remove a pack and its saved statements and discussions. Drafts and history stay (PLAN
     * §8.4). An "Everything" pack only stops: what it saved stays (⚙ → Clear removes it).
     */
    suspend fun deletePack(id: Long) = withContext(Dispatchers.IO) {
        val pack = dao.pack(id) ?: return@withContext
        if (pack.source.startsWith(ALL_PREFIX)) { dao.deletePack(id); return@withContext }
        val slugs = slugsOf(pack)
        val keep = dao.packs().filter { it.id != id }.flatMap(::slugsOf).toSet()
        val drop = slugs.filter { it !in keep }
        drop.groupBy { pv(it) }.forEach { (provider, keys) ->
            keys.chunked(500).forEach {
                dao.deleteDetails(provider, it)
                dao.deleteDiscussions(provider, it)
            }
        }
        dao.deletePack(id)
    }

    // ---- storage (Settings, PLAN §8.4) ----------------------------------------------------

    data class Storage(val problems: Int, val savedStatements: Int, val savedDiscussions: Int, val bytes: Long)

    suspend fun storage(): Storage = withContext(Dispatchers.IO) {
        val dbBytes = app.getDatabasePath("titan-code.db").length()
        val img = imagesDir.listFiles()?.sumOf { it.length() } ?: 0
        Storage(Platform.entries.sumOf { dao.problems(it.id).size }, dao.savedDetailCount(), dao.discussionCount(), dbBytes + img)
    }

    /** Clears saved statements, discussions and images. Never drafts or history (PLAN §8.4). */
    suspend fun clearSaved() = withContext(Dispatchers.IO) {
        dao.clearDetails()
        dao.clearDiscussions()
        dao.clearPosts()
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    // ---- JSON for saved discussions -------------------------------------------------------

    private fun commentsJson(cs: List<Comment>): String = JSONArray(cs.map(::commentJson)).toString()

    private fun commentJson(c: Comment): JSONObject = JSONObject()
        .put("id", c.id).put("author", c.author).put("votes", c.votes).put("created", c.createdSec)
        .put("body", c.body).put("replyCount", c.replyCount).put("replies", JSONArray(c.replies.map(::commentJson)))

    private fun commentsFrom(json: String): List<Comment> {
        val a = JSONArray(json)
        return (0 until a.length()).map { commentFrom(a.getJSONObject(it)) }
    }

    private fun commentFrom(o: JSONObject): Comment = Comment(
        o.getLong("id"), o.getString("author"), o.getInt("votes"), o.getLong("created"), o.getString("body"),
        o.getInt("replyCount"), o.optJSONArray("replies")?.let { a -> (0 until a.length()).map { commentFrom(a.getJSONObject(it)) } } ?: emptyList(),
    )

    private fun solutionsJson(ss: List<Solution>): String = JSONArray(ss.map {
        JSONObject().put("topicId", it.topicId).put("title", it.title).put("author", it.author).put("votes", it.votes)
            .put("body", it.body ?: JSONObject.NULL).put("comments", it.commentCount).put("summary", it.summary)
    }).toString()

    private fun solutionsFrom(json: String): List<Solution> {
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Solution(o.getLong("topicId"), o.getString("title"), o.getString("author"), o.getInt("votes"),
                if (o.isNull("body")) null else o.getString("body"), o.optInt("comments"), o.optString("summary"))
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun pv(key: String) = Platform.of(key).id

    companion object {
        /** Python code blocks in Markdown, each with the nearest heading above it ("1. Depth First Search"). */
        fun codeBlocks(md: String): List<Pair<String?, String>> {
            val out = mutableListOf<Pair<String?, String>>()
            var heading: String? = null
            val lines = md.replace("\r\n", "\n").split("\n")
            var i = 0
            while (i < lines.size) {
                val l = lines[i]
                if (l.startsWith("## ") || l.startsWith("### ") && heading == null) heading = l.trimStart('#', ' ').trim()
                if (l.trimStart().startsWith("```")) {
                    val lang = l.trim().removePrefix("```").trim().lowercase()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                    if (lang.isEmpty() || lang.startsWith("py")) out += heading to code.toString()
                }
                i++
            }
            return out
        }

        const val P = "leetcode"
        /**
         * Run [what] for one problem: true when it worked, false when this problem can't be saved
         * (skip it), null when it failed on the network three times. Rate limits wait (30 s, 1, 2,
         * 4, 5, 5 min) and retry; browser checks and lost sessions are thrown, since every later
         * problem would fail the same way.
         */
        internal suspend fun patiently(slug: String, sleep: suspend (Long) -> Unit = { delay(it) }, what: suspend () -> Boolean): Boolean? {
            var limited = 0
            var errors = 0
            while (true) {
                try {
                    return what()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: RateLimitedException) {
                    if (++limited > 6) throw e
                    TLog.w("pack: rate limited on $slug; waiting")
                    sleep(minOf(30_000L shl (limited - 1), 300_000L))
                } catch (e: ChallengeException) {
                    throw e
                } catch (e: SignedOutException) {
                    throw e
                } catch (e: IOException) {
                    if (++errors >= 3) { TLog.w("pack $slug: $e"); return null }
                    sleep(5_000L * errors)
                } catch (e: Exception) {
                    TLog.w("pack $slug: skipped: $e")
                    return false
                }
            }
        }

        /** Pack source for "Everything on …" (rev 25). */
        const val ALL_PREFIX = "all:"
        /** Pause after each problem a pack download fetches: NeetCode has no gate of its own, and Codeforces pages are heavy. */
        private val PACE_MS = mapOf(Platform.NEETCODE to 400L, Platform.CODEFORCES to 1500L)
        const val CF_POLL_LIMIT = 90
        private const val META_NC_SEEDED = "neetcode.seeded"
        /** Bump with a new `assets/neetcode/catalog.json` so the list is re-seeded. */
        private const val NC_SNAPSHOT = "2026-11-02"
        const val VERSIONS_KEPT = 20
        /** Comments per page on a solution post; each page also fetches the replies. */
        const val POST_PAGE = 20
        const val POLL_LIMIT = 60
        private const val META_SEEDED = "seeded"
        private const val META_LIST_AT = "list.fetchedAt"
        private const val META_DAILY = "daily"
    }
}
