package com.f1pitwall.tv.core.config

/**
 * Fuente de transmisiones seleccionable por el usuario en Ajustes.
 * Determina de qué página se obtendrán los servidores y canales disponibles en Muro de Pits.
 */
enum class BroadcastSource(
    val key: String,
    val displayName: String,
    val defaultUrl: String,
    val description: String
) {
    FLX(
        key = "FLX",
        displayName = "FLX",
        defaultUrl = "https://flive.cc.cd/f1",
        description = "Canales en vivo de FLX (flive.cc.cd/f1)."
    ),
    FUTBOL_VIVO(
        key = "FUTBOL_VIVO",
        displayName = "Fútbol Vivo",
        defaultUrl = "https://futbolvivo.tv/",
        description = "Cartelera y servidores de Fútbol Libre / Fútbol Vivo."
    );

    companion object {
        fun fromKey(key: String?): BroadcastSource {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FUTBOL_VIVO
        }
    }
}
