package com.f1pitwall.tv.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.f1pitwall.tv.core.config.VideoProviderItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("f1_pitwall_tv_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var timezone: String
        get() = prefs.getString(KEY_TIMEZONE, "America/Bogota") ?: "America/Bogota"
        set(value) = prefs.edit().putString(KEY_TIMEZONE, value).apply()

    var activeVideoProvider: String
        get() = prefs.getString(KEY_VIDEO_PROVIDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VIDEO_PROVIDER, value).apply()

    var defaultDisplayMode: String
        get() = prefs.getString(KEY_DISPLAY_MODE, "RACE_TIMING") ?: "RACE_TIMING"
        set(value) = prefs.edit().putString(KEY_DISPLAY_MODE, value).apply()

    var lastMeetingKey: Long
        get() = prefs.getLong(KEY_LAST_MEETING, 1229L)
        set(value) = prefs.edit().putLong(KEY_LAST_MEETING, value).apply()

    fun getCustomSources(): List<VideoProviderItem> {
        val json = prefs.getString(KEY_CUSTOM_SOURCES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<VideoProviderItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomSource(source: VideoProviderItem) {
        val current = getCustomSources().toMutableList()
        val index = current.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            current[index] = source
        } else {
            current.add(source)
        }
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_CUSTOM_SOURCES, json).apply()
    }

    fun deleteCustomSource(id: String) {
        val current = getCustomSources().filter { it.id != id }
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_CUSTOM_SOURCES, json).apply()
        if (activeVideoProvider == id) {
            activeVideoProvider = current.firstOrNull()?.id ?: ""
        }
    }

    fun getSelectedSource(): VideoProviderItem? {
        val sources = getCustomSources()
        if (sources.isEmpty()) return null
        return sources.find { it.id == activeVideoProvider } ?: sources.find { it.isDefault } ?: sources.first()
    }

    companion object {
        private const val KEY_TIMEZONE = "pref_timezone"
        private const val KEY_VIDEO_PROVIDER = "pref_video_provider"
        private const val KEY_DISPLAY_MODE = "pref_display_mode"
        private const val KEY_LAST_MEETING = "pref_last_meeting"
        private const val KEY_CUSTOM_SOURCES = "pref_custom_sources"
    }
}
