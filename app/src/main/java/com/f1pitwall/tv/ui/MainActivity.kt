package com.f1pitwall.tv.ui

import android.graphics.Color
import android.os.Bundle
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
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.ui.home.HomeViewManager
import com.f1pitwall.tv.ui.pitwall.PitWallViewManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var sessionRefreshJob: Job? = null
    private var selectedMeeting: Meeting? = null
    private var selectedYear: Int = AppConfig.DEFAULT_SEASON // 2026

    // Demo stream presets
    private val demoHlsUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private val demoMp4Url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupManagers()
        setupNavigation()
        setupArchiveView()
        setupSettingsView()
        loadInitialData()
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

        btnTabArchive.setOnClickListener {
            homeViewManager.saveFocusAndScroll()
            switchToTab(layoutArchive, btnTabArchive)
            loadCalendar(selectedYear)
        }

        btnTabSettings.setOnClickListener {
            homeViewManager.saveFocusAndScroll()
            switchToTab(layoutSettings, btnTabSettings)
            btnApplyStream.requestFocus() // Avoid auto-focusing EditText and opening keyboard
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
        if (etStreamUrl.text.isNullOrBlank()) {
            VideoSourcesConfig.loadProviders(this)
                .firstOrNull { it.isDefault }
                ?.let { etStreamUrl.setText(it.streamUrl) }
        }

        btnApplyStream.setOnClickListener {
            val url = etStreamUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                pitWallViewManager.playStream(url, "Señal Personalizada")
                btnTabPitWall.performClick()
            } else {
                Toast.makeText(this, "Introduce una URL válida", Toast.LENGTH_SHORT).show()
            }
        }

        btnPresetDemo1.setOnClickListener {
            etStreamUrl.setText(demoHlsUrl)
            pitWallViewManager.playStream(demoHlsUrl, "Demo 1 (HLS)")
            btnTabPitWall.performClick()
        }

        btnPresetDemo2.setOnClickListener {
            etStreamUrl.setText(demoMp4Url)
            pitWallViewManager.playStream(demoMp4Url, "Demo 2 (MP4)")
            btnTabPitWall.performClick()
        }

        btnReloadGitSources.setOnClickListener {
            val providers = VideoSourcesConfig.loadProviders(this)
            Toast.makeText(this, "Fuentes Git recargadas (${providers.size} proveedores disponibles)", Toast.LENGTH_SHORT).show()
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
        try {
            val session = repository.getCurrentOrNextSession(selectedYear)
            if (session == null) {
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
            val isUpcoming = start?.after(now) == true
            val venue = session.circuitShortName ?: session.countryName ?: "F1"
            val title = "$venue • ${session.sessionName ?: "Sesión"}"
            pitWallViewManager.onSessionUpdated(session.sessionKey, isLive, isUpcoming, title)

            if (!isLive) {
                val standings = repository.getDriverStandings(selectedYear)
                if (standings.isNotEmpty()) {
                    val fallbackItems = standings.map { st ->
                        DriverLeaderboardItem(
                            driverNumber = st.driverNumber,
                            position = st.position,
                            acronym = st.acronym,
                            fullName = st.fullName,
                            teamName = st.teamName,
                            teamColor = st.teamColor,
                            gapToLeader = st.gapToLeader ?: if (st.position == 1) "LÍDER" else "+${st.points} pts",
                            interval = "${st.points} pts",
                            lastLap = "--:--",
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
                tvRaceFlag.text = if (isUpcoming) "🏁 PRÓXIMA SESIÓN" else "🏁 ÚLTIMA SESIÓN"
                tvRaceFlag.setTextColor(Color.parseColor("#9CA3AF"))
                tvWeather.text = "⛅ Pista: -- | Aire: --"
                return
            }

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
            val intervals = api.getLatestIntervals(session.sessionKey)
            val laps = api.getLatestLaps(session.sessionKey)
            val stints = api.getStints(session.sessionKey)

            var items = buildLeaderboardItems(drivers, positions, intervals, laps, stints)
            if (items.isEmpty()) {
                val standings = repository.getDriverStandings(selectedYear)
                if (standings.isNotEmpty()) {
                    items = standings.map { st ->
                        DriverLeaderboardItem(
                            driverNumber = st.driverNumber,
                            position = st.position,
                            acronym = st.acronym,
                            fullName = st.fullName,
                            teamName = st.teamName,
                            teamColor = st.teamColor,
                            gapToLeader = st.gapToLeader ?: if (st.position == 1) "LÍDER" else "+${st.points} pts",
                            interval = "${st.points} pts",
                            lastLap = "--:--",
                            s1 = "--",
                            s2 = "--",
                            s3 = "--",
                            tyreCompound = "SOFT",
                            tyreAge = 1
                        )
                    }
                }
            }
            pitWallViewManager.leaderboardAdapter.updateData(items)
        } catch (e: Exception) {
            e.printStackTrace()
            pitWallViewManager.onScheduleUnavailable()
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
                delay(60_000L)
            }
        }
    }

    override fun onPause() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
        super.onPause()
        pitWallViewManager.onPause()
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
