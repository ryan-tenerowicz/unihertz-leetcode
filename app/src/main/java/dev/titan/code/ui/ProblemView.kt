package dev.titan.code.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import dev.titan.code.samples.Sample

/**
 * The Problem pane's statement, and the Comments pane (PLAN §4.1): a WebView with bundled dark CSS, no
 * network and no focus, so hardware keys never reach it. JavaScript is on only for the app's own
 * bundled scripts (KaTeX for maths, rev 36): the page's content-security policy allows scripts from
 * `file:` only, with no inline scripts or handlers, and the sites' HTML has its scripts removed anyway. The keyboard surface scrolls it 1:1
 * through [scrollByClamped] (PLAN §5.6); the touchscreen scrolls it as usual.
 */
@SuppressLint("ViewConstructor")
class ProblemView(context: Context) : WebView(context) {

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        isVerticalScrollBarEnabled = true
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.blockNetworkLoads = true
        // Statement images are saved under filesDir and shown from file:// URLs (PLAN §8.2).
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.textZoom = 100
        // Links never navigate away: the pane is a reader, and network loads are off anyway.
        webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("titan:")) onAppLink?.invoke(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val y = pendingScroll
                pendingScroll = 0
                if (y > 0) view.postDelayed({ view.scrollTo(0, y) }, 60)
            }
        }
    }

    /** `titan:` links (e.g. "Load more comments") go here; nothing else navigates. */
    var onAppLink: ((String) -> Unit)? = null

    private var shownHtml: String? = null
    private var pendingScroll = 0

    /** Shows [html] (a statement fragment) unless it's already showing. Returns true if it changed. */
    fun show(html: String, keepScroll: Boolean = false): Boolean {
        if (html == shownHtml) return false
        pendingScroll = if (keepScroll) scrollY else 0
        shownHtml = html
        // Rev 36: KaTeX only where there are formulas (titan-math.js draws each `data-tex`).
        val maths = html.contains("data-tex=")
        val page = """<!doctype html><html><head><meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="$CSP">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            ${if (maths) "<link rel=\"stylesheet\" href=\"../katex/katex.min.css\">" else ""}
            <link rel="stylesheet" href="problem.css"></head><body>$html
            ${if (maths) "<script src=\"../katex/katex.min.js\"></script><script src=\"../katex/titan-math.js\"></script>" else ""}
            </body></html>"""
        loadDataWithBaseURL("file:///android_asset/samples/", page, "text/html", "utf-8", null)
        return true
    }

    fun show(sample: Sample) = show(sample.statementHtml)

    companion object {
        /** Bundled files only: no inline script, no network. KaTeX sets inline styles, hence `style-src 'unsafe-inline'`. */
        const val CSP = "default-src 'none'; script-src file:; style-src file: 'unsafe-inline'; font-src file:; img-src file: data:"
    }

    /** Scroll by [dy] view pixels, kept within the content. */
    fun scrollByClamped(dy: Int) {
        val max = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
        val target = (scrollY + dy).coerceIn(0, max)
        if (target != scrollY) scrollTo(scrollX, target)
    }
}
