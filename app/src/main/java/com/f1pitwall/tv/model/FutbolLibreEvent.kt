package com.f1pitwall.tv.model

/**
 * Evento o sesión de F1 descubierto en https://futbol-libre.app/agenda
 */
data class FutbolLibreEvent(
    val id: String,
    val title: String,
    val sessionName: String,
    val gpName: String,
    val utcInstant: String?,
    val colombiaTime: String,
    val matchPath: String,
    val fullMatchUrl: String,
    val sources: List<SpanishBroadcast> = emptyList()
) {
    fun getDisplayTitle(): String {
        return "$title • $colombiaTime"
    }

    fun getShortLabel(): String {
        val shortSession = when {
            sessionName.contains("Prácticas Libres #1", ignoreCase = true) || sessionName.contains("Práctica 1", ignoreCase = true) || sessionName.contains("P1", ignoreCase = true) -> "P1"
            sessionName.contains("Prácticas Libres #2", ignoreCase = true) || sessionName.contains("Práctica 2", ignoreCase = true) || sessionName.contains("P2", ignoreCase = true) -> "P2"
            sessionName.contains("Prácticas Libres #3", ignoreCase = true) || sessionName.contains("Práctica 3", ignoreCase = true) || sessionName.contains("P3", ignoreCase = true) -> "P3"
            sessionName.contains("Clasificación", ignoreCase = true) -> "Clasificación"
            sessionName.contains("Sprint", ignoreCase = true) -> "Sprint"
            sessionName.contains("Carrera", ignoreCase = true) -> "Carrera"
            sessionName.contains("Academy", ignoreCase = true) -> "F1 Academy"
            sessionName.contains("On Board", ignoreCase = true) -> "OnBoard"
            else -> sessionName.ifBlank { "Sesión F1" }
        }
        return "$shortSession ($colombiaTime)"
    }
}
