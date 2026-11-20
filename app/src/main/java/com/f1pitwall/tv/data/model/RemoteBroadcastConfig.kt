package com.f1pitwall.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de datos para el catálogo de transmisiones administrado remotamente desde GitHub.
 */
data class RemoteBroadcastConfig(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("updatedAt") val updatedAt: String = "",
    @SerializedName("providers") val providers: List<RemoteProviderItem> = emptyList(),
    @SerializedName("channels") val channels: List<RemoteChannelItem> = emptyList()
) {
    fun getEnabledProviders(): List<RemoteProviderItem> {
        return providers.filter { it.enabled }.sortedBy { it.order }
    }

    fun getEnabledChannels(): List<RemoteChannelItem> {
        return channels.filter { it.enabled }.sortedBy { it.order }
    }

    fun isValid(): Boolean {
        if (schemaVersion < 1) return false
        if (providers.isEmpty() && channels.isEmpty()) return false

        val validProviderTypes = setOf("FLX", "FUTBOL_VIVO", "FUTBOL_LIBRE", "WEB")
        for (p in providers) {
            if (p.id.isBlank() || p.name.isBlank() || p.url.isBlank()) return false
            if (p.type.uppercase() !in validProviderTypes) return false
            if (!isValidUrl(p.url)) return false
        }

        val validChannelTypes = setOf("HLS", "DASH", "MP4", "WEB")
        for (c in channels) {
            if (c.id.isBlank() || c.name.isBlank() || c.url.isBlank()) return false
            if (c.type.uppercase() !in validChannelTypes) return false
            if (!isValidUrl(c.url)) return false
        }

        return true
    }

    companion object {
        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.startsWith("javascript:", ignoreCase = true) ||
                trimmed.startsWith("file:", ignoreCase = true) ||
                trimmed.startsWith("content:", ignoreCase = true)) {
                return false
            }
            return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                    trimmed.length > 10
        }
    }
}

data class RemoteProviderItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String = "WEB",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("order") val order: Int = 0
)

data class RemoteChannelItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String = "HLS",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("order") val order: Int = 0
)
