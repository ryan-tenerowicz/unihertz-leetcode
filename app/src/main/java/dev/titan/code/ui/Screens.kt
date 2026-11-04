package dev.titan.code.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.titan.code.data.Repository
import dev.titan.code.data.db.DraftVersionRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Settings screen shows (PLAN §3 SettingsScreen, §4.2, §8.4). */
data class SettingsState(
    val username: String?,
    val online: Boolean,
    val listSize: Int,
    val listUpdatedAt: Long,
    val storage: Repository.Storage?,
    val indentWidth: Int,
    val fontSize: Int,
    /** Rev 27: the Problem and Comments panes' text size, in percent. */
    val readingZoom: Int = 100,
    val softWrap: Boolean,
    val pxPerChar: Int,
    val swipeAccel: String = "normal",
    val swipeHaptics: String = "Normal",
    val pxPerLine: Int,
    val prefetch: Int,
    val problemLabel: String?,
    val versions: List<DraftVersionRow>,
    val packs: List<PackView> = emptyList(),
    /** Study plans not yet added: (slug, name). */
    val plans: List<Pair<String, String>> = emptyList(),
    /** Curated lists not yet added as packs: (id, title, size). */
    val collections: List<Triple<String, String, Int>> = emptyList(),
    /** What the problem list shows now, for "save this list": (description, count). */
    val listView: Pair<String, Int> = "" to 0,
    val packsOnMobile: Boolean = false,
    /** Rev 13: the NeetCode and Codeforces accounts (null: signed out). */
    val neetcode: String? = null,
    val codeforces: String? = null,
    val cfLanguage: String = "",
    /** Which platform's list the Problem pane shows, for the list rows below. */
    val listPlatform: String = "LeetCode",
    /** Rev 24: the practice timer and its reading on this problem. */
    val practiceTimer: Boolean = true,
    val timerReading: String? = null,
    /** Rev 25: "Everything on …", one row per site. */
    val everything: List<EverythingView> = emptyList(),
)

/** One "Everything on …" row: how many problems it would save, and whether its pack exists. */
data class EverythingView(val platform: dev.titan.code.data.Platform, val free: Int, val added: Boolean)

/** One offline pack row: progress comes from the database and from WorkManager. */
data class PackView(val id: Long, val title: String, val total: Int, val saved: Int, val work: String?, val everything: Boolean = false)

interface SettingsActions {
    fun signIn()
    fun signOut()
    fun signInTo(platform: dev.titan.code.data.Platform)
    fun signOutOf(platform: dev.titan.code.data.Platform)
    fun cycleCfLanguage()
    fun refreshList()
    fun clearSaved()
    fun setIndent(width: Int)
    fun changeFont(delta: Int)
    fun changeReading(delta: Int) {}
    fun toggleWrap()
    fun changeSwipe(charDelta: Int, lineDelta: Int)
    fun setSwipeAccel(level: String)
    fun setSwipeHaptics(label: String)
    fun changePrefetch(delta: Int)
    fun openSetup()
    fun restoreVersion(v: DraftVersionRow)
    fun addPlanPack(slug: String)
    fun addCollectionPack(id: String)
    fun addListPack()
    fun deletePack(id: Long)
    fun togglePacksOnMobile()
    fun downloadEverything(platform: dev.titan.code.data.Platform) {}
    fun setPracticeTimer(on: Boolean) {}
    fun resetTimer() {}
    fun close()
}

@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions, mono: FontFamily) {
    Column(
        Modifier.fillMaxSize().background(Colors.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button("Close", onClick = a::close)
        }

        Section("Accounts")
        Hint("Reading problems works without an account. Signing in lets you run and submit there.")
        if (s.username != null) {
            Line("LeetCode: ${s.username}") { Button("Sign out", onClick = a::signOut) }
        } else {
            Line("LeetCode: not signed in. Adds Run/Submit and solved marks. Username and password.") {
                Button("Sign in", enabled = s.online, onClick = a::signIn)
            }
        }
        val nc = dev.titan.code.data.Platform.NEETCODE
        if (s.neetcode != null) {
            Line("NeetCode: ${s.neetcode}") { Button("Sign out") { a.signOutOf(nc) } }
        } else {
            Line("NeetCode: not signed in. Uses GitHub or Google on NeetCode's page.") { Button("Sign in", enabled = s.online) { a.signInTo(nc) } }
        }
        val cf = dev.titan.code.data.Platform.CODEFORCES
        if (s.codeforces != null) {
            Line("Codeforces: ${s.codeforces}") { Button("Sign out") { a.signOutOf(cf) } }
        } else {
            Line("Codeforces: not signed in. Handle or email and password.") { Button("Sign in", enabled = s.online) { a.signInTo(cf) } }
        }
        Line("Codeforces submit language") { Button(s.cfLanguage, onClick = a::cycleCfLanguage) }

        Section("Problems and offline storage")
        Line("${s.listPlatform}: ${s.listSize} problems in the list" + if (s.listUpdatedAt > 0) ", updated ${ago(s.listUpdatedAt)}" else "") {
            Button("Refresh", enabled = s.online, onClick = a::refreshList)
        }
        s.storage?.let { st ->
            Line("Saved for offline: ${st.savedStatements} statements, ${st.savedDiscussions} discussions · ${st.bytes / 1024} KB") {
                Button("Clear", onClick = a::clearSaved)
            }
            Hint("Clearing never removes drafts or their history.")
        }
        Line("When a problem opens, also save the next ${s.prefetch} in the list") {
            Stepper(mono, s.prefetch.toString(), { a.changePrefetch(-1) }, { a.changePrefetch(+1) })
        }

        Section("Offline packs")
        Hint("A pack saves statements, discussions and solution lists in the background, Wi-Fi only unless allowed below. Interrupted downloads resume.")
        s.everything.forEach { e ->
            val free = if (e.platform == dev.titan.code.data.Platform.CODEFORCES) "" else "free " // Codeforces has no paid problems
            val n = if (e.free > 0) "%,d ${free}problems".format(e.free) else "the list downloads first"
            Line("Everything on ${e.platform.title} ($n, ${everythingEstimate(e.platform, e.free)})") {
                Button(if (e.added) "Update" else "Download", enabled = s.online) { a.downloadEverything(e.platform) }
            }
        }
        if (s.everything.isNotEmpty()) {
            Hint("Statements first, then discussions. Premium and Pro problems are skipped. A notification shows progress and has Stop; " +
                "Android allows such downloads about 6 hours a day, then it carries on the next day. Update adds problems published since.")
        }
        s.packs.forEach { p ->
            Line("${p.title} · ${p.saved}/${p.total} saved" + (p.work?.let { " · $it" } ?: "")) {
                Button(if (p.everything) "Stop" else "Remove") { a.deletePack(p.id) }
            }
        }
        s.plans.forEach { (slug, name) ->
            Line("$name") { Button("＋ Add", enabled = s.online) { a.addPlanPack(slug) } }
        }
        s.collections.forEach { (id, title, n) ->
            Line("$title ($n problems, about ${estimate(n)})") { Button("＋ Add", enabled = s.online) { a.addCollectionPack(id) } }
        }
        val (desc, count) = s.listView
        if (count > 0) {
            Line("The problem list as filtered now: $desc ($count problems, about ${estimate(count)})") {
                Button("＋ Add", enabled = s.online) { a.addListPack() }
            }
        }
        Line("Download packs on mobile data") {
            Button(if (s.packsOnMobile) "Allowed" else "Wi-Fi only", selected = s.packsOnMobile, onClick = a::togglePacksOnMobile)
        }
        Hint("Removing a pack deletes its saved statements and discussions (not ones another pack needs). Drafts are never removed.")

        Section("Editor")
        Line("Indent width") {
            Button("2", selected = s.indentWidth == 2) { a.setIndent(2) }
            Button("4", selected = s.indentWidth == 4) { a.setIndent(4) }
        }
        Line("Editor font size (sym+I / sym+O on the Code pane)") { Stepper(mono, "${s.fontSize} sp", { a.changeFont(-1) }, { a.changeFont(+1) }) }
        Line("Problem and Comments text size (sym+I / sym+O on those panes)") {
            Stepper(mono, "${s.readingZoom}%", { a.changeReading(-1) }, { a.changeReading(+1) })
        }
        Line("Soft wrap (sym+W)") { Button(if (s.softWrap) "On" else "Off", selected = s.softWrap, onClick = a::toggleWrap) }

        Section("Keyboard surface")
        Line("Sideways swipe: px per character (more = slower)") {
            Stepper(mono, s.pxPerChar.toString(), { a.changeSwipe(-10, 0) }, { a.changeSwipe(+10, 0) })
        }
        Line("Sideways speed-up for fast swipes (slow moves are never sped up)") {
            Button("Off", selected = s.swipeAccel == "off") { a.setSwipeAccel("off") }
            Button("Gentle", selected = s.swipeAccel == "gentle") { a.setSwipeAccel("gentle") }
            Button("Normal", selected = s.swipeAccel == "normal") { a.setSwipeAccel("normal") }
        }
        Line("Vibration per step (firmer when moving slowly)") {
            for (l in listOf("Off", "Normal", "Strong")) Button(l, selected = s.swipeHaptics == l) { a.setSwipeHaptics(l) }
        }
        Line("Up/down swipe: px per line (more = slower)") {
            Stepper(mono, s.pxPerLine.toString(), { a.changeSwipe(0, -10) }, { a.changeSwipe(0, +10) })
        }

        Section("Practice")
        Line("Timer in the status line (starts on your first edit)") {
            Button("Off", selected = !s.practiceTimer) { a.setPracticeTimer(false) }
            Button("On", selected = s.practiceTimer) { a.setPracticeTimer(true) }
        }
        if (s.problemLabel != null && s.timerReading != null) {
            Line("This problem: ${s.timerReading}") { Button("Reset") { a.resetTimer() } }
        }

        if (s.problemLabel != null) {
            Section("Draft history: ${s.problemLabel}")
            if (s.versions.isEmpty()) Hint("No earlier versions yet. One is kept on every reset (sym+N) and every 5 minutes of editing.")
            s.versions.forEach { v ->
                Line("${stamp(v.savedAt)} · ${v.reason} · ${v.text.lines().count()} lines") {
                    Button("Restore") { a.restoreVersion(v) }
                }
            }
            if (s.versions.isNotEmpty()) Hint("Restore replaces the editor text as one step; sym+U undoes it.")
        }

        Section("Setup")
        Line("Phone settings checklist") { Button("Open", onClick = a::openSetup) }
        Hint("Back closes Settings.")
    }
}

/** First-run checklist (PLAN §3 OnboardingScreen, §5.7). */
@Composable
fun SetupScreen(
    pttApp: String?,
    username: String?,
    listSize: Int,
    online: Boolean,
    listStatus: String?,
    onSignIn: () -> Unit,
    onDownload: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Colors.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Set up Coding", color = Colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Step(
            "Scroll Assistant on",
            null,
            "Quick settings → Scroll Assistant tile. Keyboard-surface swipes only reach the app with it on. (The app can't read this setting.)",
        )
        Step(
            "Red button opens Coding",
            when (pttApp) { "dev.titan.code" -> true; null -> null; else -> false },
            "Settings → Shortcut keys → PTT key → PTT app = Coding. The red button then switches Problem ↔ Code." +
                if (pttApp == null) " (Couldn't read the setting; check it by hand.)" else "",
        )
        Step("Sign in to LeetCode (optional)", username != null, if (username != null) "Signed in as $username." else "Adds Run/Submit and solved marks. Username and password only.") {
            if (username == null) Button("Sign in", enabled = online, onClick = onSignIn)
        }
        Step("Download the problem list", listSize > 100, listStatus ?: if (listSize > 100) "$listSize problems saved." else "About 40 small requests. Statements save as you open them.") {
            Button("Download", enabled = online, onClick = onDownload)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button("Done", onClick = onDone) }
        Hint("Settings (⚙ in the tab bar) can reopen this list.")
    }
}

/**
 * LeetCode's own login page in a WebView (PLAN §6.2): username and password only. Once a
 * `LEETCODE_SESSION` cookie appears and the page has left the login form, [onSession] runs.
 * Keys reach this WebView through the IME, so this is the one place the app accepts normal text input.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(loginUrl: String, onSession: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Colors.background)) {
        Row(Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sign in to LeetCode", color = Colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Button("Close", onClick = onClose)
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = check(url)
                        override fun onPageFinished(view: WebView, url: String) = check(url)
                        private fun check(url: String) {
                            val jar = CookieManager.getInstance().getCookie("https://leetcode.com").orEmpty()
                            if ("LEETCODE_SESSION=" in jar && "/accounts/login" !in url) onSession()
                        }
                    }
                    loadUrl(loginUrl)
                    requestFocus()
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---- small building blocks ------------------------------------------------------------------

@Composable
fun Button(label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).height(34.dp)
            .border(1.dp, if (selected) Colors.accent else Colors.divider, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Colors.text else Colors.dim, fontSize = 13.sp) }
}

@Composable
private fun Stepper(mono: FontFamily, value: String, minus: () -> Unit, plus: () -> Unit) {
    Button("−", onClick = minus)
    Text(value, color = Colors.text, fontSize = 13.sp, fontFamily = mono, maxLines = 1, modifier = Modifier.width(64.dp).padding(start = 6.dp))
    Button("+", onClick = plus)
}

@Composable
private fun Section(title: String) {
    Text(title, color = Colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Line(text: String, controls: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        controls()
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = Colors.dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))

@Composable
private fun Step(title: String, done: Boolean?, detail: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, Colors.divider, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (mark, color) = when (done) { true -> "✓" to Colors.good; false -> "✗" to Colors.bad; null -> "?" to Colors.dim }
        Text(mark, color = color, fontSize = 18.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Colors.text, fontSize = 14.sp)
            Text(detail, color = Colors.dim, fontSize = 12.sp)
        }
        action()
    }
}

/** About 60 KB per problem with its discussion; LeetCode posts vary a lot. */
/**
 * Rough size and time for "Everything on …" (rev 25). Measured per problem: LeetCode about
 * 1 s per statement and 5 s per discussion (about 9 requests); NeetCode about 1 s with its
 * article; Codeforces about 3 s per page in the hidden browser, with no discussion.
 */
private fun everythingEstimate(p: dev.titan.code.data.Platform, n: Int): String {
    if (n == 0) return "size unknown"
    val (kb, sec) = when (p) {
        dev.titan.code.data.Platform.LEETCODE -> 60 to 6
        dev.titan.code.data.Platform.NEETCODE -> 25 to 1
        dev.titan.code.data.Platform.CODEFORCES -> 15 to 3
    }
    val mb = n * kb / 1024
    val hours = n * sec / 3600.0
    val time = if (hours < 1) "about ${(hours * 60).toInt().coerceAtLeast(1)} min" else "about %.0f h".format(hours)
    return "about $mb MB, $time"
}

private fun estimate(count: Int): String {
    val mb = count * 60 / 1024.0
    return if (mb < 1) "${count * 60} KB" else "%.0f MB".format(mb)
}

private fun ago(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 48 * 60 -> "${min / 60} h ago"
        else -> "${min / 1440} days ago"
    }
}

private fun stamp(ms: Long) = if (ms <= 0) "from M1" else SimpleDateFormat("d MMM HH:mm", Locale.US).format(Date(ms))


/**
 * Sign-in for NeetCode and Codeforces (rev 13), each on its own site in a WebView.
 *
 * - **NeetCode** signs in with Google or GitHub through a Firebase popup, so the WebView allows
 *   popups (shown on top). Once signed in, the page's Firebase storage holds a refresh token;
 *   it's read (only while the page is on neetcode.io) and handed to [onDone] to be encrypted.
 * - **Codeforces** uses its own form at `/enter`. Signed in, the page header shows the handle,
 *   which goes to [onDone]; the session cookies stay in the WebView's cookie store.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun PlatformLoginScreen(platform: dev.titan.code.data.Platform, onDone: (account: String, secret: String?) -> Unit, onClose: () -> Unit) {
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    Column(Modifier.fillMaxSize().background(Colors.background)) {
        Row(Modifier.fillMaxWidth().background(Colors.bar).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (platform == dev.titan.code.data.Platform.NEETCODE) "Sign in to NeetCode (use GitHub or Google on the page)" else "Sign in to Codeforces",
                color = Colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2,
            )
            Button("Close", onClick = onClose)
        }
        AndroidView(
            factory = { ctx ->
                val root = android.widget.FrameLayout(ctx)
                var finished = false
                fun finish(account: String, secret: String?) {
                    if (finished) return
                    finished = true
                    main.post { onDone(account, secret) }
                }
                fun configure(w: WebView) {
                    w.settings.javaScriptEnabled = true
                    w.settings.domStorageEnabled = true
                    w.settings.setSupportMultipleWindows(true)
                    w.settings.javaScriptCanOpenWindowsAutomatically = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                }
                val web = WebView(ctx)
                configure(web)
                // Codeforces' login page is laid out for a desktop width: fit it to the screen.
                web.settings.useWideViewPort = true
                web.settings.loadWithOverviewMode = true
                web.settings.builtInZoomControls = true
                web.settings.displayZoomControls = false

                val poll = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val host = android.net.Uri.parse(web.url ?: "").host.orEmpty()
                        if (platform == dev.titan.code.data.Platform.NEETCODE && host.endsWith("neetcode.io")) web.evaluateJavascript(NEETCODE_TOKEN_JS, null)
                        main.postDelayed(this, 2000)
                    }
                }
                web.addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun found(json: String) {
                        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
                        val rt = o.optString("rt").takeIf { it.isNotEmpty() } ?: return
                        val name = o.optString("email").ifEmpty { o.optString("name") }.ifEmpty { "your account" }
                        finish(name, rt)
                    }
                }, "TitanLogin")
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (platform == dev.titan.code.data.Platform.CODEFORCES && url.startsWith("https://codeforces.com") && "/enter" !in url) {
                            view.evaluateJavascript(dev.titan.code.data.codeforces.CodeforcesWeb.HANDLE_JS) { r ->
                                val handle = runCatching { org.json.JSONTokener(r).nextValue() as? String }.getOrNull()
                                if (!handle.isNullOrBlank()) finish(handle, null)
                            }
                        }
                    }
                }
                // Popups (NeetCode's Firebase sign-in): a WebView on top, closed when the page closes it.
                web.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                        val child = WebView(ctx)
                        configure(child)
                        child.webViewClient = WebViewClient()
                        child.webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCloseWindow(window: WebView) {
                                root.removeView(window)
                                window.destroy()
                            }
                        }
                        root.addView(child, android.widget.FrameLayout.LayoutParams(-1, -1))
                        (resultMsg.obj as WebView.WebViewTransport).webView = child
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                root.addView(web, android.widget.FrameLayout.LayoutParams(-1, -1))
                web.loadUrl(
                    if (platform == dev.titan.code.data.Platform.NEETCODE) dev.titan.code.data.neetcode.NeetCodeApi.LOGIN_URL
                    else dev.titan.code.data.codeforces.CodeforcesApi.LOGIN_URL
                )
                main.postDelayed(poll, 2000)
                web.requestFocus()
                root
            },
            onRelease = { root ->
                for (i in 0 until root.childCount) (root.getChildAt(i) as? WebView)?.destroy()
                root.removeAllViews()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Reads the signed-in Firebase user from neetcode.io's storage (localStorage or IndexedDB) and
 * passes its refresh token to `TitanLogin.found`. It never creates the database if it's missing.
 */
private const val NEETCODE_TOKEN_JS = """
(function(){
  function send(u){ if (u && u.stsTokenManager && u.stsTokenManager.refreshToken) TitanLogin.found(JSON.stringify({rt:u.stsTokenManager.refreshToken,email:u.email||'',name:u.displayName||''})); }
  try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); if (k.indexOf('firebase:authUser:') == 0) { send(JSON.parse(localStorage.getItem(k))); return; } } } catch (e) {}
  try {
    var r = indexedDB.open('firebaseLocalStorageDb');
    r.onupgradeneeded = function(){ r.transaction.abort(); };
    r.onsuccess = function(){
      try {
        var g = r.result.transaction('firebaseLocalStorage','readonly').objectStore('firebaseLocalStorage').getAll();
        g.onsuccess = function(){ (g.result||[]).forEach(function(x){ send(x.value); }); r.result.close(); };
      } catch (e) { r.result.close(); }
    };
  } catch (e) {}
})();
"""
