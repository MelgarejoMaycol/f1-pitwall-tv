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

            // Source hrefs identify the event page even if no separate open link is present.
            val sourceLinks = div.select("a.agenda-source-button")
            val openMatchHref = div.selectFirst("a.agenda-open-match")?.attr("href")?.trim().orEmpty()
            val fallbackMatchHref = sourceLinks.firstOrNull()?.attr("href")?.substringBefore("#").orEmpty()
            val matchUrl = resolveFutbolLibreUrl(openMatchHref.ifBlank { fallbackMatchHref }) ?: continue
            val matchPath = runCatching { URL(matchUrl).path }.getOrNull().orEmpty()
            val slug = matchPath.removePrefix("/match/").substringBefore("/")
                .takeIf { matchPath.startsWith("/match/") && it.isNotBlank() } ?: continue

            // Preserve the actual order and source IDs announced on the agenda.
            val broadcastList = sourceLinks.mapIndexedNotNull { sourceIndex, anchor ->
                val provider = anchor.selectFirst("span")?.text()?.trim().orEmpty()
                    .ifBlank { "Fuente " + (sourceIndex + 1) }
                val channel = anchor.selectFirst("small")?.text()?.trim().orEmpty()
                    .ifBlank { provider }
                val href = anchor.attr("href").trim()
                val sourceId = extractSourceId(href)
                val resolvedSourceUrl = if (href.startsWith("#")) {
                    matchUrl.substringBefore("#") + href
                } else {
                    resolveFutbolLibreUrl(href)
                }
                val targetUrl = when {
                    !sourceId.isNullOrBlank() -> matchUrl.substringBefore("#") + "#source=" + sourceId
                    !resolvedSourceUrl.isNullOrBlank() -> resolvedSourceUrl
                    else -> matchUrl
                }

                createBroadcast(
                    index = sourceIndex + 1,
                    provider = provider,
                    channel = channel,
                    sourceId = sourceId,
                    targetUrl = targetUrl
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
                    matchPath = matchPath,
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
            parseMatchSourcesHtml(html, matchUrl)
        } catch (e: Exception) {
            Log.e("FutbolLibreExtractor", "Error fetching match sources: " + e.message, e)
            emptyList()
        }
    }

    /**
     * Parses the visible source choices from an individual event page.
     * It does not call private playback endpoints or extract media URLs.
     */
    fun parseMatchSourcesHtml(html: String, matchUrl: String): List<SpanishBroadcast> {
        val baseMatchUrl = resolveFutbolLibreUrl(matchUrl)?.substringBefore("#") ?: return emptyList()
        val doc = Jsoup.parse(html, baseMatchUrl)
        return doc.select("button.event-source-choice[data-source]").mapIndexedNotNull { index, button ->
            val provider = button.selectFirst(".source-provider")?.text()?.trim().orEmpty()
                .ifBlank { "Fuente " + (index + 1) }
            val channel = button.selectFirst(".source-channel")?.text()?.trim().orEmpty()
                .ifBlank { provider }
            val sourceId = button.attr("data-source").trim()
                .takeIf { SOURCE_ID_PATTERN.matches(it) } ?: return@mapIndexedNotNull null

            createBroadcast(
                index = index + 1,
                provider = provider,
                channel = channel,
                sourceId = sourceId,
                targetUrl = baseMatchUrl + "#source=" + sourceId
            )
        }
    }

    private fun createBroadcast(
        index: Int,
        provider: String,
        channel: String,
        sourceId: String?,
        targetUrl: String
    ): SpanishBroadcast {
        val safeSourceId = sourceId?.takeIf { SOURCE_ID_PATTERN.matches(it) }
        val clickJs = safeSourceId?.let {
            "(function(){var id='" + it + "';var b=Array.prototype.find.call(document.querySelectorAll('button.event-source-choice[data-source]'),function(el){return el.getAttribute('data-source')===id;});if(b){b.click();}else{window.location.hash='source='+encodeURIComponent(id);}})();"
        }
        return SpanishBroadcast(
            index = index,
            network = channel,
            language = "",
            serverName = provider,
            originalTitle = "$provider · $channel",
            streamUrl = targetUrl,
            isOnline = true,
            clickActionJs = clickJs,
            sourceId = safeSourceId,
            customFormattedTitle = "Transmisión " + index + " — " + channel,
            customButtonLabel = "T" + index + ": " + channel
        )
    }

    private fun extractSourceId(reference: String): String? {
        return Regex("""(?:[?#&])source=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .find(reference)?.groupValues?.getOrNull(1)
    }

    private fun resolveFutbolLibreUrl(reference: String): String? {
        if (reference.isBlank()) return null
        return try {
            val resolved = URL(URL(AGENDA_URL), reference)
            val host = resolved.host.lowercase(Locale.ROOT)
            val allowedHost = host == "futbol-libre.app" || host == "www.futbol-libre.app"
            if (resolved.protocol.equals("https", ignoreCase = true) && allowedHost) {
                resolved.toExternalForm()
            } else {
                null
            }
        } catch (_: Exception) {
            null
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
        private const val AGENDA_URL = "https://futbol-libre.app/agenda"
        private val SOURCE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,32}$")

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
            val sessionMatch = Regex(
                """(Prácticas?\s*Libres?\s*#?[123]|Práctica\s*[123]|Free\s*Practice\s*[123]|\bFP[123]\b|Practice\s*[123]|Clasificación|Qualifying|Carrera\s*Sprint|Sprint\s*Race|Sprint|Carrera\s*Principal|Main\s*Race|Carrera\s*#?[123]|\bRace\b|Cámara\s*On\s*Board[^:]*|On\s*Board[^:]*|F1\s*Academy)""",
                RegexOption.IGNORE_CASE
            ).find(t)
            return sessionMatch?.value?.trim() ?: t.substringAfterLast(":").trim().ifBlank { t }
        }

        fun extractGpName(title: String): String {
            val gpMatch = Regex(
                """(?:GP|Gran\s*Premio|Grand\s*Prix)\s*(?:de\s*|of\s*)?([^:|–—-]+)""",
                RegexOption.IGNORE_CASE
            ).find(title)
            val location = gpMatch?.groupValues?.getOrNull(1)?.trim()
            return if (location.isNullOrBlank()) "Gran Premio" else "GP de " + location
        }
    }
}
