package com.f1pitwall.tv.network

import android.util.Log
import com.f1pitwall.tv.model.FutbolLibreEvent
import com.f1pitwall.tv.model.SpanishBroadcast
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extractor para consultar la agenda de Fútbol Libre (https://futbol-libre.app/agenda),
 * identificar eventos y sesiones de Fórmula 1, convertirlos a la hora de Colombia (UTC-5)
 * y extraer todas las opciones/canales de transmisión disponibles para cada sesión.
 */
class FutbolLibreExtractor(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var extractionJob: Job? = null

    var onEventsDetected: ((List<FutbolLibreEvent>) -> Unit)? = null
    var onNoEventsDetected: (() -> Unit)? = null

    fun startExtraction(agendaUrl: String = "https://futbol-libre.app/agenda") {
        extractionJob?.cancel()
        extractionJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d("FutbolLibreExtractor", "Starting agenda extraction from $agendaUrl")
                val html = fetchHtml(agendaUrl)
                if (html.isNullOrBlank()) {
                    withContext(Dispatchers.Main) { onNoEventsDetected?.invoke() }
                    return@launch
                }

                val events = parseAgendaHtml(html)

                withContext(Dispatchers.Main) {
                    if (events.isNotEmpty()) {
                        Log.d("FutbolLibreExtractor", "Found ${events.size} F1 events in agenda")
                        onEventsDetected?.invoke(events)
                    } else {
                        Log.d("FutbolLibreExtractor", "No F1 events found in agenda")
                        onNoEventsDetected?.invoke()
                    }
                }
            } catch (e: Exception) {
                Log.e("FutbolLibreExtractor", "Error extracting agenda: ${e.message}", e)
                withContext(Dispatchers.Main) { onNoEventsDetected?.invoke() }
            }
        }
    }

    /**
     * Parsea el HTML de la agenda y extrae los eventos de F1 con sus fuentes.
     */
    fun parseAgendaHtml(html: String): List<FutbolLibreEvent> {
        val doc = Jsoup.parse(html)
        val eventDivs = doc.select("div.source-agenda-event")
        val events = mutableListOf<FutbolLibreEvent>()

        for (div in eventDivs) {
            val btn = div.selectFirst("button.source-agenda-eventbtn") ?: continue
            val timeSpan = btn.selectFirst("span.source-agenda-time")
            val timeRaw = timeSpan?.text()?.trim().orEmpty()
            val compSpan = btn.selectFirst("span.source-agenda-competition")
            val compRaw = compSpan?.text()?.trim().orEmpty()
            val textSpan = btn.selectFirst("span.source-agenda-eventtext")
            val fullTextRaw = textSpan?.text()?.trim().orEmpty()

            val combinedTitle = if (compRaw.isNotBlank()) {
                "$compRaw $fullTextRaw".replace(Regex("\\s+"), " ").trim()
            } else {
                fullTextRaw.replace(Regex("\\s+"), " ").trim()
            }

            if (!isFormulaOneEvent(combinedTitle)) {
                continue
            }

            val utcInstant = div.attr("data-source-instant").takeIf { it.isNotBlank() }
            val colombiaTime = formatToColombiaTime(utcInstant, timeRaw)

            val openMatch = div.selectFirst("a.agenda-open-match")
            val matchHref = openMatch?.attr("href")?.trim().orEmpty()
            val matchUrl = if (matchHref.startsWith("http")) matchHref else "https://futbol-libre.app$matchHref"
            val slug = matchHref.removePrefix("/match/").substringBefore("#")

            // Extraer fuentes asociadas al evento
            val sourceLinks = div.select("div.source-agenda-sources a.agenda-source-button")
            val broadcastList = mutableListOf<SpanishBroadcast>()
            var counter = 1

            for (a in sourceLinks) {
                val sProvider = a.selectFirst("span")?.text()?.trim().orEmpty().ifBlank { "Fuente $counter" }
                val sChannel = a.selectFirst("small")?.text()?.trim().orEmpty().ifBlank { sProvider }
                val sHref = a.attr("href").trim()
                val sourceId = sHref.substringAfter("#source=", "").takeIf { it.isNotBlank() }
                val targetUrl = if (sHref.startsWith("http")) sHref else "https://futbol-libre.app$sHref"

                val customTitle = "Transmisión $counter — $sChannel"
                val customBtn = "T$counter: $sChannel"
                val clickJs = if (!sourceId.isNullOrBlank()) {
                    "(function() { const b = document.querySelector('button.event-source-choice[data-source=\"$sourceId\"]'); if (b) b.click(); else if (typeof window.location !== 'undefined') window.location.hash = 'source=$sourceId'; })();"
                } else null

                broadcastList.add(
                    SpanishBroadcast(
                        index = counter++,
                        network = sChannel,
                        language = "",
                        serverName = sProvider,
                        originalTitle = "$sProvider · $sChannel",
                        streamUrl = targetUrl,
                        isOnline = true,
                        clickActionJs = clickJs,
                        sourceId = sourceId,
                        customFormattedTitle = customTitle,
                        customButtonLabel = customBtn
                    )
                )
            }

            val sessionName = extractSessionName(combinedTitle)
            val gpName = extractGpName(combinedTitle)

            events.add(
                FutbolLibreEvent(
                    id = slug.ifBlank { "event_${events.size + 1}" },
                    title = combinedTitle,
                    sessionName = sessionName,
                    gpName = gpName,
                    utcInstant = utcInstant,
                    colombiaTime = colombiaTime,
                    matchPath = matchHref,
                    fullMatchUrl = matchUrl,
                    sources = broadcastList
                )
            )
        }
        return events
    }

    /**
     * Consulta una página individual de partido (/match/...) si requiere obtener fuentes dinámicas.
     */
    suspend fun fetchSourcesForMatch(matchUrl: String): List<SpanishBroadcast> = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(matchUrl) ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)
            val buttons = doc.select("button.event-source-choice")
            val broadcasts = mutableListOf<SpanishBroadcast>()
            var counter = 1

            for (btn in buttons) {
                val prov = btn.selectFirst(".source-provider")?.text()?.trim().orEmpty().ifBlank { "Fuente $counter" }
                val chan = btn.selectFirst(".source-channel")?.text()?.trim().orEmpty().ifBlank { prov }
                val sourceId = btn.attr("data-source").trim()
                val targetUrl = if (sourceId.isNotBlank()) "$matchUrl#source=$sourceId" else matchUrl

                val clickJs = if (sourceId.isNotBlank()) {
                    "(function() { const b = document.querySelector('button.event-source-choice[data-source=\"$sourceId\"]'); if (b) b.click(); else if (typeof window.location !== 'undefined') window.location.hash = 'source=$sourceId'; })();"
                } else null

                broadcasts.add(
                    SpanishBroadcast(
                        index = counter++,
                        network = chan,
                        language = "",
                        serverName = prov,
                        originalTitle = "$prov · $chan",
                        streamUrl = targetUrl,
                        isOnline = true,
                        clickActionJs = clickJs,
                        sourceId = sourceId,
                        customFormattedTitle = "Transmisión $counter — $chan",
                        customButtonLabel = "T$counter: $chan"
                    )
                )
            }
            broadcasts
        } catch (e: Exception) {
            Log.e("FutbolLibreExtractor", "Error fetching match sources: ${e.message}")
            emptyList()
        }
    }

    private fun fetchHtml(targetUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(targetUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8")

            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w("FutbolLibreExtractor", "HTTP error ${conn.responseCode} from $targetUrl")
                null
            }
        } catch (e: Exception) {
            Log.e("FutbolLibreExtractor", "Network error fetching $targetUrl: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun destroy() {
        extractionJob?.cancel()
    }

    companion object {
        fun isFormulaOneEvent(title: String): Boolean {
            val t = title.lowercase(Locale.ROOT)
            val hasF1 = Regex("""\bf1\b|f[oó]rmula\s*(?:1|one)|f1\s*academy|motorsport.*f1|f[oó]rmula\s*1\s*academy""").containsMatchIn(t)
            val hasGp = Regex("""gran\s*premio|grand\s*prix|\bgp\b""").containsMatchIn(t)
            val hasSession = Regex("""pr[aá]cticas?\s*libres?|pr[aá]ctica\s*[123]|clasificaci[oó]n|carrera(?:\s*sprint|\s*principal)?|race|f1\s*academy""").containsMatchIn(t)
            val isOtherSeries = Regex("""\bf2\b|\bf3\b|formula\s*[23]|f[oó]rmula\s*[23]""").containsMatchIn(t)
            if (isOtherSeries && !hasF1) return false
            return hasF1 || (hasGp && hasSession) || (hasGp && t.contains("las vegas"))
        }

        fun formatToColombiaTime(utcInstantIso: String?, fallbackTime: String): String {
            if (utcInstantIso.isNullOrBlank()) return fallbackTime
            return try {
                val instant = Instant.parse(utcInstantIso)
                val colZone = ZoneId.of("America/Bogota")
                val colZdt = instant.atZone(colZone)
                val formatter = DateTimeFormatter.ofPattern("dd/MM hh:mm a", Locale.US)
                "${colZdt.format(formatter)} (Hora Col)"
            } catch (_: Exception) {
                fallbackTime
            }
        }

        fun extractSessionName(title: String): String {
            val t = title.replace(Regex("\\s+"), " ").trim()
            val sessionMatch = Regex("""(Prácticas?\s*Libres?\s*#?[123]|Práctica\s*[123]|Clasificación|Carrera\s*Sprint|Carrera\s*Principal|Carrera\s*#?[123]|Cámara\s*On\s*Board[^:]*|F1\s*Academy[^:]*)""", RegexOption.IGNORE_CASE).find(t)
            return sessionMatch?.value?.trim() ?: t.substringAfterLast(":").trim().ifBlank { t }
        }

        fun extractGpName(title: String): String {
            val gpMatch = Regex("""(GP\s*(?:de\s*)?[A-Za-z\s]+|Gran\s*Premio\s*(?:de\s*)?[A-Za-z\s]+)""", RegexOption.IGNORE_CASE).find(title)
            return gpMatch?.value?.trim() ?: "Gran Premio"
        }
    }
}
