package com.f1pitwall.tv.ui.home

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.Meeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class HomeViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onOpenRaceScreen: () -> Unit,
    private val onOpenGpDetail: (Meeting) -> Unit,
    private val onOpenHubSection: (String) -> Unit,
    private val onOpenCircuit: (CircuitCardItem) -> Unit = {},
    private val onOpenGpResults: (Meeting) -> Unit = {},
    private val onOpenDriverTelemetry: (DriverCardItem) -> Unit = {}
) {

    // Hero Elements
    private val tvHeroStateBadge: TextView = rootView.findViewById(R.id.tvHeroStateBadge)
    private val tvHeroCountdown: TextView = rootView.findViewById(R.id.tvHeroCountdown)
    private val tvHeroTitle: TextView = rootView.findViewById(R.id.tvHeroTitle)
    private val tvHeroSubtitle: TextView = rootView.findViewById(R.id.tvHeroSubtitle)
    private val tvHeroColombiaTime: TextView = rootView.findViewById(R.id.tvHeroColombiaTime)
    private val btnHeroWatchLive: Button = rootView.findViewById(R.id.btnHeroWatchLive)
    private val btnHeroViewGp: Button = rootView.findViewById(R.id.btnHeroViewGp)
    private val btnHeroResults: Button = rootView.findViewById(R.id.btnHeroResults)
    private val ivHeroFlag: ImageView = rootView.findViewById(R.id.ivHeroFlag)
    private val ivHeroCircuitMap: ImageView = rootView.findViewById(R.id.ivHeroCircuitMap)

    // Recent Results Card Elements
    private val tvRecentResultsGpTitle: TextView = rootView.findViewById(R.id.tvRecentResultsGpTitle)
    private val tvRecentResultsCircuit: TextView = rootView.findViewById(R.id.tvRecentResultsCircuit)
    private val tvRecentResultsDate: TextView = rootView.findViewById(R.id.tvRecentResultsDate)
    private val tvPodium1Name: TextView = rootView.findViewById(R.id.tvPodium1Name)
    private val tvPodium1Team: TextView = rootView.findViewById(R.id.tvPodium1Team)
    private val tvPodium2Name: TextView = rootView.findViewById(R.id.tvPodium2Name)
    private val tvPodium2Team: TextView = rootView.findViewById(R.id.tvPodium2Team)
    private val tvPodium3Name: TextView = rootView.findViewById(R.id.tvPodium3Name)
    private val tvPodium3Team: TextView = rootView.findViewById(R.id.tvPodium3Team)
    private val btnViewFullResults: Button = rootView.findViewById(R.id.btnViewFullResults)

    // Section Subtitles
    private val tvDriversSectionSubtitle: TextView? = rootView.findViewById(R.id.tvDriversSectionSubtitle)
    private val tvConstructorsSectionSubtitle: TextView? = rootView.findViewById(R.id.tvConstructorsSectionSubtitle)

    // Carousels
    private val rvCarouselGp: RecyclerView = rootView.findViewById(R.id.rvCarouselGp)
    private val rvCarouselDrivers: RecyclerView = rootView.findViewById(R.id.rvCarouselDrivers)
    private val rvCarouselConstructors: RecyclerView = rootView.findViewById(R.id.rvCarouselConstructors)
    private val rvCarouselCircuits: RecyclerView = rootView.findViewById(R.id.rvCarouselCircuits)

    // Adapters
    private lateinit var gpCarouselAdapter: GpCarouselAdapter
    private lateinit var driverCarouselAdapter: DriverCarouselAdapter
    private lateinit var constructorCarouselAdapter: ConstructorCarouselAdapter
    private lateinit var circuitCarouselAdapter: CircuitCarouselAdapter

    private var currentHeroMeeting: Meeting? = null
    private var lastFinishedMeeting: Meeting? = null
    private var activeLoadJob: Job? = null

    init {
        setupCarousels()
        setupActionButtons()
        loadHomeData(AppConfig.DEFAULT_SEASON)
    }

    private fun setupCarousels() {
        val context = rootView.context

        // 1. Grandes Premios de la Temporada (Selección dinámica)
        rvCarouselGp.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        gpCarouselAdapter = GpCarouselAdapter { meeting ->
            onOpenGpDetail(meeting)
        }
        rvCarouselGp.adapter = gpCarouselAdapter

        // 2. Mundial de Pilotos (Puntuaciones y posiciones oficiales)
        rvCarouselDrivers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        driverCarouselAdapter = DriverCarouselAdapter { driver ->
            onOpenDriverTelemetry(driver)
        }
        rvCarouselDrivers.adapter = driverCarouselAdapter

        // 3. Mundial de Constructores (Puntuaciones oficiales)
        rvCarouselConstructors.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        constructorCarouselAdapter = ConstructorCarouselAdapter { team ->
            Toast.makeText(context, "${team.position}º ${team.teamName} (${team.points} pts)", Toast.LENGTH_SHORT).show()
        }
        rvCarouselConstructors.adapter = constructorCarouselAdapter

        // 4. Circuitos Oficiales (Navega a resultados históricos de ese circuito)
        rvCarouselCircuits.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        circuitCarouselAdapter = CircuitCarouselAdapter { circuit ->
            onOpenCircuit(circuit)
        }
        rvCarouselCircuits.adapter = circuitCarouselAdapter
    }

    private fun setupActionButtons() {
        btnHeroWatchLive.setOnClickListener {
            onOpenRaceScreen()
        }

        btnHeroViewGp.setOnClickListener {
            currentHeroMeeting?.let { onOpenGpDetail(it) }
        }

        btnHeroResults.setOnClickListener {
            val target = lastFinishedMeeting ?: currentHeroMeeting
            target?.let { onOpenGpResults(it) }
        }

        btnViewFullResults.setOnClickListener {
            val target = lastFinishedMeeting ?: currentHeroMeeting
            target?.let { onOpenGpResults(it) }
        }
    }

    fun loadHomeData(year: Int = AppConfig.DEFAULT_SEASON) {
        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            try {
                // Carga concurrente paralela de fuentes independientes sin bloquear el hilo principal
                val meetingsDeferred = async(Dispatchers.IO) { repository.getMeetings(year) }
                val smartMeetingDeferred = async(Dispatchers.IO) { repository.findSmartSelectedMeeting(year) }
                val heroDataDeferred = async(Dispatchers.IO) { repository.resolveHeroBannerData(year) }
                val recentSummaryDeferred = async(Dispatchers.IO) { repository.getRecentRaceSummary(year) }
                val driversDeferred = async(Dispatchers.IO) { repository.getDriverStandings(year) }
                val constructorsDeferred = async(Dispatchers.IO) { repository.getConstructorStandings(year) }
                val circuitsDeferred = async(Dispatchers.IO) { repository.getCircuits(year) }

                // 1. Calendario y GP inteligente
                val meetings = meetingsDeferred.await()
                val smartMeeting = smartMeetingDeferred.await()
                currentHeroMeeting = smartMeeting
                gpCarouselAdapter.updateData(meetings, smartMeeting?.meetingKey)
                rvCarouselGp.scrollToPosition(0)

                // 2. Hero Banner dinámico
                val heroData = heroDataDeferred.await()
                if (heroData != null) {
                    bindHero(heroData)
                } else {
                    bindEmptyHero()
                }

                // 3. Resumen y podio de carrera reciente
                val recentSummary = recentSummaryDeferred.await()
                if (recentSummary != null) {
                    bindRecentResults(recentSummary, meetings)
                } else {
                    clearRecentResults()
                }

                // 4. Clasificación de Pilotos
                val driversStandings = driversDeferred.await()
                driverCarouselAdapter.updateData(driversStandings)
                rvCarouselDrivers.scrollToPosition(0)
                val driverLeader = driversStandings.firstOrNull()
                tvDriversSectionSubtitle?.text = if (driverLeader != null && driverLeader.position > 0) {
                    "Líder: ${driverLeader.fullName} (${driverLeader.points} pts) • Orden Oficial"
                } else {
                    "Sin clasificación oficial disponible para la temporada $year"
                }

                // 5. Clasificación de Constructores
                val constructorStandings = constructorsDeferred.await()
                constructorCarouselAdapter.updateData(constructorStandings)
                rvCarouselConstructors.scrollToPosition(0)
                val constructorLeader = constructorStandings.firstOrNull()
                tvConstructorsSectionSubtitle?.text = if (constructorLeader != null && constructorLeader.position > 0) {
                    "Líder: ${constructorLeader.teamName} (${constructorLeader.points} pts) • Orden Oficial"
                } else {
                    "Sin clasificación oficial disponible para la temporada $year"
                }

                // 6. Circuitos oficiales
                val circuits = circuitsDeferred.await()
                circuitCarouselAdapter.updateData(circuits)
                rvCarouselCircuits.scrollToPosition(0)

            } catch (e: Exception) {
                AppLogger.w("HomeViewManager", "Excepción controlada al cargar pantalla de inicio para temporada $year", e)
                clearRecentResults()
                bindEmptyHero()
            }
        }
    }

    private fun bindHero(hero: HeroBannerData) {
        tvHeroTitle.text = hero.grandPrixName

        // Presenta circuito y país de forma limpia sin duplicar ubicaciones
        val cleanCircuit = hero.circuitName.trim()
        val cleanCountry = hero.countryName.trim()
        tvHeroSubtitle.text = if (cleanCircuit.contains(cleanCountry, ignoreCase = true) || cleanCountry.isBlank()) {
            cleanCircuit
        } else {
            "$cleanCircuit • $cleanCountry"
        }

        tvHeroColombiaTime.text = hero.timingDisplay
        tvHeroCountdown.text = hero.countdownText

        // Diferenciar visualmente las prácticas, clasificación, Sprint y carrera con colores elegantes
        val lowerTiming = hero.timingDisplay.lowercase()
        val sessionColor = when {
            lowerTiming.contains("practice") || lowerTiming.contains("práctica") || lowerTiming.contains("libres") -> Color.parseColor("#38BDF8") // Steel Blue / Cyan
            lowerTiming.contains("qualifying") || lowerTiming.contains("clasificación") || lowerTiming.contains("shootout") -> Color.parseColor("#C084FC") // Purple
            lowerTiming.contains("sprint") -> Color.parseColor("#FB923C") // Apex Amber
            lowerTiming.contains("carrera") || lowerTiming.contains("race") -> Color.parseColor("#FF2A24") // Racing Red
            else -> Color.parseColor("#85D8F5")
        }
        tvHeroColombiaTime.setTextColor(sessionColor)

        when (hero.type) {
            HeroStateType.LIVE -> {
                tvHeroStateBadge.text = "🔴 EN DIRECTO"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#E10600"))
                btnHeroWatchLive.text = "▶ Ver Transmisión"
                btnHeroWatchLive.visibility = View.VISIBLE
                btnHeroWatchLive.requestFocus()
            }
            HeroStateType.UPCOMING -> {
                tvHeroStateBadge.text = "⏳ PRÓXIMO GRAN PREMIO"
                tvHeroStateBadge.setBackgroundColor(sessionColor)
                btnHeroWatchLive.text = "▶ Muro de Pits & Previa"
                btnHeroWatchLive.visibility = View.VISIBLE
            }
            HeroStateType.COMPLETED -> {
                tvHeroStateBadge.text = "🏁 RESULTADOS DISPONIBLES"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#15803D"))
                btnHeroWatchLive.text = "▶ Muro de Pits y Telemetría"
                btnHeroWatchLive.visibility = View.VISIBLE
            }
        }

        if (!hero.flagUrl.isNullOrEmpty()) {
            ivHeroFlag.visibility = View.VISIBLE
            ivHeroFlag.load(hero.flagUrl) { crossfade(true) }
        } else {
            ivHeroFlag.visibility = View.GONE
        }

        if (!hero.circuitImageUrl.isNullOrEmpty()) {
            ivHeroCircuitMap.visibility = View.VISIBLE
            ivHeroCircuitMap.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            ivHeroCircuitMap.load(hero.circuitImageUrl) { crossfade(true) }
        } else {
            ivHeroCircuitMap.visibility = View.GONE
        }
    }

    private fun bindRecentResults(summary: RecentRaceSummary, meetings: List<Meeting>) {
        tvRecentResultsGpTitle.text = "${summary.grandPrixName} ${summary.season}"
        tvRecentResultsCircuit.text = "${summary.circuitName} • ${summary.country}"
        tvRecentResultsDate.text = "Fecha oficial: ${summary.dateDisplay}"

        val championshipMeetings = meetings.filter {
            val name = it.meetingName?.lowercase() ?: ""
            !name.contains("test")
        }.sortedBy { it.dateStart }

        // Relacionar Grandes Premios mediante temporada, ronda oficial y fechas exactas
        val matchByRound = championshipMeetings.getOrNull(summary.round - 1)
        lastFinishedMeeting = if (matchByRound != null && (matchByRound.year == summary.season || summary.season == 0)) {
            matchByRound
        } else {
            meetings.find { m ->
                (m.year == summary.season || summary.season == 0) &&
                        (m.dateEnd?.startsWith(summary.dateDisplay) == true || m.dateStart?.startsWith(summary.dateDisplay) == true)
            }
        }

        btnViewFullResults.visibility = if (lastFinishedMeeting != null) View.VISIBLE else View.GONE

        val p1 = summary.podium.getOrNull(0)
        val p2 = summary.podium.getOrNull(1)
        val p3 = summary.podium.getOrNull(2)

        if (p1 != null) {
            tvPodium1Name.text = p1.fullName
            tvPodium1Team.text = "${p1.teamName} • Ganador (25 pts) 🏆"
        }
        if (p2 != null) {
            tvPodium2Name.text = p2.fullName
            tvPodium2Team.text = "${p2.teamName} • 2º Lugar (18 pts)"
        }
        if (p3 != null) {
            tvPodium3Name.text = p3.fullName
            tvPodium3Team.text = "${p3.teamName} • 3º Lugar (15 pts)"
        }
    }

    private fun clearRecentResults() {
        tvRecentResultsGpTitle.text = "Sin resultados disponibles"
        tvRecentResultsCircuit.text = "No se encontraron resultados oficiales para esta temporada"
        tvRecentResultsDate.text = ""
        tvPodium1Name.text = "-"
        tvPodium1Team.text = ""
        tvPodium2Name.text = "-"
        tvPodium2Team.text = ""
        tvPodium3Name.text = "-"
        tvPodium3Team.text = ""
        btnViewFullResults.visibility = View.GONE
    }

    private fun bindEmptyHero() {
        tvHeroTitle.text = "Fórmula 1"
        tvHeroSubtitle.text = "Sin eventos programados"
        tvHeroColombiaTime.text = ""
        tvHeroCountdown.text = ""
        tvHeroStateBadge.text = "FUERA DE TEMPORADA"
        tvHeroStateBadge.setBackgroundColor(Color.parseColor("#4B5563"))
        btnHeroWatchLive.visibility = View.GONE
        ivHeroFlag.visibility = View.GONE
        ivHeroCircuitMap.visibility = View.GONE
    }
}
