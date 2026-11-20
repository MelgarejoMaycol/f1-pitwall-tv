package com.f1pitwall.tv.core.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class VideoProviderItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("streamUrl") val streamUrl: String,
    @SerializedName("type") val type: String = "HLS",
    @SerializedName("isDefault") val isDefault: Boolean = false
)

data class F1ContentItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("streamUrl") val streamUrl: String,
    @SerializedName("type") val type: String = "HLS",
    @SerializedName("badge") val badge: String = "CONTENIDO OFICIAL",
    @SerializedName("isAvailable") val isAvailable: Boolean = true
)

data class VideoSourcesFile(
    @SerializedName("providers") val providers: List<VideoProviderItem>?,
    @SerializedName("contentHub") val contentHub: List<F1ContentItem>?,
    @SerializedName("searchTemplates") val searchTemplates: List<String>?
)

object VideoSourcesConfig {

    fun loadContentHub(context: Context): List<F1ContentItem> {
        return try {
            val json = context.assets.open("video_sources.json").bufferedReader().use { it.readText() }
            val parsed = Gson().fromJson(json, VideoSourcesFile::class.java)
            parsed.contentHub ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun loadProviders(context: Context): List<VideoProviderItem> {
        return try {
            val json = context.assets.open("video_sources.json").bufferedReader().use { it.readText() }
            val parsed = Gson().fromJson(json, VideoSourcesFile::class.java)
            parsed.providers ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(
                VideoProviderItem(
                    id = "hls_stream_1",
                    name = "Señal HLS Principal",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    type = "HLS",
                    isDefault = true
                )
            )
        }
    }

    /**
     * Genera términos de búsqueda inteligentes adaptados al Gran Premio y tipo de sesión activa.
     */
    fun generateSmartSearchQueries(gpName: String, sessionType: String?): List<String> {
        val cleanGpName = gpName
            .replace("Grand Prix", "", ignoreCase = true)
            .replace("Gran Premio", "", ignoreCase = true)
            .replace("FORMULA 1", "", ignoreCase = true)
            .trim()

        val normalizedSession = when {
            sessionType == null -> "carrera"
            sessionType.contains("practice", ignoreCase = true) || sessionType.contains("práctica", ignoreCase = true) -> "prácticas"
            sessionType.contains("qualifying", ignoreCase = true) || sessionType.contains("clasificación", ignoreCase = true) -> "clasificación"
            sessionType.contains("sprint", ignoreCase = true) -> "Sprint"
            sessionType.contains("race", ignoreCase = true) || sessionType.contains("carrera", ignoreCase = true) -> "carrera"
            else -> sessionType.lowercase()
        }

        return listOf(
            "GP $cleanGpName F1",
            "Gran Premio de $cleanGpName Fórmula 1",
            "Fórmula 1 $cleanGpName en directo",
            "F1 $cleanGpName $normalizedSession",
            "F1 $cleanGpName clasificación",
            "Carrera Fórmula 1 $cleanGpName"
        ).distinct()
    }
}
