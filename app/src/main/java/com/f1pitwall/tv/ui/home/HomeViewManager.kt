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
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.Meeting
import kotlinx.coroutines.launch

class HomeViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onOpenRaceScreen: () -> Unit,
    private val onOpenGpDetail: (Meeting) -> Unit,
    private val onOpenHubSection: (String) -> Unit,
    private val onOpenCircuit: (CircuitCardItem) -> Unit = {}
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
            Toast.makeText(context, "${driver.position}º ${driver.fullName} (${driver.points} pts)", Toast.LENGTH_SHORT).show()
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
            lastFinishedMeeting?.let { onOpenGpDetail(it) } ?: currentHeroMeeting?.let { onOpenGpDetail(it) }
        }

        btnViewFullResults.setOnClickListener {
            lastFinishedMeeting?.let { onOpenGpDetail(it) }
        }
    }

    fun loadHomeData(year: Int = AppConfig.DEFAULT_SEASON) {
        scope.launch {
            try {
                // 1. Cargar Grandes Premios del año (2026)
                val meetings = repository.getMeetings(year)

                // 2. Selección inteligente del GP: Próximo evento (Las Vegas GP) o Sesión en Vivo
                val smartMeeting = repository.findSmartSelectedMeeting(year)
                currentHeroMeeting = smartMeeting

                // Actualizar carrusel de GP iniciando en la primera carrera y destacando Las Vegas
                gpCarouselAdapter.updateData(meetings, smartMeeting?.meetingKey)
                rvCarouselGp.scrollToPosition(0)

                // 3. Resolver Hero Banner con datos dinámicos oficiales
                val heroData = repository.resolveHeroBannerData(year)
                heroData?.let { bindHero(it) }

                // 4. Cargar resumen de última carrera disputada y podio real (São Paulo GP)
                val recentSummary = repository.getRecentRaceSummary(year)
                recentSummary?.let { bindRecentResults(it, meetings) }

                // 5. Cargar Mundial de Pilotos con puntos y posiciones reales (Jolpica 2026)
                val driversStandings = repository.getDriverStandings(year)
                driverCarouselAdapter.updateData(driversStandings)
                rvCarouselDrivers.scrollToPosition(0)

                // 6. Cargar Mundial de Constructores con puntos reales (Jolpica 2026)
                val constructorStandings = repository.getConstructorStandings(year)
                constructorCarouselAdapter.updateData(constructorStandings)
                rvCarouselConstructors.scrollToPosition(0)

                // 7. Cargar Circuitos oficiales
                val circuits = repository.getCircuits(year)
                circuitCarouselAdapter.updateData(circuits)
                rvCarouselCircuits.scrollToPosition(0)

            } catch (e: Exception) {
                e.printStackTrace()
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

        lastFinishedMeeting = meetings.find {
            it.meetingName?.contains(summary.grandPrixName.replace("Grand Prix", "").trim(), ignoreCase = true) == true
        } ?: meetings.lastOrNull { it.meetingName?.contains("Grand Prix", true) == true }

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
}
