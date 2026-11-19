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

    /**
     * Obtiene el último audio de radio de equipo disponible para un piloto.
     */
    suspend fun getDriverTeamRadio(sessionKey: Long, driverNumber: Int): com.f1pitwall.tv.model.TeamRadio? = withContext(Dispatchers.IO) {
        try {
            val radios = openF1Api.getTeamRadiosForDriver(sessionKey, driverNumber)
            radios.firstOrNull { !it.recordingUrl.isNullOrEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mundial de Pilotos: Ordenado estrictamente de mayor a menor puntuación real (Jolpica F1 API 2026).
     */
    suspend fun getDriverStandings(year: Int = AppConfig.DEFAULT_SEASON): List<DriverCardItem> = withContext(Dispatchers.IO) {
        try {
            val apiStandings = jolpicaApi.getDriverStandings(year)
            if (apiStandings.isEmpty()) {
                return@withContext cachedDrivers
            }

            val leaderPoints = apiStandings.firstOrNull()?.points?.toDoubleOrNull() ?: 0.0

            val items = apiStandings.mapIndexed { index, item ->
                val pts = item.points ?: "0"
                val ptsDouble = pts.toDoubleOrNull() ?: 0.0
                val gap = if (index == 0) "LÍDER" else {
                    val diff = leaderPoints - ptsDouble
                    if (diff % 1.0 == 0.0) "-${diff.toInt()} pts" else "-%.1f pts".format(diff)
                }

                val driverObj = item.driver
                val constructorObj = item.constructors?.firstOrNull()
                val teamName = constructorObj?.name ?: "Escudería F1"
                val teamColor = getTeamColor(teamName)
                val num = driverObj?.permanentNumber?.toIntOrNull() ?: (index + 1)

                val headshot = "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/${driverObj?.familyName?.firstOrNull() ?: 'A'}/${driverObj?.code ?: "F1"}/${driverObj?.familyName?.lowercase() ?: ""}.png"

                DriverCardItem(
                    driverNumber = num,
                    acronym = driverObj?.code ?: "#$num",
                    fullName = driverObj?.fullName ?: "Piloto $num",
                    teamName = teamName,
                    teamColor = teamColor,
                    headshotUrl = headshot,
                    position = item.position?.toIntOrNull() ?: (index + 1),
                    points = pts,
                    gapToLeader = gap,
                    wins = item.wins?.toIntOrNull() ?: 0
                )
            }
            cachedDrivers = items
            items
        } catch (e: Exception) {
            e.printStackTrace()
            cachedDrivers
        }
    }

    suspend fun getDriversCarouselData(sessionKey: Long): List<DriverCardItem> = withContext(Dispatchers.IO) {
        val standings = getDriverStandings(AppConfig.DEFAULT_SEASON)
        if (standings.isNotEmpty()) {
            return@withContext standings
        }
        if (cachedDrivers.isNotEmpty()) return@withContext cachedDrivers
        val apiDrivers = openF1Api.getDrivers(sessionKey)
        val items = apiDrivers.map { d ->
            DriverCardItem(
                driverNumber = d.driverNumber,
                acronym = d.acronym ?: "#${d.driverNumber}",
                fullName = d.fullName ?: d.broadcastName ?: "Piloto ${d.driverNumber}",
                teamName = d.teamName ?: "Escudería F1",
                teamColor = d.teamColour ?: getTeamColor(d.teamName ?: ""),
                headshotUrl = d.headshotUrl
            )
        }
        cachedDrivers = items
        items
    }

    /**
     * Mundial de Constructores: Ordenado estrictamente de mayor a menor puntuación real (Jolpica F1 API 2026).
     */
    suspend fun getConstructorStandings(year: Int = AppConfig.DEFAULT_SEASON): List<ConstructorCardItem> = withContext(Dispatchers.IO) {
        try {
            val apiStandings = jolpicaApi.getConstructorStandings(year)
            if (apiStandings.isEmpty()) {
                return@withContext getFallbackConstructors()
            }

            val leaderPoints = apiStandings.firstOrNull()?.points?.toDoubleOrNull() ?: 0.0

            apiStandings.mapIndexed { index, item ->
                val pts = item.points ?: "0"
                val ptsDouble = pts.toDoubleOrNull() ?: 0.0
                val gap = if (index == 0) "LÍDER" else {
                    val diff = leaderPoints - ptsDouble
                    if (diff % 1.0 == 0.0) "-${diff.toInt()} pts" else "-%.1f pts".format(diff)
                }
                val teamName = item.constructor?.name ?: "Escudería"
                val teamColor = getTeamColor(teamName)

                ConstructorCardItem(
                    teamName = teamName,
                    teamColor = teamColor,
                    drivers = getTeamDrivers(teamName),
                    position = item.position?.toIntOrNull() ?: (index + 1),
                    points = pts,
                    wins = item.wins?.toIntOrNull() ?: 0,
                    gapToLeader = gap
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackConstructors()
        }
    }

    fun getCircularHubItems(): List<CircularHubItem> {
        return listOf(
            CircularHubItem(
                id = "mundial",
                title = "Mundial 2026",
                subtitle = "Pilotos & Constructores",
                iconEmoji = "🏆",
                badgeText = "OFICIAL",
                isAvailable = true
            ),
            CircularHubItem(
                id = "muro_pits",
                title = "Muro de Pits",
                subtitle = "Video & Telemetría",
                iconEmoji = "🏎️",
                badgeText = "ACTIVO",
                isAvailable = true
            ),
            CircularHubItem(
                id = "calendario",
                title = "Calendario 2026",
                subtitle = "24 Grandes Premios",
                iconEmoji = "📅",
                badgeText = "24 RONDAS",
                isAvailable = true
            ),
            CircularHubItem(
                id = "resultados",
                title = "GP São Paulo",
                subtitle = "Último Podio Oficial",
                iconEmoji = "🏁",
                badgeText = "FINALIZADA",
                isAvailable = true
            ),
            CircularHubItem(
                id = "configuracion",
                title = "Fuentes de Video",
                subtitle = "Control vía Git",
                iconEmoji = "📺",
                badgeText = "CONFIG",
                isAvailable = true
            )
        )
    }

    fun getConstructors(): List<ConstructorCardItem> {
        return getFallbackConstructors()
    }

    private fun getFallbackConstructors(): List<ConstructorCardItem> {
        return listOf(
            ConstructorCardItem("Mercedes", "27F4D2", listOf("Andrea Kimi Antonelli", "George Russell"), position = 1, points = "652", wins = 12, gapToLeader = "LÍDER"),
            ConstructorCardItem("Ferrari", "E80020", listOf("Lewis Hamilton", "Charles Leclerc"), position = 2, points = "510", wins = 2, gapToLeader = "-142 pts"),
            ConstructorCardItem("Red Bull Racing", "3671C6", listOf("Max Verstappen", "Isack Hadjar"), position = 3, points = "415", wins = 3, gapToLeader = "-237 pts"),
            ConstructorCardItem("McLaren", "FF8000", listOf("Lando Norris", "Oscar Piastri"), position = 4, points = "384", wins = 3, gapToLeader = "-268 pts"),
            ConstructorCardItem("Aston Martin", "229971", listOf("Fernando Alonso", "Lance Stroll"), position = 5, points = "86", wins = 0, gapToLeader = "-566 pts"),
            ConstructorCardItem("Alpine", "0093CC", listOf("Pierre Gasly", "Jack Doohan"), position = 6, points = "54", wins = 0, gapToLeader = "-598 pts"),
            ConstructorCardItem("Williams", "64C4FF", listOf("Alexander Albon", "Carlos Sainz"), position = 7, points = "48", wins = 0, gapToLeader = "-604 pts"),
            ConstructorCardItem("Racing Bulls", "6692FF", listOf("Yuki Tsunoda", "Liam Lawson"), position = 8, points = "32", wins = 0, gapToLeader = "-620 pts"),
            ConstructorCardItem("Haas F1 Team", "B6BABD", listOf("Esteban Ocon", "Oliver Bearman"), position = 9, points = "26", wins = 0, gapToLeader = "-626 pts"),
            ConstructorCardItem("Audi F1 Team", "52E252", listOf("Nico Hülkenberg", "Gabriel Bortoleto"), position = 10, points = "12", wins = 0, gapToLeader = "-640 pts"),
            ConstructorCardItem("Cadillac F1", "C5A059", listOf("Valtteri Bottas", "Sergio Pérez"), position = 11, points = "4", wins = 0, gapToLeader = "-648 pts")
        )
    }

    private fun getTeamDrivers(team: String): List<String> {
        val lower = team.lowercase()
        return when {
            lower.contains("mercedes") -> listOf("Andrea Kimi Antonelli", "George Russell")
            lower.contains("ferrari") -> listOf("Lewis Hamilton", "Charles Leclerc")
            lower.contains("red bull") -> listOf("Max Verstappen", "Isack Hadjar")
            lower.contains("mclaren") -> listOf("Lando Norris", "Oscar Piastri")
            lower.contains("aston") -> listOf("Fernando Alonso", "Lance Stroll")
            lower.contains("alpine") -> listOf("Pierre Gasly", "Jack Doohan")
            lower.contains("williams") -> listOf("Alexander Albon", "Carlos Sainz")
            lower.contains("haas") -> listOf("Esteban Ocon", "Oliver Bearman")
            lower.contains("bulls") || lower.contains("rb") -> listOf("Yuki Tsunoda", "Liam Lawson")
            lower.contains("audi") || lower.contains("sauber") -> listOf("Nico Hülkenberg", "Gabriel Bortoleto")
            lower.contains("cadillac") -> listOf("Valtteri Bottas", "Sergio Pérez")
            else -> listOf("Piloto 1", "Piloto 2")
        }
    }

    private fun getTeamColor(team: String): String {
        val lower = team.lowercase()
        return when {
            lower.contains("mercedes") -> "27F4D2"
            lower.contains("ferrari") -> "E80020"
            lower.contains("red bull") -> "3671C6"
            lower.contains("mclaren") -> "FF8000"
            lower.contains("aston") -> "229971"
            lower.contains("alpine") -> "0093CC"
            lower.contains("williams") -> "64C4FF"
            lower.contains("haas") -> "B6BABD"
            lower.contains("bulls") || lower.contains("rb") -> "6692FF"
            lower.contains("audi") || lower.contains("sauber") -> "52E252"
            lower.contains("cadillac") -> "C5A059"
            else -> "E10600"
        }
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
