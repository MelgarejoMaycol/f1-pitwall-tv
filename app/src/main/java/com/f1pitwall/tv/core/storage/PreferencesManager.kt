package com.f1pitwall.tv.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.f1pitwall.tv.core.config.BroadcastSource

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("f1_pitwall_tv_prefs", Context.MODE_PRIVATE)

    var broadcastSource: BroadcastSource
        get() = BroadcastSource.fromKey(prefs.getString(KEY_BROADCAST_SOURCE, BroadcastSource.FLX.key))
        set(value) = prefs.edit().putString(KEY_BROADCAST_SOURCE, value.key).apply()

    var timezone: String
        get() = prefs.getString(KEY_TIMEZONE, "America/Bogota") ?: "America/Bogota"
        set(value) = prefs.edit().putString(KEY_TIMEZONE, value).apply()

    var activeVideoProvider: String
        get() = prefs.getString(KEY_VIDEO_PROVIDER, "DEMO_HLS") ?: "DEMO_HLS"
        set(value) = prefs.edit().putString(KEY_VIDEO_PROVIDER, value).apply()

    var defaultDisplayMode: String
        get() = prefs.getString(KEY_DISPLAY_MODE, "RACE_TIMING") ?: "RACE_TIMING"
        set(value) = prefs.edit().putString(KEY_DISPLAY_MODE, value).apply()

    var lastMeetingKey: Long
        get() = prefs.getLong(KEY_LAST_MEETING, 1229L)
        set(value) = prefs.edit().putLong(KEY_LAST_MEETING, value).apply()

    companion object {
        private const val KEY_BROADCAST_SOURCE = "pref_broadcast_source"
        private const val KEY_TIMEZONE = "pref_timezone"
        private const val KEY_VIDEO_PROVIDER = "pref_video_provider"
        private const val KEY_DISPLAY_MODE = "pref_display_mode"
        private const val KEY_LAST_MEETING = "pref_last_meeting"
    }
}
