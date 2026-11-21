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
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.config.VideoSourcesConfig
import com.f1pitwall.tv.core.config.BroadcastSource
import com.f1pitwall.tv.core.storage.PreferencesManager
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.data.repository.RemoteBroadcastConfigRepository
import com.f1pitwall.tv.data.repository.ConfigSyncResult
import com.f1pitwall.tv.data.model.RemoteBroadcastConfig
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.network.OpenF1AccessStatus
import com.f1pitwall.tv.network.OpenF1SessionPolicy
import com.f1pitwall.tv.ui.home.HomeViewManager
import com.f1pitwall.tv.ui.pitwall.PitWallViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Tabs
    private lateinit var btnTabHome: Button
    private lateinit var btnTabPitWall: Button
    private lateinit var btnTabArchive: Button
    private lateinit var btnTabSettings: Button

    // Layout Containers
    private lateinit var layoutHome: View
    private lateinit var layoutPitWall: View
    private lateinit var layoutArchive: View
    private lateinit var layoutSettings: View

    // Managers & Repository
    private val api = OpenF1ApiClient()
    private val repository by lazy { F1Repository(api) }
    private lateinit var homeViewManager: HomeViewManager
    private lateinit var pitWallViewManager: PitWallViewManager

    // Header Status Badges
    private lateinit var tvRaceFlag: TextView
    private lateinit var tvWeather: TextView

    // Archive / Calendar UI
    private lateinit var btnYear2024: Button
    private lateinit var btnYear2025: Button
    private lateinit var btnYear2026: Button
    private lateinit var rvMeetings: RecyclerView
    private lateinit var meetingAdapter: MeetingAdapter
    private lateinit var tvSelectedGpName: TextView
    private lateinit var tvSelectedGpCircuit: TextView
    private lateinit var tvSelectedGpDates: TextView
    private lateinit var ivCircuitTrackMap: ImageView
    private lateinit var btnViewSchedule: Button
    private lateinit var btnViewResults: Button
    private lateinit var rvSessionsSchedule: RecyclerView
    private lateinit var sessionScheduleAdapter: SessionScheduleAdapter
    private lateinit var rvArchiveResults: RecyclerView
    private lateinit var archiveResultsAdapter: LeaderboardAdapter

    // Settings UI & Smart Search
    private lateinit var cardRaceSimulation: View
    private lateinit var btnSourceFlx: Button
    private lateinit var btnSourceFutbolVivo: Button
    private lateinit var btnSourceFutbolLibre: Button
    private lateinit var tvActiveSourceInfo: TextView
    private val preferencesManager by lazy { PreferencesManager(this) }
    private lateinit var btnToggleSimulationMode: Button
    private lateinit var tvSimulationStatus: TextView
    private lateinit var tvDetectedSessionTitle: TextView
    private lateinit var tvSearchChip1: TextView
    private lateinit var tvSearchChip2: TextView
    private lateinit var tvSearchChip3: TextView
    private lateinit var tvSearchChip4: TextView
    private lateinit var btnReloadGitSources: Button
    private lateinit var scrollDirectChannels: HorizontalScrollView
    private lateinit var llDynamicDirectPresets: LinearLayout
    private lateinit var remoteConfigRepo: RemoteBroadcastConfigRepository

    private var sessionRefreshJob: Job? = null
    private var hasLiveSessionForRefresh: Boolean = false
    private var selectedMeeting: Meeting? = null
    private var selectedYear: Int = AppConfig.DEFAULT_SEASON // 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        remoteConfigRepo = RemoteBroadcastConfigRepository(this)
        setupManagers()
        setupNavigation()
        setupArchiveView()
        setupSettingsView()
        loadInitialData()

        // Sincronización asíncrona del catálogo de fuentes desde GitHub
        lifecycleScope.launch {
            syncRemoteBroadcastConfig(forceRemote = false)
        }
    }

    private fun initViews() {
        btnTabHome = findViewById(R.id.btnTabHome)
        btnTabPitWall = findViewById(R.id.btnTabPitWall)
        btnTabArchive = findViewById(R.id.btnTabArchive)
        btnTabSettings = findViewById(R.id.btnTabSettings)

        layoutHome = findViewById(R.id.layoutHome)
        layoutPitWall = findViewById(R.id.layoutPitWall)
        layoutArchive = findViewById(R.id.layoutArchive)
        layoutSettings = findViewById(R.id.layoutSettings)

        tvRaceFlag = findViewById(R.id.tvRaceFlag)
        tvWeather = findViewById(R.id.tvWeather)

        // Archive / Calendar Views
        btnYear2024 = findViewById(R.id.btnYear2024)
        btnYear2025 = findViewById(R.id.btnYear2025)
        btnYear2026 = findViewById(R.id.btnYear2026)
        rvMeetings = findViewById(R.id.rvMeetings)
        tvSelectedGpName = findViewById(R.id.tvSelectedGpName)
        tvSelectedGpCircuit = findViewById(R.id.tvSelectedGpCircuit)
        tvSelectedGpDates = findViewById(R.id.tvSelectedGpDates)
        ivCircuitTrackMap = findViewById(R.id.ivCircuitTrackMap)
        btnViewSchedule = findViewById(R.id.btnViewSchedule)
        btnViewResults = findViewById(R.id.btnViewResults)
        rvSessionsSchedule = findViewById(R.id.rvSessionsSchedule)
        rvArchiveResults = findViewById(R.id.rvArchiveResults)

        // Settings Views
        cardRaceSimulation = findViewById(R.id.cardRaceSimulation)
        btnSourceFlx = findViewById(R.id.btnSourceFlx)
        btnSourceFutbolVivo = findViewById(R.id.btnSourceFutbolVivo)
        btnSourceFutbolLibre = findViewById(R.id.btnSourceFutbolLibre)
        tvActiveSourceInfo = findViewById(R.id.tvActiveSourceInfo)
        btnToggleSimulationMode = findViewById(R.id.btnToggleSimulationMode)
        tvSimulationStatus = findViewById(R.id.tvSimulationStatus)
        tvDetectedSessionTitle = findViewById(R.id.tvDetectedSessionTitle)
        tvSearchChip1 = findViewById(R.id.tvSearchChip1)
        tvSearchChip2 = findViewById(R.id.tvSearchChip2)
        tvSearchChip3 = findViewById(R.id.tvSearchChip3)
        tvSearchChip4 = findViewById(R.id.tvSearchChip4)
        btnReloadGitSources = findViewById(R.id.btnReloadGitSources)
        scrollDirectChannels = findViewById(R.id.scrollDirectChannels)
        llDynamicDirectPresets = findViewById(R.id.llDynamicDirectPresets)
    }

    private fun setupManagers() {
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
                displayMeetingDetails(meeting)
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
                        btnViewResults.performClick()
                    }
                    "configuracion" -> btnTabSettings.performClick()
                    else -> {
                        Toast.makeText(this, "Sección '$hubId' disponible", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        pitWallViewManager = PitWallViewManager(
            rootView = layoutPitWall,
            repository = repository,
            scope = lifecycleScope,
            onNavigateToSettings = {
                btnTabSettings.performClick()
            },
            defaultWebSourceUrl = VideoSourcesConfig.loadProviders(this)
                .firstOrNull { it.isDefault }?.streamUrl
        )
        pitWallViewManager.setSimulationMode(preferencesManager.isRaceSimulationEnabled)
    }

    private fun setupNavigation() {
        btnTabHome.setOnClickListener {
            switchToTab(layoutHome, btnTabHome)
            homeViewManager.restoreFocusAndScroll()
        }

        btnTabPitWall.setOnClickListener {
            homeViewManager.saveFocusAndScroll()
            switchToTab(layoutPitWall, btnTabPitWall)
            pitWallViewManager.onPitWallTabSelected()
        }

        btnTabPitWall.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val target = findViewById<View>(R.id.btnSourceBadgePitWall)
                    ?: findViewById<View>(R.id.btnSessionSelector)
                if (target != null && target.visibility == View.VISIBLE) {
                    target.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }

        btnTabArchive.setOnClickListener {
            homeViewManager.saveFocusAndScroll()
            switchToTab(layoutArchive, btnTabArchive)
            loadCalendar(selectedYear)
        }

        btnTabSettings.setOnClickListener {
            homeViewManager.saveFocusAndScroll()
            switchToTab(layoutSettings, btnTabSettings)
            (layoutSettings as? ScrollView)?.smoothScrollTo(0, 0)
            updateSettingsFocusNavigation()
            btnSourceFlx.requestFocus() // Focus on transmission source options
        }

        btnTabSettings.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                (layoutSettings as? ScrollView)?.smoothScrollTo(0, 0)
                updateSettingsFocusNavigation()
                btnSourceFlx.requestFocus()
                return@setOnKeyListener true
            }
            false
        }

        btnTabHome.isSelected = true
        btnTabHome.requestFocus()
    }

    private fun switchToTab(targetLayout: View, targetButton: Button) {
        listOf(layoutHome, layoutPitWall, layoutArchive, layoutSettings).forEach {
            it.visibility = if (it == targetLayout) View.VISIBLE else View.GONE
        }
        listOf(btnTabHome, btnTabPitWall, btnTabArchive, btnTabSettings).forEach {
            it.isSelected = (it == targetButton)
        }
    }

    private fun setupArchiveView() {
        btnYear2024.setOnClickListener { selectYear(2024) }
        btnYear2025.setOnClickListener { selectYear(2025) }
        btnYear2026.setOnClickListener { selectYear(2026) }

        btnViewSchedule.setOnClickListener {
            rvSessionsSchedule.visibility = View.VISIBLE
            rvArchiveResults.visibility = View.GONE
            btnViewSchedule.isSelected = true
            btnViewResults.isSelected = false
        }

        btnViewResults.setOnClickListener {
            rvSessionsSchedule.visibility = View.GONE
            rvArchiveResults.visibility = View.VISIBLE
            btnViewSchedule.isSelected = false
            btnViewResults.isSelected = true
        }
        btnViewSchedule.isSelected = true

        rvMeetings.layoutManager = LinearLayoutManager(this)
        meetingAdapter = MeetingAdapter { meeting ->
            displayMeetingDetails(meeting)
        }
        rvMeetings.adapter = meetingAdapter

        rvSessionsSchedule.layoutManager = LinearLayoutManager(this)
        sessionScheduleAdapter = SessionScheduleAdapter { session ->
            loadHistoricalSession(session.sessionKey)
            btnViewResults.performClick()
        }
        rvSessionsSchedule.adapter = sessionScheduleAdapter

        rvArchiveResults.layoutManager = LinearLayoutManager(this)
        archiveResultsAdapter = LeaderboardAdapter()
        rvArchiveResults.adapter = archiveResultsAdapter
    }

    private fun setupSettingsView() {
        setupBroadcastSourceSelector()
        setupSimulationModeSelector()

        btnReloadGitSources.setOnClickListener {
            lifecycleScope.launch {
                syncRemoteBroadcastConfig(forceRemote = true)
            }
        }
    }

    private suspend fun syncRemoteBroadcastConfig(forceRemote: Boolean) {
        val result = withContext(Dispatchers.IO) {
            remoteConfigRepo.syncConfig(forceRemote)
        }

        when (result) {
            is ConfigSyncResult.Success -> {
                updateDynamicDirectPresets(result.config)
                if (forceRemote) {
                    Toast.makeText(this, "Fuentes actualizadas", Toast.LENGTH_SHORT).show()
                }
            }
            is ConfigSyncResult.Error -> {
                result.fallbackConfig?.let { updateDynamicDirectPresets(it) }
                if (forceRemote) {
                    Toast.makeText(this, "No se pudieron actualizar las fuentes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDynamicDirectPresets(config: RemoteBroadcastConfig) {
        llDynamicDirectPresets.removeAllViews()
        val channels = config.getEnabledChannels()
        val density = resources.displayMetrics.density
        val buttons = mutableListOf<Button>()

        channels.forEach { channel ->
            val button = Button(this).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (38 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.btn_tab_selector)
                text = channel.name
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 11f
                isFocusable = true
                isClickable = true
                setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                setOnClickListener {
                    pitWallViewManager.playStream(channel.url, channel.name)
                    btnTabPitWall.performClick()
                }
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        scrollDirectChannels.smoothScrollTo(view.left - (24 * density).toInt(), 0)
                    }
                }
            }
            buttons.add(button)
            llDynamicDirectPresets.addView(button)
        }

        scrollDirectChannels.visibility = if (buttons.isEmpty()) View.GONE else View.VISIBLE
        updateSettingsFocusNavigation()
    }

    private fun setupBroadcastSourceSelector() {
        val currentSource = preferencesManager.broadcastSource
        updateBroadcastSourceUI(currentSource)

        btnSourceFlx.setOnClickListener {
            preferencesManager.broadcastSource = BroadcastSource.FLX
            updateBroadcastSourceUI(BroadcastSource.FLX)
        }

        btnSourceFutbolVivo.setOnClickListener {
            preferencesManager.broadcastSource = BroadcastSource.FUTBOL_VIVO
            updateBroadcastSourceUI(BroadcastSource.FUTBOL_VIVO)
        }

        btnSourceFutbolLibre.setOnClickListener {
            preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
            updateBroadcastSourceUI(BroadcastSource.FUTBOL_LIBRE)
        }

        updateSettingsFocusNavigation()
    }

    private fun updateBroadcastSourceUI(source: BroadcastSource) {
        btnSourceFlx.isSelected = (source == BroadcastSource.FLX)
        btnSourceFutbolVivo.isSelected = (source == BroadcastSource.FUTBOL_VIVO)
        btnSourceFutbolLibre.isSelected = (source == BroadcastSource.FUTBOL_LIBRE)
        tvActiveSourceInfo.text = "Activa: ${source.displayName}"
    }

    private fun setupSimulationModeSelector() {
        val isSimEnabled = preferencesManager.isRaceSimulationEnabled
        updateSimulationModeUI(isSimEnabled)

        btnToggleSimulationMode.setOnClickListener {
            val newState = !preferencesManager.isRaceSimulationEnabled
            preferencesManager.isRaceSimulationEnabled = newState
            updateSimulationModeUI(newState)
            pitWallViewManager.setSimulationMode(newState)

            if (newState) {
                Toast.makeText(this, "🏎️ Emulación de carrera activada. Ve a 'Muro de Pits' para ver la telemetría en vivo.", Toast.LENGTH_LONG).show()
            } else {
                lifecycleScope.launch {
                    loadLiveSessionData()
                }
                Toast.makeText(this, "🛑 Emulación desactivada. Modo oficial restaurado.", Toast.LENGTH_SHORT).show()
            }
        }

        updateSettingsFocusNavigation()
    }

    private fun updateSettingsFocusNavigation() {
        val hasDirectChannels = scrollDirectChannels.visibility == View.VISIBLE && llDynamicDirectPresets.childCount > 0
        val firstChannel = if (hasDirectChannels) llDynamicDirectPresets.getChildAt(0) else null
        val targetDownFromSourcesId = firstChannel?.id ?: R.id.btnToggleSimulationMode

        // 1. Header Reload Button (Top right)
        btnReloadGitSources.nextFocusUpId = R.id.btnTabSettings
        btnReloadGitSources.nextFocusLeftId = R.id.btnSourceFutbolLibre
        btnReloadGitSources.nextFocusRightId = R.id.btnReloadGitSources
        btnReloadGitSources.nextFocusDownId = targetDownFromSourcesId

        // 2. Source Row Buttons (FLX, Futbol Vivo, Futbol Libre)
        btnSourceFlx.nextFocusUpId = R.id.btnTabSettings
        btnSourceFlx.nextFocusLeftId = R.id.btnSourceFlx
        btnSourceFlx.nextFocusRightId = R.id.btnSourceFutbolVivo
        btnSourceFlx.nextFocusDownId = targetDownFromSourcesId

        btnSourceFutbolVivo.nextFocusUpId = R.id.btnTabSettings
        btnSourceFutbolVivo.nextFocusLeftId = R.id.btnSourceFlx
        btnSourceFutbolVivo.nextFocusRightId = R.id.btnSourceFutbolLibre
        btnSourceFutbolVivo.nextFocusDownId = targetDownFromSourcesId

        btnSourceFutbolLibre.nextFocusUpId = R.id.btnTabSettings
        btnSourceFutbolLibre.nextFocusLeftId = R.id.btnSourceFutbolVivo
        btnSourceFutbolLibre.nextFocusRightId = R.id.btnReloadGitSources
        btnSourceFutbolLibre.nextFocusDownId = targetDownFromSourcesId

        // Key listeners on source buttons for direct, reliable D-Pad navigation and smooth scrolling
        val sourceDownKeyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (hasDirectChannels && firstChannel != null) {
                    firstChannel.requestFocus()
                } else {
                    val targetY = if (::cardRaceSimulation.isInitialized) cardRaceSimulation.top else 1000
                    (layoutSettings as? ScrollView)?.smoothScrollTo(0, targetY)
                    btnToggleSimulationMode.requestFocus()
                }
                return@OnKeyListener true
            }
            false
        }
        btnSourceFlx.setOnKeyListener(sourceDownKeyListener)
        btnSourceFutbolVivo.setOnKeyListener(sourceDownKeyListener)
        btnSourceFutbolLibre.setOnKeyListener(sourceDownKeyListener)
        btnReloadGitSources.setOnKeyListener(sourceDownKeyListener)

        // 3. Dynamic Channel Presets (if visible)
        for (i in 0 until llDynamicDirectPresets.childCount) {
            val child = llDynamicDirectPresets.getChildAt(i)
            child.nextFocusUpId = R.id.btnSourceFlx
            child.nextFocusDownId = R.id.btnToggleSimulationMode
            child.nextFocusLeftId = if (i > 0) llDynamicDirectPresets.getChildAt(i - 1).id else R.id.btnSourceFlx
            child.nextFocusRightId = if (i < llDynamicDirectPresets.childCount - 1) llDynamicDirectPresets.getChildAt(i + 1).id else child.id

            child.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    val targetY = if (::cardRaceSimulation.isInitialized) cardRaceSimulation.top else 1000
                    (layoutSettings as? ScrollView)?.smoothScrollTo(0, targetY)
                    btnToggleSimulationMode.requestFocus()
                    return@setOnKeyListener true
                }
                false
            }
        }

        // 4. Race Simulation Button
        btnToggleSimulationMode.nextFocusUpId = firstChannel?.id ?: R.id.btnSourceFlx
        btnToggleSimulationMode.nextFocusDownId = R.id.btnToggleSimulationMode
        btnToggleSimulationMode.nextFocusLeftId = R.id.btnToggleSimulationMode
        btnToggleSimulationMode.nextFocusRightId = R.id.btnToggleSimulationMode

        btnToggleSimulationMode.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val targetY = if (::cardRaceSimulation.isInitialized) cardRaceSimulation.top else 1000
                (layoutSettings as? ScrollView)?.smoothScrollTo(0, targetY)
            }
        }

        btnToggleSimulationMode.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                (layoutSettings as? ScrollView)?.smoothScrollTo(0, 0)
                if (hasDirectChannels && firstChannel != null) {
                    firstChannel.requestFocus()
                } else {
                    btnSourceFlx.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun updateSimulationModeUI(enabled: Boolean) {
        if (enabled) {
            btnToggleSimulationMode.text = "🛑 Detener Emulación"
            btnToggleSimulationMode.setTextColor(Color.parseColor("#EF4444"))
            tvSimulationStatus.text = "Estado: 🟢 ACTIVO • Emulando Gran Premio en vivo con 22 pilotos"
            tvSimulationStatus.setTextColor(Color.parseColor("#22C55E"))
        } else {
            btnToggleSimulationMode.text = "▶️ Iniciar Emulación"
            btnToggleSimulationMode.setTextColor(Color.WHITE)
            tvSimulationStatus.text = "Estado: Desactivado (Esperando eventos reales de F1)"
            tvSimulationStatus.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun selectYear(year: Int) {
        selectedYear = year
        updateYearButtonHighlights()
        loadCalendar(year)
    }

    private fun updateYearButtonHighlights() {
        btnYear2024.isSelected = (selectedYear == 2024)
        btnYear2025.isSelected = (selectedYear == 2025)
        btnYear2026.isSelected = (selectedYear == 2026)
    }

    private fun displayMeetingDetails(meeting: Meeting) {
        selectedMeeting = meeting
        tvSelectedGpName.text = "🏁 ${meeting.meetingName ?: "Gran Premio"}"
        tvSelectedGpCircuit.text = "${meeting.officialName ?: ""}\n📍 ${meeting.location ?: ""}, ${meeting.countryName ?: ""}"
        tvSelectedGpDates.text = formatMeetingDates(meeting.dateStart, meeting.dateEnd)

        if (!meeting.circuitImage.isNullOrEmpty()) {
            ivCircuitTrackMap.visibility = View.VISIBLE
            ivCircuitTrackMap.load(meeting.circuitImage) { crossfade(true) }
        } else {
            ivCircuitTrackMap.visibility = View.GONE
        }

        updateSmartSearchQueries(meeting.meetingName ?: "Las Vegas", "prácticas")
        loadSessionsForMeeting(meeting.meetingKey)
    }

    private fun updateSmartSearchQueries(gpName: String, sessionType: String = "prácticas") {
        tvDetectedSessionTitle.text = "🏎️ Evento identificado: $gpName • ${sessionType.replaceFirstChar { it.uppercase() }}"
        val queries = VideoSourcesConfig.generateSmartSearchQueries(gpName, sessionType)
        val chips = listOf(tvSearchChip1, tvSearchChip2, tvSearchChip3, tvSearchChip4)
        chips.forEachIndexed { idx, chip ->
            if (idx < queries.size) {
                chip.text = queries[idx]
                chip.visibility = View.VISIBLE
                chip.setOnClickListener {
                    Toast.makeText(this, "Búsqueda sugerida: ${queries[idx]}", Toast.LENGTH_SHORT).show()
                }
            } else {
                chip.visibility = View.GONE
            }
        }
    }

    private fun formatMeetingDates(startIso: String?, endIso: String?): String {
        if (startIso == null) return "Fechas por confirmar"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val sDate = inputFormat.parse(startIso.substring(0, 19))
            val eDate = endIso?.let { inputFormat.parse(it.substring(0, 19)) }

            val outFormat = SimpleDateFormat("d 'de' MMMM", Locale("es", "ES"))
            val yearFormat = SimpleDateFormat("yyyy", Locale("es", "ES"))
            outFormat.timeZone = TimeZone.getTimeZone("America/Bogota")

            if (sDate != null && eDate != null) {
                "Fechas: ${outFormat.format(sDate)} al ${outFormat.format(eDate)} de ${yearFormat.format(eDate)} (Hora Colombia)"
            } else if (sDate != null) {
                "Fecha: ${outFormat.format(sDate)} (Hora Colombia)"
            } else "Fechas por confirmar"
        } catch (e: Exception) {
            "Fechas: ${startIso.take(10)}"
        }
    }

    private fun loadInitialData() {
        updateYearButtonHighlights()
    }

    private suspend fun loadLiveSessionData() {
        if (preferencesManager.isRaceSimulationEnabled) {
            hasLiveSessionForRefresh = false
            pitWallViewManager.setSimulationMode(true)
            return
        }
        try {
            val session = repository.getCurrentOrNextSession(selectedYear)
            if (session == null) {
                hasLiveSessionForRefresh = false
                pitWallViewManager.onScheduleUnavailable()
                pitWallViewManager.leaderboardAdapter.updateData(emptyList())
                tvRaceFlag.text = "🏁 CALENDARIO NO DISPONIBLE"
                tvRaceFlag.setTextColor(Color.parseColor("#9CA3AF"))
                tvWeather.text = "⛅ Pista: -- | Aire: --"
                return
            }

            val now = Date()
            val start = TimeUtils.parseUtc(session.dateStart)
            val isLive = TimeUtils.isCurrentlyLive(session.dateStart, session.dateEnd)
            hasLiveSessionForRefresh = isLive
            val isUpcoming = start?.after(now) == true
            val venue = session.circuitShortName ?: session.countryName ?: "F1"
            val title = "$venue • ${session.sessionName ?: "Sesión"}"
            val meeting = repository.getMeetings(selectedYear).find { it.meetingKey == session.meetingKey }
            val circuitImg = meeting?.circuitImage?.takeIf { it.isNotBlank() }
                ?: repository.getCircuitSilhouetteUrl(session.circuitShortName.orEmpty(), session.countryName.orEmpty())

            val formattedDate = TimeUtils.formatToColombiaTime(session.dateStart, "d 'de' MMMM • hh:mm a")
            val countdown = TimeUtils.calculateCountdown(session.dateStart)
            val dateInfo = if (countdown.isNotBlank()) "$formattedDate ($countdown)" else formattedDate

            pitWallViewManager.onSessionUpdated(
                sessionKey = session.sessionKey,
                isLive = isLive,
                isUpcoming = isUpcoming,
                sessionTitleText = title,
                sessionType = session.sessionType ?: session.sessionName,
                circuitImageUrl = circuitImg,
                formattedSessionDate = dateInfo
            )

            if (!isLive) {
                val isHeadingToRace = session.sessionName?.contains("Race", ignoreCase = true) == true
                        || session.sessionName?.contains("Carrera", ignoreCase = true) == true
                        || session.sessionType?.equals("Race", ignoreCase = true) == true

                var startingGridItems: List<DriverLeaderboardItem> = emptyList()
                if (isHeadingToRace) {
                    startingGridItems = repository.getStartingGrid(selectedYear)
                }

                if (startingGridItems.isNotEmpty()) {
                    pitWallViewManager.leaderboardAdapter.updateData(startingGridItems)
                    tvRaceFlag.text = "🏁 SALIDA EN CARRERA"
                    tvRaceFlag.setTextColor(Color.parseColor("#38BDF8"))
                    tvWeather.text = "🏎️ PARRILLA DEFINIDA"
                    val gridTitle = "${session.circuitShortName ?: "F1"} • PARRILLA / SALIDA EN CARRERA"
                    pitWallViewManager.onSessionUpdated(
                        sessionKey = session.sessionKey,
                        isLive = false,
                        isUpcoming = true,
                        sessionTitleText = gridTitle,
                        sessionType = "Race",
                        circuitImageUrl = circuitImg,
                        formattedSessionDate = dateInfo
                    )
                    return
                }

                val standings = repository.getDriverStandings(selectedYear)
                if (standings.isNotEmpty()) {
                    val leaderPts = standings.firstOrNull()?.points?.toDoubleOrNull() ?: 0.0
                    val fallbackItems = standings.map { st ->
                        val driverPts = st.points.toDoubleOrNull() ?: 0.0
                        val diff = leaderPts - driverPts
                        val diffFormatted = if (diff % 1.0 == 0.0) "${diff.toInt()}" else String.format(Locale.US, "%.1f", diff)
                        val diffText = if (st.position == 1) "LÍDER" else "-$diffFormatted PTS"
                        val ptsText = "${st.points} PTS"

                        DriverLeaderboardItem(
                            driverNumber = st.driverNumber,
                            position = st.position,
                            acronym = st.acronym,
                            fullName = st.fullName,
                            teamName = st.teamName,
                            teamColor = st.teamColor,
                            gapToLeader = ptsText,
                            interval = diffText,
                            lastLap = diffText,
                            s1 = "--",
                            s2 = "--",
                            s3 = "--",
                            tyreCompound = "SOFT",
                            tyreAge = 1
                        )
                    }
                    pitWallViewManager.leaderboardAdapter.updateData(fallbackItems)
                } else {
                    pitWallViewManager.leaderboardAdapter.updateData(emptyList())
                }
                tvRaceFlag.text = if (isUpcoming) "🏁 PRÓXIMA FECHA: $formattedDate" else "🏁 SESIÓN NO ACTIVA"
                tvRaceFlag.setTextColor(Color.parseColor("#9CA3AF"))
                tvWeather.text = if (isUpcoming) "⏳ $countdown" else "⛅ Pista: -- | Aire: --"
                return
            }

            tvRaceFlag.visibility = View.VISIBLE
            tvRaceFlag.text = "🏁 SESIÓN ACTIVA"
            tvRaceFlag.setTextColor(Color.parseColor("#22C55E"))
            tvWeather.text = "⛅ Pista: -- | Aire: --"

            val weather = api.getLatestWeather(session.sessionKey)
            weather?.let { w ->
                tvWeather.text = "⛅ Pista: ${w.trackTemp ?: 0}°C | Aire: ${w.airTemp ?: 0}°C"
            }

            val raceControl = api.getLatestRaceControl(session.sessionKey)
            raceControl?.let { rc ->
                tvRaceFlag.text = "🏁 ${rc.flag ?: "ESTADO"}: ${rc.message ?: "NORMAL"}"
                when {
                    rc.flag.equals("YELLOW", true) -> tvRaceFlag.setTextColor(Color.parseColor("#EAB308"))
                    rc.flag.equals("RED", true) -> tvRaceFlag.setTextColor(Color.parseColor("#EF4444"))
                    else -> tvRaceFlag.setTextColor(Color.parseColor("#22C55E"))
                }
            }

            val drivers = api.getDrivers(session.sessionKey)
            val positions = api.getLatestPositions(session.sessionKey)
            val intervals = if (
                OpenF1SessionPolicy.supportsIntervals(session.sessionType, session.sessionName)
            ) {
                api.getLatestIntervals(session.sessionKey)
            } else {
                emptyList()
            }
            val laps = api.getLatestLaps(session.sessionKey)
            val stints = api.getStints(session.sessionKey)

            val items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
            pitWallViewManager.leaderboardAdapter.updateData(items)

            updateLiveOpenF1Status(
                status = api.getAccessStatus(),
                hasTimingData = items.isNotEmpty()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pitWallViewManager.onScheduleUnavailable()
        }
    }

    private fun updateLiveOpenF1Status(
        status: OpenF1AccessStatus,
        hasTimingData: Boolean
    ) {
        val message = when (status) {
            OpenF1AccessStatus.AUTHENTICATION_REQUIRED ->
                "🔒 OPENF1 LIVE: REQUIERE SUSCRIPCIÓN Y AUTENTICACIÓN"
            OpenF1AccessStatus.RATE_LIMITED ->
                "⏳ OPENF1: LÍMITE DE SOLICITUDES ALCANZADO"
            OpenF1AccessStatus.SERVICE_UNAVAILABLE ->
                "⚠️ OPENF1: SERVICIO NO DISPONIBLE"
            OpenF1AccessStatus.NETWORK_ERROR ->
                "⚠️ OPENF1: SIN CONEXIÓN CON OPENF1"
            OpenF1AccessStatus.REQUEST_FAILED ->
                "⚠️ OPENF1: SOLICITUD RECHAZADA"
            OpenF1AccessStatus.UNKNOWN, OpenF1AccessStatus.AVAILABLE ->
                if (!hasTimingData) "⏳ SESIÓN ACTIVA • ESPERANDO PRIMEROS DATOS" else null
        } ?: return

        tvRaceFlag.visibility = View.VISIBLE
        tvRaceFlag.text = message
        tvRaceFlag.setTextColor(
            when (status) {
                OpenF1AccessStatus.AUTHENTICATION_REQUIRED,
                OpenF1AccessStatus.RATE_LIMITED -> Color.parseColor("#F59E0B")
                OpenF1AccessStatus.SERVICE_UNAVAILABLE,
                OpenF1AccessStatus.NETWORK_ERROR,
                OpenF1AccessStatus.REQUEST_FAILED -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#38BDF8")
            }
        )
        tvWeather.text = when (status) {
            OpenF1AccessStatus.AUTHENTICATION_REQUIRED ->
                "Los datos en vivo requieren un plan Live y un token Bearer válido."
            OpenF1AccessStatus.RATE_LIMITED ->
                "OpenF1 limitó temporalmente las consultas. Se reintentará automáticamente."
            OpenF1AccessStatus.SERVICE_UNAVAILABLE ->
                "OpenF1 no está respondiendo. Se reintentará automáticamente."
            OpenF1AccessStatus.NETWORK_ERROR ->
                "Comprueba la conexión de red del televisor."
            OpenF1AccessStatus.REQUEST_FAILED ->
                "OpenF1 rechazó una consulta. Revisa el acceso de la sesión."
            else ->
                "Todavía no se reciben posiciones o vueltas de esta sesión."
        }

        if (status != OpenF1AccessStatus.UNKNOWN && status != OpenF1AccessStatus.AVAILABLE) {
            pitWallViewManager.showOpenF1AccessStatus(status)
        }
    }

    private fun buildLeaderboardItems(
        drivers: List<Driver>,
        positions: List<Position>,
        intervals: List<Interval>,
        laps: List<Lap>,
        stints: List<Stint>
    ): List<DriverLeaderboardItem> {
        val driverMap = drivers.associateBy { it.driverNumber }
        val intervalMap = intervals.associateBy { it.driverNumber }
        val lapMap = laps.associateBy { it.driverNumber }
        val stintMap = stints.associateBy { it.driverNumber }

        val sortedPositions = if (positions.isNotEmpty()) {
            positions.sortedBy { it.position }
        } else {
            drivers.mapIndexed { idx, d -> Position(null, d.driverNumber, idx + 1, 0) }
        }

        return sortedPositions.map { pos ->
            val driver = driverMap[pos.driverNumber]
            val interval = intervalMap[pos.driverNumber]
            val lap = lapMap[pos.driverNumber]
            val stint = stintMap[pos.driverNumber]

            val gapStr = if (pos.position == 1) "LÍDER" else {
                interval?.gapToLeader?.toString()?.takeIf { it.isNotBlank() }?.let { "+${it}s" } ?: "—"
            }
            val intervalStr = interval?.interval?.toString()?.takeIf { it.isNotBlank() }?.let { "+${it}s" } ?: "—"
            val lastLapStr = lap?.lapDuration?.let { formatLapDuration(it) } ?: "--:--.---"

            DriverLeaderboardItem(
                driverNumber = pos.driverNumber,
                position = pos.position,
                acronym = driver?.acronym ?: "#${pos.driverNumber}",
                fullName = driver?.fullName ?: "Driver ${pos.driverNumber}",
                teamName = driver?.teamName ?: "",
                teamColor = driver?.teamColour ?: "E10600",
                gapToLeader = gapStr,
                interval = intervalStr,
                lastLap = lastLapStr,
                s1 = lap?.s1?.toString() ?: "-",
                s2 = lap?.s2?.toString() ?: "-",
                s3 = lap?.s3?.toString() ?: "-",
                tyreCompound = stint?.compound ?: "MEDIUM",
                tyreAge = stint?.tyreAgeAtStart ?: 1
            )
        }
    }

    private fun formatLapDuration(duration: Double): String {
        val minutes = (duration / 60).toInt()
        val seconds = duration % 60
        return String.format("%d:%06.3f", minutes, seconds)
    }

    private fun loadCalendar(year: Int) {
        lifecycleScope.launch {
            try {
                val meetings = repository.getMeetings(year)
                meetingAdapter.updateData(meetings)

                if (meetings.isEmpty()) {
                    selectedMeeting = null
                    sessionScheduleAdapter.updateData(emptyList())
                    tvSelectedGpName.text = "Calendario F1 $year no disponible"
                    tvSelectedGpCircuit.text = "No se recibieron eventos desde OpenF1. Comprueba la conexión y vuelve a entrar en Carreras y Archivo."
                    tvSelectedGpDates.text = "Temporada $year • Datos pendientes"
                    ivCircuitTrackMap.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "No fue posible cargar el calendario F1 $year", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val targetMeeting = if (selectedMeeting != null && selectedMeeting!!.year == year) {
                    selectedMeeting!!
                } else {
                    repository.findSmartSelectedMeeting(year) ?: meetings.first()
                }

                displayMeetingDetails(targetMeeting)

                val index = meetings.indexOfFirst { it.meetingKey == targetMeeting.meetingKey }
                if (index >= 0) {
                    rvMeetings.scrollToPosition(index)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error cargando el calendario F1: ${e.localizedMessage ?: "red"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSessionsForMeeting(meetingKey: Long) {
        lifecycleScope.launch {
            try {
                val sessions = repository.getSessions(meetingKey, selectedYear).sortedBy { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }
                sessionScheduleAdapter.updateData(sessions)

                if (sessions.isEmpty()) {
                    val currentText = tvSelectedGpCircuit.text.toString()
                    if (!currentText.contains("sesiones aún no publicadas", true)) {
                        tvSelectedGpCircuit.text = "$currentText\nSesiones aún no publicadas por el proveedor."
                    }
                    return@launch
                }

                val raceSession = sessions.find { it.sessionType.equals("Race", true) } ?: sessions.lastOrNull()
                raceSession?.let { s ->
                    val round = (selectedMeeting?.circuitKey ?: 1)
                    loadHistoricalSession(s.sessionKey, round)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadHistoricalSession(sessionKey: Long, round: Int = 1) {
        lifecycleScope.launch {
            try {
                val drivers = api.getDrivers(sessionKey)
                val positions = api.getLatestPositions(sessionKey)
                val intervals = api.getLatestIntervals(sessionKey)
                val laps = api.getLatestLaps(sessionKey)
                val stints = api.getStints(sessionKey)

                var items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                if (items.isEmpty()) {
                    val r = repository.getRaceResults(selectedYear, round)
                    if (r.isNotEmpty()) {
                        items = r.mapIndexed { idx, res ->
                            val pos = res.position?.toIntOrNull() ?: (idx + 1)
                            val driverNum = res.number?.toIntOrNull() ?: (idx + 1)
                            val acronym = res.driver?.code ?: res.driver?.familyName?.take(3)?.uppercase() ?: "F1"
                            val team = res.constructor?.name ?: "Equipo F1"
                            val teamColor = repository.getTeamColor(team)
                            DriverLeaderboardItem(
                                driverNumber = driverNum,
                                position = pos,
                                acronym = acronym,
                                fullName = res.driver?.fullName ?: "Piloto F1",
                                teamName = team,
                                teamColor = teamColor,
                                gapToLeader = if (pos == 1) "GANADOR" else (res.time?.time ?: "+${res.points ?: "0"} pts"),
                                interval = "${res.points ?: "0"} pts",
                                lastLap = res.time?.time ?: "--",
                                s1 = "--",
                                s2 = "--",
                                s3 = "--",
                                tyreCompound = "HARD",
                                tyreAge = 1
                            )
                        }
                    }
                }
                archiveResultsAdapter.updateData(items)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pitWallViewManager.onResume()
        sessionRefreshJob?.cancel()
        sessionRefreshJob = lifecycleScope.launch {
            while (isActive) {
                loadLiveSessionData()
                delay(if (hasLiveSessionForRefresh) 30_000L else 60_000L)
            }
        }
    }

    override fun onPause() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
        super.onPause()
        pitWallViewManager.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                    if (::layoutPitWall.isInitialized) {
                        if (layoutPitWall.visibility != View.VISIBLE) {
                            btnTabPitWall.performClick()
                        }
                        pitWallViewManager.cycleTelemetryMode(forward = false)
                        return true
                    }
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    if (::layoutPitWall.isInitialized) {
                        if (layoutPitWall.visibility != View.VISIBLE) {
                            btnTabPitWall.performClick()
                        }
                        pitWallViewManager.cycleTelemetryMode(forward = true)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (layoutPitWall.visibility == View.VISIBLE && pitWallViewManager.handleBackPressed()) {
            return
        }
        if (layoutHome.visibility != View.VISIBLE) {
            btnTabHome.performClick()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pitWallViewManager.onDestroy()
    }
}
