package com.tanam.smarteatingmonitor.web

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Abstraction the session logic talks to. */
interface VideoHandle {
    fun load(useKids: Boolean)
    fun play()
    fun pause()
    /** While locked the page is forced to stay paused (blocks auto-play of the next video). */
    fun setLocked(locked: Boolean)
    /** Re-injects the control script if the page navigated (cheap no-op otherwise). */
    fun ensureInjected()
    fun signOut(reloadKids: Boolean)
}

/**
 * In-app YouTube browser. Sign-in persists (cookies kept on disk).
 * Video is controlled through injected JS on the page's <video> element — same approach as iOS.
 */
class YouTubeController(
    private val activity: Activity,
    private val onPlayingChanged: (Boolean) -> Unit,
) : VideoHandle {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUrl: String? = null
    private var locked = false

    /** Current WebView. Replaced automatically if the renderer process crashes. */
    var webView: WebView by mutableStateOf(createWebView())
        private set

    /** Non-null while a video is in YouTube's full-screen mode. */
    var fullscreenView: View? by mutableStateOf(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    // region VideoHandle

    override fun load(useKids: Boolean) {
        val url = if (useKids) KIDS_URL else YOUTUBE_URL
        lastUrl = url
        webView.loadUrl(url)
    }

    override fun play() = eval("window.TANAM && window.TANAM.play();")

    override fun pause() = eval("window.TANAM && window.TANAM.pause();")

    override fun setLocked(locked: Boolean) {
        this.locked = locked
        eval("window.TANAM && window.TANAM.setLocked(${if (locked) "true" else "false"});")
    }

    override fun ensureInjected() {
        eval(VIDEO_CONTROL_JS)
        if (locked) eval("window.TANAM && window.TANAM.setLocked(true);")
    }

    override fun signOut(reloadKids: Boolean) {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            mainHandler.post {
                cookies.flush()
                WebStorage.getInstance().deleteAllData()
                webView.clearCache(true)
                webView.clearHistory()
                load(reloadKids)
            }
        }
    }

    // endregion

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() = webView.goBack()

    fun exitFullscreen() {
        if (fullscreenView != null) {
            fullscreenCallback?.onCustomViewHidden()
            hideFullscreen()
        }
    }

    /** Stops all page media and timers (call when the session ends). */
    fun stopPage() {
        exitFullscreen()
        pause()
        webView.loadUrl("about:blank")
        lastUrl = null
    }

    fun onAppStopped() {
        CookieManager.getInstance().flush()
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyWebView(webView)
    }

    private fun eval(js: String) {
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) { }
    }

    private fun hideFullscreen() {
        fullscreenView = null
        fullscreenCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val view = WebView(activity)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        view.setBackgroundColor(android.graphics.Color.BLACK)

        val s = view.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportMultipleWindows(false)          // target=_blank opens in this view
        s.javaScriptCanOpenWindowsAutomatically = false
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // Google blocks sign-in from "embedded" WebViews; present a normal Chrome user agent instead.
        s.userAgentString = chromeUserAgent(s.userAgentString)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.addJavascriptInterface(Bridge(), "TanamAndroid")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(view, VIDEO_CONTROL_JS, setOf("*"))
            } catch (_: Throwable) { }
        }

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return !isAllowed(request.url) // external sites are blocked during a session
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && url != "about:blank") lastUrl = url
                ensureInjected()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // YouTube is a single-page app: navigation happens without page loads
                ensureInjected()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Never let a WebView renderer crash take the whole app down: rebuild it.
                if (view === webView) {
                    exitFullscreen()
                    val restoreUrl = lastUrl
                    val old = webView
                    webView = createWebView()
                    destroyWebView(old)
                    restoreUrl?.let { webView.loadUrl(it) }
                } else {
                    destroyWebView(view)
                }
                return true
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideCustomView() {
                hideFullscreen()
            }

            // Avoids the grey "play" placeholder some WebView versions draw before a video starts
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        return view
    }

    private fun destroyWebView(view: WebView) {
        try {
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.webChromeClient = null
            view.destroy()
        } catch (_: Throwable) { }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onState(playing: Boolean) {
            mainHandler.post { onPlayingChanged(playing) }
        }
    }

    companion object {
        const val YOUTUBE_URL = "https://m.youtube.com"
        const val KIDS_URL = "https://www.youtubekids.com"

        private val ALLOWED_DOMAINS = listOf(
            "youtube.com", "youtubekids.com", "youtu.be", "google.com", "google.co",
            "gstatic.com", "ytimg.com", "googlevideo.com", "googleusercontent.com",
            "googleapis.com", "accounts.youtube.com",
        )

        fun isAllowed(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme == "about") return true
            if (scheme != "http" && scheme != "https") return false // intent://, market://, etc.
            val host = uri.host?.lowercase() ?: return false
            return ALLOWED_DOMAINS.any { host == it || host.endsWith(".$it") || host.contains(it) }
        }

        fun chromeUserAgent(default: String): String =
            default.replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")

        /** Same control script as iOS, with the Android bridge. Safe to inject many times. */
        val VIDEO_CONTROL_JS = """
            (function() {
                if (window.TANAM) return;
                var locked = false;
                function post(playing) {
                    try { if (window.TanamAndroid) { window.TanamAndroid.onState(playing); } } catch (e) {}
                }
                function videos() { return document.querySelectorAll('video'); }
                function attach(v) {
                    if (!v || v.__tanam) return;
                    v.__tanam = true;
                    v.addEventListener('play', function() {
                        if (locked) { v.pause(); return; }
                        post(true);
                    });
                    v.addEventListener('pause', function() { post(false); });
                    v.addEventListener('ended', function() { post(false); });
                }
                function check() {
                    var list = videos();
                    for (var i = 0; i < list.length; i++) {
                        var v = list[i];
                        attach(v);
                        if (locked && !v.paused) { v.pause(); }
                    }
                }
                try {
                    new MutationObserver(check).observe(document.documentElement || document, { childList: true, subtree: true });
                } catch (e) {}
                setInterval(check, 750);
                window.TANAM = {
                    play: function() {
                        var v = document.querySelector('video');
                        if (v) { var p = v.play(); if (p && p.catch) { p.catch(function() {}); } }
                    },
                    pause: function() {
                        var list = videos();
                        for (var i = 0; i < list.length; i++) { list[i].pause(); }
                    },
                    isPlaying: function() {
                        var v = document.querySelector('video');
                        return v ? (!v.paused && !v.ended) : false;
                    },
                    setLocked: function(value) { locked = !!value; check(); }
                };
                check();
            })();
        """.trimIndent()
    }
}
