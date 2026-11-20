package com.f1pitwall.tv

import com.f1pitwall.tv.core.config.BroadcastSource
import com.f1pitwall.tv.model.FutbolLibreEvent
import com.f1pitwall.tv.model.SpanishBroadcast
import com.f1pitwall.tv.network.FutbolLibreExtractor
import org.junit.Assert.*
import org.junit.Test

class FutbolLibreExtractorTest {

    @Test
    fun testIsFormulaOneEventRecognition() {
        // Casos que SÍ deben reconocerse como eventos de F1
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 1 - Gran Premio de Las Vegas - Clasificación"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 - GP de España - Carrera"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 1: Práctica Libre 1"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 1 - Práctica 2"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 - Práctica Libre 3"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1: GP Las Vegas – Práctica 3"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Sports: F1 – GP Las Vegas – P3"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 | GP de Las Vegas: Prácticas Libres #1"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 | GP de Las Vegas: Cámara On Board - Colapinto"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 1 - Carrera Sprint"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 - Carrera Principal"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("F1 Academy - Carrera 1"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Sports: F1 Academy – GP Las Vegas – Carrera #1"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Formula 1 Academy - Clasificación"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Gran Premio de Las Vegas - Clasificación"))
        assertTrue(FutbolLibreExtractor.isFormulaOneEvent("Grand Prix Mónaco - Carrera"))
    }

    @Test
    fun testRejectionOfOtherSportsAndCategories() {
        // Series de soporte o menores que NO deben confundirse con F1
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 2 - Clasificación"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("F2 - Sprint Race"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Fórmula 3 - Feature Race"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("F3 - Sprint Race"))

        // Otros deportes de motor
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("MotoGP - Gran Premio de Cataluña - Carrera"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Moto 2 - Gran Premio de Valencia"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Moto 3 - Clasificación"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("IndyCar - Grand Prix of Long Beach"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("NASCAR - Cup Series - Race"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Formula E - ePrix de Mónaco"))

        // Fútbol u otros deportes convencionales
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Copa Libertadores - River Plate vs Boca Juniors"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("UEFA Champions League - Real Madrid vs Bayern"))
        assertFalse(FutbolLibreExtractor.isFormulaOneEvent("Premier League - Arsenal vs Chelsea"))
    }

    @Test
    fun testFormatToColombiaTime() {
        // 2026-11-21T00:30:00+00:00 en UTC es 2026-11-20 a las 19:30 en Colombia (UTC-5)
        val formatted1 = FutbolLibreExtractor.formatToColombiaTime("2026-11-21T00:30:00+00:00", "00:30")
        assertEquals("20/11 07:30 PM (Hora Col)", formatted1)

        // 2026-11-22T06:00:00Z en UTC es 2026-11-22 a las 01:00 en Colombia (UTC-5)
        val formatted2 = FutbolLibreExtractor.formatToColombiaTime("2026-11-22T06:00:00Z", "06:00")
        assertEquals("22/11 01:00 AM (Hora Col)", formatted2)

        // Caso inválido o nulo debe devolver el fallback
        assertEquals("20:00", FutbolLibreExtractor.formatToColombiaTime(null, "20:00"))
        assertEquals("21:00", FutbolLibreExtractor.formatToColombiaTime("", "21:00"))
        assertEquals("22:00", FutbolLibreExtractor.formatToColombiaTime("invalid-date", "22:00"))
    }

    @Test
    fun testParseAgendaHtmlWithF1SessionsAndChannels() {
        val extractor = FutbolLibreExtractor()

        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="source-agenda-event" data-source-instant="2026-11-21T00:30:00+00:00">
                    <button class="source-agenda-eventbtn">
                        <span class="source-agenda-time">00:30</span>
                        <span class="source-agenda-competition">Fórmula 1</span>
                        <span class="source-agenda-eventtext">Gran Premio de Las Vegas - Clasificación</span>
                    </button>
                    <a class="agenda-open-match" href="/match/gp-las-vegas-clasificacion">Ver Evento</a>
                    <div class="source-agenda-sources">
                        <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=1">
                            <span>Fuente 1</span><small>Disney1</small>
                        </a>
                        <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=2">
                            <span>Fuente 2</span><small>ESPN 2</small>
                        </a>
                        <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=3">
                            <span>Fuente 3</span><small>DAZN F1</small>
                        </a>
                        <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=4">
                            <span>Fuente 4</span><small>FOX Sports 1 AR</small>
                        </a>
                        <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=5">
                            <span>Fuente 5</span><small>SporTV 3</small>
                        </a>
                    </div>
                </div>

                <!-- Evento de fútbol que debe ser ignorado -->
                <div class="source-agenda-event" data-source-instant="2026-11-21T02:00:00+00:00">
                    <button class="source-agenda-eventbtn">
                        <span class="source-agenda-time">02:00</span>
                        <span class="source-agenda-competition">Liga Argentina</span>
                        <span class="source-agenda-eventtext">Boca Juniors vs Racing</span>
                    </button>
                    <a class="agenda-open-match" href="/match/boca-racing">Ver Partido</a>
                    <div class="source-agenda-sources">
                        <a class="agenda-source-button" href="/match/boca-racing#source=1"><span>Fuente 1</span><small>ESPN Premium</small></a>
                    </div>
                </div>

                <!-- Evento de F2 que debe ser ignorado si no es F1 -->
                <div class="source-agenda-event" data-source-instant="2026-11-21T04:00:00+00:00">
                    <button class="source-agenda-eventbtn">
                        <span class="source-agenda-time">04:00</span>
                        <span class="source-agenda-competition">Fórmula 2</span>
                        <span class="source-agenda-eventtext">Las Vegas - Sprint Race</span>
                    </button>
                    <a class="agenda-open-match" href="/match/f2-las-vegas">Ver Carrera</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val events = extractor.parseAgendaHtml(sampleHtml)

        // Debe haber filtrado exactamente el evento de F1
        assertEquals(1, events.size)

        val f1Event = events[0]
        assertEquals("gp-las-vegas-clasificacion", f1Event.id)
        assertTrue(f1Event.title.contains("Fórmula 1") && f1Event.title.contains("Gran Premio de Las Vegas"))
        assertEquals("Clasificación", f1Event.sessionName)
        assertEquals("https://futbol-libre.app/match/gp-las-vegas-clasificacion", f1Event.fullMatchUrl)

        // Verificar que no se filtran por idioma y se conservan las 5 fuentes numeradas
        assertEquals(5, f1Event.sources.size)

        val s1 = f1Event.sources[0]
        assertEquals(1, s1.index)
        assertEquals("Disney1", s1.network)
        assertEquals("Fuente 1", s1.serverName)
        assertEquals("Transmisión 1 — Disney1", s1.getFormattedTitle())
        assertEquals("T1: Disney1", s1.getButtonLabel())
        assertEquals("https://futbol-libre.app/match/gp-las-vegas-clasificacion#source=1", s1.streamUrl)
        assertEquals("1", s1.sourceId)

        val s2 = f1Event.sources[1]
        assertEquals(2, s2.index)
        assertEquals("ESPN 2", s2.network)
        assertEquals("Transmisión 2 — ESPN 2", s2.getFormattedTitle())
        assertEquals("T2: ESPN 2", s2.getButtonLabel())

        val s3 = f1Event.sources[2]
        assertEquals(3, s3.index)
        assertEquals("DAZN F1", s3.network)
        assertEquals("Transmisión 3 — DAZN F1", s3.getFormattedTitle())
        assertEquals("T3: DAZN F1", s3.getButtonLabel())

        val s4 = f1Event.sources[3]
        assertEquals(4, s4.index)
        assertEquals("FOX Sports 1 AR", s4.network)
        assertEquals("Transmisión 4 — FOX Sports 1 AR", s4.getFormattedTitle())
        assertEquals("T4: FOX Sports 1 AR", s4.getButtonLabel())

        // Idioma portugués (SporTV 3) conservado sin filtrar
        val s5 = f1Event.sources[4]
        assertEquals(5, s5.index)
        assertEquals("SporTV 3", s5.network)
        assertEquals("Transmisión 5 — SporTV 3", s5.getFormattedTitle())
        assertEquals("T5: SporTV 3", s5.getButtonLabel())
    }

    @Test
    fun testEightSourcesNumberingAndFormatting() {
        val extractor = FutbolLibreExtractor()
        val buttonsHtml = (1..8).joinToString("\n") { i ->
            """
            <button class="source-pill event-source-choice" data-source="$i">
                <span class="source-provider">Fuente $i</span>
                <span class="source-channel">Canal F1 $i</span>
            </button>
            """.trimIndent()
        }

        val sources = extractor.parseMatchSourcesHtml(buttonsHtml, "https://futbol-libre.app/match/f1-gp-las-vegas")
        assertEquals(8, sources.size)

        for (i in 1..8) {
            val s = sources[i - 1]
            assertEquals(i, s.index)
            assertEquals("Canal F1 $i", s.network)
            assertEquals("Fuente $i", s.serverName)
            assertEquals("Transmisión $i — Canal F1 $i", s.getFormattedTitle())
            assertEquals("T$i: Canal F1 $i", s.getButtonLabel())
            assertEquals("$i", s.sourceId)
            assertEquals("https://futbol-libre.app/match/f1-gp-las-vegas#source=$i", s.streamUrl)
            // Verificar que el clickJs contiene el polling/retry robusto
            assertNotNull(s.clickActionJs)
            assertTrue(s.clickActionJs!!.contains("tryClick"))
            assertTrue(s.clickActionJs!!.contains("var id='$i'"))
        }
    }

    @Test
    fun testParseMatchSourcesHtmlUsesConsistentOneBasedLabels() {
        val extractor = FutbolLibreExtractor()
        val html = """
            <button class="source-pill event-source-choice" data-source="16722">
                <span class="source-provider">Fuente 1</span>
                <span class="source-channel">Disney +</span>
            </button>
            <button class="source-pill event-source-choice" data-source="16723">
                <span class="source-provider">Fuente 2</span>
                <span class="source-channel">ESPN</span>
            </button>
        """.trimIndent()

        val sources = extractor.parseMatchSourcesHtml(
            html,
            "https://futbol-libre.app/match/gp-las-vegas-clasificacion"
        )

        assertEquals(2, sources.size)
        assertEquals(1, sources[0].index)
        assertEquals("Transmisión 1 — Disney +", sources[0].getFormattedTitle())
        assertEquals("T1: Disney +", sources[0].getButtonLabel())
        assertEquals("16722", sources[0].sourceId)
        assertTrue(sources[0].clickActionJs.orEmpty().contains("event-source-choice"))
        assertEquals(
            "https://futbol-libre.app/match/gp-las-vegas-clasificacion#source=16722",
            sources[0].streamUrl
        )
        assertEquals(2, sources[1].index)
        assertEquals("Transmisión 2 — ESPN", sources[1].getFormattedTitle())
        assertEquals("T2: ESPN", sources[1].getButtonLabel())
        assertEquals("16723", sources[1].sourceId)
    }

    @Test
    fun testAgendaCanResolveMatchFromSourceHrefWhenOpenLinkIsAbsent() {
        val extractor = FutbolLibreExtractor()
        val html = """
            <div class="source-agenda-event" data-source-instant="2026-11-21T00:30:00+00:00">
                <button class="source-agenda-eventbtn">
                    <span class="source-agenda-time">00:30</span>
                    <span class="source-agenda-competition">Formula 1</span>
                    <span class="source-agenda-eventtext">Las Vegas - Free Practice 1</span>
                </button>
                <div class="source-agenda-sources">
                    <a class="agenda-source-button" href="/match/gp-las-vegas-practice-1#source=1">
                        <span>Fuente 1</span><small>ESPN 2</small>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val events = extractor.parseAgendaHtml(html)
        assertEquals(1, events.size)
        assertEquals("gp-las-vegas-practice-1", events[0].id)
        assertEquals(
            "https://futbol-libre.app/match/gp-las-vegas-practice-1",
            events[0].fullMatchUrl
        )
        assertEquals("ESPN 2", events[0].sources.single().network)
    }

    @Test
    fun testSessionNamesRecognition() {
        assertEquals("Free Practice 1", FutbolLibreExtractor.extractSessionName("Formula 1 - Las Vegas - Free Practice 1"))
        assertEquals("Prácticas Libres #1", FutbolLibreExtractor.extractSessionName("F1 | GP de Las Vegas: Prácticas Libres #1"))
        assertEquals("Práctica 3", FutbolLibreExtractor.extractSessionName("F1: GP Las Vegas – Práctica 3"))
        assertEquals("P3", FutbolLibreExtractor.extractSessionName("Sports: F1 – GP Las Vegas – P3"))
        assertEquals("Cámara On Board - Colapinto", FutbolLibreExtractor.extractSessionName("F1 | GP de Las Vegas: Cámara On Board - Colapinto"))
        assertEquals("Clasificación", FutbolLibreExtractor.extractSessionName("F1: GP Las Vegas – Clasificación"))
        assertEquals("Qualifying", FutbolLibreExtractor.extractSessionName("F1 - Grand Prix - Qualifying"))
        assertEquals("Sprint Race", FutbolLibreExtractor.extractSessionName("F1 - Sprint Race"))
        assertEquals("Carrera Principal", FutbolLibreExtractor.extractSessionName("F1 - Gran Premio de Las Vegas - Carrera Principal"))
        assertTrue(FutbolLibreExtractor.extractSessionName("Sports: F1 Academy – GP Las Vegas – Carrera #1").contains("F1 Academy"))
    }

    @Test
    fun testF1AcademyClassificationIndependent() {
        val extractor = FutbolLibreExtractor()
        val html = """
            <div class="source-agenda-event" data-source-instant="2026-11-21T02:10:00+00:00">
                <button class="source-agenda-eventbtn">
                    <span class="source-agenda-time">02:10</span>
                    <span class="source-agenda-competition">Sports:</span>
                    <span class="source-agenda-eventtext">F1 Academy – GP Las Vegas – Carrera #1</span>
                </button>
                <a class="agenda-open-match" href="/match/f1-academy-gp-las-vegas-carrera-1">Ver Carrera</a>
                <div class="source-agenda-sources">
                    <a class="agenda-source-button" href="/match/f1-academy-gp-las-vegas-carrera-1#source=16801">
                        <span>Fuente 1</span><small>ESPN 3</small>
                    </a>
                </div>
            </div>
            <div class="source-agenda-event" data-source-instant="2026-11-21T04:00:00+00:00">
                <button class="source-agenda-eventbtn">
                    <span class="source-agenda-time">04:00</span>
                    <span class="source-agenda-competition">F1:</span>
                    <span class="source-agenda-eventtext">GP Las Vegas – Clasificación</span>
                </button>
                <a class="agenda-open-match" href="/match/gp-las-vegas-clasificacion">Ver Carrera</a>
                <div class="source-agenda-sources">
                    <a class="agenda-source-button" href="/match/gp-las-vegas-clasificacion#source=16722">
                        <span>Fuente 1</span><small>Disney +</small>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val events = extractor.parseAgendaHtml(html)
        assertEquals(2, events.size)

        val academyEvent = events[0]
        val mainF1Event = events[1]

        // F1 Academy debe identificarse como categoría independiente
        assertEquals("F1 Academy", academyEvent.category)
        assertTrue(academyEvent.isF1Academy)
        assertTrue(academyEvent.getShortLabel().startsWith("Acad:"))
        assertTrue(academyEvent.getDisplayTitle().startsWith("[F1 ACADEMY]"))

        // F1 Principal
        assertEquals("Fórmula 1", mainF1Event.category)
        assertFalse(mainF1Event.isF1Academy)
        assertTrue(mainF1Event.getDisplayTitle().startsWith("[F1]"))
    }

    @Test
    fun testSessionSwitchingChannelIsolation() {
        // Evento 1: Práctica Libre con 3 fuentes
        val event1 = FutbolLibreEvent(
            id = "event-fp1",
            title = "F1 - GP Las Vegas - FP1",
            sessionName = "FP1",
            gpName = "GP de Las Vegas",
            utcInstant = "2026-11-21T00:30:00Z",
            colombiaTime = "20/11 07:30 PM (Hora Col)",
            matchPath = "/match/fp1",
            fullMatchUrl = "https://futbol-libre.app/match/fp1",
            sources = listOf(
                SpanishBroadcast(1, "Disney1", streamUrl = "https://futbol-libre.app/match/fp1#source=1", sourceId = "1"),
                SpanishBroadcast(2, "ESPN 2", streamUrl = "https://futbol-libre.app/match/fp1#source=2", sourceId = "2"),
                SpanishBroadcast(3, "Fox Sports", streamUrl = "https://futbol-libre.app/match/fp1#source=3", sourceId = "3")
            )
        )

        // Evento 2: Clasificación con 7 fuentes distintas
        val event2 = FutbolLibreEvent(
            id = "event-quali",
            title = "F1 - GP Las Vegas - Clasificación",
            sessionName = "Clasificación",
            gpName = "GP de Las Vegas",
            utcInstant = "2026-11-21T04:00:00Z",
            colombiaTime = "20/11 11:00 PM (Hora Col)",
            matchPath = "/match/quali",
            fullMatchUrl = "https://futbol-libre.app/match/quali",
            sources = (1..7).map { i ->
                SpanishBroadcast(i, "Canal Quali $i", streamUrl = "https://futbol-libre.app/match/quali#source=$i", sourceId = "$i")
            }
        )

        assertEquals(3, event1.sources.size)
        assertEquals(7, event2.sources.size)

        // Las URLs base deben ser diferentes para no mezclar canales
        assertNotEquals(event1.fullMatchUrl, event2.fullMatchUrl)
        event1.sources.forEach { s ->
            assertTrue(s.streamUrl!!.startsWith(event1.fullMatchUrl))
            assertFalse(s.streamUrl!!.startsWith(event2.fullMatchUrl))
        }
        event2.sources.forEach { s ->
            assertTrue(s.streamUrl!!.startsWith(event2.fullMatchUrl))
            assertFalse(s.streamUrl!!.startsWith(event1.fullMatchUrl))
        }
    }

    @Test
    fun testBroadcastSourceThreeOptions() {
        assertEquals("FUTBOL_LIBRE", BroadcastSource.FUTBOL_LIBRE.key)
        assertEquals("Fútbol Libre", BroadcastSource.FUTBOL_LIBRE.displayName)
        assertEquals("https://futbol-libre.app/agenda", BroadcastSource.FUTBOL_LIBRE.defaultUrl)

        // Las 3 fuentes disponibles
        val sources = BroadcastSource.values()
        assertEquals(3, sources.size)
        assertTrue(sources.contains(BroadcastSource.FLX))
        assertTrue(sources.contains(BroadcastSource.FUTBOL_VIVO))
        assertTrue(sources.contains(BroadcastSource.FUTBOL_LIBRE))

        assertEquals(BroadcastSource.FUTBOL_LIBRE, BroadcastSource.fromKey("FUTBOL_LIBRE"))
        assertEquals(BroadcastSource.FUTBOL_LIBRE, BroadcastSource.fromKey("futbol_libre"))
    }
}
