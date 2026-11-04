package dev.titan.code.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import dev.titan.code.samples.Sample

/**
 * The Problem pane's statement, and the Comments pane (PLAN §4.1): a WebView with bundled dark CSS, no JavaScript, no
 * network and no focus, so hardware keys never reach it. The keyboard surface scrolls it 1:1
 * through [scrollByClamped] (PLAN §5.6); the touchscreen scrolls it as usual.
 */
@SuppressLint("ViewConstructor")
class ProblemView(context: Context) : WebView(context) {

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        isVerticalScrollBarEnabled = true
        settings.javaScriptEnabled = false
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
        val page = """<!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="problem.css"></head><body>$html</body></html>"""
        loadDataWithBaseURL("file:///android_asset/samples/", page, "text/html", "utf-8", null)
        return true
    }

    fun show(sample: Sample) = show(sample.statementHtml)

    /** Scroll by [dy] view pixels, kept within the content. */
    fun scrollByClamped(dy: Int) {
        val max = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
        val target = (scrollY + dy).coerceIn(0, max)
        if (target != scrollY) scrollTo(scrollX, target)
    }
}
