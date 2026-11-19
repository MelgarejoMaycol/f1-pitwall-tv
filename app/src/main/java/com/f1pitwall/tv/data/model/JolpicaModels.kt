package com.f1pitwall.tv.data.model

import com.google.gson.annotations.SerializedName

data class JolpicaResponse<T>(
    @SerializedName("MRData") val mrData: T
)

data class RaceTableContainer(
    @SerializedName("RaceTable") val raceTable: RaceTable?
)

data class RaceTable(
    @SerializedName("season") val season: String?,
    @SerializedName("round") val round: String?,
    @SerializedName("Races") val races: List<JolpicaRace>?
)

data class JolpicaRace(
    @SerializedName("season") val season: String,
    @SerializedName("round") val round: String,
    @SerializedName("raceName") val raceName: String,
    @SerializedName("Circuit") val circuit: JolpicaCircuit?,
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String?,
    @SerializedName("FirstPractice") val firstPractice: JolpicaSessionTime?,
    @SerializedName("SecondPractice") val secondPractice: JolpicaSessionTime?,
    @SerializedName("ThirdPractice") val thirdPractice: JolpicaSessionTime?,
    @SerializedName("Qualifying") val qualifying: JolpicaSessionTime?,
    @SerializedName("SprintQualifying") val sprintQualifying: JolpicaSessionTime?,
    @SerializedName("Sprint") val sprint: JolpicaSessionTime?,
    @SerializedName("Results") val results: List<JolpicaResult>?
)

data class JolpicaCircuit(
    @SerializedName("circuitId") val circuitId: String,
    @SerializedName("circuitName") val circuitName: String,
    @SerializedName("Location") val location: JolpicaLocation?
)

data class JolpicaLocation(
    @SerializedName("locality") val locality: String?,
    @SerializedName("country") val country: String?
)

data class JolpicaSessionTime(
    @SerializedName("date") val date: String?,
    @SerializedName("time") val time: String?
) {
    fun toIsoUtc(): String? {
        if (date.isNullOrEmpty()) return null
        return if (!time.isNullOrEmpty()) "${date}T${time}" else "${date}T00:00:00Z"
    }
}

data class JolpicaResult(
    @SerializedName("number") val number: String?,
    @SerializedName("position") val position: String?,
    @SerializedName("points") val points: String?,
    @SerializedName("Driver") val driver: JolpicaDriver?,
    @SerializedName("Constructor") val constructor: JolpicaConstructor?,
    @SerializedName("grid") val grid: String?,
    @SerializedName("laps") val laps: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("Time") val time: JolpicaTime?
)

data class JolpicaDriver(
    @SerializedName("driverId") val driverId: String?,
    @SerializedName("permanentNumber") val permanentNumber: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("givenName") val givenName: String?,
    @SerializedName("familyName") val familyName: String?
) {
    val fullName: String get() = "${givenName ?: ""} ${familyName ?: ""}".trim()
}

data class JolpicaConstructor(
    @SerializedName("constructorId") val constructorId: String?,
    @SerializedName("name") val name: String?
)

data class JolpicaTime(
    @SerializedName("millis") val millis: String?,
    @SerializedName("time") val time: String?
)

data class RecentRaceSummary(
    val grandPrixName: String,
    val country: String,
    val circuitName: String,
    val dateDisplay: String,
    val winnerName: String,
    val winnerTeam: String,
    val podium: List<PodiumDriver>,
    val round: Int,
    val season: Int,
    val circuitId: String? = null
)

data class PodiumDriver(
    val position: Int,
    val fullName: String,
    val teamName: String,
    val code: String
)

data class StandingsTableContainer(
    @SerializedName("StandingsTable") val standingsTable: StandingsTable?
)

data class StandingsTable(
    @SerializedName("season") val season: String?,
    @SerializedName("round") val round: String?,
    @SerializedName("StandingsLists") val standingsLists: List<StandingsList>?
)

data class StandingsList(
    @SerializedName("season") val season: String?,
    @SerializedName("round") val round: String?,
    @SerializedName("DriverStandings") val driverStandings: List<JolpicaDriverStanding>?,
    @SerializedName("ConstructorStandings") val constructorStandings: List<JolpicaConstructorStanding>?
)

data class JolpicaDriverStanding(
    @SerializedName("position") val position: String?,
    @SerializedName("positionText") val positionText: String? = null,
    @SerializedName("points") val points: String?,
    @SerializedName("wins") val wins: String?,
    @SerializedName("Driver") val driver: JolpicaDriver?,
    @SerializedName("Constructors") val constructors: List<JolpicaConstructor>?
)

data class JolpicaConstructorStanding(
    @SerializedName("position") val position: String?,
    @SerializedName("positionText") val positionText: String? = null,
    @SerializedName("points") val points: String?,
    @SerializedName("wins") val wins: String?,
    @SerializedName("Constructor") val constructor: JolpicaConstructor?
)
