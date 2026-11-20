package com.f1pitwall.tv

import com.f1pitwall.tv.core.config.BroadcastSource
import com.f1pitwall.tv.core.util.BroadcastDetectorUtils
import com.f1pitwall.tv.model.SpanishBroadcast
import org.junit.Assert.*
import org.junit.Test

class BroadcastDetectorTest {

    @Test
    fun testSpanishLanguageDetectionStrict() {
        // Casos confirmados en español (deben ser true)
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("HD | DAZN F1 [Spanish]"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("SD | DAZN F1 [Spanish]"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("Transmisión en Español"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("ESPN 2 (ESP)"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("F1 Carrera ES"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("Fox Sports [Español]"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("Audio Castellano DAZN"))
        assertTrue(BroadcastDetectorUtils.isConfirmedSpanish("Canal 1 [ES]"))

        // Casos NO confirmados en español (deben ser estrictamente false)
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("Sky Sports F1 [UK]"))
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("HD | Sky Sport F1 [German]"))
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("HD | Viaplay [Dutch]"))
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("HD | Sky Sport F1 [Italian]"))
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("ESPN 1")) // No confirma idioma explícito
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("FOX Sports 2")) // No confirma idioma explícito
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("Session Best Time")) // "es" dentro de palabras
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish("Reset speed")) // "es" dentro de reset
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish(""))
        assertFalse(BroadcastDetectorUtils.isConfirmedSpanish(null))
    }

    @Test
    fun testNetworkDetection() {
        assertEquals("DAZN", BroadcastDetectorUtils.detectNetwork("HD | DAZN F1 [Spanish]"))
        assertEquals("ESPN 1", BroadcastDetectorUtils.detectNetwork("ESPN 1"))
        assertEquals("ESPN 2", BroadcastDetectorUtils.detectNetwork("ESPN 2 en vivo"))
        assertEquals("ESPN Premium", BroadcastDetectorUtils.detectNetwork("ESPN Premium [ESP]"))
        assertEquals("FOX Sports", BroadcastDetectorUtils.detectNetwork("Transmisión Fox Sports [Spanish]"))
        assertEquals("FOX Sports", BroadcastDetectorUtils.detectNetwork("FOXSports 1 (ES)"))
        assertEquals("Movistar", BroadcastDetectorUtils.detectNetwork("Movistar F1 Directo"))
        assertEquals("Star+", BroadcastDetectorUtils.detectNetwork("Star+ Latinoamérica"))
        assertEquals("Sky Sports", BroadcastDetectorUtils.detectNetwork("Sky Sports F1 HD"))
        assertEquals("F1 TV", BroadcastDetectorUtils.detectNetwork("F1 TV Pro"))
        assertNull(BroadcastDetectorUtils.detectNetwork("Señal General 1"))
    }

    @Test
    fun testSpanishBroadcastFormatting() {
        // Ejemplo ilustrativo de la especificación:
        // Transmisión 1 — ESPN — Español — Server 1
        val b1 = SpanishBroadcast(
            index = 1,
            network = "ESPN",
            language = "Español",
            serverName = "Server 1",
            originalTitle = "ESPN 1 [Spanish]",
            streamUrl = "https://example.com/stream1"
        )
        assertEquals("Transmisión 1 — ESPN — Español — Server 1", b1.getFormattedTitle())
        assertEquals("T1: ESPN (S1)", b1.getButtonLabel())

        // Transmisión 2 — DAZN — Español — Server 1
        val b2 = SpanishBroadcast(
            index = 2,
            network = "DAZN",
            language = "Español",
            serverName = "Server 1",
            originalTitle = "HD | DAZN F1 [Spanish]",
            streamUrl = "https://example.com/stream2"
        )
        assertEquals("Transmisión 2 — DAZN — Español — Server 1", b2.getFormattedTitle())
        assertEquals("T2: DAZN (S1)", b2.getButtonLabel())

        // Transmisión 3 — FOX Sports — Español — Server 2
        val b3 = SpanishBroadcast(
            index = 3,
            network = "FOX Sports",
            language = "Español",
            serverName = "Server 2",
            originalTitle = "FOX Sports 1 (ESP)",
            streamUrl = "https://example.com/stream3"
        )
        assertEquals("Transmisión 3 — FOX Sports — Español — Server 2", b3.getFormattedTitle())
        assertEquals("T3: FOX Sports (S2)", b3.getButtonLabel())

        // Caso sin cadena identificada
        val b4 = SpanishBroadcast(
            index = 4,
            network = null,
            language = "Español",
            serverName = "Server 1",
            originalTitle = "Opción 1 [Spanish]",
            streamUrl = "https://example.com/stream4"
        )
        assertEquals("Transmisión 4 — Español — Server 1", b4.getFormattedTitle())
    }

    @Test
    fun testBroadcastSourceEnum() {
        assertEquals("FLX", BroadcastSource.FLX.displayName)
        assertEquals("https://flive.cc.cd/f1", BroadcastSource.FLX.defaultUrl)
        assertEquals("Fútbol Vivo", BroadcastSource.FUTBOL_VIVO.displayName)
        assertEquals("https://futbolvivo.tv/", BroadcastSource.FUTBOL_VIVO.defaultUrl)

        assertEquals(BroadcastSource.FLX, BroadcastSource.fromKey("FLX"))
        assertEquals(BroadcastSource.FLX, BroadcastSource.fromKey("flx"))
        assertEquals(BroadcastSource.FUTBOL_VIVO, BroadcastSource.fromKey("FUTBOL_VIVO"))
        assertEquals(BroadcastSource.FUTBOL_VIVO, BroadcastSource.fromKey("unknown"))
        assertEquals(BroadcastSource.FUTBOL_VIVO, BroadcastSource.fromKey(null))
    }

    @Test
    fun testFlxRealWorldCatalogParsing() {
        // Simulación del catálogo real de https://cdn.flive.cc.cd/channels.json
        val simulatedCatalog = listOf(
            Triple("Sky Sports F1 [UK]", "https://videocdn-4726.website/...", "embed"),
            Triple("F1 TV", "https://weellz.live/beta?stream=...", "embed"),
            Triple("ESPN 1", "https://embedsports.top/embed/alpha/espn/1", "embed"), // Sin idioma -> ignorar
            Triple("HD | DAZN F1 [Spanish]", "https://taifood-blog.asia/embed/las-vegas-32439", "embed"),
            Triple("SD | DAZN F1 [Spanish]", "https://taifood-blog.asia/embed/las-vegas-32439_540p", "embed"),
            Triple("HD | Sky Sport F1 [German]", "https://taifood-blog.asia/embed/las-vegas-32440", "embed"),
            Triple("FOX Sports 1 [Spanish]", "https://stream.example/fox1", "f1Tv"),
            Triple("Driver Cam: Max Verstappen", "https://weellz.live/...", "embed")
        )

        val spanishBroadcasts = mutableListOf<SpanishBroadcast>()
        var counter = 1
        for ((title, uri, provider) in simulatedCatalog) {
            if (BroadcastDetectorUtils.isConfirmedSpanish(title)) {
                val net = BroadcastDetectorUtils.detectNetwork(title)
                val srv = if (provider.equals("f1Tv", ignoreCase = true)) "Server 2" else "Server 1"
                spanishBroadcasts.add(
                    SpanishBroadcast(
                        index = counter++,
                        network = net,
                        language = "Español",
                        serverName = srv,
                        originalTitle = title,
                        streamUrl = uri
                    )
                )
            }
        }

        // Deben detectarse exactamente 3 transmisiones en español
        assertEquals(3, spanishBroadcasts.size)
        assertEquals("Transmisión 1 — DAZN — Español — Server 1", spanishBroadcasts[0].getFormattedTitle())
        assertEquals("Transmisión 2 — DAZN — Español — Server 1", spanishBroadcasts[1].getFormattedTitle())
        assertEquals("Transmisión 3 — FOX Sports — Español — Server 2", spanishBroadcasts[2].getFormattedTitle())
    }
}
