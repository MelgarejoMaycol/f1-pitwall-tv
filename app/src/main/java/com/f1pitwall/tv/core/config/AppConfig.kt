package com.f1pitwall.tv.core.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SeasonConfigData(
    @SerializedName("defaultSeason") val defaultSeason: Int = 2026,
    @SerializedName("supportedSeasons") val supportedSeasons: List<Int> = listOf(2024, 2025, 2026),
    @SerializedName("timeZone") val timeZone: String = "America/Bogota",
    @SerializedName("refreshIntervalLiveSeconds") val refreshIntervalLiveSeconds: Int = 4,
    @SerializedName("primaryDataSource") val primaryDataSource: String = "OpenF1_and_Jolpica"
)

object AppConfig {
    const val DEFAULT_SEASON = 2026
    const val DEFAULT_TIMEZONE = "America/Bogota"
    const val REFRESH_INTERVAL_LIVE_MS = 4000L

    private var loadedConfig: SeasonConfigData? = null

    fun getConfig(context: Context? = null): SeasonConfigData {
        if (loadedConfig != null) return loadedConfig!!
        if (context == null) return SeasonConfigData()

        return try {
            val json = context.assets.open("season_config.json").bufferedReader().use { it.readText() }
            val parsed = Gson().fromJson(json, SeasonConfigData::class.java)
            loadedConfig = parsed
            parsed
        } catch (e: Exception) {
            e.printStackTrace()
            SeasonConfigData()
        }
    }
}
