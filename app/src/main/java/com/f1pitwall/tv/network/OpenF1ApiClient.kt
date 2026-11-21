package com.f1pitwall.tv.network

import com.f1pitwall.tv.model.*
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class OpenF1AccessStatus {
    UNKNOWN,
    AVAILABLE,
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    NETWORK_ERROR,
    REQUEST_FAILED
}

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

    @Volatile
    private var accessStatus: OpenF1AccessStatus = OpenF1AccessStatus.UNKNOWN

    private var lastRequestTime = 0L

    fun getAccessStatus(): OpenF1AccessStatus = accessStatus

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
                    val statusCode = response.code
                    accessStatus = when (statusCode) {
                        401, 403 -> OpenF1AccessStatus.AUTHENTICATION_REQUIRED
                        429 -> OpenF1AccessStatus.RATE_LIMITED
                        in 500..599 -> OpenF1AccessStatus.SERVICE_UNAVAILABLE
                        else -> OpenF1AccessStatus.REQUEST_FAILED
                    }
                    Log.w(
                        "OpenF1ApiClient",
                        "OpenF1 request rejected with HTTP $statusCode; access status=$accessStatus"
                    )
                    response.close()
                    return@withContext null
                }

                // Una respuesta histórica correcta no debe borrar un 401/403 recibido
                // en un endpoint de datos en vivo durante esta misma sesión.
                if (accessStatus != OpenF1AccessStatus.AUTHENTICATION_REQUIRED) {
                    accessStatus = OpenF1AccessStatus.AVAILABLE
                }

                val body = response.body?.string() ?: return@withContext null
                val result = gson.fromJson<T>(body, typeToken.type)
                if (result != null && useCache) {
                    cache[url] = result
                }
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                accessStatus = OpenF1AccessStatus.NETWORK_ERROR
                Log.w("OpenF1ApiClient", "Network error while requesting OpenF1 data", e)
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
        return get("$baseUrl/sessions?year=$year", type, useCache = false) ?: emptyList()
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

    suspend fun getLapsForDriver(sessionKey: Long, driverNumber: Int): List<Lap> {
        val type = object : TypeToken<List<Lap>>() {}
        val all = get("$baseUrl/laps?session_key=$sessionKey&driver_number=$driverNumber", type, useCache = false) ?: emptyList()
        return all.reversed()
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

    suspend fun getTeamRadiosForDriverLatest(driverNumber: Int): List<TeamRadio> {
        val type = object : TypeToken<List<TeamRadio>>() {}
        val all = get("$baseUrl/team_radio?session_key=latest&driver_number=$driverNumber", type, useCache = false) ?: emptyList()
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

    suspend fun getCarLocations(sessionKey: Long): List<CarLocation> {
        val type = object : TypeToken<List<CarLocation>>() {}
        val all = get("$baseUrl/location?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.filter { it.x != 0f || it.y != 0f }
            .groupBy { it.driverNumber }
            .mapNotNull { it.value.lastOrNull() }
    }

    suspend fun getTrackOutline(sessionKey: Long, driverNumber: Int = 1): List<CarLocation> {
        val type = object : TypeToken<List<CarLocation>>() {}
        var list = get("$baseUrl/location?session_key=$sessionKey&driver_number=$driverNumber", type, useCache = true) ?: emptyList()
        if (list.isEmpty() && driverNumber != 3) {
            list = get("$baseUrl/location?session_key=$sessionKey&driver_number=3", type, useCache = true) ?: emptyList()
        }
        if (list.isEmpty() && driverNumber != 4) {
            list = get("$baseUrl/location?session_key=$sessionKey&driver_number=4", type, useCache = true) ?: emptyList()
        }

        // If current session has no location points (e.g. upcoming race before cars run):
        if (list.isEmpty()) {
            try {
                val sessionType = object : TypeToken<List<Session>>() {}
                val sInfo = get("$baseUrl/sessions?session_key=$sessionKey", sessionType, useCache = true)?.firstOrNull()
                val circuitKey = sInfo?.circuitKey
                if (circuitKey != null) {
                    val circuitRaces = get("$baseUrl/sessions?circuit_key=$circuitKey&session_name=Race", sessionType, useCache = true) ?: emptyList()
                    val pastRace = circuitRaces.filter { (it.year ?: 0) <= 2024 }.lastOrNull()
                        ?: circuitRaces.firstOrNull { it.sessionKey != sessionKey }
                    if (pastRace != null) {
                        list = get("$baseUrl/location?session_key=${pastRace.sessionKey}&driver_number=1", type, useCache = true) ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val validPoints = list.filter { it.x != 0f || it.y != 0f }
        if (validPoints.size <= 250) return validPoints
        val step = (validPoints.size / 250).coerceAtLeast(1)
        return validPoints.filterIndexed { index, _ -> index % step == 0 }
    }

    suspend fun getPitStops(sessionKey: Long): List<PitStop> {
        val type = object : TypeToken<List<PitStop>>() {}
        return get("$baseUrl/pit?session_key=$sessionKey", type, useCache = false) ?: emptyList()
    }

    suspend fun getAllStintsForSession(sessionKey: Long): List<Stint> {
        val type = object : TypeToken<List<Stint>>() {}
        return get("$baseUrl/stints?session_key=$sessionKey", type, useCache = false) ?: emptyList()
    }

    suspend fun getRaceControlMessages(sessionKey: Long): List<RaceControl> {
        val type = object : TypeToken<List<RaceControl>>() {}
        val all = get("$baseUrl/race_control?session_key=$sessionKey", type, useCache = false) ?: emptyList()
        return all.reversed()
    }
}
