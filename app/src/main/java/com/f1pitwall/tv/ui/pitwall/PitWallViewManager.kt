package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import com.f1pitwall.tv.ui.LeaderboardAdapter
import kotlinx.coroutines.launch

class PitWallViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onNavigateToSettings: () -> Unit,
    private val defaultWebSourceUrl: String? = null
) {
    private val context: Context = rootView.context

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
    private val activePopupWebViews = mutableListOf<WebView>()
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
    }

    /**
     * Loads a user-selected website as a top-level page. The provider's own
     * JavaScript, cookies and HTML5 player are handled by Android WebView.
     * Server-side frame and access policies are not bypassed.
     */
    private fun setupWebView() {
        webVideoView.apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                // Use a desktop-style UA for TV layouts while retaining the installed WebView's
                // actual Chromium version, rather than spoofing an obsolete browser build.
                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace(" Mobile", "")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(webVideoView, true)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val scheme = request?.url?.scheme?.lowercase() ?: return false
                    return scheme != "http" && scheme != "https"
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
                    val pageTitle = webVideoView.title?.takeIf { it.isNotBlank() } ?: "Fuente web"
                    tvLiveSessionAlertTitle.text = "🌐 " + pageTitle
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = createPopupWindow(resultMsg)

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
    }

    private fun createPopupWindow(resultMsg: Message?): Boolean {
        val message = resultMsg ?: return false
        val transport = message.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(context)
        activePopupWebViews.add(popup)
        playerSurface.addView(popup, FrameLayout.LayoutParams(1, 1))
        popup.settings.javaScriptEnabled = true
        popup.settings.domStorageEnabled = true
        popup.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
        popup.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank() && url != "about:blank") {
                    webVideoView.loadUrl(url)
                    closePopup(popup)
                }
            }
        }
        popup.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                closePopup(popup)
            }
        }
        transport.webView = popup
        message.sendToTarget()
        return true
    }

    private fun closePopup(popup: WebView) {
        activePopupWebViews.remove(popup)
        (popup.parent as? ViewGroup)?.removeView(popup)
        popup.stopLoading()
        popup.destroy()
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

        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🌐 Cargando " + title
        webVideoView.loadUrl(url)
        webVideoView.requestFocus()
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
        btnStandbyPreview.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }
        btnStandbyRecap.setOnClickListener { openDefaultWebSource("Archivo F1 en la fuente web") }
        btnStandbyOpenWeb.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }

        btnStandbyStopVideo.setOnClickListener {
            switchToStandby()
        }

        // Durante una sesión activa o próxima, abrir la web real del proveedor configurado.
        btnLiveSessionAction.setOnClickListener {
            openDefaultWebSource("Fútbol Vivo / Fútbol Libre")
        }

        // Contenido y telemetría: no reproducir HLS de demostración como si fuese F1.
        btnContentPreview.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }
        btnContentRecap.setOnClickListener { openDefaultWebSource("Archivo F1 en la fuente web") }
        btnContentBoxes.setOnClickListener { btnMetricsTelemetry.performClick() }

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

    private fun setupLeaderboardView() {
        rvLeaderboard.layoutManager = LinearLayoutManager(context)
        rvLeaderboard.adapter = leaderboardAdapter
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
        activePopupWebViews.toList().forEach { closePopup(it) }
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
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
        updateScheduleBanner()
    }

    fun onScheduleUnavailable() {
        currentSessionKey = null
        isLiveSessionActive = false
        isUpcomingSession = false
        currentScheduleTitle = "Calendario de F1 no disponible"
        tvSessionTitle.text = currentScheduleTitle
        updateScheduleBanner()
    }

    private fun updateScheduleBanner() {
        when {
            isLiveSessionActive -> {
                tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $currentScheduleTitle"
                btnLiveSessionAction.text = "📺 ABRIR TV WEB"
                tvStandbyStatusBadge.text = "🔴 SESIÓN ACTIVA • EN DIRECTO"
            }
            isUpcomingSession -> {
                tvLiveSessionAlertTitle.text = "⏳ PRÓXIMA SESIÓN: $currentScheduleTitle"
                btnLiveSessionAction.text = "📺 ABRIR TV WEB"
                tvStandbyStatusBadge.text = "⏳ PRÓXIMA SESIÓN"
            }
            currentScheduleTitle == "Calendario de F1 no disponible" -> {
                tvLiveSessionAlertTitle.text = "⚠️ Calendario F1 no disponible"
                btnLiveSessionAction.text = "📺 ABRIR TV WEB"
                tvStandbyStatusBadge.text = "CALENDARIO NO DISPONIBLE"
            }
            else -> {
                tvLiveSessionAlertTitle.text = "🏁 ÚLTIMA SESIÓN: $currentScheduleTitle"
                btnLiveSessionAction.text = "📺 ABRIR TV WEB"
                tvStandbyStatusBadge.text = "SIN SESIÓN ACTIVA"
            }
        }
    }

    fun onPause() {
        exoPlayer?.pause()
        webVideoView.onPause()
    }

    fun onResume() {
        webVideoView.onResume()
        webVideoView.resumeTimers()
    }

    fun onDestroy() {
        activePopupWebViews.toList().forEach { closePopup(it) }
        webVideoView.stopLoading()
        webVideoView.destroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
