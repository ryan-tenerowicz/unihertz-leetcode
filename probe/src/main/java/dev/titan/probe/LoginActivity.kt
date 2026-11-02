package dev.titan.probe

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * C01: sign in on LeetCode's own login page (username + password only), then compare the same
 * GraphQL request sent three ways: OkHttp default UA, OkHttp with the WebView's UA + cookies,
 * and fetch() inside the logged-in WebView. Cookie VALUES are never logged — only their names.
 */
class LoginActivity : ProbeActivity() {
    override val logInput: Boolean get() = false

    private lateinit var web: WebView
    private lateinit var status: TextView
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val query = """{"query":"query globalData { userStatus { username isSignedIn } }"}"""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.setBackgroundColor(0xFF000000.toInt())
        status = label("Sign in with your LeetCode username and password, then tap Run checks.", 12f, mono = true)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "ProbeBridge")
        root.add(row(button("Run checks") { runChecks() }, button("Close") { finish() }))
        root.add(status)
        root.add(web, 0, 1f)
        setContentView(root)
        web.loadUrl("https://leetcode.com/accounts/login/")
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun show(msg: String) {
        runOnUiThread { status.text = status.text.toString() + "\n" + msg }
        EventLog.noteAsync("net", msg)
    }

    private fun runChecks() {
        status.text = "Running checks…"
        val cm = CookieManager.getInstance()
        cm.flush()
        val cookies = cm.getCookie("https://leetcode.com") ?: ""
        val names = cookies.split(";").map { it.trim().substringBefore("=") }.filter { it.isNotEmpty() }
        val csrf = cookies.split(";").map { it.trim() }.firstOrNull { it.startsWith("csrftoken=") }?.substringAfter("=") ?: ""
        val ua = web.settings.userAgentString
        show("cookies present: ${names.joinToString(",")}")
        show("has LEETCODE_SESSION=${"LEETCODE_SESSION" in names} csrftoken=${"csrftoken" in names} cf_clearance=${"cf_clearance" in names}")
        show("page: ${web.url}")

        Thread {
            okhttp("okhttp-default-UA", cookies, csrf, null)
            okhttp("okhttp-webview-UA", cookies, csrf, ua)
        }.start()

        if (web.url?.contains("leetcode.com") == true) {
            val js = """
                (function(){
                  var m = document.cookie.match(/csrftoken=([^;]+)/);
                  var csrf = m ? m[1] : '';
                  fetch('/graphql/', {method:'POST', credentials:'include',
                    headers:{'Content-Type':'application/json','x-csrftoken':csrf},
                    body: JSON.stringify({query:'query globalData { userStatus { username isSignedIn } }'})})
                  .then(function(r){ return r.text().then(function(t){ ProbeBridge.result('webview-fetch', r.status, t.slice(0,300)); }); })
                  .catch(function(e){ ProbeBridge.result('webview-fetch', -1, String(e)); });
                })();
            """.trimIndent()
            web.evaluateJavascript(js, null)
        } else {
            show("webview-fetch: skipped (page is not on leetcode.com)")
        }
    }

    private fun okhttp(name: String, cookies: String, csrf: String, ua: String?) {
        try {
            val b = Request.Builder()
                .url("https://leetcode.com/graphql/")
                .post(query.toRequestBody("application/json".toMediaType()))
                .header("Cookie", cookies)
                .header("x-csrftoken", csrf)
                .header("Referer", "https://leetcode.com/")
                .header("Origin", "https://leetcode.com")
            if (ua != null) b.header("User-Agent", ua)
            client.newCall(b.build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                report(name, r.code, body, r.header("cf-mitigated"))
            }
        } catch (e: Exception) {
            show("$name: ERROR ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun report(name: String, code: Int, body: String, cfMitigated: String?) {
        val challenge = cfMitigated != null || body.contains("Just a moment") || body.contains("cf-chl")
        val signedIn = try {
            JSONObject(body).optJSONObject("data")?.optJSONObject("userStatus")?.optBoolean("isSignedIn")
        } catch (e: Exception) { null }
        show("$name: HTTP $code challenge=$challenge signedIn=$signedIn body=${body.take(120).replace("\n", " ")}")
    }

    inner class Bridge {
        @JavascriptInterface
        fun result(name: String, status: Int, body: String) {
            report(name, status, body, null)
        }
    }
}
