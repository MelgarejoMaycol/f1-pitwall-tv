package com.f1pitwall.tv.data.mapper

import com.f1pitwall.tv.model.*
import org.junit.Assert.*
import org.junit.Test

class LeaderboardMapperTest {

    @Test
    fun `empty positions returns empty list and does NOT fabricate positions`() {
        val drivers = listOf(
            Driver(1, "VER", "Max Verstappen", "VER", "Red Bull Racing", "3671C6", null),
            Driver(4, "NOR", "Lando Norris", "NOR", "McLaren", "FF8000", null)
        )

        // Empty positions returned by API (e.g. before session or no data)
        val items = LeaderboardMapper.buildLeaderboardItems(
            drivers = drivers,
            positions = emptyList(),
            intervals = emptyList(),
            laps = emptyList(),
            stints = emptyList()
        )

        // Must be completely empty, no positions invented with mapIndexed
        assertTrue("Leaderboard items must be empty when API returns no positions", items.isEmpty())
    }

    @Test
    fun `missing tyre data produces null compound and age without fake defaults`() {
        val drivers = listOf(
            Driver(1, "VER", "Max Verstappen", "VER", "Red Bull Racing", "3671C6", null)
        )
        val positions = listOf(
            Position("2026-11-20T14:10:00", 1, 1, 100L)
        )

        // No stints available
        val items = LeaderboardMapper.buildLeaderboardItems(
            drivers = drivers,
            positions = positions,
            intervals = emptyList(),
            laps = emptyList(),
            stints = emptyList()
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(1, item.driverNumber)
        assertEquals(1, item.position)
        assertNull("Tyre compound must be null when stint is missing, not hardcoded MEDIUM", item.tyreCompound)
        assertNull("Tyre age must be null when stint is missing, not hardcoded 1", item.tyreAge)
    }

    @Test
    fun `valid position interval and stint data are mapped correctly`() {
        val drivers = listOf(
            Driver(1, "VER", "Max Verstappen", "VER", "Red Bull Racing", "3671C6", null),
            Driver(4, "NOR", "Lando Norris", "NOR", "McLaren", "FF8000", null)
        )
        val positions = listOf(
            Position("2026-11-20T14:10:00", 4, 2, 100L),
            Position("2026-11-20T14:10:00", 1, 1, 100L)
        )
        val intervals = listOf(
            Interval("2026-11-20T14:10:00", 1, 0.0, 0.0),
            Interval("2026-11-20T14:10:00", 4, 1.250, 1.250)
        )
        val laps = listOf(
            Lap(1, 15, 84.520, 24.1, 28.3, 32.12, false),
            Lap(4, 15, 84.850, 24.2, 28.4, 32.25, false)
        )
        val stints = listOf(
            Stint(1, 1, "SOFT", 1, 15, 15),
            Stint(4, 1, "MEDIUM", 1, 15, 15)
        )

        val items = LeaderboardMapper.buildLeaderboardItems(
            drivers = drivers,
            positions = positions,
            intervals = intervals,
            laps = laps,
            stints = stints
        )

        assertEquals(2, items.size)
        // Ordered by position
        assertEquals(1, items[0].position)
        assertEquals("VER", items[0].acronym)
        assertEquals("LÍDER", items[0].gapToLeader)
        assertEquals("SOFT", items[0].tyreCompound)
        assertEquals(15, items[0].tyreAge)

        assertEquals(2, items[1].position)
        assertEquals("NOR", items[1].acronym)
        assertEquals("+1.25s", items[1].gapToLeader)
        assertEquals("MEDIUM", items[1].tyreCompound)
        assertEquals(15, items[1].tyreAge)
    }
}
