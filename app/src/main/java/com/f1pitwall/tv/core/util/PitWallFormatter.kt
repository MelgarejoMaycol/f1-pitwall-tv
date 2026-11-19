package com.f1pitwall.tv.core.util

enum class DrsDisplayState(val text: String, val colorHex: String) {
    ACTIVE("DRS ACTIVO", "#15803D"),
    INACTIVE("DRS INACTIVO", "#4B5563"),
    UNAVAILABLE("DRS: --", "#4B5563")
}

object PitWallFormatter {

    fun formatSpeed(speed: Int?): String {
        return speed?.toString() ?: "--"
    }

    fun formatGear(gear: Int?): String {
        return when (gear) {
            null -> "--"
            0 -> "N"
            else -> gear.toString()
        }
    }

    fun formatRpm(rpm: Int?): String {
        return if (rpm != null) {
            String.format(java.util.Locale.US, "%,d RPM", rpm)
        } else {
            "RPM: --"
        }
    }

    fun formatThrottle(throttle: Int?): String {
        return if (throttle != null) {
            "Acelerador: $throttle%"
        } else {
            "Acelerador: --"
        }
    }

    fun formatBrake(brake: Int?): String {
        return if (brake != null) {
            val brk = if (brake > 0) 100 else 0
            "Freno: $brk%"
        } else {
            "Freno: --"
        }
    }

    fun resolveDrsState(drs: Int?): DrsDisplayState {
        return when (drs) {
            in listOf(10, 12, 14) -> DrsDisplayState.ACTIVE
            in listOf(0, 1, 8, 9) -> DrsDisplayState.INACTIVE
            else -> DrsDisplayState.UNAVAILABLE
        }
    }

    fun formatTelemetrySessionStatus(isLive: Boolean, hasSession: Boolean): String {
        return when {
            !hasSession -> "No disponible"
            isLive -> "🔴 Telemetría en Vivo"
            else -> "🏁 Telemetría Histórica (Sesión Finalizada)"
        }
    }

    fun formatWeatherTemperature(trackTemp: Double?, airTemp: Double?): String {
        val trackStr = trackTemp?.let { "${it}°C" } ?: "--"
        val airStr = airTemp?.let { "${it}°C" } ?: "--"
        return "Pista: $trackStr | Aire: $airStr"
    }

    fun formatRadioTitle(driverName: String, driverNumber: Int, hasRecording: Boolean): String {
        return if (hasRecording) {
            "📻 Audio de Radio ($driverName #$driverNumber): Grabación disponible"
        } else {
            "📻 Audio de Radio ($driverName #$driverNumber): Grabación no disponible"
        }
    }
}
