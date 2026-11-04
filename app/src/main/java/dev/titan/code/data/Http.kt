package dev.titan.code.data

import dev.titan.code.TLog
import dev.titan.code.data.leetcode.LeetCodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cloudflare (or LeetCode) answered with a challenge instead of data (PLAN §6.2). */
class ChallengeException : IOException("LeetCode asked for a browser check")

/** LeetCode refused because the session isn't signed in (or has expired). */
class SignedOutException : IOException("Not signed in to LeetCode")

/** HTTP 429: LeetCode wants us to slow down. */
class RateLimitedException : IOException("LeetCode says: too many requests, wait a moment")

/** A request the browser fallback can repeat: method, absolute URL, JSON body, referer. */
data class WebRequest(val method: String, val url: String, val body: String?, val referer: String)

/** Sends a request from inside a logged-in WebView (PLAN §6.2 step 2). Returns (status, body). */
fun interface BrowserFetch {
    suspend fun fetch(req: WebRequest): Pair<Int, String>
}

/**
 * HTTP for leetcode.com (PLAN §6.2):
 * 1. OkHttp with the WebView's exact user agent and the saved cookies, plus `x-csrftoken`,
 *    `Referer` and `Origin`. Requests are spaced at least [minGapMs] apart.
 * 2. On a challenge (or a 403 on a POST), the same request is re-sent through [browser], a hidden
 *    WebView using the browser's own TLS stack and cookies. After two such failures in a row, the
 *    browser path is used first for the rest of the session.
 * 3. If the browser is challenged too, [ChallengeException] reaches the UI, which offers the
 *    sign-in page (a visible WebView) to pass the check.
 */
class LeetCodeHttp(
    private val cookies: () -> String?,
    private val userAgent: String,
    private val minGapMs: Long = 250,
) {
    /** Set by the activity once a WebView can exist. */
    var browser: BrowserFetch? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private var lastAt = 0L
    private var okHttpFailures = 0
    private var preferBrowser = false

    suspend fun graphql(body: String, referer: String = "${LeetCodeApi.ORIGIN}/problemset/"): String =
        send(WebRequest("POST", LeetCodeApi.GRAPHQL, body, referer))

    /**
     * Run, Submit and their `check` polls. Checked 2026-11-02: these REST endpoints answer a
     * non-browser client with a Cloudflare challenge, so they go through the browser first.
     */
    suspend fun post(path: String, body: String, referer: String): String =
        send(WebRequest("POST", LeetCodeApi.ORIGIN + path, body, referer), browserFirst = true)

    suspend fun get(path: String, referer: String): String =
        send(WebRequest("GET", LeetCodeApi.ORIGIN + path, null, referer), browserFirst = true)

    private suspend fun send(req: WebRequest, browserFirst: Boolean = false): String {
        space()
        val b = browser
        if ((preferBrowser || browserFirst) && b != null) return viaBrowser(b, req)
        return try {
            okHttp(req).also { okHttpFailures = 0 }
        } catch (e: ChallengeException) {
            if (b == null) throw e
            okHttpFailures++
            if (okHttpFailures >= 2) {
                preferBrowser = true
                TLog.w("http: OkHttp challenged twice; using the browser path from now on")
            }
            viaBrowser(b, req)
        }
    }

    private suspend fun space() = gate.withLock {
        val wait = lastAt + minGapMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastAt = System.currentTimeMillis()
    }

    private suspend fun okHttp(r: WebRequest): String = withContext(Dispatchers.IO) {
        val jar = cookies().orEmpty()
        val csrf = jar.split(';').map { it.trim() }.firstOrNull { it.startsWith("csrftoken=") }?.substringAfter('=')
        val req = Request.Builder()
            .url(r.url)
            .apply { if (r.method == "POST") post((r.body ?: "").toRequestBody("application/json".toMediaType())) else get() }
            .header("User-Agent", userAgent)
            .header("Referer", r.referer)
            .header("Origin", LeetCodeApi.ORIGIN)
            .header("X-Requested-With", "XMLHttpRequest")
            .apply {
                if (jar.isNotEmpty()) header("Cookie", jar)
                if (csrf != null) header("x-csrftoken", csrf)
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            check(resp.code, text, resp.header("cf-mitigated") != null, r)
            text
        }
    }

    private suspend fun viaBrowser(b: BrowserFetch, r: WebRequest): String {
        TLog.d("http: ${r.method} ${r.url.removePrefix(LeetCodeApi.ORIGIN)} via browser")
        val (code, text) = b.fetch(r)
        if (code < 0) throw IOException(text)
        check(code, text, false, r)
        return text
    }

    private fun check(code: Int, text: String, cfHeader: Boolean, r: WebRequest) {
        if (cfHeader || text.contains("Just a moment") || text.contains("cf-chl")) {
            TLog.w("http: challenge (HTTP $code) on ${r.url}")
            throw ChallengeException()
        }
        when {
            code == 429 -> throw RateLimitedException()
            (code == 401 || code == 403) && r.method == "POST" && r.url != LeetCodeApi.GRAPHQL -> {
                // A 403 on Run/Submit is usually a missing session or CSRF; HTML means Cloudflare.
                if (text.trimStart().startsWith("<")) throw ChallengeException() else throw SignedOutException()
            }
            code !in 200..299 && !text.trimStart().startsWith("{") -> throw IOException("HTTP $code")
        }
    }

    /** A plain GET (the Codeforces API, rev 13): no cookies, the WebView's user agent. */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        space()
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.header("cf-mitigated") != null || text.contains("Just a moment")) throw ChallengeException()
            if (r.code == 429) throw RateLimitedException()
            text
        }
    }

    /** Fetch a statement image (PLAN §8.2). */
    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for image")
            r.body?.bytes() ?: ByteArray(0)
        }
    }
}
