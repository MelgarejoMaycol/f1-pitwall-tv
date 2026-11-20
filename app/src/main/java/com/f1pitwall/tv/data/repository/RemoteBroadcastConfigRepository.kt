package com.f1pitwall.tv.data.repository

import android.content.Context
import android.util.Log
import com.f1pitwall.tv.data.model.RemoteBroadcastConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class ConfigOrigin {
    REMOTE_GITHUB,
    LOCAL_CACHE,
    LOCAL_ASSETS
}

sealed class ConfigSyncResult {
    data class Success(
        val config: RemoteBroadcastConfig,
        val origin: ConfigOrigin,
        val providerCount: Int,
        val enabledChannelCount: Int,
        val updatedAt: String,
        val message: String
    ) : ConfigSyncResult()

    data class Error(
        val fallbackConfig: RemoteBroadcastConfig?,
        val origin: ConfigOrigin?,
        val errorMessage: String
    ) : ConfigSyncResult()
}

class RemoteBroadcastConfigRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val remoteUrl: String = DEFAULT_REMOTE_URL
) {
    companion object {
        const val TAG = "RemoteBroadcastRepo"
        const val DEFAULT_REMOTE_URL =
            "https://raw.githubusercontent.com/MelgarejoMaycol/f1-pitwall-tv/test/spanish-broadcast-selection/config/broadcast_sources.json"
        const val CACHE_FILE_NAME = "remote_broadcast_sources_cache.json"
        const val PREFS_NAME = "remote_broadcast_config_prefs"
        const val KEY_ETAG = "last_etag"
        const val KEY_LAST_MODIFIED = "last_modified"
    }

    private val gson = Gson()
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var inMemoryConfig: RemoteBroadcastConfig? = null

    /**
     * Sincroniza la configuración descargándola de GitHub o usando el fallback de caché / assets.
     */
    suspend fun syncConfig(forceRemote: Boolean = false): ConfigSyncResult = withContext(Dispatchers.IO) {
        // 1. Intentar descargar desde GitHub
        try {
            val requestBuilder = Request.Builder()
                .url(remoteUrl)
                .addHeader("User-Agent", "F1PitWallTV-Android")

            val lastEtag = prefs.getString(KEY_ETAG, null)
            val lastModified = prefs.getString(KEY_LAST_MODIFIED, null)

            if (!forceRemote) {
                if (!lastEtag.isNullOrBlank()) {
                    requestBuilder.addHeader("If-None-Match", lastEtag)
                }
                if (!lastModified.isNullOrBlank()) {
                    requestBuilder.addHeader("If-Modified-Since", lastModified)
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code == 304 && cacheFile.exists()) {
                val cached = loadFromCache()
                if (cached != null && cached.isValid()) {
                    inMemoryConfig = cached
                    val enabledChannels = cached.getEnabledChannels()
                    return@withContext ConfigSyncResult.Success(
                        config = cached,
                        origin = ConfigOrigin.LOCAL_CACHE,
                        providerCount = cached.providers.size,
                        enabledChannelCount = enabledChannels.size,
                        updatedAt = cached.updatedAt,
                        message = "Catálogo sin cambios en GitHub. Usando copia local en caché."
                    )
                }
            }

            if (response.isSuccessful) {
                val jsonBody = response.body?.string().orEmpty()
                val parsed = gson.fromJson(jsonBody, RemoteBroadcastConfig::class.java)

                if (parsed != null && parsed.isValid()) {
                    saveToCache(jsonBody)
                    val newEtag = response.header("ETag")
                    val newLastModified = response.header("Last-Modified")
                    prefs.edit()
                        .putString(KEY_ETAG, newEtag)
                        .putString(KEY_LAST_MODIFIED, newLastModified)
                        .apply()

                    inMemoryConfig = parsed
                    val enabledChannels = parsed.getEnabledChannels()
                    return@withContext ConfigSyncResult.Success(
                        config = parsed,
                        origin = ConfigOrigin.REMOTE_GITHUB,
                        providerCount = parsed.providers.size,
                        enabledChannelCount = enabledChannels.size,
                        updatedAt = parsed.updatedAt,
                        message = "Catálogo descargado con éxito desde GitHub."
                    )
                } else {
                    Log.w(TAG, "El JSON remoto descargado no es válido. Descartándolo para no sobreescribir la configuración previa.")
                }
            } else {
                Log.w(TAG, "GitHub retornó código HTTP no exitoso: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al descargar catálogo remoto desde GitHub: ${e.message}")
        }

        // 2. Fallback a caché local
        val cached = loadFromCache()
        if (cached != null && cached.isValid()) {
            inMemoryConfig = cached
            val enabledChannels = cached.getEnabledChannels()
            return@withContext ConfigSyncResult.Success(
                config = cached,
                origin = ConfigOrigin.LOCAL_CACHE,
                providerCount = cached.providers.size,
                enabledChannelCount = enabledChannels.size,
                updatedAt = cached.updatedAt,
                message = "GitHub no disponible. Usando última copia válida en caché."
            )
        }

        // 3. Fallback a assets
        val assetConfig = loadFromAssets()
        if (assetConfig != null && assetConfig.isValid()) {
            inMemoryConfig = assetConfig
            val enabledChannels = assetConfig.getEnabledChannels()
            return@withContext ConfigSyncResult.Success(
                config = assetConfig,
                origin = ConfigOrigin.LOCAL_ASSETS,
                providerCount = assetConfig.providers.size,
                enabledChannelCount = enabledChannels.size,
                updatedAt = assetConfig.updatedAt,
                message = "GitHub y caché no disponibles. Usando catálogo integrado en assets."
            )
        }

        return@withContext ConfigSyncResult.Error(
            fallbackConfig = inMemoryConfig,
            origin = null,
            errorMessage = "No fue posible obtener el catálogo remoto ni utilizar copias locales."
        )
    }

    fun getCurrentConfig(): RemoteBroadcastConfig {
        inMemoryConfig?.let { return it }
        loadFromCache()?.takeIf { it.isValid() }?.let {
            inMemoryConfig = it
            return it
        }
        loadFromAssets()?.takeIf { it.isValid() }?.let {
            inMemoryConfig = it
            return it
        }
        return RemoteBroadcastConfig(
            schemaVersion = 1,
            updatedAt = "2026-11-20T15:00:00Z"
        )
    }

    fun saveToCache(rawJson: String) {
        try {
            cacheFile.writeText(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar la copia en caché: ${e.message}")
        }
    }

    fun loadFromCache(): RemoteBroadcastConfig? {
        return try {
            if (!cacheFile.exists()) return null
            val raw = cacheFile.readText()
            gson.fromJson(raw, RemoteBroadcastConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo caché: ${e.message}")
            null
        }
    }

    fun loadFromAssets(): RemoteBroadcastConfig? {
        return try {
            val assetNames = listOf("broadcast_sources.json", "video_sources.json")
            for (name in assetNames) {
                try {
                    val raw = context.assets.open(name).bufferedReader().use { it.readText() }
                    val parsed = gson.fromJson(raw, RemoteBroadcastConfig::class.java)
                    if (parsed != null && parsed.isValid()) return parsed
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo assets: ${e.message}")
            null
        }
    }
}
