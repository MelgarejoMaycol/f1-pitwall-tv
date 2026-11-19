package com.f1pitwall.tv.ui.discovery

/**
 * Canal de reproducción descubierto en la página de un evento específico.
 *
 * @param name   Nombre visible en el botón (ej: "Live 1", "Live 2", "Live 3", "Servidor 1")
 * @param index  Índice dentro del selector de servidores en la página web
 * @param type   "TAB" (botón de selector) | "IFRAME" | "VIDEO_SRC"
 * @param url    URL directa del iframe o stream si está disponible
 */
data class DiscoveredChannel(
    val name: String,
    val index: Int = 0,
    val type: String = "TAB",
    val url: String = ""
)
