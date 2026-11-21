package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.JolpicaApiClient
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.network.OpenF1AccessStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class F1Repository(
    private val openF1Api: OpenF1ApiClient,
    private val jolpicaApi: JolpicaApiClient = JolpicaApiClient()
) {

    private var cachedMeetings: MutableMap<Int, List<Meeting>> = mutableMapOf()
    private var cachedDrivers: List<DriverCardItem> = emptyList()
    private var cachedJolpicaRacesByYear: MutableMap<Int, List<JolpicaRace>> = mutableMapOf()
    private var cachedJolpicaMeetings: MutableMap<Long, JolpicaRace> = mutableMapOf()

    suspend fun getMeetings(year: Int = AppConfig.DEFAULT_SEASON): List<Meeting> = withContext(Dispatchers.IO) {
        if (cachedMeetings.containsKey(year) && cachedMeetings[year]!!.isNotEmpty()) {
            return@withContext cachedMeetings[year]!!
        }
        var meetings = openF1Api.getMeetings(year)
        if (meetings.isEmpty()) {
            val schedule = jolpicaApi.getSchedule(year)
            if (schedule.isNotEmpty()) {
                cachedJolpicaRacesByYear[year] = schedule
                meetings = schedule.mapIndexed { index, race ->
                    val meeting = mapJolpicaRaceToMeeting(race, year, index + 1)
                    cachedJolpicaMeetings[meeting.meetingKey] = race
                    meeting
                }
            }
        }
        if (meetings.isNotEmpty()) {
            cachedMeetings[year] = meetings
        }
        meetings
    }

    suspend fun getSessions(meetingKey: Long, year: Int = AppConfig.DEFAULT_SEASON): List<Session> = withContext(Dispatchers.IO) {
        val openF1Sessions = openF1Api.getSessions(meetingKey)
        if (openF1Sessions.isNotEmpty()) return@withContext openF1Sessions

        val race = cachedJolpicaMeetings[meetingKey] ?: run {
            getMeetings(year)
            cachedJolpicaMeetings[meetingKey]
        } ?: cachedJolpicaRacesByYear[year]?.find { r ->
            (year * 1000L + (r.round.toLongOrNull() ?: 1L)) == meetingKey
        }

        if (race != null) {
            return@withContext mapJolpicaRaceToSessions(race, meetingKey, year)
        }
        emptyList()
    }

    /**
     * Returns the session that should drive the Pit Wall:
     * active now -> nearest future session -> most recently completed session.
     * It queries the full season, not only the last session of the latest meeting.
     */
    suspend fun getCurrentOrNextSession(year: Int = AppConfig.DEFAULT_SEASON): Session? = withContext(Dispatchers.IO) {
        val now = Date()

        fun chooseSession(sessions: List<Session>): Session? {
            val timed = sessions.filterNot { it.isCancelled == true }.mapNotNull { session ->
                val start = TimeUtils.parseUtc(session.dateStart)
                val end = TimeUtils.parseUtc(session.dateEnd)
                if (start != null && end != null) Triple(session, start, end) else null
            }.sortedBy { it.second.time }

            return timed.firstOrNull { (_, start, end) ->
                !now.before(start) && !now.after(end)
            }?.first
                ?: timed.firstOrNull { (_, start, _) -> start.after(now) }?.first
                ?: timed.filter { (_, _, end) -> !end.after(now) }
                    .maxByOrNull { it.third.time }?.first
        }

        var seasonSessions = openF1Api.getSessionsForYear(year)
        if (seasonSessions.isEmpty()) {
            val meetings = getMeetings(year)
            seasonSessions = meetings.flatMap { m -> getSessions(m.meetingKey, year) }
        }

        val selectedSeasonSession = chooseSession(seasonSessions)
        val selectedStart = selectedSeasonSession?.let { TimeUtils.parseUtc(it.dateStart) }
        val selectedIsCurrentOrUpcoming = selectedSeasonSession != null &&
            (TimeUtils.isCurrentlyLive(selectedSeasonSession.dateStart, selectedSeasonSession.dateEnd) ||
                (selectedStart != null && selectedStart.after(now)))

        if (selectedIsCurrentOrUpcoming) return@withContext selectedSeasonSession

        // If the season feed has only historical rows, merge the next meeting's sessions.
        val selectedMeeting = findSmartSelectedMeeting(year)
        val meetingSessions = selectedMeeting?.let { getSessions(it.meetingKey, year) }.orEmpty()
        chooseSession(seasonSessions + meetingSessions) ?: selectedSeasonSession
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
            val sessions = getSessions(currentWeekendMeeting.meetingKey, year)
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
            val sessions = getSessions(upcomingMeeting.meetingKey, year)
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
        val sessions = getSessions(lastMeeting.meetingKey, year)
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

    fun getOpenF1AccessStatus(): OpenF1AccessStatus = openF1Api.getAccessStatus()

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
            var radios = openF1Api.getTeamRadiosForDriver(sessionKey, driverNumber)
            if (radios.isEmpty()) {
                radios = openF1Api.getTeamRadiosForDriverLatest(driverNumber)
            }
            if (radios.isEmpty() && driverNumber == 1) {
                radios = openF1Api.getTeamRadiosForDriverLatest(3)
            }
            radios.firstOrNull { !it.recordingUrl.isNullOrEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDriverTeamRadios(sessionKey: Long, driverNumber: Int): List<com.f1pitwall.tv.model.TeamRadio> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getTeamRadiosForDriver(sessionKey, driverNumber)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSessionTeamRadios(sessionKey: Long): List<com.f1pitwall.tv.model.TeamRadio> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getTeamRadios(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDriverTeamRadiosLatest(driverNumber: Int): List<com.f1pitwall.tv.model.TeamRadio> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getTeamRadiosForDriverLatest(driverNumber)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLatestLapForDriver(sessionKey: Long, driverNumber: Int): com.f1pitwall.tv.model.Lap? = withContext(Dispatchers.IO) {
        try {
            val laps = openF1Api.getLapsForDriver(sessionKey, driverNumber)
            laps.firstOrNull { it.s1 != null || it.s2 != null || it.s3 != null || it.lapDuration != null }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDrivers(sessionKey: Long): List<Driver> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getDrivers(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCarLocations(sessionKey: Long): List<CarLocation> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getCarLocations(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrackOutline(sessionKey: Long): List<CarLocation> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getTrackOutline(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPitStops(sessionKey: Long): List<PitStop> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getPitStops(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRaceControlMessages(sessionKey: Long): List<RaceControl> = withContext(Dispatchers.IO) {
        try {
            openF1Api.getRaceControlMessages(sessionKey)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLatestWeather(sessionKey: Long): Weather? = withContext(Dispatchers.IO) {
        try {
            openF1Api.getLatestWeather(sessionKey)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTyreStrategies(sessionKey: Long): List<DriverTyreStrategyItem> = withContext(Dispatchers.IO) {
        try {
            val drivers = openF1Api.getDrivers(sessionKey)
            val stints = openF1Api.getAllStintsForSession(sessionKey)
            val pitStops = openF1Api.getPitStops(sessionKey)
            val positions = openF1Api.getLatestPositions(sessionKey).associateBy { it.driverNumber }

            val stintsByDriver = stints.groupBy { it.driverNumber }
            val pitsByDriver = pitStops.groupBy { it.driverNumber }

            drivers.map { d ->
                val dStints = stintsByDriver[d.driverNumber]?.sortedBy { it.stintNumber } ?: emptyList()
                val currentStint = dStints.lastOrNull()
                val dPits = pitsByDriver[d.driverNumber] ?: emptyList()
                val lastPit = dPits.lastOrNull()
                val pos = positions[d.driverNumber]?.position ?: 99

                val compound = currentStint?.compound ?: "MEDIUM"
                val tyreAge = currentStint?.tyreAgeAtStart ?: 0

                val historyStr = if (dStints.isNotEmpty()) {
                    dStints.joinToString(" ➔ ") { s ->
                        val compInitial = s.compound?.take(1)?.uppercase() ?: "?"
                        "$compInitial (${s.lapEnd?.let { end -> (end - (s.lapStart ?: 1) + 1) } ?: s.tyreAgeAtStart ?: "?"}v)"
                    }
                } else "Sin paradas registradas"

                val pitDurationStr = lastPit?.pitDuration?.let { String.format(Locale.US, "%.1fs", it) }
                    ?: lastPit?.stopDuration?.let { String.format(Locale.US, "%.1fs", it) }
                    ?: if (dPits.isNotEmpty()) "Completada" else "--"

                DriverTyreStrategyItem(
                    driverNumber = d.driverNumber,
                    position = pos,
                    acronym = d.acronym ?: "#${d.driverNumber}",
                    fullName = d.fullName ?: "Piloto ${d.driverNumber}",
                    teamName = d.teamName ?: "",
                    teamColor = d.teamColour ?: getTeamColor(d.teamName ?: ""),
                    currentCompound = compound,
                    tyreAge = tyreAge,
                    totalStints = dStints.size,
                    pitStopsCount = dPits.size,
                    lastPitStopDuration = pitDurationStr,
                    stintsHistory = historyStr
                )
            }.sortedBy { it.position }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

                val headshot = getOfficialDriverHeadshot(driverObj?.code, driverObj?.driverId, driverObj?.familyName)

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
            val headshot = d.headshotUrl ?: getOfficialDriverHeadshot(d.acronym, null, d.broadcastName)
            DriverCardItem(
                driverNumber = d.driverNumber,
                acronym = d.acronym ?: "#${d.driverNumber}",
                fullName = d.fullName ?: d.broadcastName ?: "Piloto ${d.driverNumber}",
                teamName = d.teamName ?: "Escudería F1",
                teamColor = d.teamColour ?: getTeamColor(d.teamName ?: ""),
                headshotUrl = headshot
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
                val logoRes = getConstructorLogoRes(teamName)

                ConstructorCardItem(
                    teamName = teamName,
                    teamColor = teamColor,
                    drivers = getTeamDrivers(teamName),
                    position = item.position?.toIntOrNull() ?: (index + 1),
                    points = pts,
                    wins = item.wins?.toIntOrNull() ?: 0,
                    gapToLeader = gap,
                    logoResId = logoRes
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
            ConstructorCardItem("Mercedes", "27F4D2", listOf("Andrea Kimi Antonelli", "George Russell"), position = 1, points = "652", wins = 12, gapToLeader = "LÍDER", logoResId = com.f1pitwall.tv.R.drawable.ic_team_mercedes),
            ConstructorCardItem("Ferrari", "E80020", listOf("Lewis Hamilton", "Charles Leclerc"), position = 2, points = "510", wins = 2, gapToLeader = "-142 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_ferrari),
            ConstructorCardItem("Red Bull Racing", "3671C6", listOf("Max Verstappen", "Isack Hadjar"), position = 3, points = "415", wins = 3, gapToLeader = "-237 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_redbull),
            ConstructorCardItem("McLaren", "FF8000", listOf("Lando Norris", "Oscar Piastri"), position = 4, points = "384", wins = 3, gapToLeader = "-268 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_mclaren),
            ConstructorCardItem("Aston Martin", "229971", listOf("Fernando Alonso", "Lance Stroll"), position = 5, points = "86", wins = 0, gapToLeader = "-566 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_astonmartin),
            ConstructorCardItem("Alpine", "0093CC", listOf("Pierre Gasly", "Jack Doohan"), position = 6, points = "54", wins = 0, gapToLeader = "-598 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_alpine),
            ConstructorCardItem("Williams", "64C4FF", listOf("Alexander Albon", "Carlos Sainz"), position = 7, points = "48", wins = 0, gapToLeader = "-604 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_williams),
            ConstructorCardItem("Racing Bulls", "6692FF", listOf("Yuki Tsunoda", "Liam Lawson"), position = 8, points = "32", wins = 0, gapToLeader = "-620 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_rb),
            ConstructorCardItem("Haas F1 Team", "B6BABD", listOf("Esteban Ocon", "Oliver Bearman"), position = 9, points = "26", wins = 0, gapToLeader = "-626 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_haas),
            ConstructorCardItem("Audi F1 Team", "52E252", listOf("Nico Hülkenberg", "Gabriel Bortoleto"), position = 10, points = "12", wins = 0, gapToLeader = "-640 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_audi),
            ConstructorCardItem("Cadillac F1", "C5A059", listOf("Valtteri Bottas", "Sergio Pérez"), position = 11, points = "4", wins = 0, gapToLeader = "-648 pts", logoResId = com.f1pitwall.tv.R.drawable.ic_team_cadillac)
        )
    }

    fun getConstructorLogoRes(teamName: String): Int {
        val lower = teamName.lowercase()
        return when {
            lower.contains("mercedes") -> com.f1pitwall.tv.R.drawable.ic_team_mercedes
            lower.contains("ferrari") -> com.f1pitwall.tv.R.drawable.ic_team_ferrari
            lower.contains("red bull") -> com.f1pitwall.tv.R.drawable.ic_team_redbull
            lower.contains("mclaren") -> com.f1pitwall.tv.R.drawable.ic_team_mclaren
            lower.contains("aston") -> com.f1pitwall.tv.R.drawable.ic_team_astonmartin
            lower.contains("alpine") -> com.f1pitwall.tv.R.drawable.ic_team_alpine
            lower.contains("williams") -> com.f1pitwall.tv.R.drawable.ic_team_williams
            lower.contains("haas") -> com.f1pitwall.tv.R.drawable.ic_team_haas
            lower.contains("bulls") || lower.contains("rb") -> com.f1pitwall.tv.R.drawable.ic_team_rb
            lower.contains("audi") || lower.contains("sauber") || lower.contains("kick") -> com.f1pitwall.tv.R.drawable.ic_team_audi
            lower.contains("cadillac") -> com.f1pitwall.tv.R.drawable.ic_team_cadillac
            else -> com.f1pitwall.tv.R.drawable.ic_pitwall_logo
        }
    }

    fun getOfficialDriverHeadshot(code: String?, driverId: String?, familyName: String?): String {
        val c = code?.uppercase() ?: ""
        val fid = driverId?.lowercase() ?: ""
        val fam = familyName?.lowercase() ?: ""

        return when {
            c == "ANT" || fid.contains("antonelli") || fam.contains("antonelli") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/K/ANDANT01_Kimi_Antonelli/andant01.png.transform/1col/image.png"
            c == "VER" || fid.contains("verstappen") || fam.contains("verstappen") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/M/MAXVER01_Max_Verstappen/maxver01.png.transform/1col/image.png"
            c == "RUS" || fid.contains("russell") || fam.contains("russell") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GEORUS01_George_Russell/georus01.png.transform/1col/image.png"
            c == "HAM" || fid.contains("hamilton") || fam.contains("hamilton") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LEWHAM01_Lewis_Hamilton/lewham01.png.transform/1col/image.png"
            c == "LEC" || fid.contains("leclerc") || fam.contains("leclerc") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/C/CHALEC01_Charles_Leclerc/chalec01.png.transform/1col/image.png"
            c == "NOR" || fid.contains("norris") || fam.contains("norris") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LANNOR01_Lando_Norris/lannor01.png.transform/1col/image.png"
            c == "PIA" || fid.contains("piastri") || fam.contains("piastri") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OSCPIA01_Oscar_Piastri/oscpia01.png.transform/1col/image.png"
            c == "HAD" || fid.contains("hadjar") || fam.contains("hadjar") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/I/ISAHAD01_Isack_Hadjar/isahad01.png.transform/1col/image.png"
            c == "LAW" || fid.contains("lawson") || fam.contains("lawson") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LIALAW01_Liam_Lawson/lialaw01.png.transform/1col/image.png"
            c == "GAS" || fid.contains("gasly") || fam.contains("gasly") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/P/PIEGAS01_Pierre_Gasly/piegas01.png.transform/1col/image.png"
            c == "LIN" || fid.contains("lindblad") || fam.contains("lindblad") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png"
            c == "COL" || fid.contains("colapinto") || fam.contains("colapinto") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/F/FRACOL01_Franco_Colapinto/fracol01.png.transform/1col/image.png"
            c == "BEA" || fid.contains("bearman") || fam.contains("bearman") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OLIBEA01_Oliver_Bearman/olibea01.png.transform/1col/image.png"
            c == "BOR" || fid.contains("bortoleto") || fam.contains("bortoleto") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GABBOR01_Gabriel_Bortoleto/gabbor01.png.transform/1col/image.png"
            c == "HUL" || fid.contains("hulkenberg") || fam.contains("hulkenberg") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/N/NICHUL01_Nico_Hulkenberg/nichul01.png.transform/1col/image.png"
            c == "OCO" || fid.contains("ocon") || fam.contains("ocon") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/E/ESTOCO01_Esteban_Ocon/estoco01.png.transform/1col/image.png"
            c == "ALO" || fid.contains("alonso") || fam.contains("alonso") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/F/FERALO01_Fernando_Alonso/feralo01.png.transform/1col/image.png"
            c == "SAI" || fid.contains("sainz") || fam.contains("sainz") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/C/CARSAI01_Carlos_Sainz/carsai01.png.transform/1col/image.png"
            c == "ALB" || fid.contains("albon") || fam.contains("albon") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ALEALB01_Alexander_Albon/alealb01.png.transform/1col/image.png"
            c == "TSU" || fid.contains("tsunoda") || fam.contains("tsunoda") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/Y/YUKTSU01_Yuki_Tsunoda/yuktsu01.png.transform/1col/image.png"
            c == "STR" || fid.contains("stroll") || fam.contains("stroll") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LANSTR01_Lance_Stroll/lanstr01.png.transform/1col/image.png"
            c == "BOT" || fid.contains("bottas") || fam.contains("bottas") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/V/VALBOT01_Valtteri_Bottas/valbot01.png.transform/1col/image.png"
            c == "PER" || fid.contains("perez") || fam.contains("perez") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/S/SERPER01_Sergio_Perez/serper01.png.transform/1col/image.png"
            c == "DOO" || fid.contains("doohan") || fam.contains("doohan") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/J/JACDOO01_Jack_Doohan/jacdoo01.png.transform/1col/image.png"
            c == "MAG" || fid.contains("magnussen") || fam.contains("magnussen") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/K/KEVMAG01_Kevin_Magnussen/kevmag01.png.transform/1col/image.png"
            c == "ZHO" || fid.contains("zhou") || fam.contains("zhou") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GUAZHO01_Guanyu_Zhou/guazho01.png.transform/1col/image.png"
            c == "SAR" || fid.contains("sargeant") || fam.contains("sargeant") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LOGSAR01_Logan_Sargeant/logsar01.png.transform/1col/image.png"
            c == "RIC" || fid.contains("ricciardo") || fam.contains("ricciardo") ->
                "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/D/DANRIC01_Daniel_Ricciardo/danric01.png.transform/1col/image.png"
            else -> "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/fallback.png"
        }
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

    fun getTeamColor(team: String): String {
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

    suspend fun getRaceResults(year: Int = AppConfig.DEFAULT_SEASON, round: Int) = jolpicaApi.getRaceResults(year, round)

    /**
     * Obtiene la parrilla de salida (salida en carrera) a partir de la clasificación del Gran Premio.
     */
    suspend fun getStartingGrid(year: Int = AppConfig.DEFAULT_SEASON, round: Int? = null): List<DriverLeaderboardItem> = withContext(Dispatchers.IO) {
        try {
            val qResults = if (round != null) {
                jolpicaApi.getQualifyingResults(year, round)
            } else {
                jolpicaApi.getLatestQualifyingResults(year)?.second ?: emptyList()
            }

            if (qResults.isEmpty()) return@withContext emptyList()

            qResults.mapIndexed { index, item ->
                val pos = item.position?.toIntOrNull() ?: (index + 1)
                val row = (pos + 1) / 2
                val side = if (pos % 2 == 1) "I" else "D"
                val bestQ = item.q3?.takeIf { it.isNotBlank() }
                    ?: item.q2?.takeIf { it.isNotBlank() }
                    ?: item.q1?.takeIf { it.isNotBlank() }
                    ?: "--:--.---"

                val gapStr = if (pos == 1) "POLE POSITION" else "FILA $row ($side)"

                DriverLeaderboardItem(
                    driverNumber = item.number?.toIntOrNull()
                        ?: item.driver?.permanentNumber?.toIntOrNull()
                        ?: (index + 1),
                    position = pos,
                    acronym = item.driver?.code ?: "F1",
                    fullName = item.driver?.fullName ?: "Piloto",
                    teamName = item.constructor?.name ?: "",
                    teamColor = getTeamColor(item.constructor?.name ?: ""),
                    gapToLeader = gapStr,
                    interval = bestQ,
                    lastLap = bestQ,
                    s1 = item.q1 ?: "--",
                    s2 = item.q2 ?: "--",
                    s3 = item.q3 ?: "--",
                    tyreCompound = "SOFT",
                    tyreAge = 1
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getCircuits(year: Int = AppConfig.DEFAULT_SEASON): List<CircuitCardItem> = withContext(Dispatchers.IO) {
        val meetings = getMeetings(year)
        val seen = mutableSetOf<Int>()
        meetings.filter { it.circuitKey != null }
            .filter { seen.add(it.circuitKey!!) }
            .map { m ->
                val img = m.circuitImage?.takeIf { it.isNotBlank() }
                    ?: getCircuitSilhouetteUrl(m.location.orEmpty(), m.countryName.orEmpty())
                CircuitCardItem(
                    circuitKey = m.circuitKey!!,
                    circuitName = m.circuitShortName ?: m.location ?: "Circuito",
                    location = m.location ?: "",
                    country = m.countryName ?: "",
                    circuitImageUrl = img
                )
            }
    }

    private fun mapJolpicaRaceToMeeting(race: JolpicaRace, year: Int, index: Int): Meeting {
        val roundNum = race.round.toLongOrNull() ?: index.toLong()
        val meetingKey = year * 1000L + roundNum
        val locality = race.circuit?.location?.locality ?: ""
        val country = race.circuit?.location?.country ?: ""
        val circuitName = race.circuit?.circuitName ?: "Circuito $locality"
        val flag = getCountryFlagUrl(country)
        val circuitImg = getCircuitSilhouetteUrl(locality, country)

        val dateStart = race.firstPractice?.toIsoUtc() ?: "${race.date}T00:00:00Z"
        val dateEnd = if (!race.time.isNullOrEmpty()) "${race.date}T${race.time}" else "${race.date}T23:59:59Z"

        return Meeting(
            meetingKey = meetingKey,
            meetingName = race.raceName,
            officialName = "Formula 1 ${race.raceName} $year",
            location = locality,
            countryName = country,
            countryCode = "",
            countryFlag = flag,
            circuitKey = roundNum.toInt(),
            circuitShortName = circuitName,
            circuitImage = circuitImg,
            dateStart = dateStart,
            dateEnd = dateEnd,
            gmtOffset = null,
            year = year,
            isCancelled = false
        )
    }

    private fun mapJolpicaRaceToSessions(race: JolpicaRace, meetingKey: Long, year: Int): List<Session> {
        val sessions = mutableListOf<Session>()
        val circuit = race.circuit?.circuitName ?: "Circuito"
        val country = race.circuit?.location?.country ?: ""

        fun addSession(keySuffix: Long, name: String, type: String, sessionTime: JolpicaSessionTime?, defaultDurationHours: Long = 1) {
            val startIso = sessionTime?.toIsoUtc() ?: return
            val endIso = TimeUtils.addHoursToIso(startIso, defaultDurationHours) ?: startIso
            sessions.add(
                Session(
                    sessionKey = meetingKey * 10L + keySuffix,
                    sessionName = name,
                    sessionType = type,
                    dateStart = startIso,
                    dateEnd = endIso,
                    meetingKey = meetingKey,
                    circuitShortName = circuit,
                    countryName = country,
                    isCancelled = false
                )
            )
        }

        // Practice 1
        addSession(1L, "Práctica Libre 1", "Practice", race.firstPractice)

        // Practice 2 (or Sprint Qualifying)
        if (race.sprintQualifying != null) {
            addSession(2L, "Sprint Shootout", "Qualifying", race.sprintQualifying)
        } else if (race.secondPractice != null) {
            addSession(2L, "Práctica Libre 2", "Practice", race.secondPractice)
        }

        // Practice 3 (or Sprint)
        if (race.sprint != null) {
            addSession(3L, "Carrera Sprint", "Sprint", race.sprint)
        } else if (race.thirdPractice != null) {
            addSession(3L, "Práctica Libre 3", "Practice", race.thirdPractice)
        }

        // Qualifying
        addSession(4L, "Clasificación", "Qualifying", race.qualifying)

        // Race
        val raceStart = if (!race.time.isNullOrEmpty()) "${race.date}T${race.time}" else "${race.date}T13:00:00Z"
        val raceEnd = TimeUtils.addHoursToIso(raceStart, 2) ?: raceStart
        sessions.add(
            Session(
                sessionKey = meetingKey * 10L + 5L,
                sessionName = "Carrera Principal",
                sessionType = "Race",
                dateStart = raceStart,
                dateEnd = raceEnd,
                meetingKey = meetingKey,
                circuitShortName = circuit,
                countryName = country,
                isCancelled = false
            )
        )

        return sessions.sortedBy { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }
    }

    fun getCircuitSilhouetteUrl(location: String, country: String): String {
        val loc = "$location $country".lowercase()
        return when {
            loc.contains("albert") || loc.contains("australia") || loc.contains("melbourne") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Australia_Circuit.png"
            loc.contains("shanghai") || loc.contains("china") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/China_Circuit.png"
            loc.contains("suzuka") || loc.contains("japan") || loc.contains("japón") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Japan_Circuit.png"
            loc.contains("miami") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Miami_Circuit.png"
            loc.contains("canada") || loc.contains("canadá") || loc.contains("montreal") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Canada_Circuit.png"
            loc.contains("monaco") || loc.contains("mónaco") || loc.contains("monte carlo") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Monaco_Circuit.png"
            loc.contains("catalunya") || loc.contains("barcelona") || loc.contains("spain") || loc.contains("españa") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Spain_Circuit.png"
            loc.contains("red bull ring") || loc.contains("austria") || loc.contains("spielberg") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Austria_Circuit.png"
            loc.contains("silverstone") || loc.contains("great britain") || loc.contains("gran bretaña") || loc.contains("uk") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Great_Britain_Circuit.png"
            loc.contains("spa") || loc.contains("belgium") || loc.contains("bélgica") || loc.contains("francorchamps") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Belgium_Circuit.png"
            loc.contains("hungaroring") || loc.contains("hungary") || loc.contains("hungría") || loc.contains("budapest") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Hungary_Circuit.png"
            loc.contains("zandvoort") || loc.contains("netherlands") || loc.contains("países bajos") || loc.contains("holanda") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Netherlands_Circuit.png"
            loc.contains("monza") || loc.contains("italy") || loc.contains("italia") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Italy_Circuit.png"
            loc.contains("imola") || loc.contains("emilia") || loc.contains("romagna") || loc.contains("enzo") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Emilia_Romagna_Circuit.png"
            loc.contains("baku") || loc.contains("azerbaijan") || loc.contains("azerbaiyán") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Baku_Circuit.png"
            loc.contains("singapore") || loc.contains("singapur") || loc.contains("marina bay") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Singapore_Circuit.png"
            loc.contains("vegas") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Las_Vegas_Circuit.png"
            loc.contains("austin") || loc.contains("americas") || loc.contains("cota") ||
                    (loc.contains("united states") && !loc.contains("vegas") && !loc.contains("miami")) ||
                    (loc.contains("estados unidos") && !loc.contains("vegas") && !loc.contains("miami")) ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/USA_Circuit.png"
            loc.contains("mexico") || loc.contains("méxico") || loc.contains("rodriguez") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Mexico_Circuit.png"
            loc.contains("interlagos") || loc.contains("brazil") || loc.contains("brasil") || loc.contains("são paulo") || loc.contains("sao paulo") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Brazil_Circuit.png"
            loc.contains("qatar") || loc.contains("losail") || loc.contains("lusail") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Qatar_Circuit.png"
            loc.contains("abu dhabi") || loc.contains("yas marina") || loc.contains("uae") || loc.contains("emiratos") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Abu_Dhabi_Circuit.png"
            loc.contains("bahrain") || loc.contains("barein") || loc.contains("sakhir") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Bahrain_Circuit.png"
            loc.contains("jeddah") || loc.contains("saudi") || loc.contains("arabia") ->
                "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/Saudi_Arabia_Circuit.png"
            else -> "https://media.formula1.com/image/upload/content/dam/fom-website/2018-redesign-assets/Circuit%20maps%2016x9/USA_Circuit.png"
        }
    }

    fun getCountryFlagUrl(country: String): String {
        val lower = country.lowercase()
        val code = when {
            lower.contains("australia") -> "au"
            lower.contains("china") -> "cn"
            lower.contains("japan") || lower.contains("japón") -> "jp"
            lower.contains("united states") || lower.contains("usa") || lower.contains("estados unidos") -> "us"
            lower.contains("canada") || lower.contains("canadá") -> "ca"
            lower.contains("monaco") || lower.contains("mónaco") -> "mc"
            lower.contains("spain") || lower.contains("españa") -> "es"
            lower.contains("austria") -> "at"
            lower.contains("great britain") || lower.contains("uk") || lower.contains("gran bretaña") || lower.contains("united kingdom") -> "gb"
            lower.contains("belgium") || lower.contains("bélgica") -> "be"
            lower.contains("hungary") || lower.contains("hungría") -> "hu"
            lower.contains("netherlands") || lower.contains("países bajos") || lower.contains("holanda") -> "nl"
            lower.contains("italy") || lower.contains("italia") -> "it"
            lower.contains("azerbaijan") || lower.contains("azerbaiyán") -> "az"
            lower.contains("singapore") || lower.contains("singapur") -> "sg"
            lower.contains("mexico") || lower.contains("méxico") -> "mx"
            lower.contains("brazil") || lower.contains("brasil") -> "br"
            lower.contains("qatar") -> "qa"
            lower.contains("uae") || lower.contains("emirates") || lower.contains("emiratos") -> "ae"
            lower.contains("bahrain") || lower.contains("barein") -> "bh"
            lower.contains("saudi") -> "sa"
            lower.contains("malaysia") -> "my"
            else -> "un"
        }
        return "https://flagcdn.com/w160/$code.png"
    }
}
