package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.model.CarTelemetry
import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.network.JolpicaApiClient
import com.f1pitwall.tv.network.OpenF1ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class F1Repository(
    private val openF1Api: OpenF1ApiClient,
    private val jolpicaApi: JolpicaApiClient = JolpicaApiClient()
) {

    private var cachedMeetings: MutableMap<Int, List<Meeting>> = mutableMapOf()
    private var cachedDrivers: List<DriverCardItem> = emptyList()

    suspend fun getMeetings(year: Int = AppConfig.DEFAULT_SEASON): List<Meeting> = withContext(Dispatchers.IO) {
        if (cachedMeetings.containsKey(year) && cachedMeetings[year]!!.isNotEmpty()) {
            return@withContext cachedMeetings[year]!!
        }
        val meetings = openF1Api.getMeetings(year)
        cachedMeetings[year] = meetings
        meetings
    }

    /**
     * Selección inteligente del Gran Premio:
     * 1. Si hay una carrera/sesión en directo, esa tiene prioridad absoluta.
     * 2. Si no hay sesión en directo, se selecciona el PRÓXIMO evento (ej. GP de Las Vegas en 2026).
     * 3. Si la temporada ya terminó, se selecciona el evento más reciente finalizado.
     */
    suspend fun findSmartSelectedMeeting(year: Int = AppConfig.DEFAULT_SEASON): Meeting? = withContext(Dispatchers.IO) {
        val meetings = getMeetings(year)
        if (meetings.isEmpty()) return@withContext null

        val now = Date()

        // 1. Sesión en vivo
        val liveMeeting = meetings.firstOrNull { m ->
            val start = TimeUtils.parseUtc(m.dateStart)
            val end = TimeUtils.parseUtc(m.dateEnd)
            start != null && end != null && now.time in (start.time - 3600000L)..(end.time + 3600000L)
        }
        if (liveMeeting != null) return@withContext liveMeeting

        // 2. Próximo Gran Premio en el calendario (ej. Las Vegas GP en 2026)
        val upcomingMeeting = meetings.firstOrNull { m ->
            val end = TimeUtils.parseUtc(m.dateEnd)
            end != null && end.after(now) && m.meetingName?.contains("Grand Prix", true) == true
        } ?: meetings.firstOrNull { m ->
            val end = TimeUtils.parseUtc(m.dateEnd)
            end != null && end.after(now)
        }
        if (upcomingMeeting != null) return@withContext upcomingMeeting

        // 3. Temporada finalizada: último GP disputado
        return@withContext meetings.lastOrNull { m ->
            m.meetingName?.contains("Grand Prix", true) == true
        } ?: meetings.lastOrNull()
    }

    /**
     * Resuelve dinámicamente los datos para el Hero Banner de la pantalla de inicio.
     */
    suspend fun resolveHeroBannerData(year: Int = AppConfig.DEFAULT_SEASON): HeroBannerData? = withContext(Dispatchers.IO) {
        val meetings = getMeetings(year)
        if (meetings.isEmpty()) return@withContext null

        val now = Date()

        // 1. Verificar si hay sesión en vivo este fin de semana
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
            val sessions = openF1Api.getSessions(currentWeekendMeeting.meetingKey)
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
                    timingDisplay = "🔴 Sesión Activa: ${liveSession.sessionName ?: "En Pista"}",
                    countdownText = "EN DIRECTO",
                    flagUrl = currentWeekendMeeting.countryFlag,
                    circuitImageUrl = currentWeekendMeeting.circuitImage,
                    meetingKey = currentWeekendMeeting.meetingKey,
                    sessionKey = liveSession.sessionKey
                )
            }
        }

        // 2. Próximo Gran Premio (en 2026 es el Gran Premio de Las Vegas)
        val upcomingMeeting = meetings.firstOrNull { meeting ->
            val endDate = TimeUtils.parseUtc(meeting.dateEnd)
            endDate != null && endDate.after(now) && meeting.meetingName?.contains("Grand Prix", true) == true
        } ?: meetings.firstOrNull { meeting ->
            val endDate = TimeUtils.parseUtc(meeting.dateEnd)
            endDate != null && endDate.after(now)
        }

        if (upcomingMeeting != null) {
            val sessions = openF1Api.getSessions(upcomingMeeting.meetingKey)
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

        // 3. Si la temporada terminó, mostrar el último evento
        val lastMeeting = meetings.lastOrNull { it.meetingName?.contains("Grand Prix", true) == true } ?: meetings.last()
        val sessions = openF1Api.getSessions(lastMeeting.meetingKey)
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

    /**
     * Obtiene el resumen del Gran Premio más reciente que haya finalizado con sus resultados oficiales (Jolpica F1).
     * Por ejemplo, en 2026: São Paulo Grand Prix (Brasil) - Ganador: Lando Norris (McLaren).
     */
    suspend fun getRecentRaceSummary(year: Int = AppConfig.DEFAULT_SEASON): RecentRaceSummary? = withContext(Dispatchers.IO) {
        try {
            val latest = jolpicaApi.getLatestRaceResults(year) ?: return@withContext null
            val race = latest.first
            val results = latest.second

            val winner = results.firstOrNull()
            val podium = results.take(3).map { r ->
                PodiumDriver(
                    position = r.position?.toIntOrNull() ?: 1,
                    fullName = r.driver?.fullName ?: "Piloto F1",
                    teamName = r.constructor?.name ?: "Escudería",
                    code = r.driver?.code ?: "F1"
                )
            }

            RecentRaceSummary(
                grandPrixName = race.raceName,
                country = race.circuit?.location?.country ?: "País",
                circuitName = race.circuit?.circuitName ?: "Circuito",
                dateDisplay = race.date,
                winnerName = winner?.driver?.fullName ?: "Por confirmar",
                winnerTeam = winner?.constructor?.name ?: "",
                podium = podium,
                round = race.round.toIntOrNull() ?: 1,
                season = race.season.toIntOrNull() ?: year
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Consulta la telemetría en tiempo real o histórica para un piloto específico.
     */
    suspend fun getTelemetryForDriver(sessionKey: Long, driverNumber: Int): CarTelemetry? = withContext(Dispatchers.IO) {
        openF1Api.getLatestTelemetry(sessionKey, driverNumber)
    }

    suspend fun getDriversCarouselData(sessionKey: Long): List<DriverCardItem> = withContext(Dispatchers.IO) {
        if (cachedDrivers.isNotEmpty()) return@withContext cachedDrivers
        val apiDrivers = openF1Api.getDrivers(sessionKey)
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
            CircularHubItem("calendario", "Calendario", "📅", "2026"),
            CircularHubItem("resultados", "Resultados", "🏁"),
            CircularHubItem("mundial", "Mundial", "🏆"),
            CircularHubItem("estadisticas", "Telemetría", "📈"),
            CircularHubItem("circuitos", "Circuitos", "🗺️"),
            CircularHubItem("configuracion", "Fuentes de Video", "📺")
        )
    }

    fun getConstructors(): List<ConstructorCardItem> {
        return listOf(
            ConstructorCardItem("McLaren", "F47600", listOf("Lando Norris", "Oscar Piastri")),
            ConstructorCardItem("Ferrari", "E80020", listOf("Charles Leclerc", "Lewis Hamilton")),
            ConstructorCardItem("Red Bull Racing", "3671C6", listOf("Max Verstappen", "Isack Hadjar")),
            ConstructorCardItem("Mercedes-AMG", "27F4D2", listOf("George Russell", "Kimi Antonelli")),
            ConstructorCardItem("Aston Martin", "229971", listOf("Fernando Alonso", "Lance Stroll")),
            ConstructorCardItem("Alpine", "0093CC", listOf("Pierre Gasly", "Jack Doohan")),
            ConstructorCardItem("Williams", "64C4FF", listOf("Alexander Albon", "Carlos Sainz")),
            ConstructorCardItem("Haas F1 Team", "B6BABD", listOf("Esteban Ocon", "Oliver Bearman")),
            ConstructorCardItem("Racing Bulls", "6692FF", listOf("Yuki Tsunoda", "Liam Lawson")),
            ConstructorCardItem("Audi F1 Team", "52E252", listOf("Nico Hülkenberg", "Gabriel Bortoleto")),
            ConstructorCardItem("Cadillac F1", "C5A059", listOf("Valtteri Bottas", "Sergio Pérez"))
        )
    }

    suspend fun getCircuits(year: Int = AppConfig.DEFAULT_SEASON): List<CircuitCardItem> = withContext(Dispatchers.IO) {
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
