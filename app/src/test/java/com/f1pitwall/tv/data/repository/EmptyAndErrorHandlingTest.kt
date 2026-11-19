package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.core.util.DrsDisplayState
import com.f1pitwall.tv.core.util.PitWallFormatter
import com.f1pitwall.tv.core.util.SessionStatusResolver
import com.f1pitwall.tv.data.mapper.LeaderboardMapper
import com.f1pitwall.tv.data.mapper.OfficialResultsMapper
import com.f1pitwall.tv.data.model.JolpicaConstructor
import com.f1pitwall.tv.data.model.JolpicaDriver
import com.f1pitwall.tv.data.model.JolpicaResult
import com.f1pitwall.tv.model.Driver
import com.f1pitwall.tv.model.Position
import com.f1pitwall.tv.model.SessionStatusType
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Fixture-based tests verifying robust handling of empty data, null values,
 * and error scenarios without producing invented F1 information.
 */
class EmptyAndErrorHandlingTest {

    @Test
    fun `completely null telemetry returns standard unavailable placeholders without crashing`() {
        assertEquals("--", PitWallFormatter.formatSpeed(null))
        assertEquals("--", PitWallFormatter.formatGear(null))
        assertEquals("RPM: --", PitWallFormatter.formatRpm(null))
        assertEquals("Acelerador: --", PitWallFormatter.formatThrottle(null))
        assertEquals("Freno: --", PitWallFormatter.formatBrake(null))
        assertEquals(DrsDisplayState.UNAVAILABLE, PitWallFormatter.resolveDrsState(null))
        assertEquals("Pista: -- | Aire: --", PitWallFormatter.formatWeatherTemperature(null, null))
        assertEquals("No disponible", PitWallFormatter.formatTelemetrySessionStatus(isLive = false, hasSession = false))
    }

    @Test
    fun `official results mapper handles retired and disqualified entries safely`() {
        val testFixtures = listOf(
            JolpicaResult(
                number = "55",
                position = "19",
                points = null,
                driver = JolpicaDriver(
                    driverId = "sainz",
                    permanentNumber = null,
                    code = "SAI",
                    givenName = "Carlos",
                    familyName = "Sainz"
                ),
                constructor = JolpicaConstructor("ferrari", "Ferrari"),
                grid = "4",
                laps = "22",
                status = "Spun off",
                time = null
            ),
            JolpicaResult(
                number = "20",
                position = "20",
                points = "0",
                driver = JolpicaDriver(
                    driverId = "kevin_magnussen",
                    permanentNumber = "20",
                    code = "MAG",
                    givenName = "Kevin",
                    familyName = "Magnussen"
                ),
                constructor = JolpicaConstructor("haas", "Haas"),
                grid = "15",
                laps = "56",
                status = "Disqualified",
                time = null
            )
        )

        val items = OfficialResultsMapper.mapJolpicaResultsToLeaderboardItems(testFixtures) { "E80020" }

        assertEquals(2, items.size)

        val dnf = items[0]
        assertEquals(55, dnf.driverNumber)
        assertEquals("SAI", dnf.acronym)
        assertEquals("Carlos Sainz", dnf.fullName)
        assertEquals("DNF (Spun off)", dnf.gapToLeader)
        assertEquals("Spun off", dnf.interval)
        assertEquals("22 Vueltas", dnf.lastLap)

        val dsq = items[1]
        assertEquals(20, dsq.driverNumber)
        assertEquals("MAG", dsq.acronym)
        assertEquals("Descalificado", dsq.gapToLeader)
        assertEquals("Disqualified", dsq.interval)
    }

    @Test
    fun `leaderboard mapper handles empty drivers list gracefully with fallback identifiers`() {
        val positions = listOf(
            Position("2026-11-20T14:10:00", 1, 1, 100L)
        )

        val items = LeaderboardMapper.buildLeaderboardItems(
            drivers = emptyList(),
            positions = positions,
            intervals = emptyList(),
            laps = emptyList(),
            stints = emptyList()
        )

        // When driver profile is missing, fallback uses car number without crashing
        assertEquals(1, items.size)
        assertEquals("#1", items[0].acronym)
        assertEquals("Piloto 1", items[0].fullName)
    }

    @Test
    fun `session status resolver handles empty sessions list safely`() {
        val result = SessionStatusResolver.resolveMeetingSessions(
            sessions = emptyList(),
            now = Date()
        )

        assertEquals(SessionStatusType.NO_DATA, result.status)
        assertNull(result.activeSession)
        assertNull(result.lastCompletedSession)
        assertNull(result.upcomingSession)
    }
}
