package com.f1pitwall.tv.data.mapper

import com.f1pitwall.tv.data.model.JolpicaConstructor
import com.f1pitwall.tv.data.model.JolpicaDriver
import com.f1pitwall.tv.data.model.JolpicaResult
import com.f1pitwall.tv.data.model.JolpicaTime
import org.junit.Assert.*
import org.junit.Test

class OfficialResultsMapperTest {

    @Test
    fun `empty results returns empty list`() {
        val items = OfficialResultsMapper.mapJolpicaResultsToLeaderboardItems(emptyList())
        assertTrue("Mapeo de resultados vacíos debe ser una lista vacía", items.isEmpty())
    }

    @Test
    fun `maps official race classification correctly with winner and intervals`() {
        val results = listOf(
            JolpicaResult(
                number = "1",
                position = "1",
                points = "25",
                driver = JolpicaDriver(
                    driverId = "max_verstappen",
                    permanentNumber = "1",
                    code = "VER",
                    givenName = "Max",
                    familyName = "Verstappen"
                ),
                constructor = JolpicaConstructor("red_bull", "Red Bull Racing"),
                grid = "1",
                laps = "57",
                status = "Finished",
                time = JolpicaTime(millis = "5295540", time = "1:28:15.540")
            ),
            JolpicaResult(
                number = "4",
                position = "2",
                points = "18",
                driver = JolpicaDriver(
                    driverId = "norris",
                    permanentNumber = "4",
                    code = "NOR",
                    givenName = "Lando",
                    familyName = "Norris"
                ),
                constructor = JolpicaConstructor("mclaren", "McLaren"),
                grid = "2",
                laps = "57",
                status = "Finished",
                time = JolpicaTime(millis = null, time = "+5.432")
            )
        )

        val items = OfficialResultsMapper.mapJolpicaResultsToLeaderboardItems(results) { team ->
            if (team.contains("Red Bull")) "3671C6" else "FF8000"
        }

        assertEquals(2, items.size)

        // P1 Winner
        val p1 = items[0]
        assertEquals(1, p1.driverNumber)
        assertEquals(1, p1.position)
        assertEquals("VER", p1.acronym)
        assertEquals("Max Verstappen", p1.fullName)
        assertEquals("Red Bull Racing", p1.teamName)
        assertEquals("3671C6", p1.teamColor)
        assertEquals("1:28:15.540", p1.gapToLeader)
        assertEquals("+25 pts", p1.interval)
        assertEquals("57 Vueltas", p1.lastLap)
        assertEquals("Grid: P1", p1.s1)
        assertNull(p1.tyreCompound)
        assertNull(p1.tyreAge)

        // P2 Second place
        val p2 = items[1]
        assertEquals(4, p2.driverNumber)
        assertEquals(2, p2.position)
        assertEquals("NOR", p2.acronym)
        assertEquals("Lando Norris", p2.fullName)
        assertEquals("McLaren", p2.teamName)
        assertEquals("FF8000", p2.teamColor)
        assertEquals("+5.432", p2.gapToLeader)
        assertEquals("+18 pts", p2.interval)
        assertEquals("57 Vueltas", p2.lastLap)
        assertEquals("Grid: P2", p2.s1)
    }

    @Test
    fun `formats DNF and special statuses accurately`() {
        assertEquals("Finalizado", OfficialResultsMapper.formatStatus("Finished"))
        assertEquals("+1 Lap", OfficialResultsMapper.formatStatus("+1 Lap"))
        assertEquals("+2 Laps", OfficialResultsMapper.formatStatus("+2 Laps"))
        assertEquals("DNF (Accidente)", OfficialResultsMapper.formatStatus("Accident"))
        assertEquals("DNF (Accidente)", OfficialResultsMapper.formatStatus("Collision"))
        assertEquals("DNF (Motor)", OfficialResultsMapper.formatStatus("Engine"))
        assertEquals("Descalificado", OfficialResultsMapper.formatStatus("Disqualified"))
        assertEquals("No inició", OfficialResultsMapper.formatStatus("Did not start"))
        assertEquals("DNF (Retirado)", OfficialResultsMapper.formatStatus("Retired"))
        assertEquals("DNF (Gearbox)", OfficialResultsMapper.formatStatus("Gearbox"))
    }

    @Test
    fun `handles DNF driver in race results without time object`() {
        val results = listOf(
            JolpicaResult(
                number = "16",
                position = "19",
                points = "0",
                driver = JolpicaDriver(
                    driverId = "leclerc",
                    permanentNumber = "16",
                    code = "LEC",
                    givenName = "Charles",
                    familyName = "Leclerc"
                ),
                constructor = JolpicaConstructor("ferrari", "Ferrari"),
                grid = "4",
                laps = "23",
                status = "Engine",
                time = null
            )
        )

        val items = OfficialResultsMapper.mapJolpicaResultsToLeaderboardItems(results)
        assertEquals(1, items.size)
        val dnf = items[0]
        assertEquals(19, dnf.position)
        assertEquals("LEC", dnf.acronym)
        assertEquals("DNF (Motor)", dnf.gapToLeader)
        assertEquals("Engine", dnf.interval)
        assertEquals("23 Vueltas", dnf.lastLap)
    }
}
