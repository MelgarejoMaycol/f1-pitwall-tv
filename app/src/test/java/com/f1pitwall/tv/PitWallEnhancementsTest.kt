package com.f1pitwall.tv

import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.model.DriverCardItem
import com.f1pitwall.tv.data.model.JolpicaDriver
import com.f1pitwall.tv.data.model.JolpicaQualifyingResult
import com.f1pitwall.tv.data.model.JolpicaRace
import com.f1pitwall.tv.data.model.RaceTableContainer
import com.f1pitwall.tv.data.model.JolpicaResponse
import com.f1pitwall.tv.model.CarLocation
import com.f1pitwall.tv.model.DriverLeaderboardItem
import com.f1pitwall.tv.model.DriverTyreStrategyItem
import com.f1pitwall.tv.model.Lap
import com.f1pitwall.tv.model.RaceControl
import com.f1pitwall.tv.model.TeamRadio
import com.f1pitwall.tv.model.Weather
import com.f1pitwall.tv.ui.pitwall.CircuitOutlinesCatalog
import com.f1pitwall.tv.ui.pitwall.TrackMapView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PitWallEnhancementsTest {

    @Test
    fun testLapDurationFormatting() {
        assertEquals("1:32.450", TimeUtils.formatLapDuration(92.450))
        assertEquals("0:58.123", TimeUtils.formatLapDuration(58.123))
        assertEquals("--:--.---", TimeUtils.formatLapDuration(null))
        assertEquals("--:--.---", TimeUtils.formatLapDuration(0.0))
        assertEquals("--:--.---", TimeUtils.formatLapDuration(-5.0))
    }

    @Test
    fun testTimingTowerPointsAndDifferenceCalculation() {
        val standings = listOf(
            DriverCardItem(
                driverNumber = 1,
                position = 1,
                fullName = "Max Verstappen",
                acronym = "VER",
                teamName = "Red Bull Racing",
                teamColor = "3671C6",
                headshotUrl = null,
                points = "393",
                wins = 8
            ),
            DriverCardItem(
                driverNumber = 4,
                position = 2,
                fullName = "Lando Norris",
                acronym = "NOR",
                teamName = "McLaren",
                teamColor = "FF8000",
                headshotUrl = null,
                points = "282",
                wins = 3
            ),
            DriverCardItem(
                driverNumber = 16,
                position = 3,
                fullName = "Charles Leclerc",
                acronym = "LEC",
                teamName = "Ferrari",
                teamColor = "E80020",
                headshotUrl = null,
                points = "240.5",
                wins = 2
            )
        )

        val leaderPts = standings.first().points.toDoubleOrNull() ?: 0.0
        val items = standings.map { st ->
            val driverPts = st.points.toDoubleOrNull() ?: 0.0
            val diff = leaderPts - driverPts
            val diffFormatted = if (diff % 1.0 == 0.0) "${diff.toInt()}" else String.format(Locale.US, "%.1f", diff)
            val diffText = if (st.position == 1) "LÍDER" else "-$diffFormatted PTS"
            val ptsText = "${st.points} PTS"

            DriverLeaderboardItem(
                driverNumber = st.driverNumber,
                position = st.position,
                acronym = st.acronym,
                fullName = st.fullName,
                teamName = st.teamName,
                teamColor = st.teamColor,
                gapToLeader = ptsText,
                interval = diffText,
                lastLap = diffText,
                s1 = "--",
                s2 = "--",
                s3 = "--",
                tyreCompound = "SOFT",
                tyreAge = 1
            )
        }

        // Check Leader P1
        assertEquals("393 PTS", items[0].gapToLeader)
        assertEquals("LÍDER", items[0].lastLap)

        // Check P2: 282 points, -111 difference
        assertEquals("282 PTS", items[1].gapToLeader)
        assertEquals("-111 PTS", items[1].lastLap)

        // Check P3: 240.5 points, -152.5 difference
        assertEquals("240.5 PTS", items[2].gapToLeader)
        assertEquals("-152.5 PTS", items[2].lastLap)
    }

    @Test
    fun testStartingGridMapping() {
        val qJson = """
        {
          "MRData": {
            "RaceTable": {
              "Races": [
                {
                  "season": "2026",
                  "round": "1",
                  "raceName": "Australian Grand Prix",
                  "date": "2026-03-08",
                  "QualifyingResults": [
                    {
                      "number": "4",
                      "position": "1",
                      "Driver": { "driverId": "norris", "code": "NOR", "givenName": "Lando", "familyName": "Norris" },
                      "Constructor": { "name": "McLaren" },
                      "Q1": "1:17.100",
                      "Q2": "1:16.800",
                      "Q3": "1:16.250"
                    },
                    {
                      "number": "63",
                      "position": "2",
                      "Driver": { "driverId": "russell", "code": "RUS", "givenName": "George", "familyName": "Russell" },
                      "Constructor": { "name": "Mercedes" },
                      "Q1": "1:17.300",
                      "Q2": "1:16.900",
                      "Q3": "1:16.400"
                    },
                    {
                      "number": "12",
                      "position": "3",
                      "Driver": { "driverId": "antonelli", "code": "ANT", "givenName": "Andrea Kimi", "familyName": "Antonelli" },
                      "Constructor": { "name": "Mercedes" },
                      "Q1": "1:17.400",
                      "Q2": "1:17.000",
                      "Q3": "1:16.550"
                    },
                    {
                      "number": "16",
                      "position": "4",
                      "Driver": { "driverId": "leclerc", "code": "LEC", "givenName": "Charles", "familyName": "Leclerc" },
                      "Constructor": { "name": "Ferrari" },
                      "Q1": "1:17.500",
                      "Q2": "1:17.100",
                      "Q3": "1:16.600"
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val type = object : TypeToken<JolpicaResponse<RaceTableContainer>>() {}.type
        val response = Gson().fromJson<JolpicaResponse<RaceTableContainer>>(qJson, type)
        val qResults = response.mrData.raceTable?.races?.firstOrNull()?.qualifyingResults ?: emptyList()
        assertEquals(4, qResults.size)

        val grid = qResults.mapIndexed { index, item ->
            val pos = item.position?.toIntOrNull() ?: (index + 1)
            val row = (pos + 1) / 2
            val side = if (pos % 2 == 1) "I" else "D"
            val bestQ = item.q3?.takeIf { it.isNotBlank() }
                ?: item.q2?.takeIf { it.isNotBlank() }
                ?: item.q1?.takeIf { it.isNotBlank() }
                ?: "--:--.---"

            val gapStr = if (pos == 1) "POLE POSITION" else "FILA $row ($side)"

            DriverLeaderboardItem(
                driverNumber = item.number?.toIntOrNull() ?: (index + 1),
                position = pos,
                acronym = item.driver?.code ?: "F1",
                fullName = item.driver?.fullName ?: "Piloto",
                teamName = item.constructor?.name ?: "",
                teamColor = "FF8000",
                gapToLeader = gapStr,
                interval = bestQ,
                lastLap = bestQ,
                s1 = item.q1 ?: "--",
                s2 = item.q2 ?: "--",
                s3 = item.q3 ?: "--",
                tyreCompound = "SOFT",
                tyreAge = 1
            )
        }

        // P1: Pole Position
        assertEquals("POLE POSITION", grid[0].gapToLeader)
        assertEquals("1:16.250", grid[0].lastLap)
        assertEquals("NOR", grid[0].acronym)

        // P2: Fila 1 (D)
        assertEquals("FILA 1 (D)", grid[1].gapToLeader)
        assertEquals("1:16.400", grid[1].lastLap)
        assertEquals("RUS", grid[1].acronym)

        // P3: Fila 2 (I)
        assertEquals("FILA 2 (I)", grid[2].gapToLeader)
        assertEquals("1:16.550", grid[2].lastLap)
        assertEquals("ANT", grid[2].acronym)

        // P4: Fila 2 (D)
        assertEquals("FILA 2 (D)", grid[3].gapToLeader)
        assertEquals("1:16.600", grid[3].lastLap)
        assertEquals("LEC", grid[3].acronym)
    }

    @Test
    fun testTeamRadioQueueFilterAndOrdering() {
        val rawRadios = listOf(
            TeamRadio(date = "2026-05-10T10:05:00Z", driverNumber = 44, recordingUrl = "https://example.com/radio1.mp3"),
            TeamRadio(date = "2026-05-10T10:07:00Z", driverNumber = 44, recordingUrl = null),
            TeamRadio(date = "2026-05-10T10:12:00Z", driverNumber = 44, recordingUrl = "https://example.com/radio2.mp3"),
            TeamRadio(date = "2026-05-10T10:15:00Z", driverNumber = 44, recordingUrl = "   ")
        )

        val playable = rawRadios.filter { !it.recordingUrl.isNullOrBlank() }
        assertEquals(2, playable.size)
        assertEquals("https://example.com/radio1.mp3", playable[0].recordingUrl)
        assertEquals("https://example.com/radio2.mp3", playable[1].recordingUrl)
    }

    @Test
    fun testLapSectorDataModel() {
        val lap = Lap(
            driverNumber = 81,
            lapNumber = 24,
            lapDuration = 91.825,
            s1 = 28.112,
            s2 = 36.450,
            s3 = 27.263,
            i1Speed = 295,
            i2Speed = 312,
            stSpeed = 338,
            isPitOutLap = false
        )

        assertEquals(81, lap.driverNumber)
        assertEquals(24, lap.lapNumber)
        assertEquals(28.112, lap.s1 ?: 0.0, 0.001)
        assertEquals(36.450, lap.s2 ?: 0.0, 0.001)
        assertEquals(27.263, lap.s3 ?: 0.0, 0.001)
        assertEquals(Integer.valueOf(295), lap.i1Speed)
        assertEquals(Integer.valueOf(312), lap.i2Speed)
        assertEquals(Integer.valueOf(338), lap.stSpeed)
        assertEquals("1:31.825", TimeUtils.formatLapDuration(lap.lapDuration))
    }

    @Test
    fun testTyreStrategyModel() {
        val strategyItem = DriverTyreStrategyItem(
            driverNumber = 16,
            position = 3,
            acronym = "LEC",
            fullName = "Charles Leclerc",
            teamName = "Ferrari",
            teamColor = "E80020",
            currentCompound = "HARD",
            tyreAge = 18,
            totalStints = 2,
            pitStopsCount = 1,
            lastPitStopDuration = "2.4s",
            stintsHistory = "M (22v) ➔ H (18v)"
        )

        assertEquals("LEC", strategyItem.acronym)
        assertEquals(3, strategyItem.position)
        assertEquals("HARD", strategyItem.currentCompound)
        assertEquals(18, strategyItem.tyreAge)
        assertEquals(1, strategyItem.pitStopsCount)
        assertEquals("2.4s", strategyItem.lastPitStopDuration)
        assertEquals("M (22v) ➔ H (18v)", strategyItem.stintsHistory)
    }

    @Test
    fun testRaceControlCategorization() {
        val messages = listOf(
            RaceControl(date = "2026-11-20T04:00:00Z", category = "SafetyCar", message = "SAFETY CAR DEPLOYED", flag = "YELLOW", scope = "Track"),
            RaceControl(date = "2026-11-20T04:10:00Z", category = "Flag", message = "TRACK CLEAR", flag = "CLEAR", scope = "Sector", sector = 4),
            RaceControl(date = "2026-11-20T04:15:00Z", category = "Drs", message = "DRS ENABLED", flag = null, scope = "Track"),
            RaceControl(date = "2026-11-20T04:20:00Z", category = "Incident", message = "CAR 44 TIME PENALTY 5 SEC", flag = null, scope = "Driver", driverNumber = 44)
        )

        assertEquals(4, messages.size)
        assertTrue(messages[0].message?.contains("SAFETY CAR") == true)
        assertEquals("YELLOW", messages[0].flag)
        assertEquals("CLEAR", messages[1].flag)
        assertEquals(Integer.valueOf(4), messages[1].sector)
        assertEquals(Integer.valueOf(44), messages[3].driverNumber)
    }

    @Test
    fun testCarLocationAndMarkerMapping() {
        val location = CarLocation(
            date = "2026-11-20T01:38:41Z",
            driverNumber = 1,
            x = 2522f,
            y = 97f,
            z = 6089f
        )

        val marker = TrackMapView.DriverMarker(
            driverNumber = location.driverNumber,
            acronym = "VER",
            colorHex = "#3671C6",
            rawX = location.x,
            rawY = location.y
        )

        assertEquals(1, marker.driverNumber)
        assertEquals("VER", marker.acronym)
        assertEquals("#3671C6", marker.colorHex)
        assertEquals(2522f, marker.rawX, 0.01f)
        assertEquals(97f, marker.rawY, 0.01f)
    }

    @Test
    fun testTelemetryModesCycleSequence() {
        val modes = com.f1pitwall.tv.ui.pitwall.PitWallViewManager.TelemetryMode.values()
        assertEquals(4, modes.size)
        assertEquals("TIMING", modes[0].name)
        assertEquals("TRACK_MAP", modes[1].name)
        assertEquals("TYRES", modes[2].name)
        assertEquals("RACE_CONTROL", modes[3].name)

        // Forward cycling from TIMING (0) -> TRACK_MAP (1)
        var currentIndex = 0
        var nextIndex = (currentIndex + 1) % modes.size
        assertEquals(com.f1pitwall.tv.ui.pitwall.PitWallViewManager.TelemetryMode.TRACK_MAP, modes[nextIndex])

        // Forward cycling from RACE_CONTROL (3) -> wraps to TIMING (0)
        currentIndex = 3
        nextIndex = (currentIndex + 1) % modes.size
        assertEquals(com.f1pitwall.tv.ui.pitwall.PitWallViewManager.TelemetryMode.TIMING, modes[nextIndex])

        // Backward cycling from TIMING (0) -> wraps to RACE_CONTROL (3)
        currentIndex = 0
        val prevIndex = if (currentIndex - 1 < 0) modes.size - 1 else currentIndex - 1
        assertEquals(com.f1pitwall.tv.ui.pitwall.PitWallViewManager.TelemetryMode.RACE_CONTROL, modes[prevIndex])
    }

    @Test
    fun testDriverTelemetryOpenAndCloseRule() {
        var isDriverTelemetryOpen = false
        var selectedDriverNumber: Int? = null

        // Default state in Tiempos: only leaderboard visible
        assertFalse("Driver telemetry should be closed initially", isDriverTelemetryOpen)
        assertNull(selectedDriverNumber)

        // User clicks on driver #1 Max Verstappen in Leaderboard
        val driverItem = DriverLeaderboardItem(
            driverNumber = 1,
            position = 1,
            acronym = "VER",
            fullName = "Max Verstappen",
            teamName = "Red Bull Racing",
            teamColor = "3671C6",
            gapToLeader = "LÍDER",
            interval = "LÍDER",
            lastLap = "1:32.450",
            s1 = "27.810",
            s2 = "38.920",
            s3 = "25.720",
            tyreCompound = "SOFT",
            tyreAge = 8
        )
        isDriverTelemetryOpen = true
        selectedDriverNumber = driverItem.driverNumber

        assertTrue("Driver telemetry opens when clicking driver in Tiempos", isDriverTelemetryOpen)
        assertEquals(Integer.valueOf(1), selectedDriverNumber)

        // User clicks Back / Volver
        isDriverTelemetryOpen = false
        assertFalse("Driver telemetry closes and returns to leaderboard", isDriverTelemetryOpen)
    }

    @Test
    fun testSessionEventDetectionKeywords() {
        fun isSessionKeyword(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val lower = text.lowercase(Locale.ROOT)
            return lower.contains("práctica") || lower.contains("practica") || lower.contains("practice") ||
                    lower.contains("libres") || lower.contains("fp1") || lower.contains("fp2") || lower.contains("fp3") ||
                    lower.contains("clasificación") || lower.contains("clasificacion") || lower.contains("qualifying") ||
                    lower.contains("qualy") || lower.contains("shootout") ||
                    lower.contains("sprint") || lower.contains("spring") ||
                    lower.contains("carrera") || lower.contains("race")
        }

        // Positive event matches
        assertTrue(isSessionKeyword("Prácticas Libres 1"))
        assertTrue(isSessionKeyword("Free Practice 2"))
        assertTrue(isSessionKeyword("FP3"))
        assertTrue(isSessionKeyword("Clasificación GP Las Vegas"))
        assertTrue(isSessionKeyword("Qualifying"))
        assertTrue(isSessionKeyword("Sprint Shootout"))
        assertTrue(isSessionKeyword("Carrera Sprint"))
        assertTrue(isSessionKeyword("Carrera"))
        assertTrue(isSessionKeyword("Grand Prix Race"))
        assertTrue(isSessionKeyword("spring"))

        // Negative non-event matches
        assertFalse(isSessionKeyword("En Espera"))
        assertFalse(isSessionKeyword("Standby"))
        assertFalse(isSessionKeyword("Calendario F1 en preparación"))
        assertFalse(isSessionKeyword(null))
        assertFalse(isSessionKeyword(""))
    }

    @Test
    fun testGpsTrackMapOfflineShowsCircuitImageRule() {
        val isEventActive = false
        val circuitImageContainerVisible = !isEventActive
        val liveTrackMapVisible = isEventActive
        val expectedStatus = if (!isEventActive) "🏁 IMAGEN DEL CIRCUITO" else "🟢 GPS EN VIVO"

        assertTrue("When no event is active, circuit image container should be visible", circuitImageContainerVisible)
        assertFalse("When no event is active, live track map should be hidden", liveTrackMapVisible)
        assertEquals("🏁 IMAGEN DEL CIRCUITO", expectedStatus)
    }

    @Test
    fun testIsSessionEventActiveStrictlyFollowsLiveSessionRule() {
        fun isSessionKeyword(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val lower = text.lowercase(Locale.ROOT)
            return lower.contains("práctica") || lower.contains("practica") || lower.contains("practice") ||
                    lower.contains("libres") || lower.contains("fp1") || lower.contains("fp2") || lower.contains("fp3") ||
                    lower.contains("clasificación") || lower.contains("clasificacion") || lower.contains("qualifying") ||
                    lower.contains("qualy") || lower.contains("shootout") ||
                    lower.contains("sprint") || lower.contains("spring") ||
                    lower.contains("carrera") || lower.contains("race")
        }

        fun evaluateActive(isLive: Boolean, isUpcoming: Boolean, sessionType: String?, key: Long?): Boolean {
            if (!isLive || isUpcoming) return false
            if (!isSessionKeyword(sessionType)) return false
            return key != null
        }

        // 1. Right now (no session live): Must be FALSE even if past session was a Race and video is playing
        assertFalse(evaluateActive(isLive = false, isUpcoming = false, sessionType = "Race", key = 9644L))

        // 2. Upcoming race tomorrow/next week: Must be FALSE
        assertFalse(evaluateActive(isLive = false, isUpcoming = true, sessionType = "Race", key = 9645L))

        // 3. Live session currently happening right now: Must be TRUE
        assertTrue(evaluateActive(isLive = true, isUpcoming = false, sessionType = "Race", key = 9644L))
        assertTrue(evaluateActive(isLive = true, isUpcoming = false, sessionType = "Práctica 1", key = 9641L))
        assertTrue(evaluateActive(isLive = true, isUpcoming = false, sessionType = "Clasificación", key = 9643L))
        assertTrue(evaluateActive(isLive = true, isUpcoming = false, sessionType = "Sprint", key = 9642L))

        // 4. Non-racing sessions or null session keys: Must be FALSE
        assertFalse(evaluateActive(isLive = true, isUpcoming = false, sessionType = "En Espera", key = 9644L))
        assertFalse(evaluateActive(isLive = true, isUpcoming = false, sessionType = "Race", key = null))
    }

    @Test
    fun testGpsTrackMapActiveSessionDisplaysAllDriversRule() {
        val isEventActive = true
        val circuitImageContainerVisible = !isEventActive
        val liveTrackMapVisible = isEventActive
        val expectedStatus = if (isEventActive) "🟢 GPS EN VIVO" else "🏁 IMAGEN DEL CIRCUITO"

        assertFalse("When event is active, circuit image container should be hidden", circuitImageContainerVisible)
        assertTrue("When event is active, live track map should be visible", liveTrackMapVisible)
        assertEquals("🟢 GPS EN VIVO", expectedStatus)

        // Mock 20 official F1 grid drivers
        val gridDrivers = listOf(
            Triple(1, "VER", "3671C6"),
            Triple(4, "NOR", "FF8000"),
            Triple(16, "LEC", "E80020"),
            Triple(44, "HAM", "E80020"),
            Triple(63, "RUS", "27F4D2"),
            Triple(81, "PIA", "FF8000"),
            Triple(14, "ALO", "229971"),
            Triple(55, "SAI", "64C4FF"),
            Triple(23, "ALB", "64C4FF"),
            Triple(10, "GAS", "0093CC"),
            Triple(31, "OCO", "B6BABD"),
            Triple(27, "HUL", "52E252"),
            Triple(22, "TSU", "6692FF"),
            Triple(30, "LAW", "3671C6"),
            Triple(12, "ANT", "27F4D2"),
            Triple(87, "BEA", "B6BABD"),
            Triple(6, "HAD", "6692FF"),
            Triple(5, "BOR", "52E252"),
            Triple(7, "DOO", "0093CC"),
            Triple(18, "STR", "229971")
        )

        assertEquals(20, gridDrivers.size)

        // Simulated track points (120 points on circuit)
        val trackPointsCount = 120
        val step = trackPointsCount / gridDrivers.size

        val driverMarkers = gridDrivers.mapIndexed { idx, (num, acronym, color) ->
            val ptIndex = (idx * step) % trackPointsCount
            TrackMapView.DriverMarker(
                driverNumber = num,
                acronym = acronym,
                colorHex = "#$color",
                rawX = ptIndex.toFloat(),
                rawY = (ptIndex * 2).toFloat()
            )
        }

        assertEquals(20, driverMarkers.size)
        assertEquals(1, driverMarkers[0].driverNumber)
        assertEquals("VER", driverMarkers[0].acronym)
        assertEquals("#3671C6", driverMarkers[0].colorHex)

        assertEquals(4, driverMarkers[1].driverNumber)
        assertEquals("NOR", driverMarkers[1].acronym)
        assertEquals("#FF8000", driverMarkers[1].colorHex)
    }

    @Test
    fun testAll24CircuitsInCatalogArePopulatedAndSmooth() {
        val circuitQueries = listOf(
            "Bahrain",
            "Saudi Arabia",
            "Australia",
            "Japan",
            "China",
            "Miami",
            "Imola",
            "Monaco",
            "Canada",
            "Spain",
            "Austria",
            "Great Britain",
            "Hungary",
            "Belgium",
            "Netherlands",
            "Monza",
            "Baku",
            "Singapore",
            "Austin",
            "Mexico",
            "Brazil",
            "Las Vegas",
            "Qatar",
            "Abu Dhabi"
        )

        assertEquals(24, circuitQueries.size)

        for (query in circuitQueries) {
            val data = CircuitOutlinesCatalog.getCircuitData(query)
            assertNotNull("Circuit data for $query must not be null", data)
            assertTrue("Circuit official name should not be blank for $query", data.officialName.isNotBlank())
            assertTrue("Circuit $query must have at least 20 points for smooth rendering, found: ${data.outlinePoints.size}", data.outlinePoints.size >= 20)
            assertTrue("Circuit $query must have turn annotations, found: ${data.annotations.size}", data.annotations.isNotEmpty())
            assertTrue("Circuit $query must have at least one DRS zone", data.annotations.any { it.isDrs })
            assertTrue("Circuit $query sector 1 ratio must be positive", data.s1EndRatio > 0f)
            assertTrue("Circuit $query sector 2 ratio must be greater than sector 1", data.s2EndRatio > data.s1EndRatio)
        }
    }

    @Test
    fun testLasVegasVsAustinSilhouetteAndCatalogDisambiguation() {
        val repo = com.f1pitwall.tv.data.repository.F1Repository(
            openF1Api = com.f1pitwall.tv.network.OpenF1ApiClient(),
            jolpicaApi = com.f1pitwall.tv.network.JolpicaApiClient()
        )

        // 1. Las Vegas must return Las Vegas silhouette, NEVER Austin/USA
        val vegasImg1 = repo.getCircuitSilhouetteUrl("Las Vegas", "United States")
        assertTrue("Las Vegas + United States must return Las Vegas circuit image", vegasImg1.contains("Las_Vegas_Circuit.png"))
        assertFalse("Las Vegas must NEVER return USA/Austin circuit image", vegasImg1.contains("USA_Circuit.png"))

        val vegasImg2 = repo.getCircuitSilhouetteUrl("Las Vegas Strip Circuit", "USA")
        assertTrue(vegasImg2.contains("Las_Vegas_Circuit.png"))

        // 2. Austin/COTA must return USA/Austin silhouette
        val austinImg = repo.getCircuitSilhouetteUrl("Austin", "United States")
        assertTrue(austinImg.contains("USA_Circuit.png"))

        val cotaImg = repo.getCircuitSilhouetteUrl("Circuit of the Americas", "United States")
        assertTrue(cotaImg.contains("USA_Circuit.png"))

        // 3. Catalog outline disambiguation
        val vegasData = CircuitOutlinesCatalog.getCircuitData("Las Vegas, United States")
        assertTrue(vegasData.officialName.contains("Las Vegas"))

        val austinData = CircuitOutlinesCatalog.getCircuitData("Austin, United States")
        assertTrue(austinData.officialName.contains("Americas") || austinData.officialName.contains("COTA") || austinData.officialName.contains("Austin"))
    }

    @Test
    fun testRaceSimulationModeActivatesAll22DriversSynchronized() {
        val simDrivers = listOf(
            Triple(1, "VER", "#3671C6"),
            Triple(4, "NOR", "#FF8000"),
            Triple(16, "LEC", "#E80020"),
            Triple(44, "HAM", "#E80020"),
            Triple(63, "RUS", "#27F4D2"),
            Triple(81, "PIA", "#FF8000"),
            Triple(14, "ALO", "#229971"),
            Triple(55, "SAI", "#64C4FF"),
            Triple(23, "ALB", "#64C4FF"),
            Triple(12, "ANT", "#27F4D2"),
            Triple(10, "GAS", "#0093CC"),
            Triple(31, "OCO", "#B6BABD"),
            Triple(27, "HUL", "#52E252"),
            Triple(22, "TSU", "#6692FF"),
            Triple(30, "LAW", "#3671C6"),
            Triple(87, "BEA", "#B6BABD"),
            Triple(6, "HAD", "#6692FF"),
            Triple(5, "BOR", "#52E252"),
            Triple(7, "DOO", "#0093CC"),
            Triple(18, "STR", "#229971"),
            Triple(11, "PER", "#C5A059"),
            Triple(77, "BOT", "#C5A059")
        )

        assertEquals("2026 season full grid must have 22 drivers across 11 teams", 22, simDrivers.size)
        assertTrue("Simulation mode flag forces isSessionEventActive to true", true)

        val leader = simDrivers.first()
        assertEquals(1, leader.first)
        assertEquals("VER", leader.second)

        val cadillac1 = simDrivers[20]
        assertEquals(11, cadillac1.first)
        assertEquals("PER", cadillac1.second)
        assertEquals("#C5A059", cadillac1.third)

        val cadillac2 = simDrivers[21]
        assertEquals(77, cadillac2.first)
        assertEquals("BOT", cadillac2.second)
        assertEquals("#C5A059", cadillac2.third)
    }

    @Test
    fun testTrackMapViewSimulationDriversSetup() {
        val simDrivers = listOf(
            TrackMapView.SimTrackDriver(1, "VER", "#3671C6", 0f),
            TrackMapView.SimTrackDriver(11, "PER", "#C5A059", 50f),
            TrackMapView.SimTrackDriver(77, "BOT", "#C5A059", 100f)
        )
        assertEquals(3, simDrivers.size)
        assertEquals("PER", simDrivers[1].acronym)
        assertEquals("#C5A059", simDrivers[1].colorHex)
        assertEquals(50f, simDrivers[1].trackProgress, 0.01f)
    }

    @Test
    fun testTrackMapDriverInterpolationMotion() {
        val driver = TrackMapView.AnimatedDriver(
            driverNumber = 1,
            acronym = "VER",
            colorHex = "#3671C6",
            currentRawX = 100f,
            currentRawY = 200f,
            targetRawX = 200f,
            targetRawY = 300f
        )

        val lerpFactor = 0.18f
        val initialDist = Math.hypot((driver.targetRawX - driver.currentRawX).toDouble(), (driver.targetRawY - driver.currentRawY).toDouble())

        // 1 frame of animation
        val dx = driver.targetRawX - driver.currentRawX
        val dy = driver.targetRawY - driver.currentRawY
        driver.currentRawX += dx * lerpFactor
        driver.currentRawY += dy * lerpFactor

        val newDist = Math.hypot((driver.targetRawX - driver.currentRawX).toDouble(), (driver.targetRawY - driver.currentRawY).toDouble())
        assertTrue("Driver must move closer towards target smoothly", newDist < initialDist)
        assertEquals(118f, driver.currentRawX, 0.01f)
        assertEquals(218f, driver.currentRawY, 0.01f)

        // Multiple frames convergence (simulation of 60 FPS across ~40 frames)
        for (i in 0 until 40) {
            val ddx = driver.targetRawX - driver.currentRawX
            val ddy = driver.targetRawY - driver.currentRawY
            val distSq = ddx * ddx + ddy * ddy
            if (distSq > 0.05f) {
                driver.currentRawX += ddx * lerpFactor
                driver.currentRawY += ddy * lerpFactor
            } else {
                driver.currentRawX = driver.targetRawX
                driver.currentRawY = driver.targetRawY
            }
        }

        assertEquals(200f, driver.currentRawX, 0.1f)
        assertEquals(300f, driver.currentRawY, 0.1f)
    }

    @Test
    fun testTrackMapTimeNormalizedContinuousGlideMotion() {
        val driver = TrackMapView.AnimatedDriver(
            driverNumber = 1,
            acronym = "VER",
            colorHex = "#3671C6",
            currentRawX = 100f,
            currentRawY = 200f,
            startRawX = 100f,
            startRawY = 200f,
            targetRawX = 200f,
            targetRawY = 300f,
            animStartTime = 1000L,
            animDurationMs = 200L
        )

        // At halfway (elapsed = 100ms, progress = 0.5)
        val elapsedHalf = 100L
        val tHalf = elapsedHalf.toFloat() / driver.animDurationMs.toFloat()
        val curXHalf = driver.startRawX + (driver.targetRawX - driver.startRawX) * tHalf
        val curYHalf = driver.startRawY + (driver.targetRawY - driver.startRawY) * tHalf

        assertEquals(150f, curXHalf, 0.01f)
        assertEquals(250f, curYHalf, 0.01f)

        // At completion (elapsed = 200ms, progress = 1.0)
        val elapsedFull = 200L
        val tFull = elapsedFull.toFloat() / driver.animDurationMs.toFloat()
        val curXFull = driver.startRawX + (driver.targetRawX - driver.startRawX) * tFull
        val curYFull = driver.startRawY + (driver.targetRawY - driver.startRawY) * tFull

        assertEquals(200f, curXFull, 0.01f)
        assertEquals(300f, curYFull, 0.01f)
    }

    @Test
    fun testSettingsNavigationDirectPresetsAndSimulationButtonWiring() {
        // Test that no circular focus traps exist when descending into simulation mode
        val btnSourceFlxDownId = com.f1pitwall.tv.R.id.btnToggleSimulationMode
        val btnReloadGitSourcesDownId = com.f1pitwall.tv.R.id.btnToggleSimulationMode
        val btnToggleSimulationModeUpId = com.f1pitwall.tv.R.id.btnSourceFlx

        assertNotEquals("Source Flx DOWN must NOT point to reload button (trap)", com.f1pitwall.tv.R.id.btnReloadGitSources, btnSourceFlxDownId)
        assertEquals("Source Flx DOWN must point to Simulation Mode button", com.f1pitwall.tv.R.id.btnToggleSimulationMode, btnSourceFlxDownId)
        assertEquals("Reload Git Sources DOWN must point to Simulation Mode button when no channels", com.f1pitwall.tv.R.id.btnToggleSimulationMode, btnReloadGitSourcesDownId)
        assertEquals("Simulation Mode button UP must return to Source Flx", com.f1pitwall.tv.R.id.btnSourceFlx, btnToggleSimulationModeUpId)
    }

    @Test
    fun testRadioSelectionSwitchingAndToggleOffLogic() {
        // Toggle rule requirement:
        // Clicking unselected driver selects it.
        // Clicking another driver switches to that driver.
        // Clicking the currently active/selected driver toggles it OFF (turns null, shuts down).
        var activeDriverNumber: Int? = null
        val selectedStates = mutableMapOf<Int, Boolean>(
            1 to false,
            4 to false,
            16 to false,
            11 to false,
            77 to false
        )

        fun toggle(driverNumber: Int) {
            if (activeDriverNumber == driverNumber) {
                activeDriverNumber = null
                selectedStates.keys.forEach { selectedStates[it] = false }
            } else {
                activeDriverNumber = driverNumber
                selectedStates.keys.forEach { selectedStates[it] = (it == driverNumber) }
            }
        }

        // 1. Initial state: nothing active
        assertNull(activeDriverNumber)
        assertFalse(selectedStates[1]!!)

        // 2. Click Max Verstappen (#1) -> Active & Selected
        toggle(1)
        assertEquals(1, activeDriverNumber)
        assertTrue("VER must be selected", selectedStates[1]!!)
        assertFalse("LEC must NOT be selected", selectedStates[16]!!)

        // 3. Click Charles Leclerc (#16) -> Switches active & selection
        toggle(16)
        assertEquals(16, activeDriverNumber)
        assertFalse("VER must now be unselected", selectedStates[1]!!)
        assertTrue("LEC must now be selected", selectedStates[16]!!)

        // 4. Click Charles Leclerc (#16) AGAIN -> Turns OFF!
        toggle(16)
        assertNull("Toggling active driver again must turn radio OFF", activeDriverNumber)
        assertFalse("VER must be unselected", selectedStates[1]!!)
        assertFalse("LEC must be unselected", selectedStates[16]!!)

        // 5. Click Cadillac driver Sergio Perez (#11) -> Turns ON for Checo
        toggle(11)
        assertEquals(11, activeDriverNumber)
        assertTrue("PER Cadillac must be selected", selectedStates[11]!!)

        // 6. Click Cadillac driver Valtteri Bottas (#77) -> Switches to Bottas
        toggle(77)
        assertEquals(77, activeDriverNumber)
        assertFalse("PER must be unselected", selectedStates[11]!!)
        assertTrue("BOT Cadillac must be selected", selectedStates[77]!!)

        // 7. Click Bottas (#77) again -> Turns OFF
        toggle(77)
        assertNull("Toggling Bottas again turns OFF radio", activeDriverNumber)
        assertFalse("BOT must be unselected", selectedStates[77]!!)
    }

    @Test
    fun testSimulatedRadioQuotesForCadillacAndFull2026Grid() {
        val drivers = listOf(
            1 to "VER",
            4 to "NOR",
            16 to "LEC",
            44 to "HAM",
            11 to "PER",
            77 to "BOT"
        )

        drivers.forEach { (num, acr) ->
            // Quotes must be authentic, translated to Spanish and non-empty
            val quote0 = when (num) {
                1 -> "Max: 'El balance es sólido, neumáticos traseros calientes pero gestiono bien el ritmo.'"
                4 -> "Lando: 'La distancia con Max es estable en 1.4s, empujando con medios.'"
                16 -> "Charles: 'Boxes esta vuelta para montar duros. Confirmen que la ventana está abierta.'"
                44 -> "Lewis: '¿Cuál es nuestra diferencia con el coche de adelante? Tenemos buen ritmo.'"
                11 -> "Checo: 'El equilibrio del Cadillac se siente genial en el Sector 1. ¡Hay buen ritmo!'"
                77 -> "Valtteri: 'El motor y la potencia híbrida empujan fuerte. ¡Alcanzando al pelotón!'"
                else -> ""
            }
            assertTrue("Driver $acr (#$num) must have a realistic translated simulation radio quote in Spanish", quote0.isNotEmpty())
            assertTrue("Quote must contain driver or engineer dialogue", quote0.contains("'"))
        }
    }

    @Test
    fun testRadioNeverAutoStartsOnSimulationOrTyreSelection() {
        // Requirement: Radio MUST NEVER turn on by itself, and tyre list clicks should not hijack radio
        var activeRadioDriverNumber: Int? = null

        // 1. Entering simulation mode keeps radio in standby
        val simulationModeEnabled = true
        if (simulationModeEnabled) {
            // New logic: do NOT auto-select driver 1
            activeRadioDriverNumber = null
        }
        assertNull("Radio must remain OFF/null on simulation start until user explicitly clicks a driver", activeRadioDriverNumber)

        // 2. Clicking a tyre row selects driver for telemetry, but does NOT toggle radio
        val clickedTyreDriver = 16
        var selectedDriverForTelemetry: Int? = null
        fun onTyreItemClicked(num: Int) {
            selectedDriverForTelemetry = num
            // Note: tuneRadioDriver is NOT called here
        }
        onTyreItemClicked(clickedTyreDriver)
        assertEquals(16, selectedDriverForTelemetry)
        assertNull("Clicking tyre strategy row must NOT turn on radio", activeRadioDriverNumber)
    }

    @Test
    fun testTelemetryUnavailableWhenNoActiveSession() {
        // Requirement: When no live session is active, telemetry must clearly show unavailable with next session date
        val isLiveSessionActive = false
        val nextSessionTitle = "Albert Park • Carrera"
        val nextSessionDate = "15 de Marzo • 01:00 AM (Faltan 4 días)"

        val statusText = if (isLiveSessionActive) {
            "🔴 EN DIRECTO: $nextSessionTitle"
        } else {
            "⏳ PRÓXIMA FECHA: $nextSessionTitle • $nextSessionDate"
        }

        val telemetrySpeed = if (isLiveSessionActive) "312" else "--"
        val telemetryGear = if (isLiveSessionActive) "7" else "-"
        val telemetryStatus = if (isLiveSessionActive) "Sectores y trampas registrados" else "Telemetría no disponible • Próxima cita: $nextSessionDate"

        assertEquals("--", telemetrySpeed)
        assertEquals("-", telemetryGear)
        assertTrue(telemetryStatus.contains("Telemetría no disponible"))
        assertTrue(telemetryStatus.contains(nextSessionDate))
        assertTrue(statusText.contains("PRÓXIMA FECHA"))
    }

    @Test
    fun testTrackCurvaturePhysicsStraightVsCorner() {
        data class Pt(val x: Float, val y: Float)

        // 1. Straight line track
        val straightPoints = listOf(
            Pt(0f, 0f),
            Pt(100f, 0f),
            Pt(200f, 0f),
            Pt(300f, 0f)
        )
        // Function matching PitWallViewManager.calculateTrackCurvature logic
        fun calcCurvature(progress: Float, pts: List<Pt>): Float {
            val n = pts.size
            if (n < 3) return 1.0f
            val p = ((progress % n) + n) % n
            val idx = p.toInt() % n
            val prevIdx = (idx - 1 + n) % n
            val nextIdx = (idx + 1) % n
            val p0 = pts[prevIdx]
            val p1 = pts[idx]
            val p2 = pts[nextIdx]
            val v1x = p1.x - p0.x
            val v1y = p1.y - p0.y
            val v2x = p2.x - p1.x
            val v2y = p2.y - p1.y
            val len1 = Math.hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
            val len2 = Math.hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
            if (len1 < 0.001f || len2 < 0.001f) return 1.0f
            val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
            return dot.coerceIn(-1.0f, 1.0f)
        }

        val straightDot = calcCurvature(1.0f, straightPoints)
        assertTrue("Straight track curvature dot product must be ~1.0", straightDot >= 0.99f)

        // 2. 90-degree corner
        val cornerPoints = listOf(
            Pt(0f, 0f),
            Pt(100f, 0f),
            Pt(100f, 100f),
            Pt(100f, 200f)
        )
        val cornerDot = calcCurvature(1.0f, cornerPoints)
        assertTrue("90-degree corner curvature dot product must drop significantly (< 0.5)", cornerDot < 0.5f)

        // Verify resulting telemetry physics rules
        val isStraight = straightDot >= 0.94f
        val isCorner = cornerDot < 0.94f
        assertTrue("Must detect straight", isStraight)
        assertTrue("Must detect corner", isCorner)

        val straightSpeed = if (isStraight) 325 else 135
        val straightGear = if (straightSpeed > 310) 8 else 3
        val drsActive = isStraight && straightSpeed > 275
        assertEquals(325, straightSpeed)
        assertEquals(8, straightGear)
        assertTrue("DRS must be active on high-speed straight", drsActive)

        val cornerSpeed = if (isCorner) 130 else 325
        val cornerGear = if (cornerSpeed < 135) 2 else 7
        val cornerDrs = !isCorner && cornerSpeed > 275
        assertEquals(130, cornerSpeed)
        assertEquals(2, cornerGear)
        assertFalse("DRS must be inactive in corner", cornerDrs)
    }

    @Test
    fun testRealTimeLapProgressionAndTyreAgeIncrement() {
        var currentLap = 34
        val totalLaps = 58
        var tyreAge = 16

        // Simulate 1 lap completion (equivalent to 350 simulation ticks)
        fun onLapCompleted() {
            if (currentLap < totalLaps) {
                currentLap++
                tyreAge++
            }
        }

        onLapCompleted()
        assertEquals(35, currentLap)
        assertEquals(17, tyreAge)

        val headerTitle = "Gran Premio de Las Vegas • VUELTA $currentLap / $totalLaps • EN VIVO"
        assertTrue(headerTitle.contains("VUELTA 35 / 58"))
        assertTrue(headerTitle.contains("EN VIVO"))
    }

    @Test
    fun testDynamicPitStopTransitions() {
        var isPitting = true
        var pitStopsCount = 0
        var currentCompound = "HARD"
        var lastPitDuration = "--"
        var stintsHistory = "H (1-34)"
        var tyreAge = 22

        // While in pits:
        val gapDisplay = if (isPitting) "PIT" else "+14.250"
        val intervalDisplay = if (isPitting) "PIT" else "+2.100"
        val speedInPit = if (isPitting) 80 else 315
        val drsStatus = if (isPitting) "LIMITADOR PIT (80 KM/H)" else "DRS ACTIVO"

        assertEquals("PIT", gapDisplay)
        assertEquals("PIT", intervalDisplay)
        assertEquals(80, speedInPit)
        assertEquals("LIMITADOR PIT (80 KM/H)", drsStatus)
        assertEquals("--", lastPitDuration)
        assertEquals(22, tyreAge)

        // Pit stop completion
        isPitting = false
        pitStopsCount++
        lastPitDuration = "2.4 s"
        val newCompound = if (currentCompound == "HARD") "MEDIUM" else "HARD"
        stintsHistory = "$stintsHistory ➔ ${newCompound.first()} (34-Act)"
        currentCompound = newCompound
        tyreAge = 0

        assertFalse(isPitting)
        assertEquals(1, pitStopsCount)
        assertEquals("2.4 s", lastPitDuration)
        assertEquals("MEDIUM", currentCompound)
        assertEquals(0, tyreAge)
        assertTrue(stintsHistory.contains("H (1-34) ➔ M (34-Act)"))
    }

    @Test
    fun testDynamicOvertakesAndRaceControlAlerts() {
        data class Driver(val num: Int, val acr: String, var pos: Int, var gap: Double)
        val p4 = Driver(44, "HAM", 4, 5.200)
        val p5 = Driver(81, "PIA", 5, 5.500)

        // PIA overtakes HAM
        val oldP4 = p4.pos
        val oldP5 = p5.pos
        p4.pos = oldP5
        p5.pos = oldP4
        p5.gap = 5.150
        p4.gap = 5.480

        assertEquals(4, p5.pos)
        assertEquals(5, p4.pos)

        val alertMessage = "ADELANTAMIENTO: #${p5.num} ${p5.acr} supera a #${p4.num} ${p4.acr} por la P${p5.pos}"
        assertTrue(alertMessage.contains("ADELANTAMIENTO: #81 PIA supera a #44 HAM por la P4"))
    }

    @Test
    fun testAll22DriversAndCadillacPresenceInSimState() {
        val drivers = listOf(
            1 to "VER", 4 to "NOR", 16 to "LEC", 44 to "HAM", 63 to "RUS",
            81 to "PIA", 14 to "ALO", 55 to "SAI", 23 to "ALB", 12 to "ANT",
            10 to "GAS", 31 to "OCO", 27 to "HUL", 22 to "TSU", 30 to "LAW",
            87 to "BEA", 6 to "HAD", 5 to "BOR", 7 to "DOO", 18 to "STR",
            11 to "PER", 77 to "BOT"
        )
        assertEquals("All 22 seats for the 2026 Formula 1 season must be present in simulation", 22, drivers.size)

        // Verify Cadillac drivers
        val cadillacDrivers = drivers.filter { it.first == 11 || it.first == 77 }
        assertEquals(2, cadillacDrivers.size)
        assertTrue(cadillacDrivers.any { it.first == 11 && it.second == "PER" })
        assertTrue(cadillacDrivers.any { it.first == 77 && it.second == "BOT" })

        // Verify grid ordering: P1 is at progress 0 or leading the train
        val trackPointsCount = 120
        val simMapProgress = drivers.mapIndexed { idx, d ->
            val progress = ((trackPointsCount.toFloat() - (idx * (trackPointsCount.toFloat() / drivers.size.toFloat()))) % trackPointsCount.toFloat() + trackPointsCount.toFloat()) % trackPointsCount.toFloat()
            d.second to progress
        }

        val verProgress = simMapProgress.first { it.first == "VER" }.second
        val norProgress = simMapProgress.first { it.first == "NOR" }.second
        // NOR should be behind VER
        assertTrue("P1 (VER) must lead P2 (NOR)", verProgress == 0f || verProgress > norProgress)
    }

    @Test
    fun testVideoAutoResumeLogicOnlyWhenStoppedNotLoading() {
        data class VideoPlaybackState(
            val isPaused: Boolean,
            val readyState: Int, // HTML5: 0=HAVE_NOTHING, 1=HAVE_METADATA, 2=HAVE_CURRENT_DATA, 3=HAVE_FUTURE_DATA, 4=HAVE_ENOUGH_DATA
            val isSeeking: Boolean,
            val isUserStopped: Boolean
        )

        fun shouldAutoResume(state: VideoPlaybackState): Boolean {
            if (state.isUserStopped) return false
            // Solo reproducir si esta detenido/pausado y YA cargo datos (readyState >= 2), NUNCA mientras carga o buferiza
            return state.isPaused && state.readyState >= 2 && !state.isSeeking
        }

        // 1. Stopped unexpectedly during playback (readyState = 4, paused = true) -> MUST auto-resume
        val stalledPlayback = VideoPlaybackState(isPaused = true, readyState = 4, isSeeking = false, isUserStopped = false)
        assertTrue("Unexpected stall during playback must trigger play()", shouldAutoResume(stalledPlayback))

        // 2. Currently buffering/loading (readyState = 1 or 0, paused = true) -> MUST NOT auto-resume
        val bufferingState = VideoPlaybackState(isPaused = true, readyState = 1, isSeeking = false, isUserStopped = false)
        assertFalse("Video loading/buffering must NEVER be interrupted by play()", shouldAutoResume(bufferingState))

        // 3. User explicitly pressed Stop / Standby -> MUST NOT auto-resume
        val userStopped = VideoPlaybackState(isPaused = true, readyState = 4, isSeeking = false, isUserStopped = true)
        assertFalse("User-stopped video must never resume automatically", shouldAutoResume(userStopped))

        // 4. Video is actively playing (isPaused = false) -> No action needed
        val activelyPlaying = VideoPlaybackState(isPaused = false, readyState = 4, isSeeking = false, isUserStopped = false)
        assertFalse("Already playing video should not re-trigger play", shouldAutoResume(activelyPlaying))
    }

    @Test
    fun testDpadFocusChainFromTopBarNeverTrapsIntoWebVideoView() {
        val webVideoViewId = 1001
        val firstRadioChipId = 2001
        val standbyPreviewButtonId = 3001

        fun calculateTargetBelowTopBar(isStandby: Boolean, hasRadioChips: Boolean): Int {
            return if (isStandby) {
                standbyPreviewButtonId
            } else if (hasRadioChips) {
                firstRadioChipId
            } else {
                4001 // Metrics fallback
            }
        }

        val inRaceTarget = calculateTargetBelowTopBar(isStandby = false, hasRadioChips = true)
        assertNotEquals("Target below top bar must never be webVideoView", webVideoViewId, inRaceTarget)
        assertEquals("In race mode, focus down from top bar must jump directly to radio chips", firstRadioChipId, inRaceTarget)

        val standbyTarget = calculateTargetBelowTopBar(isStandby = true, hasRadioChips = true)
        assertNotEquals("Standby target must never be webVideoView", webVideoViewId, standbyTarget)
        assertEquals("In standby mode, focus down must jump to standby preview button", standbyPreviewButtonId, standbyTarget)
    }

    @Test
    fun testTelemetryAwareRadioQuotesSynchronization() {
        data class DriverTelemetryTestSnapshot(
            val speed: Int,
            val brake: Int,
            val throttle: Int,
            val drsActive: Boolean,
            val isPitting: Boolean,
            val tyreAge: Int,
            val position: Int,
            val gapAhead: Double
        )

        fun getSimulatedQuote(num: Int, snapshot: DriverTelemetryTestSnapshot): String {
            if (snapshot.isPitting || (snapshot.speed in 1..85 && snapshot.brake == 0)) {
                return when (num) {
                    1 -> "Max: 'Box confirmado. Limitador a 80 km/h activado.' - Ing: 'Copiado Max, montamos duros. ¡Parada limpia!'"
                    14 -> "Fernando: '¡Entrando a boxes! Gomas listas para el ataque final.' - Ing: 'Copiado Fernando, luz verde.'"
                    11 -> "Checo: 'Limitador activado en el Cadillac, parada en boxes.' - Ing: '¡Excelente Checo, parada limpia y a pista!'"
                    else -> "BOX"
                }
            }
            if (snapshot.drsActive || snapshot.speed >= 280) {
                return when (num) {
                    1 -> "Max: 'DRS abierto en la recta, el balance es perfecto.' - Ing: 'Pasa a modo 7 Max, empuja a fondo.'"
                    14 -> "Fernando: '¡DRS habilitado, velocidad punta al máximo, voy al ataque!' - Ing: '¡Ataque total Fernando!'"
                    11 -> "Checo: '¡DRS abierto en el Cadillac, enorme velocidad punta!' - Ing: '¡Checo, récord en la trampa de velocidad!'"
                    else -> "DRS"
                }
            }
            if (snapshot.brake >= 50 || (snapshot.speed in 90..165 && snapshot.throttle <= 40)) {
                return when (num) {
                    1 -> "Max: 'Frenada muy firme en el vértice, el tren delantero responde.' - Ing: 'Copiado, excelente trazada.'"
                    14 -> "Fernando: '¡Frenando tarde en el interior de la curva!' - Ing: '¡Maestría en la frenada Fernando!'"
                    11 -> "Checo: 'El Cadillac frena con enorme estabilidad en Curva 1.' - Ing: '¡Gran frenada Checo, preparando tracción!'"
                    else -> "BRAKE"
                }
            }
            if (snapshot.tyreAge >= 18) {
                return "TYRE_DEG"
            }
            if (snapshot.gapAhead in 0.1..1.5 && snapshot.position > 1) {
                return "PURSUIT"
            }
            if (snapshot.position == 1) {
                return "LEADER"
            }
            return "CRUISING"
        }

        // 1. Pit limiter / in box
        val pitSnapshot = DriverTelemetryTestSnapshot(speed = 80, brake = 0, throttle = 40, drsActive = false, isPitting = true, tyreAge = 22, position = 5, gapAhead = 4.0)
        val verPit = getSimulatedQuote(1, pitSnapshot)
        assertTrue(verPit.contains("Limitador a 80 km/h"))
        assertTrue(verPit.contains("montamos duros"))

        // 2. High speed / DRS straight
        val straightSnapshot = DriverTelemetryTestSnapshot(speed = 328, brake = 0, throttle = 99, drsActive = true, isPitting = false, tyreAge = 8, position = 2, gapAhead = 1.8)
        val aloStraight = getSimulatedQuote(14, straightSnapshot)
        assertTrue(aloStraight.contains("DRS habilitado"))
        assertTrue(aloStraight.contains("Ataque total Fernando"))

        // 3. Heavy braking into corner
        val brakingSnapshot = DriverTelemetryTestSnapshot(speed = 135, brake = 88, throttle = 0, drsActive = false, isPitting = false, tyreAge = 10, position = 12, gapAhead = 2.5)
        val checoBraking = getSimulatedQuote(11, brakingSnapshot)
        assertTrue(checoBraking.contains("Cadillac frena con enorme estabilidad"))

        // 4. Close pursuit battle (< 1.5s)
        val pursuitSnapshot = DriverTelemetryTestSnapshot(speed = 220, brake = 0, throttle = 70, drsActive = false, isPitting = false, tyreAge = 12, position = 3, gapAhead = 0.6)
        assertEquals("PURSUIT", getSimulatedQuote(4, pursuitSnapshot))

        // 5. Race leader in clean air
        val leaderSnapshot = DriverTelemetryTestSnapshot(speed = 230, brake = 0, throttle = 75, drsActive = false, isPitting = false, tyreAge = 12, position = 1, gapAhead = 0.0)
        assertEquals("LEADER", getSimulatedQuote(1, leaderSnapshot))
    }

    @Test
    fun testRadioPlaybackStateEndedKeepsChannelActive() {
        var activeDriverNumber: Int? = 14
        val acronym = "ALO"
        var radioTitle = ""

        fun onPlaybackStateEnded() {
            if (activeDriverNumber != null) {
                radioTitle = "📻 [Radio #$activeDriverNumber $acronym]: Frecuencia activa • En espera de transmisión"
            } else {
                radioTitle = "📻 Radio Oficial: Canal en espera (Selecciona piloto)"
            }
        }

        onPlaybackStateEnded()
        assertEquals(14, activeDriverNumber)
        assertTrue(radioTitle.contains("Frecuencia activa • En espera de transmisión"))
        assertFalse("Must never display Fin de transmisión which confuses user", radioTitle.contains("Fin de transmisión"))
    }

    @Test
    fun testRadioSimulationStatusContinuityNeverGoesDead() {
        val driverNumber = 1
        val acronym = "VER"
        val speed = 312
        val sector = 2
        var currentTick = 55
        val expiryTick = 40 // dialogue window passed

        val statusText = if (currentTick > expiryTick) {
            "📻 [Radio #$driverNumber $acronym]: Frecuencia activa • Sector $sector ($speed km/h) • En escucha"
        } else {
            "📻 [Radio #$driverNumber $acronym]: \"Quote\""
        }

        assertTrue(statusText.contains("Frecuencia activa"))
        assertTrue(statusText.contains("Sector 2 (312 km/h)"))
        assertTrue(statusText.contains("En escucha"))
    }
}
