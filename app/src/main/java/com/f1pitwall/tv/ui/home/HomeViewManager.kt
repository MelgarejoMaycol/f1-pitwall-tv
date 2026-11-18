package com.f1pitwall.tv.ui.home

import android.graphics.Color
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
import com.f1pitwall.tv.data.model.HeroBannerData
import com.f1pitwall.tv.data.model.HeroStateType
import com.f1pitwall.tv.data.model.RecentRaceSummary
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.Meeting
import kotlinx.coroutines.launch

class HomeViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onOpenRaceScreen: () -> Unit,
    private val onOpenGpDetail: (Meeting) -> Unit,
    private val onOpenHubSection: (String) -> Unit
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
    private val rvCircularHubs: RecyclerView = rootView.findViewById(R.id.rvCircularHubs)
    private val rvCarouselGp: RecyclerView = rootView.findViewById(R.id.rvCarouselGp)
    private val rvCarouselDrivers: RecyclerView = rootView.findViewById(R.id.rvCarouselDrivers)
    private val rvCarouselConstructors: RecyclerView = rootView.findViewById(R.id.rvCarouselConstructors)
    private val rvCarouselCircuits: RecyclerView = rootView.findViewById(R.id.rvCarouselCircuits)

    // Adapters
    private lateinit var gpCarouselAdapter: GpCarouselAdapter
    private lateinit var driverCarouselAdapter: DriverCarouselAdapter
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

        // 1. Circular Hubs
        rvCircularHubs.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val hubAdapter = CircularHubAdapter(repository.getCircularHubItems()) { hub ->
            onOpenHubSection(hub.id)
        }
        rvCircularHubs.adapter = hubAdapter

        // 2. Grandes Premios
        rvCarouselGp.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        gpCarouselAdapter = GpCarouselAdapter { meeting ->
            onOpenGpDetail(meeting)
        }
        rvCarouselGp.adapter = gpCarouselAdapter

        // 3. Pilotos
        rvCarouselDrivers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        driverCarouselAdapter = DriverCarouselAdapter { driver ->
            Toast.makeText(context, "${driver.fullName} #${driver.driverNumber} (${driver.teamName})", Toast.LENGTH_SHORT).show()
        }
        rvCarouselDrivers.adapter = driverCarouselAdapter

        // 4. Constructores
        rvCarouselConstructors.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val constructorAdapter = ConstructorCarouselAdapter(repository.getConstructors()) { team ->
            Toast.makeText(context, "Escudería: ${team.teamName}", Toast.LENGTH_SHORT).show()
        }
        rvCarouselConstructors.adapter = constructorAdapter

        // 5. Circuitos
        rvCarouselCircuits.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        circuitCarouselAdapter = CircuitCarouselAdapter { circuit ->
            Toast.makeText(context, "${circuit.circuitName} (${circuit.country})", Toast.LENGTH_SHORT).show()
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
                // 1. Cargar Grandes Premios del año (2026 por defecto)
                val meetings = repository.getMeetings(year)
                gpCarouselAdapter.updateData(meetings)

                // 2. Selección inteligente del GP para Hero y Detalles
                val smartMeeting = repository.findSmartSelectedMeeting(year)
                currentHeroMeeting = smartMeeting

                // 3. Resolver Hero Banner con datos dinámicos reales
                val heroData = repository.resolveHeroBannerData(year)
                heroData?.let { bindHero(it) }

                // 4. Cargar resumen de última carrera disputada y podio real (São Paulo GP)
                val recentSummary = repository.getRecentRaceSummary(year)
                recentSummary?.let { bindRecentResults(it, meetings) }

                // 5. Cargar Pilotos de la parrilla
                val driverSessionKey = heroData?.sessionKey ?: 9472L
                val drivers = repository.getDriversCarouselData(driverSessionKey)
                driverCarouselAdapter.updateData(drivers)

                // 6. Cargar Circuitos del año
                val circuits = repository.getCircuits(year)
                circuitCarouselAdapter.updateData(circuits)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun bindHero(hero: HeroBannerData) {
        tvHeroTitle.text = hero.grandPrixName
        tvHeroSubtitle.text = "${hero.circuitName} • ${hero.location}"
        tvHeroColombiaTime.text = hero.timingDisplay
        tvHeroCountdown.text = hero.countdownText

        when (hero.type) {
            HeroStateType.LIVE -> {
                tvHeroStateBadge.text = "🔴 EN DIRECTO"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#E10600"))
                btnHeroWatchLive.text = "🏎️ Ver en Directo (Muro de Pits)"
                btnHeroWatchLive.visibility = View.VISIBLE
                btnHeroWatchLive.requestFocus()
            }
            HeroStateType.UPCOMING -> {
                tvHeroStateBadge.text = "⏳ PRÓXIMA CARRERA"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#2563EB"))
                btnHeroWatchLive.text = "🏎️ Muro de Pits (En Espera)"
                btnHeroWatchLive.visibility = View.VISIBLE
            }
            HeroStateType.COMPLETED -> {
                tvHeroStateBadge.text = "🏁 RESULTADOS DISPONIBLES"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#15803D"))
                btnHeroWatchLive.text = "🏎️ Muro de Pits y Telemetría"
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
            tvPodium1Team.text = "${p1.teamName} • Ganador 🏆"
        }
        if (p2 != null) {
            tvPodium2Name.text = p2.fullName
            tvPodium2Team.text = p2.teamName
        }
        if (p3 != null) {
            tvPodium3Name.text = p3.fullName
            tvPodium3Team.text = p3.teamName
        }
    }
}
