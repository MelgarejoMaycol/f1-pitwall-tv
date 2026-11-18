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
import com.f1pitwall.tv.data.model.HeroBannerData
import com.f1pitwall.tv.data.model.HeroStateType
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

    private var currentHeroMeetingKey: Long? = null

    init {
        setupCarousels()
        loadHomeData()
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

        // Action Buttons
        btnHeroWatchLive.setOnClickListener {
            onOpenRaceScreen()
        }

        btnHeroViewGp.setOnClickListener {
            currentHeroMeetingKey?.let { key ->
                scope.launch {
                    val meetings = repository.getMeetings(2024)
                    val m = meetings.find { it.meetingKey == key } ?: meetings.firstOrNull()
                    m?.let { onOpenGpDetail(it) }
                }
            }
        }

        btnHeroResults.setOnClickListener {
            currentHeroMeetingKey?.let { key ->
                scope.launch {
                    val meetings = repository.getMeetings(2024)
                    val m = meetings.find { it.meetingKey == key } ?: meetings.firstOrNull()
                    m?.let { onOpenGpDetail(it) }
                }
            }
        }
    }

    fun loadHomeData(year: Int = 2024) {
        scope.launch {
            try {
                // 1. Resolve Dynamic Hero Banner
                val heroData = repository.resolveHeroBannerData(year)
                heroData?.let { bindHero(it) }

                // 2. Load GP Meetings Carousel
                val meetings = repository.getMeetings(year)
                gpCarouselAdapter.updateData(meetings)

                // 3. Load Drivers Carousel
                val lastMeetingKey = heroData?.meetingKey ?: meetings.lastOrNull()?.meetingKey
                if (lastMeetingKey != null) {
                    val drivers = repository.getDriversCarouselData(heroData?.sessionKey ?: 9472L)
                    driverCarouselAdapter.updateData(drivers)
                }

                // 4. Load Circuits Carousel
                val circuits = repository.getCircuits(year)
                circuitCarouselAdapter.updateData(circuits)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun bindHero(hero: HeroBannerData) {
        currentHeroMeetingKey = hero.meetingKey
        tvHeroTitle.text = hero.grandPrixName
        tvHeroSubtitle.text = "${hero.circuitName} • ${hero.location}"
        tvHeroColombiaTime.text = hero.timingDisplay
        tvHeroCountdown.text = hero.countdownText

        when (hero.type) {
            HeroStateType.LIVE -> {
                tvHeroStateBadge.text = "🔴 EN DIRECTO"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#E10600"))
                btnHeroWatchLive.visibility = View.VISIBLE
                btnHeroWatchLive.requestFocus()
            }
            HeroStateType.UPCOMING -> {
                tvHeroStateBadge.text = "⏳ PRÓXIMA CARRERA"
                tvHeroStateBadge.setBackgroundColor(Color.parseColor("#2563EB"))
                btnHeroWatchLive.text = "🏎️ Ir al Muro de Pits"
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
}
