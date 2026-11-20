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
}
