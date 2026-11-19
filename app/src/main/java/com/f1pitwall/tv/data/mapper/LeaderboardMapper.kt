package com.f1pitwall.tv.data.mapper

import com.f1pitwall.tv.model.*
import java.util.Locale

object LeaderboardMapper {

    /**
     * Transforma los datos crudos de OpenF1 a elementos listos para mostrar en el Leaderboard.
     * Si no hay posiciones reales proporcionadas por la API, devuelve una lista vacía
     * para evitar crear clasificaciones o puestos ficticios.
     */
    fun buildLeaderboardItems(
        drivers: List<Driver>,
        positions: List<Position>,
        intervals: List<Interval>,
        laps: List<Lap>,
        stints: List<Stint>
    ): List<DriverLeaderboardItem> {
        // Validación estricta: No inventar posiciones si la API no reporta posiciones reales
        if (positions.isEmpty()) {
            return emptyList()
        }

        val driverMap = drivers.associateBy { it.driverNumber }
        val intervalMap = intervals.associateBy { it.driverNumber }
        val lapMap = laps.associateBy { it.driverNumber }
        val stintMap = stints.associateBy { it.driverNumber }

        val sortedPositions = positions.sortedBy { it.position }

        return sortedPositions.map { pos ->
            val driver = driverMap[pos.driverNumber]
            val interval = intervalMap[pos.driverNumber]
            val lap = lapMap[pos.driverNumber]
            val stint = stintMap[pos.driverNumber]

            val gapStr = if (pos.position == 1) "LÍDER" else {
                interval?.gapToLeader?.toString()?.let { "+${it}s" } ?: "+--"
            }
            val intervalStr = interval?.interval?.toString()?.let { "+${it}s" } ?: ""

            val lastLapStr = lap?.lapDuration?.let { formatLapDuration(it) } ?: "--:--.---"

            DriverLeaderboardItem(
                driverNumber = pos.driverNumber,
                position = pos.position,
                acronym = driver?.acronym ?: "#${pos.driverNumber}",
                fullName = driver?.fullName ?: driver?.broadcastName ?: "Piloto ${pos.driverNumber}",
                teamName = driver?.teamName ?: "",
                teamColor = driver?.teamColour ?: "E10600",
                gapToLeader = gapStr,
                interval = intervalStr,
                lastLap = lastLapStr,
                s1 = lap?.s1?.toString() ?: "-",
                s2 = lap?.s2?.toString() ?: "-",
                s3 = lap?.s3?.toString() ?: "-",
                tyreCompound = stint?.compound,
                tyreAge = stint?.tyreAgeAtStart
            )
        }
    }

    fun formatLapDuration(duration: Double): String {
        val minutes = (duration / 60).toInt()
        val seconds = duration % 60
        return String.format(Locale.US, "%d:%06.3f", minutes, seconds)
    }
}
