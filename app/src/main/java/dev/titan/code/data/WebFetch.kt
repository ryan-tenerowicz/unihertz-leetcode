package dev.titan.code.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.titan.code.TLog
import dev.titan.code.data.leetcode.LeetCodeApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * PLAN §6.2 step 2: a hidden WebView on leetcode.com that repeats a request with JavaScript
 * `fetch()`, so it uses the browser's TLS stack and cookies (the same `CookieManager` the sign-in
 * page filled). The page it sits on is `robots.txt`: same origin, almost nothing to load. The
 * result comes back through a JavaScript bridge. Cookie values never leave the WebView here.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebFetch(context: Context) : BrowserFetch {
    private val app = context.applicationContext
    private var web: WebView? = null
    private var loaded = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String>>>()
    private val ids = AtomicInteger()

    override suspend fun fetch(req: WebRequest): Pair<Int, String> = withContext(Dispatchers.Main) {
        val w = ensure()
        withTimeout(20_000) { loaded.await() }
        val id = ids.incrementAndGet()
        val done = CompletableDeferred<Pair<Int, String>>()
        pending[id] = done
        val js = """
            (function(){
              var m = document.cookie.match(/csrftoken=([^;]+)/);
              var h = {'Content-Type':'application/json','x-csrftoken': m ? m[1] : '','X-Requested-With':'XMLHttpRequest'};
              var o = {method: ${JSONObject.quote(req.method)}, credentials: 'include', headers: h, referrer: ${JSONObject.quote(req.referer)}};
              ${if (req.body != null) "o.body = ${JSONObject.quote(req.body)};" else ""}
              fetch(${JSONObject.quote(req.url)}, o)
                .then(function(r){ return r.text().then(function(t){ TitanFetch.done($id, r.status, t); }); })
                .catch(function(e){ TitanFetch.done($id, -1, String(e)); });
            })();
        """.trimIndent()
        w.evaluateJavascript(js, null)
        try {
            withTimeout(45_000) { done.await() }
        } finally {
            pending.remove(id)
        }
    }

    private fun ensure(): WebView {
        web?.let { return it }
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        w.addJavascriptInterface(Bridge(), "TitanFetch")
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                TLog.d("webfetch: page ready")
                loaded.complete(Unit)
            }
        }
        w.loadUrl("${LeetCodeApi.ORIGIN}/robots.txt")
        web = w
        return w
    }

    /** Drop the page (after sign-in or sign-out) so the next fetch starts with fresh cookies. */
    fun reset() {
        web?.destroy()
        web = null
        loaded = CompletableDeferred()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun done(id: Int, status: Int, body: String) {
            pending[id]?.complete(status to body)
        }
    }
}
