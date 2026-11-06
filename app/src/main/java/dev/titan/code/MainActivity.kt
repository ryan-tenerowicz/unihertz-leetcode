package dev.titan.code

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.titan.code.data.AuthStore
import dev.titan.code.data.ChallengeException
import dev.titan.code.data.LeetCodeHttp
import dev.titan.code.data.Post
import dev.titan.code.data.ProblemDetail
import dev.titan.code.data.RateLimitedException
import dev.titan.code.data.SignedOutException
import dev.titan.code.data.SubmissionRecord
import dev.titan.code.data.TestCase
import dev.titan.code.data.WebFetch
import dev.titan.code.data.Platform
import dev.titan.code.data.ProblemFilter
import dev.titan.code.data.codeforces.CodeforcesApi
import dev.titan.code.data.codeforces.CodeforcesWeb
import dev.titan.code.data.neetcode.NeetCodeApi
import dev.titan.code.data.neetcode.NeetCodeHttp
import dev.titan.code.ui.PlatformLoginScreen
import dev.titan.code.data.Sync
import dev.titan.code.ui.PackView
import dev.titan.code.ui.ReadingScale
import dev.titan.code.ui.EverythingView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.titan.code.data.leetcode.Judge
import dev.titan.code.data.Repository
import dev.titan.code.data.Solution
import dev.titan.code.data.db.DraftVersionRow
import dev.titan.code.data.leetcode.LeetCodeApi
import dev.titan.code.editor.Clip
import dev.titan.code.editor.Completer
import dev.titan.code.editor.CompletionSite
import dev.titan.code.ui.CompletionStrip
import dev.titan.code.editor.EditorActions
import dev.titan.code.editor.TitanEditor
import dev.titan.code.input.GestureEngine
import dev.titan.code.input.InputRouter
import dev.titan.code.input.Leader
import dev.titan.code.input.SurfaceScroller
import dev.titan.code.samples.Sample
import dev.titan.code.samples.SampleLibrary
import dev.titan.code.samples.Starter
import dev.titan.code.ui.CheatSheet
import dev.titan.code.ui.Colors
import dev.titan.code.ui.KeySinkView
import dev.titan.code.ui.LoginScreen
import dev.titan.code.ui.Pages
import dev.titan.code.ui.Pane
import dev.titan.code.ui.PaletteStrip
import dev.titan.code.ui.ProblemList
import dev.titan.code.ui.ProblemListModel
import dev.titan.code.ui.ProblemView
import dev.titan.code.ui.SettingsActions
import dev.titan.code.ui.SettingsScreen
import dev.titan.code.ui.SettingsState
import dev.titan.code.ui.SetupScreen
import dev.titan.code.ui.SolutionList
import dev.titan.code.ui.Outcome
import dev.titan.code.ui.ResultsPane
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.subscribeAlways
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The single activity (PLAN §3): four full-screen panes with the tab bar in the band beside the
 * camera (PLAN §4.1), a title line, the bottom status line, overlays (symbol palette, cheat
 * sheet, Settings, setup checklist, sign-in), the red button, Back, and the input interception
 * points.
 *
 * M2: problems come from the offline-first [Repository] (PLAN §8). The Problem pane has two
 * views, the list and the statement; `sym+P` switches to the list.
 */
class MainActivity : ComponentActivity(), InputRouter.Shell {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var library: SampleLibrary
    private lateinit var auth: AuthStore
    private lateinit var repo: Repository
    private lateinit var editor: TitanEditor
    private lateinit var problemView: ProblemView
    private lateinit var commentsView: ProblemView
    private lateinit var keySink: KeySinkView
    private lateinit var router: InputRouter
    private lateinit var gestures: GestureEngine
    private lateinit var haptics: dev.titan.code.input.SwipeHaptics
    private lateinit var problemScroller: SurfaceScroller
    private lateinit var listScroller: SurfaceScroller
    private lateinit var commentsScroller: SurfaceScroller

    private var pane by mutableStateOf(Pane.CODE)
    /** Where `sym+T` returns to when it closes Results. */
    private var paneBeforeResults = Pane.CODE
    private var problem by mutableStateOf<ProblemDetail?>(null)
    private var listMode by mutableStateOf(false)
    private val list = ProblemListModel()
    private var online by mutableStateOf(true)
    private var username by mutableStateOf<String?>(null)

    /** Shown on the title line only when something needs attention. Saving is otherwise silent (rev 9). */
    private var draftNote by mutableStateOf<String?>(null)
    private var symHeld by mutableStateOf(false)
    private var position by mutableStateOf("Ln 1 Col 1")
    private var message by mutableStateOf<String?>(null)
    private var messageSeq by mutableIntStateOf(0)
    private var paletteOpen by mutableStateOf(false)
    private var cheatOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var setupOpen by mutableStateOf(false)
    /** The sign-in page showing, if any (rev 13: one per platform). */
    private var loginFor by mutableStateOf<Platform?>(null)
    private val loginOpen get() = loginFor != null
    private var settingsState by mutableStateOf<SettingsState?>(null)
    /** Camera cutout in px (top-left square on the Titan 2), read at runtime (PLAN §4.1). */
    private var cutoutPx by mutableStateOf(Rect())

    private var discussion by mutableStateOf<Repository.Discussion?>(null)
    private var discussionStatus by mutableStateOf<String?>(null)
    /** Comments pane (owner, rev 10): Solutions first and by default, then Discussion. */
    private var commentsTab by mutableStateOf(CommentsTab.SOLUTIONS)
    private var openSolution by mutableStateOf<Solution?>(null)
    private var openPost: Post? = null
    private var postStatus: String? = null
    private var solutionHighlight by mutableIntStateOf(0)
    private var postJob: Job? = null

    private enum class CommentsTab { SOLUTIONS, DISCUSSION }

    // Rev 13: NeetCode and Codeforces.
    private lateinit var nc: NeetCodeHttp
    private lateinit var cfWeb: CodeforcesWeb
    private var ncAccount by mutableStateOf<String?>(null)
    private var cfHandle by mutableStateOf<String?>(null)
    /** LeetCode's curated lists (NeetCode 150 / Blind 75 over LeetCode slugs, rev 12). */
    private var lcCollections = emptyList<dev.titan.code.data.ProblemCollection>()

    // M3: Run and Submit on LeetCode (PLAN §6.2), shown in the Results pane.
    private lateinit var webFetch: WebFetch
    private lateinit var runner: dev.titan.code.runner.RunnerClient
    private lateinit var completer: dev.titan.code.runner.CompletionClient
    private lateinit var resultsScroller: SurfaceScroller
    private val resultsScroll = ScrollState(0)
    /** The last outcome per problem, for this session. */
    private val outcomes = mutableMapOf<String, Outcome>()
    private var outcome by mutableStateOf<Outcome?>(null)
    private var testCases by mutableStateOf<List<TestCase>>(emptyList())
    private var history by mutableStateOf<List<SubmissionRecord>>(emptyList())
    private var judgeJob: Job? = null
    /** `sym+S` / Submit armed: a second press before this time submits (PLAN §5.4). */
    private var submitArmedUntil = 0L
    private var submitConfirm by mutableStateOf(false)
    /** Pack id → "downloading 12/150", "waiting for Wi-Fi"… from WorkManager. */
    private var packWork = mapOf<Long, String>()
    /** Rev 25: preparing an "Everything on …" pack (refreshing the list first). */
    private var everythingJob: Job? = null

    /** `sym+N` armed: a second press before this time resets (PLAN §5.4). */
    private var resetArmedUntil = 0L
    /** True while the Code pane holds something other than the draft (a test case being edited): no autosave. */
    private var testBuffer = false

    /** Practice timer (rev 24), shown in the status line as "⏱ 12:34". */
    private val timer = dev.titan.code.data.PracticeTimer { SystemClock.elapsedRealtime() }
    private var timerText by mutableStateOf<String?>(null)
    private var timerSavedAt = 0L

    private fun showTimer() {
        timerText = if (prefs.practiceTimer && timer.started) "⏱ " + dev.titan.code.data.PracticeTimer.format(timer.elapsedMs()) +
            (if (timer.done) " ✓" else "") else null
    }

    /** Write the timer down (pause, problem switch, every 30 s while it runs, submit). */
    private fun persistTimer() {
        val slug = timer.slug ?: return
        val row = timer.row(repo.timerProvider(slug))
        timerSavedAt = SystemClock.elapsedRealtime()
        lifecycleScope.launch { repo.saveTimer(slug, row) }
    }


    /** The test case being edited in the Code pane (rev 24); the draft is saved and put back after. */
    private data class CaseEdit(val slug: String, val replacing: Long?)
    private var caseEdit by mutableStateOf<CaseEdit?>(null)

    private fun busyMessage() = "Finish the test case first: sym+S saves, Back cancels"
    /** True while we replace the buffer ourselves (loading a draft): not a user edit. */
    private var loading = false
    private var dirty = false
    private var lastVersionAt = 0L
    private var listAccumulator = 0f
    private var openJob: Job? = null
    private var discussionJob: Job? = null
    private var prefetchJob: Job? = null
    private var listJob: Job? = null
    private val saveRunnable = Runnable { saveDraft() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds only: lets chrome://inspect look into the WebViews (site markup changes).
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = Prefs(this)
        library = SampleLibrary(assets)
        auth = AuthStore(this)
        username = auth.username
        webFetch = WebFetch(this)
        Sync.scheduleWeeklyRefresh(this, prefs.packsOnMobile)
        watchPackWork()
        // Bind the local runner now so Python is warm before the first run (PLAN §7).
        runner = dev.titan.code.runner.RunnerClient(this).also { it.bind() }
        // And the completer (rev 38): Jedi loads and warms up in its own process.
        completer = dev.titan.code.runner.CompletionClient(this).also { it.bind() }
        val ua = WebSettings.getDefaultUserAgent(this)
        val http = LeetCodeHttp(auth::cookies, ua).also { it.browser = webFetch }
        nc = NeetCodeHttp(auth, ua)
        cfWeb = CodeforcesWeb(this)
        ncAccount = auth.account(Platform.NEETCODE.id)?.takeIf { nc.signedIn }
        cfHandle = auth.account(Platform.CODEFORCES.id)
        repo = Repository(this, http, nc, cfWeb)
        editor = TitanEditor(this).apply {
            setWordwrap(prefs.softWrap, true)
            setTextSize(prefs.fontSize.toFloat())
            actions.indentWidth = prefs.indentWidth
        }
        problemView = ProblemView(this)
        commentsView = ProblemView(this)
        applyReadingZoom()
        keySink = KeySinkView(this) { e ->
            when {
                blockingOverlay() -> router.onShellKey(e)
                pane == Pane.CODE -> {
                    // Focus hasn't moved to the editor yet (it was hidden a moment ago).
                    editor.requestFocus()
                    router.onEditorKey(e)
                }
                else -> router.onShellKey(e)
            }
        }
        router = InputRouter(editor.actions, this, { delay, task ->
            val r = Runnable(task)
            handler.postDelayed(r, delay)
            ({ handler.removeCallbacks(r) })
        })
        editor.keyHandler = { e -> if (blockingOverlay()) router.onShellKey(e) else router.onEditorKey(e) }
        editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            updatePosition()
            // Moving away from the word closes the suggestions.
            if (completions.isNotEmpty() && editor.actions.completionSite()?.anchor != completionSite?.anchor) closeCompletions()
        }
        editor.subscribeAlways<ContentChangeEvent> { e ->
            if (e.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && !loading) onBufferChanged()
        }
        gestures = GestureEngine(EditorGestureSink(), gestureConfig())
        haptics = dev.titan.code.input.SwipeHaptics(this).also { it.level = dev.titan.code.input.SwipeHaptics.Level.byName(prefs.swipeHaptics) }
        problemScroller = SurfaceScroller({ problemView.scrollByClamped(it) })
        commentsScroller = SurfaceScroller({ commentsView.scrollByClamped(it) })
        listScroller = SurfaceScroller(::onListSwipe)
        resultsScroller = SurfaceScroller({ dy -> lifecycleScope.launch { resultsScroll.scrollBy(dy.toFloat()) } })
        commentsView.onAppLink = {
            when {
                it == Pages.MORE_LINK -> loadMoreComments()
                it.startsWith(Pages.OPEN_LINK) -> it.removePrefix(Pages.OPEN_LINK).takeIf { u -> u.startsWith(CodeforcesApi.ORIGIN) }?.let(::openUrl)
            }
        }
        // The practice timer's tick (rev 24): the status line each second, the database every 30 s.
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (timer.running) {
                    showTimer()
                    if (SystemClock.elapsedRealtime() - timerSavedAt > 30_000) persistTimer()
                }
            }
        }

        // The screen-edge back swipe normally arrives as KEYCODE_BACK (the manifest opts out of
        // predictive back) and goes through the router. If the system ever calls onBackPressed
        // instead, it still lands in the same rules and never closes the activity.
        onBackPressedDispatcher.addCallback(this) { onBack() }

        lcCollections = runCatching { dev.titan.code.data.Collections.load(assets) }.getOrDefault(emptyList())
        list.platform = Platform.byId(prefs.listPlatform)

        watchNetwork()
        setContent { Screen() }
        if (!prefs.onboardingDone) setupOpen = true

        lifecycleScope.launch {
            repo.setUp(library.all)
            reloadList()
            openProblem(prefs.activeProblem ?: library.all.first().slug, announce = false)
            autoRefresh()
        }

        window.decorView.post {
            window.decorView.rootWindowInsets?.displayCutout?.boundingRects
                ?.firstOrNull { it.top == 0 && it.left == 0 && !it.isEmpty }
                ?.let { cutoutPx = it }
        }
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** A PTT relaunch (singleTask) just brings the app forward; it doesn't toggle panes (PLAN §4.1). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TLog.d("onNewIntent ${intent.action}")
    }

    override fun onPause() {
        super.onPause()
        saveDraft()
        repo.flushDrafts()
        timer.foreground(false)
        persistTimer()
    }

    override fun onResume() {
        super.onResume()
        timer.foreground(true)
        showTimer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { router.reset(); gestures.releaseKeys() }
    }

    private fun gestureConfig() = GestureEngine.Config(
        pxPerChar = prefs.pxPerChar.toFloat(),
        pxPerLine = prefs.pxPerLine.toFloat(),
        maxAcceleration = when (prefs.swipeAccel) { "off" -> 1f; "gentle" -> 2f; else -> 3.5f },
    )

    // ---- network (PLAN §8.1) ----------------------------------------------------------------

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val now = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                handler.post { onNetworkChanged(now) }
            }
            override fun onLost(network: Network) { handler.post { onNetworkChanged(false) } }
        })
    }

    private fun onNetworkChanged(now: Boolean) {
        if (now == online) return
        online = now
        TLog.d("network: ${if (now) "online" else "offline"}")
        if (now) lifecycleScope.launch { autoRefresh() }
    }

    /** On start and when coming online: the list weekly, the daily problem daily, a sample's real statement. */
    private suspend fun autoRefresh() {
        if (!online) return
        val week = 7L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - repo.listUpdatedAt() > week && prefs.onboardingDone) refreshList(quiet = true)
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (list.daily?.date != today) runCatching { list.daily = repo.refreshDaily() }
    }

    private fun netError(e: Throwable, platform: Platform = problem?.platform ?: list.platform): String = when (e) {
        is ChallengeException -> "${platform.title} asked for a browser check. Open ⚙ → Accounts → Sign in to pass it, then retry."
        is SignedOutException -> "Your ${platform.title} session isn't signed in or has expired. Sign in again (⚙ → Accounts)."
        is RateLimitedException -> e.message ?: "Too many requests"
        is Judge.JudgeError -> e.message ?: "${platform.title} error"
        is LeetCodeApi.ApiError -> "LeetCode error: ${e.message}"
        is NeetCodeApi.ApiError -> "NeetCode: ${e.message}"
        is CodeforcesApi.ApiError -> "Codeforces: ${e.message}"
        is IOException -> if (online) "Network error: ${e.message}" else "Offline"
        else -> "Error: ${e.message}"
    }

    // ---- problem list (PLAN §4.3) -----------------------------------------------------------

    private suspend fun reloadList() {
        val p = list.platform
        list.all = repo.problems(p)
        list.collections = when (p) {
            Platform.LEETCODE -> lcCollections
            Platform.NEETCODE -> NeetCodeApi.collections(repo.ncCatalog)
            Platform.CODEFORCES -> emptyList()
        }
        list.saved = repo.savedSlugs()
        list.drafts = repo.draftSlugs()
        list.daily = repo.daily()
        list.updatedAt = repo.listUpdatedAt(p)
    }

    /** The list's site switch (rev 13). Filters reset, since lists and tags differ per site. */
    /** 🎲 on the problem list (rev 22): open a random problem from the current view. */
    private fun openRandomProblem() {
        val p = list.random() ?: return
        list.focus(p.slug)
        openProblem(p.slug)
    }

    private fun switchPlatform(p: Platform) {
        prefs.listPlatform = p.id
        list.platform = p
        list.status = null
        list.updateFilter(ProblemFilter())
        listJob?.cancel()
        lifecycleScope.launch {
            reloadList()
            problem?.let { list.focus(it.slug) }
            if (list.all.isEmpty() && online) refreshList(quiet = true)
        }
    }

    private fun refreshList(quiet: Boolean = false) {
        if (listJob?.isActive == true) return
        if (list.platform == Platform.NEETCODE) {
            showMessage("NeetCode's list ships with the app (snapshot ${repo.ncCatalog.size} problems, 2026-11-02)")
            return
        }
        if (!online) { showMessage("Offline: the saved list stays as it is"); return }
        val platform = list.platform
        listJob = lifecycleScope.launch {
            list.status = "Downloading the ${platform.title} problem list…"
            try {
                repo.refreshList(platform) { done, total -> list.status = "Downloading the ${platform.title} problem list: $done / $total" }
                reloadList()
                list.status = null
                if (!quiet) showMessage("Problem list updated: ${list.all.size} problems")
            } catch (e: Exception) {
                TLog.w("list refresh: $e")
                list.status = netError(e, platform)
            }
            refreshSettings()
        }
    }

    /** Keyboard-surface swipes on a list (problems, solutions) move its highlight. */
    private fun onListSwipe(dy: Int) {
        // Finger down moves the highlight down, like the editor cursor (agent decision, rev 9).
        val move: (Int) -> Unit = if (pane == Pane.COMMENTS) ::moveSolution else list::move
        listAccumulator -= dy
        val step = prefs.pxPerLine * LIST_SWIPE_FACTOR
        while (listAccumulator >= step) { move(1); listAccumulator -= step }
        while (listAccumulator <= -step) { move(-1); listAccumulator += step }
    }

    private val listInput = object : InputRouter.TextInput {
        override fun type(text: String) = list.updateFilter(list.filter.copy(text = list.filter.text + text))
        override fun backspace() = list.updateFilter(list.filter.copy(text = list.filter.text.dropLast(1)))
        override fun enter() { list.highlighted()?.let { openProblem(it.slug) } }
        override fun move(delta: Int) = list.move(delta)
    }

    /** The list, with the open problem highlighted so Back-then-Enter returns to it. */
    private fun openList() {
        listMode = true
        problem?.let { list.focus(it.slug) }
    }

    override val textInput: InputRouter.TextInput?
        get() = when {
            blockingOverlay() -> null
            pane == Pane.PROBLEM && listMode -> listInput
            solutionListShowing() -> solutionInput
            else -> null
        }

    // ---- problems and drafts (PLAN §8.2, §12) -----------------------------------------------

    /**
     * Opens a problem from the store, then refreshes it from LeetCode if it was never saved or is a
     * bundled placeholder (save-on-open, PLAN §8.4). Also loads its discussion and saves the next
     * few problems in the list.
     */
    private fun openProblem(slug: String, announce: Boolean = true) {
        endCaseEdit(cancelled = caseEdit != null && caseEdit?.slug != slug)
        openJob?.cancel()
        openJob = lifecycleScope.launch {
            var detail = repo.savedDetail(slug)
            val placeholder = repo.isPlaceholder(slug)
            detail?.let { setProblem(it, placeholder) }
            if ((detail == null || placeholder || repo.isStale(slug)) && online) {
                if (detail == null) showMessage("Downloading…")
                try {
                    repo.fetchDetail(slug)?.let { detail = it; setProblem(it, placeholder = false) }
                } catch (e: Exception) {
                    TLog.w("open $slug: $e")
                    if (detail == null) showMessage(netError(e))
                }
            }
            val d = detail ?: run {
                if (!online) showMessage("Offline, and that problem isn't saved yet")
                return@launch
            }
            list.saved = list.saved + d.slug
            if (announce) showMessage("Opened ${d.label}")
            loadDiscussion(d.slug, force = false)
            loadTestCases(d)
            // Codeforces pages load in the one hidden browser that Submit also needs: no prefetch (rev 13).
            val next = if (d.platform == Platform.CODEFORCES) emptyList() else list.after(slug, prefs.prefetchCount)
            if (online && next.isNotEmpty()) {
                prefetchJob?.cancel()
                prefetchJob = lifecycleScope.launch {
                    repo.prefetch(next)
                    list.saved = repo.savedSlugs()
                }
            }
        }
    }

    /** Shows [d]. The buffer only reloads when the problem changes (a refreshed statement keeps your code). */
    private fun setProblem(d: ProblemDetail, placeholder: Boolean) {
        val switching = problem?.slug != d.slug
        if (switching) {
            saveDraft()
            persistTimer()
            timer.load(d.slug, null)
            timerText = null
            lifecycleScope.launch {
                val row = repo.timer(d.slug)
                if (timer.slug == d.slug && !timer.started) { timer.load(d.slug, row); showTimer() }
            }
            if (!testBuffer) loadDraftOrStarter(d)
            commentsTab = CommentsTab.SOLUTIONS
            closePost()
            solutionHighlight = 0
            discussion = null
            discussionStatus = null
            problemView.scrollTo(0, 0)
        }
        problem = d
        prefs.activeProblem = d.slug
        listMode = false
        problemView.show(Pages.statement(d, placeholder))
        if (switching) renderComments()
        TLog.d("problem -> ${d.slug}${if (placeholder) " (bundled)" else ""}")
    }

    private fun loadDraftOrStarter(d: ProblemDetail) {
        val draft = repo.loadDraft(d.slug)
        if (draft != null) {
            loadBuffer(draft.text, draft.line, draft.column)
        } else {
            val p = Starter.prepare(d.starter ?: "# This problem has no Python 3 starter code.\n", prefs.indentWidth)
            loadBuffer(p.text, p.line, p.column)
        }
        dirty = false
        lastVersionAt = SystemClock.uptimeMillis()
    }

    private fun loadBuffer(text: String, line: Int, column: Int) {
        loading = true
        try { editor.load(text, line, column) } finally { loading = false }
        updatePosition()
    }

    private fun onBufferChanged() {
        if (testBuffer) return
        if (prefs.practiceTimer && !timer.started) { timer.edited(System.currentTimeMillis()); showTimer() }
        dirty = true
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, AUTOSAVE_DEBOUNCE_MS)
    }

    /**
     * Queues the draft and cursor for writing, if anything changed. Runs 1 s after the last edit,
     * on pause and on problem switch (PLAN §12, E14). Silent unless it fails (rev 9). Keeps a
     * version in the history every 5 minutes of editing (PLAN §8.2).
     */
    private fun saveDraft() {
        handler.removeCallbacks(saveRunnable)
        val p = problem ?: return
        if (testBuffer || !dirty) return
        dirty = false
        val c = editor.cursor
        val text = editor.text.toString()
        repo.saveDraft(p.slug, Repository.Draft(text, c.rightLine, c.rightColumn)) {
            handler.post { draftNote = SAVE_FAILED }
        }
        if (draftNote == SAVE_FAILED) draftNote = null
        list.drafts = list.drafts + p.slug
        val now = SystemClock.uptimeMillis()
        if (now - lastVersionAt > VERSION_EVERY_MS) {
            repo.addVersion(p.slug, text, "periodic")
            lastVersionAt = now
        }
    }

    /** `sym+N`, pressed twice within 3 s: keep the draft in history, then reset to the starter (PLAN §5.4). */
    private fun resetToStarter() {
        val p = problem ?: return
        val now = SystemClock.uptimeMillis()
        if (now > resetArmedUntil) {
            resetArmedUntil = now + RESET_CONFIRM_MS
            showMessage("Press sym+N again to reset ${p.title} to the starter code")
            return
        }
        resetArmedUntil = 0
        repo.addVersion(p.slug, editor.text.toString(), "reset")
        val s = Starter.prepare(p.starter ?: "", prefs.indentWidth)
        editor.replaceAll(s.text, s.line, s.column)
        saveDraft()
        showMessage("Reset to starter. Old draft is in Settings → Draft history; sym+U undoes")
    }

    // ---- discussion (PLAN §4.1 Comments, §8.2) ----------------------------------------------

    private fun loadDiscussion(slug: String, force: Boolean) {
        discussionJob?.cancel()
        // Rev 32: Codeforces shows the contest editorial's part for this problem.
        val cf = Platform.of(slug) == Platform.CODEFORCES
        discussionJob = lifecycleScope.launch {
            if (!force) {
                discussion = repo.savedDiscussion(slug)
                renderComments()
                if (discussion != null) return@launch
            }
            if (cf && pane != Pane.COMMENTS) {
                // The editorial loads in the hidden browser Submit also uses: only when it's wanted.
                discussionStatus = null
                return@launch
            }
            if (!online) {
                discussionStatus = if (discussion != null) null
                    else if (cf) "Offline: no editorial saved for this problem yet" else "Offline: no discussion saved for this problem yet"
                renderComments()
                return@launch
            }
            discussionStatus = if (cf) "Loading the contest's editorial from codeforces.com… (about 10 s)" else "Downloading discussion and solutions…"
            renderComments()
            try {
                discussion = repo.fetchDiscussion(slug)
                discussionStatus = null
            } catch (e: Exception) {
                TLog.w("discussion $slug: $e")
                discussionStatus = netError(e)
            }
            renderComments()
        }
    }

    /** The WebView shows the Discussion tab or an opened solution post; the solution list is native. */
    private fun renderComments(keepScroll: Boolean = false) {
        val p = problem ?: return
        // Owner, rev 35: a Codeforces problem has exactly one entry (its editorial), so it opens
        // straight away instead of a one-row list.
        if (singleEntry(p) && openSolution == null && commentsTab == CommentsTab.SOLUTIONS && solutions.size == 1) {
            openSolutionPost(solutions.single())
            return
        }
        val s = openSolution
        when {
            commentsTab == CommentsTab.DISCUSSION -> commentsView.show(Pages.discussion(p.label, discussion, discussionStatus))
            s != null -> commentsView.show(Pages.post(s, openPost, postStatus), keepScroll)
        }
    }

    private val solutions get() = discussion?.solutions.orEmpty()

    /** Rev 35: sites whose Comments pane holds one entry, shown without the list. */
    private fun singleEntry(p: ProblemDetail) = p.platform == Platform.CODEFORCES

    private fun moveSolution(delta: Int) {
        solutionHighlight = (solutionHighlight + delta).coerceIn(0, (solutions.size - 1).coerceAtLeast(0))
    }

    /** Open a solution post: saved copy first, then its body and first page of comments from LeetCode. */
    private fun openSolutionPost(s: Solution) {
        val p = problem ?: return
        solutionHighlight = solutions.indexOf(s).coerceAtLeast(0)
        openSolution = s
        openPost = null
        postStatus = null
        commentsView.scrollTo(0, 0)
        renderComments()
        postJob?.cancel()
        if (p.platform != Platform.LEETCODE) {
            // NeetCode's article (rev 13) and a Codeforces editorial (rev 32) are the whole post, with no comments.
            openPost = Post(s.topicId, s.body.orEmpty(), emptyList(), 0, 1, System.currentTimeMillis())
            renderComments()
            return
        }
        postJob = lifecycleScope.launch {
            openPost = repo.savedPost(s.topicId)
            if (openPost == null) {
                postStatus = if (online) "Loading the post and its comments…" else "Offline: this post's comments aren't saved"
                renderComments()
                if (online) {
                    postStatus = try {
                        openPost = repo.fetchPost(p.slug, s.topicId, s.body)
                        null
                    } catch (e: Exception) {
                        TLog.w("post ${s.topicId}: $e")
                        netError(e)
                    }
                }
            }
            renderComments()
        }
    }

    private fun refreshPost() {
        val p = problem ?: return
        val s = openSolution ?: return
        if (p.platform != Platform.LEETCODE) {
            // Rev 35: dropped first, so the fresh copy opens, not the one on screen.
            closePost()
            discussion = null
            loadDiscussion(p.slug, force = true)
            return
        }
        if (!online) { showMessage("Offline"); return }
        postJob?.cancel()
        postJob = lifecycleScope.launch {
            postStatus = "Refreshing…"
            renderComments(keepScroll = true)
            postStatus = try { openPost = repo.fetchPost(p.slug, s.topicId, null); null } catch (e: Exception) { netError(e) }
            renderComments(keepScroll = true)
        }
    }

    /** "Load more comments" at the end of a post. */
    private fun loadMoreComments() {
        val p = problem ?: return
        val post = openPost ?: return
        if (!post.hasMore || postJob?.isActive == true) return
        if (!online) { showMessage("Offline: can't load more comments"); return }
        showMessage("Loading more comments…")
        postJob = lifecycleScope.launch {
            try {
                openPost = repo.fetchPost(p.slug, post.topicId, null, more = post)
            } catch (e: Exception) {
                showMessage(netError(e))
            }
            renderComments(keepScroll = true)
        }
    }

    private fun closePost() {
        postJob?.cancel()
        openSolution = null
        openPost = null
        postStatus = null
    }

    private val solutionInput = object : InputRouter.TextInput {
        override fun type(text: String) = Unit
        override fun backspace() = Unit
        override fun enter() { solutions.getOrNull(solutionHighlight)?.let(::openSolutionPost) }
        override fun move(delta: Int) = moveSolution(delta)
    }

    private fun solutionListShowing() = pane == Pane.COMMENTS && commentsTab == CommentsTab.SOLUTIONS && openSolution == null

    // ---- Run and Submit on LeetCode (PLAN §6.2, M3) -----------------------------------------

    private fun loadTestCases(d: ProblemDetail) {
        lifecycleScope.launch {
            testCases = repo.testCases(d)
            history = repo.submissions(d.slug)
            outcome = outcomes[d.slug]
        }
    }

    private fun deleteTestCase(c: TestCase) {
        val id = c.id ?: return
        lifecycleScope.launch {
            repo.deleteTestCase(id)
            problem?.let { testCases = repo.testCases(it) }
        }
    }

    /**
     * The test-case editor (rev 24): [c] (or, for a new case, the first example) opens in the
     * Code pane as one section per input; sym+S saves, Back cancels. Editing an example or a
     * case from a submission saves a new case of yours; editing yours replaces it.
     */
    private fun editCase(c: TestCase?) {
        val p = problem ?: return
        if (testBuffer) { showMessage(busyMessage()); return }
        if (judgeJob?.isActive == true) { showMessage("Still running…"); return }
        val base = c ?: testCases.firstOrNull()
        saveDraft()
        testBuffer = true
        caseEdit = CaseEdit(p.slug, c?.takeIf { it.source == "custom" }?.id)
        loadBuffer(dev.titan.code.data.CaseBuffer.render(p, base?.input, c?.expected?.takeIf { !c.verified || c.source == "custom" }),
            dev.titan.code.data.CaseBuffer.firstValueLine(), 0)
        draftNote = "test case"
        showPane(Pane.CODE)
    }

    private fun saveCaseEdit() {
        val p = problem ?: return
        val ce = caseEdit ?: return
        when (val r = dev.titan.code.data.CaseBuffer.parse(p, editor.text.toString())) {
            is dev.titan.code.data.CaseBuffer.Parsed.Error -> {
                editor.setSelection(r.line.coerceIn(0, editor.text.lineCount - 1), 0)
                editor.ensureSelectionVisible()
                showMessage(r.message)
            }
            is dev.titan.code.data.CaseBuffer.Parsed.Case -> lifecycleScope.launch {
                repo.saveCase(ce.slug, ce.replacing, r.input, r.expected)
                endCaseEdit()
                testCases = repo.testCases(p)
                showPane(Pane.RESULTS)
                showMessage(if (ce.replacing != null) "Test case updated" else "Test case added")
            }
        }
    }

    /** Put the draft back (Back, or after saving). */
    private fun endCaseEdit(cancelled: Boolean = false) {
        if (caseEdit == null) return
        caseEdit = null
        testBuffer = false
        draftNote = null
        problem?.let { loadDraftOrStarter(it) }
        if (cancelled) showMessage("Test case not saved")
    }

    /** A custom test from the clipboard (PLAN §8.3); warns if its line count doesn't fit the problem. */
    private fun addCaseFromClipboard() {
        val p = problem ?: return
        val raw = clip?.text?.replace("\r\n", "\n")?.trim('\n')
        // Codeforces input is used as typed (rev 13); LeetCode-style input is one value per line.
        val stdin = p.platform.stdin
        val text = if (stdin) raw?.let { it + "\n" } else raw?.lines()?.map { it.trim() }?.joinToString("\n")
        if (text.isNullOrBlank()) { showMessage("Clipboard is empty. Select the input in the editor and press sym+C."); return }
        val need = Judge.linesPerCase(p.metaData)
        val have = text.lines().size
        lifecycleScope.launch {
            if (!repo.addCustomCase(p.slug, text)) { showMessage("That test is already saved"); return@launch }
            testCases = repo.testCases(p)
            showMessage(if (have == need || stdin) "Test added" else "Test added, but it has $have lines and this problem takes $need")
        }
    }

    /** Run the test cases on the phone, offline (PLAN §7). Needs no network or account. */
    private fun runLocally() {
        val p = problem ?: return
        when {
            p.starter == null -> { showMessage("This problem has no Python 3 version"); return }
            judgeJob?.isActive == true -> { showMessage("Still running…"); return }
            testBuffer -> { showMessage(busyMessage()); return }
        }
        val code = editor.text.toString()
        saveDraft()
        val cases = testCases
        if (cases.isEmpty()) { showMessage("No test cases for this problem"); return }
        val inputs = cases.map { it.input }
        val expected = cases.map { it.expected }
        // Rev 24: the harness stops a case at its own limit and writes the results so far to this
        // file, so even a run that has to be killed shows the cases that finished.
        val progress = java.io.File(cacheDir, "runner-progress.json").apply { delete() }
        val limit = dev.titan.code.runner.LocalJudge.let { it.overallLimitMs(inputs.size, it.caseLimitMs(p)) }
        startJudge(p, Outcome.Running("Run locally", "Running on the phone…")) { _ ->
            val lj = dev.titan.code.runner.LocalJudge
            try {
                Outcome.Ran(lj.parse(runner.run(lj.request(code, p, inputs, expected, progress.path), limit), inputs, expected))
            } catch (e: dev.titan.code.runner.RunnerTimeoutException) {
                Outcome.Ran(lj.parseKilled(progress.takeIf { it.exists() }?.readText(), inputs, expected,
                    "Time Limit Exceeded: stuck inside one call that holds Python (a huge built-in operation, such as sum(range(10**12))). " +
                        "The runner was stopped and restarted.", "time"))
            } catch (e: dev.titan.code.runner.RunnerCrashedException) {
                // The runner process died: almost always memory (one allocation too big to watch).
                Outcome.Ran(lj.parseKilled(progress.takeIf { it.exists() }?.readText(), inputs, expected,
                    "Memory Limit Exceeded: the runner process was killed (${e.message}). It has been restarted.", "memory"))
            }
        }
    }

    /**
     * Stress test (rev 24): the draft against a reference solution (NeetCode's article or a saved
     * LeetCode solution post) on inputs generated from the constraints, offline.
     */
    private fun stressTest() {
        val p = problem ?: return
        when {
            p.platform.stdin -> { showMessage("Stress tests are for LeetCode and NeetCode problems"); return }
            p.starter == null -> { showMessage("This problem has no Python 3 version"); return }
            judgeJob?.isActive == true -> { showMessage("Still running…"); return }
            testBuffer -> { showMessage(busyMessage()); return }
        }
        val code = editor.text.toString()
        saveDraft()
        val inputs = testCases.map { it.input }
        val expected = testCases.map { it.expected }
        startJudge(p, Outcome.Running("Stress test", "Finding a reference solution…")) { progress ->
            val lj = dev.titan.code.runner.LocalJudge
            val refs = repo.references(p, online)
            if (refs.isEmpty()) {
                return@startJudge Outcome.Stressed(dev.titan.code.runner.LocalJudge.Stress(0, 0, 0.0, "", null, null, emptyList(), emptyList(),
                    error = "No reference solution is saved for this problem. Open Comments once while online " +
                        "(NeetCode's solution, or LeetCode's solution posts), then try again."))
            }
            progress.update("Comparing with ${refs.first().first} on random inputs… (about ${lj.STRESS_SECONDS} s)")
            try {
                Outcome.Stressed(lj.parseStress(runner.run(lj.stressRequest(code, p, inputs, expected, refs), lj.STRESS_LIMIT_MS)))
            } catch (e: dev.titan.code.runner.RunnerTimeoutException) {
                Outcome.Failed("Stress test", "A call got stuck in a built-in operation and the runner was restarted. Run your examples (sym+R) to find it.")
            } catch (e: dev.titan.code.runner.RunnerCrashedException) {
                Outcome.Failed("Stress test", "The runner ran out of memory (${e.message}) and was restarted.")
            }
        }
    }

    /** Save a stress test's counterexample as a test case, with the reference's output as expected. */
    private fun saveStressCase(m: dev.titan.code.runner.LocalJudge.Mismatch) {
        val p = problem ?: return
        lifecycleScope.launch {
            if (!repo.addCustomCase(p.slug, m.input, m.expected)) { showMessage("That test is already saved"); return@launch }
            testCases = repo.testCases(p)
            showMessage("Test added (expected from the reference)")
        }
    }

    /** "Go to line N" from a traceback: put the cursor there in the Code pane (PLAN §7). */
    private fun goToLine(line: Int) {
        val l = (line - 1).coerceIn(0, editor.text.lineCount - 1)
        editor.setSelection(l, editor.text.getLineString(l).let { it.length - it.trimStart().length })
        editor.ensureSelectionVisible()
        showPane(Pane.CODE)
    }

    /** Checks shared by Run and Submit. Returns the problem, or null after telling the user why not. */
    private fun judgeReady(): ProblemDetail? {
        val p = problem ?: return null
        val signedIn = when (p.platform) {
            Platform.LEETCODE -> username != null
            Platform.NEETCODE -> nc.signedIn
            Platform.CODEFORCES -> cfHandle != null
        }
        when {
            !online -> showMessage(OFFLINE_SUBMIT)
            !signedIn -> showMessage("Sign in to ${p.platform.title} first (⚙ → Accounts)")
            p.starter == null -> showMessage("This problem has no Python 3 version")
            judgeJob?.isActive == true -> showMessage("Still waiting for ${p.platform.title}…")
            testBuffer -> showMessage(busyMessage())
            else -> return p
        }
        return null
    }

    /** Run the test cases on the problem's site; the Results pane opens straight away (PLAN §4.1). */
    private fun runRemote() {
        problem?.takeIf { !it.platform.remoteRun }?.let {
            showMessage("${it.platform.title} has no remote run here: sym+R runs the samples on the phone, sym+S submits")
            return
        }
        val p = judgeReady() ?: return
        val code = editor.text.toString()
        saveDraft()
        val cases = testCases.map { it.input }.ifEmpty { listOf(p.exampleTestcases) }
        startJudge(p, Outcome.Running("Run on ${p.platform.title}", "Starting…")) { progress ->
            Outcome.Ran(repo.runRemote(p, code, cases, progress))
        }
    }

    /** `sym+S` or Submit: the first press arms it for 3 s, the second submits (PLAN §4.1, §5.4). */
    private fun armSubmit() {
        val now = SystemClock.uptimeMillis()
        if (now > submitArmedUntil) {
            if (judgeReady() == null) return
            submitArmedUntil = now + RESET_CONFIRM_MS
            submitConfirm = true
            handler.postDelayed({ if (SystemClock.uptimeMillis() >= submitArmedUntil) submitConfirm = false }, RESET_CONFIRM_MS)
            showMessage("Press sym+S again (or tap Confirm?) to submit ${problem?.title} to ${problem?.platform?.title}" +
                if (problem?.platform == Platform.CODEFORCES) " in ${CodeforcesApi.Language.byName(prefs.cfLanguage).label}" else "")
            return
        }
        disarmSubmit()
        val p = judgeReady() ?: return
        val code = editor.text.toString()
        saveDraft()
        // Rev 24: the practice timer's reading goes with the submission; an accepted one stops it.
        val elapsed = timer.takeIf { it.started && it.slug == p.slug && prefs.practiceTimer }?.elapsedMs()
        startJudge(p, Outcome.Running("Submit to ${p.platform.title}", "Submitting…")) { progress ->
            val o = if (p.platform == Platform.CODEFORCES) {
                val v = repo.submitCodeforces(p, code, cfHandle ?: throw SignedOutException(), CodeforcesApi.Language.byName(prefs.cfLanguage), progress, elapsed)
                Outcome.CfSubmitted(v, CodeforcesApi.submissionUrl(v.contestId, v.id))
            } else {
                Outcome.Submitted(repo.submit(p, code, progress, elapsed))
            }
            val accepted = (o as? Outcome.Submitted)?.result?.accepted ?: (o as? Outcome.CfSubmitted)?.verdict?.accepted ?: false
            if (accepted && timer.slug == p.slug) { timer.finish(System.currentTimeMillis()); persistTimer(); showTimer() }
            o
        }
    }

    private fun disarmSubmit() {
        submitArmedUntil = 0
        submitConfirm = false
    }

    private fun startJudge(p: ProblemDetail, start: Outcome.Running, work: suspend (Repository.Progress) -> Outcome) {
        outcome = start
        outcomes[p.slug] = start
        if (pane != Pane.RESULTS) toggleResults()
        lifecycleScope.launch { resultsScroll.scrollTo(0) }
        judgeJob = lifecycleScope.launch {
            val result = try {
                work { text -> if (problem?.slug == p.slug) outcome = Outcome.Running(start.kind, text) }
            } catch (e: Exception) {
                TLog.w("${start.kind} ${p.slug}: $e")
                Outcome.Failed(start.kind, netError(e, p.platform))
            }
            outcomes[p.slug] = result
            if (problem?.slug == p.slug) {
                outcome = result
                testCases = repo.testCases(p)
                history = repo.submissions(p.slug)
            }
            if (result is Outcome.Submitted || result is Outcome.CfSubmitted) list.all = repo.problems(list.platform)
            val text = when (result) {
                is Outcome.Submitted -> result.result.statusMsg
                is Outcome.CfSubmitted -> result.verdict.label
                is Outcome.Ran -> result.result.verdict
                is Outcome.Stressed -> result.stress.let { s ->
                    when { s.error != null -> "can't run"; s.mismatch != null -> "different answer"; s.big?.error != null -> "too slow"; else -> "no differences in ${s.tried}" }
                }
                else -> ""
            }
            showMessage("${start.kind}: ${result.badge ?: ""} $text".trim())
        }
    }

    // ---- panes ------------------------------------------------------------------------------

    private fun showPane(p: Pane) {
        if (p == pane) return
        pane = p
        TLog.d("pane -> $p")
        if (p != Pane.CODE) closeOverlays()
        // Rev 32: a Codeforces editorial is fetched the first time Comments is shown.
        problem?.let { d ->
            if (p == Pane.COMMENTS && d.platform == Platform.CODEFORCES && discussion == null && discussionJob?.isActive != true) loadDiscussion(d.slug, force = false)
        }
        // The editor keeps its cursor, selection and scroll: it's only hidden, never recreated.
        // Other panes focus the key sink so keys still arrive before the IME.
        if (p == Pane.CODE) editor.post { editor.requestFocus() } else keySink.post { keySink.requestFocus() }
    }

    /** Red button (keycode 401, PTT app = Titan Code): Problem ↔ Code; other panes → Code. */
    private fun onRedButton() {
        settingsOpen = false
        setupOpen = false
        showPane(if (pane == Pane.CODE) Pane.PROBLEM else Pane.CODE)
    }

    private fun toggleResults() {
        if (pane == Pane.RESULTS) {
            showPane(paneBeforeResults)
        } else {
            paneBeforeResults = pane
            showPane(Pane.RESULTS)
        }
    }

    /** Links out (a Codeforces submission page) open in the phone's browser. */
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            .onFailure { showMessage("No browser to open $url") }
    }

    private fun showMessage(text: String) {
        message = text
        messageSeq++
    }

    private fun closeOverlays() {
        paletteOpen = false
        cheatOpen = false
        closeCompletions()
    }

    /** Settings and the setup checklist take the whole screen; keys mustn't reach the editor under them. */
    private fun blockingOverlay() = settingsOpen || setupOpen


    // ---- InputRouter.Shell ------------------------------------------------------------------

    /**
     * PLAN §5.1: overlays first; Code ignores Back; Results/Comments → Code; on Problem the
     * statement goes to the list and the list back to the statement. Back never closes or
     * backgrounds the app (owner, rev 13).
     */
    override fun onBack() {
        when {
            setupOpen -> finishSetup()
            settingsOpen -> settingsOpen = false
            cheatOpen -> cheatOpen = false
            paletteOpen -> paletteOpen = false
            completions.isNotEmpty() -> closeCompletions()
            caseEdit != null && pane == Pane.CODE -> endCaseEdit(cancelled = true)
            pane == Pane.CODE -> Unit
            pane == Pane.COMMENTS && openSolution != null && problem?.let(::singleEntry) != true -> closePost()
            pane == Pane.RESULTS || pane == Pane.COMMENTS -> showPane(Pane.CODE)
            // Owner, rev 10: Back on a statement goes to the problem list. Owner, rev 13: Back
            // never leaves the app; on the list it returns to the open problem (if any).
            pane == Pane.PROBLEM && !listMode -> openList()
            pane == Pane.PROBLEM -> if (problem != null) listMode = false
        }
    }

    override fun onLeader(command: Leader) {
        if (command != Leader.RESET) resetArmedUntil = 0
        if (command != Leader.SUBMIT) disarmSubmit()
        if (blockingOverlay() && command != Leader.CHEAT_SHEET) return
        when (command) {
            Leader.RESULTS -> toggleResults()
            Leader.PROBLEMS -> {
                if (pane == Pane.PROBLEM && listMode && problem != null) listMode = false
                else { showPane(Pane.PROBLEM); openList() }
            }
            Leader.RUN_LOCAL -> runLocally()
            Leader.RUN_REMOTE -> runRemote()
            Leader.SUBMIT -> if (caseEdit != null) saveCaseEdit() else armSubmit()
            Leader.CHEAT_SHEET -> cheatOpen = !cheatOpen
            Leader.FONT_UP -> textSize(+1)
            Leader.FONT_DOWN -> textSize(-1)
            Leader.RESET -> resetToStarter()
            Leader.COPY -> showMessage(if (clip?.wholeLine == true) "Copied line" else "Copied")
            Leader.CUT -> showMessage(if (clip?.wholeLine == true) "Cut line" else "Cut")
            Leader.PASTE -> if (clip == null) showMessage("Clipboard is empty")
            else -> Unit
        }
    }

    override fun onSymHeld(held: Boolean) {
        symHeld = held
    }

    override fun onSoftWrapChanged(on: Boolean) {
        prefs.softWrap = on
        showMessage(if (on) "Soft wrap on" else "Soft wrap off")
        refreshSettings()
    }

    override fun onKeyLogged(description: String) = Unit
    override fun onTypingKey(code: Int, down: Boolean, timeMs: Long) = gestures.onKey(code, down, timeMs)

    override fun onFn() {
        if (blockingOverlay()) return
        paletteOpen = !paletteOpen
    }

    override fun closePalette(): Boolean = paletteOpen.also { paletteOpen = false }

    override fun closeCheatSheet(): Boolean = cheatOpen.also { cheatOpen = false }

    override fun onEdited() {
        resetArmedUntil = 0
        refreshCompletions()
    }

    // ---- completion (PLAN §5.4, M5) ---------------------------------------------------------

    private var completions by mutableStateOf<List<Completer.Item>>(emptyList())
    private var completionPrefix = ""
    private var completionSite: CompletionSite? = null
    /** Jedi's last list and where it was for, to narrow at once while the next answer comes (rev 38). */
    private var jediLast: Pair<Pair<Int, Int>, List<Completer.Item>>? = null
    private val completionFallback = Runnable { completionSite?.let { completions = fallbackCompletions(it) } }

    /**
     * After each edit (rev 38): ask Jedi for the names that fit the cursor, typed by what it infers
     * (`nums.` → the list methods). Until it answers, the last answer narrowed to the new prefix
     * stays up; if it hasn't answered in [JEDI_WAIT_MS] (starting, or busy) the word list shows.
     */
    private fun refreshCompletions() {
        handler.removeCallbacks(completionFallback)
        val site = editor.actions.completionSite().takeIf { pane == Pane.CODE }
        if (site == null || (!site.afterDot && site.prefix.length < 2)) { closeCompletions(); return }
        completionSite = site
        completionPrefix = site.prefix
        // A test case being edited is JSON, not Python: the word list only.
        if (caseEdit != null) { completions = fallbackCompletions(site); return }
        val last = jediLast?.takeIf { it.first == site.anchor }?.second
        completions = last?.filter { it.label.startsWith(site.prefix) && it.label != site.prefix } ?: emptyList()
        val words = editor.actions.words()
        val sent = completer.request(Completer.request(editor.actions.bufferText(), site.line, site.col)) { r ->
            handler.removeCallbacks(completionFallback)
            if (completionSite != site || editor.actions.completionSite() != site) return@request
            val jedi = Completer.parse(r)
            if (jedi == null) { completions = fallbackCompletions(site); return@request }
            jediLast = site.anchor to jedi
            completions = Completer.merge(jedi, site, words)
        }
        if (!sent) completions = fallbackCompletions(site)
        else if (completions.isEmpty()) handler.postDelayed(completionFallback, JEDI_WAIT_MS)
    }

    /** The word list: buffer words and common names; nothing after a `.` (it can't know the type). */
    private fun fallbackCompletions(site: CompletionSite): List<Completer.Item> =
        if (site.afterDot) emptyList() else Completer.suggest(site.prefix, editor.actions.words())

    private fun closeCompletions() {
        handler.removeCallbacks(completionFallback)
        if (::completer.isInitialized) completer.cancel()
        completions = emptyList()
        completionSite = null
    }

    override fun acceptCompletion(): Boolean {
        val item = completions.firstOrNull() ?: return false
        acceptCompletion(item)
        return true
    }

    private fun acceptCompletion(item: Completer.Item) {
        val site = completionSite
        editor.actions.complete(completionPrefix.length, if (site != null) Completer.insertFor(item, site) else item.insert)
        closeCompletions()
        resetArmedUntil = 0
    }

    /** `sym+I` / `sym+O` (rev 27): the editor's font on Code, the reading size on Problem and Comments. */
    private fun textSize(delta: Int) = when (pane) {
        Pane.CODE -> changeFont(delta)
        Pane.PROBLEM, Pane.COMMENTS -> changeReading(delta)
        else -> showMessage("Text size: sym+I / sym+O on the Code, Problem or Comments pane")
    }

    /** Reading text size of the Problem and Comments panes, as a percentage (rev 27). */
    private var readingZoom by mutableStateOf(100)

    private fun applyReadingZoom() {
        readingZoom = prefs.readingZoom
        problemView.settings.textZoom = readingZoom
        commentsView.settings.textZoom = readingZoom
    }

    private fun changeReading(delta: Int) {
        val steps = Prefs.READING_ZOOMS
        val i = steps.indexOfFirst { it >= prefs.readingZoom }.coerceAtLeast(0)
        prefs.readingZoom = steps[(i + delta).coerceIn(0, steps.size - 1)]
        applyReadingZoom()
        showMessage("Problem and Comments text ${prefs.readingZoom}%")
        refreshSettings()
    }

    private fun changeFont(delta: Int) {
        val size = (prefs.fontSize + delta).coerceIn(Prefs.MIN_FONT_SIZE, Prefs.MAX_FONT_SIZE)
        prefs.fontSize = size
        editor.setTextSize(size.toFloat())
        editor.ensureSelectionVisible()
        showMessage("Font $size sp")
        refreshSettings()
    }

    /** App clipboard plus the system clipboard; text copied elsewhere pastes as plain text. */
    private var appClip: Clip? = null
    override var clip: Clip?
        get() {
            val cm = getSystemService(ClipboardManager::class.java)
            val sys = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            val mine = appClip
            return when {
                sys == null -> mine
                mine != null && mine.text == sys -> mine
                else -> Clip(sys, false)
            }
        }
        set(value) {
            appClip = value
            if (value != null) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", value.text))
            }
        }

    /** The palette inserts through the same path as a typed key (PLAN §5.3). */
    private fun onPaletteSymbol(s: String) {
        paletteOpen = false
        editor.actions.insert(s)
        onEdited()
    }

    // ---- Settings, setup, sign-in (PLAN §3, §6.2) -------------------------------------------

    private fun openSettings() {
        settingsOpen = true
        closeOverlays()
        refreshSettings()
    }

    /** Rev 25: the download notification needs this on Android 13+; refused, downloads still run, just without it. */
    private fun askForNotifications() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    /** Follow pack downloads: progress for Settings, and the ⤓ marks when one finishes. */
    private fun watchPackWork() {
        lifecycleScope.launch {
            WorkManager.getInstance(this@MainActivity).getWorkInfosByTagFlow(Sync.TAG_PACK).collect { infos ->
                val map = mutableMapOf<Long, String>()
                var finished = false
                infos.forEach { w ->
                    val id = w.tags.firstOrNull { it.startsWith("pack-id-") }?.removePrefix("pack-id-")?.toLongOrNull() ?: return@forEach
                    when (w.state) {
                        WorkInfo.State.RUNNING -> {
                            val phase = w.progress.getString(Sync.KEY_PHASE) ?: "downloading"
                            val failed = w.progress.getInt(Sync.KEY_FAILED, 0)
                            map[id] = "$phase ${w.progress.getInt(Sync.KEY_DONE, 0)}/${w.progress.getInt(Sync.KEY_TOTAL, 0)}" +
                                if (failed > 0) ", $failed skipped" else ""
                        }
                        WorkInfo.State.ENQUEUED -> map[id] = when {
                            w.runAttemptCount > 0 -> "paused; resumes by itself"
                            prefs.packsOnMobile -> "waiting for a connection"
                            else -> "waiting for Wi-Fi"
                        }
                        WorkInfo.State.FAILED -> map[id] = "failed; remove and add again"
                        WorkInfo.State.SUCCEEDED -> {
                            finished = true
                            val failed = w.outputData.getInt(Sync.KEY_FAILED, 0)
                            if (failed > 0) map[id] = "done; $failed couldn't be saved (Update tries them again)"
                        }
                        else -> Unit
                    }
                }
                packWork = map
                if (finished || map.isNotEmpty()) list.saved = repo.savedSlugs()
                refreshSettings()
            }
        }
    }

    private fun listDescription(): String {
        val f = list.filter
        val parts = listOfNotNull(f.collection?.title, f.text.trim().takeIf { it.isNotEmpty() }?.let { "\"$it\"" }, f.difficulty, f.status, if (f.savedOnly) "saved" else null)
        return if (parts.isEmpty()) "all problems" else parts.joinToString(" · ")
    }

    private fun refreshSettings() {
        if (!settingsOpen) return
        lifecycleScope.launch {
            val slug = problem?.slug
            val packs = repo.packs()
            val planTitles = packs.map { it.title }.toSet()
            settingsState = SettingsState(
                username = username,
                online = online,
                listSize = list.all.size,
                listUpdatedAt = repo.listUpdatedAt(list.platform),
                storage = repo.storage(),
                indentWidth = prefs.indentWidth,
                fontSize = prefs.fontSize,
                readingZoom = prefs.readingZoom,
                softWrap = prefs.softWrap,
                pxPerChar = prefs.pxPerChar,
                swipeAccel = prefs.swipeAccel,
                swipeHaptics = dev.titan.code.input.SwipeHaptics.Level.byName(prefs.swipeHaptics).label,
                pxPerLine = prefs.pxPerLine,
                prefetch = prefs.prefetchCount,
                problemLabel = problem?.label,
                versions = if (slug != null) repo.versions(slug) else emptyList(),
                packs = packs.map { PackView(it.id, it.title, it.slugs.size, it.saved, packWork[it.id], it.everything) },
                everything = Platform.entries.map { p ->
                    EverythingView(p, repo.freeProblemCount(p), packs.any { it.source == Repository.ALL_PREFIX + p.id })
                },
                plans = LeetCodeApi.STUDY_PLANS.filter { it.second !in planTitles },
                collections = list.collections.filter { it.title !in planTitles }.map { Triple(it.id, it.title, it.slugs.size) },
                listView = listDescription() to list.shown.size,
                packsOnMobile = prefs.packsOnMobile,
                neetcode = ncAccount,
                codeforces = cfHandle,
                cfLanguage = CodeforcesApi.Language.byName(prefs.cfLanguage).label,
                listPlatform = list.platform.title,
                practiceTimer = prefs.practiceTimer,
                timerReading = timer.takeIf { it.started && it.slug == problem?.slug }
                    ?.let { dev.titan.code.data.PracticeTimer.format(it.elapsedMs()) + if (it.done) " (solved)" else "" },
            )
        }
    }

    private val settingsActions = object : SettingsActions {
        override fun signIn() { loginFor = Platform.LEETCODE }
        override fun signOut() {
            auth.clear()
            webFetch.reset()
            username = null
            showMessage("Signed out")
            refreshSettings()
        }
        override fun signInTo(platform: Platform) { loginFor = platform }
        override fun signOutOf(platform: Platform) {
            when (platform) {
                Platform.LEETCODE -> signOut()
                Platform.NEETCODE -> {
                    nc.signOut()
                    ncAccount = null
                    // The page's own Firebase session too, so the next sign-in starts fresh.
                    android.webkit.WebStorage.getInstance().deleteOrigin(NeetCodeApi.ORIGIN)
                    dev.titan.code.data.AuthStore.expireCookies(NeetCodeApi.ORIGIN)
                }
                Platform.CODEFORCES -> {
                    dev.titan.code.data.AuthStore.expireCookies(CodeforcesApi.ORIGIN)
                    cfWeb.reset()
                    cfHandle = null
                }
            }
            auth.setAccount(platform.id, null)
            showMessage("Signed out of ${platform.title}")
            refreshSettings()
        }
        override fun cycleCfLanguage() {
            val all = CodeforcesApi.Language.entries
            prefs.cfLanguage = all[(CodeforcesApi.Language.byName(prefs.cfLanguage).ordinal + 1) % all.size].name
            refreshSettings()
        }
        override fun refreshList() = this@MainActivity.refreshList()
        override fun clearSaved() {
            lifecycleScope.launch {
                repo.clearSaved()
                list.saved = repo.savedSlugs()
                showMessage("Saved statements and discussions cleared. Drafts kept.")
                refreshSettings()
            }
        }
        override fun setIndent(width: Int) {
            prefs.indentWidth = width
            editor.actions.indentWidth = width
            refreshSettings()
        }
        override fun changeFont(delta: Int) = this@MainActivity.changeFont(delta)
        override fun changeReading(delta: Int) = this@MainActivity.changeReading(delta)
        override fun toggleWrap() = onSoftWrapChanged(editor.actions.toggleSoftWrap())
        override fun changeSwipe(charDelta: Int, lineDelta: Int) {
            prefs.pxPerChar = (prefs.pxPerChar + charDelta).coerceIn(Prefs.MIN_SWIPE_PX, Prefs.MAX_SWIPE_PX)
            prefs.pxPerLine = (prefs.pxPerLine + lineDelta).coerceIn(Prefs.MIN_SWIPE_PX, Prefs.MAX_SWIPE_PX)
            gestures.config = gestureConfig()
            refreshSettings()
        }
        override fun setSwipeHaptics(label: String) {
            val level = dev.titan.code.input.SwipeHaptics.Level.entries.first { it.label == label }
            prefs.swipeHaptics = level.name
            haptics.level = level
            if (level != dev.titan.code.input.SwipeHaptics.Level.OFF) haptics.lineEdge() // a sample
            refreshSettings()
        }
        override fun setSwipeAccel(level: String) {
            prefs.swipeAccel = level
            gestures.config = gestureConfig()
            refreshSettings()
        }
        override fun changePrefetch(delta: Int) {
            prefs.prefetchCount = (prefs.prefetchCount + delta).coerceIn(0, 20)
            refreshSettings()
        }
        override fun openSetup() { setupOpen = true }
        override fun restoreVersion(v: DraftVersionRow) {
            val p = problem ?: return
            if (v.slug != p.slug) return
            repo.addVersion(p.slug, editor.text.toString(), "before restore")
            editor.replaceAll(v.text, 0, 0)
            saveDraft()
            settingsOpen = false
            showPane(Pane.CODE)
            showMessage("Restored the ${v.reason} version; sym+U undoes")
        }
        override fun addPlanPack(slug: String) {
            lifecycleScope.launch {
                try {
                    val id = repo.createPlanPack(slug)
                    Sync.downloadPack(this@MainActivity, id, prefs.packsOnMobile)
                    showMessage("Pack added; downloading in the background")
                } catch (e: Exception) {
                    showMessage(netError(e))
                }
                refreshSettings()
            }
        }
        override fun addCollectionPack(id: String) {
            val c = list.collections.firstOrNull { it.id == id } ?: return
            lifecycleScope.launch {
                val packId = repo.createPack(c.title, "list:${c.id}", c.slugs)
                Sync.downloadPack(this@MainActivity, packId, prefs.packsOnMobile)
                showMessage("Pack added: ${c.title}; downloading in the background")
                refreshSettings()
            }
        }
        override fun addListPack() {
            val slugs = list.shown.map { it.slug }
            if (slugs.isEmpty()) return
            lifecycleScope.launch {
                val id = repo.createPack("List: ${listDescription()}", "list", slugs)
                Sync.downloadPack(this@MainActivity, id, prefs.packsOnMobile)
                showMessage("Pack added: ${slugs.size} problems; downloading in the background")
                refreshSettings()
            }
        }
        override fun downloadEverything(platform: Platform) {
            if (everythingJob?.isActive == true) { showMessage("Already preparing a download; one moment"); return }
            askForNotifications()
            everythingJob = lifecycleScope.launch {
                try {
                    showMessage("Updating the ${platform.title} problem list…")
                    val (id, n) = repo.createEverythingPack(platform) { done, total ->
                        showMessage("Updating the ${platform.title} problem list: $done / $total")
                    }
                    if (list.platform == platform) reloadList()
                    Sync.downloadPack(this@MainActivity, id, prefs.packsOnMobile)
                    showMessage("Saving ${"%,d".format(n)} ${platform.title} problems in the background; already-saved ones are skipped")
                } catch (e: Exception) {
                    TLog.w("everything ${platform.id}: $e")
                    showMessage(netError(e, platform))
                }
                refreshSettings()
            }
        }
        override fun deletePack(id: Long) {
            Sync.cancelPack(this@MainActivity, id)
            lifecycleScope.launch {
                repo.deletePack(id)
                list.saved = repo.savedSlugs()
                showMessage("Pack removed. Drafts kept.")
                refreshSettings()
            }
        }
        override fun togglePacksOnMobile() {
            prefs.packsOnMobile = !prefs.packsOnMobile
            Sync.scheduleWeeklyRefresh(this@MainActivity, prefs.packsOnMobile)
            // Re-queue unfinished packs with the new network rule.
            lifecycleScope.launch {
                repo.packs().filter { it.saved < it.slugs.size }.forEach {
                    Sync.cancelPack(this@MainActivity, it.id)
                    Sync.downloadPack(this@MainActivity, it.id, prefs.packsOnMobile)
                }
                refreshSettings()
            }
        }
        override fun setPracticeTimer(on: Boolean) {
            prefs.practiceTimer = on
            showTimer()
            refreshSettings()
        }
        override fun resetTimer() {
            timer.reset()
            persistTimer()
            showTimer()
            refreshSettings()
        }
        override fun close() { settingsOpen = false }
    }

    private fun finishSetup() {
        prefs.onboardingDone = true
        setupOpen = false
        if (list.all.size <= library.all.size) refreshList()
    }

    /** The Unihertz PTT app setting, if this app may read it (PLAN §5.7). */
    private fun pttApp(): String? = runCatching { Settings.System.getString(contentResolver, "func1_smartkey_ptt_app_picker") }.getOrNull()

    private var finishingLogin = false

    private fun onLoginSession() {
        if (finishingLogin) return
        finishingLogin = true
        lifecycleScope.launch {
            try {
                if (!auth.saveFromWebView()) return@launch
                loginFor = null
                val name = runCatching { repo.username() }.getOrNull()
                if (name == null) {
                    auth.clear()
                    showMessage("Sign-in didn't work. Try again.")
                } else {
                    auth.setUsername(name)
                    webFetch.reset()
                    username = name
                    showMessage("Signed in as $name. Updating solved marks…")
                    refreshList(quiet = true)
                }
                refreshSettings()
            } finally {
                finishingLogin = false
            }
        }
    }

    /** NeetCode or Codeforces sign-in finished (rev 13). */
    private fun onPlatformLogin(platform: Platform, account: String, secret: String?) {
        loginFor = null
        when (platform) {
            Platform.NEETCODE -> {
                if (secret == null) return
                nc.signIn(secret)
                ncAccount = account
            }
            Platform.CODEFORCES -> {
                cfHandle = account
                cfWeb.reset()
            }
            Platform.LEETCODE -> return
        }
        auth.setAccount(platform.id, account)
        showMessage("Signed in to ${platform.title} as $account")
        refreshSettings()
    }

    // ---- input interception (PLAN §3) -------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (loginOpen) {
            // The sign-in page is a normal web form: keys go to it through the IME.
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) loginFor = null
                return true
            }
            if (event.keyCode == InputRouter.KEYCODE_RED_BUTTON) return true
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == InputRouter.KEYCODE_RED_BUTTON) {
            if (event.action == KeyEvent.ACTION_UP) {
                TLog.d("key RED_BUTTON")
                onRedButton()
            }
            return true
        }
        if (pane != Pane.CODE || blockingOverlay()) {
            if (!keySink.hasFocus()) keySink.requestFocus()
            if (router.onShellKey(event)) return true
        } else if (!editor.hasFocus()) {
            // The editor's pre-IME hook normally takes every key. If it lost focus, route here.
            editor.requestFocus()
            if (router.onEditorKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Keyboard-surface touches (device `touchPad`) are screen-coordinate touch events and must
     * never reach a view (PLAN §5.6). Code: cursor. Problem: scroll the statement 1:1, or move the
     * list highlight. Comments: scroll. Elsewhere they're swallowed.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.device?.name != TOUCHPAD_DEVICE) return super.dispatchTouchEvent(ev)
        if (loginOpen || blockingOverlay()) return true
        val shift = (ev.metaState and KeyEvent.META_SHIFT_ON) != 0
        val action = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> GestureEngine.Action.DOWN
            MotionEvent.ACTION_MOVE -> GestureEngine.Action.MOVE
            MotionEvent.ACTION_UP -> GestureEngine.Action.UP
            MotionEvent.ACTION_CANCEL -> GestureEngine.Action.CANCEL
            else -> return true
        }
        when (pane) {
            Pane.CODE -> {
                if (action == GestureEngine.Action.MOVE) {
                    for (i in 0 until ev.historySize) {
                        gestures.onSample(action, ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i), shift)
                    }
                }
                gestures.onSample(action, ev.x, ev.y, ev.eventTime, shift)
            }
            Pane.PROBLEM -> {
                if (action == GestureEngine.Action.DOWN) listAccumulator = 0f
                (if (listMode) listScroller else problemScroller).onSample(action, ev.x, ev.y)
            }
            Pane.RESULTS -> resultsScroller.onSample(action, ev.x, ev.y)
            Pane.COMMENTS -> {
                if (action == GestureEngine.Action.DOWN) listAccumulator = 0f
                (if (solutionListShowing()) listScroller else commentsScroller).onSample(action, ev.x, ev.y)
            }
            else -> Unit
        }
        return true
    }

    private inner class EditorGestureSink : GestureEngine.Sink {
        private var mark: EditorActions.Mark? = null
        override fun beginStroke() { mark = editor.actions.saveSelection() }
        /** A sideways stroke already bumped into the line's edge (one click until it moves again). */
        private var atEdge = false
        override fun move(direction: EditorActions.Direction, steps: Int, extend: Boolean) {
            val before = editor.actions.saveSelection()
            repeat(steps) { editor.actions.move(direction, extend, withinLine = true) }
            val after = editor.actions.saveSelection()
            val moved = before.startLine != after.startLine || before.startColumn != after.startColumn ||
                before.endLine != after.endLine || before.endColumn != after.endColumn
            val vertical = direction == EditorActions.Direction.UP || direction == EditorActions.Direction.DOWN
            when {
                moved -> { atEdge = false; if (vertical) haptics.line() else haptics.character() }
                !vertical && !atEdge -> { atEdge = true; haptics.lineEdge() }
            }
        }
        override fun abortStroke() {
            mark?.let { editor.actions.restoreSelection(it) }
            TLog.d("touchPad stroke undone")
        }
        override fun strokeSummary(summary: String) {
            TLog.d(summary)
        }
    }

    private fun updatePosition() {
        val c = editor.cursor
        position = "Ln ${c.rightLine + 1} Col ${c.rightColumn + 1}" + if (c.isSelected) " · ⇧sel" else ""
    }

    // ---- UI ---------------------------------------------------------------------------------

    @Composable
    private fun Screen() {
        val density = LocalDensity.current
        val bandHeight = with(density) { cutoutPx.bottom.toDp() }.coerceAtLeast(48.dp)
        val bandStart = with(density) { cutoutPx.right.toDp() }
        val mono = FontFamily(Font(R.font.jetbrains_mono_nl_regular))

        Column(Modifier.fillMaxSize().background(Colors.background).navigationBarsPadding()) {
            TabBar(Modifier.height(bandHeight).padding(start = bandStart + 4.dp, end = CORNER_CLEARANCE))
            TitleLine(mono)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Colors.divider))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    if (pane == Pane.PROBLEM && !listMode) ReadingScale(readingZoom / 100f) { StatementBar() }
                    if (pane == Pane.COMMENTS) ReadingScale(readingZoom / 100f) { CommentsBar() }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { editor.also { it.post { it.requestFocus() } } },
                            update = {
                                it.visibility = if (pane == Pane.CODE) View.VISIBLE else View.GONE
                                if (pane == Pane.CODE && !it.hasFocus() && !loginOpen) it.post { it.requestFocus() }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        AndroidView(
                            factory = { problemView },
                            update = { it.visibility = if (pane == Pane.PROBLEM && !listMode) View.VISIBLE else View.GONE },
                            modifier = Modifier.fillMaxSize(),
                        )
                        AndroidView(
                            factory = { commentsView },
                            update = { it.visibility = if (pane == Pane.COMMENTS && !solutionListShowing()) View.VISIBLE else View.GONE },
                            modifier = Modifier.fillMaxSize(),
                        )
                        AndroidView(factory = { keySink }, modifier = Modifier.width(1.dp).height(1.dp))
                        if (pane == Pane.PROBLEM && listMode) {
                            ProblemList(list, mono, online, onOpen = { openProblem(it) }, onRefresh = { refreshList() }, onPlatform = ::switchPlatform, onRandom = ::openRandomProblem, textScale = readingZoom / 100f)
                        }
                        if (solutionListShowing()) {
                            SolutionList(solutions, solutionHighlight, discussionStatus, mono, ::openSolutionPost, readingZoom / 100f)
                        }
                        if (pane == Pane.RESULTS) {
                            ResultsPane(problem?.platform ?: Platform.LEETCODE, outcome, testCases, history, resultsScroll, mono,
                                ::deleteTestCase, ::addCaseFromClipboard, ::goToLine, ::openUrl, ::stressTest, ::saveStressCase, ::editCase)
                        }
                    }
                }
                if (cheatOpen) CheatSheet(mono) { cheatOpen = false }
                if (settingsOpen) settingsState?.let { SettingsScreen(it, settingsActions, mono) }
                if (setupOpen) {
                    SetupScreen(pttApp(), username, list.all.size, online, list.status,
                        onSignIn = { loginFor = Platform.LEETCODE }, onDownload = { refreshList() }, onDone = ::finishSetup)
                }
                when (val lf = loginFor) {
                    null -> Unit
                    Platform.LEETCODE -> LoginScreen(LeetCodeApi.LOGIN_URL, ::onLoginSession) { loginFor = null }
                    else -> PlatformLoginScreen(lf, { account, secret -> onPlatformLogin(lf, account, secret) }) { loginFor = null }
                }
            }
            MessageStrip(mono)
            if (paletteOpen && pane == Pane.CODE) PaletteStrip(mono, ::onPaletteSymbol)
            else if (completions.isNotEmpty() && pane == Pane.CODE && !blockingOverlay()) CompletionStrip(mono, completions) { acceptCompletion(it) }
            StatusLine(mono)
        }
    }

    @Composable
    private fun TabBar(modifier: Modifier) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pane.entries.forEach { p ->
                val selected = p == pane
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { settingsOpen = false; showPane(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        p.title + if (p == Pane.RESULTS) outcome?.badge?.let { " $it" }.orEmpty() else "",
                        color = if (selected) Colors.text else Colors.dim,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    if (selected) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                                .width(28.dp).height(3.dp).background(Colors.accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            Box(
                Modifier.width(44.dp).fillMaxHeight().clickable { if (settingsOpen) settingsOpen = false else openSettings() },
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = if (settingsOpen) Colors.accent else Colors.dim, fontSize = 18.sp) }
        }
    }

    @Composable
    private fun TitleLine(mono: FontFamily) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val p = problem
            Text(
                if (p != null) listOfNotNull(p.platform.title.takeIf { p.platform != Platform.LEETCODE }, p.label, p.difficulty.ifEmpty { null }).joinToString(" · ")
                else "No problem open",
                color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            draftNote?.let {
                Text("● $it", color = if (it == SAVE_FAILED) Colors.bad else Colors.accent, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
            }
            // Only shown when it matters: offline (PLAN §8.1; quiet like the save state, rev 9).
            if (!online) Text("  ⦿ offline", color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
        }
    }

    /** Above the statement: back to the list. */
    @Composable
    private fun StatementBar() {
        Row(
            Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallButton("☰ Problems  sym+P · Back") { openList() }
        }
    }

    @Composable
    private fun CommentsBar() {
        Row(
            Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val platform = problem?.platform ?: Platform.LEETCODE
            if (openSolution != null && commentsTab == CommentsTab.SOLUTIONS) {
                // Rev 35: a Codeforces editorial has no list to go back to.
                if (platform == Platform.CODEFORCES) SmallButton("Editorial", selected = true) {}
                else SmallButton(if (platform == Platform.LEETCODE) "← Solutions · Back" else "← Back") { closePost() }
                Spacer(Modifier.weight(1f))
                SmallButton("⟳") { refreshPost() }
                return@Row
            }
            SmallButton(when (platform) { Platform.NEETCODE -> "NeetCode solution"; Platform.CODEFORCES -> "Editorial"; else -> "Solutions" }, selected = commentsTab == CommentsTab.SOLUTIONS) { commentsTab = CommentsTab.SOLUTIONS }
            if (platform.comments) SmallButton("Discussion", selected = commentsTab == CommentsTab.DISCUSSION) {
                commentsTab = CommentsTab.DISCUSSION
                commentsView.scrollTo(0, 0)
                renderComments()
            }
            Spacer(Modifier.weight(1f))
            SmallButton("⟳") { problem?.let { if (online) loadDiscussion(it.slug, force = true) else showMessage("Offline") } }
        }
    }

    @Composable
    private fun SmallButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
        Box(
            Modifier.height(30.dp).border(1.dp, if (selected) Colors.accent else Colors.divider, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = if (selected) Colors.text else Colors.dim, fontSize = 12.sp) }
    }

    @Composable
    private fun MessageStrip(mono: FontFamily) {
        val m = message ?: return
        LaunchedEffect(messageSeq) {
            delay(2500)
            message = null
        }
        Text(
            m, color = Colors.text, fontSize = 13.sp, fontFamily = mono,
            modifier = Modifier.fillMaxWidth().background(Colors.messageBg).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun StatusLine(mono: FontFamily) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).background(Colors.bar)
                .padding(start = 12.dp, end = CORNER_CLEARANCE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(position, color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
            timerText?.let { Text(it, color = Colors.dim, fontSize = 12.sp, fontFamily = mono, maxLines = 1) }
            if (symHeld) {
                Text(
                    "sym", color = Colors.background, fontSize = 12.sp, fontFamily = mono, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Colors.accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            BarButton(
                "▶ Run",
                Modifier.combinedClickable(
                    onClick = { onLeader(Leader.RUN_LOCAL) },
                    onLongClick = { onLeader(Leader.RUN_REMOTE) },
                ),
            )
            BarButton(if (submitConfirm) "Confirm?" else "Submit", Modifier.clickable { onLeader(Leader.SUBMIT) })
        }
    }

    @Composable
    private fun BarButton(label: String, modifier: Modifier) {
        Box(
            Modifier.height(32.dp).border(1.dp, Colors.divider, RoundedCornerShape(6.dp)).then(modifier)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Colors.text, fontSize = 13.sp)
        }
    }

    private companion object {
        const val TOUCHPAD_DEVICE = "touchPad"
        const val REQUEST_NOTIFICATIONS = 7
        /** Keeps controls at least 30 px clear of the 100 px rounded screen corners (PLAN §4.1). */
        val CORNER_CLEARANCE = 32.dp
        const val AUTOSAVE_DEBOUNCE_MS = 1000L
        /** How long to wait for Jedi before showing the word list (rev 38). */
        const val JEDI_WAIT_MS = 700L
        const val RESET_CONFIRM_MS = 3000L
        const val VERSION_EVERY_MS = 5 * 60 * 1000L
        /** Local run time limit; the phone is slower than LeetCode's judge (agent decision, rev 10). */
        /** List rows are taller than code lines; a slightly shorter swipe moves one row. */
        const val LIST_SWIPE_FACTOR = 0.8f
        const val SAVE_FAILED = "save failed"
        const val OFFLINE_SUBMIT = "Offline — your draft is saved. Submit when you're back online."
    }
}
