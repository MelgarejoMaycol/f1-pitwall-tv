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

    // Settings UI
    private lateinit var etStreamUrl: EditText
    private lateinit var btnApplyStream: Button
    private lateinit var btnPresetDemo1: Button
    private lateinit var btnPresetDemo2: Button
    private lateinit var btnScanFutbolVivo: Button

    // FutbolVivo Banner
    private lateinit var layoutFutbolVivoBanner: LinearLayout
    private lateinit var tvFutbolVivoTitle: TextView
    private lateinit var btnTuneFutbolVivo: Button
    private lateinit var futbolVivoExtractor: com.f1pitwall.tv.network.FutbolVivoExtractor
    private var currentFutbolVivoStreamUrl: String? = null
    private var currentSessionKey: Long? = null
    private var latestRadioUrl: String? = null
    private var selectedMeeting: Meeting? = null
    private var selectedYear: Int = 2024

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
                btnTabArchive.performClick()
                displayMeetingDetails(meeting)
            },
            onOpenHubSection = { hubId ->
                when (hubId) {
                    "calendario" -> btnTabArchive.performClick()
                    "resultados" -> {
                        btnTabArchive.performClick()
                        btnViewResults.performClick()
                    }
                    "configuracion" -> btnTabSettings.performClick()
                    else -> {
                        Toast.makeText(this, "Sección '$hubId' disponible en las siguientes fases", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        tvRaceFlag = findViewById(R.id.tvRaceFlag)
        tvWeather = findViewById(R.id.tvWeather)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        btnRefreshLive = findViewById(R.id.btnRefreshLive)
        rvLeaderboard = findViewById(R.id.rvLeaderboard)
        tvRadioTitle = findViewById(R.id.tvRadioTitle)
        btnPlayRadio = findViewById(R.id.btnPlayRadio)

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

        etStreamUrl = findViewById(R.id.etStreamUrl)
        btnApplyStream = findViewById(R.id.btnApplyStream)
        btnPresetDemo1 = findViewById(R.id.btnPresetDemo1)
        btnPresetDemo2 = findViewById(R.id.btnPresetDemo2)
        btnScanFutbolVivo = findViewById(R.id.btnScanFutbolVivo)

        layoutFutbolVivoBanner = findViewById(R.id.layoutFutbolVivoBanner)
        tvFutbolVivoTitle = findViewById(R.id.tvFutbolVivoTitle)
        btnTuneFutbolVivo = findViewById(R.id.btnTuneFutbolVivo)

        setupFutbolVivoExtractor()
    }

    private fun setupFutbolVivoExtractor() {
        futbolVivoExtractor = com.f1pitwall.tv.network.FutbolVivoExtractor(this)
        futbolVivoExtractor.onF1EventDetected = { event ->
            layoutFutbolVivoBanner.visibility = View.VISIBLE
            val status = if (event.isLive) "🔴 EN VIVO" else "⏳ PROGRAMADA"
            tvFutbolVivoTitle.text = "🏁 futbolvivo.tv: ${event.title} ($status)"
            currentFutbolVivoStreamUrl = event.streamUrl

            if (event.streamUrl != null) {
                btnTuneFutbolVivo.text = "⚡ Sintonizar (${event.streamName ?: "HD"})"
                btnTuneFutbolVivo.visibility = View.VISIBLE
            } else {
                btnTuneFutbolVivo.text = "⏳ Señal en espera"
                btnTuneFutbolVivo.visibility = View.VISIBLE
            }
        }

        futbolVivoExtractor.onNoEventDetected = {
            tvFutbolVivoTitle.text = "📡 futbolvivo.tv: No hay carrera programada en este momento."
            btnTuneFutbolVivo.visibility = View.GONE
        }

        btnTuneFutbolVivo.setOnClickListener {
            currentFutbolVivoStreamUrl?.let { url ->
                playStream(url)
                Toast.makeText(this, "Sintonizando carrera desde futbolvivo.tv", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Carrera detectada en futbolvivo.tv. Las señales se activan antes de iniciar la carrera.", Toast.LENGTH_LONG).show()
            }
        }

        btnScanFutbolVivo.setOnClickListener {
            Toast.makeText(this, "Escaneando futbolvivo.tv en busca de carreras de F1...", Toast.LENGTH_SHORT).show()
            futbolVivoExtractor.startExtraction()
        }

        futbolVivoExtractor.startExtraction()
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        playStream(demoHlsUrl)

        radioPlayer = ExoPlayer.Builder(this).build()

        btnPlayRadio.setOnClickListener {
            latestRadioUrl?.let { url ->
                val mediaItem = MediaItem.fromUri(url)
                radioPlayer?.setMediaItem(mediaItem)
                radioPlayer?.prepare()
                radioPlayer?.play()
                Toast.makeText(this, "Reproduciendo audio de radio...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "No hay audio de radio disponible", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Piloto: ${item.fullName} (${item.teamName})", Toast.LENGTH_SHORT).show()
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

        loadSessionsForMeeting(meeting.meetingKey)
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
            outFormat.timeZone = TimeZone.getDefault()

            if (sDate != null && eDate != null) {
                "Fechas: ${outFormat.format(sDate)} al ${outFormat.format(eDate)} de ${yearFormat.format(eDate)}"
            } else if (sDate != null) {
                "Fecha: ${outFormat.format(sDate)}"
            } else "Fechas por confirmar"
        } catch (e: Exception) {
            "Fechas: ${startIso.take(10)}"
        }
    }

    private fun loadInitialData() {
        loadLiveSessionData()
    }

    private fun loadLiveSessionData() {
        lifecycleScope.launch {
            try {
                btnRefreshLive.text = "⏳ Cargando..."
                val session = api.getLatestSession()
                if (session != null) {
                    currentSessionKey = session.sessionKey
                    tvSessionTitle.text = "${session.circuitShortName ?: "F1"} - ${session.sessionName ?: "Sesión"}"

                    val drivers = api.getDrivers(session.sessionKey)
                    val positions = api.getLatestPositions(session.sessionKey)
                    val intervals = api.getLatestIntervals(session.sessionKey)
                    val laps = api.getLatestLaps(session.sessionKey)
                    val stints = api.getStints(session.sessionKey)
                    val radios = api.getTeamRadios(session.sessionKey)
                    val weather = api.getLatestWeather(session.sessionKey)
                    val raceControl = api.getLatestRaceControl(session.sessionKey)

                    // Weather & Race Control
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

                    // Radio
                    radios.firstOrNull()?.let { r ->
                        latestRadioUrl = r.recordingUrl
                        val driver = drivers.find { it.driverNumber == r.driverNumber }
                        tvRadioTitle.text = "📻 Radio: #${r.driverNumber} ${driver?.broadcastName ?: "Piloto"}"
                    }

                    // Build Leaderboard Items
                    val items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                    leaderboardAdapter.updateData(items)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                btnRefreshLive.text = "🔄 Actualizar"
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
                val meetings = api.getMeetings(year)
                meetingAdapter.updateData(meetings)
                if (meetings.isNotEmpty()) {
                    val defaultMeeting = meetings.firstOrNull { it.meetingName?.contains("Grand Prix", true) == true } ?: meetings.first()
                    displayMeetingDetails(defaultMeeting)
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

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
