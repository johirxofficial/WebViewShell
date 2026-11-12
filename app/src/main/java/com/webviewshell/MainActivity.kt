package com.webviewshell

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * A clean, single-activity WebView shell.
 *
 * - One screen, no browser UI: no URL bar, no menu, no tabs.
 * - Every web link stays inside the app. Nothing is handed to an external browser.
 * - HTML5 video works, including fullscreen and picture-in-picture style players.
 * - Audio keeps playing in the background (configurable).
 * - File uploads, downloads, camera/mic (WebRTC) and geolocation all work.
 *
 * The start URL and behaviour switches live in res/values/app_config.xml.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: View
    private lateinit var errorOverlay: View
    private lateinit var fullscreenContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingDownload: (() -> Unit)? = null

    private var splashScreen: SplashScreen? = null
    private var firstPageShown = false

    // ------------------------------------------------------------------ config

    private val startUrl: String by lazy { getString(R.string.start_url) }
    private val pullToRefreshEnabled: Boolean by lazy { resources.getBoolean(R.bool.enable_pull_to_refresh) }
    private val backgroundAudioEnabled: Boolean by lazy { resources.getBoolean(R.bool.keep_audio_playing_in_background) }
    private val openNonWebSchemes: Boolean by lazy { resources.getBoolean(R.bool.open_non_web_links_in_other_apps) }
    private val showLoadingBar: Boolean by lazy { resources.getBoolean(R.bool.show_loading_bar) }
    private val pinchZoomEnabled: Boolean by lazy { resources.getBoolean(R.bool.enable_pinch_zoom) }

    // ------------------------------------------------------- activity results

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            callback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        }

    private val geoPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

    private val webPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val request = pendingWebPermission
            pendingWebPermission = null
            if (request == null) return@registerForActivityResult
            val granted = result.filterValues { it }.keys.toTypedArray()
            if (granted.isEmpty()) request.deny() else request.grant(granted)
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingDownload
            pendingDownload = null
            if (granted) {
                action?.invoke()
            } else {
                toast(getString(R.string.download_permission_denied))
            }
        }

    // -------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        splashScreen = installSplashScreen()
        splashScreen?.setKeepOnScreenCondition { !firstPageShown }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        configureWebView()
        wireInteractions()

        // Never let a stalled page keep the splash up forever.
        Handler(Looper.getMainLooper()).postDelayed({ firstPageShown = true }, SPLASH_TIMEOUT_MS)

        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored != null) {
            firstPageShown = true
        } else {
            webView.loadUrl(startUrl)
        }
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorOverlay = findViewById(R.id.errorOverlay)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
    }

    private fun wireInteractions() {
        swipeRefresh.isEnabled = pullToRefreshEnabled
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand))
        swipeRefresh.setOnRefreshListener {
            hideError()
            webView.reload()
        }

        findViewById<View>(R.id.retryButton).setOnClickListener {
            hideError()
            webView.loadUrl(startUrl)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideFullscreen()
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ------------------------------------------------------------ webview setup

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.window_background))
        // Kill the glow/overscroll effect and the scrollbars so the page feels native.
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isHapticFeedbackEnabled = true

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)

            useWideViewPort = true
            loadWithOverviewMode = true

            setSupportZoom(pinchZoomEnabled)
            builtInZoomControls = pinchZoomEnabled
            displayZoomControls = false
            textZoom = 100

            // Autoplay muted AND unmuted media, and allow audio in the background.
            mediaPlaybackRequiresUserGesture = false

            cacheMode = WebSettings.LOAD_DEFAULT

            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = ShellWebViewClient()
        webView.webChromeClient = ShellChromeClient()

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }
    }

    // ------------------------------------------------------------ webview client

    private inner class ShellWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()

            // http/https and other in-page schemes always stay inside the app.
            if (scheme == null || scheme in IN_PAGE_SCHEMES) return false

            // tel:, mailto:, market:, intent: ... stay blocked by default so the
            // user never silently leaves the app.
            return if (openNonWebSchemes) {
                launchExternalIntent(uri)
                true
            } else {
                toast(getString(R.string.link_not_supported))
                true
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            hideError()
            if (showLoadingBar) progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            firstPageShown = true
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            firstPageShown = true
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            showError()
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            // Certificate errors are never bypassed - that would defeat HTTPS.
            handler?.cancel()
            showError()
        }
    }

    // ----------------------------------------------------- webview chrome host

    private inner class ShellChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (!showLoadingBar) return
            progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }

        /** Popups (target=_blank, window.open) are rendered in this same screen. */
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val message = resultMsg ?: return false
            val popup = WebView(this@MainActivity)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    popup.destroy()
                    webView.loadUrl(target)
                    return true
                }
            }
            (message.obj as? WebView.WebViewTransport)?.webView = popup
            message.sendToTarget()
            return true
        }

        /** HTML5 fullscreen video. */
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null) return
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback

            fullscreenContainer.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            fullscreenContainer.visibility = View.VISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setSystemBarsHidden(true)
        }

        override fun onHideCustomView() {
            hideFullscreen()
        }

        /** <input type="file"> support, including multiple files. */
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback

            val mimeType = fileChooserParams?.acceptTypes
                ?.firstOrNull { it.contains("/") }
                ?: "*/*"

            return try {
                fileChooserLauncher.launch(mimeType)
                true
            } catch (e: ActivityNotFoundException) {
                fileChooserCallback = null
                false
            }
        }

        /** Camera/microphone for WebRTC (video calls, voice recording ...). */
        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return
            val needed = mutableListOf<String>()
            for (resource in request.resources) {
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        if (!hasPermission(Manifest.permission.CAMERA)) needed += Manifest.permission.CAMERA

                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) needed += Manifest.permission.RECORD_AUDIO
                }
            }
            if (needed.isEmpty()) {
                request.grant(request.resources)
            } else {
                pendingWebPermission = request
                webPermissionLauncher.launch(needed.toTypedArray())
            }
        }

        /** navigator.geolocation */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            if (origin == null || callback == null) return
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                callback.invoke(origin, true, false)
            } else {
                pendingGeoOrigin = origin
                pendingGeoCallback = callback
                geoPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        override fun onCloseWindow(window: WebView?) {
            window?.destroy()
        }
    }

    // --------------------------------------------------------------- downloads

    private fun downloadFile(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (!url.startsWith("http", ignoreCase = true)) {
            // blob: and data: downloads need a JS bridge; be honest about it.
            toast(getString(R.string.download_unsupported))
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            pendingDownload = { startDownload(url, userAgent, contentDisposition, mimeType) }
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        startDownload(url, userAgent, contentDisposition, mimeType)
    }

    @Suppress("DEPRECATION")
    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            toast(getString(R.string.download_started))
        } catch (e: Exception) {
            toast(getString(R.string.download_failed))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hideFullscreen() {
        val view = customView ?: return
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSystemBarsHidden(false)
    }

    private fun launchExternalIntent(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.link_not_supported))
        }
    }

    private fun showError() {
        errorOverlay.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        if (!backgroundAudioEnabled) webView.onResume()
    }

    override fun onPause() {
        // Deliberately skip webView.onPause() when background audio is wanted:
        // that is what keeps HTML5 media playing while the app is in the background.
        if (!backgroundAudioEnabled) webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        if (customView != null) hideFullscreen()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_TIMEOUT_MS = 8_000L
        private val IN_PAGE_SCHEMES = setOf(
            "http", "https", "about", "data", "blob", "javascript", "file", "content"
        )
    }
}
