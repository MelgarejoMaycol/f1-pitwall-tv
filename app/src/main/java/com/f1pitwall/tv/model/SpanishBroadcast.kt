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
    val serverName: String,
    val originalTitle: String,
    val streamUrl: String?,
    val isOnline: Boolean = true,
    val clickActionJs: String? = null
) {
    /**
     * Construye el nombre exacto de la transmisión conforme a la especificación:
     * Transmisión X — [Cadena] — Español — [Servidor]
     */
    fun getFormattedTitle(): String {
        val parts = mutableListOf<String>()
        parts.add("Transmisión $index")
        if (!network.isNullOrBlank()) {
            parts.add(network)
        }
        parts.add(language)
        parts.add(serverName)
        return parts.joinToString(" — ")
    }

    /**
     * Etiqueta concisa para los botones del control remoto en Android TV.
     */
    fun getButtonLabel(): String {
        val net = network ?: "Opción $index"
        val srv = when {
            serverName.contains("1") -> "S1"
            serverName.contains("2") -> "S2"
            else -> serverName
        }
        return "T$index: $net ($srv)"
    }
}
