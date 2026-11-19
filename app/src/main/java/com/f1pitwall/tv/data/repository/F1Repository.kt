package com.f1pitwall.tv.data.repository

import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.storage.TimedCache
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.core.util.SessionStatusResolver
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.model.*
import com.f1pitwall.tv.data.mapper.LeaderboardMapper
import com.f1pitwall.tv.data.mapper.OfficialResultsMapper
import com.f1pitwall.tv.model.CarTelemetry
import com.f1pitwall.tv.model.DriverLeaderboardItem
import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.model.ResolvedSessionInfo
import com.f1pitwall.tv.model.Session
import com.f1pitwall.tv.model.SessionStatusType
import com.f1pitwall.tv.network.JolpicaApiClient
import com.f1pitwall.tv.network.OpenF1ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class F1Repository(
    private val openF1Api: OpenF1ApiClient,
    private val jolpicaApi: JolpicaApiClient = JolpicaApiClient()
) {

    private val meetingsCache = TimedCache<Int, List<Meeting>>(TimedCache.DEFAULT_TTL_MS)
    private val driversCache = TimedCache<Int, List<DriverCardItem>>(TimedCache.DEFAULT_TTL_MS)
    private val constructorsCache = TimedCache<Int, List<ConstructorCardItem>>(TimedCache.DEFAULT_TTL_MS)
    private val recentRaceCache = TimedCache<Int, RecentRaceSummary>(TimedCache.DEFAULT_TTL_MS)
    private val circuitsCache = TimedCache<Int, List<CircuitCardItem>>(TimedCache.DEFAULT_TTL_MS)
    private val historicalResultsCache = TimedCache<String, List<DriverLeaderboardItem>>(TimedCache.LONG_TTL_MS)
    private val sessionStatusCache = TimedCache<Int, ResolvedSessionInfo>(TimedCache.SHORT_TTL_MS)

    suspend fun getMeetings(year: Int = AppConfig.DEFAULT_SEASON): List<Meeting> = withContext(Dispatchers.IO) {
        val cached = meetingsCache.get(year)
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }
        try {
            val meetings = openF1Api.getMeetings(year)
            if (meetings.isNotEmpty()) {
                val ttl = if (year < AppConfig.DEFAULT_SEASON) TimedCache.LONG_TTL_MS else TimedCache.DEFAULT_TTL_MS
                meetingsCache.put(year, meetings, ttl)
            }
            meetings
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error fetching meetings for $year, using stale cache if available", e)
            meetingsCache.getStale(year) ?: emptyList()
        }
    }

    /**
     * Resuelve de forma centralizada y basada estrictamente en fechas/horas reales de la API
     * el estado de sesión: en vivo, próxima programada, última finalizada o sin información.
     */
    suspend fun resolveCurrentSessionStatus(
        year: Int = AppConfig.DEFAULT_SEASON,
        now: Date = Date()
    ): ResolvedSessionInfo = withContext(Dispatchers.IO) {
        val cached = sessionStatusCache.get(year)
        if (cached != null) {
            return@withContext cached
        }
        val meetings = getMeetings(year)
        if (meetings.isEmpty()) {
            return@withContext ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }
        val resolved = SessionStatusResolver.resolveSeasonStatus(
            meetings = meetings,
            fetchSessions = { meetingKey -> openF1Api.getSessions(meetingKey) },
            now = now
        )
        sessionStatusCache.put(year, resolved, TimedCache.SHORT_TTL_MS)
        resolved
    }

    /**
     * Selección inteligente del Gran Premio basada en el estado real resuelto:
     * 1. Sesión activa en pista.
     * 2. Próximo Gran Premio en el calendario.
     * 3. Último Gran Premio finalizado.
     */
    suspend fun findSmartSelectedMeeting(
        year: Int = AppConfig.DEFAULT_SEASON,
        now: Date = Date()
    ): Meeting? = withContext(Dispatchers.IO) {
        val resolved = resolveCurrentSessionStatus(year, now)
        when (resolved.status) {
            SessionStatusType.LIVE -> resolved.activeMeeting
            SessionStatusType.UPCOMING -> resolved.upcomingMeeting ?: resolved.lastCompletedMeeting
            SessionStatusType.COMPLETED -> resolved.lastCompletedMeeting
            SessionStatusType.NO_DATA -> getMeetings(year).firstOrNull()
        } ?: getMeetings(year).firstOrNull()
    }

    /**
     * Resuelve dinámicamente los datos para el Hero Banner de la pantalla de inicio
     * utilizando exclusivamente el estado y sesiones reales obtenidas de la API.
     */
    suspend fun resolveHeroBannerData(
        year: Int = AppConfig.DEFAULT_SEASON,
        now: Date = Date()
    ): HeroBannerData? = withContext(Dispatchers.IO) {
        val resolved = resolveCurrentSessionStatus(year, now)

        when (resolved.status) {
            SessionStatusType.LIVE -> {
                val meeting = resolved.activeMeeting
                val session = resolved.activeSession
                if (meeting != null && session != null) {
                    HeroBannerData(
                        type = HeroStateType.LIVE,
                        grandPrixName = meeting.meetingName ?: "Gran Premio en Vivo",
                        officialName = meeting.officialName ?: (meeting.meetingName ?: ""),
                        circuitName = meeting.circuitShortName ?: "Circuito Oficial",
                        location = "${meeting.location ?: ""}, ${meeting.countryName ?: ""}",
                        countryName = meeting.countryName ?: "",
                        dateDisplay = TimeUtils.formatColombiaDateRange(meeting.dateStart, meeting.dateEnd),
                        timingDisplay = "🔴 Sesión Activa: ${session.sessionName ?: "En Pista"}",
                        countdownText = "EN DIRECTO",
                        flagUrl = meeting.countryFlag,
                        circuitImageUrl = meeting.circuitImage,
                        meetingKey = meeting.meetingKey,
                        sessionKey = session.sessionKey
                    )
                } else null
            }
            SessionStatusType.UPCOMING -> {
                val meeting = resolved.upcomingMeeting
                val session = resolved.upcomingSession
                if (meeting != null) {
                    val sStart = session?.dateStart ?: meeting.dateStart
                    val countdown = TimeUtils.calculateCountdown(sStart, now)
                    val sessionName = session?.sessionName ?: "Próxima Carrera"
                    val colombiaTime = TimeUtils.formatToColombiaTime(sStart)

                    HeroBannerData(
                        type = HeroStateType.UPCOMING,
                        grandPrixName = meeting.meetingName ?: "Próximo Gran Premio",
                        officialName = meeting.officialName ?: "",
                        circuitName = meeting.circuitShortName ?: (meeting.location ?: "Circuito"),
                        location = "${meeting.location ?: ""}, ${meeting.countryName ?: ""}",
                        countryName = meeting.countryName ?: "",
                        dateDisplay = TimeUtils.formatColombiaDateRange(meeting.dateStart, meeting.dateEnd),
                        timingDisplay = "$sessionName • $colombiaTime (Hora Colombia)",
                        countdownText = countdown,
                        flagUrl = meeting.countryFlag,
                        circuitImageUrl = meeting.circuitImage,
                        meetingKey = meeting.meetingKey,
                        sessionKey = session?.sessionKey
                    )
                } else null
            }
            SessionStatusType.COMPLETED -> {
                val meeting = resolved.lastCompletedMeeting
                val session = resolved.lastCompletedSession
                if (meeting != null) {
                    HeroBannerData(
                        type = HeroStateType.COMPLETED,
                        grandPrixName = meeting.meetingName ?: "Gran Premio Finalizado",
                        officialName = meeting.officialName ?: "",
                        circuitName = meeting.circuitShortName ?: (meeting.location ?: "Circuito"),
                        location = "${meeting.location ?: ""}, ${meeting.countryName ?: ""}",
                        countryName = meeting.countryName ?: "",
                        dateDisplay = TimeUtils.formatColombiaDateRange(meeting.dateStart, meeting.dateEnd),
                        timingDisplay = "Temporada Finalizada • ${session?.sessionName ?: "Resultados Oficiales"}",
                        countdownText = "TEMPORADA FINALIZADA",
                        flagUrl = meeting.countryFlag,
                        circuitImageUrl = meeting.circuitImage,
                        meetingKey = meeting.meetingKey,
                        sessionKey = session?.sessionKey
                    )
                } else null
            }
            SessionStatusType.NO_DATA -> null
        }
    }

    /**
     * Obtiene el resumen del Gran Premio más reciente que haya finalizado con sus resultados oficiales (Jolpica F1).
     * Si no hay resultados disponibles, retorna null para no mostrar información fabricada.
     */
    suspend fun getRecentRaceSummary(year: Int = AppConfig.DEFAULT_SEASON): RecentRaceSummary? = withContext(Dispatchers.IO) {
        val cached = recentRaceCache.get(year)
        if (cached != null) return@withContext cached

        try {
            val latest = jolpicaApi.getLatestRaceResults(year) ?: return@withContext recentRaceCache.getStale(year)
            val race = latest.first
            val results = latest.second

            if (results.isEmpty()) {
                return@withContext recentRaceCache.getStale(year)
            }

            val winner = results.firstOrNull() ?: return@withContext null
            val podium = results.take(3).map { r ->
                PodiumDriver(
                    position = r.position?.toIntOrNull() ?: 1,
                    fullName = r.driver?.fullName ?: "Piloto F1",
                    teamName = r.constructor?.name ?: "Escudería",
                    code = r.driver?.code ?: "F1"
                )
            }

            val summary = RecentRaceSummary(
                grandPrixName = race.raceName,
                country = race.circuit?.location?.country ?: "País",
                circuitName = race.circuit?.circuitName ?: "Circuito",
                circuitId = race.circuit?.circuitId,
                dateDisplay = race.date,
                winnerName = winner.driver?.fullName ?: "Por confirmar",
                winnerTeam = winner.constructor?.name ?: "",
                podium = podium,
                round = race.round.toIntOrNull() ?: 1,
                season = race.season.toIntOrNull() ?: year
            )
            val ttl = if (year < AppConfig.DEFAULT_SEASON) TimedCache.LONG_TTL_MS else TimedCache.DEFAULT_TTL_MS
            recentRaceCache.put(year, summary, ttl)
            summary
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error al obtener resumen de carrera para $year, usando caché previo si existe", e)
            recentRaceCache.getStale(year)
        }
    }

    /**
     * Consulta la telemetría en tiempo real o histórica para un piloto específico.
     */
    suspend fun getTelemetryForDriver(
        sessionKey: Long,
        driverNumber: Int,
        fromDate: String? = null
    ): CarTelemetry? = withContext(Dispatchers.IO) {
        try {
            openF1Api.getLatestTelemetry(sessionKey, driverNumber, fromDate)
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error consultando telemetría para piloto #$driverNumber en sesión $sessionKey", e)
            null
        }
    }

    /**
     * Obtiene el último audio de radio de equipo disponible para un piloto.
     */
    suspend fun getDriverTeamRadio(sessionKey: Long, driverNumber: Int): com.f1pitwall.tv.model.TeamRadio? = withContext(Dispatchers.IO) {
        try {
            val radios = openF1Api.getTeamRadiosForDriver(sessionKey, driverNumber)
            radios.firstOrNull { !it.recordingUrl.isNullOrEmpty() }
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error al obtener radio de equipo para piloto #$driverNumber", e)
            null
        }
    }

    /**
     * Mundial de Pilotos: Ordenado estrictamente de mayor a menor puntuación real (Jolpica F1 API).
     */
    suspend fun getDriverStandings(year: Int = AppConfig.DEFAULT_SEASON): List<DriverCardItem> = withContext(Dispatchers.IO) {
        val cached = driversCache.get(year)
        if (!cached.isNullOrEmpty()) return@withContext cached

        try {
            val apiStandings = jolpicaApi.getDriverStandings(year)
            if (apiStandings.isEmpty()) {
                return@withContext driversCache.getStale(year) ?: emptyList()
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
                val num = driverObj?.permanentNumber?.toIntOrNull() ?: 0

                val headshot = getOfficialDriverHeadshot(driverObj?.code, driverObj?.driverId, driverObj?.familyName)

                DriverCardItem(
                    driverNumber = num,
                    acronym = driverObj?.code ?: (if (num > 0) "#$num" else "#--"),
                    fullName = driverObj?.fullName ?: "Piloto F1",
                    teamName = teamName,
                    teamColor = teamColor,
                    headshotUrl = headshot,
                    position = item.position?.toIntOrNull() ?: (index + 1),
                    points = pts,
                    gapToLeader = gap,
                    wins = item.wins?.toIntOrNull() ?: 0
                )
            }
            val ttl = if (year < AppConfig.DEFAULT_SEASON) TimedCache.LONG_TTL_MS else TimedCache.DEFAULT_TTL_MS
            driversCache.put(year, items, ttl)
            items
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error al obtener clasificación de pilotos para $year, usando respaldo en caché", e)
            driversCache.getStale(year) ?: emptyList()
        }
    }

    suspend fun getDriversCarouselData(sessionKey: Long): List<DriverCardItem> = withContext(Dispatchers.IO) {
        val standings = getDriverStandings(AppConfig.DEFAULT_SEASON)
        if (standings.isNotEmpty()) {
            return@withContext standings
        }
        val cached = driversCache.getStale(AppConfig.DEFAULT_SEASON)
        if (!cached.isNullOrEmpty()) return@withContext cached
        try {
            val apiDrivers = openF1Api.getDrivers(sessionKey)
            val items = apiDrivers.map { d ->
                val rawUrl = d.headshotUrl?.replace("d_driver_fallback_image.png/", "")
                val headshot = getOfficialDriverHeadshot(d.acronym, null, d.broadcastName) ?: rawUrl
                DriverCardItem(
                    driverNumber = d.driverNumber,
                    acronym = d.acronym ?: "#${d.driverNumber}",
                    fullName = d.fullName ?: d.broadcastName ?: "Piloto ${d.driverNumber}",
                    teamName = d.teamName ?: "Escudería F1",
                    teamColor = d.teamColour ?: getTeamColor(d.teamName ?: ""),
                    headshotUrl = headshot
                )
            }
            driversCache.put(AppConfig.DEFAULT_SEASON, items, TimedCache.DEFAULT_TTL_MS)
            items
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error obteniendo pilotos de sesión $sessionKey", e)
            driversCache.getStale(AppConfig.DEFAULT_SEASON) ?: emptyList()
        }
    }

    /**
     * Mundial de Constructores: Ordenado estrictamente de mayor a menor puntuación real (Jolpica F1 API).
     * Si no hay datos en la API, devuelve una lista vacía para no inventar puntuaciones.
     * Los pilotos de cada escudería se obtienen dinámicamente de la clasificación de pilotos de la misma temporada,
     * eliminando asociaciones fijas o desactualizadas en código.
     */
    suspend fun getConstructorStandings(year: Int = AppConfig.DEFAULT_SEASON): List<ConstructorCardItem> = withContext(Dispatchers.IO) {
        val cached = constructorsCache.get(year)
        if (!cached.isNullOrEmpty()) return@withContext cached

        try {
            val apiStandings = jolpicaApi.getConstructorStandings(year)
            if (apiStandings.isEmpty()) {
                return@withContext constructorsCache.getStale(year) ?: emptyList()
            }

            // Mapeo dinámico de constructores a sus pilotos según las carreras de la temporada
            val constructorDriversMap = mutableMapOf<String, MutableList<String>>()
            try {
                val driverStandings = jolpicaApi.getDriverStandings(year)
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
            } catch (e: Exception) {
                // Silenciar fallo secundario de pilotos de constructores
            }

            val leaderPoints = apiStandings.firstOrNull()?.points?.toDoubleOrNull() ?: 0.0

            val items = apiStandings.mapIndexed { index, item ->
                val pts = item.points ?: "0"
                val ptsDouble = pts.toDoubleOrNull() ?: 0.0
                val gap = if (index == 0) "LÍDER" else {
                    val diff = leaderPoints - ptsDouble
                    if (diff % 1.0 == 0.0) "-${diff.toInt()} pts" else "-%.1f pts".format(diff)
                }

                val teamName = item.constructor?.name ?: "Escudería"
                val teamId = item.constructor?.constructorId?.lowercase()?.trim() ?: ""
                val teamColor = getTeamColor(teamName)

                // Pilotos resueltos dinámicamente desde los resultados oficiales de la temporada
                val dynamicDrivers = constructorDriversMap[teamName.lowercase().trim()]
                    ?: constructorDriversMap[teamId]
                    ?: emptyList()

                ConstructorCardItem(
                    teamName = teamName,
                    teamColor = teamColor,
                    drivers = dynamicDrivers,
                    position = item.position?.toIntOrNull() ?: (index + 1),
                    points = pts,
                    wins = item.wins?.toIntOrNull() ?: 0,
                    gapToLeader = gap,
                    logoResId = getConstructorLogoRes(teamName)
                )
            }
            val ttl = if (year < AppConfig.DEFAULT_SEASON) TimedCache.LONG_TTL_MS else TimedCache.DEFAULT_TTL_MS
            constructorsCache.put(year, items, ttl)
            items
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error al obtener constructores para $year, usando respaldo en caché", e)
            constructorsCache.getStale(year) ?: emptyList()
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
            lower.contains("bulls") || lower.contains("rb") -> com.f1pitwall.tv.R.drawable.ic_team_rb
            lower.contains("haas") -> com.f1pitwall.tv.R.drawable.ic_team_haas
            lower.contains("audi") || lower.contains("sauber") -> com.f1pitwall.tv.R.drawable.ic_team_audi
            lower.contains("cadillac") -> com.f1pitwall.tv.R.drawable.ic_team_cadillac
            else -> com.f1pitwall.tv.R.drawable.ic_pitwall_logo
        }
    }

    fun getOfficialDriverHeadshot(code: String?, driverId: String?, familyName: String?): String? {
        val c = (code ?: "").uppercase().trim()
        val fid = (driverId ?: "").lowercase().trim()
        val fam = (familyName ?: "").lowercase().trim()

        return when {
            c == "ANT" || fid.contains("antonelli") || fam.contains("antonelli") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/K/ANDANT01_Kimi_Antonelli/andant01.png.transform/1col/image.png"
            c == "VER" || fid.contains("verstappen") || fam.contains("verstappen") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/M/MAXVER01_Max_Verstappen/maxver01.png.transform/1col/image.png"
            c == "RUS" || fid.contains("russell") || fam.contains("russell") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/G/GEORUS01_George_Russell/georus01.png.transform/1col/image.png"
            c == "HAM" || fid.contains("hamilton") || fam.contains("hamilton") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/L/LEWHAM01_Lewis_Hamilton/lewham01.png.transform/1col/image.png"
            c == "LEC" || fid.contains("leclerc") || fam.contains("leclerc") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/C/CHALEC01_Charles_Leclerc/chalec01.png.transform/1col/image.png"
            c == "NOR" || fid.contains("norris") || fam.contains("norris") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/L/LANNOR01_Lando_Norris/lannor01.png.transform/1col/image.png"
            c == "PIA" || fid.contains("piastri") || fam.contains("piastri") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/O/OSCPIA01_Oscar_Piastri/oscpia01.png.transform/1col/image.png"
            c == "HAD" || fid.contains("hadjar") || fam.contains("hadjar") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/I/ISAHAD01_Isack_Hadjar/isahad01.png.transform/1col/image.png"
            c == "LAW" || fid.contains("lawson") || fam.contains("lawson") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/L/LIALAW01_Liam_Lawson/lialaw01.png.transform/1col/image.png"
            c == "GAS" || fid.contains("gasly") || fam.contains("gasly") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/P/PIEGAS01_Pierre_Gasly/piegas01.png.transform/1col/image.png"
            c == "LIN" || fid.contains("lindblad") || fam.contains("lindblad") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/A/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png"
            c == "COL" || fid.contains("colapinto") || fam.contains("colapinto") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/F/FRACOL01_Franco_Colapinto/fracol01.png.transform/1col/image.png"
            c == "BEA" || fid.contains("bearman") || fam.contains("bearman") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/O/OLIBEA01_Oliver_Bearman/olibea01.png.transform/1col/image.png"
            c == "BOR" || fid.contains("bortoleto") || fam.contains("bortoleto") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/G/GABBOR01_Gabriel_Bortoleto/gabbor01.png.transform/1col/image.png"
            c == "HUL" || fid.contains("hulkenberg") || fam.contains("hulkenberg") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/N/NICHUL01_Nico_Hulkenberg/nichul01.png.transform/1col/image.png"
            c == "OCO" || fid.contains("ocon") || fam.contains("ocon") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/E/ESTOCO01_Esteban_Ocon/estoco01.png.transform/1col/image.png"
            c == "ALO" || fid.contains("alonso") || fam.contains("alonso") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/F/FERALO01_Fernando_Alonso/feralo01.png.transform/1col/image.png"
            c == "SAI" || fid.contains("sainz") || fam.contains("sainz") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/C/CARSAI01_Carlos_Sainz/carsai01.png.transform/1col/image.png"
            c == "ALB" || fid.contains("albon") || fam.contains("albon") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/A/ALEALB01_Alexander_Albon/alealb01.png.transform/1col/image.png"
            c == "TSU" || fid.contains("tsunoda") || fam.contains("tsunoda") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/Y/YUKTSU01_Yuki_Tsunoda/yuktsu01.png.transform/1col/image.png"
            c == "STR" || fid.contains("stroll") || fam.contains("stroll") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/L/LANSTR01_Lance_Stroll/lanstr01.png.transform/1col/image.png"
            c == "BOT" || fid.contains("bottas") || fam.contains("bottas") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/V/VALBOT01_Valtteri_Bottas/valbot01.png.transform/1col/image.png"
            c == "PER" || fid.contains("perez") || fam.contains("perez") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/S/SERPER01_Sergio_Perez/serper01.png.transform/1col/image.png"
            c == "DOO" || fid.contains("doohan") || fam.contains("doohan") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/J/JACDOO01_Jack_Doohan/jacdoo01.png.transform/1col/image.png"
            c == "MAG" || fid.contains("magnussen") || fam.contains("magnussen") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/K/KEVMAG01_Kevin_Magnussen/kevmag01.png.transform/1col/image.png"
            c == "ZHO" || fid.contains("zhou") || fam.contains("zhou") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/G/GUAZHO01_Guanyu_Zhou/guazho01.png.transform/1col/image.png"
            c == "SAR" || fid.contains("sargeant") || fam.contains("sargeant") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/L/LOGSAR01_Logan_Sargeant/logsar01.png.transform/1col/image.png"
            c == "RIC" || fid.contains("ricciardo") || fam.contains("ricciardo") ->
                "https://media.formula1.com/content/dam/fom-website/drivers/D/DANRIC01_Daniel_Ricciardo/danric01.png.transform/1col/image.png"
            else -> null
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

    suspend fun getCircuits(year: Int = AppConfig.DEFAULT_SEASON): List<CircuitCardItem> = withContext(Dispatchers.IO) {
        val cached = circuitsCache.get(year)
        if (!cached.isNullOrEmpty()) return@withContext cached

        val meetings = getMeetings(year)
        val championshipMeetings = meetings.filter { !it.isPreSeasonTesting }
        val targetMeetings = if (championshipMeetings.isNotEmpty()) championshipMeetings else meetings

        val items = targetMeetings.mapIndexed { index, m ->
            CircuitCardItem(
                circuitKey = m.circuitKey ?: 0,
                meetingKey = m.meetingKey,
                circuitName = m.circuitShortName ?: m.location ?: "Circuito",
                location = m.location ?: "",
                country = m.countryName ?: "",
                circuitImageUrl = m.circuitImage,
                season = m.year ?: year,
                round = index + 1
            )
        }
        val ttl = if (year < AppConfig.DEFAULT_SEASON) TimedCache.LONG_TTL_MS else TimedCache.DEFAULT_TTL_MS
        circuitsCache.put(year, items, ttl)
        items
    }

    /**
     * Obtiene los resultados históricos oficiales para un Gran Premio o sesión.
     * Utiliza Jolpica como fuente principal para resultados oficiales de carrera (clasificación FIA con tiempos,
     * diferencias, puntos y abandonos).
     * Si no hay resultados oficiales en Jolpica (o es una sesión de prácticas/clasificación),
     * consulta la telemetría de OpenF1 como respaldo técnico.
     */
    suspend fun getHistoricalResults(
        year: Int,
        meeting: Meeting,
        session: Session? = null
    ): List<DriverLeaderboardItem> = withContext(Dispatchers.IO) {
        val cacheKey = "$year-${meeting.meetingKey}-${session?.sessionKey ?: 0}"
        val cached = historicalResultsCache.get(cacheKey)
        if (!cached.isNullOrEmpty()) {
            return@withContext cached
        }

        val isRace = session == null || session.sessionType?.equals("Race", ignoreCase = true) == true

        if (isRace && !meeting.isPreSeasonTesting) {
            // Resolver la ronda oficial del Gran Premio dentro del calendario del campeonato
            val meetings = getMeetings(year)
            val championshipMeetings = meetings.filter { !it.isPreSeasonTesting }
            val roundIndex = championshipMeetings.indexOfFirst { it.meetingKey == meeting.meetingKey }
            val round = if (roundIndex >= 0) roundIndex + 1 else null

            if (round != null) {
                try {
                    val jolpicaResults = jolpicaApi.getRaceResults(year, round)
                    if (jolpicaResults.isNotEmpty()) {
                        val items = OfficialResultsMapper.mapJolpicaResultsToLeaderboardItems(
                            results = jolpicaResults,
                            teamColorResolver = { teamName -> getTeamColor(teamName) }
                        )
                        historicalResultsCache.put(cacheKey, items, TimedCache.LONG_TTL_MS)
                        return@withContext items
                    }
                } catch (e: Exception) {
                    AppLogger.w("F1Repository", "Error consultando resultados oficiales de Jolpica para ronda $round año $year", e)
                }
            }
        }

        // Si no es carrera o Jolpica no tiene datos de clasificación final oficial,
        // consultar datos de OpenF1 si hay sessionKey disponible
        val targetSessionKey = session?.sessionKey ?: try {
            val sessions = openF1Api.getSessions(meeting.meetingKey)
            val race = sessions.find { it.sessionType?.equals("Race", ignoreCase = true) == true } ?: sessions.lastOrNull()
            race?.sessionKey
        } catch (e: Exception) {
            AppLogger.w("F1Repository", "Error obteniendo sesión para reunión ${meeting.meetingKey}", e)
            null
        }

        if (targetSessionKey != null) {
            try {
                val drivers = openF1Api.getDrivers(targetSessionKey)
                val positions = openF1Api.getLatestPositions(targetSessionKey)
                val intervals = openF1Api.getLatestIntervals(targetSessionKey)
                val laps = openF1Api.getLatestLaps(targetSessionKey)
                val stints = openF1Api.getStints(targetSessionKey)
                val items = LeaderboardMapper.buildLeaderboardItems(drivers, positions, intervals, laps, stints)
                if (items.isNotEmpty()) {
                    historicalResultsCache.put(cacheKey, items, TimedCache.DEFAULT_TTL_MS)
                }
                return@withContext items
            } catch (e: Exception) {
                AppLogger.w("F1Repository", "Error obteniendo telemetría OpenF1 para sesión $targetSessionKey", e)
            }
        }

        historicalResultsCache.getStale(cacheKey) ?: emptyList()
    }
}
