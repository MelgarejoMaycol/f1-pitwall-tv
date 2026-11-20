package com.f1pitwall.tv.network

import com.f1pitwall.tv.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OpenF1ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://api.openf1.org/v1"

    // In-memory cache to avoid redundant calls and respect OpenF1 rate limits
    private val cache = ConcurrentHashMap<String, Any>()
    private val requestMutex = Mutex()
    private var lastRequestTime = 0L

    private suspend fun <T> get(url: String, typeToken: TypeToken<T>, useCache: Boolean = true): T? = withContext(Dispatchers.IO) {
        if (useCache && cache.containsKey(url)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[url] as? T
        }

        // Throttle to respect OpenF1's 3 requests/second limit
        requestMutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTime
            if (timeSinceLast < 380) {
                delay(380 - timeSinceLast)
            }
            lastRequestTime = System.currentTimeMillis()

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "F1PitWallTV/1.0")
                    .build()
                var response = client.newCall(request).execute()

                // If rate limited, wait 1 second and retry once
                if (response.code == 429) {
                    response.close()
                    delay(1000)
                    response = client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val result = gson.fromJson<T>(body, typeToken.type)
                if (result != null && useCache) {
                    cache[url] = result
                }
                result
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getMeetings(year: Int = 2024): List<Meeting> {
        val type = object : TypeToken<List<Meeting>>() {}
        return get("$baseUrl/meetings?year=$year", type, useCache = true) ?: emptyList()
    }

    suspend fun getSessions(meetingKey: Long): List<Session> {
        val type = object : TypeToken<List<Session>>() {}
        return get("$baseUrl/sessions?meeting_key=$meetingKey", type, useCache = true) ?: emptyList()
    }

    suspend fun getLatestSession(): Session? {
        val type = object : TypeToken<List<Session>>() {}
        val list = get("$baseUrl/sessions?meeting_key=latest", type, useCache = false) ?: emptyList()
        val now = System.currentTimeMillis()
        val timed = list.mapNotNull { session ->
            val start = com.f1pitwall.tv.core.util.TimeUtils.parseUtc(session.dateStart)?.time
            val end = com.f1pitwall.tv.core.util.TimeUtils.parseUtc(session.dateEnd)?.time
            if (start != null && end != null) Triple(session, start, end) else null
        }
        return timed.firstOrNull { now >= it.second && now <= it.third }?.first
            ?: timed.filter { it.second > now }.minByOrNull { it.second }?.first
            ?: timed.filter { it.third < now }.maxByOrNull { it.third }?.first
    }

    /**
     * Retrieves the complete season calendar. OpenF1 exposes future sessions through
     * the year filter; selecting sessions?meeting_key=latest alone is insufficient.
     */
    suspend fun getSessionsForYear(year: Int): List<Session> {
        val type = object : TypeToken<List<Session>>() {}
        return get("$baseUrl/sessions?year=$year&limit=300", type, useCache = false) ?: emptyList()
    }

    suspend fun getDrivers(sessionKey: Long): List<Driver> {
        val type = object : TypeToken<List<Driver>>() {}
        return get("$baseUrl/drivers?session_key=$sessionKey", type, useCache = true) ?: emptyList()
    }

    suspend fun getLatestPositions(sessionKey: Long): List<Position> {
        val type = object : TypeToken<List<Position>>() {}
        val all = get("$baseUrl/position?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.groupBy { it.driverNumber }
            .mapNotNull { it.value.lastOrNull() }
            .sortedBy { it.position }
    }

    suspend fun getLatestIntervals(sessionKey: Long): List<Interval> {
        val type = object : TypeToken<List<Interval>>() {}
        val all = get("$baseUrl/intervals?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.groupBy { it.driverNumber }.mapNotNull { it.value.lastOrNull() }
    }

    suspend fun getLatestLaps(sessionKey: Long): List<Lap> {
        val type = object : TypeToken<List<Lap>>() {}
        val all = get("$baseUrl/laps?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.groupBy { it.driverNumber }.mapNotNull { it.value.lastOrNull() }
    }

    suspend fun getStints(sessionKey: Long): List<Stint> {
        val type = object : TypeToken<List<Stint>>() {}
        val all = get("$baseUrl/stints?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.groupBy { it.driverNumber }.mapNotNull { it.value.lastOrNull() }
    }

    suspend fun getTeamRadios(sessionKey: Long): List<TeamRadio> {
        val type = object : TypeToken<List<TeamRadio>>() {}
        val all = get("$baseUrl/team_radio?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.takeLast(10).reversed()
    }

    suspend fun getTeamRadiosForDriver(sessionKey: Long, driverNumber: Int): List<TeamRadio> {
        val type = object : TypeToken<List<TeamRadio>>() {}
        val all = get("$baseUrl/team_radio?session_key=$sessionKey&driver_number=$driverNumber", type, useCache = true) ?: emptyList()
        return all.reversed()
    }

    suspend fun getLatestRaceControl(sessionKey: Long): RaceControl? {
        val type = object : TypeToken<List<RaceControl>>() {}
        val all = get("$baseUrl/race_control?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.lastOrNull()
    }

    suspend fun getLatestWeather(sessionKey: Long): Weather? {
        val type = object : TypeToken<List<Weather>>() {}
        val all = get("$baseUrl/weather?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.lastOrNull()
    }

    suspend fun getLatestTelemetry(sessionKey: Long, driverNumber: Int): CarTelemetry? {
        val type = object : TypeToken<List<CarTelemetry>>() {}
        val all = get("$baseUrl/car_data?session_key=$sessionKey&driver_number=$driverNumber&speed>=0", type, useCache = false) ?: emptyList()
        return all.lastOrNull()
    }
}
