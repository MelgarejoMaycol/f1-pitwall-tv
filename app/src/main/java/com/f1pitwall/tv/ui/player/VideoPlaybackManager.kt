package com.f1pitwall.tv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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

class VideoPlaybackManager(
    private val context: Context,
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
    private val webVideoViewDedicated: WebView,
    private val layoutDedicatedTopBar: LinearLayout,
    private val btnDedicatedBack: Button,
    private val tvDedicatedSourceName: TextView,
    private val tvDedicatedSourceTypeBadge: TextView,
    private val btnDedicatedPlayPause: Button,
    private val btnDedicatedReload: Button,
    private val layoutDedicatedStatusOverlay: LinearLayout,
    private val pbDedicatedLoading: ProgressBar,
    private val tvDedicatedStatusTitle: TextView,
    private val tvDedicatedStatusMessage: TextView,
    private val layoutDedicatedErrorActions: LinearLayout,
    private val btnDedicatedRetry: Button,
    private val btnDedicatedGoSettings: Button,
    private val llDedicatedChannels: LinearLayout? = null,
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

    // Estado del motor de canales F1
    private var discoveredChannels: List<DiscoveredChannel> = emptyList()
    private var activeChannelIndex: Int = 0
    private var channelButtons: MutableList<Button> = mutableListOf()
    private var currentF1Event: DiscoveredEvent? = null

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
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                    "Formato incompatible: La transmisión no pudo ser decodificada (verifica si es HLS, DASH o MP4)."
                else ->
                    "Error de reproducción: ${fallbackMessage ?: "No se pudo reproducir la transmisión."}"
            }
        }
    }

    // Bridge para descubrimiento en WebView
    inner class WebDiscoveryBridge(private val webView: WebView) {
        @JavascriptInterface
        fun onF1Events(json: String) {
            mainHandler.post {
                handleF1EventsDiscovered(webView, json)
            }
        }

        @JavascriptInterface
        fun onChannels(json: String) {
            mainHandler.post {
                handleChannelsDiscovered(json)
            }
        }

        @JavascriptInterface
        fun onLog(msg: String) {
            Log.d(TAG, "Bridge: $msg")
        }
    }

    init {
        initExoPlayer()
        setupWebViews()
        setupActions()
    }

    private fun initExoPlayer() {
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
                    layoutPlayerStatusOverlay.visibility = View.GONE
                    layoutDedicatedStatusOverlay.visibility = View.GONE
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
                        layoutPlayerStatusOverlay.visibility = View.GONE
                        layoutDedicatedStatusOverlay.visibility = View.GONE
                    }
                    Player.STATE_ENDED -> {
                        val endMsg = "La transmisión ha finalizado"
                        showPlayerErrorState(endMsg)
                        showDedicatedError(endMsg)
                    }
                    Player.STATE_IDLE -> {
                        // Estado en reposo
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViews() {
        configureWebView(webVideoView, isDedicated = false)
        configureWebView(webVideoViewDedicated, isDedicated = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView, isDedicated: Boolean) {
        webView.apply {
            // Aceleración por hardware obligatoria en TV para video HTML5/WebGL
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
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(webView, true)
                }
            }

            addJavascriptInterface(WebDiscoveryBridge(webView), "F1Bridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    val scheme = url.scheme ?: ""
                    val isWeb = scheme == "https" || scheme == "http"
                    return !isWeb // false = navegar internamente sin abrir navegadores externos
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val title = "Cargando señal: ${currentPlayingSourceName ?: "F1 Web"}"
                    if (isDedicated) {
                        showDedicatedBuffering(title, "Conectando con reproductor...")
                    } else {
                        showPlayerBufferingState(title, "Conectando con reproductor...")
                    }

                    // Watchdog de seguridad: el overlay NUNCA debe quedarse más de 4 segundos tapando el video
                    mainHandler.postDelayed({
                        dismissOverlays()
                    }, 4000)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    dismissOverlays()
                    onPlaybackStarted()
                    webView.requestFocus()

                    val currentUrl = url.orEmpty().lowercase()
                    val isEventPage = currentUrl.contains("soccer.html") ||
                            currentUrl.contains("us.html") ||
                            currentUrl.contains("latin.html")

                    if (isEventPage) {
                        // Estamos en la página del evento: extraer canales y limpiar UI
                        view?.evaluateJavascript(F1ContentDiscovery.buildEventPageScannerJs(), null)
                    } else if (currentUrl.contains("futbolvivo.tv") || currentUrl.contains("futbollibre")) {
                        // Estamos en el directorio: buscar eventos de F1 automáticamente
                        view?.evaluateJavascript(F1ContentDiscovery.buildDirectoryScannerJs(), null)
                    }

                    // Inyección de autolimpieza de banners publicitarios y unmute automático
                    injectAdCleanAndAutoplay(view)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val desc = error?.description?.toString() ?: "No se pudo conectar con el sitio web."
                        val msg = "Error al abrir la señal: $desc"
                        if (isDedicated) {
                            showDedicatedError(msg)
                        } else {
                            showPlayerErrorState(msg)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                    Log.d(TAG, "Permisos de media concedidos en WebView")
                }

                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS[${msg?.messageLevel()}]: ${msg?.message()}")
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

    private fun dismissOverlays() {
        layoutDedicatedStatusOverlay.visibility = View.GONE
        layoutPlayerStatusOverlay.visibility = View.GONE
    }

    /**
     * Inyecta script para eliminar banners flotantes molestos y asegurar reproducción activa
     */
    private fun injectAdCleanAndAutoplay(view: WebView?) {
        val cleanJs = """
            (function() {
                // 1. Eliminar overlays de publicidad comunes
                var ads = document.querySelectorAll('#html1, #button1, .ads, [id*="banner"], [class*="banner"]');
                ads.forEach(function(el) { el.style.display = 'none'; el.remove(); });
                
                // 2. Intentar desmutear y reproducir videos HTML5
                document.querySelectorAll('video').forEach(function(v) {
                    v.muted = false;
                    v.play().catch(function(){});
                });
                
                // 3. Clic automático en botones de desmuteo conocidos
                var unmute = document.getElementById('unmute') || document.querySelector('.clappr-play-button');
                if (unmute) { unmute.click(); }
            })();
        """.trimIndent()
        view?.evaluateJavascript(cleanJs, null)
    }

    // ======================================================================
    // Manejo de eventos descubiertos
    // ======================================================================

    private fun handleF1EventsDiscovered(webView: WebView, json: String) {
        val events = F1ContentDiscovery.parseF1Events(json)
        Log.d(TAG, "F1 Events descubiertos en VideoPlaybackManager: ${events.size}")
        if (events.isNotEmpty()) {
            val target = events.first()
            currentF1Event = target
            tvDedicatedSourceName.text = target.title
            Log.d(TAG, "Navegando a F1 target: ${target.title} -> ${target.url}")
            webView.loadUrl(target.url)
        }
    }

    private fun handleChannelsDiscovered(json: String) {
        val (title, channels) = F1ContentDiscovery.parseEventPageChannels(json)
        Log.d(TAG, "Canales descubiertos en VideoPlaybackManager: ${channels.size}")
        discoveredChannels = channels
        if (title.isNotBlank()) {
            tvDedicatedSourceName.text = title
        }
        if (channels.isNotEmpty() && llDedicatedChannels != null) {
            buildDedicatedChannelButtons(channels)
            activeChannelIndex = 0
            highlightDedicatedChannel(0)
        }
    }

    private fun buildDedicatedChannelButtons(channels: List<DiscoveredChannel>) {
        val container = llDedicatedChannels ?: return
        container.removeAllViews()
        channelButtons.clear()

        channels.forEachIndexed { i, ch ->
            val btn = Button(context).apply {
                text = ch.name
                isFocusable = true
                isFocusableInTouchMode = true
                textSize = 11f
                setPadding(dp(10), dp(2), dp(10), dp(2))
                stylePill(this, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
                setOnClickListener {
                    switchToDiscoveredChannel(i)
                }
            }
            channelButtons.add(btn)
            val params = LinearLayout.LayoutParams(-2, dp(34)).apply {
                marginEnd = dp(6)
            }
            container.addView(btn, params)
        }

        container.visibility = View.VISIBLE
    }

    private fun highlightDedicatedChannel(index: Int) {
        channelButtons.forEachIndexed { i, btn ->
            if (i == index) {
                stylePill(btn, Color.rgb(225, 6, 0), Color.WHITE)
            } else {
                stylePill(btn, Color.rgb(25, 35, 50), Color.rgb(180, 210, 240))
            }
        }
    }

    private fun switchToDiscoveredChannel(index: Int) {
        if (index !in discoveredChannels.indices) return
        activeChannelIndex = index
        highlightDedicatedChannel(index)
        val ch = discoveredChannels[index]
        tvDedicatedSourceName.text = "${currentF1Event?.title ?: "F1"} · ${ch.name}"

        val switchJs = F1ContentDiscovery.buildSwitchChannelJs(index)
        val targetWv = if (isDedicatedFullscreenActive) webVideoViewDedicated else webVideoView
        targetWv.evaluateJavascript(switchJs) {
            Log.d(TAG, "Cambio a canal $index: $it")
            mainHandler.postDelayed({ injectAdCleanAndAutoplay(targetWv) }, 1000)
        }
    }

    private fun stylePill(btn: Button, bgColor: Int, textColor: Int) {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(bgColor)
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        btn.background = shape
        btn.setTextColor(textColor)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    // ======================================================================
    // Reproducción
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

            if (isDedicatedFullscreenActive) {
                webVideoView.visibility = View.GONE
                webVideoViewDedicated.visibility = View.VISIBLE
                showDedicatedBuffering("Cargando señal: $currentPlayingSourceName", "Conectando...")
                webVideoViewDedicated.stopLoading()
                webVideoViewDedicated.loadUrl(url)
                webVideoViewDedicated.requestFocus()
            } else {
                webVideoViewDedicated.visibility = View.GONE
                webVideoView.visibility = View.VISIBLE
                showPlayerBufferingState("Cargando señal: $currentPlayingSourceName", "Conectando...")
                webVideoView.stopLoading()
                webVideoView.loadUrl(url)
                webVideoView.requestFocus()
            }
        } else {
            webVideoView.stopLoading()
            webVideoView.loadUrl("about:blank")
            webVideoView.visibility = View.GONE

            webVideoViewDedicated.stopLoading()
            webVideoViewDedicated.loadUrl("about:blank")
            webVideoViewDedicated.visibility = View.GONE

            llDedicatedChannels?.visibility = View.GONE

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

    fun openDedicatedFullscreen() {
        isDedicatedFullscreenActive = true
        layoutDedicatedPlayback.visibility = View.VISIBLE

        tvDedicatedSourceName.text = currentPlayingSourceName ?: "Transmisión en Vivo"
        tvDedicatedSourceTypeBadge.text = currentPlayingSourceType ?: "VIDEO"

        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            playerViewDedicated.visibility = View.GONE
            webVideoViewDedicated.visibility = View.VISIBLE
            webVideoView.visibility = View.GONE

            val url = currentPlayingSourceUrl
            if (url != null && webVideoViewDedicated.url != url) {
                showDedicatedBuffering("Cargando señal: $currentPlayingSourceName", "Conectando...")
                webVideoViewDedicated.loadUrl(url)
            }
            webVideoViewDedicated.requestFocus()
        } else {
            webVideoViewDedicated.visibility = View.GONE
            playerViewDedicated.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            playerViewDedicated.requestFocus()
        }
    }

    fun closeDedicatedFullscreen() {
        isDedicatedFullscreenActive = false
        layoutDedicatedPlayback.visibility = View.GONE

        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            webVideoViewDedicated.stopLoading()
            webVideoViewDedicated.visibility = View.GONE
            webVideoView.visibility = View.VISIBLE
            webVideoView.requestFocus()
        } else {
            playerViewDedicated.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playerView.requestFocus()
        }
    }

    fun togglePlayPause() {
        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            retryPlayback()
        } else {
            val player = exoPlayer ?: return
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun reloadCurrent() {
        if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            if (isDedicatedFullscreenActive) {
                webVideoViewDedicated.reload()
            } else {
                webVideoView.reload()
            }
        } else {
            retryPlayback()
        }
    }

    fun retryPlayback() {
        val url = currentPlayingSourceUrl ?: return
        playStream(url, currentPlayingSourceType, currentPlayingSourceName, isDedicatedFullscreenActive)
    }

    fun handleBackPressed(): Boolean {
        if (isDedicatedFullscreenActive) {
            if ((currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") && webVideoViewDedicated.canGoBack()) {
                webVideoViewDedicated.goBack()
                return true
            }
            closeDedicatedFullscreen()
            onExitDedicatedFullscreen()
            return true
        }

        if ((currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") && webVideoView.visibility == View.VISIBLE && webVideoView.canGoBack()) {
            webVideoView.goBack()
            return true
        }

        return false
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
        webVideoViewDedicated.onPause()
    }

    fun stop() {
        exoPlayer?.stop()
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE

        webVideoViewDedicated.stopLoading()
        webVideoViewDedicated.loadUrl("about:blank")
        webVideoViewDedicated.visibility = View.GONE

        playerView.visibility = View.GONE
        playerViewDedicated.visibility = View.GONE
        layoutPlayerStatusOverlay.visibility = View.GONE
        layoutDedicatedStatusOverlay.visibility = View.GONE
        layoutDedicatedPlayback.visibility = View.GONE
        isDedicatedFullscreenActive = false

        currentPlayingSourceUrl = null
        currentPlayingSourceName = null
        currentPlayingSourceType = null
    }

    fun isPlaying(): Boolean {
        return if (currentPlayingSourceType == "WEB" || currentPlayingSourceType == "WEBVIEW" || currentPlayingSourceType == "HTML") {
            webVideoView.visibility == View.VISIBLE || webVideoViewDedicated.visibility == View.VISIBLE
        } else {
            exoPlayer?.isPlaying == true
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        webVideoView.destroy()
        webVideoViewDedicated.destroy()
    }
}
