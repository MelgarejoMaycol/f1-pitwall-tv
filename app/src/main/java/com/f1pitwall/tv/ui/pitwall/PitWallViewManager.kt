package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.FutbolVivoExtractor
import com.f1pitwall.tv.ui.LeaderboardAdapter
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

class PitWallViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onNavigateToSettings: () -> Unit,
    private val defaultWebSourceUrl: String? = null
) {
    private val context: Context = rootView.context
    private val futbolVivoExtractor = FutbolVivoExtractor(context)

    // Video Player & Standby
    private val playerSurface: FrameLayout = rootView.findViewById(R.id.framePitWallSurface)
    private val playerView: PlayerView = rootView.findViewById(R.id.playerView)
    private val webVideoView: WebView = rootView.findViewById(R.id.webVideoView)
    private val layoutPlayerStandby: View = rootView.findViewById(R.id.layoutPlayerStandby)
    private val tvStandbyStatusBadge: TextView = rootView.findViewById(R.id.tvStandbyStatusBadge)
    private val tvStandbyTitle: TextView = rootView.findViewById(R.id.tvStandbyTitle)
    private val tvStandbySubtitle: TextView = rootView.findViewById(R.id.tvStandbySubtitle)
    private val btnStandbyPreview: Button = rootView.findViewById(R.id.btnStandbyPreview)
    private val btnStandbyRecap: Button = rootView.findViewById(R.id.btnStandbyRecap)
    private val btnStandbyOpenWeb: Button = rootView.findViewById(R.id.btnStandbyOpenWeb)
    private val btnStandbyStopVideo: Button = rootView.findViewById(R.id.btnStandbyStopVideo)

    // Alert Banner & Content Hub Bar
    private val layoutLiveSessionAlert: View = rootView.findViewById(R.id.layoutLiveSessionAlert)
    private val tvLiveSessionAlertTitle: TextView = rootView.findViewById(R.id.tvLiveSessionAlertTitle)
    private val btnLiveSessionAction: Button = rootView.findViewById(R.id.btnLiveSessionAction)
    private val layoutServerSelector: View = rootView.findViewById(R.id.layoutServerSelector)
    private val tvServerLabel: TextView = rootView.findViewById(R.id.tvServerLabel)
    private val btnServer1: Button = rootView.findViewById(R.id.btnServer1)
    private val btnServer2: Button = rootView.findViewById(R.id.btnServer2)
    private val btnServer3: Button = rootView.findViewById(R.id.btnServer3)
    private var selectedServerIndex: Int = 0
    private val btnContentPreview: Button = rootView.findViewById(R.id.btnContentPreview)
    private val btnContentRecap: Button = rootView.findViewById(R.id.btnContentRecap)
    private val btnContentBoxes: Button = rootView.findViewById(R.id.btnContentBoxes)

    // Radio
    private val tvRadioTitle: TextView = rootView.findViewById(R.id.tvRadioTitle)
    private val btnPlayRadio: Button = rootView.findViewById(R.id.btnPlayRadio)
    private val btnRadioVer: Button = rootView.findViewById(R.id.btnRadioVer)
    private val btnRadioNor: Button = rootView.findViewById(R.id.btnRadioNor)
    private val btnRadioLec: Button = rootView.findViewById(R.id.btnRadioLec)
    private val btnRadioHam: Button = rootView.findViewById(R.id.btnRadioHam)
    private val btnRadioRus: Button = rootView.findViewById(R.id.btnRadioRus)
    private val btnRadioPia: Button = rootView.findViewById(R.id.btnRadioPia)
    private val btnRadioAlo: Button = rootView.findViewById(R.id.btnRadioAlo)

    // Header Status & Live Refresh
    private val tvSessionTitle: TextView = rootView.findViewById(R.id.tvSessionTitle)
    private val btnRefreshLive: Button? = rootView.findViewById(R.id.btnRefreshLive)

    // Metrics Switcher & Containers
    private val btnMetricsTiming: Button = rootView.findViewById(R.id.btnMetricsTiming)
    private val btnMetricsTelemetry: Button = rootView.findViewById(R.id.btnMetricsTelemetry)
    private val rvLeaderboard: RecyclerView = rootView.findViewById(R.id.rvLeaderboard)
    private val layoutTelemetryPanel: View = rootView.findViewById(R.id.layoutTelemetryPanel)

    // Telemetry Gauges
    private val tvTelemetryDriverHeader: TextView = rootView.findViewById(R.id.tvTelemetryDriverHeader)
    private val tvTelemetryDrs: TextView = rootView.findViewById(R.id.tvTelemetryDrs)
    private val tvTelemetrySpeed: TextView = rootView.findViewById(R.id.tvTelemetrySpeed)
    private val tvTelemetryGear: TextView = rootView.findViewById(R.id.tvTelemetryGear)
    private val tvTelemetryRpm: TextView = rootView.findViewById(R.id.tvTelemetryRpm)
    private val pbTelemetryRpm: ProgressBar = rootView.findViewById(R.id.pbTelemetryRpm)
    private val tvTelemetryThrottle: TextView = rootView.findViewById(R.id.tvTelemetryThrottle)
    private val pbTelemetryThrottle: ProgressBar = rootView.findViewById(R.id.pbTelemetryThrottle)
    private val tvTelemetryBrake: TextView = rootView.findViewById(R.id.tvTelemetryBrake)
    private val pbTelemetryBrake: ProgressBar = rootView.findViewById(R.id.pbTelemetryBrake)
    private val btnTelemetryDemoToggle: Button = rootView.findViewById(R.id.btnTelemetryDemoToggle)

    // State & Players
    private var exoPlayer: ExoPlayer? = null
    private var radioPlayer: ExoPlayer? = null
    private var latestRadioUrl: String? = null
    private var currentSessionKey: Long? = null
    private var isLiveSessionActive: Boolean = false
    private var isUpcomingSession: Boolean = false
    private var currentScheduleTitle: String = "Calendario F1 en preparación"
    private var futbolVivoStatusText: String = "FÚTBOL LIBRE • BUSCANDO EVENTOS F1"
    private var discoveredFutbolEventUrl: String? = null
    private var discoveredAlternativeUrls: List<String> = emptyList()
    private var discoveredFutbolEventTitle: String = "Evento de Fórmula 1"
    private var currentWebUrl: String? = null
    private var customFullScreenView: View? = null
    private var customFullScreenCallback: WebChromeClient.CustomViewCallback? = null

    val leaderboardAdapter = LeaderboardAdapter { item ->
        tvTelemetryDriverHeader.text = "#${item.driverNumber} ${item.fullName} • ${item.teamName}"
        btnMetricsTelemetry.performClick()
        loadDriverTelemetry(item.driverNumber)
    }

    init {
        setupWebView()
        initPlayers()
        setupListeners()
        setupLeaderboardView()
        setupFutbolVivoDiscovery()
    }

    /**
     * Loads a user-selected website as a top-level page. The provider's own
     * JavaScript, cookies and HTML5 player are handled by Android WebView.
     * Server-side frame and access policies are not bypassed.
     */
    private fun setupWebView() {
        webVideoView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace(" Mobile", "")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(webVideoView, true)
                }
            }

            addJavascriptInterface(PitWallBridge(), "AndroidPitWallBridge")

            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    triggerPlayerTap()
                    true
                } else {
                    false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return null
                    val isAd = listOf(
                        "acscdn.com", "al5sm.com", "deltalcherup.com", "histats.com",
                        "popads", "adsterra", "doubleclick", "googlesyndication",
                        "googleads", "adnxs", "propellerads", "trafficjunky",
                        "exoclick", "juicyads", "syndication", "adsystem", "adservice"
                    ).any { url.contains(it) }
                    if (isAd) {
                        return WebResourceResponse("text/javascript", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: ""
                    val host = uri.host?.lowercase() ?: ""
                    if (scheme != "http" && scheme != "https") return true

                    val isStreamHost = listOf(
                        "vivogoal", "futbol", "golygoals", "kora-plus",
                        "sport", "streameast", "strm-api", "cdn"
                    ).any { host.contains(it) }

                    if (!isStreamHost && request.isForMainFrame) {
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val reason = error?.description?.toString() ?: "error de red"
                        Toast.makeText(context, "No se pudo cargar la página: " + reason, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                        Toast.makeText(
                            context,
                            "El sitio respondió HTTP " + errorResponse?.statusCode,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val sessionPrefix = if (isLiveSessionActive) "🔴 EN VIVO" else "⏳ PRÓXIMA SESIÓN"
                    tvLiveSessionAlertTitle.text = "$sessionPrefix: $currentScheduleTitle"
                    btnLiveSessionAction.text = "🔄 Refrescar"

                    val cleanJs = """
                        (function() {
                            try { window.open = function() { return null; }; } catch(e) {}
                            function cleanAndMaximize() {
                                const style = document.createElement('style');
                                style.id = '__f1_tv_clean';
                                style.innerHTML = `
                                    html, body {
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        width: 100vw !important;
                                        height: 100vh !important;
                                        background: #000000 !important;
                                        overflow: hidden !important;
                                    }
                                    header, footer, nav, .se-match-head, .se-teams, .se-match-title,
                                    .se-tabs, .navbar, .se-footer, .nav-mobile-only, .se-home-link,
                                    #streamTabs, #matchTitle, #matchTeams, .se-sport-pill,
                                    .histats, [id*="histats"], [class*="histats"], [id*="aclib"] {
                                        display: none !important;
                                    }
                                    .se-hero, .se-container, .se-match-card, .se-player, .se-player-wrap, #playerWrap, #playerContainer {
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100vw !important;
                                        height: 100vh !important;
                                        max-width: 100vw !important;
                                        max-height: 100vh !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        border: none !important;
                                        border-radius: 0 !important;
                                        background: #000000 !important;
                                        z-index: 999999 !important;
                                    }
                                    iframe {
                                        position: absolute !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100% !important;
                                        height: 100% !important;
                                        border: none !important;
                                        background: #000 !important;
                                    }
                                `;
                                if (!document.getElementById('__f1_tv_clean')) {
                                    document.head.appendChild(style);
                                }

                                document.querySelectorAll('video').forEach(v => {
                                    v.muted = false;
                                    v.play().catch(() => {
                                        v.muted = true;
                                        v.play().catch(() => {});
                                    });
                                });

                                document.querySelectorAll('div').forEach(d => {
                                    if (d.id !== 'playerWrap' && d.id !== 'playerContainer' && !d.classList.contains('se-player-wrap') && !d.classList.contains('se-player')) {
                                        const s = window.getComputedStyle(d);
                                        if ((s.position === 'fixed' || s.position === 'absolute') && parseInt(s.zIndex) > 999999) {
                                            d.remove();
                                        }
                                    }
                                });
                            }

                            function reportServers() {
                                try {
                                    if (window.currentMatch) {
                                        console.log("F1_DEBUG: currentMatch=" + JSON.stringify({
                                            id: window.currentMatch.id,
                                            name: window.currentMatch.name,
                                            streams: window.currentMatch.streams,
                                            channels: window.currentMatch.channels
                                        }));
                                    }
                                    let servers = [];
                                    let active = 0;
                                    if (window.currentMatch && Array.isArray(window.currentMatch.channels) && window.currentMatch.channels.length > 0) {
                                        servers = window.currentMatch.channels.map((c, i) => c.server_name || ('Opción ' + (i + 1)));
                                        active = typeof window.activeServer !== 'undefined' ? window.activeServer : 0;
                                    } else if (window.currentMatch && Array.isArray(window.currentMatch.streams) && window.currentMatch.streams.length > 0) {
                                        servers = window.currentMatch.streams.map((s, i) => s.language || s.name || ('Opción ' + (i + 1)));
                                        active = typeof window.activeStream !== 'undefined' ? window.activeStream : 0;
                                    } else {
                                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                                        if (tabs && tabs.length > 0) {
                                            tabs.forEach((t, i) => {
                                                servers.push(t.innerText.trim() || ('Opción ' + (i + 1)));
                                                if (t.classList.contains('active')) active = i;
                                            });
                                        }
                                    }
                                    if (servers.length > 0 && window.AndroidPitWallBridge) {
                                        window.AndroidPitWallBridge.onServersAvailable(servers.length, active, JSON.stringify(servers));
                                    }
                                } catch(e) {}
                            }

                            cleanAndMaximize();
                            reportServers();
                            setTimeout(function() { cleanAndMaximize(); reportServers(); }, 600);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); }, 1500);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(cleanJs, null)

                    view?.postDelayed({ triggerPlayerTap() }, 3000)
                    view?.postDelayed({ triggerPlayerTap() }, 5500)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    hideCustomView()
                    customFullScreenView = view
                    customFullScreenCallback = callback
                    webVideoView.visibility = View.GONE
                    playerSurface.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    view.bringToFront()
                    view.requestFocus()
                }

                override fun onHideCustomView() {
                    hideCustomView()
                }
            }
        }

        playerSurface.setOnClickListener {
            triggerPlayerTap()
        }
    }

    fun triggerPlayerTap() {
        if (webVideoView.visibility != View.VISIBLE) return
        val w = webVideoView.width.toFloat()
        val h = webVideoView.height.toFloat()
        if (w <= 0 || h <= 0) return
        val density = context.resources.displayMetrics.density

        val focusedView = rootView.findFocus() ?: btnServer1

        sendTouch(w / 2f, h / 2f)
        webVideoView.postDelayed({
            sendTouch(w / 2f, 35f * density)
            focusedView.post {
                focusedView.requestFocus()
            }
        }, 300)
    }

    private fun sendTouch(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        webVideoView.dispatchTouchEvent(down)
        webVideoView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun hideCustomView() {
        val fullscreen = customFullScreenView ?: return
        playerSurface.removeView(fullscreen)
        customFullScreenView = null
        val callback = customFullScreenCallback
        customFullScreenCallback = null
        webVideoView.visibility = View.VISIBLE
        callback?.onCustomViewHidden()
    }

    private fun isDirectMediaUrl(rawUrl: String): Boolean {
        val path = runCatching { Uri.parse(rawUrl).path.orEmpty().lowercase() }.getOrDefault("")
        return listOf(".m3u8", ".mpd", ".mp4", ".m4v").any { path.endsWith(it) }
    }

    fun openDiscoveredFutbolEvent() {
        val eventUrl = discoveredFutbolEventUrl
        if (eventUrl.isNullOrBlank()) {
            openDefaultWebSource("Fútbol Vivo / Fútbol Libre")
            return
        }

        val uri = runCatching { Uri.parse(eventUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
            openDefaultWebSource("Fútbol Vivo / Fútbol Libre")
            return
        }

        playWebPage(eventUrl, discoveredFutbolEventTitle)
    }

    private fun openDefaultWebSource(title: String) {
        val url = defaultWebSourceUrl
        if (url.isNullOrBlank()) {
            onNavigateToSettings()
            Toast.makeText(context, "Configura una fuente web en Ajustes de Video", Toast.LENGTH_LONG).show()
        } else {
            playWebPage(url, title)
        }
    }

    fun playWebPage(url: String, title: String = "Fuente web") {
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https") || parsed.host.isNullOrBlank()) {
            Toast.makeText(context, "Introduce una URL web válida (http o https)", Toast.LENGTH_LONG).show()
            return
        }

        var targetUrl = url
        if (selectedServerIndex > 0 && !url.contains("s=")) {
            val sep = if (url.contains("?")) "&" else "?"
            targetUrl = "$url${sep}s=$selectedServerIndex"
        }

        currentWebUrl = targetUrl
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🏎️ " + title
        webVideoView.loadUrl(targetUrl)
        btnServer1.post { btnServer1.requestFocus() }
    }

    fun handleBackPressed(): Boolean {
        if (customFullScreenView != null) {
            hideCustomView()
            return true
        }
        if (webVideoView.visibility == View.VISIBLE && webVideoView.canGoBack()) {
            webVideoView.goBack()
            return true
        }
        return false
    }

    private fun initPlayers() {
        exoPlayer = ExoPlayer.Builder(context).build()
        playerView.player = exoPlayer

        radioPlayer = ExoPlayer.Builder(context).build()

        // By default off-race: Standby visible, playerView hidden, NO auto-playing demo video!
        switchToStandby()
    }

    private fun setupListeners() {
        // Los accesos de contenido abren la fuente web configurada; no muestran vídeos de prueba.
        btnStandbyPreview.setOnClickListener { openDiscoveredFutbolEvent() }
        btnStandbyRecap.setOnClickListener { openDefaultWebSource("Cartelera de eventos F1") }
        btnStandbyOpenWeb.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }

        btnStandbyStopVideo.setOnClickListener {
            switchToStandby()
        }

        // Selección de servidores / opciones de señal
        btnServer1.setOnClickListener { selectServer(0) }
        btnServer2.setOnClickListener { selectServer(1) }
        btnServer3.setOnClickListener { selectServer(2) }
        btnServer1.isSelected = true

        // TV Remote Focus Chain
        btnServer1.nextFocusUpId = R.id.btnTabPitWall
        btnServer1.nextFocusRightId = R.id.btnServer2
        btnServer1.nextFocusDownId = R.id.btnPlayRadio
        btnServer1.nextFocusLeftId = R.id.btnServer1

        btnServer2.nextFocusUpId = R.id.btnTabPitWall
        btnServer2.nextFocusLeftId = R.id.btnServer1
        btnServer2.nextFocusRightId = R.id.btnServer3
        btnServer2.nextFocusDownId = R.id.btnPlayRadio

        btnServer3.nextFocusUpId = R.id.btnTabPitWall
        btnServer3.nextFocusLeftId = R.id.btnServer2
        btnServer3.nextFocusRightId = R.id.btnLiveSessionAction
        btnServer3.nextFocusDownId = R.id.btnPlayRadio

        btnLiveSessionAction.nextFocusUpId = R.id.btnTabPitWall
        btnLiveSessionAction.nextFocusLeftId = R.id.btnServer3
        btnLiveSessionAction.nextFocusRightId = R.id.btnMetricsTiming
        btnLiveSessionAction.nextFocusDownId = R.id.btnPlayRadio

        btnPlayRadio.nextFocusUpId = R.id.btnServer1
        btnRadioVer.nextFocusUpId = R.id.btnServer1
        btnRadioNor.nextFocusUpId = R.id.btnServer1
        btnRadioLec.nextFocusUpId = R.id.btnServer2
        btnRadioHam.nextFocusUpId = R.id.btnServer2
        btnRadioRus.nextFocusUpId = R.id.btnServer3
        btnRadioPia.nextFocusUpId = R.id.btnServer3
        btnRadioAlo.nextFocusUpId = R.id.btnLiveSessionAction

        // Botón de refrescar / recargar la señal en vivo si cae
        btnLiveSessionAction.setOnClickListener {
            refreshStream()
        }
        layoutLiveSessionAlert.setOnClickListener {
            refreshStream()
        }

        // Radio Player
        btnPlayRadio.setOnClickListener {
            latestRadioUrl?.let { url ->
                val mediaItem = MediaItem.fromUri(url)
                radioPlayer?.setMediaItem(mediaItem)
                radioPlayer?.prepare()
                radioPlayer?.play()
                Toast.makeText(context, "Reproduciendo audio de radio...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(context, "No hay audio de radio disponible en este momento", Toast.LENGTH_SHORT).show()
            }
        }

        // Radio Driver Chips
        btnRadioVer.setOnClickListener { tuneRadioDriver(1, "Max Verstappen") }
        btnRadioNor.setOnClickListener { tuneRadioDriver(4, "Lando Norris") }
        btnRadioLec.setOnClickListener { tuneRadioDriver(16, "Charles Leclerc") }
        btnRadioHam.setOnClickListener { tuneRadioDriver(44, "Lewis Hamilton") }
        btnRadioRus.setOnClickListener { tuneRadioDriver(63, "George Russell") }
        btnRadioPia.setOnClickListener { tuneRadioDriver(81, "Oscar Piastri") }
        btnRadioAlo.setOnClickListener { tuneRadioDriver(14, "Fernando Alonso") }

        // Metrics Switcher
        btnMetricsTiming.setOnClickListener {
            rvLeaderboard.visibility = View.VISIBLE
            layoutTelemetryPanel.visibility = View.GONE
            btnMetricsTiming.isSelected = true
            btnMetricsTelemetry.isSelected = false
        }

        btnMetricsTelemetry.setOnClickListener {
            rvLeaderboard.visibility = View.GONE
            layoutTelemetryPanel.visibility = View.VISIBLE
            btnMetricsTiming.isSelected = false
            btnMetricsTelemetry.isSelected = true
            loadDriverTelemetry(1)
        }
        btnMetricsTiming.isSelected = true

        btnTelemetryDemoToggle.setOnClickListener {
            loadDriverTelemetry(1)
        }
    }

    private fun setupFutbolVivoDiscovery() {
        val sourceUrl = defaultWebSourceUrl
        if (sourceUrl.isNullOrBlank() || !sourceUrl.contains("futbolvivo.tv", ignoreCase = true)) {
            futbolVivoStatusText = "FÚTBOL LIBRE • FUENTE NO CONFIGURADA"
            tvStandbySubtitle.text = "Configura futbolvivo.tv como fuente web predeterminada para consultar eventos."
            updateFutbolVivoStatusBadge()
            return
        }

        tvStandbySubtitle.text = "Buscando eventos y sesiones de Fórmula 1 en Fútbol Libre..."
        futbolVivoExtractor.onF1EventDetected = { event ->
            val status = if (event.isLive) "EN VIVO" else "PROGRAMADA"
            futbolVivoStatusText = "FÚTBOL LIBRE • $status"
            discoveredFutbolEventUrl = event.eventUrl
            discoveredAlternativeUrls = event.alternativeUrls
            discoveredFutbolEventTitle = event.title
            val start = event.startTime?.let { " • $it" }.orEmpty()
            tvStandbySubtitle.text = "Evento F1 detectado: ${event.title}$start"
            updateFutbolVivoStatusBadge()
            btnLiveSessionAction.text = "🔄 Refrescar"
            // Auto play if Pit Wall is visible and video is not playing
            if (rootView.visibility == View.VISIBLE && !isVideoPlaying() && !event.eventUrl.isNullOrBlank()) {
                openDiscoveredFutbolEvent()
            }
        }
        futbolVivoExtractor.onNoEventDetected = {
            discoveredFutbolEventUrl = null
            discoveredAlternativeUrls = emptyList()
            futbolVivoStatusText = "FÚTBOL LIBRE • SIN EVENTO F1"
            tvStandbySubtitle.text =
                "No se detectan eventos F1 en la cartelera actual. Abre TV Web para consultar los eventos publicados."
            btnLiveSessionAction.text = "🔄 Refrescar"
            updateFutbolVivoStatusBadge()
        }
        futbolVivoExtractor.startExtraction(sourceUrl)
    }

    private fun updateFutbolVivoStatusBadge() {
        tvStandbyStatusBadge.text = futbolVivoStatusText
    }

    private fun setupLeaderboardView() {
        rvLeaderboard.layoutManager = LinearLayoutManager(context)
        rvLeaderboard.adapter = leaderboardAdapter
    }

    fun isVideoPlaying(): Boolean {
        val isExoPlaying = exoPlayer?.isPlaying == true && playerView.visibility == View.VISIBLE
        val isWebPlaying = webVideoView.visibility == View.VISIBLE && currentWebUrl != null && currentWebUrl != "about:blank"
        return isExoPlaying || isWebPlaying
    }

    fun onPitWallTabSelected() {
        btnServer1.post { btnServer1.requestFocus() }
        if (!isVideoPlaying()) {
            if (!discoveredFutbolEventUrl.isNullOrBlank()) {
                openDiscoveredFutbolEvent()
            } else {
                futbolVivoExtractor.startExtraction(defaultWebSourceUrl ?: "https://futbolvivo.tv/")
            }
        }
    }

    fun playStream(url: String, title: String? = null) {
        if (!isDirectMediaUrl(url)) {
            playWebPage(url, title ?: "Fuente web")
            return
        }

        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE

        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        if (title != null) {
            tvLiveSessionAlertTitle.text = "🏎️ $title"
            Toast.makeText(context, "Cargando señal: $title", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchToStandby() {
        if (customFullScreenView != null) hideCustomView()
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
        currentWebUrl = null
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.GONE
        updateScheduleBanner()
    }

    fun tuneRadioDriver(driverNumber: Int, driverName: String) {
        tvRadioTitle.text = "📻 Sintonizando Radio: $driverName (#$driverNumber)..."
        scope.launch {
            try {
                val key = currentSessionKey
                if (key == null) {
                    tvRadioTitle.text = "📻 Radio: espera a que el calendario identifique una sesión"
                    Toast.makeText(context, "No hay una sesión F1 seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val radio = repository.getDriverTeamRadio(key, driverNumber)
                if (radio != null && !radio.recordingUrl.isNullOrEmpty()) {
                    latestRadioUrl = radio.recordingUrl
                    tvRadioTitle.text = "📻 Radio Oficial: $driverName (#$driverNumber) • Conectado"
                    val mediaItem = MediaItem.fromUri(radio.recordingUrl)
                    radioPlayer?.setMediaItem(mediaItem)
                    radioPlayer?.prepare()
                    radioPlayer?.play()
                    Toast.makeText(context, "Reproduciendo radio de $driverName", Toast.LENGTH_SHORT).show()
                } else {
                    tvRadioTitle.text = "📻 Radio: $driverName (#$driverNumber) [Canal en espera]"
                    Toast.makeText(context, "Sin transmisión de radio activa para $driverName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDriverTelemetry(driverNumber: Int) {
        scope.launch {
            try {
                val key = currentSessionKey
                if (key == null) {
                    Toast.makeText(context, "No hay una sesión F1 seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val telemetry = repository.getTelemetryForDriver(key, driverNumber)

                if (telemetry != null && (telemetry.speed ?: 0) > 0) {
                    val spd = telemetry.speed ?: 0
                    tvTelemetrySpeed.text = spd.toString()

                    val gear = telemetry.gear ?: 0
                    tvTelemetryGear.text = if (gear == 0) "N" else gear.toString()

                    val rpm = telemetry.rpm ?: 0
                    tvTelemetryRpm.text = String.format("%,d RPM", rpm)
                    pbTelemetryRpm.progress = rpm

                    val thr = telemetry.throttle ?: 0
                    tvTelemetryThrottle.text = "Acelerador: $thr%"
                    pbTelemetryThrottle.progress = thr

                    val brk = if ((telemetry.brake ?: 0) > 0) 100 else 0
                    tvTelemetryBrake.text = "Freno: $brk%"
                    pbTelemetryBrake.progress = brk

                    val drsActive = (telemetry.drs ?: 0) in listOf(10, 12, 14)
                    if (drsActive) {
                        tvTelemetryDrs.text = "DRS ACTIVO"
                        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#15803D"))
                    } else {
                        tvTelemetryDrs.text = "DRS INACTIVO"
                        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    }
                } else {
                    tvTelemetrySpeed.text = "--"
                    tvTelemetryGear.text = "--"
                    tvTelemetryRpm.text = "Sin datos disponibles"
                    pbTelemetryRpm.progress = 0
                    tvTelemetryThrottle.text = "Acelerador: --"
                    pbTelemetryThrottle.progress = 0
                    tvTelemetryBrake.text = "Freno: --"
                    pbTelemetryBrake.progress = 0
                    tvTelemetryDrs.text = "DRS --"
                    tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    val message = if (isUpcomingSession) {
                        "La telemetría estará disponible cuando comience $currentScheduleTitle"
                    } else {
                        "OpenF1 todavía no ha publicado telemetría para esta sesión"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSessionUpdated(
        sessionKey: Long,
        isLive: Boolean,
        isUpcoming: Boolean,
        sessionTitleText: String
    ) {
        currentSessionKey = sessionKey
        isLiveSessionActive = isLive
        isUpcomingSession = isUpcoming
        currentScheduleTitle = sessionTitleText
        tvSessionTitle.text = sessionTitleText
        tvStandbyTitle.text = sessionTitleText.uppercase()
        updateScheduleBanner()
    }

    fun onScheduleUnavailable() {
        currentSessionKey = null
        isLiveSessionActive = false
        isUpcomingSession = false
        currentScheduleTitle = "Calendario de F1 no disponible"
        tvSessionTitle.text = currentScheduleTitle
        tvStandbyTitle.text = "F1 PIT WALL TV"
        updateScheduleBanner()
    }

    private fun updateScheduleBanner() {
        when {
            isLiveSessionActive -> {
                tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $currentScheduleTitle"
            }
            isUpcomingSession -> {
                tvLiveSessionAlertTitle.text = "⏳ PRÓXIMA SESIÓN: $currentScheduleTitle"
            }
            currentScheduleTitle == "Calendario de F1 no disponible" -> {
                tvLiveSessionAlertTitle.text = "⚠️ Calendario F1 no disponible"
            }
            else -> {
                tvLiveSessionAlertTitle.text = "🏁 ÚLTIMA SESIÓN: $currentScheduleTitle"
            }
        }
        btnLiveSessionAction.text = "🔄 Refrescar"
        updateFutbolVivoStatusBadge()
    }

    inner class PitWallBridge {
        @JavascriptInterface
        fun onServersAvailable(count: Int, activeIdx: Int, titlesJson: String?) {
            rootView.post {
                updateServerButtons(count, activeIdx, titlesJson)
            }
        }
    }

    private fun updateServerButtons(count: Int, activeIdx: Int, titlesJson: String?) {
        android.util.Log.d("PitWallServer", "updateServerButtons: count=$count, active=$activeIdx, titles=$titlesJson")
        // Always keep all 3 server options visible so user can switch anytime
        btnServer1.visibility = View.VISIBLE
        btnServer2.visibility = View.VISIBLE
        btnServer3.visibility = View.VISIBLE

        if (!titlesJson.isNullOrBlank()) {
            try {
                val list = Gson().fromJson(titlesJson, Array<String>::class.java)
                if (list != null) {
                    if (list.isNotEmpty()) btnServer1.text = formatServerTitle(list[0], 1)
                    if (list.size > 1) btnServer2.text = formatServerTitle(list[1], 2)
                    if (list.size > 2) btnServer3.text = formatServerTitle(list[2], 3)
                }
            } catch (_: Exception) {}
        }

        selectedServerIndex = activeIdx
        btnServer1.isSelected = (activeIdx == 0)
        btnServer2.isSelected = (activeIdx == 1)
        btnServer3.isSelected = (activeIdx == 2)
    }

    private fun formatServerTitle(raw: String, fallbackNum: Int): String {
        val clean = raw.trim()
        if (clean.equals("English", ignoreCase = true)) return "Opción $fallbackNum (ENG)"
        if (clean.equals("Spanish", ignoreCase = true) || clean.equals("Español", ignoreCase = true)) return "Opción $fallbackNum (ESP)"
        return if (clean.length in 1..14) clean else "Opción $fallbackNum"
    }

    fun selectServer(index: Int) {
        selectedServerIndex = index
        btnServer1.isSelected = (index == 0)
        btnServer2.isSelected = (index == 1)
        btnServer3.isSelected = (index == 2)

        Toast.makeText(context, "Conectando a Opción ${index + 1}...", Toast.LENGTH_SHORT).show()

        val altUrl = if (index < discoveredAlternativeUrls.size) discoveredAlternativeUrls[index] else null

        val switchJs = """
            (function(idx, fallbackAltUrl) {
                try {
                    // 1. soccer.html (currentMatch.channels)
                    if (window.currentMatch && Array.isArray(window.currentMatch.channels) && window.currentMatch.channels[idx]) {
                        window.activeServer = idx;
                        if (typeof window.updatePlayer === 'function') {
                            window.updatePlayer(window.currentMatch.channels[idx]);
                        }
                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                        tabs.forEach((b, i) => b.classList.toggle('active', i === idx));
                        return 'switched_channel_' + idx;
                    }
                    // 2. us.html / latin.html (currentMatch.streams)
                    if (window.currentMatch && Array.isArray(window.currentMatch.streams) && window.currentMatch.streams[idx]) {
                        window.activeStream = idx;
                        if (typeof window.renderPlayer === 'function') {
                            window.renderPlayer();
                        }
                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                        tabs.forEach((b, i) => b.classList.toggle('active', i === idx));
                        return 'switched_stream_' + idx;
                    }
                    // 3. Fallback: click tab button in DOM
                    const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                    if (tabs && tabs[idx]) {
                        tabs[idx].click();
                        return 'clicked_tab_' + idx;
                    }
                    // 4. Fallback: navigate to alternative discovered F1 match URL
                    if (fallbackAltUrl && fallbackAltUrl !== window.location.href) {
                        window.location.replace(fallbackAltUrl);
                        return 'navigated_alt_url_' + fallbackAltUrl;
                    }
                    // 5. URL navigation with &s=idx for direct reload
                    const u = new URL(window.location.href);
                    u.searchParams.set('s', idx);
                    window.location.replace(u.toString());
                    return 'navigated_param_' + idx;
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })($index, ${altUrl?.let { "'$it'" } ?: "null"});
        """.trimIndent()

        webVideoView.evaluateJavascript(switchJs) { res ->
            android.util.Log.d("PitWallServer", "Server switch result: $res")
        }

        webVideoView.postDelayed({ triggerPlayerTap() }, 2000)
        webVideoView.postDelayed({ triggerPlayerTap() }, 4500)
    }

    fun refreshStream() {
        Toast.makeText(context, "🔄 Recargando transmisión F1...", Toast.LENGTH_SHORT).show()
        if (webVideoView.visibility == View.VISIBLE && currentWebUrl != null && currentWebUrl != "about:blank") {
            selectServer(selectedServerIndex)
        } else {
            openDiscoveredFutbolEvent()
        }
    }

    fun onPause() {
        exoPlayer?.pause()
        webVideoView.onPause()
        futbolVivoExtractor.onPause()
    }

    fun onResume() {
        webVideoView.onResume()
        webVideoView.resumeTimers()
        futbolVivoExtractor.onResume()
    }

    fun onDestroy() {
        futbolVivoExtractor.destroy()
        webVideoView.stopLoading()
        webVideoView.destroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
