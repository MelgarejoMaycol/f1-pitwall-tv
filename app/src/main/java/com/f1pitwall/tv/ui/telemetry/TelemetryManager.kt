package com.f1pitwall.tv.ui.telemetry

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.core.util.PitWallFormatter
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.CarTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TelemetryManager(
    private val containerView: View,
    private val repository: F1Repository,
    private val scope: CoroutineScope
) {
    private val tvTelemetryDriverHeader: TextView = containerView.findViewById(R.id.tvTelemetryDriverHeader)
    private val tvTelemetryDrs: TextView = containerView.findViewById(R.id.tvTelemetryDrs)
    private val tvTelemetrySessionStatus: TextView = containerView.findViewById(R.id.tvTelemetrySessionStatus)
    private val tvTelemetryTemperature: TextView = containerView.findViewById(R.id.tvTelemetryTemperature)
    private val pbTelemetryLoading: ProgressBar = containerView.findViewById(R.id.pbTelemetryLoading)
    private val tvTelemetrySpeed: TextView = containerView.findViewById(R.id.tvTelemetrySpeed)
    private val tvTelemetryGear: TextView = containerView.findViewById(R.id.tvTelemetryGear)
    private val tvTelemetryRpm: TextView = containerView.findViewById(R.id.tvTelemetryRpm)
    private val pbTelemetryRpm: ProgressBar = containerView.findViewById(R.id.pbTelemetryRpm)
    private val tvTelemetryThrottle: TextView = containerView.findViewById(R.id.tvTelemetryThrottle)
    private val pbTelemetryThrottle: ProgressBar = containerView.findViewById(R.id.pbTelemetryThrottle)
    private val tvTelemetryBrake: TextView = containerView.findViewById(R.id.tvTelemetryBrake)
    private val pbTelemetryBrake: ProgressBar = containerView.findViewById(R.id.pbTelemetryBrake)
    val btnTelemetryDemoToggle: Button = containerView.findViewById(R.id.btnTelemetryDemoToggle)

    private var activeTelemetryJob: Job? = null
    var currentDriverNumber: Int? = null
        private set

    fun loadDriverTelemetry(
        sessionKey: Long?,
        driverNumber: Int,
        driverName: String,
        driverTeam: String,
        isLive: Boolean
    ) {
        currentDriverNumber = driverNumber
        activeTelemetryJob?.cancel()

        if (sessionKey == null) {
            showTelemetryUnavailable("Sin sesión seleccionada")
            return
        }

        val header = if (driverTeam.isNotEmpty()) "#$driverNumber $driverName • $driverTeam" else "#$driverNumber $driverName"
        tvTelemetryDriverHeader.text = header

        showTelemetryLoading(isLive)

        activeTelemetryJob = scope.launch {
            try {
                val telemetry = repository.getTelemetryForDriver(sessionKey, driverNumber)

                if (currentDriverNumber != driverNumber) return@launch

                pbTelemetryLoading.visibility = View.GONE

                if (telemetry != null && (telemetry.speed != null || telemetry.rpm != null || telemetry.gear != null || telemetry.throttle != null || telemetry.brake != null)) {
                    displayTelemetryData(telemetry, isLive)
                } else {
                    showTelemetryUnavailable("Sin telemetría disponible para #$driverNumber en esta sesión")
                }
            } catch (e: Exception) {
                AppLogger.w("TelemetryManager", "Error al obtener telemetría de #$driverNumber", e)
                if (currentDriverNumber == driverNumber) {
                    pbTelemetryLoading.visibility = View.GONE
                    showTelemetryError("Error al obtener telemetría de #$driverNumber")
                }
            }
        }
    }

    private fun showTelemetryLoading(isLive: Boolean) {
        pbTelemetryLoading.visibility = View.VISIBLE
        tvTelemetrySpeed.text = "--"
        tvTelemetryGear.text = "--"
        tvTelemetryRpm.text = "Cargando telemetría..."
        pbTelemetryRpm.progress = 0
        tvTelemetryThrottle.text = "Acelerador: --"
        pbTelemetryThrottle.progress = 0
        tvTelemetryBrake.text = "Freno: --"
        pbTelemetryBrake.progress = 0
        tvTelemetryDrs.text = "DRS: --"
        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
        tvTelemetrySessionStatus.text = if (isLive) "🔴 Telemetría en Vivo (cargando...)" else "🏁 Telemetría Histórica (cargando...)"
    }

    private fun displayTelemetryData(telemetry: CarTelemetry, isLive: Boolean) {
        tvTelemetrySpeed.text = PitWallFormatter.formatSpeed(telemetry.speed)
        tvTelemetryGear.text = PitWallFormatter.formatGear(telemetry.gear)
        tvTelemetryRpm.text = PitWallFormatter.formatRpm(telemetry.rpm)
        pbTelemetryRpm.progress = (telemetry.rpm ?: 0).coerceIn(0, 15000)
        tvTelemetryThrottle.text = PitWallFormatter.formatThrottle(telemetry.throttle)
        pbTelemetryThrottle.progress = (telemetry.throttle ?: 0).coerceIn(0, 100)
        tvTelemetryBrake.text = PitWallFormatter.formatBrake(telemetry.brake)
        pbTelemetryBrake.progress = if ((telemetry.brake ?: 0) > 0) 100 else 0

        val drsState = PitWallFormatter.resolveDrsState(telemetry.drs)
        tvTelemetryDrs.text = drsState.text
        tvTelemetryDrs.setBackgroundColor(Color.parseColor(drsState.colorHex))

        tvTelemetrySessionStatus.text = PitWallFormatter.formatTelemetrySessionStatus(isLive, hasSession = true)
    }

    fun showTelemetryUnavailable(message: String) {
        pbTelemetryLoading.visibility = View.GONE
        tvTelemetrySpeed.text = "--"
        tvTelemetryGear.text = "--"
        tvTelemetryRpm.text = message
        pbTelemetryRpm.progress = 0
        tvTelemetryThrottle.text = "Acelerador: --"
        pbTelemetryThrottle.progress = 0
        tvTelemetryBrake.text = "Freno: --"
        pbTelemetryBrake.progress = 0
        tvTelemetryDrs.text = "DRS: --"
        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
        tvTelemetrySessionStatus.text = "No disponible"
    }

    fun showTelemetryError(message: String) {
        pbTelemetryLoading.visibility = View.GONE
        tvTelemetrySpeed.text = "--"
        tvTelemetryGear.text = "--"
        tvTelemetryRpm.text = message
        pbTelemetryRpm.progress = 0
        tvTelemetryThrottle.text = "Acelerador: --"
        pbTelemetryThrottle.progress = 0
        tvTelemetryBrake.text = "Freno: --"
        pbTelemetryBrake.progress = 0
        tvTelemetryDrs.text = "DRS: --"
        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
        tvTelemetrySessionStatus.text = "Error de conexión"
    }

    fun resetTelemetryPanel(statusMessage: String = "Sin datos") {
        showTelemetryUnavailable(statusMessage)
    }

    fun clear() {
        activeTelemetryJob?.cancel()
        currentDriverNumber = null
    }
}
