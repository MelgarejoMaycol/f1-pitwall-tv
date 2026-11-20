package com.f1pitwall.tv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.storage.PreferencesManager
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.mapper.LeaderboardMapper
import com.f1pitwall.tv.data.model.CircuitCardItem
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.ui.archive.ArchiveViewManager
import com.f1pitwall.tv.ui.home.HomeViewManager
import com.f1pitwall.tv.ui.navigation.NavigationBackstack
import com.f1pitwall.tv.ui.navigation.NavigationDestination
import com.f1pitwall.tv.ui.navigation.NavigationSubSection
import com.f1pitwall.tv.ui.navigation.ScreenState
import com.f1pitwall.tv.ui.player.RadioManager
import com.f1pitwall.tv.ui.player.VideoPlaybackManager
import com.f1pitwall.tv.ui.settings.SettingsViewManager
import com.f1pitwall.tv.ui.telemetry.TelemetryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Navigation Backstack & Focus History
    private val navigationBackstack = NavigationBackstack()

    // Tabs
    private lateinit var btnTabHome: Button
    private lateinit var btnTabPitWall: Button
    private lateinit var btnTabArchive: Button
    private lateinit var btnTabSettings: Button

    // Layout Containers
    private lateinit var layoutHome: View
    private lateinit var layoutPitWall: LinearLayout
    private lateinit var layoutArchive: LinearLayout
    private lateinit var layoutSettings: View

    // Repositories & Data Providers
    private val api = OpenF1ApiClient()
    private val repository by lazy { F1Repository(api) }
    private val preferencesManager by lazy { PreferencesManager(this) }

    // Specialized Feature Managers (Architecture & Separation of Concerns)
    private lateinit var homeViewManager: HomeViewManager
    private lateinit var archiveViewManager: ArchiveViewManager
    private lateinit var settingsViewManager: SettingsViewManager
    private lateinit var videoPlaybackManager: VideoPlaybackManager
    private lateinit var radioManager: RadioManager
    private lateinit var telemetryManager: TelemetryManager

    // Pit Wall Live Metrics UI
    private lateinit var tvRaceFlag: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvSessionTitle: TextView
    private lateinit var btnRefreshLive: Button
    private lateinit var rvLeaderboard: RecyclerView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var btnMetricsTiming: Button
    private lateinit var btnMetricsTelemetry: Button
    private lateinit var layoutTelemetryPanel: LinearLayout

    // Standby Muro de Pits Container & Elements
    private lateinit var layoutPitWallStandby: LinearLayout
    private lateinit var tvStandbyBadge: TextView
    private lateinit var tvStandbyTitle: TextView
    private lateinit var tvStandbyNextSession: TextView
    private lateinit var tvStandbyLastRace: TextView
    private lateinit var btnStandbyViewSchedule: Button
    private lateinit var btnStandbyViewResults: Button
    private lateinit var btnStandbyConfigureSource: Button

    // Live Alert Banner & Hub bar
    private lateinit var layoutLiveSessionAlert: LinearLayout
    private lateinit var tvLiveSessionAlertTitle: TextView
    private lateinit var btnLiveSessionAction: Button
    private lateinit var btnContentPreview: Button
    private lateinit var btnContentRecap: Button
    private lateinit var btnContentBoxes: Button

    // Runtime state
    private var currentSessionKey: Long? = null
    private var selectedMeeting: Meeting? = null
    private var selectedYear: Int = AppConfig.DEFAULT_SEASON
    private var isLiveSessionActive: Boolean = false
    private var selectedDriverNumber: Int? = null
    private var selectedDriverName: String? = null
    private var selectedDriverTeam: String? = null
    private var activeLivePollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        setContentView(R.layout.activity_main)

        initViews()
        setupNavigation()
        setupPitWallLiveContent()
        loadInitialData()
    }

    private fun initViews() {
        // Navigation Tabs
        btnTabHome = findViewById(R.id.btnTabHome)
        btnTabPitWall = findViewById(R.id.btnTabPitWall)
        btnTabArchive = findViewById(R.id.btnTabArchive)
        btnTabSettings = findViewById(R.id.btnTabSettings)

        // Containers
        layoutHome = findViewById(R.id.layoutHome)
        layoutPitWall = findViewById(R.id.layoutPitWall)
        layoutArchive = findViewById(R.id.layoutArchive)
        layoutSettings = findViewById(R.id.layoutSettings)

        // Pit Wall Live & Standby Views
        tvRaceFlag = findViewById(R.id.tvRaceFlag)
        tvWeather = findViewById(R.id.tvWeather)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        btnRefreshLive = findViewById(R.id.btnRefreshLive)
        rvLeaderboard = findViewById(R.id.rvLeaderboard)
        btnMetricsTiming = findViewById(R.id.btnMetricsTiming)
        btnMetricsTelemetry = findViewById(R.id.btnMetricsTelemetry)
        layoutTelemetryPanel = findViewById(R.id.layoutTelemetryPanel)

        layoutPitWallStandby = findViewById(R.id.layoutPitWallStandby)
        tvStandbyBadge = findViewById(R.id.tvStandbyBadge)
        tvStandbyTitle = findViewById(R.id.tvStandbyTitle)
        tvStandbyNextSession = findViewById(R.id.tvStandbyNextSession)
        tvStandbyLastRace = findViewById(R.id.tvStandbyLastRace)
        btnStandbyViewSchedule = findViewById(R.id.btnStandbyViewSchedule)
        btnStandbyViewResults = findViewById(R.id.btnStandbyViewResults)
        btnStandbyConfigureSource = findViewById(R.id.btnStandbyConfigureSource)

        layoutLiveSessionAlert = findViewById(R.id.layoutLiveSessionAlert)
        tvLiveSessionAlertTitle = findViewById(R.id.tvLiveSessionAlertTitle)
        btnLiveSessionAction = findViewById(R.id.btnLiveSessionAction)
        btnContentPreview = findViewById(R.id.btnContentPreview)
        btnContentRecap = findViewById(R.id.btnContentRecap)
        btnContentBoxes = findViewById(R.id.btnContentBoxes)

        // 1. Video Playback Manager (Inline Pit Wall + Dedicated Fullscreen)
        videoPlaybackManager = VideoPlaybackManager(
            context = this,
            framePitWallSurface = findViewById(R.id.framePitWallSurface),
            frameDedicatedSurface = findViewById(R.id.frameDedicatedSurface),
            // Inline Pit Wall Controls
            playerView = findViewById(R.id.playerView),
            webVideoView = findViewById(R.id.webVideoView),
            layoutPlayerStatusOverlay = findViewById(R.id.layoutPlayerStatusOverlay),
            pbPlayerLoading = findViewById(R.id.pbPlayerLoading),
            tvPlayerStatusTitle = findViewById(R.id.tvPlayerStatusTitle),
            tvPlayerStatusMessage = findViewById(R.id.tvPlayerStatusMessage),
            layoutPlayerErrorActions = findViewById(R.id.layoutPlayerErrorActions),
            btnPlayerRetry = findViewById(R.id.btnPlayerRetry),
            btnPlayerGoSettings = findViewById(R.id.btnPlayerGoSettings),
            // Dedicated Fullscreen Controls
            layoutDedicatedPlayback = findViewById(R.id.layoutDedicatedPlayback),
            playerViewDedicated = findViewById(R.id.playerViewDedicated),
            layoutDedicatedTopBar = findViewById(R.id.layoutDedicatedTopBar),
            btnDedicatedBack = findViewById(R.id.btnDedicatedBack),
            tvDedicatedSourceName = findViewById(R.id.tvDedicatedSourceName),
            tvDedicatedSourceTypeBadge = findViewById(R.id.tvDedicatedSourceTypeBadge),
            btnDedicatedPlayPause = findViewById(R.id.btnDedicatedPlayPause),
            btnDedicatedReload = findViewById(R.id.btnDedicatedReload),
            btnDedicatedMute = findViewById(R.id.btnDedicatedMute),
            btnDedicatedSessions = findViewById(R.id.btnDedicatedSessions),
            layoutDedicatedStatusOverlay = findViewById(R.id.layoutDedicatedStatusOverlay),
            pbDedicatedLoading = findViewById(R.id.pbDedicatedLoading),
            tvDedicatedStatusTitle = findViewById(R.id.tvDedicatedStatusTitle),
            tvDedicatedStatusMessage = findViewById(R.id.tvDedicatedStatusMessage),
            layoutDedicatedErrorActions = findViewById(R.id.layoutDedicatedErrorActions),
            btnDedicatedRetry = findViewById(R.id.btnDedicatedRetry),
            btnDedicatedGoSettings = findViewById(R.id.btnDedicatedGoSettings),
            llDedicatedChannels = findViewById(R.id.llDedicatedChannels),
            // Inline Pit Wall Active Controls Bar
            llPitWallPlayerBar = findViewById(R.id.llPitWallPlayerBar),
            llPitWallChannels = findViewById(R.id.llPitWallChannels),
            btnPitWallMute = findViewById(R.id.btnPitWallMute),
            btnPitWallReload = findViewById(R.id.btnPitWallReload),
            btnPitWallSessions = findViewById(R.id.btnPitWallSessions),
            btnPitWallFullscreenInline = findViewById(R.id.btnPitWallFullscreenInline),
            layoutCustomViewContainer = findViewById(R.id.layoutCustomViewContainer),
            layoutPopupContainer = findViewById(R.id.layoutPopupContainer),
            onPlaybackStarted = {
                radioManager.pause()
            },
            onNavigateToSettings = {
                btnTabSettings.performClick()
            },
            onExitDedicatedFullscreen = {
                btnTabPitWall.requestFocus()
            }
        )

        // 2. Radio Manager
        val radioButtons = listOf<Button>(
            findViewById(R.id.btnRadioVer),
            findViewById(R.id.btnRadioNor),
            findViewById(R.id.btnRadioLec),
            findViewById(R.id.btnRadioHam),
            findViewById(R.id.btnRadioRus),
            findViewById(R.id.btnRadioPia),
            findViewById(R.id.btnRadioAlo)
        )
        radioManager = RadioManager(
            context = this,
            repository = repository,
            scope = lifecycleScope,
            tvRadioTitle = findViewById(R.id.tvRadioTitle),
            btnPlayRadio = findViewById(R.id.btnPlayRadio),
            radioButtons = radioButtons,
            onPlaybackStarted = {
                videoPlaybackManager.pause()
            }
        )

        // Setup individual radio channel chips
        val driverMap = listOf(
            Pair(1, "VER"),
            Pair(4, "NOR"),
            Pair(16, "LEC"),
            Pair(44, "HAM"),
            Pair(63, "RUS"),
            Pair(81, "PIA"),
            Pair(14, "ALO")
        )
        radioButtons.forEachIndexed { index, button ->
            val driver = driverMap.getOrNull(index)
            if (driver != null) {
                button.setOnClickListener {
                    radioManager.tuneRadioDriver(currentSessionKey, driver.first, driver.second, button)
                }
            }
        }

        // 3. Telemetry Manager
        telemetryManager = TelemetryManager(
            containerView = layoutTelemetryPanel,
            repository = repository,
            scope = lifecycleScope
        )

        // 4. Archive View Manager
        archiveViewManager = ArchiveViewManager(
            rootView = layoutArchive,
            repository = repository,
            api = api,
            scope = lifecycleScope,
            onMeetingClicked = { meeting ->
                selectedMeeting = meeting
                settingsViewManager.updateSmartSearchQueries(meeting.meetingName ?: "F1", "carrera")
            }
        )

        // 5. Settings View Manager
        settingsViewManager = SettingsViewManager(
            rootView = layoutSettings,
            preferencesManager = preferencesManager,
            onSourceActiveChanged = { source ->
                // Actualización de fuente activa
            },
            onSourceDeleted = { source ->
                if (videoPlaybackManager.currentPlayingSourceUrl == source.streamUrl) {
                    videoPlaybackManager.stop()
                    layoutPitWallStandby.visibility = View.VISIBLE
                    findViewById<View>(R.id.playerView).visibility = View.GONE
                }
            },
            onStartPlayback = { url, type, name ->
                navigateToPitWallForPlayback()
                videoPlaybackManager.playStream(url, type, name, startInFullscreen = false)
            },
            onBeforeEditing = {
                pushCurrentScreenToBackstack()
            }
        )

        // 6. Home View Manager
        homeViewManager = HomeViewManager(
            rootView = layoutHome,
            repository = repository,
            scope = lifecycleScope,
            onOpenRaceScreen = {
                btnTabPitWall.performClick()
            },
            onOpenGpDetail = { meeting ->
                selectedMeeting = meeting
                btnTabArchive.performClick()
                archiveViewManager.displayMeetingDetails(meeting)
                archiveViewManager.showScheduleTab()
                archiveViewManager.rvMeetings.post {
                    val index = archiveViewManager.meetingAdapter.meetings.indexOfFirst { it.meetingKey == meeting.meetingKey }
                    if (index >= 0) {
                        archiveViewManager.rvMeetings.scrollToPosition(index)
                    }
                }
            },
            onOpenHubSection = { hubId ->
                when (hubId) {
                    "mundial" -> {
                        val scroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollHome)
                        val target = findViewById<View>(R.id.sectionDrivers)
                        if (scroll != null && target != null) {
                            scroll.smoothScrollTo(0, target.top)
                            target.requestFocus()
                        }
                    }
                    "muro_pits" -> btnTabPitWall.performClick()
                    "calendario" -> btnTabArchive.performClick()
                    "resultados" -> {
                        btnTabArchive.performClick()
                        archiveViewManager.showResultsTab()
                    }
                    "configuracion" -> btnTabSettings.performClick()
                    "estadisticas" -> {
                        btnTabPitWall.performClick()
                        btnMetricsTelemetry.performClick()
                    }
                    else -> {
                        Toast.makeText(this, "Sección '$hubId' disponible", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onOpenCircuit = { circuit: CircuitCardItem ->
                btnTabArchive.performClick()
                lifecycleScope.launch {
                    val meetings = repository.getMeetings(selectedYear)
                    val match = if (circuit.meetingKey != null) {
                        meetings.find { it.meetingKey == circuit.meetingKey }
                    } else if (circuit.circuitKey > 0) {
                        meetings.find { it.circuitKey == circuit.circuitKey }
                    } else null

                    if (match != null) {
                        archiveViewManager.displayMeetingDetails(match)
                        val index = meetings.indexOfFirst { it.meetingKey == match.meetingKey }
                        if (index >= 0) {
                            archiveViewManager.rvMeetings.scrollToPosition(index)
                        }
                    } else {
                        archiveViewManager.clearMeetingDetails()
                        archiveViewManager.archiveResultsAdapter.updateData(emptyList())
                        archiveViewManager.rvArchiveResults.visibility = View.GONE
                        archiveViewManager.layoutEmptyArchiveResults.visibility = View.VISIBLE
                        archiveViewManager.tvEmptyArchiveResults.text = "No se encontraron resultados disponibles para ${circuit.circuitName}"
                    }
                    archiveViewManager.showResultsTab()
                }
            },
            onOpenGpResults = { meeting ->
                selectedMeeting = meeting
                btnTabArchive.performClick()
                archiveViewManager.displayMeetingDetails(meeting)
                archiveViewManager.showResultsTab()
                archiveViewManager.rvMeetings.post {
                    val index = archiveViewManager.meetingAdapter.meetings.indexOfFirst { it.meetingKey == meeting.meetingKey }
                    if (index >= 0) {
                        archiveViewManager.rvMeetings.scrollToPosition(index)
                    }
                }
            },
            onOpenDriverTelemetry = { driver ->
                selectedDriverNumber = driver.driverNumber
                selectedDriverName = driver.fullName
                selectedDriverTeam = driver.teamName
                btnTabPitWall.performClick()
                btnMetricsTelemetry.performClick()
                telemetryManager.loadDriverTelemetry(
                    sessionKey = currentSessionKey,
                    driverNumber = driver.driverNumber,
                    driverName = driver.fullName,
                    driverTeam = driver.teamName,
                    isLive = isLiveSessionActive
                )
            }
        )

    }

    private fun setupPitWallLiveContent() {
        rvLeaderboard.layoutManager = LinearLayoutManager(this)
        leaderboardAdapter = LeaderboardAdapter { item ->
            pushCurrentScreenToBackstack()
            selectedDriverNumber = item.driverNumber
            selectedDriverName = item.fullName
            selectedDriverTeam = item.teamName
            btnMetricsTelemetry.performClick()
            telemetryManager.loadDriverTelemetry(
                sessionKey = currentSessionKey,
                driverNumber = item.driverNumber,
                driverName = item.fullName,
                driverTeam = item.teamName,
                isLive = isLiveSessionActive
            )
            radioManager.tuneRadioDriver(currentSessionKey, item.driverNumber, item.fullName, null)
        }
        rvLeaderboard.adapter = leaderboardAdapter

        btnMetricsTiming.setOnClickListener {
            rvLeaderboard.visibility = View.VISIBLE
            layoutTelemetryPanel.visibility = View.GONE
            btnMetricsTiming.isSelected = true
            btnMetricsTelemetry.isSelected = false
            val peek = navigationBackstack.peek()
            if (peek != null && peek.destination == NavigationDestination.PIT_WALL && peek.subSection == NavigationSubSection.TIMING_TOWER) {
                navigationBackstack.pop()
            }
        }

        btnMetricsTelemetry.setOnClickListener {
            rvLeaderboard.visibility = View.GONE
            layoutTelemetryPanel.visibility = View.VISIBLE
            btnMetricsTiming.isSelected = false
            btnMetricsTelemetry.isSelected = true
            val num = selectedDriverNumber
            if (num != null) {
                telemetryManager.loadDriverTelemetry(
                    sessionKey = currentSessionKey,
                    driverNumber = num,
                    driverName = selectedDriverName ?: "Piloto",
                    driverTeam = selectedDriverTeam ?: "",
                    isLive = isLiveSessionActive
                )
            } else {
                telemetryManager.showTelemetryUnavailable("Selecciona un piloto de la Torre de Tiempos")
            }
        }
        btnMetricsTiming.isSelected = true

        telemetryManager.btnTelemetryDemoToggle.setOnClickListener {
            val num = selectedDriverNumber
            if (num != null) {
                telemetryManager.loadDriverTelemetry(
                    sessionKey = currentSessionKey,
                    driverNumber = num,
                    driverName = selectedDriverName ?: "Piloto",
                    driverTeam = selectedDriverTeam ?: "",
                    isLive = isLiveSessionActive
                )
            } else {
                telemetryManager.showTelemetryUnavailable("Selecciona un piloto de la Torre de Tiempos")
            }
        }

        btnContentPreview.setOnClickListener {
            pushCurrentScreenToBackstack()
            btnTabArchive.performClick()
            archiveViewManager.showScheduleTab()
        }

        btnContentRecap.setOnClickListener {
            pushCurrentScreenToBackstack()
            btnTabArchive.performClick()
            archiveViewManager.showResultsTab()
        }

        btnContentBoxes.setOnClickListener {
            btnMetricsTelemetry.performClick()
        }

        btnStandbyViewSchedule.setOnClickListener {
            pushCurrentScreenToBackstack()
            btnTabArchive.performClick()
            archiveViewManager.showScheduleTab()
        }

        btnStandbyViewResults.setOnClickListener {
            pushCurrentScreenToBackstack()
            btnTabArchive.performClick()
            archiveViewManager.showResultsTab()
        }

        btnStandbyConfigureSource.setOnClickListener {
            btnTabSettings.performClick()
        }

        btnRefreshLive.setOnClickListener {
            loadLiveSessionData()
        }
    }

    private fun setupNavigation() {
        btnTabHome.setOnClickListener {
            if (layoutHome.visibility != View.VISIBLE) {
                pushCurrentScreenToBackstack()
                showLayout(layoutHome)
                updateTabSelection(NavigationDestination.HOME)
            }
        }

        btnTabPitWall.setOnClickListener {
            if (layoutPitWall.visibility != View.VISIBLE) {
                pushCurrentScreenToBackstack()
                showLayout(layoutPitWall)
                updateTabSelection(NavigationDestination.PIT_WALL)
            } else {
                updateTabSelection(NavigationDestination.PIT_WALL)
            }

            val isPlaybackActive = videoPlaybackManager.isPlaying()
            if (!isLiveSessionActive && !isPlaybackActive) {
                findViewById<View>(R.id.playerView).visibility = View.GONE
                findViewById<View>(R.id.webVideoView).visibility = View.GONE
                layoutPitWallStandby.visibility = View.VISIBLE
            } else if (isPlaybackActive) {
                layoutPitWallStandby.visibility = View.GONE
                if (videoPlaybackManager.currentPlayingSourceType == "WEB") {
                    findViewById<View>(R.id.playerView).visibility = View.GONE
                    findViewById<View>(R.id.webVideoView).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.webVideoView).visibility = View.GONE
                    findViewById<View>(R.id.playerView).visibility = View.VISIBLE
                }
            }
        }

        val btnPitWallFullscreen: Button = findViewById(R.id.btnPitWallFullscreen)
        btnPitWallFullscreen.setOnClickListener {
            val active = preferencesManager.getSelectedSource()
            if (active != null) {
                if (!videoPlaybackManager.isPlaying()) {
                    navigateToPitWallForPlayback()
                    videoPlaybackManager.playStream(active.streamUrl, active.type, active.name, startInFullscreen = true)
                } else {
                    videoPlaybackManager.openDedicatedFullscreen()
                }
            } else {
                Toast.makeText(this@MainActivity, "Configura una señal en Ajustes", Toast.LENGTH_SHORT).show()
                btnTabSettings.performClick()
            }
        }

        val btnStandbyOpenStream: Button = findViewById(R.id.btnStandbyOpenStream)
        btnStandbyOpenStream.setOnClickListener {
            val active = preferencesManager.getSelectedSource()
            if (active != null) {
                navigateToPitWallForPlayback()
                videoPlaybackManager.playStream(active.streamUrl, active.type, active.name, startInFullscreen = false)
            } else {
                Toast.makeText(this@MainActivity, "Configura una señal en Ajustes para ver la transmisión", Toast.LENGTH_SHORT).show()
                btnTabSettings.performClick()
            }
        }

        btnTabArchive.setOnClickListener {
            if (layoutArchive.visibility != View.VISIBLE) {
                pushCurrentScreenToBackstack()
                showLayout(layoutArchive)
                updateTabSelection(NavigationDestination.ARCHIVE)
                if (archiveViewManager.meetingAdapter.itemCount == 0) {
                    archiveViewManager.loadCalendar(selectedYear)
                }
            } else {
                updateTabSelection(NavigationDestination.ARCHIVE)
            }
        }

        btnTabSettings.setOnClickListener {
            if (layoutSettings.visibility != View.VISIBLE) {
                pushCurrentScreenToBackstack()
                showLayout(layoutSettings)
                updateTabSelection(NavigationDestination.SETTINGS)
                settingsViewManager.refreshConfiguredSourcesList()
                findViewById<View>(R.id.etStreamName).requestFocus()
            } else {
                updateTabSelection(NavigationDestination.SETTINGS)
            }
        }

        btnTabHome.isSelected = true
        btnTabHome.requestFocus()
    }

    private fun loadInitialData() {
        loadLiveSessionData()
    }

    private fun loadLiveSessionData() {
        activeLivePollingJob?.cancel()
        activeLivePollingJob = lifecycleScope.launch {
            try {
                btnRefreshLive.text = "⏳"
                val resolved = repository.resolveCurrentSessionStatus(selectedYear)

                when (resolved.status) {
                    SessionStatusType.LIVE -> {
                        val session = resolved.activeSession!!
                        val meeting = resolved.activeMeeting
                        currentSessionKey = session.sessionKey
                        isLiveSessionActive = true

                        val gpName = session.circuitShortName ?: meeting?.circuitShortName ?: "F1"
                        val sName = session.sessionName ?: "En Vivo"
                        tvSessionTitle.text = "🔴 $gpName - $sName"
                        tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $gpName - $sName"
                        btnLiveSessionAction.text = "⚡ Ver Señal en Vivo"
                        btnLiveSessionAction.setOnClickListener {
                            val active = preferencesManager.getSelectedSource()
                            if (active != null) {
                                navigateToPitWallForPlayback()
                                videoPlaybackManager.playStream(active.streamUrl, active.type, active.name)
                            } else {
                                Toast.makeText(this@MainActivity, "Configura una señal en Ajustes para ver la transmisión", Toast.LENGTH_SHORT).show()
                                btnTabSettings.performClick()
                            }
                        }

                        // Consultar endpoints en vivo únicamente para sesión activa
                        val drivers = api.getDrivers(session.sessionKey)
                        val positions = api.getLatestPositions(session.sessionKey)
                        val intervals = api.getLatestIntervals(session.sessionKey)
                        val laps = api.getLatestLaps(session.sessionKey)
                        val stints = api.getStints(session.sessionKey)
                        val weather = api.getLatestWeather(session.sessionKey)
                        val raceControl = api.getLatestRaceControl(session.sessionKey)

                        if (weather != null && (weather.trackTemp != null || weather.airTemp != null)) {
                            val trackStr = weather.trackTemp?.let { "${it}°C" } ?: "--"
                            val airStr = weather.airTemp?.let { "${it}°C" } ?: "--"
                            tvWeather.visibility = View.VISIBLE
                            tvWeather.text = "⛅ Pista: $trackStr | Aire: $airStr"
                        } else {
                            tvWeather.visibility = View.GONE
                        }

                        if (raceControl != null && !raceControl.flag.isNullOrBlank()) {
                            tvRaceFlag.visibility = View.VISIBLE
                            tvRaceFlag.text = "🏁 BANDERA ${raceControl.flag.uppercase()}"
                            when {
                                raceControl.flag.equals("YELLOW", true) -> tvRaceFlag.setTextColor(Color.parseColor("#EAB308"))
                                raceControl.flag.equals("RED", true) -> tvRaceFlag.setTextColor(Color.parseColor("#EF4444"))
                                else -> tvRaceFlag.setTextColor(Color.parseColor("#22C55E"))
                            }
                        } else {
                            tvRaceFlag.visibility = View.GONE
                        }

                        val items = LeaderboardMapper.buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                        leaderboardAdapter.updateData(items)
                    }

                    SessionStatusType.UPCOMING -> {
                        isLiveSessionActive = false
                        tvWeather.visibility = View.GONE
                        tvRaceFlag.visibility = View.GONE

                        val nextMeeting = resolved.upcomingMeeting
                        val nextSession = resolved.upcomingSession
                        val sStart = nextSession?.dateStart ?: nextMeeting?.dateStart
                        val countdown = TimeUtils.calculateCountdown(sStart)
                        val sName = nextSession?.sessionName ?: "Próxima Carrera"
                        val mName = nextMeeting?.meetingName ?: "Próximo Gran Premio"

                        tvSessionTitle.text = "Muro de Pits • En Espera"
                        tvLiveSessionAlertTitle.text = "Sin sesión en curso • Muro de Pits en Standby"
                        val activeUpcoming = preferencesManager.getSelectedSource()
                        if (activeUpcoming != null) {
                            btnLiveSessionAction.text = "⚡ Ver ${activeUpcoming.name}"
                            btnLiveSessionAction.setOnClickListener {
                                navigateToPitWallForPlayback()
                                videoPlaybackManager.playStream(activeUpcoming.streamUrl, activeUpcoming.type, activeUpcoming.name, startInFullscreen = false)
                            }
                        } else {
                            btnLiveSessionAction.text = "Configurar Video"
                            btnLiveSessionAction.setOnClickListener { btnTabSettings.performClick() }
                        }

                        tvStandbyBadge.text = "MODO STANDBY • FUERA DE SESIÓN"
                        tvStandbyTitle.text = "Sin transmisión en directo en este momento"
                        tvStandbyNextSession.text = "Próximo evento: $mName ($sName) • $countdown"

                        val lastSession = resolved.lastCompletedSession
                        val lastMeeting = resolved.lastCompletedMeeting
                        if (lastSession != null && lastMeeting != null) {
                            currentSessionKey = lastSession.sessionKey
                            tvStandbyLastRace.text = "Última sesión finalizada: ${lastMeeting.meetingName} (${lastSession.sessionName})"

                            val drivers = api.getDrivers(lastSession.sessionKey)
                            val positions = api.getLatestPositions(lastSession.sessionKey)
                            val intervals = api.getLatestIntervals(lastSession.sessionKey)
                            val laps = api.getLatestLaps(lastSession.sessionKey)
                            val stints = api.getStints(lastSession.sessionKey)
                            val items = LeaderboardMapper.buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                            leaderboardAdapter.updateData(items)
                            telemetryManager.resetTelemetryPanel("Selecciona un piloto para consultar telemetría histórica")
                        } else {
                            currentSessionKey = null
                            tvStandbyLastRace.text = "Temporada Oficial de Fórmula 1"
                            leaderboardAdapter.updateData(emptyList())
                            telemetryManager.resetTelemetryPanel("Próxima sesión: $mName ($sName)")
                        }

                        if (!videoPlaybackManager.isPlaying()) {
                            layoutPitWallStandby.visibility = View.VISIBLE
                            findViewById<View>(R.id.playerView).visibility = View.GONE
                        }
                    }

                    SessionStatusType.COMPLETED -> {
                        isLiveSessionActive = false
                        tvWeather.visibility = View.GONE
                        tvRaceFlag.visibility = View.GONE

                        val lastSession = resolved.lastCompletedSession
                        val lastMeeting = resolved.lastCompletedMeeting

                        tvSessionTitle.text = "Muro de Pits • Temporada Finalizada"
                        tvLiveSessionAlertTitle.text = "Temporada Finalizada • Resultados Oficiales"
                        val activeCompleted = preferencesManager.getSelectedSource()
                        if (activeCompleted != null) {
                            btnLiveSessionAction.text = "⚡ Ver ${activeCompleted.name}"
                            btnLiveSessionAction.setOnClickListener {
                                navigateToPitWallForPlayback()
                                videoPlaybackManager.playStream(activeCompleted.streamUrl, activeCompleted.type, activeCompleted.name, startInFullscreen = false)
                            }
                        } else {
                            btnLiveSessionAction.text = "Configurar Video"
                            btnLiveSessionAction.setOnClickListener { btnTabSettings.performClick() }
                        }

                        tvStandbyBadge.text = "TEMPORADA CONCLUIDA"
                        tvStandbyTitle.text = "Todas las carreras de la temporada han finalizado"
                        tvStandbyNextSession.text = "Próxima temporada de Fórmula 1 en preparación"

                        if (lastSession != null && lastMeeting != null) {
                            currentSessionKey = lastSession.sessionKey
                            tvStandbyLastRace.text = "Último evento: ${lastMeeting.meetingName} (${lastSession.sessionName})"

                            val drivers = api.getDrivers(lastSession.sessionKey)
                            val positions = api.getLatestPositions(lastSession.sessionKey)
                            val intervals = api.getLatestIntervals(lastSession.sessionKey)
                            val laps = api.getLatestLaps(lastSession.sessionKey)
                            val stints = api.getStints(lastSession.sessionKey)
                            val items = LeaderboardMapper.buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                            leaderboardAdapter.updateData(items)
                            telemetryManager.resetTelemetryPanel("Selecciona un piloto para consultar telemetría histórica")
                        } else {
                            currentSessionKey = null
                            tvStandbyLastRace.text = ""
                            leaderboardAdapter.updateData(emptyList())
                            telemetryManager.resetTelemetryPanel("Temporada concluida")
                        }

                        if (!videoPlaybackManager.isPlaying()) {
                            layoutPitWallStandby.visibility = View.VISIBLE
                            findViewById<View>(R.id.playerView).visibility = View.GONE
                        }
                    }

                    SessionStatusType.NO_DATA -> {
                        isLiveSessionActive = false
                        currentSessionKey = null
                        tvSessionTitle.text = "Muro de Pits • Sin Información"
                        tvLiveSessionAlertTitle.text = "Sin información de sesiones disponibles"
                        btnLiveSessionAction.text = "Configurar Video"
                        btnLiveSessionAction.setOnClickListener { btnTabSettings.performClick() }

                        tvStandbyBadge.text = "SIN INFORMACIÓN"
                        tvStandbyTitle.text = "No se encontraron sesiones registradas para la temporada $selectedYear"
                        tvStandbyNextSession.text = "Selecciona otro año en Archivo o intenta de nuevo más tarde."
                        tvStandbyLastRace.text = ""

                        leaderboardAdapter.updateData(emptyList())
                        tvWeather.visibility = View.GONE
                        tvRaceFlag.visibility = View.GONE

                        layoutPitWallStandby.visibility = View.VISIBLE
                        findViewById<View>(R.id.playerView).visibility = View.GONE
                        telemetryManager.resetTelemetryPanel("Sin sesión")
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("MainActivity", "Error cargando datos de sesión en vivo", e)
                isLiveSessionActive = false
                currentSessionKey = null
                tvSessionTitle.text = "Muro de Pits • Error de Conexión"
                tvLiveSessionAlertTitle.text = "Error al conectar con el servidor de F1"
                btnLiveSessionAction.text = "Reintentar"
                btnLiveSessionAction.setOnClickListener { loadLiveSessionData() }

                tvStandbyBadge.text = "ERROR DE CONEXIÓN"
                tvStandbyTitle.text = "No se pudo obtener la información de sesiones"
                tvStandbyNextSession.text = "Comprueba tu conexión a internet y pulsa reintentar."
                tvStandbyLastRace.text = ""

                leaderboardAdapter.updateData(emptyList())
                tvWeather.visibility = View.GONE
                tvRaceFlag.visibility = View.GONE

                layoutPitWallStandby.visibility = View.VISIBLE
                findViewById<View>(R.id.playerView).visibility = View.GONE
                telemetryManager.resetTelemetryPanel("Error de conexión")
            } finally {
                btnRefreshLive.text = "🔄"
            }
        }
    }

    private fun navigateToPitWallForPlayback() {
        if (layoutPitWall.visibility != View.VISIBLE) {
            pushCurrentScreenToBackstack()
        }
        showLayout(layoutPitWall)
        updateTabSelection(NavigationDestination.PIT_WALL)

        layoutPitWallStandby.visibility = View.GONE
        val active = preferencesManager.getSelectedSource()
        if (active?.type == "WEB") {
            findViewById<View>(R.id.playerView).visibility = View.GONE
            findViewById<View>(R.id.webVideoView).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.webVideoView).visibility = View.GONE
            findViewById<View>(R.id.playerView).visibility = View.VISIBLE
        }
    }

    private fun showLayout(target: View) {
        if (target != layoutPitWall) {
            radioManager.pause()
            findViewById<View>(R.id.layoutDedicatedPlayback).visibility = View.GONE
        }
        layoutHome.visibility = if (target == layoutHome) View.VISIBLE else View.GONE
        layoutPitWall.visibility = if (target == layoutPitWall) View.VISIBLE else View.GONE
        layoutArchive.visibility = if (target == layoutArchive) View.VISIBLE else View.GONE
        layoutSettings.visibility = if (target == layoutSettings) View.VISIBLE else View.GONE
    }

    private fun updateTabSelection(destination: NavigationDestination) {
        btnTabHome.isSelected = (destination == NavigationDestination.HOME)
        btnTabPitWall.isSelected = (destination == NavigationDestination.PIT_WALL)
        btnTabArchive.isSelected = (destination == NavigationDestination.ARCHIVE)
        btnTabSettings.isSelected = (destination == NavigationDestination.SETTINGS)
    }

    private fun captureCurrentScreenState(): ScreenState {
        val currentDest = when {
            layoutHome.visibility == View.VISIBLE -> NavigationDestination.HOME
            layoutPitWall.visibility == View.VISIBLE -> NavigationDestination.PIT_WALL
            layoutArchive.visibility == View.VISIBLE -> NavigationDestination.ARCHIVE
            layoutSettings.visibility == View.VISIBLE -> NavigationDestination.SETTINGS
            else -> NavigationDestination.HOME
        }

        val currentSub = when (currentDest) {
            NavigationDestination.HOME -> NavigationSubSection.DEFAULT
            NavigationDestination.PIT_WALL -> {
                if (layoutTelemetryPanel.visibility == View.VISIBLE) {
                    NavigationSubSection.TELEMETRY_PANEL
                } else {
                    NavigationSubSection.TIMING_TOWER
                }
            }
            NavigationDestination.ARCHIVE -> {
                if (archiveViewManager.btnViewResults.isSelected) {
                    NavigationSubSection.RESULTS
                } else {
                    NavigationSubSection.SCHEDULE
                }
            }
            NavigationDestination.SETTINGS -> NavigationSubSection.SOURCES_LIST
        }

        val currentFocused = currentFocus
        var focusedViewId: Int? = currentFocused?.id
        var itemPosition: Int? = null

        if (currentFocused != null) {
            val parentRv = findParentRecyclerView(currentFocused)
            if (parentRv != null) {
                focusedViewId = parentRv.id
                val directChild = findDirectChildOfRecyclerView(parentRv, currentFocused)
                if (directChild != null) {
                    itemPosition = parentRv.getChildAdapterPosition(directChild)
                }
            }
        }

        return ScreenState(
            destination = currentDest,
            subSection = currentSub,
            focusedViewId = if (focusedViewId != null && focusedViewId != View.NO_ID) focusedViewId else null,
            focusedItemPosition = if (itemPosition != null && itemPosition != RecyclerView.NO_POSITION) itemPosition else null,
            meetingKey = selectedMeeting?.meetingKey,
            driverNumber = selectedDriverNumber,
            selectedYear = selectedYear
        )
    }

    private fun pushCurrentScreenToBackstack() {
        val state = captureCurrentScreenState()
        navigationBackstack.push(state)
    }

    private fun restoreFocus(state: ScreenState) {
        if (state.focusedViewId != null) {
            val targetView = findViewById<View>(state.focusedViewId)
            if (targetView is RecyclerView && state.focusedItemPosition != null) {
                val pos = state.focusedItemPosition
                targetView.scrollToPosition(pos)
                targetView.post {
                    val vh = targetView.findViewHolderForAdapterPosition(pos)
                    if (vh != null) {
                        vh.itemView.requestFocus()
                    } else {
                        targetView.requestFocus()
                    }
                }
                return
            } else if (targetView != null) {
                targetView.post {
                    targetView.requestFocus()
                }
                return
            }
        }

        when (state.destination) {
            NavigationDestination.HOME -> btnTabHome.requestFocus()
            NavigationDestination.PIT_WALL -> btnTabPitWall.requestFocus()
            NavigationDestination.ARCHIVE -> btnTabArchive.requestFocus()
            NavigationDestination.SETTINGS -> btnTabSettings.requestFocus()
        }
    }

    private fun navigateToState(state: ScreenState) {
        when (state.destination) {
            NavigationDestination.HOME -> {
                showLayout(layoutHome)
                updateTabSelection(NavigationDestination.HOME)
            }
            NavigationDestination.PIT_WALL -> {
                showLayout(layoutPitWall)
                updateTabSelection(NavigationDestination.PIT_WALL)
                if (state.subSection == NavigationSubSection.TELEMETRY_PANEL && state.driverNumber != null) {
                    btnMetricsTelemetry.performClick()
                    telemetryManager.loadDriverTelemetry(
                        sessionKey = currentSessionKey,
                        driverNumber = state.driverNumber,
                        driverName = selectedDriverName ?: "Piloto",
                        driverTeam = selectedDriverTeam ?: "",
                        isLive = isLiveSessionActive
                    )
                } else {
                    btnMetricsTiming.performClick()
                }
            }
            NavigationDestination.ARCHIVE -> {
                showLayout(layoutArchive)
                updateTabSelection(NavigationDestination.ARCHIVE)
                if (state.selectedYear != null && state.selectedYear != selectedYear) {
                    archiveViewManager.selectYear(state.selectedYear)
                }
                if (state.meetingKey != null) {
                    val meeting = archiveViewManager.meetingAdapter.meetings.find { it.meetingKey == state.meetingKey }
                    if (meeting != null) {
                        archiveViewManager.displayMeetingDetails(meeting)
                    }
                }
                if (state.subSection == NavigationSubSection.RESULTS) {
                    archiveViewManager.showResultsTab()
                } else {
                    archiveViewManager.showScheduleTab()
                }
            }
            NavigationDestination.SETTINGS -> {
                showLayout(layoutSettings)
                updateTabSelection(NavigationDestination.SETTINGS)
                settingsViewManager.refreshConfiguredSourcesList()
            }
        }
    }

    private fun findParentRecyclerView(view: View): RecyclerView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is RecyclerView) return parent
            parent = parent.parent
        }
        return null
    }

    private fun findDirectChildOfRecyclerView(recyclerView: RecyclerView, view: View): View? {
        var child = view
        while (child.parent != null && child.parent != recyclerView) {
            val parent = child.parent
            if (parent !is View) return null
            child = parent
        }
        return if (child.parent == recyclerView) child else null
    }

    override fun onPause() {
        super.onPause()
        videoPlaybackManager.pause()
        radioManager.pause()
    }

    override fun onStop() {
        super.onStop()
        videoPlaybackManager.pause()
        radioManager.pause()
        activeLivePollingJob?.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (radioManager.isPlaying()) {
                    findViewById<Button>(R.id.btnPlayRadio).performClick()
                    return true
                } else if (layoutPitWall.visibility == View.VISIBLE) {
                    if (videoPlaybackManager.isPlaying()) {
                        videoPlaybackManager.pause()
                    } else {
                        videoPlaybackManager.retryPlayback()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (findViewById<Button>(R.id.btnPlayRadio).isEnabled && !radioManager.isPlaying()) {
                    findViewById<Button>(R.id.btnPlayRadio).performClick()
                    return true
                } else if (layoutPitWall.visibility == View.VISIBLE && !videoPlaybackManager.isPlaying()) {
                    videoPlaybackManager.retryPlayback()
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (radioManager.isPlaying()) {
                    findViewById<Button>(R.id.btnPlayRadio).performClick()
                    return true
                } else if (videoPlaybackManager.isPlaying()) {
                    videoPlaybackManager.pause()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 0. Si el reproductor dedicado o el webview maneja el evento atrás
        if (videoPlaybackManager.handleBackPressed()) {
            return
        }

        // 1. Si está en el Muro de Pits en Telemetría y vino de la Torre de Tiempos
        if (layoutPitWall.visibility == View.VISIBLE && layoutTelemetryPanel.visibility == View.VISIBLE) {
            val state = navigationBackstack.peek()
            if (state != null && state.destination == NavigationDestination.PIT_WALL && state.subSection == NavigationSubSection.TIMING_TOWER) {
                navigationBackstack.pop()
                btnMetricsTiming.performClick()
                restoreFocus(state)
                return
            }
        }

        // 2. Si está en Archivo Resultados y vino de Cronograma de Sesiones
        if (layoutArchive.visibility == View.VISIBLE && archiveViewManager.btnViewResults.isSelected) {
            val state = navigationBackstack.peek()
            if (state != null && state.destination == NavigationDestination.ARCHIVE && state.subSection == NavigationSubSection.SCHEDULE) {
                navigationBackstack.pop()
                archiveViewManager.showScheduleTab()
                restoreFocus(state)
                return
            }
        }

        // 3. Si hay historial en la pila de navegación, recuperar pantalla anterior y foco
        if (!navigationBackstack.isEmpty) {
            val previousState = navigationBackstack.pop()
            if (previousState != null) {
                navigateToState(previousState)
                restoreFocus(previousState)
                return
            }
        }

        // 4. Si no está en Inicio y la pila está vacía, retornar a Inicio
        if (layoutHome.visibility != View.VISIBLE) {
            showLayout(layoutHome)
            updateTabSelection(NavigationDestination.HOME)
            btnTabHome.requestFocus()
            return
        }

        // 5. Si ya está en Inicio con pila vacía, salir de la aplicación
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeLivePollingJob?.cancel()
        videoPlaybackManager.release()
        radioManager.release()
        telemetryManager.clear()
        archiveViewManager.clear()
    }
}
