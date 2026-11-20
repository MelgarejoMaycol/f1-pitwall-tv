package com.f1pitwall.tv.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.f1pitwall.tv.core.util.BroadcastDetectorUtils
import com.f1pitwall.tv.model.SpanishBroadcast
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extrae y detecta transmisiones disponibles desde FLX (https://flive.cc.cd/f1).
 *
 * Consulta el catálogo de canales de FLX, identifica Server 1 y Server 2,
 * y filtra estrictamente las transmisiones en español según etiquetas confirmadas
 * (Español, Spanish, ESP, ES) e identifica la cadena (DAZN, ESPN, FOX Sports, etc.).
 */
class FlxExtractor(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    var onSpanishBroadcastsDetected: ((List<SpanishBroadcast>) -> Unit)? = null
    var onNoSpanishBroadcastsDetected: (() -> Unit)? = null

    private var extractionJob: Job? = null

    fun startExtraction(sourceUrl: String = "https://flive.cc.cd/f1") {
        extractionJob?.cancel()
        extractionJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d("FlxExtractor", "Starting FLX extraction from $sourceUrl")
                val catalogUrl = "https://cdn.flive.cc.cd/channels.json"
                val json = fetchUrlWithHeaders(catalogUrl, referer = "https://flive.cc.cd/")

                if (json.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onNoSpanishBroadcastsDetected?.invoke()
                    }
                    return@launch
                }

                val jsonArray = gson.fromJson(json, JsonArray::class.java)
                val spanishBroadcasts = mutableListOf<SpanishBroadcast>()
                var counter = 1

                for (elem in jsonArray) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val uri = obj.get("uri")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val provider = obj.get("provider")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                    // Identificación estricta de idioma español
                    if (BroadcastDetectorUtils.isConfirmedSpanish(title)) {
                        val network = BroadcastDetectorUtils.detectNetwork(title)
                        // En FLX, el proveedor 'f1Tv' corresponde a Server 2, el resto a Server 1
                        val serverName = if (provider.equals("f1Tv", ignoreCase = true)) "Server 2" else "Server 1"
                        val isOnline = status.equals("online", ignoreCase = true)

                        spanishBroadcasts.add(
                            SpanishBroadcast(
                                index = counter++,
                                network = network,
                                language = "Español",
                                serverName = serverName,
                                originalTitle = title,
                                streamUrl = uri,
                                isOnline = isOnline
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    if (spanishBroadcasts.isNotEmpty()) {
                        Log.d("FlxExtractor", "Detected ${spanishBroadcasts.size} Spanish broadcasts in FLX")
                        onSpanishBroadcastsDetected?.invoke(spanishBroadcasts)
                    } else {
                        Log.d("FlxExtractor", "No Spanish broadcasts found in FLX")
                        onNoSpanishBroadcastsDetected?.invoke()
                    }
                }
            } catch (e: Exception) {
                Log.e("FlxExtractor", "Error extracting FLX streams: ${e.message}")
                withContext(Dispatchers.Main) {
                    onNoSpanishBroadcastsDetected?.invoke()
                }
            }
        }
    }

    private fun fetchUrlWithHeaders(targetUrl: String, referer: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(targetUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")

            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w("FlxExtractor", "HTTP error ${conn.responseCode} from $targetUrl")
                null
            }
        } catch (e: Exception) {
            Log.e("FlxExtractor", "Network error fetching $targetUrl: ${e.javaClass.simpleName} - ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun destroy() {
        extractionJob?.cancel()
    }
}
