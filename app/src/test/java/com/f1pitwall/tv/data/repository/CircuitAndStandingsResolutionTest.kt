package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.model.Meeting
import org.junit.Assert.*
import org.junit.Test

class CircuitAndStandingsResolutionTest {

    @Test
    fun `circuit matching with stable meetingKey resolves accurately among same country circuits`() {
        val miamiMeeting = Meeting(
            meetingKey = 1250L,
            meetingName = "Miami Grand Prix",
            officialName = "Formula 1 Crypto.com Miami Grand Prix 2026",
            location = "Miami",
            countryName = "United States",
            countryCode = "USA",
            countryFlag = null,
            circuitKey = 151,
            circuitShortName = "Miami",
            circuitImage = null,
            dateStart = "2026-05-01T10:00:00",
            dateEnd = "2026-05-03T18:00:00",
            gmtOffset = "-04:00",
            year = 2026,
            isCancelled = false
        )

        val austinMeeting = Meeting(
            meetingKey = 1260L,
            meetingName = "United States Grand Prix",
            officialName = "Formula 1 Lenovo United States Grand Prix 2026",
            location = "Austin",
            countryName = "United States",
            countryCode = "USA",
            countryFlag = null,
            circuitKey = 9,
            circuitShortName = "Austin",
            circuitImage = null,
            dateStart = "2026-10-16T10:00:00",
            dateEnd = "2026-10-18T18:00:00",
            gmtOffset = "-05:00",
            year = 2026,
            isCancelled = false
        )

        val lasVegasMeeting = Meeting(
            meetingKey = 1270L,
            meetingName = "Las Vegas Grand Prix",
            officialName = "Formula 1 Heineken Silver Las Vegas Grand Prix 2026",
            location = "Las Vegas",
            countryName = "United States",
            countryCode = "USA",
            countryFlag = null,
            circuitKey = 152,
            circuitShortName = "Las Vegas",
            circuitImage = null,
            dateStart = "2026-11-19T10:00:00",
            dateEnd = "2026-11-21T18:00:00",
            gmtOffset = "-08:00",
            year = 2026,
            isCancelled = false
        )

        val meetings = listOf(miamiMeeting, austinMeeting, lasVegasMeeting)

        // Simular selección de tarjeta de Las Vegas por meetingKey
        val selectedLasVegas = CircuitCardItem(
            circuitKey = 152,
            meetingKey = 1270L,
            circuitName = "Las Vegas",
            location = "Las Vegas",
            country = "United States"
        )

        // Matching estricto usando meetingKey y circuitKey
        val match = if (selectedLasVegas.meetingKey != null) {
            meetings.find { it.meetingKey == selectedLasVegas.meetingKey }
        } else if (selectedLasVegas.circuitKey > 0) {
            meetings.find { it.circuitKey == selectedLasVegas.circuitKey }
        } else null

        assertNotNull(match)
        assertEquals(1270L, match!!.meetingKey)
        assertEquals("Las Vegas Grand Prix", match.meetingName)
        assertNotEquals("Miami Grand Prix", match.meetingName)
        assertNotEquals("United States Grand Prix", match.meetingName)
    }

    @Test
    fun `unmatched circuit returns null and does not retain previous circuit`() {
        val meetings = listOf(
            Meeting(
                meetingKey = 1250L,
                meetingName = "Miami Grand Prix",
                officialName = "Formula 1 Miami Grand Prix 2026",
                location = "Miami",
                countryName = "United States",
                countryCode = "USA",
                countryFlag = null,
                circuitKey = 151,
                circuitShortName = "Miami",
                circuitImage = null,
                dateStart = "2026-05-01T10:00:00",
                dateEnd = "2026-05-03T18:00:00",
                gmtOffset = "-04:00",
                year = 2026,
                isCancelled = false
            )
        )

        val unknownCircuit = CircuitCardItem(
            circuitKey = 9999,
            meetingKey = 9999L,
            circuitName = "Circuito Fantasma",
            location = "Desconocida",
            country = "País Inexistente"
        )

        val match = if (unknownCircuit.meetingKey != null) {
            meetings.find { it.meetingKey == unknownCircuit.meetingKey }
        } else if (unknownCircuit.circuitKey > 0) {
            meetings.find { it.circuitKey == unknownCircuit.circuitKey }
        } else null

        assertNull("Circuito no encontrado debe ser null, no coincidir por fallback erróneo", match)
    }

    @Test
    fun `dynamic driver mapping groups drivers into constructors without hardcoded fallback`() {
        val driverStandings = listOf(
            JolpicaDriverStanding(
                position = "1",
                points = "60",
                wins = "3",
                driver = JolpicaDriver("norris", "4", "NOR", "Lando", "Norris"),
                constructors = listOf(JolpicaConstructor("mclaren", "McLaren"))
            ),
            JolpicaDriverStanding(
                position = "2",
                points = "50",
                wins = "1",
                driver = JolpicaDriver("leclerc", "16", "LEC", "Charles", "Leclerc"),
                constructors = listOf(JolpicaConstructor("ferrari", "Ferrari"))
            ),
            JolpicaDriverStanding(
                position = "3",
                points = "40",
                wins = "1",
                driver = JolpicaDriver("piastri", "81", "PIA", "Oscar", "Piastri"),
                constructors = listOf(JolpicaConstructor("mclaren", "McLaren"))
            ),
            JolpicaDriverStanding(
                position = "4",
                points = "30",
                wins = "1",
                driver = JolpicaDriver("hamilton", "44", "HAM", "Lewis", "Hamilton"),
                constructors = listOf(JolpicaConstructor("ferrari", "Ferrari"))
            )
        )

        val constructorDriversMap = mutableMapOf<String, MutableList<String>>()
        for (standing in driverStandings) {
            val driverName = standing.driver?.fullName ?: continue
            standing.constructors?.forEach { constructor ->
                val cName = constructor.name?.lowercase()?.trim() ?: ""
                val cId = constructor.constructorId?.lowercase()?.trim() ?: ""
                if (cName.isNotEmpty()) {
                    val list = constructorDriversMap.getOrPut(cName) { mutableListOf() }
                    if (!list.contains(driverName)) list.add(driverName)
                }
                if (cId.isNotEmpty()) {
                    val list = constructorDriversMap.getOrPut(cId) { mutableListOf() }
                    if (!list.contains(driverName)) list.add(driverName)
                }
            }
        }

        val mclarenDrivers = constructorDriversMap["mclaren"] ?: emptyList()
        val ferrariDrivers = constructorDriversMap["ferrari"] ?: emptyList()
        val redBullDrivers = constructorDriversMap["red bull"] ?: emptyList()

        assertEquals(listOf("Lando Norris", "Oscar Piastri"), mclarenDrivers)
        assertEquals(listOf("Charles Leclerc", "Lewis Hamilton"), ferrariDrivers)
        assertTrue("Equipo sin pilotos asignados debe ser lista vacía, no 'Piloto 1'", redBullDrivers.isEmpty())
    }

    @Test
    fun `matching latest Grand Prix resolves by round and season rather than fuzzy names`() {
        val bahrain = Meeting(
            meetingKey = 1001L,
            meetingName = "Bahrain Grand Prix",
            officialName = null,
            location = "Sakhir",
            countryName = "Bahrain",
            countryCode = "BHR",
            countryFlag = null,
            circuitKey = 63,
            circuitShortName = "Sakhir",
            circuitImage = null,
            dateStart = "2026-03-05T10:00:00",
            dateEnd = "2026-03-08T18:00:00",
            gmtOffset = "+03:00",
            year = 2026,
            isCancelled = false
        )

        val saudi = Meeting(
            meetingKey = 1002L,
            meetingName = "Saudi Arabian Grand Prix",
            officialName = null,
            location = "Jeddah",
            countryName = "Saudi Arabia",
            countryCode = "KSA",
            countryFlag = null,
            circuitKey = 149,
            circuitShortName = "Jeddah",
            circuitImage = null,
            dateStart = "2026-03-19T10:00:00",
            dateEnd = "2026-03-22T18:00:00",
            gmtOffset = "+03:00",
            year = 2026,
            isCancelled = false
        )

        val meetings = listOf(bahrain, saudi)
        val championshipMeetings = meetings.filter { !it.isPreSeasonTesting }.sortedBy { it.dateStart }

        val summary = RecentRaceSummary(
            grandPrixName = "Saudi Arabian Grand Prix",
            country = "Saudi Arabia",
            circuitName = "Jeddah Corniche Circuit",
            dateDisplay = "2026-03-22",
            winnerName = "Lando Norris",
            winnerTeam = "McLaren",
            podium = emptyList(),
            round = 2,
            season = 2026
        )

        // Relación por ronda oficial
        val matchByRound = championshipMeetings.getOrNull(summary.round - 1)
        val matchedMeeting = if (matchByRound != null && (matchByRound.year == summary.season || summary.season == 0)) {
            matchByRound
        } else {
            meetings.find { m ->
                (m.year == summary.season || summary.season == 0) &&
                        (m.dateEnd?.startsWith(summary.dateDisplay) == true || m.dateStart?.startsWith(summary.dateDisplay) == true)
            }
        }

        assertNotNull(matchedMeeting)
        assertEquals(1002L, matchedMeeting!!.meetingKey)
        assertEquals("Saudi Arabian Grand Prix", matchedMeeting.meetingName)
    }
}
