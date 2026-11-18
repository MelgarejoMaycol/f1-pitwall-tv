package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.network.OpenF1ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class F1Repository(private val api: OpenF1ApiClient) {

    private var cachedMeetings: List<Meeting> = emptyList()
    private var cachedDrivers: List<DriverCardItem> = emptyList()

    suspend fun getMeetings(year: Int = 2024): List<Meeting> = withContext(Dispatchers.IO) {
        if (cachedMeetings.isNotEmpty() && cachedMeetings.firstOrNull()?.year == year) {
            return@withContext cachedMeetings
        }
        val meetings = api.getMeetings(year)
        cachedMeetings = meetings
        meetings
    }

    suspend fun resolveHeroBannerData(year: Int = 2024): HeroBannerData? = withContext(Dispatchers.IO) {
        val meetings = getMeetings(year)
        if (meetings.isEmpty()) return@withContext null

        val now = Date()

        // 1. Check if any meeting has a live session right now (only if current date matches race weekend)
        val currentWeekendMeeting = meetings.firstOrNull { m ->
            val start = TimeUtils.parseUtc(m.dateStart)
            val end = TimeUtils.parseUtc(m.dateEnd)
            if (start != null && end != null) {
                val windowStart = start.time - 86_400_000L
                val windowEnd = end.time + 86_400_000L
                now.time in windowStart..windowEnd
            } else false
        }

        if (currentWeekendMeeting != null) {
            val sessions = api.getSessions(currentWeekendMeeting.meetingKey)
            val liveSession = sessions.find { TimeUtils.isCurrentlyLive(it.dateStart, it.dateEnd) }
            if (liveSession != null) {
                return@withContext HeroBannerData(
                    type = HeroStateType.LIVE,
                    grandPrixName = currentWeekendMeeting.meetingName ?: "Gran Premio en Vivo",
                    officialName = currentWeekendMeeting.officialName ?: (currentWeekendMeeting.meetingName ?: ""),
                    circuitName = currentWeekendMeeting.circuitShortName ?: "Circuito Oficial",
                    location = "${currentWeekendMeeting.location ?: ""}, ${currentWeekendMeeting.countryName ?: ""}",
                    countryName = currentWeekendMeeting.countryName ?: "",
                    dateDisplay = TimeUtils.formatColombiaDateRange(currentWeekendMeeting.dateStart, currentWeekendMeeting.dateEnd),
                    timingDisplay = "Sesión Activa: ${liveSession.sessionName ?: "En Pista"}",
                    countdownText = "EN DIRECTO",
                    flagUrl = currentWeekendMeeting.countryFlag,
                    circuitImageUrl = currentWeekendMeeting.circuitImage,
                    meetingKey = currentWeekendMeeting.meetingKey,
                    sessionKey = liveSession.sessionKey
                )
            }
        }

        // 2. Check for the next upcoming Grand Prix
        val upcomingMeeting = meetings.firstOrNull { meeting ->
            val endDate = TimeUtils.parseUtc(meeting.dateEnd)
            endDate != null && endDate.after(now)
        }

        if (upcomingMeeting != null) {
            val sessions = api.getSessions(upcomingMeeting.meetingKey)
            val nextSession = sessions.firstOrNull { s ->
                val sStart = TimeUtils.parseUtc(s.dateStart)
                sStart != null && sStart.after(now)
            } ?: sessions.firstOrNull()

            val countdown = TimeUtils.calculateCountdown(nextSession?.dateStart ?: upcomingMeeting.dateStart)
            val sessionName = nextSession?.sessionName ?: "Próxima Carrera"
            val colombiaTime = TimeUtils.formatToColombiaTime(nextSession?.dateStart ?: upcomingMeeting.dateStart)

            return@withContext HeroBannerData(
                type = HeroStateType.UPCOMING,
                grandPrixName = upcomingMeeting.meetingName ?: "Próximo Gran Premio",
                officialName = upcomingMeeting.officialName ?: "",
                circuitName = upcomingMeeting.circuitShortName ?: (upcomingMeeting.location ?: "Circuito"),
                location = "${upcomingMeeting.location ?: ""}, ${upcomingMeeting.countryName ?: ""}",
                countryName = upcomingMeeting.countryName ?: "",
                dateDisplay = TimeUtils.formatColombiaDateRange(upcomingMeeting.dateStart, upcomingMeeting.dateEnd),
                timingDisplay = "$sessionName • $colombiaTime (Hora Colombia)",
                countdownText = countdown,
                flagUrl = upcomingMeeting.countryFlag,
                circuitImageUrl = upcomingMeeting.circuitImage,
                meetingKey = upcomingMeeting.meetingKey,
                sessionKey = nextSession?.sessionKey
            )
        }

        // 3. Off-season or past: Show the last completed Grand Prix
        val lastMeeting = meetings.lastOrNull { it.meetingName?.contains("Grand Prix", true) == true } ?: meetings.last()
        val sessions = api.getSessions(lastMeeting.meetingKey)
        val raceSession = sessions.find { it.sessionType.equals("Race", true) } ?: sessions.lastOrNull()

        return@withContext HeroBannerData(
            type = HeroStateType.COMPLETED,
            grandPrixName = lastMeeting.meetingName ?: "Gran Premio",
            officialName = lastMeeting.officialName ?: "",
            circuitName = lastMeeting.circuitShortName ?: (lastMeeting.location ?: "Circuito"),
            location = "${lastMeeting.location ?: ""}, ${lastMeeting.countryName ?: ""}",
            countryName = lastMeeting.countryName ?: "",
            dateDisplay = TimeUtils.formatColombiaDateRange(lastMeeting.dateStart, lastMeeting.dateEnd),
            timingDisplay = "Carrera Finalizada • Resultados Oficiales Disponibles",
            countdownText = "TEMPORADA FINALIZADA",
            flagUrl = lastMeeting.countryFlag,
            circuitImageUrl = lastMeeting.circuitImage,
            meetingKey = lastMeeting.meetingKey,
            sessionKey = raceSession?.sessionKey
        )
    }

    suspend fun getDriversCarouselData(sessionKey: Long): List<DriverCardItem> = withContext(Dispatchers.IO) {
        if (cachedDrivers.isNotEmpty()) return@withContext cachedDrivers
        val apiDrivers = api.getDrivers(sessionKey)
        val items = apiDrivers.map { d ->
            DriverCardItem(
                driverNumber = d.driverNumber,
                acronym = d.acronym ?: "#${d.driverNumber}",
                fullName = d.fullName ?: d.broadcastName ?: "Piloto ${d.driverNumber}",
                teamName = d.teamName ?: "Escudería F1",
                teamColor = d.teamColour ?: "E10600",
                headshotUrl = d.headshotUrl
            )
        }
        cachedDrivers = items
        items
    }

    fun getCircularHubItems(): List<CircularHubItem> {
        return listOf(
            CircularHubItem("pilotos", "Pilotos", "🏎️"),
            CircularHubItem("constructores", "Escuderías", "🛠️"),
            CircularHubItem("calendario", "Calendario", "📅", "2024-2026"),
            CircularHubItem("resultados", "Resultados", "🏁"),
            CircularHubItem("mundial", "Mundial", "🏆"),
            CircularHubItem("estadisticas", "Telemetría", "📈"),
            CircularHubItem("circuitos", "Circuitos", "🗺️"),
            CircularHubItem("configuracion", "Ajustes", "⚙️")
        )
    }

    fun getConstructors(): List<ConstructorCardItem> {
        return listOf(
            ConstructorCardItem("McLaren", "F47600", listOf("Lando Norris", "Oscar Piastri")),
            ConstructorCardItem("Ferrari", "E80020", listOf("Charles Leclerc", "Carlos Sainz")),
            ConstructorCardItem("Red Bull Racing", "3671C6", listOf("Max Verstappen", "Sergio Pérez")),
            ConstructorCardItem("Mercedes-AMG", "27F4D2", listOf("Lewis Hamilton", "George Russell")),
            ConstructorCardItem("Aston Martin", "229971", listOf("Fernando Alonso", "Lance Stroll")),
            ConstructorCardItem("Alpine", "0093CC", listOf("Pierre Gasly", "Esteban Ocon")),
            ConstructorCardItem("Williams", "64C4FF", listOf("Alexander Albon", "Franco Colapinto")),
            ConstructorCardItem("Haas F1 Team", "B6BABD", listOf("Nico Hülkenberg", "Kevin Magnussen")),
            ConstructorCardItem("Racing Bulls", "6692FF", listOf("Yuki Tsunoda", "Liam Lawson")),
            ConstructorCardItem("Kick Sauber", "52E252", listOf("Valtteri Bottas", "Zhou Guanyu"))
        )
    }

    suspend fun getCircuits(year: Int = 2024): List<CircuitCardItem> = withContext(Dispatchers.IO) {
        val meetings = getMeetings(year)
        val seen = mutableSetOf<Int>()
        meetings.filter { it.circuitKey != null && it.circuitImage != null }
            .filter { seen.add(it.circuitKey!!) }
            .map { m ->
                CircuitCardItem(
                    circuitKey = m.circuitKey!!,
                    circuitName = m.circuitShortName ?: m.location ?: "Circuito",
                    location = m.location ?: "",
                    country = m.countryName ?: "",
                    circuitImageUrl = m.circuitImage
                )
            }
    }
}
