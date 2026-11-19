package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.ui.LeaderboardAdapter
import kotlinx.coroutines.launch

class PitWallViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onNavigateToSettings: () -> Unit
) {
    private val context: Context = rootView.context

    // Video Player & Standby
    private val playerView: PlayerView = rootView.findViewById(R.id.playerView)
    private val layoutPlayerStandby: View = rootView.findViewById(R.id.layoutPlayerStandby)
    private val tvStandbyStatusBadge: TextView = rootView.findViewById(R.id.tvStandbyStatusBadge)
    private val tvStandbyTitle: TextView = rootView.findViewById(R.id.tvStandbyTitle)
    private val tvStandbySubtitle: TextView = rootView.findViewById(R.id.tvStandbySubtitle)
    private val btnStandbyPreview: Button = rootView.findViewById(R.id.btnStandbyPreview)
    private val btnStandbyRecap: Button = rootView.findViewById(R.id.btnStandbyRecap)
    private val btnStandbyStopVideo: Button = rootView.findViewById(R.id.btnStandbyStopVideo)

    // Alert Banner & Content Hub Bar
    private val layoutLiveSessionAlert: View = rootView.findViewById(R.id.layoutLiveSessionAlert)
    private val tvLiveSessionAlertTitle: TextView = rootView.findViewById(R.id.tvLiveSessionAlertTitle)
    private val btnLiveSessionAction: Button = rootView.findViewById(R.id.btnLiveSessionAction)
    private val btnContentPreview: Button = rootView.findViewById(R.id.btnContentPreview)
    private val btnContentRecap: Button = rootView.findViewById(R.id.btnContentRecap)
    private val btnContentBoxes: Button = rootView.findViewById(R.id.btnContentBoxes)

    // Radio
    private val tvRadioTitle: TextView = rootView.findViewById(R.id.tvRadioTitle)
    private val btnPlayRadio: Button = rootView.findViewById(R.id.btnPlayRadio)
    private val btnRadioVer: Button = rootView.findViewById(R.id.btnRadioVer)
    private val btnRadioNor: Button = rootView.findViewById(R.id.btnRadioNor)
    private val btnRadioLec: Button = rootView.findViewById(R.id.btnRadioLec)
    private val btnRadioHam: Button = rootView.findViewById(R.id.btnRadioHam)
    private val btnRadioRus: Button = rootView.findViewById(R.id.btnRadioRus)
    private val btnRadioPia: Button = rootView.findViewById(R.id.btnRadioPia)
    private val btnRadioAlo: Button = rootView.findViewById(R.id.btnRadioAlo)

    // Header Status & Live Refresh
    private val tvSessionTitle: TextView = rootView.findViewById(R.id.tvSessionTitle)
    private val btnRefreshLive: Button? = rootView.findViewById(R.id.btnRefreshLive)

    // Metrics Switcher & Containers
    private val btnMetricsTiming: Button = rootView.findViewById(R.id.btnMetricsTiming)
    private val btnMetricsTelemetry: Button = rootView.findViewById(R.id.btnMetricsTelemetry)
    private val rvLeaderboard: RecyclerView = rootView.findViewById(R.id.rvLeaderboard)
    private val layoutTelemetryPanel: View = rootView.findViewById(R.id.layoutTelemetryPanel)

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

    // State & Players
    private var exoPlayer: ExoPlayer? = null
    private var radioPlayer: ExoPlayer? = null
    private var latestRadioUrl: String? = null
    private var currentSessionKey: Long? = null
    private var isLiveSessionActive: Boolean = false

    val leaderboardAdapter = LeaderboardAdapter { item ->
        tvTelemetryDriverHeader.text = "#${item.driverNumber} ${item.fullName} • ${item.teamName}"
        btnMetricsTelemetry.performClick()
        loadDriverTelemetry(item.driverNumber, forceDemo = true)
    }

    init {
        initPlayers()
        setupListeners()
        setupLeaderboardView()
    }

    private fun initPlayers() {
        exoPlayer = ExoPlayer.Builder(context).build()
        playerView.player = exoPlayer

        radioPlayer = ExoPlayer.Builder(context).build()

        // By default off-race: Standby visible, playerView hidden, NO auto-playing demo video!
        switchToStandby()
    }

    private fun setupListeners() {
        // Video Standby Controls
        btnStandbyPreview.setOnClickListener {
            playStream("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "Previa Oficial: GP Las Vegas 2026")
        }

        btnStandbyRecap.setOnClickListener {
            playStream("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "Resumen Oficial: GP São Paulo 2026")
        }

        btnStandbyStopVideo.setOnClickListener {
            switchToStandby()
        }

        // Live Action Button
        btnLiveSessionAction.setOnClickListener {
            if (isLiveSessionActive) {
                playStream("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "Transmisión en Vivo: F1 PitWall")
            } else {
                onNavigateToSettings()
            }
        }

        // Content Hub Bar
        btnContentPreview.setOnClickListener { btnStandbyPreview.performClick() }
        btnContentRecap.setOnClickListener { btnStandbyRecap.performClick() }
        btnContentBoxes.setOnClickListener {
            playStream("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "Feed Técnico de Boxes y Pista")
        }

        // Radio Player
        btnPlayRadio.setOnClickListener {
            latestRadioUrl?.let { url ->
                val mediaItem = MediaItem.fromUri(url)
                radioPlayer?.setMediaItem(mediaItem)
                radioPlayer?.prepare()
                radioPlayer?.play()
                Toast.makeText(context, "Reproduciendo audio de radio...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(context, "No hay audio de radio disponible en este momento", Toast.LENGTH_SHORT).show()
            }
        }

        // Radio Driver Chips
        btnRadioVer.setOnClickListener { tuneRadioDriver(1, "Max Verstappen") }
        btnRadioNor.setOnClickListener { tuneRadioDriver(4, "Lando Norris") }
        btnRadioLec.setOnClickListener { tuneRadioDriver(16, "Charles Leclerc") }
        btnRadioHam.setOnClickListener { tuneRadioDriver(44, "Lewis Hamilton") }
        btnRadioRus.setOnClickListener { tuneRadioDriver(63, "George Russell") }
        btnRadioPia.setOnClickListener { tuneRadioDriver(81, "Oscar Piastri") }
        btnRadioAlo.setOnClickListener { tuneRadioDriver(14, "Fernando Alonso") }

        // Metrics Switcher
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

    private fun setupLeaderboardView() {
        rvLeaderboard.layoutManager = LinearLayoutManager(context)
        rvLeaderboard.adapter = leaderboardAdapter
    }

    fun playStream(url: String, title: String? = null) {
        layoutPlayerStandby.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE

        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        if (title != null) {
            tvLiveSessionAlertTitle.text = "🏎️ $title"
            Toast.makeText(context, "Cargando señal: $title", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchToStandby() {
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.GONE

        if (!isLiveSessionActive) {
            tvLiveSessionAlertTitle.text = "⏸️ Sin carrera en vivo • Próxima: GP Las Vegas (Jue 19 nov, 7:30 PM Col)"
            btnLiveSessionAction.text = "⚡ Señal y Ajustes"
            tvStandbyStatusBadge.text = "⏸️ SEÑAL EN ESPERA • PRÓXIMA: GP LAS VEGAS"
        }
    }

    fun tuneRadioDriver(driverNumber: Int, driverName: String) {
        tvRadioTitle.text = "📻 Sintonizando Radio: $driverName (#$driverNumber)..."
        scope.launch {
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
                    Toast.makeText(context, "Reproduciendo radio de $driverName", Toast.LENGTH_SHORT).show()
                } else {
                    tvRadioTitle.text = "📻 Radio: $driverName (#$driverNumber) [Canal en espera]"
                    Toast.makeText(context, "Sin transmisión de radio activa para $driverName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDriverTelemetry(driverNumber: Int, forceDemo: Boolean = false) {
        scope.launch {
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
                    Toast.makeText(context, "Sin telemetría en vivo. Pulsa 'Cargar Datos Oficiales' para ver telemetría de sesión.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSessionUpdated(sessionKey: Long, isLive: Boolean, sessionTitleText: String) {
        currentSessionKey = sessionKey
        isLiveSessionActive = isLive
        tvSessionTitle.text = sessionTitleText

        if (isLive) {
            tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $sessionTitleText"
            btnLiveSessionAction.text = "⚡ Ver Señal en Vivo"
            tvStandbyStatusBadge.text = "🔴 SESIÓN ACTIVA • EN DIRECTO"
        } else {
            tvLiveSessionAlertTitle.text = "⏸️ Sin carrera en vivo • Próxima: GP Las Vegas (Jue 19 nov, 7:30 PM Col)"
            btnLiveSessionAction.text = "⚡ Señal y Ajustes"
            tvStandbyStatusBadge.text = "⏸️ SEÑAL EN ESPERA • PRÓXIMA: GP LAS VEGAS"
        }
    }

    fun onPause() {
        exoPlayer?.pause()
    }

    fun onDestroy() {
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
