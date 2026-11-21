package com.f1pitwall.tv.network

import java.util.Locale

/**
 * OpenF1 publica el endpoint /intervals durante carreras.
 * En prácticas y clasificación los huecos se representan como "--".
 */
object OpenF1SessionPolicy {
    fun supportsIntervals(sessionType: String?, sessionName: String?): Boolean {
        fun isRaceFormat(value: String?): Boolean {
            val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return normalized == "race" ||
                normalized == "sprint" ||
                normalized == "carrera" ||
                normalized == "carrera sprint"
        }

        return isRaceFormat(sessionType) || isRaceFormat(sessionName)
    }
}
