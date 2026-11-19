package com.f1pitwall.tv.data.mapper

import com.f1pitwall.tv.data.model.JolpicaResult
import com.f1pitwall.tv.model.DriverLeaderboardItem

object OfficialResultsMapper {

    fun mapJolpicaResultsToLeaderboardItems(
        results: List<JolpicaResult>,
        teamColorResolver: (String) -> String = { "E10600" }
    ): List<DriverLeaderboardItem> {
        if (results.isEmpty()) return emptyList()

        return results.mapIndexed { index, res ->
            val num = res.number?.toIntOrNull()
                ?: res.driver?.permanentNumber?.toIntOrNull()
                ?: 0
            val pos = res.position?.toIntOrNull() ?: (index + 1)
            val acronym = res.driver?.code ?: (if (num > 0) "#$num" else "#--")
            val fullName = res.driver?.fullName?.ifBlank { "Piloto F1" } ?: "Piloto F1"
            val teamName = res.constructor?.name ?: "Escudería F1"
            val teamColor = teamColorResolver(teamName)

            // Gap o estado de finalización
            val gap = when {
                pos == 1 -> res.time?.time ?: "1º (${res.points ?: "25"} pts)"
                !res.time?.time.isNullOrEmpty() -> res.time?.time ?: "--"
                !res.status.isNullOrEmpty() -> formatStatus(res.status)
                else -> "--"
            }

            // Intervalo / Puntos oficiales
            val pts = res.points ?: "0"
            val interval = if (pts != "0") "+$pts pts" else (res.status ?: "")

            // Vueltas completadas
            val laps = res.laps?.let { "$it Vueltas" } ?: "--"

            DriverLeaderboardItem(
                driverNumber = num,
                position = pos,
                acronym = acronym,
                fullName = fullName,
                teamName = teamName,
                teamColor = teamColor,
                gapToLeader = gap,
                interval = interval,
                lastLap = laps,
                s1 = if (!res.grid.isNullOrEmpty()) "Grid: P${res.grid}" else "--",
                s2 = "--",
                s3 = "--",
                tyreCompound = null,
                tyreAge = null
            )
        }
    }

    fun formatStatus(status: String): String {
        return when {
            status.equals("Finished", ignoreCase = true) -> "Finalizado"
            status.startsWith("+") -> status
            status.contains("Accident", ignoreCase = true) || status.contains("Collision", ignoreCase = true) -> "DNF (Accidente)"
            status.contains("Engine", ignoreCase = true) -> "DNF (Motor)"
            status.contains("Disqualified", ignoreCase = true) -> "Descalificado"
            status.contains("Did not start", ignoreCase = true) -> "No inició"
            status.contains("Retired", ignoreCase = true) -> "DNF (Retirado)"
            else -> "DNF ($status)"
        }
    }
}
