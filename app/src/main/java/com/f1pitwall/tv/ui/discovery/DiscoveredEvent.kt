package com.f1pitwall.tv.ui.discovery

/**
 * Evento de F1 descubierto automáticamente en la página del directorio.
 *
 * @param title   Título del evento (ej: "Las Vegas Grand Prix vs Practice 1")
 * @param league  Categoría o liga (ej: "Formula 1", "Racing")
 * @param url     URL de la página de reproducción del evento
 * @param score   Puntaje de relevancia según palabras clave y sesión
 * @param session Nombre de la sesión detectada (ej: "Practice 1", "FP2", "Qualifying", "Race")
 */
data class DiscoveredEvent(
    val title: String,
    val league: String,
    val url: String,
    val score: Int = 0,
    val session: String = ""
)
