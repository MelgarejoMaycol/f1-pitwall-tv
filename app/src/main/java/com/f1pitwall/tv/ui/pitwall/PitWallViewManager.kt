package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.util.Log
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
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.config.BroadcastSource
import com.f1pitwall.tv.core.storage.PreferencesManager
import com.f1pitwall.tv.core.util.BroadcastDetectorUtils
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.FlxExtractor
import com.f1pitwall.tv.network.FutbolLibreExtractor
import com.f1pitwall.tv.network.FutbolVivoExtractor
import com.f1pitwall.tv.ui.LeaderboardAdapter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Locale

class PitWallViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onNavigateToSettings: () -> Unit,
    private val defaultWebSourceUrl: String? = null
) {
    private val context: Context = rootView.context
    private val preferencesManager = PreferencesManager(context)
    private val futbolVivoExtractor = FutbolVivoExtractor(context)
    private val flxExtractor = FlxExtractor(scope)
    private val futbolLibreExtractor = FutbolLibreExtractor(scope)

    // Video Player & Standby
    private val playerSurface: FrameLayout = rootView.findViewById(R.id.framePitWallSurface)
    private val playerView: PlayerView = rootView.findViewById(R.id.playerView)
    private val webVideoView: WebView = rootView.findViewById(R.id.webVideoView)
    private val layoutPlayerStandby: View = rootView.findViewById(R.id.layoutPlayerStandby)
    private val tvStandbyStatusBadge: TextView = rootView.findViewById(R.id.tvStandbyStatusBadge)
    private val tvStandbyTitle: TextView = rootView.findViewById(R.id.tvStandbyTitle)
    private val tvStandbySubtitle: TextView = rootView.findViewById(R.id.tvStandbySubtitle)
    private val tvStandbyCircuitSpecs: TextView? = rootView.findViewById(R.id.tvStandbyCircuitSpecs)
    private val btnStandbyPreview: Button = rootView.findViewById(R.id.btnStandbyPreview)
    private val btnStandbyRecap: Button = rootView.findViewById(R.id.btnStandbyRecap)
    private val btnStandbyOpenWeb: Button = rootView.findViewById(R.id.btnStandbyOpenWeb)
    private val btnStandbyStopVideo: Button = rootView.findViewById(R.id.btnStandbyStopVideo)

    // Alert Banner & Content Hub Bar
    private val layoutLiveSessionAlert: View = rootView.findViewById(R.id.layoutLiveSessionAlert)
    private val tvLiveSessionAlertTitle: TextView = rootView.findViewById(R.id.tvLiveSessionAlertTitle)
    private val btnLiveSessionAction: Button = rootView.findViewById(R.id.btnLiveSessionAction)
    private val layoutServerSelector: View = rootView.findViewById(R.id.layoutServerSelector)
    private val scrollServerSelector: HorizontalScrollView? = rootView.findViewById(R.id.scrollServerSelector)
    private val btnSourceBadgePitWall: Button? = rootView.findViewById(R.id.btnSourceBadgePitWall)
    private val btnSessionSelector: Button? = rootView.findViewById(R.id.btnSessionSelector)
    private val llDynamicChannelsContainer: LinearLayout? = rootView.findViewById(R.id.llDynamicChannelsContainer)
    private val dynamicChannelButtons = mutableListOf<Button>()
    private val serverButtons: List<Button> get() = dynamicChannelButtons
    private var selectedServerIndex: Int = 0
    private var activeSpanishBroadcasts: List<SpanishBroadcast> = emptyList()
    private var selectedBroadcastIndex: Int = 0
    private var currentLoadedSource: BroadcastSource? = null
    private var futbolLibreEvents: List<FutbolLibreEvent> = emptyList()
    private var selectedFutbolLibreEventIndex: Int = 0
    private var futbolLibreSourcesJob: Job? = null
    private var pendingFutbolLibreSourceId: String? = null
    private val btnContentPreview: Button = rootView.findViewById(R.id.btnContentPreview)
    private val btnContentRecap: Button = rootView.findViewById(R.id.btnContentRecap)
    private val btnContentBoxes: Button = rootView.findViewById(R.id.btnContentBoxes)

    // Radio
    val tvRadioTitle: TextView = rootView.findViewById(R.id.tvRadioTitle)
    val scrollRadioChips: HorizontalScrollView? = rootView.findViewById(R.id.scrollRadioChips)
    val llRadioChips: LinearLayout? = rootView.findViewById(R.id.llRadioChips)
    val tvRadioLabel: TextView? = rootView.findViewById(R.id.tvRadioLabel)
    var activeRadioDriverNumber: Int? = null
        internal set
    val radioButtons = mutableMapOf<Int, Button>()
    var nextSessionFormattedDate: String? = null
        internal set

    // Header Status & Live Refresh
    private val tvSessionTitle: TextView = rootView.findViewById(R.id.tvSessionTitle)
    private val btnRefreshLive: Button? = rootView.findViewById(R.id.btnRefreshLive)
    private val tvRaceFlag: TextView? get() = rootView.rootView?.findViewById(R.id.tvRaceFlag)
    private val tvWeather: TextView? get() = rootView.rootView?.findViewById(R.id.tvWeather)

    enum class TelemetryMode(val title: String, val icon: String) {
        TIMING("Tiempos", "⏱️ TIEMPOS"),
        TRACK_FIA("Pista + FIA", "🗺️ PISTA + FIA"),
        TYRES("Neumáticos", "🛞 NEUMÁTICOS"),
        SOLO_TRACK("Solo Pista", "🏎️ SOLO PISTA"),
        RACE_CONTROL("Solo Mensajes", "🚩 SOLO MENSAJES")
    }

    var activeTelemetryMode: TelemetryMode = TelemetryMode.TIMING
        private set

    var isDriverTelemetryOpen: Boolean = false
        private set

    private var selectedDriverNumber: Int = 1
    private var selectedDriverItem: DriverLeaderboardItem? = null

    // Video Card Container for Direct D-Pad Navigation
    private val cardVideoContainer: View? = rootView.findViewById(R.id.cardVideoContainer)
    private val layoutVideoColumn: View? = rootView.findViewById(R.id.layoutVideoColumn)
    private val btnPipFullscreenTrack: Button? = rootView.findViewById(R.id.btnPipFullscreenTrack)
    private val layoutFloatingPipTrackMap: View? = rootView.findViewById(R.id.layoutFloatingPipTrackMap)
    private val pipTrackMapView: TrackMapView? = rootView.findViewById(R.id.pipTrackMapView)
    var isFullscreenVideoPipMode: Boolean = false
        private set

    // Metrics Switcher & Containers (6 Dynamic Views)
    private val scrollTelemetryModes: HorizontalScrollView? = rootView.findViewById(R.id.scrollTelemetryModes)
    private val btnMetricsTiming: Button = rootView.findViewById(R.id.btnMetricsTiming)
    private val btnMetricsTrackMap: Button? = rootView.findViewById(R.id.btnMetricsTrackMap)
    private val btnMetricsTyres: Button? = rootView.findViewById(R.id.btnMetricsTyres)
    private val btnMetricsSoloTrack: Button? = rootView.findViewById(R.id.btnMetricsSoloTrack)
    private val btnMetricsRaceControl: Button? = rootView.findViewById(R.id.btnMetricsRaceControl)

    // View Panels
    private val layoutTimingLeaderboardPanel: View? = rootView.findViewById(R.id.layoutTimingLeaderboardPanel)
    private val rvLeaderboard: RecyclerView = rootView.findViewById(R.id.rvLeaderboard)
    private val tvLeaderboardHeader: TextView? = rootView.findViewById(R.id.tvLeaderboardHeader)
    private val layoutTelemetryPanel: View = rootView.findViewById(R.id.layoutTelemetryPanel)
    private val btnBackToTiming: Button? = rootView.findViewById(R.id.btnBackToTiming)
    private val layoutPistaFiaPanel: View? = rootView.findViewById(R.id.layoutPistaFiaPanel)
    private val layoutTrackMapContainer: View? = rootView.findViewById(R.id.layoutTrackMapContainer)
    private val frameTrackMapArea: View? = rootView.findViewById(R.id.frameTrackMapArea)
    private val layoutFiaMessagesContainer: View? = rootView.findViewById(R.id.layoutFiaMessagesContainer)
    private val layoutTyresPanel: View? = rootView.findViewById(R.id.layoutTyresPanel)
    private val layoutRaceControlPanel: View? = rootView.findViewById(R.id.layoutRaceControlPanel)

    // View 2: Last 2 FIA Messages
    private val tvFiaMsg1Flag: TextView? = rootView.findViewById(R.id.tvFiaMsg1Flag)
    private val tvFiaMsg1Time: TextView? = rootView.findViewById(R.id.tvFiaMsg1Time)
    private val tvFiaMsg1Text: TextView? = rootView.findViewById(R.id.tvFiaMsg1Text)
    private val tvFiaMsg2Flag: TextView? = rootView.findViewById(R.id.tvFiaMsg2Flag)
    private val tvFiaMsg2Time: TextView? = rootView.findViewById(R.id.tvFiaMsg2Time)
    private val tvFiaMsg2Text: TextView? = rootView.findViewById(R.id.tvFiaMsg2Text)

    // Track Map Views
    private val trackMapView: TrackMapView? = rootView.findViewById(R.id.trackMapView)
    private val layoutTrackMapCircuitImageContainer: View? = rootView.findViewById(R.id.layoutTrackMapCircuitImageContainer)
    private val ivTrackMapCircuitImage: ImageView? = rootView.findViewById(R.id.ivTrackMapCircuitImage)
    private val tvTrackMapImageCaption: TextView? = rootView.findViewById(R.id.tvTrackMapImageCaption)
    private val tvTrackMapCircuitName: TextView? = rootView.findViewById(R.id.tvTrackMapCircuitName)
    private val tvTrackMapGpsStatus: TextView? = rootView.findViewById(R.id.tvTrackMapGpsStatus)
    private val tvTrackMapFollowedDriver: TextView? = rootView.findViewById(R.id.tvTrackMapFollowedDriver)
    private val tvTrackMapLegend: TextView? = rootView.findViewById(R.id.tvTrackMapLegend)
    private var trackMapPollingJob: Job? = null
    private var telemetryToast: Toast? = null
    private var currentCircuitImageUrl: String? = null
    private var currentSessionType: String? = null
    private var lastKnownMarkers: List<TrackMapView.DriverMarker> = emptyList()

    // Tyres Views & Adapter
    private val rvTyreStrategy: RecyclerView? = rootView.findViewById(R.id.rvTyreStrategy)
    val tyreStrategyAdapter = TyreStrategyAdapter { item ->
        selectedDriverNumber = item.driverNumber
        if (isDriverTelemetryOpen) {
            loadDriverTelemetry(item.driverNumber)
        }
    }

    // Race Control Views & Adapter
    private val tvRcMainFlagBanner: TextView? = rootView.findViewById(R.id.tvRcMainFlagBanner)
    private val tvRcTrackTemp: TextView? = rootView.findViewById(R.id.tvRcTrackTemp)
    private val tvRcAirTemp: TextView? = rootView.findViewById(R.id.tvRcAirTemp)
    private val tvRcRain: TextView? = rootView.findViewById(R.id.tvRcRain)
    private val tvRcWind: TextView? = rootView.findViewById(R.id.tvRcWind)
    private val rvRaceControl: RecyclerView? = rootView.findViewById(R.id.rvRaceControl)
    val raceControlAdapter = RaceControlAdapter()

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

    // Sector Telemetry Views
    private val tvTelemetryS1: TextView = rootView.findViewById(R.id.tvTelemetryS1)
    private val tvTelemetryS1Speed: TextView = rootView.findViewById(R.id.tvTelemetryS1Speed)
    private val tvTelemetryS2: TextView = rootView.findViewById(R.id.tvTelemetryS2)
    private val tvTelemetryS2Speed: TextView = rootView.findViewById(R.id.tvTelemetryS2Speed)
    private val tvTelemetryS3: TextView = rootView.findViewById(R.id.tvTelemetryS3)
    private val tvTelemetryS3Speed: TextView = rootView.findViewById(R.id.tvTelemetryS3Speed)
    private val tvTelemetryLapTime: TextView = rootView.findViewById(R.id.tvTelemetryLapTime)

    // State & Players
    private var exoPlayer: ExoPlayer? = null
    private var radioPlayer: ExoPlayer? = null
    private var radioPollingJob: Job? = null
    private var latestRadioUrl: String? = null
    private var lastPlayedRadioUrl: String? = null
    @Volatile
    private var currentDirectStreamUrl: String? = null
    private var currentSessionKey: Long? = null
    private var isLiveSessionActive: Boolean = false
    private var isUpcomingSession: Boolean = false
    private var currentScheduleTitle: String = "Calendario F1 en preparación"
    private var futbolVivoStatusText: String = "FÚTBOL VIVO • BUSCANDO CANALES EN ESPAÑOL"
    private var discoveredFutbolEventUrl: String? = null
    private var discoveredAlternativeUrls: List<String> = emptyList()
    private var discoveredFutbolEventTitle: String = "Evento de Fórmula 1"
    @Volatile
    private var currentWebUrl: String? = null
    private var customFullScreenView: View? = null
    private var customFullScreenCallback: WebChromeClient.CustomViewCallback? = null

    // Simulation / Demo Mode State
    private var simulationEngineJob: Job? = null
    private var simulationTick: Int = 0
    private var radioMessageExpiryTick: Int = 0
    var isSimulationMode: Boolean = false
        private set

    data class SimDriver(
        val driverNumber: Int,
        val position: Int,
        val acronym: String,
        val fullName: String,
        val teamName: String,
        val teamColor: String,
        val tyreCompound: String,
        val tyreAge: Int,
        val bestLap: String
    )

    private val simulatedDrivers = listOf(
        SimDriver(1, 1, "VER", "Max Verstappen", "Red Bull Racing", "#3671C6", "MEDIUM", 16, "1:35.490"),
        SimDriver(4, 2, "NOR", "Lando Norris", "McLaren", "#FF8000", "MEDIUM", 16, "1:35.620"),
        SimDriver(16, 3, "LEC", "Charles Leclerc", "Ferrari", "#E80020", "HARD", 22, "1:35.780"),
        SimDriver(44, 4, "HAM", "Lewis Hamilton", "Ferrari", "#E80020", "HARD", 22, "1:35.910"),
        SimDriver(63, 5, "RUS", "George Russell", "Mercedes", "#27F4D2", "MEDIUM", 15, "1:36.050"),
        SimDriver(81, 6, "PIA", "Oscar Piastri", "McLaren", "#FF8000", "SOFT", 8, "1:35.530"),
        SimDriver(14, 7, "ALO", "Fernando Alonso", "Aston Martin", "#229971", "MEDIUM", 18, "1:36.310"),
        SimDriver(55, 8, "SAI", "Carlos Sainz", "Williams", "#64C4FF", "HARD", 24, "1:36.450"),
        SimDriver(23, 9, "ALB", "Alex Albon", "Williams", "#64C4FF", "MEDIUM", 14, "1:36.620"),
        SimDriver(12, 10, "ANT", "Andrea Kimi Antonelli", "Mercedes", "#27F4D2", "HARD", 21, "1:36.750"),
        SimDriver(10, 11, "GAS", "Pierre Gasly", "Alpine", "#0093CC", "SOFT", 9, "1:36.800"),
        SimDriver(31, 12, "OCO", "Esteban Ocon", "Haas", "#B6BABD", "HARD", 20, "1:36.950"),
        SimDriver(27, 13, "HUL", "Nico Hulkenberg", "Sauber", "#52E252", "MEDIUM", 17, "1:37.110"),
        SimDriver(22, 14, "TSU", "Yuki Tsunoda", "RB", "#6692FF", "SOFT", 10, "1:37.240"),
        SimDriver(30, 15, "LAW", "Liam Lawson", "Red Bull Racing", "#3671C6", "MEDIUM", 15, "1:37.380"),
        SimDriver(87, 16, "BEA", "Oliver Bearman", "Haas", "#B6BABD", "MEDIUM", 13, "1:37.690"),
        SimDriver(6, 17, "HAD", "Isack Hadjar", "RB", "#6692FF", "HARD", 19, "1:37.840"),
        SimDriver(5, 18, "BOR", "Gabriel Bortoleto", "Sauber", "#52E252", "SOFT", 11, "1:38.010"),
        SimDriver(7, 19, "DOO", "Jack Doohan", "Alpine", "#0093CC", "MEDIUM", 16, "1:38.180"),
        SimDriver(18, 20, "STR", "Lance Stroll", "Aston Martin", "#229971", "HARD", 23, "1:38.350"),
        SimDriver(11, 21, "PER", "Sergio Pérez", "Cadillac F1 Team", "#C5A059", "SOFT", 12, "1:37.950"),
        SimDriver(77, 22, "BOT", "Valtteri Bottas", "Cadillac F1 Team", "#C5A059", "MEDIUM", 17, "1:38.120")
    )

    data class SimDriverState(
        val driverNumber: Int,
        var position: Int,
        val acronym: String,
        val fullName: String,
        val teamName: String,
        val teamColor: String,
        var tyreCompound: String,
        var tyreAge: Int,
        var bestLap: String,
        var lastLapTime: String,
        var gapToLeader: Double,
        var intervalToAhead: Double,
        var pitStopsCount: Int = 0,
        var isPitting: Boolean = false,
        var pitTicksRemaining: Int = 0,
        var lastPitDuration: String = "--",
        var stintsHistory: String = "",
        var s1: String = "27.200",
        var s2: String = "31.800",
        var s3: String = "26.100"
    )

    private val activeSimDrivers = mutableListOf<SimDriverState>()
    var currentRaceLap: Int = 34
        private set
    val totalRaceLaps: Int = 58
    private var nextOvertakeTick: Int = 120
    private var nextPitStopTick: Int = 180
    private val simRaceControlLog = mutableListOf<RaceControl>()

    fun getActiveSimDrivers(): List<SimDriverState> = activeSimDrivers
    fun getSimRaceControlLog(): List<RaceControl> = simRaceControlLog

    val leaderboardAdapter = LeaderboardAdapter { item ->
        openDriverTelemetry(item)
    }

    fun openDriverTelemetry(item: DriverLeaderboardItem) {
        isDriverTelemetryOpen = true
        selectedDriverItem = item
        selectedDriverNumber = item.driverNumber

        layoutTimingLeaderboardPanel?.visibility = View.GONE
        layoutPistaFiaPanel?.visibility = View.GONE
        layoutTelemetryPanel.visibility = View.VISIBLE

        tvTelemetryDriverHeader.text = "#${item.driverNumber} ${item.fullName} • ${item.teamName}"
        trackMapView?.selectedDriverNumber = item.driverNumber
        tvTrackMapFollowedDriver?.text = "Piloto seguido: #${item.driverNumber} ${item.acronym} (${item.teamName})"

        loadDriverTelemetry(item.driverNumber)
        btnBackToTiming?.requestFocus()
    }

    fun closeDriverTelemetry() {
        isDriverTelemetryOpen = false
        layoutTelemetryPanel.visibility = View.GONE
        switchTelemetryMode(activeTelemetryMode)
        if (activeTelemetryMode == TelemetryMode.TIMING) {
            rvLeaderboard.requestFocus()
        }
    }

    init {
        leaderboardAdapter.onDriverKeyLeft = { item ->
            cardVideoContainer?.let { card ->
                card.requestFocus()
                true
            } ?: run {
                val chip = radioButtons[item.driverNumber] ?: radioButtons.values.firstOrNull()
                chip?.let {
                    it.requestFocus()
                    scrollRadioChips?.smoothScrollTo((it.left - 40).coerceAtLeast(0), 0)
                    true
                } ?: false
            }
        }

        leaderboardAdapter.onFirstItemKeyUp = {
            btnMetricsTiming.requestFocus()
            true
        }

        setupWebView()
        initPlayers()
        setupListeners()
        setupLeaderboardView()
        initBroadcastSource()
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
            isFocusable = false
            isFocusableInTouchMode = false

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
                    val rawUrl = request?.url?.toString() ?: return null
                    val lowerUrl = rawUrl.lowercase(Locale.ROOT)
                    val isAd = listOf(
                        "acscdn.com", "al5sm.com", "deltalcherup.com", "histats.com",
                        "popads", "adsterra", "doubleclick", "googlesyndication",
                        "googleads", "adnxs", "propellerads", "trafficjunky",
                        "exoclick", "juicyads", "syndication", "adsystem", "adservice",
                        "monetag", "onclick", "ouo.io", "linkvertise", "shortener"
                    ).any { lowerUrl.contains(it) }
                    if (isAd) {
                        return WebResourceResponse("text/javascript", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // SNIFFER: Capturar señal directa (.m3u8 o stream HLS/DASH) para reproducción fluida en ExoPlayer
                    if (isDirectStreamUrl(rawUrl)) {
                        if (currentDirectStreamUrl == null) {
                            val headers = request.requestHeaders ?: emptyMap()
                            val referer = headers["Referer"] ?: headers["referer"] ?: currentWebUrl.orEmpty()
                            val origin = headers["Origin"] ?: headers["origin"] ?: ""
                            val userAgent = headers["User-Agent"] ?: headers["user-agent"] ?: ""

                            scope.launch(Dispatchers.Main) {
                                playDirectStreamInExoPlayer(rawUrl, referer, origin, userAgent)
                            }
                        }
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
                        "sport", "streameast", "strm-api", "cdn",
                        "flive", "taifood", "videocdn", "weellz"
                    ).any { host.contains(it) }

                    val isAdRedirect = listOf(
                        "ouo", "linkvertise", "adsterra", "popads", "onclick",
                        "monetag", "shorturl", "acscdn", "al5sm", "bet", "casino"
                    ).any { host.contains(it) }

                    if (isAdRedirect || (!isStreamHost && request.isForMainFrame)) {
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
                                    .site-header, .site-footer, .breadcrumb, .match-info,
                                    #cq-back-to-top, .cq-back-to-top,
                                    .histats, [id*="histats"], [class*="histats"], [id*="aclib"],
                                    .homepage-faq-title, #cq-branding-layout-style,
                                    [id*="ad"], [class*="ad-"], [class*="banner"], [id*="banner"],
                                    [class*="overlay-ad"], [class*="popup"], [id*="popup"],
                                    a[href*="ouo"], a[href*="short"], [class*="ad_overlay"],
                                    .floating-banner, #floating-banner {
                                        display: none !important;
                                        pointer-events: none !important;
                                    }
                                    .se-hero, .se-container, .se-match-card, .se-player, .se-player-wrap, #playerWrap, #playerContainer,
                                    .aspect-video, [class*="aspect-video"], #player, [id*="player"] {
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        border: none !important;
                                        background: #000000 !important;
                                    }
                                    .player-panel {
                                        display: flex !important;
                                        flex-direction: column !important;
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
                                        background: #000000 !important;
                                        z-index: 999999 !important;
                                    }
                                    .player-toolbar, .server-switcher, .event-source-switcher {
                                        display: flex !important;
                                        visibility: visible !important;
                                        opacity: 1 !important;
                                        flex-shrink: 0 !important;
                                        background: rgba(10, 15, 20, 0.90) !important;
                                        padding: 6px 12px !important;
                                        z-index: 1000001 !important;
                                        align-items: center !important;
                                        overflow-x: auto !important;
                                    }
                                    .player-source-state {
                                        display: flex !important;
                                        visibility: visible !important;
                                        align-items: center !important;
                                        margin-right: 12px !important;
                                    }
                                    button.event-source-choice, .source-pill {
                                        display: inline-flex !important;
                                        visibility: visible !important;
                                        opacity: 1 !important;
                                        cursor: pointer !important;
                                    }
                                    .player-box {
                                        flex: 1 1 auto !important;
                                        position: relative !important;
                                        width: 100% !important;
                                        height: 100% !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        background: #000000 !important;
                                    }
                                    #source-frame, #chinaq-video, iframe {
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

                                function setupAutoResume(v) {
                                    if (v.__f1_autoresume_attached) return;
                                    v.__f1_autoresume_attached = true;
                                    v.addEventListener('pause', function() {
                                        setTimeout(function() {
                                            // Solo cuando se detenga, NO cuando está cargando (readyState >= 2)
                                            if (v.paused && v.readyState >= 2 && !v.seeking) {
                                                v.play().catch(function() {});
                                            }
                                        }, 400);
                                    });
                                    v.addEventListener('ended', function() {
                                        setTimeout(function() {
                                            if (v.paused) {
                                                v.play().catch(function() {});
                                            }
                                        }, 500);
                                    });
                                }

                                document.querySelectorAll('video').forEach(v => {
                                    setupAutoResume(v);
                                    v.muted = false;
                                    v.play().catch(() => {
                                        v.muted = true;
                                        v.play().catch(() => {});
                                    });
                                });

                                if (!window.__f1_video_watchdog) {
                                    window.__f1_video_watchdog = true;
                                    setInterval(function() {
                                        try {
                                            document.querySelectorAll('video').forEach(function(v) {
                                                setupAutoResume(v);
                                                if (v.paused && v.readyState >= 2 && !v.seeking) {
                                                    v.play().catch(function() {});
                                                }
                                            });
                                            document.querySelectorAll('iframe').forEach(function(f) {
                                                try {
                                                    if (f.contentDocument) {
                                                        f.contentDocument.querySelectorAll('video').forEach(function(v) {
                                                            setupAutoResume(v);
                                                            if (v.paused && v.readyState >= 2 && !v.seeking) {
                                                                v.play().catch(function() {});
                                                            }
                                                        });
                                                    }
                                                } catch(e) {}
                                            });
                                        } catch(e) {}
                                    }, 1500);
                                }

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
                                    if (window.location.hostname.indexOf('futbolvivo') === -1 && window.location.hostname.indexOf('futbollibre') === -1) return;
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

                            function scanSpanishBroadcasts() {
                                try {
                                    var host = (window.location.hostname || '').toLowerCase();
                                    var pathname = window.location.pathname || '';
                                    var isFutbolLibre = host === 'futbol-libre.app' || host.endsWith('.futbol-libre.app');
                                    if (isFutbolLibre) {
                                        if (pathname.indexOf('/match/') !== -1) {
                                            var libreBroadcasts = [];
                                            var sourceButtons = Array.prototype.slice.call(
                                                document.querySelectorAll('button.event-source-choice[data-source]')
                                            );
                                            sourceButtons.forEach(function(button, idx) {
                                                var sourceId = (button.getAttribute('data-source') || '').trim();
                                                if (!/^[A-Za-z0-9_-]{1,32}$/.test(sourceId)) return;
                                                var providerNode = button.querySelector('.source-provider');
                                                var channelNode = button.querySelector('.source-channel');
                                                var provider = (providerNode && providerNode.textContent || '').trim() || ('Fuente ' + (idx + 1));
                                                var channel = (channelNode && channelNode.textContent || '').trim() || provider;
                                                var pageUrl = window.location.href.split('#')[0];
                                                var targetUrl = pageUrl + '#source=' + encodeURIComponent(sourceId);
                                                var clickJs = "(function(){var id=" + JSON.stringify(sourceId) + ";" +
                                                    "function tryClick(){var b=document.querySelector('button.event-source-choice[data-source=\"'+id+'\"]');if(b){b.click();return true;}return false;}" +
                                                    "if(!tryClick()){var attempts=0;var timer=setInterval(function(){attempts++;if(tryClick()||attempts>=25){clearInterval(timer);}},200);}})();";
                                                libreBroadcasts.push({
                                                    index: idx + 1,
                                                    network: channel,
                                                    language: '',
                                                    serverName: provider,
                                                    originalTitle: provider + ' · ' + channel,
                                                    streamUrl: targetUrl,
                                                    clickActionJs: clickJs,
                                                    sourceId: sourceId,
                                                    customFormattedTitle: 'Transmisión ' + (idx + 1) + ' — ' + channel,
                                                    customButtonLabel: 'T' + (idx + 1) + ': ' + channel
                                                });
                                            });

                                            if (libreBroadcasts.length > 0 &&
                                                window.AndroidPitWallBridge &&
                                                typeof window.AndroidPitWallBridge.onSpanishBroadcastsDetected === 'function') {
                                                window.AndroidPitWallBridge.onSpanishBroadcastsDetected(JSON.stringify(libreBroadcasts));
                                            }
                                            return;
                                        }

                                        if (pathname.indexOf('/agenda') !== -1) {
                                            var agendaEvents = [];
                                            var eventDivs = Array.prototype.slice.call(document.querySelectorAll('div.source-agenda-event'));
                                            eventDivs.forEach(function(div, evIdx) {
                                                var btn = div.querySelector('button.source-agenda-eventbtn');
                                                if (!btn) return;
                                                var timeSpan = btn.querySelector('span.source-agenda-time');
                                                var timeRaw = (timeSpan && timeSpan.textContent || '').trim();
                                                var compSpan = btn.querySelector('span.source-agenda-competition');
                                                var compRaw = (compSpan && compSpan.textContent || '').trim();
                                                var textSpan = btn.querySelector('span.source-agenda-eventtext');
                                                var textRaw = (textSpan && textSpan.textContent || '').trim();
                                                var combined = (compRaw ? (compRaw + ' ' + textRaw) : textRaw).replace(/\s+/g, ' ').trim();

                                                var t = combined.toLowerCase();
                                                var hasF1 = /\bf1\b|f[oó]rmula\s*(?:1|one)|f1\s*academy|motorsport.*f1/i.test(t);
                                                var isOther = /\bf2\b|\bf3\b|formula\s*[23]|motogp|indycar|nascar/i.test(t);
                                                if (isOther && !hasF1) return;
                                                if (!hasF1) {
                                                    var hasGp = /gran\s*premio|grand\s*prix|\bgp\b/i.test(t);
                                                    var hasSess = /pr[aá]cticas?\s*libres?|pr[aá]ctica\s*[123]|clasificaci[oó]n|carrera/i.test(t);
                                                    if (!hasGp || !hasSess) return;
                                                }

                                                var instant = div.getAttribute('data-source-instant') || '';
                                                var srcLinks = Array.prototype.slice.call(div.querySelectorAll('a.agenda-source-button'));
                                                var openMatch = div.querySelector('a.agenda-open-match');
                                                var href = (openMatch && openMatch.getAttribute('href') || '').trim();
                                                if (!href && srcLinks.length > 0) {
                                                    href = (srcLinks[0].getAttribute('href') || '').split('#')[0].trim();
                                                }
                                                if (!href) return;
                                                var matchUrl = href.indexOf('http') === 0 ? href : ('https://futbol-libre.app' + href);
                                                var slug = href.replace('/match/', '').split('/')[0].split('#')[0];

                                                var broadcastList = [];
                                                srcLinks.forEach(function(anchor, sIdx) {
                                                    var sp = anchor.querySelector('span');
                                                    var sm = anchor.querySelector('small');
                                                    var prov = (sp && sp.textContent || '').trim() || ('Fuente ' + (sIdx + 1));
                                                    var chan = (sm && sm.textContent || '').trim() || prov;
                                                    var sHref = (anchor.getAttribute('href') || '').trim();
                                                    var srcMatch = sHref.match(/[?#&]source=([A-Za-z0-9_-]+)/);
                                                    var sId = srcMatch ? srcMatch[1] : null;
                                                    var sTarget = sHref.indexOf('http') === 0 ? sHref : ('https://futbol-libre.app' + sHref);
                                                    var sClick = sId ? ("(function(){var id=" + JSON.stringify(sId) + ";" +
                                                        "function tryClick(){var b=document.querySelector('button.event-source-choice[data-source=\"'+id+'\"]');if(b){b.click();return true;}return false;}" +
                                                        "if(!tryClick()){var attempts=0;var timer=setInterval(function(){attempts++;if(tryClick()||attempts>=25){clearInterval(timer);}},200);}})();") : null;

                                                    broadcastList.push({
                                                        index: sIdx + 1,
                                                        network: chan,
                                                        language: '',
                                                        serverName: prov,
                                                        originalTitle: prov + ' · ' + chan,
                                                        streamUrl: sTarget,
                                                        clickActionJs: sClick,
                                                        sourceId: sId,
                                                        customFormattedTitle: 'Transmisión ' + (sIdx + 1) + ' — ' + chan,
                                                        customButtonLabel: 'T' + (sIdx + 1) + ': ' + chan
                                                    });
                                                });

                                                agendaEvents.push({
                                                    id: slug || ('event_' + (evIdx + 1)),
                                                    title: combined,
                                                    sessionName: combined.split(':').pop().trim() || combined,
                                                    gpName: 'GP de Las Vegas',
                                                    utcInstant: instant,
                                                    colombiaTime: timeRaw,
                                                    matchPath: href,
                                                    fullMatchUrl: matchUrl,
                                                    sources: broadcastList
                                                });
                                            });

                                            if (agendaEvents.length > 0 &&
                                                window.AndroidPitWallBridge &&
                                                typeof window.AndroidPitWallBridge.onFutbolLibreAgendaDetected === 'function') {
                                                window.AndroidPitWallBridge.onFutbolLibreAgendaDetected(JSON.stringify(agendaEvents));
                                            }
                                            return;
                                        }
                                    }

                                    if (host.indexOf('futbolvivo') === -1 && host.indexOf('futbollibre') === -1) return;
                                    let broadcasts = [];
                                    let counter = 1;
                                    function isEsp(txt) {
                                        return /(?:\b(?:español|spanish|castellano)\b|\[(?:spanish|español|esp|es)\]|\((?:spanish|español|esp|es)\)|\b(?:esp|es)\b)/i.test(txt || '');
                                    }
                                    function detectNet(txt) {
                                        const l = (txt || '').toLowerCase();
                                        if (l.indexOf('dazn') !== -1) return 'DAZN';
                                        if (l.indexOf('fox sports') !== -1 || l.indexOf('foxsports') !== -1) return 'FOX Sports';
                                        if (l.indexOf('fox') !== -1) return 'FOX Sports';
                                        if (l.indexOf('espn premium') !== -1) return 'ESPN Premium';
                                        if (l.indexOf('espn 1') !== -1 || l.indexOf('espn1') !== -1) return 'ESPN 1';
                                        if (l.indexOf('espn 2') !== -1 || l.indexOf('espn2') !== -1) return 'ESPN 2';
                                        if (l.indexOf('espn 3') !== -1 || l.indexOf('espn3') !== -1) return 'ESPN 3';
                                        if (l.indexOf('espn') !== -1) return 'ESPN';
                                        if (l.indexOf('movistar') !== -1) return 'Movistar';
                                        if (l.indexOf('star+') !== -1) return 'Star+';
                                        return null;
                                    }
                                    if (window.currentMatch && Array.isArray(window.currentMatch.channels)) {
                                        window.currentMatch.channels.forEach((ch, idx) => {
                                            const combined = [ch.name, ch.channel, ch.server_name, ch.language].filter(Boolean).join(' ');
                                            if (isEsp(combined)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(combined),
                                                    language: 'Español',
                                                    serverName: ch.server_name || ('Server ' + (idx + 1)),
                                                    originalTitle: combined,
                                                    streamUrl: ch.stream_url || ch.url || null,
                                                    clickActionJs: '(function(){ if(window.updatePlayer && window.currentMatch && window.currentMatch.channels[' + idx + ']) window.updatePlayer(window.currentMatch.channels[' + idx + ']); })()'
                                                });
                                            }
                                        });
                                    }
                                    if (broadcasts.length === 0 && window.currentMatch && Array.isArray(window.currentMatch.streams)) {
                                        window.currentMatch.streams.forEach((st, idx) => {
                                            const combined = [st.name, st.language].filter(Boolean).join(' ');
                                            if (isEsp(combined)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(combined),
                                                    language: 'Español',
                                                    serverName: st.name || ('Server ' + (idx + 1)),
                                                    originalTitle: combined,
                                                    streamUrl: st.url || null,
                                                    clickActionJs: '(function(){ window.activeStream = ' + idx + '; if(window.renderPlayer) window.renderPlayer(); })()'
                                                });
                                            }
                                        });
                                    }
                                    if (broadcasts.length === 0) {
                                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                                        tabs.forEach((t, idx) => {
                                            const label = (t.innerText || '').trim();
                                            if (isEsp(label)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(label),
                                                    language: 'Español',
                                                    serverName: 'Server ' + (idx + 1),
                                                    originalTitle: label,
                                                    clickActionJs: 'document.querySelectorAll("#streamTabs .se-tab-btn")[' + idx + '].click()'
                                                });
                                            }
                                        });
                                    }

                                    if (broadcasts.length > 0 && window.AndroidPitWallBridge &&
                                        typeof window.AndroidPitWallBridge.onSpanishBroadcastsDetected === 'function') {
                                        window.AndroidPitWallBridge.onSpanishBroadcastsDetected(JSON.stringify(broadcasts));
                                    } else if (broadcasts.length === 0 && window.AndroidPitWallBridge &&
                                        typeof window.AndroidPitWallBridge.onNoSpanishBroadcastsDetected === 'function') {
                                        window.AndroidPitWallBridge.onNoSpanishBroadcastsDetected();
                                    }
                                } catch(e) {}
                            }

                            cleanAndMaximize();
                            reportServers();
                            scanSpanishBroadcasts();
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 600);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 1500);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(cleanJs, null)

                    if (currentLoadedSource == BroadcastSource.FUTBOL_LIBRE) {
                        val sourceId = pendingFutbolLibreSourceId ?: activeSpanishBroadcasts.getOrNull(selectedBroadcastIndex)?.sourceId
                        if (!sourceId.isNullOrBlank()) {
                            val ensureJs = """
                                (function() {
                                    var id = '$sourceId';
                                    function ensureActive() {
                                        var b = document.querySelector('button.event-source-choice[data-source="' + id + '"]');
                                        if (b) {
                                            if (!b.classList.contains('selected') && !b.classList.contains('active-source')) {
                                                b.click();
                                            }
                                            return true;
                                        }
                                        return false;
                                    }
                                    if (!ensureActive()) {
                                        var count = 0;
                                        var t = setInterval(function() {
                                            count++;
                                            if (ensureActive() || count >= 20) {
                                                clearInterval(t);
                                            }
                                        }, 250);
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(ensureJs, null)
                        }
                        pendingFutbolLibreSourceId = null
                    }

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

        val focusedView = rootView.findFocus() ?: btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()

        sendTouch(w / 2f, h / 2f)
        webVideoView.postDelayed({
            sendTouch(w / 2f, 35f * density)
            focusedView?.post {
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
        isUserStoppedPlayback = false
        startVideoPlaybackWatchdog()
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🏎️ " + title
        webVideoView.loadUrl(targetUrl)
        updatePitWallFocusChains()
        val targetBtn = btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()
        targetBtn?.post { targetBtn.requestFocus() }
    }

    fun handleBackPressed(): Boolean {
        if (isDriverTelemetryOpen) {
            closeDriverTelemetry()
            return true
        }
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

    private var isUserStoppedPlayback: Boolean = false
    private var videoWatchdogJob: Job? = null

    private fun initPlayers() {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 45_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
        playerView.player = exoPlayer
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !isUserStoppedPlayback) {
                    exoPlayer?.seekToDefaultPosition()
                    exoPlayer?.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && !isUserStoppedPlayback) {
                    val state = exoPlayer?.playbackState
                    // Solo dar play si se detuvo y está listo, NO cuando está cargando (STATE_BUFFERING)
                    if (state == Player.STATE_READY && exoPlayer?.playWhenReady == false) {
                        exoPlayer?.play()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (isUserStoppedPlayback) return

                Log.e("PitWallViewManager", "Video playback failed: code=${error.errorCode}, message=${error.message}", error)

                val fallbackUrl = currentWebUrl
                currentDirectStreamUrl = null
                exoPlayer?.stop()

                if (!fallbackUrl.isNullOrBlank()) {
                    playerView.visibility = View.GONE
                    webVideoView.visibility = View.VISIBLE
                    updatePitWallFocusChains()
                    webVideoView.loadUrl(fallbackUrl)
                } else {
                    Toast.makeText(context, "No se pudo reproducir la señal de video", Toast.LENGTH_LONG).show()
                    switchToStandby()
                }
            }
        })

        val radioAudioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()
        radioPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(radioAudioAttrs, false)
            .build()
        radioPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val num = activeRadioDriverNumber
                    if (num != null) {
                        val acr = simulatedDrivers.find { it.driverNumber == num }?.acronym ?: "CAR$num"
                        tvRadioTitle.text = "📻 [Radio #$num $acr]: Frecuencia activa • En espera de transmisión"
                    } else {
                        tvRadioTitle.text = "📻 Radio Oficial: Canal en espera (Selecciona piloto)"
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val num = activeRadioDriverNumber
                if (num != null) {
                    val acr = simulatedDrivers.find { it.driverNumber == num }?.acronym ?: "CAR$num"
                    tvRadioTitle.text = "📻 [Radio #$num $acr]: Frecuencia conectada • Esperando transmisión en vivo"
                }
            }
        })

        // By default off-race: Standby visible, playerView hidden, NO auto-playing demo video!
        switchToStandby()
    }

    fun isDirectStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val clean = url.substringBefore("#").trim()
        val path = clean.substringBefore("?").lowercase(Locale.ROOT)

        val isManifest = path.endsWith(".m3u8") || path.endsWith(".mpd") ||
                path.contains(".m3u8/") || path.contains(".mpd/") ||
                clean.contains(".m3u8?", ignoreCase = true) || clean.contains(".mpd?", ignoreCase = true)

        // Exclude TS / AAC / key segments so we only intercept playlists/manifests
        val isSegment = path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".key") || path.endsWith(".aac")
        val isAd = listOf(
            "doubleclick", "googlesyndication", "adsystem", "adnxs",
            "adsterra", "popads", "monetag", "trafficjunky", "exoclick"
        ).any { clean.contains(it, ignoreCase = true) }

        return isManifest && !isSegment && !isAd
    }

    fun playDirectStreamInExoPlayer(
        streamUrl: String,
        referer: String? = null,
        origin: String? = null,
        userAgent: String? = null
    ) {
        if (currentDirectStreamUrl == streamUrl && exoPlayer?.isPlaying == true) {
            return
        }
        currentDirectStreamUrl = streamUrl
        isUserStoppedPlayback = false

        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE

        playerView.visibility = View.VISIBLE
        layoutPlayerStandby.visibility = View.GONE
        btnStandbyStopVideo.visibility = View.VISIBLE
        updatePitWallFocusChains()

        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)

            val requestHeaders = mutableMapOf<String, String>()
            if (!referer.isNullOrBlank()) {
                requestHeaders["Referer"] = referer
            }
            if (!origin.isNullOrBlank()) {
                requestHeaders["Origin"] = origin
            }
            if (requestHeaders.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(requestHeaders)
            }
            if (!userAgent.isNullOrBlank()) {
                httpDataSourceFactory.setUserAgent(userAgent)
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
            val mediaItem = MediaItem.fromUri(streamUrl)
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
            exoPlayer?.play()
            startVideoPlaybackWatchdog()
            Log.i("PitWallViewManager", "Direct stream routed to ExoPlayer: $streamUrl")
        } catch (e: Exception) {
            Log.e("PitWallViewManager", "Error playing direct stream in ExoPlayer", e)
            currentDirectStreamUrl = null
            playerView.visibility = View.GONE
            webVideoView.visibility = View.VISIBLE
            updatePitWallFocusChains()
            currentWebUrl?.let { url -> webVideoView.loadUrl(url) }
        }
    }

    private fun startVideoPlaybackWatchdog() {
        videoWatchdogJob?.cancel()
        videoWatchdogJob = scope.launch {
            while (isActive) {
                delay(2000L)
                if (isUserStoppedPlayback) continue

                // 1. ExoPlayer: dar play cuando se detenga, NO cuando esté cargando (STATE_BUFFERING)
                exoPlayer?.let { player ->
                    if (playerView.visibility == View.VISIBLE) {
                        val state = player.playbackState
                        if (state == Player.STATE_READY && !player.playWhenReady) {
                            player.play()
                        } else if (state == Player.STATE_ENDED) {
                            player.seekToDefaultPosition()
                            player.play()
                        }
                    }
                }

                // 2. WebView: solo dar play cuando se detenga, NO cuando está cargando (readyState >= 2)
                if (webVideoView.visibility == View.VISIBLE) {
                    val resumeJs = """
                        (function() {
                            document.querySelectorAll('video').forEach(function(v) {
                                if (v.paused && v.readyState >= 2 && !v.seeking) {
                                    v.play().catch(function() {});
                                }
                            });
                        })();
                    """.trimIndent()
                    webVideoView.evaluateJavascript(resumeJs, null)
                }
            }
        }
    }

    private fun setupListeners() {
        // Los accesos de contenido abren la fuente web configurada; no muestran vídeos de prueba.
        btnStandbyPreview.setOnClickListener { openDiscoveredFutbolEvent() }
        btnStandbyRecap.setOnClickListener { openDefaultWebSource("Cartelera de eventos F1") }
        btnStandbyOpenWeb.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }

        btnStandbyStopVideo.setOnClickListener {
            switchToStandby()
        }

        // Selección de fuentes y agenda
        btnSourceBadgePitWall?.setOnClickListener {
            showSourceSelectionDialog()
        }
        btnSessionSelector?.setOnClickListener {
            if (futbolLibreEvents.isNotEmpty()) {
                showSessionSelectionDialog(futbolLibreEvents)
            } else if (preferencesManager.broadcastSource == BroadcastSource.FUTBOL_LIBRE) {
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            } else {
                preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                updateSourceBadgeUI()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
        }
        updateSourceBadgeUI()
        updateDynamicServerFocusChain()

        // Botón de refrescar / recargar la señal en vivo si cae
        btnLiveSessionAction.setOnClickListener {
            refreshStream()
        }
        layoutLiveSessionAlert.setOnClickListener {
            refreshStream()
        }

        // Configuración de chips de Radio para todos los pilotos de la temporada 2026
        setupRadioChips()

        // Setup Adapters for new panels
        rvTyreStrategy?.layoutManager = LinearLayoutManager(context)
        rvTyreStrategy?.adapter = tyreStrategyAdapter

        rvRaceControl?.layoutManager = LinearLayoutManager(context)
        rvRaceControl?.adapter = raceControlAdapter

        // Metrics Switcher (6 Dynamic Modes)
        btnMetricsTiming.setOnClickListener { switchTelemetryMode(TelemetryMode.TIMING) }
        btnMetricsTrackMap?.setOnClickListener { switchTelemetryMode(TelemetryMode.TRACK_FIA) }
        btnMetricsTyres?.setOnClickListener { switchTelemetryMode(TelemetryMode.TYRES) }
        btnMetricsSoloTrack?.setOnClickListener { switchTelemetryMode(TelemetryMode.SOLO_TRACK) }
        btnMetricsRaceControl?.setOnClickListener { switchTelemetryMode(TelemetryMode.RACE_CONTROL) }
        btnRefreshLive?.setOnClickListener { refreshActiveTelemetryMode() }

        // Navegación fluida por D-Pad para el Reproductor de Video
        cardVideoContainer?.setOnClickListener {
            toggleVideoPlayPause()
        }
        cardVideoContainer?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        focusActiveTelemetry()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val upTarget = btnSessionSelector?.takeIf { it.isShown && it.isFocusable }
                            ?: btnSourceBadgePitWall?.takeIf { it.isShown && it.isFocusable }
                            ?: btnLiveSessionAction
                        upTarget.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val targetChip = activeRadioDriverNumber?.let { radioButtons[it] }
                            ?: radioButtons.values.firstOrNull()
                        targetChip?.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        toggleVideoPlayPause()
                        true
                    }
                    else -> false
                }
            } else false
        }

        btnBackToTiming?.setOnClickListener {
            closeDriverTelemetry()
        }

        btnTelemetryDemoToggle.setOnClickListener {
            loadDriverTelemetry(selectedDriverNumber)
        }

        switchTelemetryMode(TelemetryMode.TIMING)
    }

    private fun initBroadcastSource() {
        val selectedSource = preferencesManager.broadcastSource
        loadBroadcastSource(selectedSource, forceReload = false)
    }

    fun loadBroadcastSource(source: BroadcastSource, forceReload: Boolean = false) {
        val isSourceChange = (currentLoadedSource != source)
        if (!forceReload && !isSourceChange && activeSpanishBroadcasts.isNotEmpty() && isVideoPlaying()) {
            return
        }
        futbolLibreSourcesJob?.cancel()
        currentLoadedSource = source
        activeSpanishBroadcasts = emptyList()
        selectedBroadcastIndex = 0
        selectedServerIndex = 0
        layoutServerSelector.visibility = View.GONE
        if (source != BroadcastSource.FUTBOL_LIBRE) {
            futbolLibreEvents = emptyList()
            selectedFutbolLibreEventIndex = 0
        }

        if (forceReload || isSourceChange) {
            currentWebUrl = null
            currentDirectStreamUrl = null
            webVideoView.stopLoading()
            webVideoView.loadUrl("about:blank")
            webVideoView.visibility = View.GONE
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            playerView.visibility = View.GONE
            layoutPlayerStandby.visibility = View.VISIBLE
        }

        tvStandbyStatusBadge.text = "${source.displayName.uppercase()} • BUSCANDO CANALES EN ESPAÑOL"
        tvStandbySubtitle.text = "Consultando servidores y opciones en español de ${source.displayName}..."
        tvStandbyCircuitSpecs?.text = "FUENTE ACTIVA: ${source.displayName} (${source.defaultUrl})"
        tvLiveSessionAlertTitle.text = "📡 Conectando a ${source.displayName}..."

        when (source) {
            BroadcastSource.FLX -> {
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "📅 Ver Agenda F1"
                btnSessionSelector?.setOnClickListener {
                    preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                    updateSourceBadgeUI()
                    loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
                    Toast.makeText(context, "Cambiando a Fútbol Libre — Agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                futbolVivoExtractor.stop()
                futbolLibreExtractor.destroy()
                flxExtractor.onSpanishBroadcastsDetected = { list ->
                    onSpanishBroadcastsFound(list, BroadcastSource.FLX)
                }
                flxExtractor.onNoSpanishBroadcastsDetected = {
                    onNoSpanishBroadcastsFound(BroadcastSource.FLX)
                }
                flxExtractor.startExtraction(source.defaultUrl)
            }
            BroadcastSource.FUTBOL_VIVO -> {
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "📅 Ver Agenda F1"
                btnSessionSelector?.setOnClickListener {
                    preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                    updateSourceBadgeUI()
                    loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
                    Toast.makeText(context, "Cambiando a Fútbol Libre — Agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                flxExtractor.destroy()
                futbolLibreExtractor.destroy()
                futbolVivoExtractor.onF1EventDetected = { event ->
                    val status = if (event.isLive) "EN VIVO" else "PROGRAMADA"
                    futbolVivoStatusText = "FÚTBOL VIVO • $status"
                    discoveredFutbolEventUrl = event.eventUrl
                    discoveredAlternativeUrls = event.alternativeUrls
                    discoveredFutbolEventTitle = event.title
                    val start = event.startTime?.let { " • $it" }.orEmpty()
                    tvStandbySubtitle.text = "Evento F1 detectado: ${event.title}$start"
                    updateFutbolVivoStatusBadge()
                    btnLiveSessionAction.text = "🔄 Refrescar"

                    if (!event.eventUrl.isNullOrBlank()) {
                        playWebPage(event.eventUrl, event.title)
                    }
                }
                futbolVivoExtractor.onNoEventDetected = {
                    discoveredFutbolEventUrl = null
                    discoveredAlternativeUrls = emptyList()
                    onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_VIVO)
                }
                futbolVivoExtractor.startExtraction(source.defaultUrl)
            }
            BroadcastSource.FUTBOL_LIBRE -> {
                flxExtractor.destroy()
                futbolVivoExtractor.stop()
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "⏳ Cargando agenda..."
                btnSessionSelector?.setOnClickListener {
                    Toast.makeText(context, "Consultando agenda de carreras en futbol-libre.app/agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                futbolLibreExtractor.onEventsDetected = { events ->
                    onFutbolLibreEventsFound(events)
                }
                futbolLibreExtractor.onNoEventsDetected = {
                    onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_LIBRE)
                }
                futbolLibreExtractor.startExtraction(source.defaultUrl)
            }
        }
    }

    private fun onFutbolLibreEventsFound(events: List<FutbolLibreEvent>) {
        futbolLibreEvents = events
        selectedFutbolLibreEventIndex = 0

        if (events.isEmpty()) {
            onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_LIBRE)
            return
        }

        btnSessionSelector?.visibility = View.VISIBLE
        btnSessionSelector?.setOnClickListener {
            showSessionSelectionDialog(futbolLibreEvents)
        }

        selectFutbolLibreEvent(0)
    }

    private fun updateSessionSelectorButton(event: FutbolLibreEvent) {
        val count = futbolLibreEvents.size
        btnSessionSelector?.text = "📅 ${event.getShortLabel()} ($count) ▼"
    }

    fun selectFutbolLibreEvent(index: Int) {
        if (index !in futbolLibreEvents.indices) return
        futbolLibreSourcesJob?.cancel()
        selectedFutbolLibreEventIndex = index
        selectedBroadcastIndex = 0
        activeSpanishBroadcasts = emptyList()
        layoutServerSelector.visibility = View.GONE

        val event = futbolLibreEvents[index]
        updateSessionSelectorButton(event)
        val categoryPrefix = if (event.isF1Academy) "F1 ACADEMY • " else ""
        tvStandbyTitle.text = (categoryPrefix + event.gpName).uppercase()
        tvStandbySubtitle.text = event.title + " • " + event.colombiaTime
        tvStandbyStatusBadge.text = (if (event.isF1Academy) "F1 ACADEMY" else "FÚTBOL LIBRE") + " • " + event.sources.size + " TRANSMISIÓN(ES)"

        if (event.sources.isNotEmpty()) {
            onSpanishBroadcastsFound(event.sources, BroadcastSource.FUTBOL_LIBRE)
            val currentBroadcast = event.sources[0]
            val catAlert = if (event.isF1Academy) " [F1 Academy]" else ""
            tvLiveSessionAlertTitle.text = "🏎️ " + event.gpName + catAlert + " — " + event.sessionName + " — " + currentBroadcast.getFormattedTitle()
            return
        }

        tvLiveSessionAlertTitle.text = "Consultando fuentes de " + event.sessionName + "..."
        futbolLibreSourcesJob = scope.launch(Dispatchers.IO) {
            val matchSources = futbolLibreExtractor.fetchSourcesForMatch(event.fullMatchUrl)
            withContext(Dispatchers.Main) {
                val selectedEvent = futbolLibreEvents.getOrNull(selectedFutbolLibreEventIndex)
                if (selectedEvent?.id != event.id || currentLoadedSource != BroadcastSource.FUTBOL_LIBRE) {
                    return@withContext
                }
                if (matchSources.isNotEmpty()) {
                    futbolLibreEvents = futbolLibreEvents.toMutableList().also { events ->
                        events[index] = events[index].copy(sources = matchSources)
                    }
                    onSpanishBroadcastsFound(matchSources, BroadcastSource.FUTBOL_LIBRE)
                    val currentBroadcast = matchSources[0]
                    val catAlert = if (event.isF1Academy) " [F1 Academy]" else ""
                    tvLiveSessionAlertTitle.text = "🏎️ " + event.gpName + catAlert + " — " + event.sessionName + " — " + currentBroadcast.getFormattedTitle()
                } else {
                    tvLiveSessionAlertTitle.text = event.title
                    tvStandbySubtitle.text = "No se encontraron fuentes en el HTML inicial. Abriendo la ficha del evento..."
                    playWebPage(event.fullMatchUrl, event.title)
                }
            }
        }
    }

    private fun showSessionSelectionDialog(events: List<FutbolLibreEvent>) {
        val sessionNames = events.map {
            val prefix = if (it.isF1Academy) "🏁 [F1 Academy] " else "🏎️ [F1] "
            "$prefix${it.title} (${it.colombiaTime})"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Seleccionar Sesión de Carreras")
            .setSingleChoiceItems(sessionNames, selectedFutbolLibreEventIndex) { dialog, which ->
                dialog.dismiss()
                selectFutbolLibreEvent(which)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun updateFutbolVivoStatusBadge() {
        if (currentLoadedSource == BroadcastSource.FUTBOL_VIVO && activeSpanishBroadcasts.isEmpty()) {
            tvStandbyStatusBadge.text = futbolVivoStatusText
        }
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
        updateSourceBadgeUI()
        val targetFocus = btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()
        targetFocus?.post { targetFocus.requestFocus() }
        val selectedSource = preferencesManager.broadcastSource

        if (currentLoadedSource != selectedSource) {
            // Source changed in Settings! Immediate reload for new provider
            loadBroadcastSource(selectedSource, forceReload = true)
        } else if (!isVideoPlaying()) {
            if (activeSpanishBroadcasts.isNotEmpty()) {
                selectSpanishBroadcast(selectedBroadcastIndex)
            } else {
                loadBroadcastSource(selectedSource, forceReload = false)
            }
        }
    }

    fun playStream(url: String, title: String? = null) {
        if (!isDirectMediaUrl(url)) {
            playWebPage(url, title ?: "Fuente web")
            return
        }

        // Los enlaces directos usan el mismo flujo ExoPlayer que las señales detectadas:
        // registra la URL activa y evita reintentos infinitos si la reproducción falla.
        currentWebUrl = null
        playDirectStreamInExoPlayer(url)

        if (title != null) {
            tvLiveSessionAlertTitle.text = "🏎️ $title"
            Toast.makeText(context, "Cargando señal: $title", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchToStandby() {
        isUserStoppedPlayback = true
        currentDirectStreamUrl = null
        videoWatchdogJob?.cancel()
        if (customFullScreenView != null) hideCustomView()
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
        currentWebUrl = null
        exoPlayer?.pause()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.GONE
        updateScheduleBanner()
        updatePitWallFocusChains()
    }

    fun playCurrentOrSessionRadio() {
        tvRadioTitle.text = "📻 Buscando radio oficial de la sesión..."
        scope.launch {
            try {
                val key = currentSessionKey
                val radios = if (key != null) {
                    repository.getSessionTeamRadios(key)
                } else emptyList()

                val playableRadios = radios.filter { !it.recordingUrl.isNullOrEmpty() }
                if (playableRadios.isNotEmpty()) {
                    latestRadioUrl = playableRadios.first().recordingUrl
                    tvRadioTitle.text = "📻 Radio Oficial de Sesión • ${playableRadios.size} audios"
                    radioPlayer?.let { player ->
                        player.stop()
                        player.clearMediaItems()
                        for (r in playableRadios) {
                            r.recordingUrl?.let { url ->
                                player.addMediaItem(MediaItem.fromUri(url))
                            }
                        }
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        player.prepare()
                        player.play()
                    }
                    Toast.makeText(context, "Reproduciendo radios de la sesión (${playableRadios.size} audios)", Toast.LENGTH_SHORT).show()
                } else {
                    latestRadioUrl?.let { url ->
                        val mediaItem = MediaItem.fromUri(url)
                        radioPlayer?.clearMediaItems()
                        radioPlayer?.setMediaItem(mediaItem)
                        radioPlayer?.prepare()
                        radioPlayer?.play()
                        Toast.makeText(context, "Reproduciendo último audio guardado", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(context, "No hay audios de radio disponibles para esta sesión", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setupRadioChips() {
        val container = llRadioChips ?: return
        container.removeAllViews()
        radioButtons.clear()

        val density = context.resources.displayMetrics.density
        val btnHeight = (24 * density).toInt()
        val marginEnd = (4 * density).toInt()
        val paddingH = (9 * density).toInt()

        simulatedDrivers.forEach { driver ->
            val btn = Button(context).apply {
                id = View.generateViewId()
                text = driver.acronym
                textSize = 9f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.btn_tab_selector)
                isClickable = true
                isFocusable = true
                minWidth = (38 * density).toInt()
                minimumWidth = (38 * density).toInt()
                setPadding(paddingH, 0, paddingH, 0)
                isSelected = (activeRadioDriverNumber == driver.driverNumber)
                nextFocusUpId = R.id.btnSourceBadgePitWall

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        scrollRadioChips?.smoothScrollTo((left - 40).coerceAtLeast(0), 0)
                    }
                }

                setOnClickListener {
                    toggleRadioDriver(driver.driverNumber, driver.fullName, driver.acronym)
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                btnHeight
            ).apply {
                this.marginEnd = marginEnd
            }

            container.addView(btn, lp)
            radioButtons[driver.driverNumber] = btn
        }

        // Setup horizontal D-pad focus chain across all radio chips
        for (i in 0 until container.childCount) {
            val current = container.getChildAt(i)
            if (i > 0) {
                current.nextFocusLeftId = container.getChildAt(i - 1).id
            }
            current.nextFocusDownId = R.id.rvLeaderboard
            if (i < container.childCount - 1) {
                current.nextFocusRightId = container.getChildAt(i + 1).id
            } else {
                current.nextFocusRightId = R.id.rvLeaderboard
            }
        }
        updatePitWallFocusChains()
    }

    fun toggleRadioDriver(driverNumber: Int, driverName: String, acronym: String) {
        if (activeRadioDriverNumber == driverNumber) {
            turnOffRadio()
        } else {
            selectRadioDriver(driverNumber, driverName, acronym)
        }
    }

    fun turnOffRadio() {
        radioPollingJob?.cancel()
        radioPollingJob = null
        activeRadioDriverNumber = null
        lastPlayedRadioUrl = null
        radioButtons.values.forEach { it.isSelected = false }
        try {
            radioPlayer?.stop()
            radioPlayer?.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tvRadioTitle.text = "📻 Radio Oficial: Canal en espera (Selecciona piloto)"
        Toast.makeText(context, "📻 Radio desactivada", Toast.LENGTH_SHORT).show()
    }

    fun selectRadioDriver(driverNumber: Int, driverName: String, acronym: String) {
        activeRadioDriverNumber = driverNumber
        radioButtons.forEach { (num, btn) ->
            btn.isSelected = (num == driverNumber)
        }
        tuneRadioDriver(driverNumber, driverName, acronym)
    }

    fun getSimulatedRadioQuote(driverNumber: Int, driverName: String, phase: Int): String {
        val quotes = when (driverNumber) {
            1 -> listOf(
                "Max: 'El balance es sólido, neumáticos traseros calientes pero gestiono bien el ritmo.'",
                "Ingeniero: 'Max, pasa a modo 7. La ventaja con Norris es de 1.8s. Protege la entrada en Curva 1.'",
                "Max: 'El coche responde perfecto en el Sector 2. La entrega de batería es buena.'",
                "Ingeniero: 'Copiado Max, ventana de parada en 6 vueltas, confirma boxes cuando estés listo.'"
            )
            4 -> listOf(
                "Lando: 'La distancia con Max es estable en 1.4s, empujando con medios.'",
                "Ingeniero: 'Lando, usa estrategia 3 cuando entres en zona de DRS.'",
                "Lando: 'El agarre trasero sigue muy positivo, ¡mantengamos la presión!'",
                "Ingeniero: 'Gran vuelta Lando, sector 2 en récord púrpura.'"
            )
            16 -> listOf(
                "Charles: 'Boxes esta vuelta para montar duros. Confirmen que la ventana está abierta.'",
                "Ingeniero: 'A boxes Charles, entra por duros. Confirmado.' - Charles: '¡Copiado!'",
                "Charles: 'El alerón delantero va bien, los neumáticos aguantan en curvas rápidas.'",
                "Ingeniero: 'Entendido Charles, Hamilton está a +2.1s con idéntica estrategia.'"
            )
            44 -> listOf(
                "Lewis: '¿Cuál es nuestra diferencia con el coche de adelante? Tenemos buen ritmo.'",
                "Ingeniero: 'Lewis, eres dos décimas por vuelta más rápido. La distancia se reduce.'",
                "Lewis: 'Entendido, los neumáticos responden genial. Empujando al máximo en la salida.'",
                "Ingeniero: 'Copiado Lewis, mantén este ritmo, estamos en pelea por el podio.'"
            )
            63 -> listOf(
                "George: 'Las temperaturas de los neumáticos están en la ventana óptima ahora.'",
                "Ingeniero: 'George, pon modo de recarga 2 en la salida de la Curva 4.'",
                "George: 'Tengo gran rebufo en la recta principal, me acerco al Ferrari de adelante.'",
                "Ingeniero: 'Entendido George, usa el botón de adelantamiento en la próxima vuelta.'"
            )
            81 -> listOf(
                "Oscar: 'Entendido. Preparando el adelantamiento con DRS en la recta siguiente.'",
                "Ingeniero: 'Oscar, entrega de energía disponible en la salida. El coche de adelante tiene degradación.'",
                "Oscar: 'Adelantamiento limpio en Curva 1. Aire limpio por delante ahora.'",
                "Ingeniero: 'Gran maniobra Oscar, ahora abre la ventaja a 2 segundos.'"
            )
            14 -> listOf(
                "Fernando: '¡Gran rebufo en la recta trasera, vamos al ataque ahora!'",
                "Ingeniero: 'Fernando, Carlos está a 1.1s adelante, DRS habilitado esta vuelta.'",
                "Fernando: 'El coche va con vida, ¡tenemos el ritmo para pelear por esta posición!'",
                "Ingeniero: 'Copiado Fernando, la ventaja atrás es de 4 segundos, modo de ataque total.'"
            )
            55 -> listOf(
                "Carlos: 'La degradación es menor a la esperada, me quedo fuera 3 vueltas más.'",
                "Ingeniero: 'Copiado Carlos, Plan B confirmado. Momento de empujar a fondo.'",
                "Carlos: 'El equilibrio del Williams es excelente en el Sector 1, defendemos en Curva 3.'",
                "Ingeniero: 'Pista despejada adelante Carlos, empuja para conseguir el undercut.'"
            )
            23 -> listOf(
                "Alex: 'La velocidad en recta es fuerte hoy. Defendiendo bien en Curva 1.'",
                "Ingeniero: 'Alex, ajusta el diferencial en mitad de curva +1 para tracción.'",
                "Alex: 'Copiado, el coche se siente mucho mejor en la frenada ahora.'",
                "Ingeniero: 'Gran vuelta Alex, manteniendo la 9ª posición con comodidad.'"
            )
            12 -> listOf(
                "Kimi: 'El delantero izquierdo empieza a desgastarse un poco, ¿cuál es el delta de ritmo?'",
                "Ingeniero: 'Kimi, el delta es +0.3s. Sé suave con la dirección en la Curva 6.'",
                "Kimi: 'Copiado, gestionando el neumático delantero. La velocidad en recta es excelente.'",
                "Ingeniero: 'Trabajo muy sólido Kimi, los tiempos de vuelta igualan al top 6.'"
            )
            10 -> listOf(
                "Pierre: 'Los frenos se sienten muy consistentes. ¡Estamos en zona de puntos!'",
                "Ingeniero: 'Pierre, motor en modo 4, defiende la posición frente al Haas detrás.'",
                "Pierre: 'Entendido, tren de DRS adelante, esperando mi oportunidad para pasar.'",
                "Ingeniero: 'Copiado Pierre, el consumo de combustible es favorable, ataca cuando puedas.'"
            )
            31 -> listOf(
                "Esteban: 'La pista está mejorando rápido. El equilibrio del Haas es firme.'",
                "Ingeniero: 'Esteban, a boxes en 2 vueltas para montar juego nuevo de duros.'",
                "Esteban: 'Copiado, confirmo boxes. La brecha atrás es segura para la salida.'",
                "Ingeniero: 'Excelente trabajo Esteban, luz verde para salir de boxes.'"
            )
            27 -> listOf(
                "Nico: 'Temperatura de neumáticos buena, ¿podemos alargar una vuelta más antes de parar?'",
                "Ingeniero: 'Copiado Nico, alargamos 2 vueltas, la ventana de parada está en verde.'",
                "Nico: 'La respuesta de par del motor Audi es muy progresiva al salir de la chicane.'",
                "Ingeniero: 'Copiado Nico, ritmo sólido, asegurando la 13ª posición.'"
            )
            22 -> listOf(
                "Yuki: '¡El coche tiene gran tracción saliendo de la horquilla, voy por el interior!'",
                "Ingeniero: '¡Brillante maniobra Yuki! Mantén la calma y cuida las gomas traseras.'",
                "Yuki: '¡Sí! ¡Vamos a cazar al coche de adelante ahora mismo!'",
                "Ingeniero: 'Copiado Yuki, la distancia con el 13° puesto es de 1.5 segundos.'"
            )
            30 -> listOf(
                "Liam: 'Copiado, igualando el ritmo del grupo en el Sector 1.'",
                "Ingeniero: 'Liam, balance de frenos al 56% para la chicane de la Curva 10.'",
                "Liam: 'Entendido, el tren delantero entra mucho mejor en desaceleración.'",
                "Ingeniero: 'Buen progreso Liam, sigue aumentando la ventaja.'"
            )
            87, 50 -> listOf(
                "Oliver: 'El ritmo mejora en cada vuelta. ¿Dónde estamos perdiendo tiempo?'",
                "Ingeniero: 'Velocidad en el vértice de Curva 3, la ganancia está en acelerar antes.'",
                "Oliver: 'Entendido, ¡gané una décima en esa vuelta al pasar por Curva 3!'",
                "Ingeniero: 'Copiado Ollie, la telemetría lo confirma, mantén esa trazada.'"
            )
            6 -> listOf(
                "Isack: 'Apreté fuerte en esa vuelta, el DRS funciona con normalidad.'",
                "Ingeniero: 'Copiado Isack, el sector 3 fue tu mejor registro personal.'",
                "Isack: 'El monoplaza va muy estable en los cambios rápidos de dirección.'",
                "Ingeniero: 'Entendido Isack, manteniendo la diferencia con el monoplaza de adelante.'"
            )
            5 -> listOf(
                "Gabriel: 'El equilibrio del coche está muy parejo, el desgaste de neumáticos es limpio.'",
                "Ingeniero: 'Mantén este ritmo Gabriel, estás rodando con mucha consistencia.'",
                "Gabriel: 'Prueba de radio, los escucho fuerte y claro. Empujando ahora.'",
                "Ingeniero: 'Fuerte y claro Gabriel, 3 vueltas para la siguiente parada de estrategia.'"
            )
            7 -> listOf(
                "Jack: 'El viento aumentó en la Curva 7, leve viento de cola en la frenada.'",
                "Ingeniero: 'Entendido Jack, frena 5 metros antes en la entrada a la Curva 7.'",
                "Jack: 'Copiado, la parte trasera no se movió esta vez.'",
                "Ingeniero: 'Buena adaptación Jack, sigue cuidando el consumo de gasolina.'"
            )
            18 -> listOf(
                "Lance: 'El desgaste de gomas se ve sólido, confirmen entrada para la próxima vuelta.'",
                "Ingeniero: 'Copiado Lance, a boxes en la siguiente vuelta para montar medios.'",
                "Lance: 'Entendido, entrando al pit lane en esta vuelta.'",
                "Ingeniero: 'Equipo de mecánicos listo Lance, adentro en esta vuelta.'"
            )
            11 -> listOf(
                "Checo: 'El equilibrio del Cadillac se siente genial en el Sector 1. ¡Hay buen ritmo!'",
                "Ingeniero: 'Gran información Checo. Tenemos excelente velocidad punta, ¡a empujar ahora!'",
                "Checo: 'Copiado, cuidando el neumático delantero derecho, listo para atacar en Curva 1.'",
                "Ingeniero: 'Checo, el DRS está habilitado. ¡Vamos a buscar el sobrepaso en la recta!'"
            )
            77 -> listOf(
                "Valtteri: 'El motor y la potencia híbrida empujan fuerte. ¡Alcanzando al pelotón!'",
                "Ingeniero: 'Copiado Valtteri, DRS habilitado, usa modo de energía 4 para defender.'",
                "Valtteri: 'Para quien corresponda... ¡el ritmo del Cadillac es sumamente competitivo!'",
                "Ingeniero: 'Entendido Valtteri, récord en trampa de velocidad, ¡impecable trabajo!'"
            )
            else -> listOf("$driverName: 'Boxes en esta vuelta para neumáticos duros, confirmen parada.'")
        }
        val safeIndex = kotlin.math.abs(phase) % quotes.size
        return quotes[safeIndex]
    }

    data class DriverTelemetrySnapshot(
        val speed: Int,
        val brake: Int,
        val throttle: Int,
        val drsActive: Boolean,
        val isPitting: Boolean,
        val tyreCompound: String,
        val tyreAge: Int,
        val position: Int,
        val gapAhead: Double,
        val sector: Int
    )

    fun getDriverTelemetrySnapshot(driverNumber: Int): DriverTelemetrySnapshot {
        if (isSimulationMode) {
            val driver = activeSimDrivers.find { it.driverNumber == driverNumber }
                ?: simulatedDrivers.find { it.driverNumber == driverNumber }?.let { d ->
                    SimDriverState(d.driverNumber, d.position, d.acronym, d.fullName, d.teamName, d.teamColor, d.tyreCompound, d.tyreAge, d.bestLap, d.bestLap, 0.0, 0.0)
                }
            val simMapDriver = trackMapView?.getSimulationDrivers()?.find { it.driverNumber == driverNumber }
            val trackPoints = trackMapView?.getTrackPoints() ?: emptyList()
            val tick = simulationTick

            val spd: Int
            val brk: Int
            val thr: Int
            val drsActive: Boolean
            val sector: Int
            val isPitting = driver?.isPitting == true

            if (simMapDriver != null && trackPoints.size >= 3) {
                val n = trackPoints.size
                val p = ((simMapDriver.trackProgress % n) + n) % n
                val curDot = calculateTrackCurvature(p, trackPoints)
                val lookDot = calculateLookaheadCurvature(p, trackPoints, 3)

                val s1End = n / 3
                val s2End = (2 * n) / 3
                sector = when {
                    p < s1End -> 1
                    p < s2End -> 2
                    else -> 3
                }

                if (isPitting) {
                    spd = 80
                    thr = 40
                    brk = 0
                    drsActive = false
                } else if (curDot >= 0.94f && lookDot >= 0.93f) {
                    val straightSpeedBonus = ((curDot - 0.94f) / 0.06f * 55f).toInt().coerceIn(0, 55)
                    spd = 285 + straightSpeedBonus + ((tick * 3) % 9)
                    thr = 99
                    brk = 0
                    drsActive = spd > 275
                } else if (curDot >= 0.93f && lookDot < 0.90f) {
                    spd = 195 + ((tick * 4) % 25)
                    thr = 0
                    brk = 92
                    drsActive = false
                } else {
                    val cornerSeverity = ((1f - curDot) / 0.3f).coerceIn(0f, 1f)
                    spd = (120 + ((1f - cornerSeverity) * 45f)).toInt() + ((tick * 2) % 6)
                    thr = (35 + ((1f - cornerSeverity) * 35f)).toInt()
                    brk = if (cornerSeverity > 0.6f && tick % 4 == 0) 65 else 0
                    drsActive = false
                }
            } else {
                val inStraight = (tick % 32) in 0..19
                spd = if (inStraight) 305 + ((tick * 3) % 28) else 125 + ((tick * 5) % 35)
                thr = if (inStraight) 98 else 35
                brk = if (!inStraight && (tick % 32 in 20..24)) 85 else 0
                drsActive = inStraight && (tick % 16 in 0..7)
                sector = 1 + ((tick / 40) % 3)
            }

            return DriverTelemetrySnapshot(
                speed = spd,
                brake = brk,
                throttle = thr,
                drsActive = drsActive,
                isPitting = isPitting,
                tyreCompound = driver?.tyreCompound ?: "MEDIUM",
                tyreAge = driver?.tyreAge ?: 15,
                position = driver?.position ?: 1,
                gapAhead = driver?.intervalToAhead ?: 1.0,
                sector = sector
            )
        } else {
            val d = simulatedDrivers.find { it.driverNumber == driverNumber }
            return DriverTelemetrySnapshot(
                speed = 295,
                brake = 0,
                throttle = 98,
                drsActive = true,
                isPitting = false,
                tyreCompound = d?.tyreCompound ?: "MEDIUM",
                tyreAge = d?.tyreAge ?: 14,
                position = d?.position ?: 1,
                gapAhead = 1.1,
                sector = 2
            )
        }
    }

    fun getTelemetryAwareRadioQuote(
        driverNumber: Int,
        driverName: String,
        acronym: String,
        snapshot: DriverTelemetrySnapshot,
        phase: Int = 0
    ): String {
        // 1. Pit stop / Limitador de boxes (80 km/h)
        if (snapshot.isPitting || (snapshot.speed in 1..85 && snapshot.brake == 0)) {
            return when (driverNumber) {
                1 -> "Max: 'Box confirmado. Limitador a 80 km/h activado.' - Ing: 'Copiado Max, montamos duros. ¡Parada limpia!'"
                4 -> "Lando: 'Entrando al pit lane ahora.' - Ing: 'Mantén el delta en verde, mecánicos listos en el cajón.'"
                16 -> "Charles: 'Boxes en esta vuelta para neumáticos duros.' - Ing: 'Confirmado Charles, parada limpia esperada.'"
                44 -> "Lewis: 'Box, box, box. Neumáticos desgastados.' - Ing: 'Entrando Lewis, limitador listo, salida limpia.'"
                63 -> "George: 'Limitador activado, entrando al pit lane.' - Ing: 'Copiado George, ajuste de alerón +1 clic.'"
                81 -> "Oscar: 'Entrando a boxes en esta vuelta.' - Ing: 'Copiado Oscar, juego nuevo de medios preparado.'"
                14 -> "Fernando: '¡Entrando a boxes! Gomas listas para el ataque final.' - Ing: 'Copiado Fernando, luz verde.'"
                55 -> "Carlos: 'A boxes en esta vuelta para el undercut.' - Ing: 'Copiado Carlos, Plan B en marcha.'"
                23 -> "Alex: 'Limitador de pit activado, 80 km/h estables.' - Ing: 'Copiado Alex, excelente ingreso.'"
                12 -> "Kimi: 'Entrando al pit lane para parada programada.' - Ing: 'Tranquilo Kimi, parada limpia y salimos.'"
                10 -> "Pierre: 'Boxes en esta vuelta, confirmen compuesto.' - Ing: 'Entendido Pierre, juego nuevo de duros.'"
                31 -> "Esteban: 'Entrando al cajón de parada ahora.' - Ing: 'Copiado Esteban, servicio rápido.'"
                27 -> "Nico: 'Limitador de boxes puesto.' - Ing: 'Copiado Nico, parada estratégica confirmada.'"
                22 -> "Yuki: '¡Box, box! ¡Listos con los neumáticos!' - Ing: '¡Listos Yuki! Parada rápida.'"
                30 -> "Liam: 'Entrando a boxes, confirmo parada.' - Ing: 'Copiado Liam, buen ritmo de entrada.'"
                87, 50 -> "Oliver: 'Limitador activado, entrando a cambiar gomas.' - Ing: 'Copiado Ollie, mantén la calma.'"
                6 -> "Isack: 'Entrando a boxes, parada programada.' - Ing: 'Copiado Isack, mecánicos en posición.'"
                5 -> "Gabriel: 'Pit limiter activado, cambio de gomas.' - Ing: 'Copiado Gabriel, parada veloz.'"
                7 -> "Jack: 'Entrando a pit lane en esta vuelta.' - Ing: 'Copiado Jack, confirmamos medios.'"
                18 -> "Lance: 'Entrando a boxes, gomas listas.' - Ing: 'Copiado Lance, adentro en esta vuelta.'"
                11 -> "Checo: 'Limitador activado en el Cadillac, parada en boxes.' - Ing: '¡Excelente Checo, parada limpia y a pista!'"
                77 -> "Valtteri: 'Box en esta vuelta, cambio por neumáticos duros.' - Ing: 'Copiado Valtteri, mecánicos listos en el box.'"
                else -> "$acronym: 'Entrando al pit lane con limitador a 80 km/h.' - Ing: 'Confirmado, parada limpia.'"
            }
        }

        // 2. Recta de alta velocidad / DRS activado / Trampa de velocidad
        if (snapshot.drsActive || snapshot.speed >= 280) {
            return when (driverNumber) {
                1 -> "Max: 'DRS abierto en la recta, el balance es perfecto.' - Ing: 'Pasa a modo 7 Max, empuja a fondo.'"
                4 -> "Lando: '¡Gran rebufo con DRS! La velocidad punta es tremenda.' - Ing: 'Estrategia 3 habilitada Lando.'"
                16 -> "Charles: 'Abriendo DRS en la recta larga, recuperamos terreno.' - Ing: 'Entrega de energía disponible.'"
                44 -> "Lewis: 'Tengo DRS, voy a la caza del coche de adelante.' - Ing: 'Lewis, eres dos décimas más rápido en recta.'"
                63 -> "George: 'DRS activo, cerrando la brecha a máxima velocidad.' - Ing: 'Usa el botón de adelantamiento George.'"
                81 -> "Oscar: 'Activando DRS, maniobra de adelantamiento en marcha.' - Ing: 'Adelante Oscar, pista despejada.'"
                14 -> "Fernando: '¡DRS habilitado, velocidad punta al máximo, voy al ataque!' - Ing: '¡Ataque total Fernando!'"
                55 -> "Carlos: 'Excelente velocidad en la trampa con DRS.' - Ing: 'El Williams vuela en recta, mantén la presión Carlos.'"
                23 -> "Alex: 'DRS abierto, velocidad punta formidable en el sector.' - Ing: 'Gran delta en recta Alex.'"
                12 -> "Kimi: 'DRS activado, alcanzando velocidad de punta.' - Ing: 'Excelente ritmo Kimi, récord en sector 2.'"
                10 -> "Pierre: 'Abriendo DRS en recta principal, buscando el hueco.' - Ing: 'Motor en modo ataque Pierre.'"
                31 -> "Esteban: 'Buena velocidad con DRS en la recta.' - Ing: 'Mantén el rebufo Esteban.'"
                27 -> "Nico: 'DRS abierto, el motor Audi empuja fuerte.' - Ing: 'Copiado Nico, velocidad récord en sector.'"
                22 -> "Yuki: '¡DRS abierto! ¡Voy a pasarlo en la recta!' - Ing: '¡Vamos Yuki, tienes la velocidad!'"
                30 -> "Liam: 'DRS activado, ganando tiempo en la recta.' - Ing: 'Buen ritmo Liam, mantén el acelerador.'"
                87, 50 -> "Oliver: 'Abriendo DRS, el coche se siente muy rápido.' - Ing: 'Copiado Ollie, excelente velocidad punta.'"
                6 -> "Isack: 'DRS abierto, cerrando la distancia.' - Ing: 'Sector récord Isack, sigue así.'"
                5 -> "Gabriel: 'DRS activo, el monoplaza vuela en la recta.' - Ing: 'Muy bien Gabriel, ganas 3 décimas.'"
                7 -> "Jack: 'DRS habilitado en la zona de detección.' - Ing: 'Copiado Jack, despliegue de batería listo.'"
                18 -> "Lance: 'Abriendo DRS en la recta de atrás.' - Ing: 'Copiado Lance, ritmo competitivo.'"
                11 -> "Checo: '¡DRS abierto en el Cadillac, enorme velocidad punta!' - Ing: '¡Checo, récord en la trampa de velocidad!'"
                77 -> "Valtteri: 'DRS activado, alcanzando a los coches de adelante.' - Ing: '¡Potencia al 100% Valtteri, gran velocidad!'"
                else -> "$acronym: 'DRS activado en recta, velocidad punta máxima.' - Ing: 'Acelerador a fondo, gran ritmo.'"
            }
        }

        // 3. Frenada fuerte / Vértice de curva
        if (snapshot.brake >= 50 || (snapshot.speed in 90..165 && snapshot.throttle <= 40)) {
            return when (driverNumber) {
                1 -> "Max: 'Frenada muy firme en el vértice, el tren delantero responde.' - Ing: 'Copiado, excelente trazada.'"
                4 -> "Lando: 'Fuerte deceleración, los frenos muerden con precisión.' - Ing: 'Cuida la tracción al salir Lando.'"
                16 -> "Charles: 'Frenada tardía en la curva cerrada, sin bloqueos.' - Ing: 'Gran entrada Charles, mantén el balance.'"
                44 -> "Lewis: 'Coche plantado en la frenada de la horquilla.' - Ing: 'Impecable Lewis, ganas tiempo en la entrada.'"
                63 -> "George: 'Mucho agarre en desaceleración, pasando por el vértice.' - Ing: 'Diferencial en modo +1 George.'"
                81 -> "Oscar: 'Frenada controlada, trazada limpia en la chicane.' - Ing: 'Muy consistente Oscar, tracción óptima.'"
                14 -> "Fernando: '¡Frenando tarde en el interior de la curva!' - Ing: '¡Maestría en la frenada Fernando!'"
                55 -> "Carlos: 'El monoplaza no se desestabiliza en frenada fuerte.' - Ing: 'Balance perfecto Carlos.'"
                23 -> "Alex: 'Frenos consistentes, buena rotación en curva lenta.' - Ing: 'Excelente Alex, acelerando ahora.'"
                12 -> "Kimi: 'Frenada limpia, cuidando no bloquear el delantero.' - Ing: 'Muy suave con la dirección Kimi.'"
                10 -> "Pierre: 'Frenando fuerte en la entrada de la chicana.' - Ing: 'Copiado Pierre, buena deceleración.'"
                31 -> "Esteban: 'Balance de frenos correcto para la curva 1.' - Ing: 'Entendido Esteban, tracción limpia.'"
                27 -> "Nico: 'Sin problemas en la frenada brusca.' - Ing: 'Copiado Nico, trazada impecable.'"
                22 -> "Yuki: '¡Frenando al límite por dentro!' - Ing: '¡Cuidado con el piano Yuki, gran control!'"
                30 -> "Liam: 'Frenada estable, el coche rota bien.' - Ing: 'Copiado Liam, balance de frenos al 56%.'"
                87, 50 -> "Oliver: 'Gran frenada en la curva técnica.' - Ing: 'Ganaste una décima en el vértice Ollie.'"
                6 -> "Isack: 'Desaceleración limpia sin sobreviraje.' - Ing: 'Muy bien Isack, acelera con confianza.'"
                5 -> "Gabriel: 'Frenos en temperatura ideal, buena parada.' - Ing: 'Consistente Gabriel, sigue así.'"
                7 -> "Jack: 'Frenando 5 metros antes por el viento.' - Ing: 'Buena adaptación Jack, parte trasera firme.'"
                18 -> "Lance: 'Frenada profunda en la horquilla.' - Ing: 'Copiado Lance, buena rotación.'"
                11 -> "Checo: 'El Cadillac frena con enorme estabilidad en Curva 1.' - Ing: '¡Gran frenada Checo, preparando tracción!'"
                77 -> "Valtteri: 'Frenada precisa, sin bloqueo de neumáticos.' - Ing: 'Copiado Valtteri, salida limpia.'"
                else -> "$acronym: 'Frenada fuerte en la curva, el balance responde.' - Ing: 'Trazada limpia, cuida la salida.'"
            }
        }

        // 4. Alta degradación de neumáticos
        if (snapshot.tyreAge >= 18) {
            return when (driverNumber) {
                1 -> "Max: 'Neumáticos traseros con desgaste, pero ritmo bajo control.' - Ing: 'Copiado Max, ventana en 3 vueltas.'"
                4 -> "Lando: 'Empiezo a deslizar con los medios en curvas lentas.' - Ing: 'Gestiona en curvas rápidas Lando.'"
                16 -> "Charles: 'Los neumáticos sufren en curvas de alta carga.' - Ing: 'Copiado Charles, parada pronto.'"
                44 -> "Lewis: 'Las gomas traseras están perdiendo agarre.' - Ing: 'Lewis, alargamos dos vueltas para el undercut.'"
                14 -> "Fernando: 'Gomas desgastadas pero el ritmo sigue firme.' - Ing: 'Genial gestión Fernando, ventaja de 3s.'"
                55 -> "Carlos: 'Degradación progresiva, cuidando la delantera izquierda.' - Ing: 'Copiado Carlos, Plan B listo.'"
                else -> "$acronym: 'Empieza a notarse la degradación en los neumáticos.' - Ing: 'Copiado, gestiona el ritmo.'"
            }
        }

        // 5. Persecución / Batalla cercana por posición (< 1.5s)
        if (snapshot.gapAhead in 0.1..1.5 && snapshot.position > 1) {
            return when (driverNumber) {
                1 -> "Max: 'A tiro de DRS, voy a buscar el hueco en la frenada.' - Ing: 'Despliegue de batería listo Max.'"
                4 -> "Lando: 'A 0.8s del coche de adelante, preparando el ataque.' - Ing: 'Empuja Lando, tiene menos agarre.'"
                16 -> "Charles: 'Muy cerca del coche que me precede, voy al rebufo.' - Ing: 'Modo adelantamiento disponible.'"
                44 -> "Lewis: 'La distancia es de solo 6 décimas, atacando ahora.' - Ing: '¡Adelante Lewis, tienes el ritmo!'"
                14 -> "Fernando: '¡Estoy pegado a su alerón trasero, voy por fuera!' - Ing: '¡Con todo Fernando, espacio limpio!'"
                11 -> "Checo: 'Presionando de cerca con el Cadillac, buscando el sobrepaso.' - Ing: '¡Vamos Checo, ataca en la frenada!'"
                else -> "$acronym: 'Presionando a menos de un segundo del coche de adelante.' - Ing: 'Prepara el sobrepaso con DRS.'"
            }
        }

        // 6. Líder de carrera (P1)
        if (snapshot.position == 1) {
            return when (driverNumber) {
                1 -> "Max: 'Aire limpio, el coche se siente en rieles.' - Ing: 'Liderando la carrera Max, ritmo perfecto (+2.4s).'"
                4 -> "Lando: 'P1 con aire limpio, marcando vueltas consistentes.' - Ing: 'Líder Lando, ventaja sólida.'"
                16 -> "Charles: 'Gestionando la punta de la carrera cómodamente.' - Ing: 'Excelente ritmo en P1 Charles.'"
                else -> "$acronym: 'Liderando en aire limpio con gran ritmo.' - Ing: 'Líder de carrera, gestiona la ventaja.'"
            }
        }

        // 7. Ritmo de crucero estándar / diálogo según piloto
        return getSimulatedRadioQuote(driverNumber, driverName, phase)
    }

    fun tuneRadioDriver(driverNumber: Int, driverName: String, acronym: String? = null) {
        val acr = acronym ?: simulatedDrivers.find { it.driverNumber == driverNumber }?.acronym ?: "CAR$driverNumber"
        activeRadioDriverNumber = driverNumber
        radioButtons.forEach { (num, btn) ->
            btn.isSelected = (num == driverNumber)
        }

        val snapshot = getDriverTelemetrySnapshot(driverNumber)
        val quote = getTelemetryAwareRadioQuote(driverNumber, driverName, acr, snapshot, simulationTick / 140)

        if (isSimulationMode) {
            tvRadioTitle.text = "📻 [Radio #$driverNumber $acr]: \"$quote\""
            radioMessageExpiryTick = simulationTick + 50 // Permanece visible por 10 segundos
            Toast.makeText(context, "📻 [Radio $acr]: Canal sintonizado", Toast.LENGTH_SHORT).show()
            return
        }
        tvRadioTitle.text = "📻 [Radio #$driverNumber $acr]: \"$quote\""
        radioPollingJob?.cancel()
        radioPollingJob = scope.launch {
            var isFirstFetch = true
            while (isActive && activeRadioDriverNumber == driverNumber) {
                try {
                    val key = currentSessionKey
                    var radios = if (key != null) {
                        repository.getDriverTeamRadios(key, driverNumber)
                    } else emptyList()

                    // Si es Verstappen (#1), en 2026 corre con el #3 en OpenF1
                    if (radios.isEmpty() && (driverNumber == 1 || driverNumber == 3) && key != null) {
                        val altNum = if (driverNumber == 1) 3 else 1
                        radios = repository.getDriverTeamRadios(key, altNum)
                    }

                    // Si no hay clips en la sesión actual, buscar en la sesión más reciente con radios
                    if (radios.isEmpty()) {
                        radios = repository.getDriverTeamRadiosLatest(driverNumber)
                        if (radios.isEmpty() && (driverNumber == 1 || driverNumber == 3)) {
                            radios = repository.getDriverTeamRadiosLatest(if (driverNumber == 1) 3 else 1)
                        }
                    }

                    // Si aún está vacío, fallback a radios de la sesión general
                    if (radios.isEmpty() && key != null) {
                        radios = repository.getSessionTeamRadios(key)
                    }

                    val playableRadios = radios.filter { !it.recordingUrl.isNullOrEmpty() }
                    val newestUrl = playableRadios.lastOrNull()?.recordingUrl

                    if (playableRadios.isNotEmpty()) {
                        latestRadioUrl = newestUrl
                        if (newestUrl != null && newestUrl != lastPlayedRadioUrl) {
                            lastPlayedRadioUrl = newestUrl
                            val currentQuote = getTelemetryAwareRadioQuote(driverNumber, driverName, acr, getDriverTelemetrySnapshot(driverNumber), simulationTick / 140)
                            tvRadioTitle.text = "📻 [Radio #$driverNumber $acr]: \"$currentQuote\" • [Audio Oficial]"
                            radioPlayer?.let { player ->
                                player.stop()
                                player.clearMediaItems()
                                for (r in playableRadios) {
                                    r.recordingUrl?.let { url ->
                                        player.addMediaItem(MediaItem.fromUri(url))
                                    }
                                }
                                player.repeatMode = Player.REPEAT_MODE_OFF
                                player.prepare()
                                player.play()
                            }
                            if (isFirstFetch) {
                                Toast.makeText(context, "Sintonizando radios de $driverName (${playableRadios.size} audios)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (isFirstFetch) {
                        tvRadioTitle.text = "📻 [Radio #$driverNumber $acr]: \"$quote\""
                        Toast.makeText(context, "📻 [Radio $acr]: Frecuencia conectada", Toast.LENGTH_SHORT).show()
                    }
                    isFirstFetch = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // Mantener sintonía activa y consultar periódicamente
                delay(15000L)
            }
        }
    }

    fun calculateTrackCurvature(progress: Float, trackPoints: List<PointF>): Float {
        val n = trackPoints.size
        if (n < 3) return 1.0f
        val p = ((progress % n) + n) % n
        val idx = p.toInt() % n
        val prevIdx = (idx - 1 + n) % n
        val nextIdx = (idx + 1) % n
        val p0 = trackPoints[prevIdx]
        val p1 = trackPoints[idx]
        val p2 = trackPoints[nextIdx]
        val v1x = p1.x - p0.x
        val v1y = p1.y - p0.y
        val v2x = p2.x - p1.x
        val v2y = p2.y - p1.y
        val len1 = Math.hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
        val len2 = Math.hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
        if (len1 < 0.001f || len2 < 0.001f) return 1.0f
        val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
        return dot.coerceIn(-1.0f, 1.0f)
    }

    fun calculateLookaheadCurvature(progress: Float, trackPoints: List<PointF>, lookaheadSteps: Int = 3): Float {
        val n = trackPoints.size
        if (n < 3) return 1.0f
        return calculateTrackCurvature(progress + lookaheadSteps, trackPoints)
    }

    private fun updateSimulatedDriverTelemetry(driverNumber: Int) {
        val driver = activeSimDrivers.find { it.driverNumber == driverNumber }
            ?: simulatedDrivers.find { it.driverNumber == driverNumber }?.let { d ->
                SimDriverState(d.driverNumber, d.position, d.acronym, d.fullName, d.teamName, d.teamColor, d.tyreCompound, d.tyreAge, d.bestLap, d.bestLap, 0.0, 0.0)
            } ?: return

        val simMapDriver = trackMapView?.getSimulationDrivers()?.find { it.driverNumber == driverNumber }
        val trackPoints = trackMapView?.getTrackPoints() ?: emptyList()
        val tick = simulationTick

        val spd: Int
        val gear: Int
        val rpm: Int
        val thr: Int
        val brk: Int
        val drsText: String
        val drsColor: Int
        val activeSector: Int

        if (simMapDriver != null && trackPoints.size >= 3) {
            val n = trackPoints.size
            val p = ((simMapDriver.trackProgress % n) + n) % n
            val curDot = calculateTrackCurvature(p, trackPoints)
            val lookDot = calculateLookaheadCurvature(p, trackPoints, 3)

            val s1End = n / 3
            val s2End = (2 * n) / 3
            activeSector = when {
                p < s1End -> 1
                p < s2End -> 2
                else -> 3
            }

            if (driver.isPitting) {
                spd = 80
                gear = 2
                rpm = 5400 + ((tick * 10) % 200)
                thr = 40
                brk = 0
                drsText = "LIMITADOR PIT (80 KM/H)"
                drsColor = Color.parseColor("#F59E0B")
            } else if (curDot >= 0.94f && lookDot >= 0.93f) {
                // Recta completa (Aceleración a fondo + DRS)
                val straightSpeedBonus = ((curDot - 0.94f) / 0.06f * 55f).toInt().coerceIn(0, 55)
                spd = 285 + straightSpeedBonus + ((tick * 3) % 9)
                gear = if (spd > 310) 8 else 7
                rpm = 11400 + ((spd - 280) * 16).coerceIn(0, 1100) + ((tick * 25) % 150)
                thr = 99
                brk = 0
                val isDrsActive = spd > 275
                drsText = if (isDrsActive) "DRS ACTIVO" else "DRS DISPONIBLE"
                drsColor = if (isDrsActive) Color.parseColor("#15803D") else Color.parseColor("#38BDF8")
            } else if (curDot >= 0.93f && lookDot < 0.90f) {
                // Entrada a curva: zona de frenada fuerte
                spd = 195 + ((tick * 4) % 25)
                gear = 5
                rpm = 10400 + ((tick * 30) % 300)
                thr = 0
                brk = 92
                drsText = "FRENADA FUERTE"
                drsColor = Color.parseColor("#EF4444")
            } else {
                // Negociando curva / vértice / chicana
                val cornerSeverity = ((1f - curDot) / 0.3f).coerceIn(0f, 1f)
                spd = (120 + ((1f - cornerSeverity) * 45f)).toInt() + ((tick * 2) % 6)
                gear = if (spd < 135) 2 else 3
                rpm = 9100 + ((spd - 115) * 20).coerceIn(0, 1200)
                thr = (35 + ((1f - cornerSeverity) * 35f)).toInt()
                brk = if (cornerSeverity > 0.6f && tick % 4 == 0) 65 else 0
                drsText = "DRS INACTIVO (CURVA)"
                drsColor = Color.parseColor("#4B5563")
            }
        } else {
            // Emulación sintética de respaldo si no hay circuito cargado
            val inStraight = (tick % 32) in 0..19
            spd = if (inStraight) 305 + ((tick * 3) % 28) else 125 + ((tick * 5) % 35)
            gear = if (inStraight) (7 + ((tick / 2) % 2)) else (2 + ((tick / 2) % 2))
            rpm = if (inStraight) 11500 + ((tick * 80) % 900) else 9200 + ((tick * 60) % 800)
            thr = if (inStraight) 98 else 35
            brk = if (!inStraight && (tick % 32 in 20..24)) 85 else 0
            val isDrsActive = inStraight && (tick % 16 in 0..7)
            drsText = if (isDrsActive) "DRS ACTIVO" else "DRS INACTIVO"
            drsColor = if (isDrsActive) Color.parseColor("#15803D") else Color.parseColor("#4B5563")
            activeSector = 1 + ((tick / 40) % 3)
        }

        tvTelemetryDriverHeader.text = "#${driver.driverNumber} ${driver.fullName} • ${driver.teamName}"
        tvTelemetrySpeed.text = spd.toString()
        tvTelemetryGear.text = gear.toString()
        tvTelemetryRpm.text = String.format(Locale.US, "%,d RPM", rpm)
        pbTelemetryRpm.progress = rpm
        tvTelemetryThrottle.text = "Acelerador: $thr%"
        pbTelemetryThrottle.progress = thr
        tvTelemetryBrake.text = "Freno: $brk%"
        pbTelemetryBrake.progress = brk

        tvTelemetryDrs.text = drsText
        tvTelemetryDrs.setBackgroundColor(drsColor)

        // Resaltado dinámico del sector activo en pista
        val s1Text = if (activeSector == 1) "⚡ ${driver.s1} (ACTIVO)" else driver.s1
        val s2Text = if (activeSector == 2) "⚡ ${driver.s2} (ACTIVO)" else driver.s2
        val s3Text = if (activeSector == 3) "⚡ ${driver.s3} (ACTIVO)" else driver.s3

        tvTelemetryS1.text = s1Text
        tvTelemetryS1.setTextColor(if (activeSector == 1) Color.parseColor("#38BDF8") else Color.WHITE)
        tvTelemetryS1Speed.text = "Trampa S1: ${if (activeSector == 1) "$spd km/h" else "315 km/h"}"

        tvTelemetryS2.text = s2Text
        tvTelemetryS2.setTextColor(if (activeSector == 2) Color.parseColor("#A855F7") else Color.WHITE)
        tvTelemetryS2Speed.text = "Trampa S2: ${if (activeSector == 2) "$spd km/h" else "292 km/h"}"

        tvTelemetryS3.text = s3Text
        tvTelemetryS3.setTextColor(if (activeSector == 3) Color.parseColor("#F59E0B") else Color.WHITE)
        tvTelemetryS3Speed.text = "Trampa S3: ${if (activeSector == 3) "$spd km/h" else "334 km/h"}"

        tvTelemetryLapTime.text = "⏱️ VUELTA $currentRaceLap / $totalRaceLaps • MEJOR: ${driver.bestLap} • ÚLTIMA: ${driver.lastLapTime}"

        // Si la radio está sintonizada en este piloto y el mensaje sigue en pantalla durante su ventana, mantener el subtítulo acorde
        if (activeRadioDriverNumber == driverNumber && simulationTick <= radioMessageExpiryTick) {
            val snapshot = DriverTelemetrySnapshot(
                speed = spd,
                brake = brk,
                throttle = thr,
                drsActive = drsText.contains("ACTIVO"),
                isPitting = driver.isPitting,
                tyreCompound = driver.tyreCompound,
                tyreAge = driver.tyreAge,
                position = driver.position,
                gapAhead = driver.intervalToAhead,
                sector = activeSector
            )
            val quote = getTelemetryAwareRadioQuote(driver.driverNumber, driver.fullName, driver.acronym, snapshot, simulationTick / 140)
            tvRadioTitle.text = "📻 [Radio #${driver.driverNumber} ${driver.acronym}]: \"$quote\""
        }
    }

    fun showTelemetryUnavailableUI() {
        val dateInfo = nextSessionFormattedDate ?: currentScheduleTitle
        tvTelemetryDriverHeader.text = "Telemetría no disponible • Sin sesión en vivo activa"
        tvTelemetrySpeed.text = "--"
        tvTelemetryGear.text = "-"
        tvTelemetryRpm.text = "Próxima cita: $dateInfo"
        pbTelemetryRpm.progress = 0
        tvTelemetryThrottle.text = "Acelerador: --"
        pbTelemetryThrottle.progress = 0
        tvTelemetryBrake.text = "Freno: --"
        pbTelemetryBrake.progress = 0
        tvTelemetryDrs.text = "DRS --"
        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#374151"))
        tvTelemetryS1.text = "--.---"
        tvTelemetryS1Speed.text = "Trampa: --- km/h"
        tvTelemetryS2.text = "--.---"
        tvTelemetryS2Speed.text = "Trampa: --- km/h"
        tvTelemetryS3.text = "--.---"
        tvTelemetryS3Speed.text = "Trampa: --- km/h"
        tvTelemetryLapTime.text = "⏱️ Próxima sesión: $dateInfo"
    }

    fun loadDriverTelemetry(driverNumber: Int) {
        if (isSimulationMode) {
            updateSimulatedDriverTelemetry(driverNumber)
            return
        }
        if (!isSessionEventActive()) {
            showTelemetryUnavailableUI()
            return
        }
        scope.launch {
            try {
                val key = currentSessionKey
                if (key == null) {
                    Toast.makeText(context, "No hay una sesión F1 seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val targetDriver = driverNumber
                val telemetry = repository.getTelemetryForDriver(key, targetDriver)
                    ?: if (targetDriver == 1) repository.getTelemetryForDriver(key, 3) else null

                val lap = repository.getLatestLapForDriver(key, targetDriver)
                    ?: if (targetDriver == 1) repository.getLatestLapForDriver(key, 3) else null

                if (lap != null) {
                    tvTelemetryS1.text = lap.s1?.let { String.format(Locale.US, "%.3f s", it) } ?: "--.---"
                    tvTelemetryS1Speed.text = lap.i1Speed?.let { "Trampa: $it km/h" } ?: "Trampa: --- km/h"

                    tvTelemetryS2.text = lap.s2?.let { String.format(Locale.US, "%.3f s", it) } ?: "--.---"
                    tvTelemetryS2Speed.text = lap.i2Speed?.let { "Trampa: $it km/h" } ?: "Trampa: --- km/h"

                    tvTelemetryS3.text = lap.s3?.let { String.format(Locale.US, "%.3f s", it) } ?: "--.---"
                    tvTelemetryS3Speed.text = lap.stSpeed?.let { "Trampa: $it km/h" } ?: "Trampa: --- km/h"

                    val lapDurationStr = TimeUtils.formatLapDuration(lap.lapDuration)
                    tvTelemetryLapTime.text = "⏱️ TIEMPO VUELTA: $lapDurationStr"
                } else {
                    tvTelemetryS1.text = "--.---"
                    tvTelemetryS1Speed.text = "Trampa: --- km/h"
                    tvTelemetryS2.text = "--.---"
                    tvTelemetryS2Speed.text = "Trampa: --- km/h"
                    tvTelemetryS3.text = "--.---"
                    tvTelemetryS3Speed.text = "Trampa: --- km/h"
                    tvTelemetryLapTime.text = "⏱️ TIEMPO VUELTA: --:--.---"
                }

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
                    tvTelemetrySpeed.text = lap?.stSpeed?.toString() ?: "--"
                    tvTelemetryGear.text = "--"
                    tvTelemetryRpm.text = if (lap != null) "Sectores y trampas registrados" else "Sin datos disponibles"
                    pbTelemetryRpm.progress = 0
                    tvTelemetryThrottle.text = "Acelerador: --"
                    pbTelemetryThrottle.progress = 0
                    tvTelemetryBrake.text = "Freno: --"
                    pbTelemetryBrake.progress = 0
                    tvTelemetryDrs.text = "DRS --"
                    tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    if (lap == null) {
                        val message = if (isUpcomingSession) {
                            "La telemetría estará disponible cuando comience $currentScheduleTitle"
                        } else {
                            "OpenF1 todavía no ha publicado telemetría para esta sesión"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun focusActiveTelemetry() {
        if (isDriverTelemetryOpen) {
            btnBackToTiming?.requestFocus()
            return
        }
        when (activeTelemetryMode) {
            TelemetryMode.TIMING -> {
                if (rvLeaderboard.isShown && rvLeaderboard.childCount > 0) {
                    rvLeaderboard.requestFocus()
                } else {
                    btnMetricsTiming.requestFocus()
                }
            }
            TelemetryMode.TRACK_FIA -> {
                btnMetricsTrackMap?.requestFocus()
            }
            TelemetryMode.TYRES -> {
                if (rvTyreStrategy?.isShown == true && rvTyreStrategy.childCount > 0) {
                    rvTyreStrategy.requestFocus()
                } else {
                    btnMetricsTyres?.requestFocus()
                }
            }
            TelemetryMode.SOLO_TRACK -> {
                btnMetricsSoloTrack?.requestFocus()
            }
            TelemetryMode.RACE_CONTROL -> {
                if (rvRaceControl?.isShown == true && rvRaceControl.childCount > 0) {
                    rvRaceControl.requestFocus()
                } else {
                    btnMetricsRaceControl?.requestFocus()
                }
            }
        }
    }

    private fun toggleVideoPlayPause() {
        try {
            if (playerView.visibility == View.VISIBLE && exoPlayer != null) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        Toast.makeText(context, "⏸️ Video en pausa", Toast.LENGTH_SHORT).show()
                    } else {
                        player.play()
                        Toast.makeText(context, "▶️ Reanudando video", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (webVideoView.visibility == View.VISIBLE) {
                triggerPlayerTap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateRecentFiaMessages(messages: List<RaceControl>) {
        val msg1 = messages.getOrNull(0)
        val msg2 = messages.getOrNull(1)

        if (msg1 != null) {
            val flagUpper = msg1.flag?.uppercase(Locale.ROOT) ?: ""
            val categoryUpper = msg1.category?.uppercase(Locale.ROOT) ?: "CONTROL"
            val (flagBg, flagLabel) = when {
                flagUpper.contains("RED") -> "#EF4444" to "🔴 BANDERA ROJA"
                flagUpper.contains("YELLOW") -> "#EAB308" to "🟡 BANDERA AMARILLA"
                flagUpper.contains("SAFETY") || (msg1.message?.uppercase(Locale.ROOT)?.contains("SAFETY CAR") == true) -> "#F97316" to "⚠️ SAFETY CAR"
                flagUpper.contains("VIRTUAL") || (msg1.message?.uppercase(Locale.ROOT)?.contains("VSC") == true) -> "#F59E0B" to "⏱️ VSC"
                flagUpper.contains("CHEQUERED") || flagUpper.contains("CHECKERED") -> "#374151" to "🏁 BANDERA A CUADROS"
                flagUpper.contains("GREEN") || flagUpper.contains("CLEAR") -> "#15803D" to "🟢 PISTA LIBRE"
                else -> "#374151" to "🏁 $categoryUpper"
            }
            tvFiaMsg1Flag?.text = flagLabel
            tvFiaMsg1Flag?.setBackgroundColor(Color.parseColor(flagBg))
            val timeOrLap = when {
                msg1.lapNumber != null -> "VUELTA ${msg1.lapNumber}"
                !msg1.date.isNullOrBlank() -> msg1.date.takeLast(8)
                else -> "RECIENTE"
            }
            tvFiaMsg1Time?.text = timeOrLap
            tvFiaMsg1Text?.text = msg1.message ?: "Sin observaciones adicionales"
        } else {
            tvFiaMsg1Flag?.text = "🏁 FIA"
            tvFiaMsg1Flag?.setBackgroundColor(Color.parseColor("#374151"))
            tvFiaMsg1Time?.text = "EN ESPERA"
            tvFiaMsg1Text?.text = "No hay mensajes de Dirección de Carrera aún"
        }

        if (msg2 != null) {
            val flagUpper = msg2.flag?.uppercase(Locale.ROOT) ?: ""
            val categoryUpper = msg2.category?.uppercase(Locale.ROOT) ?: "CONTROL"
            val (flagBg, flagLabel) = when {
                flagUpper.contains("RED") -> "#EF4444" to "🔴 BANDERA ROJA"
                flagUpper.contains("YELLOW") -> "#EAB308" to "🟡 BANDERA AMARILLA"
                flagUpper.contains("SAFETY") || (msg2.message?.uppercase(Locale.ROOT)?.contains("SAFETY CAR") == true) -> "#F97316" to "⚠️ SAFETY CAR"
                flagUpper.contains("VIRTUAL") || (msg2.message?.uppercase(Locale.ROOT)?.contains("VSC") == true) -> "#F59E0B" to "⏱️ VSC"
                flagUpper.contains("CHEQUERED") || flagUpper.contains("CHECKERED") -> "#374151" to "🏁 BANDERA A CUADROS"
                flagUpper.contains("GREEN") || flagUpper.contains("CLEAR") -> "#15803D" to "🟢 PISTA LIBRE"
                else -> "#374151" to "🏁 $categoryUpper"
            }
            tvFiaMsg2Flag?.text = flagLabel
            tvFiaMsg2Flag?.setBackgroundColor(Color.parseColor(flagBg))
            val timeOrLap = when {
                msg2.lapNumber != null -> "VUELTA ${msg2.lapNumber}"
                !msg2.date.isNullOrBlank() -> msg2.date.takeLast(8)
                else -> "ANTERIOR"
            }
            tvFiaMsg2Time?.text = timeOrLap
            tvFiaMsg2Text?.text = msg2.message ?: "Sin observaciones"
        } else {
            tvFiaMsg2Flag?.text = "🏁 FIA"
            tvFiaMsg2Flag?.setBackgroundColor(Color.parseColor("#374151"))
            tvFiaMsg2Time?.text = "--:--"
            tvFiaMsg2Text?.text = "Esperando avisos de Dirección de Carrera..."
        }
    }

    fun switchTelemetryMode(mode: TelemetryMode) {
        if (isDriverTelemetryOpen) {
            closeDriverTelemetry()
        }
        activeTelemetryMode = mode

        btnMetricsTiming.isSelected = (mode == TelemetryMode.TIMING)
        btnMetricsTrackMap?.isSelected = (mode == TelemetryMode.TRACK_FIA)
        btnMetricsTyres?.isSelected = (mode == TelemetryMode.TYRES)
        btnMetricsSoloTrack?.isSelected = (mode == TelemetryMode.SOLO_TRACK)
        btnMetricsRaceControl?.isSelected = (mode == TelemetryMode.RACE_CONTROL)

        layoutTelemetryPanel.visibility = View.GONE
        layoutTimingLeaderboardPanel?.visibility = if (mode == TelemetryMode.TIMING) View.VISIBLE else View.GONE
        layoutTyresPanel?.visibility = if (mode == TelemetryMode.TYRES) View.VISIBLE else View.GONE
        layoutRaceControlPanel?.visibility = if (mode == TelemetryMode.RACE_CONTROL) View.VISIBLE else View.GONE

        when (mode) {
            TelemetryMode.TIMING -> {
                layoutPistaFiaPanel?.visibility = View.GONE
                stopTrackMapPolling()
            }
            TelemetryMode.TRACK_FIA -> {
                layoutPistaFiaPanel?.visibility = View.VISIBLE
                layoutTrackMapContainer?.visibility = View.VISIBLE
                (layoutTrackMapContainer?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = 0.70f
                    layoutTrackMapContainer.layoutParams = lp
                }
                layoutFiaMessagesContainer?.visibility = View.VISIBLE
                (layoutFiaMessagesContainer?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = 0.30f
                    layoutFiaMessagesContainer.layoutParams = lp
                }
                layoutPistaFiaPanel?.requestLayout()
                startTrackMapPolling()
            }
            TelemetryMode.SOLO_TRACK -> {
                layoutPistaFiaPanel?.visibility = View.VISIBLE
                layoutTrackMapContainer?.visibility = View.VISIBLE
                (layoutTrackMapContainer?.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = 1.0f
                    layoutTrackMapContainer.layoutParams = lp
                }
                layoutFiaMessagesContainer?.visibility = View.GONE
                layoutPistaFiaPanel?.requestLayout()
                startTrackMapPolling()
            }
            TelemetryMode.TYRES -> {
                layoutPistaFiaPanel?.visibility = View.GONE
                stopTrackMapPolling()
            }
            TelemetryMode.RACE_CONTROL -> {
                layoutPistaFiaPanel?.visibility = View.GONE
                stopTrackMapPolling()
            }
        }

        // Keep active button visible in horizontal scroll view
        val activeBtn = when (mode) {
            TelemetryMode.TIMING -> btnMetricsTiming
            TelemetryMode.TRACK_FIA -> btnMetricsTrackMap
            TelemetryMode.TYRES -> btnMetricsTyres
            TelemetryMode.SOLO_TRACK -> btnMetricsSoloTrack
            TelemetryMode.RACE_CONTROL -> btnMetricsRaceControl
        }
        activeBtn?.let { b ->
            scrollTelemetryModes?.smoothScrollTo((b.left - 40).coerceAtLeast(0), 0)
        }

        updatePitWallFocusChains()
        refreshActiveTelemetryMode()
    }

    fun cycleTelemetryMode(forward: Boolean) {
        if (isDriverTelemetryOpen) {
            closeDriverTelemetry()
        }
        val modes = TelemetryMode.values()
        val currentIndex = modes.indexOf(activeTelemetryMode)
        val nextIndex = if (forward) {
            (currentIndex + 1) % modes.size
        } else {
            if (currentIndex - 1 < 0) modes.size - 1 else currentIndex - 1
        }
        val targetMode = modes[nextIndex]
        switchTelemetryMode(targetMode)
        showTelemetryModeToast(targetMode)
    }

    private fun showTelemetryModeToast(mode: TelemetryMode) {
        telemetryToast?.cancel()
        telemetryToast = Toast.makeText(context, "Telemetría: ${mode.icon}", Toast.LENGTH_SHORT)
        telemetryToast?.show()
    }

    private fun startTrackMapPolling() {
        trackMapPollingJob?.cancel()
        trackMapPollingJob = scope.launch {
            while (isActive && (activeTelemetryMode == TelemetryMode.TRACK_FIA || activeTelemetryMode == TelemetryMode.SOLO_TRACK)) {
                loadTrackMapData()
                if (!isSessionEventActive()) {
                    delay(30_000L) // Chequeo lento si no hay evento activo
                } else {
                    delay(5000L) // Polling cada 5 segundos durante sesión activa para respetar rate-limits
                }
            }
        }
    }

    private fun stopTrackMapPolling() {
        trackMapPollingJob?.cancel()
        trackMapPollingJob = null
    }

    fun refreshActiveTelemetryMode() {
        when (activeTelemetryMode) {
            TelemetryMode.TIMING -> {
                if (isDriverTelemetryOpen) {
                    loadDriverTelemetry(selectedDriverNumber)
                }
            }
            TelemetryMode.TRACK_FIA -> {
                loadTrackMapData()
                loadRaceControlAndWeatherData()
            }
            TelemetryMode.SOLO_TRACK -> {
                loadTrackMapData()
            }
            TelemetryMode.TYRES -> {
                loadTyreStrategyData()
            }
            TelemetryMode.RACE_CONTROL -> {
                loadRaceControlAndWeatherData()
            }
        }
    }

    fun isSessionEventActive(): Boolean {
        // En modo emulación / demostración siempre se considera la sesión activa
        if (isSimulationMode) return true

        // 1. Debe estar confirmada una sesión en vivo por el calendario oficial
        if (!isLiveSessionActive || isUpcomingSession) {
            return false
        }

        // 2. Debe ser una sesión oficial reconocida (Prácticas, Clasificación, Sprint o Carrera)
        val isRecognized = isSessionKeyword(currentSessionType) || isSessionKeyword(currentScheduleTitle)
        if (!isRecognized) {
            return false
        }

        // 3. Debe contar con una sesión válida
        return currentSessionKey != null
    }

    private fun initSimulationDrivers() {
        activeSimDrivers.clear()
        currentRaceLap = 34
        nextOvertakeTick = 120
        nextPitStopTick = 180
        val baseDrivers = simulatedDrivers
        baseDrivers.forEachIndexed { idx, d ->
            val pos = idx + 1
            val baseGap = when (pos) {
                1 -> 0.0
                2 -> 1.420
                3 -> 3.250
                4 -> 5.110
                5 -> 7.840
                6 -> 9.920
                7 -> 14.150
                8 -> 17.800
                9 -> 22.300
                10 -> 25.400
                11 -> 29.100
                12 -> 32.800
                13 -> 36.400
                14 -> 40.100
                15 -> 43.700
                16 -> 47.900
                17 -> 52.300
                18 -> 56.800
                19 -> 61.500
                20 -> 66.900
                21 -> 72.400
                22 -> 77.900
                else -> 80.0 + (pos * 2.0)
            }
            val interval = if (pos == 1) 0.0 else when (pos) {
                2 -> 1.420
                else -> (baseGap - (when (pos - 1) {
                    2 -> 1.420
                    3 -> 3.250
                    4 -> 5.110
                    5 -> 7.840
                    6 -> 9.920
                    7 -> 14.150
                    8 -> 17.800
                    9 -> 22.300
                    10 -> 25.400
                    11 -> 29.100
                    12 -> 32.800
                    13 -> 36.400
                    14 -> 40.100
                    15 -> 43.700
                    16 -> 47.900
                    17 -> 52.300
                    18 -> 56.800
                    19 -> 61.500
                    20 -> 66.900
                    21 -> 72.400
                    else -> 75.0
                })).coerceAtLeast(0.180)
            }

            val totalStints = if (d.tyreAge > 18) 2 else 1
            val pitStops = totalStints - 1
            val lastPit = if (pitStops > 0) "2.4 s" else "--"
            val history = if (totalStints > 1) "M (1-18) ➔ ${d.tyreCompound.first()} (19-Act)" else "${d.tyreCompound.first()} (1-Act)"

            val s1 = String.format(Locale.US, "%.3f", 27.200 + (pos * 0.07))
            val s2 = String.format(Locale.US, "%.3f", 31.800 + (pos * 0.11))
            val s3 = String.format(Locale.US, "%.3f", 26.100 + (pos * 0.08))

            activeSimDrivers.add(
                SimDriverState(
                    driverNumber = d.driverNumber,
                    position = pos,
                    acronym = d.acronym,
                    fullName = d.fullName,
                    teamName = d.teamName,
                    teamColor = d.teamColor,
                    tyreCompound = d.tyreCompound,
                    tyreAge = d.tyreAge,
                    bestLap = d.bestLap,
                    lastLapTime = d.bestLap,
                    gapToLeader = baseGap,
                    intervalToAhead = interval,
                    pitStopsCount = pitStops,
                    isPitting = false,
                    pitTicksRemaining = 0,
                    lastPitDuration = lastPit,
                    stintsHistory = history,
                    s1 = s1,
                    s2 = s2,
                    s3 = s3
                )
            )
        }

        simRaceControlLog.clear()
        simRaceControlLog.addAll(
            listOf(
                RaceControl("2026-11-20T19:40:00Z", "Flag", "DRS HABILITADO EN TODAS LAS ZONAS", "GREEN", "Track", null, null, currentRaceLap),
                RaceControl("2026-11-20T19:38:15Z", "Track", "VENTANA DE PARADAS EN BOXES ABIERTA (VUELTAS 25 - 40)", "CLEAR", "Track", null, null, currentRaceLap - 2),
                RaceControl("2026-11-20T19:35:00Z", "Sector", "DIRECCIÓN DE CARRERA: Pista libre y seca en todos los sectores", "CLEAR", "Sector", 2, null, currentRaceLap - 4),
                RaceControl("2026-11-20T19:30:20Z", "Car", "MONITOREO DE LÍMITES DE PISTA ACTIVO", "INFO", "Track", 3, null, currentRaceLap - 6)
            )
        )
    }

    private fun addSimulatedRaceControlEvent(category: String, message: String, flag: String) {
        val timeSec = (simulationTick * 0.2).toInt()
        val mm = String.format(Locale.US, "%02d", (timeSec / 60) % 60)
        val ss = String.format(Locale.US, "%02d", timeSec % 60)
        val dateIso = "2026-11-20T19:$mm:${ss}Z"
        val newEvent = RaceControl(
            date = dateIso,
            category = category,
            message = message,
            flag = flag,
            scope = "Track",
            sector = null,
            driverNumber = null,
            lapNumber = currentRaceLap
        )
        simRaceControlLog.add(0, newEvent)
        if (simRaceControlLog.size > 25) {
            simRaceControlLog.removeAt(simRaceControlLog.size - 1)
        }
        raceControlAdapter.updateData(simRaceControlLog.take(8))
    }

    private fun triggerSimulatedOvertake() {
        if (activeSimDrivers.size < 4) return
        val candidates = (1 until (activeSimDrivers.size - 2)).filter { i ->
            !activeSimDrivers[i].isPitting && !activeSimDrivers[i - 1].isPitting && activeSimDrivers[i].intervalToAhead <= 1.6
        }
        val battleIdx = if (candidates.isNotEmpty()) {
            candidates.random()
        } else {
            (1..5).random()
        }

        val driverAhead = activeSimDrivers[battleIdx - 1]
        val driverBehind = activeSimDrivers[battleIdx]

        // Swap positions
        val oldPosAhead = driverAhead.position
        val oldPosBehind = driverBehind.position
        driverAhead.position = oldPosBehind
        driverBehind.position = oldPosAhead

        // Adjust gaps & intervals
        val tempGap = driverBehind.gapToLeader
        driverBehind.gapToLeader = (driverAhead.gapToLeader - 0.160).coerceAtLeast(0.0)
        driverAhead.gapToLeader = tempGap + 0.190
        driverBehind.intervalToAhead = 0.320
        driverAhead.intervalToAhead = 0.380

        activeSimDrivers[battleIdx - 1] = driverBehind
        activeSimDrivers[battleIdx] = driverAhead

        // Log race control alert
        val msg = "ADELANTAMIENTO: #${driverBehind.driverNumber} ${driverBehind.acronym} supera a #${driverAhead.driverNumber} ${driverAhead.acronym} por la P${driverBehind.position}"
        addSimulatedRaceControlEvent("Car", msg, "GREEN")
    }

    private fun handleSimulatedPitStops() {
        // 1. Process active pit stops
        val pittingDrivers = activeSimDrivers.filter { it.isPitting }
        for (d in pittingDrivers) {
            d.pitTicksRemaining--
            if (d.pitTicksRemaining <= 0) {
                d.isPitting = false
                d.pitStopsCount++
                val stopSec = 2.2 + ((simulationTick % 6) * 0.1)
                d.lastPitDuration = String.format(Locale.US, "%.1f s", stopSec)
                val newComp = if (d.tyreCompound == "HARD") "MEDIUM" else if (d.tyreCompound == "MEDIUM") "HARD" else "HARD"
                d.stintsHistory = if (d.stintsHistory.isBlank()) "${d.tyreCompound.first()} (1-$currentRaceLap) ➔ ${newComp.first()} ($currentRaceLap-Act)" else "${d.stintsHistory} ➔ ${newComp.first()} ($currentRaceLap-Act)"
                d.tyreCompound = newComp
                d.tyreAge = 0

                // Drop positions due to pit transit
                val oldIdx = activeSimDrivers.indexOf(d)
                val newIdx = (oldIdx + 3).coerceAtMost(activeSimDrivers.size - 1)
                if (newIdx != oldIdx) {
                    activeSimDrivers.removeAt(oldIdx)
                    activeSimDrivers.add(newIdx, d)
                    activeSimDrivers.forEachIndexed { idx, item ->
                        item.position = idx + 1
                        if (idx > 0) {
                            item.gapToLeader = activeSimDrivers[idx - 1].gapToLeader + (1.200 + ((idx % 4) * 0.150))
                            item.intervalToAhead = (item.gapToLeader - activeSimDrivers[idx - 1].gapToLeader).coerceAtLeast(0.180)
                        }
                    }
                }
                addSimulatedRaceControlEvent("Car", "PARADA EN BOXES: #${d.driverNumber} ${d.acronym} sale de pit lane (${d.lastPitDuration}). Neumáticos $newComp", "CLEAR")
            }
        }

        // 2. Schedule next pit stop periodically
        val isAnyonePitting = activeSimDrivers.any { it.isPitting }
        if (!isAnyonePitting && simulationTick >= nextPitStopTick) {
            nextPitStopTick = simulationTick + 190 + (simulationTick % 60)
            val candidate = activeSimDrivers.take(16).find { !it.isPitting && it.tyreAge >= 16 && it.pitStopsCount < 2 }
            if (candidate != null) {
                candidate.isPitting = true
                candidate.pitTicksRemaining = 25 // 5 seconds in pits
                addSimulatedRaceControlEvent("Car", "PARADA EN BOXES: #${candidate.driverNumber} ${candidate.acronym} entra a boxes", "INFO")
            }
        }
    }

    private fun handleSimulatedLapProgression(circuitName: String) {
        if (simulationTick > 0 && simulationTick % 350 == 0) {
            if (currentRaceLap < totalRaceLaps) {
                currentRaceLap++
                activeSimDrivers.forEach { d ->
                    if (!d.isPitting) {
                        d.tyreAge++
                    }
                }
                val leader = activeSimDrivers.firstOrNull()
                addSimulatedRaceControlEvent(
                    "Track",
                    "INICIO DE VUELTA $currentRaceLap / $totalRaceLaps • LÍDER: #${leader?.driverNumber ?: 1} ${leader?.acronym ?: "VER"}",
                    "GREEN"
                )
            }
        }
        val lapProgressTitle = "$circuitName • VUELTA $currentRaceLap / $totalRaceLaps • EN VIVO"
        currentScheduleTitle = lapProgressTitle
        tvSessionTitle.text = "🏎️ $lapProgressTitle"
        tvStandbyTitle.text = lapProgressTitle.uppercase()
        tvStandbyStatusBadge.text = "🔴 EMULACIÓN EN VIVO • VUELTA $currentRaceLap / $totalRaceLaps (22 MONOPLAZAS)"
        tvRaceFlag?.text = "🟢 BANDERA VERDE • VUELTA $currentRaceLap / $totalRaceLaps"
        tvRaceFlag?.setTextColor(Color.parseColor("#22C55E"))
    }

    fun setSimulationMode(enabled: Boolean) {
        isSimulationMode = enabled
        if (enabled) {
            isLiveSessionActive = true
            isUpcomingSession = false
            currentSessionType = "Race"

            initSimulationDrivers()

            val (circuitName, circuitSpecs) = getCircuitDetails(currentScheduleTitle)
            tvTrackMapCircuitName?.text = circuitName
            trackMapView?.circuitName = circuitName
            trackMapView?.isSimulationMode = true
            trackMapView?.loadCircuit(circuitName)

            val trackPoints = trackMapView?.getTrackPoints() ?: emptyList()
            val n = trackPoints.size.coerceAtLeast(1)
            val simDriversForMap = activeSimDrivers.mapIndexed { idx, d ->
                val progress = ((n.toFloat() - (idx * (n.toFloat() / activeSimDrivers.size.toFloat()))) % n.toFloat() + n.toFloat()) % n.toFloat()
                TrackMapView.SimTrackDriver(
                    driverNumber = d.driverNumber,
                    acronym = d.acronym,
                    colorHex = d.teamColor,
                    trackProgress = progress
                )
            }
            trackMapView?.setSimulationDrivers(simDriversForMap)

            val lapProgressTitle = "$circuitName • VUELTA $currentRaceLap / $totalRaceLaps • EN VIVO"
            currentScheduleTitle = lapProgressTitle
            tvSessionTitle.text = "🏎️ $lapProgressTitle"
            tvStandbyTitle.text = lapProgressTitle.uppercase()
            tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $lapProgressTitle"
            tvStandbyStatusBadge.text = "🔴 EMULACIÓN EN VIVO • VUELTA $currentRaceLap / $totalRaceLaps (22 MONOPLAZAS)"
            tvRaceFlag?.text = "🟢 BANDERA VERDE • VUELTA $currentRaceLap / $totalRaceLaps"
            tvRaceFlag?.setTextColor(Color.parseColor("#22C55E"))

            // Preservar sintonía del piloto si ya estaba seleccionado
            val currentSelected = activeRadioDriverNumber
            if (currentSelected != null) {
                val d = simulatedDrivers.find { it.driverNumber == currentSelected }
                if (d != null) {
                    tuneRadioDriver(d.driverNumber, d.fullName, d.acronym)
                }
            } else {
                tvRadioTitle.text = "📻 Radio Oficial: Canal en espera (Selecciona piloto)"
            }

            startSimulationEngine()
        } else {
            stopSimulationEngine()
            val currentSelected = activeRadioDriverNumber
            if (currentSelected != null) {
                val d = simulatedDrivers.find { it.driverNumber == currentSelected }
                if (d != null) {
                    tuneRadioDriver(d.driverNumber, d.fullName, d.acronym)
                }
            } else {
                turnOffRadio()
                val nextInfo = nextSessionFormattedDate?.let { " • Próxima cita: $it" } ?: ""
                tvRadioTitle.text = "📻 Radio Oficial: Sin transmisión en vivo$nextInfo"
            }
            isLiveSessionActive = false
            activeSimDrivers.clear()
            simRaceControlLog.clear()
            tvStandbyStatusBadge.text = "F1 PIT WALL TV • OFICIAL"
            trackMapView?.isSimulationMode = false
            trackMapView?.setDriverPositions(emptyList())
            lastKnownMarkers = emptyList()
            val nextInfo = nextSessionFormattedDate?.let { " • Próxima cita: $it" } ?: ""
            tvRadioTitle.text = "📻 Radio Oficial: Sin transmisión en vivo$nextInfo"
            refreshActiveTelemetryMode()
        }
    }

    private fun startSimulationEngine() {
        stopSimulationEngine()
        simulationEngineJob = scope.launch {
            while (isActive && isSimulationMode) {
                simulationTick++
                runSimulationTick()
                delay(200L)
            }
        }
    }

    private fun stopSimulationEngine() {
        simulationEngineJob?.cancel()
        simulationEngineJob = null
    }

    private fun updateSimulatedLeaderboard() {
        val leaderboardItems = activeSimDrivers.map { d ->
            val pos = d.position
            val gapStr = when {
                d.isPitting -> "PIT"
                pos == 1 -> "LÍDER"
                else -> "+${String.format(Locale.US, "%.3f", d.gapToLeader)}"
            }
            val intervalStr = when {
                d.isPitting -> "PIT"
                pos == 1 -> "INTERVAL"
                else -> "+${String.format(Locale.US, "%.3f", d.intervalToAhead)}"
            }

            DriverLeaderboardItem(
                driverNumber = d.driverNumber,
                position = pos,
                acronym = d.acronym,
                fullName = d.fullName,
                teamName = d.teamName,
                teamColor = d.teamColor,
                gapToLeader = gapStr,
                interval = intervalStr,
                lastLap = d.lastLapTime,
                s1 = d.s1,
                s2 = d.s2,
                s3 = d.s3,
                tyreCompound = d.tyreCompound,
                tyreAge = d.tyreAge
            )
        }
        leaderboardAdapter.updateData(leaderboardItems)
    }

    private fun runSimulationTick() {
        val (circuitName, _) = getCircuitDetails(currentScheduleTitle)
        val tick = simulationTick
        val isSlowTick = (tick % 5 == 0) // Once every 1000ms

        // 1. Lap progression & Titles (updates lap counter in real time)
        handleSimulatedLapProgression(circuitName)

        // 2. Dynamic Battles & Pit Stops (calculated once per second)
        if (isSlowTick || tick == 1) {
            // Dynamic Overtakes every ~25-30 seconds
            if (tick >= nextOvertakeTick) {
                nextOvertakeTick = tick + 140 + (tick % 40)
                triggerSimulatedOvertake()
            }

            // Dynamic Pit Stops
            handleSimulatedPitStops()

            // Micro-variations in gaps for broadcast realism
            for (i in 1 until activeSimDrivers.size) {
                val d = activeSimDrivers[i]
                if (!d.isPitting) {
                    val drift = kotlin.math.sin((tick * 0.1) + i) * 0.02
                    d.intervalToAhead = (d.intervalToAhead + drift).coerceIn(0.180, 7.500)
                    d.gapToLeader = activeSimDrivers[i - 1].gapToLeader + d.intervalToAhead
                }
            }

            // Update Leaderboard & Timing Tower
            updateSimulatedLeaderboard()
        }

        // 3. GPS Track Map (hardware-accelerated continuous spline animation)
        if (activeTelemetryMode == TelemetryMode.TRACK_FIA || activeTelemetryMode == TelemetryMode.SOLO_TRACK) {
            layoutTrackMapCircuitImageContainer?.visibility = View.GONE
            trackMapView?.visibility = View.VISIBLE
            tvTrackMapLegend?.visibility = View.VISIBLE
            tvTrackMapGpsStatus?.text = "🟢 GPS EN VIVO (EMULACIÓN 30 FPS)"
            tvTrackMapGpsStatus?.setBackgroundColor(Color.parseColor("#15803D"))

            trackMapView?.let { map ->
                map.leaderDriverNumber = activeSimDrivers.firstOrNull()?.driverNumber ?: 1
                map.selectedRadioDriverNumber = activeRadioDriverNumber
                if (map.getSimulationDrivers().isEmpty()) {
                    val trackPoints = map.getTrackPoints()
                    if (trackPoints.isNotEmpty()) {
                        val n = trackPoints.size
                        val simDriversForMap = activeSimDrivers.mapIndexed { idx, d ->
                            val progress = ((n.toFloat() - (idx * (n.toFloat() / activeSimDrivers.size.toFloat()))) % n.toFloat() + n.toFloat()) % n.toFloat()
                            TrackMapView.SimTrackDriver(
                                driverNumber = d.driverNumber,
                                acronym = d.acronym,
                                colorHex = d.teamColor,
                                trackProgress = progress
                            )
                        }
                        map.setSimulationDrivers(simDriversForMap)
                    } else {
                        map.loadCircuit(circuitName)
                    }
                }
                map.selectedDriverNumber = if (isDriverTelemetryOpen) selectedDriverNumber else null
            }
            tvTrackMapFollowedDriver?.text = if (isDriverTelemetryOpen && selectedDriverItem != null) {
                "Piloto seguido: #${selectedDriverItem!!.driverNumber} ${selectedDriverItem!!.acronym} (${selectedDriverItem!!.teamName})"
            } else {
                "Monitoreando a los 22 monoplazas en pista • Emulación en vivo (30 FPS)"
            }
        }

        // 4. Driver Telemetry update if panel open (every 200ms for live dials!)
        if (isDriverTelemetryOpen) {
            updateSimulatedDriverTelemetry(selectedDriverNumber)
        }

        // 5. Update Tyre Strategy & Race Control (every 1 second)
        if (isSlowTick || tick == 1) {
            updateSimulatedTyreStrategy()
            updateSimulatedRaceControlAndWeather()
        }

        // 6. Update Simulated Team Radio (sincronizada con telemetría en tiempo real)
        val activeNum = activeRadioDriverNumber
        if (activeNum != null) {
            val driverInfo = activeSimDrivers.find { it.driverNumber == activeNum }?.let {
                Triple(it.driverNumber, it.fullName, it.acronym)
            } ?: simulatedDrivers.find { it.driverNumber == activeNum }?.let {
                Triple(it.driverNumber, it.fullName, it.acronym)
            }
            if (driverInfo != null) {
                val (num, fullName, acronym) = driverInfo
                val activeDriverState = activeSimDrivers.find { it.driverNumber == num }
                val cycleTicks = 140 // Transmisión periódica cada 28 segundos
                val cyclePos = tick % cycleTicks
                val isPittingNow = activeDriverState?.isPitting == true

                if (cyclePos == 0 || (isPittingNow && tick % 25 == 0)) {
                    val phase = tick / cycleTicks
                    val snapshot = getDriverTelemetrySnapshot(num)
                    val quote = getTelemetryAwareRadioQuote(num, fullName, acronym, snapshot, phase)
                    tvRadioTitle.text = "📻 [Radio #$num $acronym]: \"$quote\""
                    radioMessageExpiryTick = tick + 50 // Permanece en pantalla 10 segundos
                } else if (tick > radioMessageExpiryTick) {
                    val spd = activeDriverState?.let { getDriverTelemetrySnapshot(num).speed } ?: 290
                    val sec = activeDriverState?.let { getDriverTelemetrySnapshot(num).sector } ?: 2
                    tvRadioTitle.text = "📻 [Radio #$num $acronym]: Frecuencia activa • Sector $sec ($spd km/h) • En escucha"
                }
            }
        }
    }

    private fun updateSimulatedTyreStrategy() {
        val tyres = activeSimDrivers.map { d ->
            DriverTyreStrategyItem(
                driverNumber = d.driverNumber,
                position = d.position,
                acronym = d.acronym,
                fullName = d.fullName,
                teamName = d.teamName,
                teamColor = d.teamColor,
                currentCompound = d.tyreCompound,
                tyreAge = d.tyreAge,
                totalStints = d.pitStopsCount + 1,
                pitStopsCount = d.pitStopsCount,
                lastPitStopDuration = d.lastPitDuration,
                stintsHistory = if (d.stintsHistory.isNotBlank()) d.stintsHistory else "${d.tyreCompound.first()} (1-Act)"
            )
        }
        tyreStrategyAdapter.updateData(tyres)
    }

    private fun updateSimulatedRaceControlAndWeather() {
        val flagBanner = "🟢 BANDERA VERDE • VUELTA $currentRaceLap / $totalRaceLaps"
        tvRcMainFlagBanner?.text = flagBanner
        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#22C55E"))

        val trackTemp = 34.2 + (kotlin.math.sin(simulationTick * 0.02) * 1.2)
        val airTemp = 24.0 + (kotlin.math.sin(simulationTick * 0.01) * 0.5)
        val windSpeed = 11.8 + (kotlin.math.cos(simulationTick * 0.03) * 1.9)

        val trackTempStr = "Asfalto: ${String.format(Locale.US, "%.1f", trackTemp)} °C"
        val airTempStr = "Aire: ${String.format(Locale.US, "%.1f", airTemp)} °C"
        val rainStr = "Lluvia: ☀️ SECO (0%)"
        val windStr = "Viento: ${String.format(Locale.US, "%.1f", windSpeed)} km/h SO"

        tvRcTrackTemp?.text = trackTempStr
        tvRcAirTemp?.text = airTempStr
        tvRcRain?.text = rainStr
        tvRcWind?.text = windStr

        tvWeather?.text = "⛅ Pista: ${String.format(Locale.US, "%.1f", trackTemp)}°C | Aire: ${String.format(Locale.US, "%.1f", airTemp)}°C • Viento ${String.format(Locale.US, "%.1f", windSpeed)} km/h"

        // Vista 2: Los dos últimos mensajes de Dirección de Carrera
        updateRecentFiaMessages(simRaceControlLog)

        raceControlAdapter.updateData(simRaceControlLog.take(8))
    }

    private fun isSessionKeyword(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("práctica") || lower.contains("practica") || lower.contains("practice") ||
                lower.contains("libres") || lower.contains("fp1") || lower.contains("fp2") || lower.contains("fp3") ||
                lower.contains("clasificación") || lower.contains("clasificacion") || lower.contains("qualifying") ||
                lower.contains("qualy") || lower.contains("shootout") ||
                lower.contains("sprint") || lower.contains("spring") ||
                lower.contains("carrera") || lower.contains("race")
    }

    fun loadTrackMapData() {
        if (isSimulationMode) return
        scope.launch {
            try {
                val (circuitName, circuitSpecs) = getCircuitDetails(currentScheduleTitle)
                tvTrackMapCircuitName?.text = circuitName
                trackMapView?.circuitName = circuitName

                val isEvent = isSessionEventActive()

                if (!isEvent) {
                    // Estado Sin Evento: Mostrar imagen oficial de la carrera / circuito
                    layoutTrackMapCircuitImageContainer?.visibility = View.VISIBLE
                    trackMapView?.visibility = View.GONE
                    trackMapView?.setDriverPositions(emptyList())
                    lastKnownMarkers = emptyList()
                    tvTrackMapGpsStatus?.text = "🏁 CIRCUITO OFICIAL • SIN SESIÓN EN VIVO"
                    tvTrackMapGpsStatus?.setBackgroundColor(Color.parseColor("#1E293B"))
                    val nextInfo = nextSessionFormattedDate?.let { " • Próxima cita: $it" } ?: ""
                    tvTrackMapFollowedDriver?.text = "Sin sesión en vivo activa$nextInfo • El mapa GPS se activará durante el evento oficial"
                    tvTrackMapLegend?.visibility = View.GONE

                    val imgUrl = currentCircuitImageUrl
                        ?: repository.findSmartSelectedMeeting(AppConfig.DEFAULT_SEASON)?.circuitImage
                        ?: repository.getCircuitSilhouetteUrl(circuitName, currentScheduleTitle)

                    if (!imgUrl.isNullOrEmpty()) {
                        ivTrackMapCircuitImage?.load(imgUrl) {
                            crossfade(true)
                        }
                    }
                    tvTrackMapImageCaption?.text = "$circuitName • Trazado Oficial de Carrera$nextInfo"
                    return@launch
                }

                // Estado Con Evento (Prácticas, Clasificación, Sprint o Carrera): Mostrar mapa GPS con TODOS los pilotos
                layoutTrackMapCircuitImageContainer?.visibility = View.GONE
                trackMapView?.visibility = View.VISIBLE
                tvTrackMapLegend?.visibility = View.VISIBLE
                tvTrackMapGpsStatus?.text = "🟢 GPS EN VIVO"
                tvTrackMapGpsStatus?.setBackgroundColor(Color.parseColor("#15803D"))

                // Cargar trazado oficial del circuito específico
                trackMapView?.loadCircuit(circuitName)

                val key = currentSessionKey ?: return@launch
                val outline: List<CarLocation> = repository.getTrackOutline(key)
                if (outline.isNotEmpty()) {
                    val pts = outline.map { pt: CarLocation -> PointF(pt.x, pt.y) }
                    trackMapView?.setTrackPoints(pts)
                }

                val locations: List<CarLocation> = repository.getCarLocations(key)
                val sessionDrivers = repository.getDrivers(key).associateBy { it.driverNumber }

                val markers: List<TrackMapView.DriverMarker> = if (locations.isNotEmpty()) {
                    // Posiciones GPS en tiempo real de OpenF1
                    val realMarkers = locations.map { loc: CarLocation ->
                        val driver = sessionDrivers[loc.driverNumber]
                        val acronym = driver?.acronym ?: "#${loc.driverNumber}"
                        val teamColor = driver?.teamColour ?: repository.getTeamColor(driver?.teamName ?: "")
                        TrackMapView.DriverMarker(
                            driverNumber = loc.driverNumber,
                            acronym = acronym,
                            colorHex = teamColor,
                            rawX = loc.x,
                            rawY = loc.y
                        )
                    }
                    lastKnownMarkers = realMarkers
                    realMarkers
                } else if (lastKnownMarkers.isNotEmpty()) {
                    // Durante la carrera: resiliencia ante cortes momentáneos de red
                    lastKnownMarkers
                } else {
                    emptyList()
                }

                trackMapView?.leaderDriverNumber = markers.firstOrNull()?.driverNumber
                trackMapView?.selectedRadioDriverNumber = activeRadioDriverNumber
                trackMapView?.selectedDriverNumber = if (isDriverTelemetryOpen) selectedDriverNumber else null
                trackMapView?.setDriverPositions(markers)
                if (markers.isNotEmpty()) {
                    tvTrackMapFollowedDriver?.text = if (isDriverTelemetryOpen && selectedDriverItem != null) {
                        "Piloto seguido: #${selectedDriverItem!!.driverNumber} ${selectedDriverItem!!.acronym} (${selectedDriverItem!!.teamName})"
                    } else {
                        "Monitoreando a los ${markers.size} pilotos en pista"
                    }
                } else {
                    tvTrackMapFollowedDriver?.text = "Esperando telemetría de monoplazas en pista..."
                }
            } catch (e: Exception) {
                // Protección durante la carrera: jamás dañar la pantalla ni cerrar la vista
                Log.w("PitWallViewManager", "Resiliencia activa en telemetría de mapa: ${e.message}")
                if (lastKnownMarkers.isNotEmpty()) {
                    trackMapView?.setDriverPositions(lastKnownMarkers)
                }
            }
        }
    }

    private fun getCircuitDetails(title: String): Pair<String, String> {
        val lower = title.lowercase(Locale.ROOT)
        return when {
            lower.contains("vegas") ->
                "LAS VEGAS STRIP CIRCUIT • EE. UU." to "Longitud: 6.201 km • 17 Curvas • 2 Zonas DRS • Récord: 1:35.490 (Piastri) • Sentido: Antihorario"
            lower.contains("sakhir") || lower.contains("bahrain") || lower.contains("barein") ->
                "BAHRAIN INTERNATIONAL CIRCUIT • SAHIR" to "Longitud: 5.412 km • 15 Curvas • 3 Zonas DRS • Récord: 1:31.447 (De la Rosa) • Sentido: Horario"
            lower.contains("jeddah") || lower.contains("saudi") || lower.contains("arabia") ->
                "JEDDAH CORNICHE CIRCUIT • ARABIA SAUDÍ" to "Longitud: 6.174 km • 27 Curvas • 3 Zonas DRS • Récord: 1:30.734 (Hamilton) • Sentido: Antihorario"
            lower.contains("melbourne") || lower.contains("albert") || lower.contains("australia") ->
                "ALBERT PARK CIRCUIT • MELBOURNE" to "Longitud: 5.278 km • 14 Curvas • 4 Zonas DRS • Récord: 1:19.813 (Leclerc) • Sentido: Horario"
            lower.contains("suzuka") || lower.contains("japan") || lower.contains("japon") || lower.contains("japón") ->
                "SUZUKA INTERNATIONAL RACING COURSE • JAPÓN" to "Longitud: 5.807 km • 18 Curvas • 1 Zona DRS • Récord: 1:30.983 (Hamilton) • Sentido: Figura 8"
            lower.contains("shanghai") || lower.contains("china") ->
                "SHANGHAI INTERNATIONAL CIRCUIT • CHINA" to "Longitud: 5.451 km • 16 Curvas • 2 Zonas DRS • Récord: 1:32.238 (Schumacher) • Sentido: Horario"
            lower.contains("miami") ->
                "MIAMI INTERNATIONAL AUTODROME • EE. UU." to "Longitud: 5.412 km • 19 Curvas • 3 Zonas DRS • Récord: 1:29.708 (Verstappen) • Sentido: Antihorario"
            lower.contains("imola") || lower.contains("emilia") || lower.contains("romagna") ->
                "AUTODROMO ENZO E DINO FERRARI • IMOLA" to "Longitud: 4.909 km • 19 Curvas • 1 Zona DRS • Récord: 1:15.484 (Hamilton) • Sentido: Antihorario"
            lower.contains("monaco") || lower.contains("mónaco") || lower.contains("monte") ->
                "CIRCUIT DE MONACO • MONTE CARLO" to "Longitud: 3.337 km • 19 Curvas • 1 Zona DRS • Récord: 1:12.909 (Hamilton) • Sentido: Horario"
            lower.contains("canada") || lower.contains("canadá") || lower.contains("montreal") ->
                "CIRCUIT GILLES-VILLENEUVE • MONTREAL" to "Longitud: 4.361 km • 14 Curvas • 3 Zonas DRS • Récord: 1:13.078 (Bottas) • Sentido: Horario"
            lower.contains("catalunya") || lower.contains("barcelona") || lower.contains("españa") || lower.contains("spain") ->
                "CIRCUIT DE BARCELONA-CATALUNYA • ESPAÑA" to "Longitud: 4.657 km • 14 Curvas • 2 Zonas DRS • Récord: 1:16.330 (Verstappen) • Sentido: Horario"
            lower.contains("red bull ring") || lower.contains("austria") || lower.contains("spielberg") ->
                "RED BULL RING • SPIELBERG • AUSTRIA" to "Longitud: 4.318 km • 10 Curvas • 3 Zonas DRS • Récord: 1:05.619 (Sainz) • Sentido: Horario"
            lower.contains("silverstone") || lower.contains("gran bretaña") || lower.contains("britain") || lower.contains("uk") ->
                "SILVERSTONE CIRCUIT • REINO UNIDO" to "Longitud: 5.891 km • 18 Curvas • 2 Zonas DRS • Récord: 1:27.097 (Verstappen) • Sentido: Horario"
            lower.contains("hungaroring") || lower.contains("hungary") || lower.contains("hungria") || lower.contains("hungría") || lower.contains("budapest") ->
                "HUNGARORING • BUDAPEST • HUNGRÍA" to "Longitud: 4.381 km • 14 Curvas • 2 Zonas DRS • Récord: 1:16.627 (Hamilton) • Sentido: Horario"
            lower.contains("spa") || lower.contains("belgica") || lower.contains("bélgica") || lower.contains("belgium") || lower.contains("francorchamps") ->
                "CIRCUIT DE SPA-FRANCORCHAMPS • BÉLGICA" to "Longitud: 7.004 km • 19 Curvas • 2 Zonas DRS • Récord: 1:46.286 (Bottas) • Sentido: Horario"
            lower.contains("zandvoort") || lower.contains("netherlands") || lower.contains("países bajos") || lower.contains("holanda") ->
                "CIRCUIT ZANDVOORT • PAÍSES BAJOS" to "Longitud: 4.259 km • 14 Curvas • 2 Zonas DRS • Récord: 1:11.097 (Hamilton) • Sentido: Horario"
            lower.contains("monza") || lower.contains("italia") || lower.contains("italy") ->
                "AUTODROMO NAZIONALE MONZA • ITALIA" to "Longitud: 5.793 km • 11 Curvas • 2 Zonas DRS • Récord: 1:21.046 (Barrichello) • Sentido: Horario"
            lower.contains("baku") || lower.contains("azerbaijan") || lower.contains("azerbaiyán") ->
                "BAKU CITY CIRCUIT • AZERBAIYÁN" to "Longitud: 6.003 km • 20 Curvas • 2 Zonas DRS • Récord: 1:43.009 (Leclerc) • Sentido: Antihorario"
            lower.contains("singapore") || lower.contains("singapur") || lower.contains("marina bay") ->
                "MARINA BAY STREET CIRCUIT • SINGAPUR" to "Longitud: 4.940 km • 19 Curvas • 4 Zonas DRS • Récord: 1:35.867 (Hamilton) • Sentido: Antihorario"
            lower.contains("austin") || lower.contains("americas") || lower.contains("cota") ||
                    (lower.contains("united states") && !lower.contains("vegas") && !lower.contains("miami")) ->
                "CIRCUIT OF THE AMERICAS • AUSTIN • EE. UU." to "Longitud: 5.513 km • 20 Curvas • 2 Zonas DRS • Récord: 1:36.169 (Leclerc) • Sentido: Antihorario"
            lower.contains("mexico") || lower.contains("méxico") || lower.contains("rodriguez") ->
                "AUTÓDROMO HERMANOS RODRÍGUEZ • MÉXICO" to "Longitud: 4.304 km • 17 Curvas • 3 Zonas DRS • Récord: 1:17.774 (Bottas) • Sentido: Horario"
            lower.contains("interlagos") || lower.contains("brasil") || lower.contains("brazil") || lower.contains("são paulo") || lower.contains("sao paulo") ->
                "AUTÓDROMO JOSÉ CARLOS PACE • INTERLAGOS • BRASIL" to "Longitud: 4.309 km • 15 Curvas • 2 Zonas DRS • Récord: 1:10.540 (Bottas) • Sentido: Antihorario"
            lower.contains("qatar") || lower.contains("losail") || lower.contains("lusail") ->
                "LUSAIL INTERNATIONAL CIRCUIT • QATAR" to "Longitud: 5.419 km • 16 Curvas • 1 Zona DRS • Récord: 1:24.319 (Verstappen) • Sentido: Horario"
            lower.contains("abu dhabi") || lower.contains("yas marina") || lower.contains("uae") || lower.contains("emiratos") ->
                "YAS MARINA CIRCUIT • ABU DHABI" to "Longitud: 5.281 km • 16 Curvas • 2 Zonas DRS • Récord: 1:26.103 (Verstappen) • Sentido: Antihorario"
            else ->
                "CIRCUITO OFICIAL FÓRMULA 1" to "Trazado satelital oficial • Sectores 1, 2 y 3 • Zonas DRS"
        }
    }

    fun loadTyreStrategyData() {
        if (isSimulationMode) {
            updateSimulatedTyreStrategy()
            return
        }
        if (!isSessionEventActive()) {
            tyreStrategyAdapter.updateData(emptyList())
            return
        }
        scope.launch {
            try {
                val key = currentSessionKey ?: return@launch
                val strategies = repository.getTyreStrategies(key)
                tyreStrategyAdapter.updateData(strategies)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadRaceControlAndWeatherData() {
        if (isSimulationMode) {
            updateSimulatedRaceControlAndWeather()
            return
        }
        if (!isSessionEventActive()) {
            val nextInfo = nextSessionFormattedDate?.let { " • $it" } ?: ""
            tvRcMainFlagBanner?.text = "SIN EVENTO EN VIVO • PISTA CERRADA$nextInfo"
            tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#374151"))
            tvRcTrackTemp?.text = "Pista: --°C"
            tvRcAirTemp?.text = "Aire: --°C"
            tvRcRain?.text = "Lluvia: --%"
            tvRcWind?.text = "Viento: -- km/h"
            raceControlAdapter.updateData(emptyList())
            updateRecentFiaMessages(emptyList())
            return
        }
        scope.launch {
            try {
                val key = currentSessionKey ?: return@launch
                val messages: List<RaceControl> = repository.getRaceControlMessages(key)
                raceControlAdapter.updateData(messages)
                updateRecentFiaMessages(messages)

                val latestMsg = messages.firstOrNull()
                val flag = latestMsg?.flag?.uppercase() ?: ""
                val msgUpper = latestMsg?.message?.uppercase() ?: ""

                when {
                    flag.contains("RED") || msgUpper.contains("RED FLAG") -> {
                        tvRcMainFlagBanner?.text = "🔴 BANDERA ROJA • SESIÓN DETENIDA"
                        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#EF4444"))
                    }
                    msgUpper.contains("SAFETY CAR") -> {
                        tvRcMainFlagBanner?.text = "⚠️ SAFETY CAR EN PISTA (SC)"
                        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#F97316"))
                    }
                    msgUpper.contains("VIRTUAL") || msgUpper.contains("VSC") -> {
                        tvRcMainFlagBanner?.text = "⏱️ VIRTUAL SAFETY CAR (VSC)"
                        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#F59E0B"))
                    }
                    flag.contains("YELLOW") || msgUpper.contains("YELLOW") -> {
                        val sec = latestMsg?.sector?.let { " • SECTOR $it" } ?: ""
                        tvRcMainFlagBanner?.text = "🟡 BANDERA AMARILLA • PRECAUCIÓN$sec"
                        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#EAB308"))
                    }
                    else -> {
                        tvRcMainFlagBanner?.text = "🟢 PISTA LIBRE • BANDERA VERDE"
                        tvRcMainFlagBanner?.setBackgroundColor(Color.parseColor("#22C55E"))
                    }
                }

                val weather: Weather? = repository.getLatestWeather(key)
                weather?.let { w: Weather ->
                    tvRcTrackTemp?.text = "Asfalto: ${w.trackTemp ?: "--"} °C"
                    tvRcAirTemp?.text = "Aire: ${w.airTemp ?: "--"} °C"
                    tvRcRain?.text = if ((w.rainfall ?: 0) > 0) "Lluvia: 🌧️ DETECTADA" else "Lluvia: ☀️ SECO"
                    tvRcWind?.text = "Viento: ${w.windSpeed ?: "--"} km/h"
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
        sessionTitleText: String,
        sessionType: String? = null,
        circuitImageUrl: String? = null,
        formattedSessionDate: String? = null
    ) {
        currentSessionKey = sessionKey
        isLiveSessionActive = isLive
        isUpcomingSession = isUpcoming
        currentScheduleTitle = sessionTitleText
        nextSessionFormattedDate = formattedSessionDate
        if (!sessionType.isNullOrBlank()) {
            currentSessionType = sessionType
        }
        if (!circuitImageUrl.isNullOrBlank()) {
            currentCircuitImageUrl = circuitImageUrl
        }
        tvSessionTitle.text = if (!isLive && !formattedSessionDate.isNullOrBlank()) {
            "Próxima cita: $sessionTitleText • $formattedSessionDate"
        } else {
            sessionTitleText
        }
        tvStandbyTitle.text = sessionTitleText.uppercase()
        updateScheduleBanner()
        refreshActiveTelemetryMode()
    }

    fun onScheduleUnavailable() {
        currentSessionKey = null
        isLiveSessionActive = false
        isUpcomingSession = false
        currentSessionType = null
        nextSessionFormattedDate = null
        currentScheduleTitle = "Calendario de F1 no disponible"
        tvSessionTitle.text = currentScheduleTitle
        tvStandbyTitle.text = "F1 PIT WALL TV"
        updateScheduleBanner()
        refreshActiveTelemetryMode()
    }

    private fun updateScheduleBanner() {
        if (activeSpanishBroadcasts.isEmpty()) {
            when {
                isSimulationMode -> {
                    tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $currentScheduleTitle"
                    tvStandbyStatusBadge.text = "🔴 EMULACIÓN DE CARRERA EN VIVO (22 MONOPLAZAS)"
                }
                isLiveSessionActive -> {
                    tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $currentScheduleTitle"
                    tvStandbyStatusBadge.text = "🔴 SESIÓN EN VIVO: $currentScheduleTitle"
                }
                isUpcomingSession || !isLiveSessionActive -> {
                    val dateStr = nextSessionFormattedDate?.let { " • $it" } ?: ""
                    tvLiveSessionAlertTitle.text = "⏳ PRÓXIMA FECHA: $currentScheduleTitle$dateStr"
                    tvStandbyStatusBadge.text = "⏳ PRÓXIMA CITA: $currentScheduleTitle$dateStr"
                }
                currentScheduleTitle == "Calendario de F1 no disponible" -> {
                    tvLiveSessionAlertTitle.text = "⚠️ Calendario F1 no disponible"
                    tvStandbyStatusBadge.text = "⚠️ CALENDARIO F1 NO DISPONIBLE"
                }
                else -> {
                    tvLiveSessionAlertTitle.text = "🏁 ÚLTIMA SESIÓN: $currentScheduleTitle"
                    tvStandbyStatusBadge.text = "🏁 ÚLTIMA SESIÓN: $currentScheduleTitle"
                }
            }
        }
        btnLiveSessionAction.text = "🔄 Refrescar"
        updateFutbolVivoStatusBadge()
    }

    inner class PitWallBridge {
        @JavascriptInterface
        fun onServersAvailable(count: Int, activeIdx: Int, titlesJson: String?) {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source != BroadcastSource.FLX && activeSpanishBroadcasts.isEmpty()) {
                    updateServerButtons(count, activeIdx, titlesJson)
                }
            }
        }

        @JavascriptInterface
        fun onSpanishBroadcastsDetected(json: String?) {
            rootView.post {
                if (!json.isNullOrBlank()) {
                    try {
                        val array = Gson().fromJson(json, Array<SpanishBroadcast>::class.java)
                        if (array != null && array.isNotEmpty()) {
                            val source = currentLoadedSource ?: preferencesManager.broadcastSource
                            if (source == BroadcastSource.FUTBOL_LIBRE) {
                                val currentEvent = futbolLibreEvents.getOrNull(selectedFutbolLibreEventIndex)
                                val detectedPage = array.firstOrNull()?.streamUrl?.substringBefore("#")
                                val expectedPage = currentEvent?.fullMatchUrl?.substringBefore("#")
                                if (currentEvent != null && detectedPage != null && expectedPage != null &&
                                    !detectedPage.equals(expectedPage, ignoreCase = true)) {
                                    Log.d("PitWallViewManager", "Ignoring broadcast update from different page: $detectedPage != $expectedPage")
                                    return@post
                                }
                            }
                            onSpanishBroadcastsFound(
                                array.toList(),
                                source
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onFutbolLibreAgendaDetected(json: String?) {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source == BroadcastSource.FUTBOL_LIBRE && futbolLibreEvents.isEmpty() && !json.isNullOrBlank()) {
                    try {
                        val events = Gson().fromJson(json, Array<FutbolLibreEvent>::class.java)?.toList()
                        if (!events.isNullOrEmpty()) {
                            onFutbolLibreEventsFound(events)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onNoSpanishBroadcastsDetected() {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source != BroadcastSource.FLX && activeSpanishBroadcasts.isEmpty()) {
                    onNoSpanishBroadcastsFound(source)
                }
            }
        }
    }

    fun showSourceSelectionDialog() {
        val sources = BroadcastSource.entries.toTypedArray()
        val sourceNames = sources.map {
            val activeMarker = if (it == preferencesManager.broadcastSource) "✓ " else "  "
            "$activeMarker${it.displayName} (${it.defaultUrl})"
        }.toTypedArray()
        val currentIndex = sources.indexOf(preferencesManager.broadcastSource).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Seleccionar Fuente de Transmisiones")
            .setSingleChoiceItems(sourceNames, currentIndex) { dialog, which ->
                dialog.dismiss()
                val chosen = sources[which]
                if (chosen != preferencesManager.broadcastSource) {
                    preferencesManager.broadcastSource = chosen
                    updateSourceBadgeUI()
                    loadBroadcastSource(chosen, forceReload = true)
                    Toast.makeText(context, "Fuente cambiada a: ${chosen.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun updateSourceBadgeUI() {
        val source = preferencesManager.broadcastSource
        btnSourceBadgePitWall?.text = "📡 FUENTE: ${source.displayName.uppercase()} ▼"
        btnSourceBadgePitWall?.visibility = View.VISIBLE
    }

    private fun renderDynamicChannelButtons(list: List<SpanishBroadcast>) {
        val container = llDynamicChannelsContainer ?: return
        container.removeAllViews()
        dynamicChannelButtons.clear()

        if (list.isEmpty()) {
            updateDynamicServerFocusChain()
            return
        }

        val density = context.resources.displayMetrics.density
        val targetIdx = if (selectedBroadcastIndex in list.indices) selectedBroadcastIndex else 0

        list.forEachIndexed { idx, broadcast ->
            val btn = Button(context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (28 * density).toInt()
                ).apply {
                    marginEnd = (4 * density).toInt()
                }
                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.btn_tab_selector)
                text = broadcast.getButtonLabel()
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding((9 * density).toInt(), 0, (9 * density).toInt(), 0)
                isFocusable = true
                isClickable = true
                isSelected = (idx == targetIdx)

                setOnClickListener {
                    selectSpanishBroadcast(idx)
                }

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        scrollServerSelector?.smoothScrollTo(left - (50 * density).toInt(), 0)
                    }
                }
            }
            dynamicChannelButtons.add(btn)
            container.addView(btn)
        }

        updateDynamicServerFocusChain()
    }

    private fun onSpanishBroadcastsFound(list: List<SpanishBroadcast>, source: BroadcastSource) {
        activeSpanishBroadcasts = list
        layoutServerSelector.visibility = View.VISIBLE
        updateSourceBadgeUI()
        renderDynamicChannelButtons(list)

        val targetIdx = if (selectedBroadcastIndex in list.indices) selectedBroadcastIndex else 0
        highlightBroadcastButton(targetIdx)

        val target = list[targetIdx]
        tvLiveSessionAlertTitle.text = "🏎️ " + target.getFormattedTitle()
        tvStandbySubtitle.text = "Transmisión identificada: ${target.getFormattedTitle()}"
        val badgeText = if (source == BroadcastSource.FUTBOL_LIBRE) {
            "FÚTBOL LIBRE • ${list.size} TRANSMISIÓN(ES)"
        } else {
            "${source.displayName.uppercase()} • ${list.size} OPCIÓN(ES) EN ESPAÑOL"
        }
        tvStandbyStatusBadge.text = badgeText

        if (!isVideoPlaying() || currentWebUrl != target.streamUrl) {
            playSpanishBroadcast(target)
        }
    }

    fun updateDynamicServerFocusChain() {
        updatePitWallFocusChains()
    }

    fun updatePitWallFocusChains() {
        val firstRadioId = radioButtons.values.firstOrNull()?.id ?: R.id.btnMetricsTiming
        val isStandby = layoutPlayerStandby.visibility == View.VISIBLE

        // Focus target immediately below the top server bar:
        val targetBelowTopBar = if (isStandby) {
            btnStandbyPreview.id
        } else {
            R.id.cardVideoContainer
        }

        val buttons = dynamicChannelButtons
        val hasSessionSelector = btnSessionSelector?.visibility == View.VISIBLE
        val hasSourceBadge = btnSourceBadgePitWall?.visibility == View.VISIBLE

        val leftAnchorId = when {
            hasSessionSelector -> btnSessionSelector!!.id
            hasSourceBadge -> btnSourceBadgePitWall!!.id
            else -> View.NO_ID
        }

        buttons.forEachIndexed { index, button ->
            button.nextFocusUpId = R.id.btnTabPitWall
            button.nextFocusDownId = targetBelowTopBar
            button.nextFocusLeftId = if (index > 0) {
                buttons[index - 1].id
            } else if (leftAnchorId != View.NO_ID) {
                leftAnchorId
            } else {
                button.id
            }
            button.nextFocusRightId = if (index < buttons.size - 1) {
                buttons[index + 1].id
            } else {
                R.id.btnLiveSessionAction
            }
        }

        btnSessionSelector?.let { sel ->
            sel.nextFocusUpId = R.id.btnTabPitWall
            sel.nextFocusDownId = targetBelowTopBar
            sel.nextFocusLeftId = if (hasSourceBadge) btnSourceBadgePitWall!!.id else sel.id
            sel.nextFocusRightId = buttons.firstOrNull()?.id ?: R.id.btnLiveSessionAction
        }

        btnSourceBadgePitWall?.let { src ->
            src.nextFocusUpId = R.id.btnTabPitWall
            src.nextFocusDownId = targetBelowTopBar
            src.nextFocusLeftId = src.id
            src.nextFocusRightId = if (hasSessionSelector) btnSessionSelector!!.id else buttons.firstOrNull()?.id ?: R.id.btnLiveSessionAction
        }

        btnLiveSessionAction.nextFocusUpId = R.id.btnTabPitWall
        btnLiveSessionAction.nextFocusDownId = targetBelowTopBar
        btnLiveSessionAction.nextFocusLeftId = buttons.lastOrNull()?.id ?: btnSessionSelector?.id ?: btnSourceBadgePitWall?.id ?: R.id.btnLiveSessionAction
        btnLiveSessionAction.nextFocusRightId = R.id.btnMetricsTiming

        // Standby action buttons
        val topBarAnchorId = btnSourceBadgePitWall?.id ?: R.id.btnTabPitWall
        btnStandbyPreview.nextFocusUpId = topBarAnchorId
        btnStandbyPreview.nextFocusDownId = firstRadioId
        btnStandbyPreview.nextFocusLeftId = btnStandbyPreview.id
        btnStandbyPreview.nextFocusRightId = btnStandbyRecap.id

        btnStandbyRecap.nextFocusUpId = topBarAnchorId
        btnStandbyRecap.nextFocusDownId = firstRadioId
        btnStandbyRecap.nextFocusLeftId = btnStandbyPreview.id
        btnStandbyRecap.nextFocusRightId = btnStandbyOpenWeb.id

        btnStandbyOpenWeb.nextFocusUpId = topBarAnchorId
        btnStandbyOpenWeb.nextFocusDownId = firstRadioId
        btnStandbyOpenWeb.nextFocusLeftId = btnStandbyRecap.id
        btnStandbyOpenWeb.nextFocusRightId = if (btnStandbyStopVideo.visibility == View.VISIBLE) btnStandbyStopVideo.id else btnStandbyOpenWeb.id

        btnStandbyStopVideo.nextFocusUpId = topBarAnchorId
        btnStandbyStopVideo.nextFocusDownId = firstRadioId
        btnStandbyStopVideo.nextFocusLeftId = btnStandbyOpenWeb.id
        btnStandbyStopVideo.nextFocusRightId = btnStandbyStopVideo.id

        // Video Player Card (navegación D-Pad directa)
        val activeTelemetryFocusId = when {
            isDriverTelemetryOpen -> btnBackToTiming?.id ?: R.id.btnMetricsTiming
            activeTelemetryMode == TelemetryMode.TIMING -> R.id.rvLeaderboard
            activeTelemetryMode == TelemetryMode.TRACK_FIA -> btnMetricsTrackMap?.id ?: R.id.btnMetricsTiming
            activeTelemetryMode == TelemetryMode.TYRES -> rvTyreStrategy?.id ?: btnMetricsTyres?.id ?: R.id.btnMetricsTiming
            activeTelemetryMode == TelemetryMode.SOLO_TRACK -> btnMetricsSoloTrack?.id ?: R.id.btnMetricsTiming
            activeTelemetryMode == TelemetryMode.RACE_CONTROL -> rvRaceControl?.id ?: btnMetricsRaceControl?.id ?: R.id.btnMetricsTiming
            else -> R.id.btnMetricsTiming
        }

        cardVideoContainer?.let { card ->
            card.nextFocusUpId = topBarAnchorId
            card.nextFocusDownId = firstRadioId
            card.nextFocusRightId = activeTelemetryFocusId
            card.nextFocusLeftId = card.id
        }

        // Radio Buttons (Horizontal Chain & Vertical Navigation)
        val targetAboveRadio = if (isStandby) btnStandbyOpenWeb.id else R.id.cardVideoContainer
        val container = llRadioChips
        if (container != null) {
            val count = container.childCount
            for (i in 0 until count) {
                val chip = container.getChildAt(i)
                chip.nextFocusUpId = targetAboveRadio
                chip.nextFocusDownId = activeTelemetryFocusId
                if (i > 0) {
                    chip.nextFocusLeftId = container.getChildAt(i - 1).id
                } else {
                    chip.nextFocusLeftId = chip.id
                }
                if (i < count - 1) {
                    chip.nextFocusRightId = container.getChildAt(i + 1).id
                } else {
                    // Last radio chip connects RIGHT to Telemetry
                    chip.nextFocusRightId = activeTelemetryFocusId
                }
            }
        }

        // Metrics Switcher Buttons (6 Dynamic Modes)
        btnMetricsTiming.nextFocusUpId = R.id.btnTabPitWall
        btnMetricsTiming.nextFocusLeftId = R.id.cardVideoContainer
        btnMetricsTiming.nextFocusRightId = btnMetricsTrackMap?.id ?: (btnMetricsTyres?.id ?: R.id.btnRefreshLive)
        btnMetricsTiming.nextFocusDownId = R.id.rvLeaderboard

        btnMetricsTrackMap?.nextFocusUpId = R.id.btnTabPitWall
        btnMetricsTrackMap?.nextFocusLeftId = btnMetricsTiming.id
        btnMetricsTrackMap?.nextFocusRightId = btnMetricsTyres?.id ?: (btnMetricsSoloTrack?.id ?: R.id.btnRefreshLive)
        btnMetricsTrackMap?.nextFocusDownId = R.id.cardVideoContainer

        btnMetricsTyres?.nextFocusUpId = R.id.btnTabPitWall
        btnMetricsTyres?.nextFocusLeftId = btnMetricsTrackMap?.id ?: btnMetricsTiming.id
        btnMetricsTyres?.nextFocusRightId = btnMetricsSoloTrack?.id ?: (btnMetricsRaceControl?.id ?: R.id.btnRefreshLive)
        btnMetricsTyres?.nextFocusDownId = rvTyreStrategy?.id ?: R.id.btnMetricsTiming

        btnMetricsSoloTrack?.nextFocusUpId = R.id.btnTabPitWall
        btnMetricsSoloTrack?.nextFocusLeftId = btnMetricsTyres?.id ?: btnMetricsTiming.id
        btnMetricsSoloTrack?.nextFocusRightId = btnMetricsRaceControl?.id ?: R.id.btnRefreshLive
        btnMetricsSoloTrack?.nextFocusDownId = R.id.cardVideoContainer

        btnMetricsRaceControl?.nextFocusUpId = R.id.btnTabPitWall
        btnMetricsRaceControl?.nextFocusLeftId = btnMetricsSoloTrack?.id ?: (btnMetricsTyres?.id ?: btnMetricsTiming.id)
        btnMetricsRaceControl?.nextFocusRightId = R.id.btnRefreshLive
        btnMetricsRaceControl?.nextFocusDownId = rvRaceControl?.id ?: R.id.btnMetricsTiming

        btnRefreshLive?.nextFocusUpId = R.id.btnTabPitWall
        btnRefreshLive?.nextFocusLeftId = btnMetricsRaceControl?.id ?: btnMetricsTiming.id
        btnRefreshLive?.nextFocusRightId = btnRefreshLive?.id ?: View.NO_ID
        btnRefreshLive?.nextFocusDownId = activeTelemetryFocusId

        // Content Views Left -> Video Container
        rvLeaderboard.nextFocusUpId = btnMetricsTiming.id
        rvLeaderboard.nextFocusLeftId = R.id.cardVideoContainer

        rvTyreStrategy?.nextFocusUpId = btnMetricsTyres?.id ?: btnMetricsTiming.id
        rvTyreStrategy?.nextFocusLeftId = R.id.cardVideoContainer

        rvRaceControl?.nextFocusUpId = btnMetricsRaceControl?.id ?: btnMetricsTiming.id
        rvRaceControl?.nextFocusLeftId = R.id.cardVideoContainer

        btnBackToTiming?.nextFocusLeftId = R.id.cardVideoContainer
    }

    private fun onNoSpanishBroadcastsFound(source: BroadcastSource) {
        activeSpanishBroadcasts = emptyList()
        renderDynamicChannelButtons(emptyList())
        updateSourceBadgeUI()

        if (source == BroadcastSource.FUTBOL_LIBRE) {
            btnSessionSelector?.visibility = View.VISIBLE
            btnSessionSelector?.text = "⚠️ Agenda vacía (Reintentar)"
            btnSessionSelector?.setOnClickListener {
                Toast.makeText(context, "Reintentando conectar a Fútbol Libre Agenda...", Toast.LENGTH_SHORT).show()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
            tvLiveSessionAlertTitle.text = "⚠️ No hay eventos de Fórmula 1 disponibles actualmente en Fútbol Libre Agenda"
            tvStandbySubtitle.text = "No se encontraron eventos o sesiones de F1 en la agenda de Fútbol Libre. Puedes cambiar la fuente en Ajustes o recargar."
            tvStandbyStatusBadge.text = "FÚTBOL LIBRE • SIN EVENTOS F1"
        } else {
            btnSessionSelector?.visibility = View.VISIBLE
            btnSessionSelector?.text = "📅 Ver Agenda F1"
            btnSessionSelector?.setOnClickListener {
                preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                updateSourceBadgeUI()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
            tvLiveSessionAlertTitle.text = "⚠️ No hay transmisiones en español disponibles actualmente para ${source.displayName}"
            tvStandbySubtitle.text = "No se encontraron transmisiones en español disponibles en ${source.displayName}. Puedes cambiar la fuente en Ajustes o recargar."
            tvStandbyStatusBadge.text = "${source.displayName.uppercase()} • SIN TRANSMISIONES EN ESPAÑOL"
        }
        btnLiveSessionAction.text = "🔄 Refrescar"
        updateDynamicServerFocusChain()
    }

    fun selectSpanishBroadcast(index: Int) {
        if (index !in activeSpanishBroadcasts.indices) {
            selectServer(index)
            return
        }
        selectedBroadcastIndex = index
        highlightBroadcastButton(index)

        val broadcast = activeSpanishBroadcasts[index]
        if ((currentLoadedSource ?: preferencesManager.broadcastSource) == BroadcastSource.FUTBOL_LIBRE && selectedFutbolLibreEventIndex in futbolLibreEvents.indices) {
            val event = futbolLibreEvents[selectedFutbolLibreEventIndex]
            tvLiveSessionAlertTitle.text = "🏎️ ${event.gpName} — ${event.sessionName} — ${broadcast.getFormattedTitle()}"
        }
        Toast.makeText(context, "Sintonizando: ${broadcast.getFormattedTitle()}...", Toast.LENGTH_SHORT).show()
        playSpanishBroadcast(broadcast)
    }

    private fun highlightBroadcastButton(index: Int) {
        selectedBroadcastIndex = index
        dynamicChannelButtons.forEachIndexed { i, btn ->
            btn.isSelected = (i == index)
        }
    }

    fun playSpanishBroadcast(broadcast: SpanishBroadcast) {
        isUserStoppedPlayback = false
        currentDirectStreamUrl = null
        startVideoPlaybackWatchdog()

        val directCandidate = broadcast.streamUrl?.takeIf { it.isNotBlank() }
        if (directCandidate != null && isDirectStreamUrl(directCandidate)) {
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            webVideoView.visibility = View.GONE
            webVideoView.stopLoading()
            webVideoView.loadUrl("about:blank")
            tvLiveSessionAlertTitle.text = "🏎️ " + broadcast.getFormattedTitle()
            playDirectStreamInExoPlayer(directCandidate)
            return
        }

        exoPlayer?.pause()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🏎️ " + broadcast.getFormattedTitle()
        updatePitWallFocusChains()

        if (currentLoadedSource == BroadcastSource.FUTBOL_LIBRE) {
            val targetUrl = broadcast.streamUrl?.takeIf { it.isNotBlank() }
            if (targetUrl.isNullOrBlank()) {
                Toast.makeText(context, "La fuente seleccionada no tiene una página de evento válida.", Toast.LENGTH_SHORT).show()
                return
            }

            val targetPage = targetUrl.substringBefore("#")
            val loadedPage = (webVideoView.url ?: currentWebUrl.orEmpty()).substringBefore("#")
            currentWebUrl = targetUrl
            pendingFutbolLibreSourceId = broadcast.sourceId

            // Load the public event page first; its visible source selector then changes
            // the provider inside that page. Do not extract private media URLs.
            if (loadedPage.equals(targetPage, ignoreCase = true) && !broadcast.clickActionJs.isNullOrBlank()) {
                webVideoView.evaluateJavascript(broadcast.clickActionJs, null)
            } else {
                webVideoView.loadUrl(targetUrl)
            }

            webVideoView.postDelayed({ triggerPlayerTap() }, 1800)
            webVideoView.postDelayed({ triggerPlayerTap() }, 4000)
            dynamicChannelButtons.firstOrNull()?.let { targetBtn ->
                targetBtn.post { targetBtn.requestFocus() }
            }
            return
        }

        // Keep the existing FLX and Fútbol Vivo playback path unchanged.
        if (!broadcast.clickActionJs.isNullOrBlank()) {
            webVideoView.evaluateJavascript(broadcast.clickActionJs, null)
            webVideoView.postDelayed({ triggerPlayerTap() }, 2000)
            webVideoView.postDelayed({ triggerPlayerTap() }, 4500)
        } else if (!broadcast.streamUrl.isNullOrBlank()) {
            currentWebUrl = broadcast.streamUrl
            webVideoView.loadUrl(broadcast.streamUrl)
            dynamicChannelButtons.firstOrNull()?.let { targetBtn ->
                targetBtn.post { targetBtn.requestFocus() }
            }
        } else {
            Toast.makeText(context, "Transmisión no disponible. Selecciona otra opción.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServerButtons(count: Int, activeIdx: Int, titlesJson: String?) {
        if (activeSpanishBroadcasts.isNotEmpty() || (currentLoadedSource ?: preferencesManager.broadcastSource) == BroadcastSource.FLX) {
            return
        }
        android.util.Log.d("PitWallServer", "updateServerButtons: count=$count, active=$activeIdx, titles=$titlesJson")

        val titles = if (!titlesJson.isNullOrBlank()) {
            runCatching { Gson().fromJson(titlesJson, Array<String>::class.java)?.toList().orEmpty() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val syntheticBroadcasts = (0 until count).map { i ->
            val title = titles.getOrNull(i)?.let { formatServerTitle(it, i + 1) } ?: ("Opción " + (i + 1))
            SpanishBroadcast(
                index = i + 1,
                network = title,
                language = "",
                serverName = title,
                originalTitle = title,
                streamUrl = currentWebUrl.orEmpty(),
                clickActionJs = "window.switchServer && window.switchServer($i);",
                sourceId = "$i",
                customFormattedTitle = title,
                customButtonLabel = "S${i + 1}: $title"
            )
        }
        renderDynamicChannelButtons(syntheticBroadcasts)
        highlightBroadcastButton(activeIdx.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    private fun formatServerTitle(raw: String, fallbackNum: Int): String {
        val clean = raw.trim()
        if (clean.equals("English", ignoreCase = true)) return "Opción $fallbackNum (ENG)"
        if (clean.equals("Spanish", ignoreCase = true) || clean.equals("Español", ignoreCase = true)) return "Opción $fallbackNum (ESP)"
        return if (clean.length in 1..14) clean else "Opción $fallbackNum"
    }

    fun selectServer(index: Int) {
        selectedServerIndex = index
        currentDirectStreamUrl = null
        serverButtons.forEachIndexed { buttonIndex, button ->
            button.isSelected = buttonIndex == index
        }

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
        val source = preferencesManager.broadcastSource
        Toast.makeText(context, "🔄 Recargando transmisiones (${source.displayName})...", Toast.LENGTH_SHORT).show()
        if (webVideoView.visibility == View.VISIBLE && currentWebUrl != null && currentWebUrl != "about:blank" && activeSpanishBroadcasts.isNotEmpty()) {
            selectSpanishBroadcast(selectedBroadcastIndex)
        } else {
            loadBroadcastSource(source, forceReload = true)
        }
    }

    fun onPause() {
        stopTrackMapPolling()
        exoPlayer?.pause()
        webVideoView.onPause()
        flxExtractor.destroy()
        futbolVivoExtractor.onPause()
        futbolLibreExtractor.destroy()
    }

    fun onResume() {
        if (activeTelemetryMode == TelemetryMode.TRACK_FIA || activeTelemetryMode == TelemetryMode.SOLO_TRACK) {
            startTrackMapPolling()
        }
        webVideoView.onResume()
        webVideoView.resumeTimers()
        futbolVivoExtractor.onResume()
    }

    fun onDestroy() {
        radioPollingJob?.cancel()
        radioPollingJob = null
        stopSimulationEngine()
        stopTrackMapPolling()
        futbolLibreSourcesJob?.cancel()
        flxExtractor.destroy()
        futbolVivoExtractor.destroy()
        futbolLibreExtractor.destroy()
        webVideoView.stopLoading()
        webVideoView.destroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
