package com.f1pitwall.tv.model

/**
 * Representa una transmisión identificada en español con su cadena (ESPN, DAZN, FOX Sports, etc.)
 * y servidor (Server 1, Server 2).
 *
 * Ejemplo requerido por especificación:
 * - Transmisión 1 — ESPN — Español — Server 1
 * - Transmisión 2 — DAZN — Español — Server 1
 * - Transmisión 3 — FOX Sports — Español — Server 2
 */
data class SpanishBroadcast(
    val index: Int,
    val network: String?,
    val language: String = "Español",
    val serverName: String = "Server 1",
    val originalTitle: String = "",
    val streamUrl: String?,
    val isOnline: Boolean = true,
    val clickActionJs: String? = null,
    val sourceId: String? = null,
    val customFormattedTitle: String? = null,
    val customButtonLabel: String? = null
) {
    /**
     * Construye el nombre exacto de la transmisión conforme a la especificación:
     * Transmisión X — [Cadena] — Español — [Servidor]
     * O el formato personalizado si está presente (para Fútbol Libre).
     */
    fun getFormattedTitle(): String {
        if (!customFormattedTitle.isNullOrBlank()) {
            return customFormattedTitle
        }
        val parts = mutableListOf<String>()
        parts.add("Transmisión $index")
        if (!network.isNullOrBlank()) {
            parts.add(network)
        }
        if (language.isNotBlank()) {
            parts.add(language)
        }
        if (serverName.isNotBlank()) {
            parts.add(serverName)
        }
        return parts.joinToString(" — ")
    }

    /**
     * Etiqueta concisa para los botones del control remoto en Android TV.
     */
    fun getButtonLabel(): String {
        if (!customButtonLabel.isNullOrBlank()) {
            return customButtonLabel
        }
        val net = network ?: "Opción $index"
        val srv = when {
            serverName.contains("1") -> "S1"
            serverName.contains("2") -> "S2"
            else -> serverName
        }
        return "T$index: $net ($srv)"
    }
}
