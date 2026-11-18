package com.f1pitwall.tv.network

import com.f1pitwall.tv.data.model.JolpicaRace
import com.f1pitwall.tv.data.model.JolpicaResponse
import com.f1pitwall.tv.data.model.JolpicaResult
import com.f1pitwall.tv.data.model.RaceTableContainer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JolpicaApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://api.jolpi.ca/ergast/f1"
    private val cache = ConcurrentHashMap<String, Any>()

    private suspend fun <T> get(url: String, typeToken: TypeToken<T>, useCache: Boolean = true): T? = withContext(Dispatchers.IO) {
        if (useCache && cache.containsKey(url)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[url] as? T
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "F1PitWallTV/1.0")
                .build()

            val response = client.newCall(request).execute()
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

    suspend fun getSchedule(year: Int = 2026): List<JolpicaRace> {
        val type = object : TypeToken<JolpicaResponse<RaceTableContainer>>() {}
        val response = get("$baseUrl/$year.json?limit=50", type, useCache = true)
        return response?.mrData?.raceTable?.races ?: emptyList()
    }

    suspend fun getRaceResults(year: Int = 2026, round: Int): List<JolpicaResult> {
        val type = object : TypeToken<JolpicaResponse<RaceTableContainer>>() {}
        val response = get("$baseUrl/$year/$round/results.json", type, useCache = true)
        return response?.mrData?.raceTable?.races?.firstOrNull()?.results ?: emptyList()
    }

    suspend fun getLatestRaceResults(year: Int = 2026): Pair<JolpicaRace, List<JolpicaResult>>? {
        val type = object : TypeToken<JolpicaResponse<RaceTableContainer>>() {}
        val response = get("$baseUrl/$year/results.json?limit=100", type, useCache = true)
        val races = response?.mrData?.raceTable?.races ?: return null
        val lastFinished = races.lastOrNull { !it.results.isNullOrEmpty() } ?: return null
        return Pair(lastFinished, lastFinished.results ?: emptyList())
    }
}
