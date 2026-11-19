package com.f1pitwall.tv.core.config

import android.content.Context
import com.f1pitwall.tv.core.storage.PreferencesManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class VideoProviderItem(
    @SerializedName(value = "id", alternate = ["identificador"]) val id: String,
    @SerializedName(value = "name", alternate = ["nombre", "title", "titulo"]) val name: String,
    @SerializedName(value = "streamUrl", alternate = ["url", "enlace", "link"]) val streamUrl: String,
    @SerializedName(value = "type", alternate = ["tipo"]) val type: String = "HLS",
    @SerializedName(value = "isDefault", alternate = ["predeterminado", "porDefecto"]) val isDefault: Boolean = false
)

data class F1ContentItem(
    @SerializedName("id") val id: String,
    @SerializedName(value = "title", alternate = ["titulo", "name", "nombre"]) val title: String,
    @SerializedName(value = "description", alternate = ["descripcion"]) val description: String,
    @SerializedName(value = "streamUrl", alternate = ["url", "enlace", "link"]) val streamUrl: String,
    @SerializedName(value = "type", alternate = ["tipo"]) val type: String = "HLS",
    @SerializedName("badge") val badge: String = "CONTENIDO OFICIAL",
    @SerializedName("isAvailable") val isAvailable: Boolean = true
)

data class VideoSourcesFile(
    @SerializedName(value = "providers", alternate = ["proveedores", "sources", "fuentes"]) val providers: List<VideoProviderItem>?,
    @SerializedName(value = "contentHub", alternate = ["contenido", "hub"]) val contentHub: List<F1ContentItem>?,
    @SerializedName(value = "searchTemplates", alternate = ["plantillasBusqueda"]) val searchTemplates: List<String>?
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

    fun parseProvidersFromJson(json: String): List<VideoProviderItem> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            if (trimmed.startsWith("[")) {
                val listType = object : TypeToken<List<VideoProviderItem>>() {}.type
                Gson().fromJson<List<VideoProviderItem>>(trimmed, listType) ?: emptyList()
            } else {
                val parsed = Gson().fromJson(trimmed, VideoSourcesFile::class.java)
                parsed.providers ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadProviders(context: Context): List<VideoProviderItem> {
        val userSources = PreferencesManager(context).getCustomSources()
        val assetSources = try {
            val json = context.assets.open("video_sources.json").bufferedReader().use { it.readText() }
            parseProvidersFromJson(json)
        } catch (_: Exception) {
            emptyList()
        }
        // Custom sources override asset entries with matching IDs; otherwise both are available.
        return (assetSources + userSources).associateBy { it.id }.values.toList()
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
