package dev.titan.code.data.codeforces

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.titan.code.TLog
import dev.titan.code.data.ChallengeException
import dev.titan.code.data.SignedOutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A hidden WebView on codeforces.com (rev 13). Codeforces puts its pages behind Cloudflare, so
 * statements are loaded as a browser would, and Submit uses the site's own form on
 * `/problemset/submit`, filled and sent from inside the page, with the cookies the sign-in page
 * left in `CookieManager`. One page at a time ([lock]).
 */
@SuppressLint("SetJavaScriptEnabled")
class CodeforcesWeb(context: Context) {
    private val app = context.applicationContext
    private var web: WebView? = null
    private var finished: CompletableDeferred<String>? = null
    private val lock = Mutex()

    /** The page's HTML after loading [url] and waiting out a Cloudflare check. */
    suspend fun page(url: String): String = lock.withLock {
        withContext(Dispatchers.Main) {
            load(url)
            eval("document.documentElement.outerHTML")
        }
    }

    /**
     * Submit [code] for problem [contestId][index] in [language]. Returns when Codeforces accepted
     * the form (it moves to the status page); throws with Codeforces' message otherwise.
     */
    suspend fun submit(contestId: Int, index: String, code: String, language: CodeforcesApi.Language) = lock.withLock {
        withContext(Dispatchers.Main) {
            val at = load(CodeforcesApi.SUBMIT_URL)
            if ("/enter" in at) throw SignedOutException()
            val js = """
                (function(){
                  var f = document.querySelector('form.submit-form') || document.querySelector('form[action*="submit"]');
                  if (!f) return 'noform';
                  var pc = f.querySelector('input[name=submittedProblemCode]');
                  if (pc) pc.value = ${JSONObject.quote("$contestId$index")};
                  var sel = f.querySelector('select[name=programTypeId]');
                  if (sel) {
                    var pick = null;
                    for (var i = 0; i < sel.options.length; i++) { var t = sel.options[i].text; if (${language.js}) { pick = sel.options[i].value; break; } }
                    if (!pick) return 'nolang';
                    sel.value = pick;
                    sel.dispatchEvent(new Event('change'));
                  }
                  var src = ${JSONObject.quote(code)};
                  var ta = f.querySelector('textarea[name=source]');
                  if (ta) ta.value = src;
                  try { if (window.ace) ace.edit('editor').setValue(src, -1); } catch (e) {}
                  var b = f.querySelector('input[type=submit], button[type=submit], .submit');
                  if (b) b.click(); else f.submit();
                  return 'sent';
                })()
            """.trimIndent()
            finished = CompletableDeferred()
            when (val r = eval(js)) {
                "sent" -> Unit
                "nolang" -> throw CodeforcesApi.ApiError("The submit form has no ${language.label}; pick another language in ⚙")
                else -> throw CodeforcesApi.ApiError("Codeforces' submit form wasn't found ($r)")
            }
            val next = withTimeoutOrNull(30_000) { finished!!.await() } ?: throw CodeforcesApi.ApiError("Codeforces didn't answer the submission")
            if ("/status" in next || "/my" in next) return@withContext
            val err = eval("Array.from(document.querySelectorAll('.error, span.error')).map(function(e){return e.textContent.trim()}).filter(function(t){return t}).join(' · ')")
            throw CodeforcesApi.ApiError(err.ifEmpty { "Codeforces didn't accept the submission" })
        }
    }

    /** The signed-in handle shown in the page header, or null. */
    suspend fun handle(): String? = lock.withLock {
        withContext(Dispatchers.Main) {
            load(CodeforcesApi.ORIGIN + "/")
            eval(HANDLE_JS).takeIf { it.isNotBlank() }
        }
    }

    fun reset() {
        web?.destroy()
        web = null
    }

    /** Load [url], wait for it, and wait out a "Just a moment" check (up to 25 s). Returns the final URL. */
    private suspend fun load(url: String): String {
        val w = ensure()
        finished = CompletableDeferred()
        w.loadUrl(url)
        var at = withTimeout(40_000) { finished!!.await() }
        var waited = 0
        while (eval("document.title").contains("Just a moment") && waited < 25) {
            finished = CompletableDeferred()
            withTimeoutOrNull(5_000) { at = finished!!.await() }
            waited += 5
        }
        if (eval("document.title").contains("Just a moment")) {
            TLog.w("codeforces: still challenged after ${waited}s")
            throw ChallengeException()
        }
        // Give the page's own scripts (the submit form's) a moment to run.
        delay(300)
        return at
    }

    private suspend fun eval(js: String): String {
        val w = ensure()
        val out = CompletableDeferred<String>()
        w.evaluateJavascript(js) { out.complete(it ?: "null") }
        val raw = out.await()
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: ""
    }

    private fun ensure(): WebView {
        web?.let { return it }
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.blockNetworkImage = true
        CookieManager.getInstance().setAcceptCookie(true)
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                finished?.complete(url)
            }
        }
        web = w
        return w
    }

    companion object {
        /**
         * The signed-in handle: only when the page has a logout link (so a blog author's link on a
         * signed-out page never counts), from the header, or the profile link just before logout.
         */
        const val HANDLE_JS = """(function(){
          var out = document.querySelector('a[href*="/logout"]');
          if (!out) return '';
          var a = document.querySelector('.lang-chooser a[href^="/profile/"]') || document.querySelector('#header a[href^="/profile/"]');
          if (a) return a.textContent.trim() || a.getAttribute('href').split('/').pop();
          var best = '';
          document.querySelectorAll('a[href^="/profile/"]').forEach(function(x){
            if (x.compareDocumentPosition(out) & Node.DOCUMENT_POSITION_FOLLOWING) best = x.getAttribute('href').split('/').pop();
          });
          return best;
        })()"""
    }
}
