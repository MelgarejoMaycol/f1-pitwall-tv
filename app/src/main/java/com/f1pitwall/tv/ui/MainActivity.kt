package com.f1pitwall.tv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.config.VideoSourcesConfig
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.ui.home.HomeViewManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private var radioPlayer: ExoPlayer? = null

    // Tabs
    private lateinit var btnTabHome: Button
    private lateinit var btnTabPitWall: Button
    private lateinit var btnTabArchive: Button
    private lateinit var btnTabSettings: Button

    // Layout Containers
    private lateinit var layoutHome: View
    private lateinit var layoutPitWall: LinearLayout
    private lateinit var layoutArchive: LinearLayout
    private lateinit var layoutSettings: LinearLayout

    // F1 Repository & Home Manager
    private val api = OpenF1ApiClient()
    private val repository by lazy { F1Repository(api) }
    private lateinit var homeViewManager: HomeViewManager

    // Pit Wall UI
    private lateinit var tvRaceFlag: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvSessionTitle: TextView
    private lateinit var btnRefreshLive: Button
    private lateinit var rvLeaderboard: RecyclerView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var tvRadioTitle: TextView
    private lateinit var btnPlayRadio: Button

    // F1 Content Space Controls (Off-Race Hub)
    private lateinit var btnContentPreview: Button
    private lateinit var btnContentRecap: Button
    private lateinit var btnContentBoxes: Button

    // Team Radio Driver Selector
    private lateinit var btnRadioVer: Button
    private lateinit var btnRadioNor: Button
    private lateinit var btnRadioLec: Button
    private lateinit var btnRadioHam: Button
    private lateinit var btnRadioRus: Button
    private lateinit var btnRadioPia: Button
    private lateinit var btnRadioAlo: Button

    // Live Alert Banner (desacoplado y legítimo)
    private lateinit var layoutLiveSessionAlert: LinearLayout
    private lateinit var tvLiveSessionAlertTitle: TextView
    private lateinit var btnLiveSessionAction: Button

    // Telemetry Panel UI
    private lateinit var btnMetricsTiming: Button
    private lateinit var btnMetricsTelemetry: Button
    private lateinit var layoutTelemetryPanel: LinearLayout
    private lateinit var tvTelemetryDriverHeader: TextView
    private lateinit var tvTelemetryDrs: TextView
    private lateinit var tvTelemetrySpeed: TextView
    private lateinit var tvTelemetryGear: TextView
    private lateinit var tvTelemetryRpm: TextView
    private lateinit var pbTelemetryRpm: ProgressBar
    private lateinit var tvTelemetryThrottle: TextView
    private lateinit var pbTelemetryThrottle: ProgressBar
    private lateinit var tvTelemetryBrake: TextView
    private lateinit var pbTelemetryBrake: ProgressBar
    private lateinit var btnTelemetryDemoToggle: Button

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
    private lateinit var tvDetectedSessionTitle: TextView
    private lateinit var tvSearchChip1: TextView
    private lateinit var tvSearchChip2: TextView
    private lateinit var tvSearchChip3: TextView
    private lateinit var tvSearchChip4: TextView
    private lateinit var etStreamUrl: EditText
    private lateinit var btnApplyStream: Button
    private lateinit var btnPresetDemo1: Button
    private lateinit var btnPresetDemo2: Button
    private lateinit var btnReloadGitSources: Button

    private var currentSessionKey: Long? = null
    private var latestRadioUrl: String? = null
    private var selectedMeeting: Meeting? = null
    private var selectedYear: Int = AppConfig.DEFAULT_SEASON // 2026 por defecto
    private var isLiveSessionActive: Boolean = false

    // Demo stream presets
    private val demoHlsUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private val demoMp4Url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initExoPlayer()
        setupNavigation()
        setupLeaderboards()
        setupTelemetryView()
        loadInitialData()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        btnTabHome = findViewById(R.id.btnTabHome)
        btnTabPitWall = findViewById(R.id.btnTabPitWall)
        btnTabArchive = findViewById(R.id.btnTabArchive)
        btnTabSettings = findViewById(R.id.btnTabSettings)

        layoutHome = findViewById(R.id.layoutHome)
        layoutPitWall = findViewById(R.id.layoutPitWall)
        layoutArchive = findViewById(R.id.layoutArchive)
        layoutSettings = findViewById(R.id.layoutSettings)

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
                    "estadisticas" -> {
                        btnTabPitWall.performClick()
                        btnMetricsTelemetry.performClick()
                    }
                    else -> {
                        Toast.makeText(this, "Sección '$hubId' disponible", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        // Pit Wall Header & Controls
        tvRaceFlag = findViewById(R.id.tvRaceFlag)
        tvWeather = findViewById(R.id.tvWeather)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        btnRefreshLive = findViewById(R.id.btnRefreshLive)
        rvLeaderboard = findViewById(R.id.rvLeaderboard)
        tvRadioTitle = findViewById(R.id.tvRadioTitle)
        btnPlayRadio = findViewById(R.id.btnPlayRadio)

        // F1 Content Hub Controls
        btnContentPreview = findViewById(R.id.btnContentPreview)
        btnContentRecap = findViewById(R.id.btnContentRecap)
        btnContentBoxes = findViewById(R.id.btnContentBoxes)

        // Team Radio Driver Selector
        btnRadioVer = findViewById(R.id.btnRadioVer)
        btnRadioNor = findViewById(R.id.btnRadioNor)
        btnRadioLec = findViewById(R.id.btnRadioLec)
        btnRadioHam = findViewById(R.id.btnRadioHam)
        btnRadioRus = findViewById(R.id.btnRadioRus)
        btnRadioPia = findViewById(R.id.btnRadioPia)
        btnRadioAlo = findViewById(R.id.btnRadioAlo)

        setupContentHubAndRadios()

        layoutLiveSessionAlert = findViewById(R.id.layoutLiveSessionAlert)
        tvLiveSessionAlertTitle = findViewById(R.id.tvLiveSessionAlertTitle)
        btnLiveSessionAction = findViewById(R.id.btnLiveSessionAction)

        // Telemetry
        btnMetricsTiming = findViewById(R.id.btnMetricsTiming)
        btnMetricsTelemetry = findViewById(R.id.btnMetricsTelemetry)
        layoutTelemetryPanel = findViewById(R.id.layoutTelemetryPanel)
        tvTelemetryDriverHeader = findViewById(R.id.tvTelemetryDriverHeader)
        tvTelemetryDrs = findViewById(R.id.tvTelemetryDrs)
        tvTelemetrySpeed = findViewById(R.id.tvTelemetrySpeed)
        tvTelemetryGear = findViewById(R.id.tvTelemetryGear)
        tvTelemetryRpm = findViewById(R.id.tvTelemetryRpm)
        pbTelemetryRpm = findViewById(R.id.pbTelemetryRpm)
        tvTelemetryThrottle = findViewById(R.id.tvTelemetryThrottle)
        pbTelemetryThrottle = findViewById(R.id.pbTelemetryThrottle)
        tvTelemetryBrake = findViewById(R.id.tvTelemetryBrake)
        pbTelemetryBrake = findViewById(R.id.pbTelemetryBrake)
        btnTelemetryDemoToggle = findViewById(R.id.btnTelemetryDemoToggle)

        // Archive / Calendar
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

        // Settings & Smart Search
        tvDetectedSessionTitle = findViewById(R.id.tvDetectedSessionTitle)
        tvSearchChip1 = findViewById(R.id.tvSearchChip1)
        tvSearchChip2 = findViewById(R.id.tvSearchChip2)
        tvSearchChip3 = findViewById(R.id.tvSearchChip3)
        tvSearchChip4 = findViewById(R.id.tvSearchChip4)
        etStreamUrl = findViewById(R.id.etStreamUrl)
        btnApplyStream = findViewById(R.id.btnApplyStream)
        btnPresetDemo1 = findViewById(R.id.btnPresetDemo1)
        btnPresetDemo2 = findViewById(R.id.btnPresetDemo2)
        btnReloadGitSources = findViewById(R.id.btnReloadGitSources)

        btnLiveSessionAction.setOnClickListener {
            btnTabSettings.performClick()
        }
    }

    private fun setupTelemetryView() {
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
            loadDriverTelemetry(1, forceDemo = true)
        }
    }

    private fun loadDriverTelemetry(driverNumber: Int, forceDemo: Boolean = false) {
        lifecycleScope.launch {
            try {
                val key = if (forceDemo || currentSessionKey == null) 9472L else currentSessionKey!!
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
                    // Estado informativo sin datos simulados falsos
                    tvTelemetrySpeed.text = "0"
                    tvTelemetryGear.text = "N"
                    tvTelemetryRpm.text = "Motor en reposo"
                    pbTelemetryRpm.progress = 0
                    tvTelemetryThrottle.text = "Acelerador: 0%"
                    pbTelemetryThrottle.progress = 0
                    tvTelemetryBrake.text = "Freno: 0%"
                    pbTelemetryBrake.progress = 0
                    tvTelemetryDrs.text = "DRS OFF"
                    tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    Toast.makeText(this@MainActivity, "Sin telemetría en vivo en este momento. Pulsa 'Cargar Datos Oficiales' para ver telemetría de sesión.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        // El reproductor inicia vacío: solo debe reproducir una fuente configurada por el usuario.

        radioPlayer = ExoPlayer.Builder(this).build()

        btnPlayRadio.setOnClickListener {
            latestRadioUrl?.let { url ->
                val mediaItem = MediaItem.fromUri(url)
                radioPlayer?.setMediaItem(mediaItem)
                radioPlayer?.prepare()
                radioPlayer?.play()
                Toast.makeText(this, "Reproduciendo audio de radio...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "No hay audio de radio disponible en este momento", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playStream(url: String) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun setupContentHubAndRadios() {
        // 1. Off-race F1 Content Hub
        btnContentPreview.setOnClickListener {
            playStream("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            tvLiveSessionAlertTitle.text = "🏎️ F1 PitWall: Previa Oficial GP Las Vegas 2026 (Ronda 21)"
            Toast.makeText(this, "Cargando Previa GP Las Vegas 2026", Toast.LENGTH_SHORT).show()
        }

        btnContentRecap.setOnClickListener {
            playStream("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4")
            tvLiveSessionAlertTitle.text = "🏁 F1 PitWall: Resumen Oficial GP São Paulo (Norris 1º, Verstappen 2º)"
            Toast.makeText(this, "Cargando Resumen GP São Paulo", Toast.LENGTH_SHORT).show()
        }

        btnContentBoxes.setOnClickListener {
            playStream("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            tvLiveSessionAlertTitle.text = "📡 F1 PitWall: Feed Técnico de Boxes y Telemetría en Vivo"
            Toast.makeText(this, "Cargando Feed Técnico de Pista", Toast.LENGTH_SHORT).show()
        }

        // 2. Team Radio Multi-Driver Selector (Requirement 10)
        btnRadioVer.setOnClickListener { tuneRadioDriver(1, "Max Verstappen") }
        btnRadioNor.setOnClickListener { tuneRadioDriver(4, "Lando Norris") }
        btnRadioLec.setOnClickListener { tuneRadioDriver(16, "Charles Leclerc") }
        btnRadioHam.setOnClickListener { tuneRadioDriver(44, "Lewis Hamilton") }
        btnRadioRus.setOnClickListener { tuneRadioDriver(63, "George Russell") }
        btnRadioPia.setOnClickListener { tuneRadioDriver(81, "Oscar Piastri") }
        btnRadioAlo.setOnClickListener { tuneRadioDriver(14, "Fernando Alonso") }
    }

    private fun tuneRadioDriver(driverNumber: Int, driverName: String) {
        if (!isLiveSessionActive || currentSessionKey == null) {
            latestRadioUrl = null
            radioPlayer?.stop()
            tvRadioTitle.text = "Radio desconectada: no hay sesión en directo"
            Toast.makeText(this, "La radio solo está disponible durante una sesión en directo.", Toast.LENGTH_SHORT).show()
            return
        }
        tvRadioTitle.text = "📻 Sintonizando Radio: $driverName (#$driverNumber)..."
        lifecycleScope.launch {
            try {
                val key = currentSessionKey ?: 9472L
                val radio = repository.getDriverTeamRadio(key, driverNumber)
                if (radio != null && !radio.recordingUrl.isNullOrEmpty()) {
                    latestRadioUrl = radio.recordingUrl
                    tvRadioTitle.text = "📻 Radio Oficial: $driverName (#$driverNumber) • Conectado"
                    val mediaItem = MediaItem.fromUri(radio.recordingUrl)
                    radioPlayer?.setMediaItem(mediaItem)
                    radioPlayer?.prepare()
                    radioPlayer?.play()
                    Toast.makeText(this@MainActivity, "Reproduciendo radio de $driverName", Toast.LENGTH_SHORT).show()
                } else {
                    tvRadioTitle.text = "📻 Radio: $driverName (#$driverNumber) [Canal en espera]"
                    Toast.makeText(this@MainActivity, "Sin transmisión de radio activa para $driverName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupNavigation() {
        btnTabHome.setOnClickListener {
            showLayout(layoutHome)
            btnTabHome.isSelected = true
            btnTabPitWall.isSelected = false
            btnTabArchive.isSelected = false
            btnTabSettings.isSelected = false
        }

        btnTabPitWall.setOnClickListener {
            showLayout(layoutPitWall)
            btnTabHome.isSelected = false
            btnTabPitWall.isSelected = true
            btnTabArchive.isSelected = false
            btnTabSettings.isSelected = false

            if (!isLiveSessionActive) {
                tvLiveSessionAlertTitle.text = "⏸️ Sin carrera en vivo • Próxima: GP Las Vegas (Jue 19 nov, 7:30 PM Col)"
                btnLiveSessionAction.text = "⚡ Señal y Demostración"
            }
        }

        btnTabArchive.setOnClickListener {
            showLayout(layoutArchive)
            btnTabHome.isSelected = false
            btnTabPitWall.isSelected = false
            btnTabArchive.isSelected = true
            btnTabSettings.isSelected = false
            loadCalendar(selectedYear)
        }

        btnTabSettings.setOnClickListener {
            showLayout(layoutSettings)
            btnTabHome.isSelected = false
            btnTabPitWall.isSelected = false
            btnTabArchive.isSelected = false
            btnTabSettings.isSelected = true
        }

        btnApplyStream.setOnClickListener {
            val url = etStreamUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                playStream(url)
                Toast.makeText(this, "Señal actualizada", Toast.LENGTH_SHORT).show()
                btnTabPitWall.performClick()
            }
        }

        btnPresetDemo1.setOnClickListener {
            etStreamUrl.setText(demoHlsUrl)
            playStream(demoHlsUrl)
            Toast.makeText(this, "Cargado Demo 1 (HLS)", Toast.LENGTH_SHORT).show()
        }

        btnPresetDemo2.setOnClickListener {
            etStreamUrl.setText(demoMp4Url)
            playStream(demoMp4Url)
            Toast.makeText(this, "Cargado Demo 2 (MP4)", Toast.LENGTH_SHORT).show()
        }

        btnReloadGitSources.setOnClickListener {
            val providers = VideoSourcesConfig.loadProviders(this)
            Toast.makeText(this, "Fuentes Git recargadas (${providers.size} proveedores disponibles)", Toast.LENGTH_SHORT).show()
        }

        btnRefreshLive.setOnClickListener {
            loadLiveSessionData()
        }

        // Year Buttons
        btnYear2024.setOnClickListener { selectYear(2024) }
        btnYear2025.setOnClickListener { selectYear(2025) }
        btnYear2026.setOnClickListener { selectYear(2026) }
        updateYearButtonHighlights()

        // Schedule vs Results Buttons
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

        btnTabHome.isSelected = true
        btnTabHome.requestFocus()
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

    private fun showLayout(target: View) {
        layoutHome.visibility = if (target == layoutHome) View.VISIBLE else View.GONE
        layoutPitWall.visibility = if (target == layoutPitWall) View.VISIBLE else View.GONE
        layoutArchive.visibility = if (target == layoutArchive) View.VISIBLE else View.GONE
        layoutSettings.visibility = if (target == layoutSettings) View.VISIBLE else View.GONE
    }

    private fun setupLeaderboards() {
        rvLeaderboard.layoutManager = LinearLayoutManager(this)
        leaderboardAdapter = LeaderboardAdapter { item ->
            tvTelemetryDriverHeader.text = "#${item.driverNumber} ${item.fullName} • ${item.teamName}"
            btnMetricsTelemetry.performClick()
            loadDriverTelemetry(item.driverNumber, forceDemo = true)
        }
        rvLeaderboard.adapter = leaderboardAdapter

        rvArchiveResults.layoutManager = LinearLayoutManager(this)
        archiveResultsAdapter = LeaderboardAdapter()
        rvArchiveResults.adapter = archiveResultsAdapter

        rvSessionsSchedule.layoutManager = LinearLayoutManager(this)
        sessionScheduleAdapter = SessionScheduleAdapter { session ->
            loadHistoricalSession(session.sessionKey)
            btnViewResults.performClick()
        }
        rvSessionsSchedule.adapter = sessionScheduleAdapter

        rvMeetings.layoutManager = LinearLayoutManager(this)
        meetingAdapter = MeetingAdapter { meeting ->
            displayMeetingDetails(meeting)
        }
        rvMeetings.adapter = meetingAdapter
    }

    private fun displayMeetingDetails(meeting: Meeting) {
        selectedMeeting = meeting
        tvSelectedGpName.text = "🏁 ${meeting.meetingName ?: "Gran Premio"}"
        tvSelectedGpCircuit.text = "${meeting.officialName ?: ""}\n📍 ${meeting.location ?: ""}, ${meeting.countryName ?: ""}"
        tvSelectedGpDates.text = formatMeetingDates(meeting.dateStart, meeting.dateEnd)

        // Load Official Circuit Image
        if (!meeting.circuitImage.isNullOrEmpty()) {
            ivCircuitTrackMap.visibility = View.VISIBLE
            ivCircuitTrackMap.load(meeting.circuitImage) {
                crossfade(true)
            }
        } else {
            ivCircuitTrackMap.visibility = View.GONE
        }

        // Actualizar búsqueda adaptativa en ajustes
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
        loadLiveSessionData()
        updateYearButtonHighlights()
    }

    private fun loadLiveSessionData() {
        lifecycleScope.launch {
            try {
                btnRefreshLive.text = "⏳"
                val session = api.getLatestSession()
                if (session != null) {
                    currentSessionKey = session.sessionKey
                    tvSessionTitle.text = "${session.circuitShortName ?: "F1"} - ${session.sessionName ?: "Sesión"}"

                    val isLive = TimeUtils.isCurrentlyLive(session.dateStart, session.dateEnd)
                    isLiveSessionActive = isLive

                    if (isLive) {
                        tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: ${session.circuitShortName} - ${session.sessionName}"
                        btnLiveSessionAction.text = "⚡ Ver Señal en Vivo"
                    } else {
                        tvLiveSessionAlertTitle.text = "⏸️ Sin sesión en curso • Datos oficiales disponibles"
                        btnLiveSessionAction.text = "⚡ Señal de Prueba"
                    }

                    val drivers = api.getDrivers(session.sessionKey)
                    val positions = api.getLatestPositions(session.sessionKey)
                    val intervals = api.getLatestIntervals(session.sessionKey)
                    val laps = api.getLatestLaps(session.sessionKey)
                    val stints = api.getStints(session.sessionKey)
                    val radios = api.getTeamRadios(session.sessionKey)
                    val weather = api.getLatestWeather(session.sessionKey)
                    val raceControl = api.getLatestRaceControl(session.sessionKey)

                    weather?.let { w ->
                        tvWeather.text = "⛅ Pista: ${w.trackTemp ?: 0}°C | Aire: ${w.airTemp ?: 0}°C"
                    }
                    raceControl?.let { rc ->
                        tvRaceFlag.text = "🏁 ${rc.flag ?: "ESTADO"}: ${rc.message ?: "NORMAL"}"
                        if (rc.flag.equals("YELLOW", true)) {
                            tvRaceFlag.setTextColor(Color.parseColor("#EAB308"))
                        } else if (rc.flag.equals("RED", true)) {
                            tvRaceFlag.setTextColor(Color.parseColor("#EF4444"))
                        } else {
                            tvRaceFlag.setTextColor(Color.parseColor("#22C55E"))
                        }
                    }

                    radios.firstOrNull()?.let { r ->
                        latestRadioUrl = r.recordingUrl
                        val driver = drivers.find { it.driverNumber == r.driverNumber }
                        tvRadioTitle.text = "📻 Radio: #${r.driverNumber} ${driver?.broadcastName ?: "Piloto"}"
                    }

                    val items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                    leaderboardAdapter.updateData(items)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                btnRefreshLive.text = "🔄"
            }
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
                interval?.gapToLeader?.toString()?.let { "+${it}s" } ?: "+--"
            }
            val intervalStr = interval?.interval?.toString()?.let { "+${it}s" } ?: ""

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

                if (meetings.isNotEmpty()) {
                    // Selección inteligente: No seleccionar siempre Australia (index 0).
                    // Seleccionar el Gran Premio activo o próximo (en 2026: Las Vegas GP)
                    val targetMeeting = if (selectedMeeting != null && selectedMeeting!!.year == year) {
                        selectedMeeting!!
                    } else {
                        repository.findSmartSelectedMeeting(year) ?: meetings.first()
                    }

                    displayMeetingDetails(targetMeeting)

                    // Desplazar la lista al Gran Premio seleccionado
                    val index = meetings.indexOfFirst { it.meetingKey == targetMeeting.meetingKey }
                    if (index >= 0) {
                        rvMeetings.scrollToPosition(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadSessionsForMeeting(meetingKey: Long) {
        lifecycleScope.launch {
            try {
                val sessions = api.getSessions(meetingKey)
                sessionScheduleAdapter.updateData(sessions)

                val raceSession = sessions.find { it.sessionType.equals("Race", true) } ?: sessions.lastOrNull()
                raceSession?.let { s ->
                    loadHistoricalSession(s.sessionKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadHistoricalSession(sessionKey: Long) {
        lifecycleScope.launch {
            try {
                val drivers = api.getDrivers(sessionKey)
                val positions = api.getLatestPositions(sessionKey)
                val intervals = api.getLatestIntervals(sessionKey)
                val laps = api.getLatestLaps(sessionKey)
                val stints = api.getStints(sessionKey)

                val items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                archiveResultsAdapter.updateData(items)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (layoutHome.visibility != View.VISIBLE) {
            btnTabHome.performClick()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
