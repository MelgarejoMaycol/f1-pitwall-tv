package com.f1pitwall.tv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.ui.discovery.DiscoveredChannel
import com.f1pitwall.tv.ui.discovery.DiscoveredEvent
import com.f1pitwall.tv.ui.discovery.F1ContentDiscovery

/**
 * Motor unificado de reproducción de video para F1PitWallTV.
 *
 * Administra de forma integrada y sin duplicación de Activities:
 * - Reproducción nativa HLS/DASH/MP4 con ExoPlayer (Media3).
 * - Reproducción web HTML5 con Chromium WebView optimizado para Android TV:
 *     - Gestión de ventanas secundarias con WebChromeClient.onCreateWindow() para target="_blank" y window.open().
 *     - Traslado dinámico (reparenting) de la instancia única de WebView entre el contenedor
 *       integrado del Muro de Pits y la pantalla completa dedicada, preservando el estado multimedia,
 *       cookies, búfer y reproducción continua sin recargas de página.
 *     - Pantalla completa HTML5 (onShowCustomView / onHideCustomView).
 *     - Descubrimiento automático de eventos de F1 y canales reales (Live 1, Live 2).
 *     - Manejo de saturación de servidor (HTTP 429) con sugerencia y enfoque automático de Live 2.
 *     - Control unificado de audio (mute/unmute), recarga y navegación con control remoto (D-Pad).
 */
class VideoPlaybackManager(
    private val context: Context,
    // Superficies de renderizado
    private val framePitWallSurface: FrameLayout,
    private val frameDedicatedSurface: FrameLayout,
    // Inline Pit Wall Controls
    private val playerView: PlayerView,
    private val webVideoView: WebView,
    private val layoutPlayerStatusOverlay: LinearLayout,
    private val pbPlayerLoading: ProgressBar,
    private val tvPlayerStatusTitle: TextView,
    private val tvPlayerStatusMessage: TextView,
    private val layoutPlayerErrorActions: LinearLayout,
    private val btnPlayerRetry: Button,
    private val btnPlayerGoSettings: Button,
    // Dedicated Fullscreen Controls
    private val layoutDedicatedPlayback: FrameLayout,
    private val playerViewDedicated: PlayerView,
    private val layoutDedicatedTopBar: LinearLayout,
    private val btnDedicatedBack: Button,
    private val tvDedicatedSourceName: TextView,
    private val tvDedicatedSourceTypeBadge: TextView,
    private val btnDedicatedPlayPause: Button,
    private val btnDedicatedReload: Button,
    private val btnDedicatedMute: Button? = null,
    private val btnDedicatedSessions: Button? = null,
    private val layoutDedicatedStatusOverlay: LinearLayout,
    private val pbDedicatedLoading: ProgressBar,
    private val tvDedicatedStatusTitle: TextView,
    private val tvDedicatedStatusMessage: TextView,
    private val layoutDedicatedErrorActions: LinearLayout,
    private val btnDedicatedRetry: Button,
    private val btnDedicatedGoSettings: Button,
    private val llDedicatedChannels: LinearLayout? = null,
    // Inline Pit Wall Active Controls Bar
    private val llPitWallPlayerBar: LinearLayout? = null,
    private val llPitWallChannels: LinearLayout? = null,
    private val btnPitWallMute: Button? = null,
    private val btnPitWallReload: Button? = null,
    private val btnPitWallSessions: Button? = null,
    private val btnPitWallFullscreenInline: Button? = null,
    // Contenedores auxiliares para HTML5 fullscreen y popups de WebView
    private val layoutCustomViewContainer: FrameLayout? = null,
    private val layoutPopupContainer: FrameLayout? = null,
    // Callbacks
    private val onPlaybackStarted: () -> Unit = {},
    private val onNavigateToSettings: () -> Unit = {},
    private val onExitDedicatedFullscreen: () -> Unit = {}
) {
    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var currentPlayingSourceUrl: String? = null
        private set
    var currentPlayingSourceName: String? = null
        private set
    var currentPlayingSourceType: String? = null
        private set
    var isDedicatedFullscreenActive: Boolean = false
        private set
    var isMuted: Boolean = false
        private set

    // Estado del motor de canales y descubrimiento F1
    private enum class PageMode { DIRECTORY, EVENT_PLAYER, DIRECT_STREAM }
    private var currentMode = PageMode.DIRECTORY
    private var discoveredChannels: List<DiscoveredChannel> = emptyList()
    private var activeChannelIndex: Int = 0
    private var channelButtonsDedicated: MutableList<Button> = mutableListOf()
    private var channelButtonsInline: MutableList<Button> = mutableListOf()
    private var currentF1Event: DiscoveredEvent? = null
    // Avoid repeatedly switching channels when the current event's primary edge is rate-limited.
    private var autoFallbackTriggered: Boolean = false

    // Gestión de ventanas emergentes secundarias (onCreateWindow)
    private val activePopupWebViews: MutableList<WebView> = mutableListOf()

    // Gestión de pantalla completa HTML5 nativa
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    companion object {
        private const val TAG = "VideoPlaybackManager"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun resolvePlaybackErrorMessage(errorCode: Int, fallbackMessage: String? = null): String {
            return when (errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Error de conexión: No se pudo conectar con el servidor de la transmisión."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    "Fuente no disponible: El servidor devolvió un error HTTP o el archivo no existe."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "Formato incompatible: La transmisión no tiene un formato reproducible válido."
                else ->
                    "Error de reproducción: ${fallbackMessage ?: "Ocurrió un error inesperado al reproducir el video."}"
            }
        }
    }

    init {
        setupExoPlayer()
        setupWebView()
        setupActions()
    }

    // ======================================================================
    // Configuración de ExoPlayer (Media3)
    // ======================================================================

    private fun setupExoPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("F1PitWallTV/1.0 (Android TV)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView.player = exoPlayer
        playerViewDedicated.player = exoPlayer

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    onPlaybackStarted()
                    dismissOverlays()
                    btnDedicatedPlayPause.text = "⏸ Pausa"
                } else {
                    btnDedicatedPlayPause.text = "▶ Reanudar"
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (exoPlayer?.isPlaying != true) {
                            val msg = currentPlayingSourceName ?: "Buffering de señal de video"
                            showPlayerBufferingState("Cargando transmisión...", msg)
                            showDedicatedBuffering("Cargando transmisión...", msg)
                        }
                    }
                    Player.STATE_READY -> {
                        dismissOverlays()
                    }
                    Player.STATE_ENDED -> {
                        val endMsg = "La transmisión ha finalizado"
                        showPlayerErrorState(endMsg)
                        showDedicatedError(endMsg)
                    }
                    Player.STATE_IDLE -> {
                        // Reposo
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(TAG, "Error de reproducción ExoPlayer: ${error.errorCodeName}", error)
                val friendlyMessage = resolvePlaybackErrorMessage(error.errorCode, error.message)
                showPlayerErrorState(friendlyMessage)
                showDedicatedError(friendlyMessage)
            }
        })
    }

    // ======================================================================
    // Configuración del WebView Único
    // ======================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webVideoView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                userAgentString = DESKTOP_UA
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(webVideoView, true)
                }
            }

            addJavascriptInterface(WebDiscoveryBridge(), "F1Bridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    val scheme = url.scheme ?: ""
                    val urlStr = url.toString()
                    val sanitized = sanitizeUrl(urlStr)
                    Log.d(TAG, "shouldOverrideUrlLoading: $sanitized (scheme=$scheme)")

                    if (scheme == "http" || scheme == "https") {
                        if (urlStr.contains("vivogoal.space")) {
                            val fixedUrl = urlStr.replace("vivogoal.space", "golygoals.com")
                            Log.i(TAG, "Normalizando dominio a golygoals.com: ${sanitizeUrl(fixedUrl)}")
                            view?.loadUrl(fixedUrl)
                            return true
                        }
                        return false // permitir navegación interna dentro del WebView sin abrir navegador externo
                    }

                    Log.w(TAG, "Esquema no permitido rechazado: $scheme")
                    return true
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val urlStr = url ?: return false
                    if (urlStr.contains("vivogoal.space")) {
                        val fixedUrl = urlStr.replace("vivogoal.space", "golygoals.com")
                        view?.loadUrl(fixedUrl)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val title = "Cargando señal: ${currentPlayingSourceName ?: "F1 Web"}"
                    val sanitized = sanitizeUrl(url)
                    Log.i(TAG, "onPageStarted: $sanitized")

                    if (isDedicatedFullscreenActive) {
                        showDedicatedBuffering(title, "Conectando con reproductor...")
                    } else {
                        showPlayerBufferingState(title, "Conectando con reproductor...")
                    }

                    // Watchdog: descartar overlay si el video comienza
                    mainHandler.postDelayed({
                        dismissOverlays()
                    }, 4000)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val currentUrl = url.orEmpty().lowercase()
                    val sanitized = sanitizeUrl(url)
                    Log.i(TAG, "onPageFinished: $sanitized")

                    dismissOverlays()
                    onPlaybackStarted()
                    webVideoView.requestFocus()

                    val isEventPage = currentUrl.contains("soccer.html") ||
                            currentUrl.contains("us.html") ||
                            currentUrl.contains("latin.html")

                    if (isEventPage) {
                        currentMode = PageMode.EVENT_PLAYER
                        view?.evaluateJavascript(F1ContentDiscovery.buildEventPageScannerJs(), null)
                    } else if (currentUrl.contains("futbolvivo.tv") || currentUrl.contains("futbollibre")) {
                        currentMode = PageMode.DIRECTORY
                        view?.evaluateJavascript(F1ContentDiscovery.buildDirectoryScannerJs(), null)
                    }

                    // Autolimpieza y unmute
                    injectAdCleanAndAutoplay(view)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val status = errorResponse?.statusCode ?: 0
                    val isMain = request?.isForMainFrame == true
                    val sanitizedUrl = sanitizeUrl(request?.url?.toString())
                    Log.w(TAG, "HTTP $status en $sanitizedUrl (isMain=$isMain)")

                    if (status == 429) {
                        val failedHost = request?.url?.host.orEmpty()
                        val isKnownRateLimitedEdge = failedHost.contains("kora", ignoreCase = true)
                        mainHandler.post {
                            val canFallback = !isMain && isKnownRateLimitedEdge &&
                                activeChannelIndex == 0 && discoveredChannels.size > 1 && !autoFallbackTriggered
                            if (canFallback) {
                                autoFallbackTriggered = true
                                val limitMsg = "Live 1 está limitado (HTTP 429). Cambiando automáticamente a Live 2…"
                                if (isDedicatedFullscreenActive) {
                                    showDedicatedBuffering("Cambiando de servidor", limitMsg)
                                    channelButtonsDedicated.getOrNull(1)?.requestFocus()
                                } else {
                                    showPlayerBufferingState("Cambiando de servidor", limitMsg)
                                    channelButtonsInline.getOrNull(1)?.requestFocus()
                                }
                                Log.w(TAG, "HTTP 429 en edge $failedHost; fallback automático a Live 2")
                                switchToDiscoveredChannel(1)
                                mainHandler.postDelayed({ dismissOverlays() }, 1800)
                            } else {
                                val limitMsg = "El servidor está temporalmente limitado (HTTP 429)."
                                if (isDedicatedFullscreenActive) {
                                    showDedicatedBuffering("Servidor temporalmente limitado", limitMsg)
                                } else {
                                    showPlayerBufferingState("Servidor temporalmente limitado", limitMsg)
                                }
                                mainHandler.postDelayed({ dismissOverlays() }, 2500)
                            }
                        }
                    } else if (isMain && status >= 400) {
                        mainHandler.post {
                            val msg = "Error del servidor (HTTP $status)"
                            if (isDedicatedFullscreenActive) {
                                showDedicatedError(msg)
                            } else {
                                showPlayerErrorState(msg)
                            }
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val desc = error?.description?.toString() ?: "No se pudo conectar con el sitio web."
                        val msg = "Error al abrir la señal: $desc"
                        Log.e(TAG, "onReceivedError: $msg")
                        if (isDedicatedFullscreenActive) {
                            showDedicatedError(msg)
                        } else {
                            showPlayerErrorState(msg)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                // 1. Manejo de múltiples ventanas para target="_blank" y window.open()
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    Log.i(TAG, "onCreateWindow invocado (isDialog=$isDialog, isUserGesture=$isUserGesture)")
                    val childWv = createChildWebView()
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = childWv
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    Log.i(TAG, "onCloseWindow invocado")
                    closeChildWebView(window)
                }

                // 2. Pantalla completa HTML5 (onShowCustomView / onHideCustomView)
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    Log.i(TAG, "onShowCustomView: entrando a pantalla completa HTML5")
                    if (customView != null) {
                        onHideCustomView()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    val container = layoutCustomViewContainer
                    if (container != null && view != null) {
                        container.removeAllViews()
                        container.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        container.visibility = View.VISIBLE
                        container.bringToFront()
                        view.requestFocus()
                    }
                }

                override fun onHideCustomView() {
                    hideCustomViewInternal()
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                    Log.i(TAG, "Permisos multimedia concedidos: ${request?.resources?.toList()}")
                }

                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS[${msg?.messageLevel()}]: ${msg?.message()}")
                    return true
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 65) {
                        dismissOverlays()
                    }
                }
            }
        }
    }

    // ======================================================================
    // Gestión de Ventanas Emergentes Secundarias (onCreateWindow)
    // ======================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createChildWebView(): WebView {
        val child = WebView(context).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = false
            // Chromium must keep the transport WebView attached while resolving the popup URL.
            visibility = View.VISIBLE

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                userAgentString = DESKTOP_UA
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val urlStr = request?.url?.toString() ?: return false
                    val sanitized = sanitizeUrl(urlStr)
                    Log.i(TAG, "ChildWebView -> Redirigiendo URL: $sanitized")

                    if (isAllowedSportsUrl(urlStr)) {
                        val finalUrl = if (urlStr.contains("vivogoal.space")) {
                            urlStr.replace("vivogoal.space", "golygoals.com")
                        } else urlStr

                        webVideoView.loadUrl(finalUrl)
                        closeChildWebView(this@apply)
                        return true
                    }

                    Log.w(TAG, "Popup publicitario bloqueado: $sanitized")
                    closeChildWebView(this@apply)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val urlStr = url.orEmpty()
                    if (urlStr.isNotBlank() && urlStr != "about:blank") {
                        val sanitized = sanitizeUrl(urlStr)
                        Log.i(TAG, "ChildWebView onPageStarted: $sanitized")
                        if (isAllowedSportsUrl(urlStr)) {
                            val finalUrl = if (urlStr.contains("vivogoal.space")) {
                                urlStr.replace("vivogoal.space", "golygoals.com")
                            } else urlStr

                            webVideoView.loadUrl(finalUrl)
                            closeChildWebView(this@apply)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    closeChildWebView(window)
                }
            }
        }

        activePopupWebViews.add(child)
        layoutPopupContainer?.visibility = View.VISIBLE
        layoutPopupContainer?.addView(child, FrameLayout.LayoutParams(1, 1))
        return child
    }

    private fun closeChildWebView(wv: WebView?) {
        wv ?: return
        try {
            activePopupWebViews.remove(wv)
            layoutPopupContainer?.removeView(wv)
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.destroy()
            if (activePopupWebViews.isEmpty()) {
                layoutPopupContainer?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar child webview: ${e.message}")
        }
    }

    private fun closeAllPopups() {
        activePopupWebViews.toList().forEach { closeChildWebView(it) }
        activePopupWebViews.clear()
        layoutPopupContainer?.removeAllViews()
        layoutPopupContainer?.visibility = View.GONE
    }

    private fun isAllowedSportsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("futbolvivo.tv") ||
                lower.contains("golygoals.com") ||
                lower.contains("vivogoal.space") ||
                lower.contains("soccer.html") ||
                lower.contains("us.html") ||
                lower.contains("latin.html") ||
                lower.contains("kora") ||
                lower.contains("streame") ||
                lower.contains("clappr") ||
                lower.contains("sportycos")
    }

    private fun hideCustomViewInternal() {
        val view = customView ?: return
        Log.i(TAG, "hideCustomViewInternal: saliendo de pantalla completa HTML5")
        layoutCustomViewContainer?.removeView(view)
        layoutCustomViewContainer?.visibility = View.GONE
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        webVideoView.visibility = View.VISIBLE
        webVideoView.requestFocus()
    }

    // ======================================================================
    // Descubrimiento de Contenido F1 y Cambio Dinámico de Canales
    // ======================================================================

    private inner class WebDiscoveryBridge {
        @JavascriptInterface
        fun onF1Events(json: String) {
            mainHandler.post { handleF1EventsDiscovered(json) }
        }

        @JavascriptInterface
        fun onChannels(json: String) {
            mainHandler.post { handleChannelsDiscovered(json) }
        }

        @JavascriptInterface
        fun onLog(msg: String) {
            Log.d(TAG, "F1Bridge: $msg")
        }
    }

    private fun handleF1EventsDiscovered(json: String) {
        val events = F1ContentDiscovery.parseF1Events(json)
        Log.d(TAG, "F1 Events descubiertos: ${events.size}")
        if (events.isNotEmpty()) {
            val target = events.first()
            currentF1Event = target
            discoveredChannels = emptyList()
            autoFallbackTriggered = false
            channelButtonsInline.clear()
            channelButtonsDedicated.clear()
            tvDedicatedSourceName.text = target.title
            Log.i(TAG, "Navegando a evento F1: ${target.title} -> ${target.url}")
            currentMode = PageMode.EVENT_PLAYER
            webVideoView.loadUrl(target.url)
        }
    }

    private fun handleChannelsDiscovered(json: String) {
        val (title, channels) = F1ContentDiscovery.parseEventPageChannels(json)
        Log.i(TAG, "Canales descubiertos: ${channels.size}")
        discoveredChannels = channels
        if (title.isNotBlank()) {
            tvDedicatedSourceName.text = title
        }
        if (channels.isNotEmpty()) {
            buildChannelButtons(channels)
            // Live 1 previously returned HTTP 429 on this provider. Prefer Live 2
            // when the event actually advertises it; otherwise use the only available channel.
            val live2Index = channels.indexOfFirst { it.name.contains("Live 2", ignoreCase = true) }
            val preferredIndex = when {
                live2Index >= 0 -> live2Index
                channels.size > 1 -> 1
                else -> 0
            }
            activeChannelIndex = preferredIndex
            highlightActiveChannel(preferredIndex)
            Log.i(TAG, "Canal inicial seleccionado: ${channels[preferredIndex].name} (index=$preferredIndex)")
            if (preferredIndex != 0) {
                mainHandler.postDelayed({
                    if (currentMode == PageMode.EVENT_PLAYER && discoveredChannels.size > preferredIndex) {
                        switchToDiscoveredChannel(preferredIndex)
                    }
                }, 350)
            }
        }
    }

    private fun buildChannelButtons(channels: List<DiscoveredChannel>) {
        // 1. Selector en la barra de pantalla completa dedicada
        llDedicatedChannels?.let { container ->
            container.removeAllViews()
            channelButtonsDedicated.clear()

            channels.forEachIndexed { i, ch ->
                val btn = Button(context).apply {
                    text = ch.name
                    isFocusable = true
                    isFocusableInTouchMode = true
                    textSize = 11f
                    setPadding(dp(12), dp(2), dp(12), dp(2))
                    stylePill(this, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
                    setOnClickListener {
                        switchToDiscoveredChannel(i)
                    }
                }
                channelButtonsDedicated.add(btn)
                val params = LinearLayout.LayoutParams(-2, dp(34)).apply {
                    marginEnd = dp(6)
                }
                container.addView(btn, params)
            }
            container.visibility = View.VISIBLE
        }

        // 2. Selector en la barra integrada del Muro de Pits
        llPitWallChannels?.let { container ->
            container.removeAllViews()
            channelButtonsInline.clear()

            channels.forEachIndexed { i, ch ->
                val btn = Button(context).apply {
                    text = ch.name
                    isFocusable = true
                    isFocusableInTouchMode = true
                    textSize = 10f
                    setPadding(dp(10), dp(2), dp(10), dp(2))
                    stylePill(this, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
                    setOnClickListener {
                        switchToDiscoveredChannel(i)
                    }
                }
                channelButtonsInline.add(btn)
                val params = LinearLayout.LayoutParams(-2, dp(30)).apply {
                    marginEnd = dp(6)
                }
                container.addView(btn, params)
            }
            container.visibility = View.VISIBLE
        }
    }

    private fun highlightActiveChannel(index: Int) {
        channelButtonsDedicated.forEachIndexed { i, btn ->
            if (i == index) {
                stylePill(btn, Color.rgb(225, 6, 0), Color.WHITE)
            } else {
                stylePill(btn, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
            }
        }
        channelButtonsInline.forEachIndexed { i, btn ->
            if (i == index) {
                stylePill(btn, Color.rgb(225, 6, 0), Color.WHITE)
            } else {
                stylePill(btn, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
            }
        }
    }

    fun switchToDiscoveredChannel(index: Int) {
        if (index !in discoveredChannels.indices) return
        activeChannelIndex = index
        highlightActiveChannel(index)
        val ch = discoveredChannels[index]
        tvDedicatedSourceName.text = "${currentF1Event?.title ?: "F1"} · ${ch.name}"

        val switchJs = F1ContentDiscovery.buildSwitchChannelJs(index)
        webVideoView.evaluateJavascript(switchJs) {
            Log.d(TAG, "Cambio a canal $index: $it")
            mainHandler.postDelayed({ injectAdCleanAndAutoplay(webVideoView) }, 1000)
        }
    }

    private fun injectAdCleanAndAutoplay(view: WebView?) {
        val cleanJs = """
            (function() {
                var ads = document.querySelectorAll('#html1, #button1, .ads, [id*="banner"], [class*="banner"]');
                ads.forEach(function(el) { el.style.display = 'none'; el.remove(); });
                
                document.querySelectorAll('video').forEach(function(v) {
                    v.muted = false;
                    v.play().catch(function(){});
                });
                
                var unmute = document.getElementById('unmute') || document.querySelector('.clappr-play-button');
                if (unmute) { unmute.click(); }
            })();
        """.trimIndent()
        view?.evaluateJavascript(cleanJs, null)
    }

    // ======================================================================
    // Reproducción Unificada (Inline & Fullscreen)
    // ======================================================================

    private fun setupActions() {
        btnPlayerRetry.setOnClickListener { retryPlayback() }
        btnPlayerGoSettings.setOnClickListener { onNavigateToSettings() }

        btnDedicatedBack.setOnClickListener {
            closeDedicatedFullscreen()
            onExitDedicatedFullscreen()
        }
        btnDedicatedRetry.setOnClickListener { retryPlayback() }
        btnDedicatedGoSettings.setOnClickListener {
            closeDedicatedFullscreen()
            onNavigateToSettings()
        }

        btnDedicatedPlayPause.setOnClickListener { togglePlayPause() }
        btnDedicatedReload.setOnClickListener { reloadCurrent() }
        btnDedicatedMute?.setOnClickListener { toggleMute() }
        btnDedicatedSessions?.setOnClickListener { returnToDirectory() }

        // Botones integrados del Muro de Pits
        btnPitWallReload?.setOnClickListener { reloadCurrent() }
        btnPitWallMute?.setOnClickListener { toggleMute() }
        btnPitWallSessions?.setOnClickListener { returnToDirectory() }
        btnPitWallFullscreenInline?.setOnClickListener { openDedicatedFullscreen() }
    }

    fun playStream(
        url: String,
        type: String? = "HLS",
        sourceName: String? = null,
        startInFullscreen: Boolean = false
    ) {
        currentPlayingSourceUrl = url
        currentPlayingSourceName = sourceName ?: "Transmisión en Vivo"
        val cleanType = type?.uppercase() ?: "HLS"
        currentPlayingSourceType = cleanType

        tvDedicatedSourceName.text = currentPlayingSourceName
        tvDedicatedSourceTypeBadge.text = cleanType

        if (startInFullscreen) {
            openDedicatedFullscreen()
        } else {
            layoutDedicatedPlayback.visibility = View.GONE
            isDedicatedFullscreenActive = false
        }

        if (cleanType == "WEB" || cleanType == "WEBVIEW" || cleanType == "HTML") {
            exoPlayer?.pause()
            exoPlayer?.stop()
            playerView.visibility = View.GONE
            playerViewDedicated.visibility = View.GONE

            llPitWallPlayerBar?.visibility = View.VISIBLE

            // Asegurar que webVideoView esté en el contenedor correcto según el modo
            ensureWebViewParent()

            webVideoView.visibility = View.VISIBLE
            val buffTitle = "Cargando señal: $currentPlayingSourceName"
            if (isDedicatedFullscreenActive) {
                showDedicatedBuffering(buffTitle, "Conectando...")
            } else {
                showPlayerBufferingState(buffTitle, "Conectando...")
            }

            webVideoView.stopLoading()
            webVideoView.loadUrl(url)
            webVideoView.requestFocus()
        } else {
            webVideoView.stopLoading()
            webVideoView.loadUrl("about:blank")
            webVideoView.visibility = View.GONE

            llDedicatedChannels?.visibility = View.GONE
            llPitWallChannels?.visibility = View.GONE
            llPitWallPlayerBar?.visibility = View.GONE

            if (isDedicatedFullscreenActive) {
                playerView.visibility = View.GONE
                playerViewDedicated.visibility = View.VISIBLE
                playerViewDedicated.requestFocus()
                showDedicatedBuffering("Conectando con: $currentPlayingSourceName", url)
            } else {
                playerViewDedicated.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                showPlayerBufferingState("Conectando con: $currentPlayingSourceName", url)
            }

            val player = exoPlayer ?: return
            val mediaItem = when (cleanType) {
                "DASH" -> MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_MPD).build()
                "MP4" -> MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP4).build()
                else -> MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            }

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    /**
     * Preserva la sesión multimedia realizando reparenting dinámico de la única instancia
     * de WebView hacia la superficie de pantalla completa dedicada.
     */
    fun openDedicatedFullscreen() {
        if (isDedicatedFullscreenActive) return
        isDedicatedFullscreenActive = true
        layoutDedicatedPlayback.visibility = View.VISIBLE

        tvDedicatedSourceName.text = currentPlayingSourceName ?: "Transmisión en Vivo"
        tvDedicatedSourceTypeBadge.text = currentPlayingSourceType ?: "VIDEO"

        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            playerViewDedicated.visibility = View.GONE
            playerView.visibility = View.GONE

            // Reparenting limpio sin recrear ni reiniciar el WebView
            if (webVideoView.parent != frameDedicatedSurface) {
                (webVideoView.parent as? ViewGroup)?.removeView(webVideoView)
                frameDedicatedSurface.addView(
                    webVideoView,
                    0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            webVideoView.visibility = View.VISIBLE
            webVideoView.requestFocus()
        } else {
            playerView.visibility = View.GONE
            playerViewDedicated.visibility = View.VISIBLE
            playerViewDedicated.player = exoPlayer
            playerViewDedicated.requestFocus()
        }
        highlightActiveChannel(activeChannelIndex)
    }

    /**
     * Devuelve la instancia de WebView a la superficie integrada del Muro de Pits,
     * manteniendo intacto el estado de reproducción sin recargas.
     */
    fun closeDedicatedFullscreen() {
        if (!isDedicatedFullscreenActive) return
        isDedicatedFullscreenActive = false
        layoutDedicatedPlayback.visibility = View.GONE

        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            playerViewDedicated.visibility = View.GONE

            if (webVideoView.parent != framePitWallSurface) {
                (webVideoView.parent as? ViewGroup)?.removeView(webVideoView)
                val targetIndex = if (framePitWallSurface.childCount > 1) 1 else 0
                framePitWallSurface.addView(
                    webVideoView,
                    targetIndex,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            webVideoView.visibility = View.VISIBLE
            webVideoView.requestFocus()
        } else {
            playerViewDedicated.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playerView.player = exoPlayer
            playerView.requestFocus()
        }
        highlightActiveChannel(activeChannelIndex)
    }

    private fun ensureWebViewParent() {
        val targetContainer = if (isDedicatedFullscreenActive) frameDedicatedSurface else framePitWallSurface
        if (webVideoView.parent != targetContainer) {
            (webVideoView.parent as? ViewGroup)?.removeView(webVideoView)
            val insertIndex = if (isDedicatedFullscreenActive) 0 else (if (framePitWallSurface.childCount > 1) 1 else 0)
            targetContainer.addView(
                webVideoView,
                insertIndex,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    // ======================================================================
    // Controles de Reproductor (Audio, Recarga, Sesiones, Pausa)
    // ======================================================================

    fun toggleMute() {
        isMuted = !isMuted
        val muteLabel = if (isMuted) "🔇 Silenciado" else "🔊 Audio"

        btnDedicatedMute?.text = muteLabel
        btnPitWallMute?.text = muteLabel

        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            val muteJs = """
                (function() {
                    var targetMute = ${isMuted};
                    document.querySelectorAll('video').forEach(function(v) {
                        v.muted = targetMute;
                    });
                    var unmuteBtn = document.getElementById('unmute');
                    if (unmuteBtn && !targetMute) { unmuteBtn.click(); }
                    return targetMute ? 'muted' : 'unmuted';
                })();
            """.trimIndent()
            webVideoView.evaluateJavascript(muteJs, null)
        } else {
            exoPlayer?.volume = if (isMuted) 0f else 1f
        }
    }

    fun reloadCurrent() {
        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            webVideoView.reload()
        } else {
            retryPlayback()
        }
    }

    fun returnToDirectory() {
        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            currentMode = PageMode.DIRECTORY
            discoveredChannels = emptyList()
            llDedicatedChannels?.visibility = View.GONE
            llPitWallChannels?.visibility = View.GONE
            val dirUrl = currentPlayingSourceUrl ?: "https://futbolvivo.tv/"
            webVideoView.loadUrl(dirUrl)
        }
    }

    fun togglePlayPause() {
        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            val playPauseJs = """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        if (v.paused) { v.play(); return 'playing'; }
                        else { v.pause(); return 'paused'; }
                    }
                    return 'no_video';
                })();
            """.trimIndent()
            webVideoView.evaluateJavascript(playPauseJs, null)
        } else {
            val player = exoPlayer ?: return
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun retryPlayback() {
        val url = currentPlayingSourceUrl ?: return
        playStream(url, currentPlayingSourceType, currentPlayingSourceName, isDedicatedFullscreenActive)
    }

    fun handleBackPressed(): Boolean {
        // 1. Salir de pantalla completa HTML5 si está activa
        if (customView != null) {
            hideCustomViewInternal()
            return true
        }

        // 2. Cerrar ventanas emergentes si existen
        if (activePopupWebViews.isNotEmpty()) {
            closeAllPopups()
            return true
        }

        // 3. Si está en pantalla completa dedicada, volver a la vista integrada de Muro de Pits
        if (isDedicatedFullscreenActive) {
            closeDedicatedFullscreen()
            onExitDedicatedFullscreen()
            return true
        }

        // 4. Si estamos en la página del evento, volver al directorio de sesiones
        if (currentMode == PageMode.EVENT_PLAYER) {
            returnToDirectory()
            return true
        }

        // 5. Historial del WebView
        if (webVideoView.visibility == View.VISIBLE && webVideoView.canGoBack()) {
            webVideoView.goBack()
            return true
        }

        return false
    }

    private fun dismissOverlays() {
        layoutDedicatedStatusOverlay.visibility = View.GONE
        layoutPlayerStatusOverlay.visibility = View.GONE
    }

    fun showPlayerBufferingState(title: String, message: String? = null) {
        pbPlayerLoading.visibility = View.VISIBLE
        tvPlayerStatusTitle.text = title
        tvPlayerStatusMessage.text = message ?: "Conectando..."
        layoutPlayerErrorActions.visibility = View.GONE
        layoutPlayerStatusOverlay.visibility = View.VISIBLE
    }

    fun showPlayerErrorState(errorMessage: String) {
        pbPlayerLoading.visibility = View.GONE
        tvPlayerStatusTitle.text = "Error de Transmisión"
        tvPlayerStatusMessage.text = errorMessage
        layoutPlayerErrorActions.visibility = View.VISIBLE
        layoutPlayerStatusOverlay.visibility = View.VISIBLE
    }

    fun showDedicatedBuffering(title: String, message: String? = null) {
        pbDedicatedLoading.visibility = View.VISIBLE
        tvDedicatedStatusTitle.text = title
        tvDedicatedStatusMessage.text = message ?: "Conectando con la señal de video..."
        layoutDedicatedErrorActions.visibility = View.GONE
        layoutDedicatedStatusOverlay.visibility = View.VISIBLE
    }

    fun showDedicatedError(errorMessage: String) {
        pbDedicatedLoading.visibility = View.GONE
        tvDedicatedStatusTitle.text = "Error de Reproducción"
        tvDedicatedStatusMessage.text = errorMessage
        layoutDedicatedErrorActions.visibility = View.VISIBLE
        layoutDedicatedStatusOverlay.visibility = View.VISIBLE
    }

    fun pause() {
        exoPlayer?.pause()
        webVideoView.onPause()
    }

    /** Resume WebView timers and media after MainActivity returns to the foreground. */
    fun resume() {
        webVideoView.onResume()
        webVideoView.resumeTimers()
    }

    fun stop() {
        exoPlayer?.stop()
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE

        playerView.visibility = View.GONE
        playerViewDedicated.visibility = View.GONE
        layoutPlayerStatusOverlay.visibility = View.GONE
        layoutDedicatedStatusOverlay.visibility = View.GONE
        layoutDedicatedPlayback.visibility = View.GONE
        llPitWallPlayerBar?.visibility = View.GONE
        isDedicatedFullscreenActive = false

        currentPlayingSourceUrl = null
        currentPlayingSourceName = null
        currentPlayingSourceType = null
    }

    fun isPlaying(): Boolean {
        return if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            webVideoView.visibility == View.VISIBLE
        } else {
            exoPlayer?.isPlaying == true
        }
    }

    fun release() {
        closeAllPopups()
        exoPlayer?.release()
        exoPlayer = null
        webVideoView.stopLoading()
        webVideoView.clearHistory()
        webVideoView.destroy()
    }

    // ======================================================================
    // Helpers de estilo y sanitización
    // ======================================================================

    private fun sanitizeUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        return try {
            val uri = Uri.parse(rawUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            "$scheme://$host$path"
        } catch (_: Exception) {
            rawUrl.substringBefore('?')
        }
    }

    private fun stylePill(btn: Button, bgColor: Int, textColor: Int) {
        val normalState = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(bgColor)
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }

        val focusedState = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(Color.rgb(0, 160, 255))
            setStroke(dp(2), Color.WHITE)
        }

        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedState)
            addState(intArrayOf(), normalState)
        }

        btn.background = stateList
        btn.setTextColor(textColor)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
