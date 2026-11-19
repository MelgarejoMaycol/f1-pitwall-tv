package com.f1pitwall.tv.core.util

import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.model.ResolvedSessionInfo
import com.f1pitwall.tv.model.Session
import com.f1pitwall.tv.model.SessionStatusType
import java.util.*

object SessionStatusResolver {

    /**
     * Resuelve el estado de una lista de sesiones correspondientes a un evento específico.
     */
    fun resolveMeetingSessions(
        sessions: List<Session>,
        meeting: Meeting? = null,
        now: Date = Date()
    ): ResolvedSessionInfo {
        if (sessions.isEmpty()) {
            return ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }

        val activeSession = sessions.firstOrNull { TimeUtils.isCurrentlyLive(it.dateStart, it.dateEnd, now) }
        val upcomingSession = sessions
            .filter { TimeUtils.isUpcoming(it.dateStart, now) }
            .minByOrNull { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }
        val lastCompletedSession = sessions
            .filter { TimeUtils.isCompleted(it.dateEnd, now) }
            .maxByOrNull { TimeUtils.parseUtc(it.dateEnd)?.time ?: Long.MIN_VALUE }

        return when {
            activeSession != null -> ResolvedSessionInfo(
                status = SessionStatusType.LIVE,
                activeSession = activeSession,
                activeMeeting = meeting,
                upcomingSession = upcomingSession,
                upcomingMeeting = meeting,
                lastCompletedSession = lastCompletedSession,
                lastCompletedMeeting = meeting
            )
            upcomingSession != null -> ResolvedSessionInfo(
                status = SessionStatusType.UPCOMING,
                activeSession = null,
                activeMeeting = null,
                upcomingSession = upcomingSession,
                upcomingMeeting = meeting,
                lastCompletedSession = lastCompletedSession,
                lastCompletedMeeting = meeting
            )
            lastCompletedSession != null -> ResolvedSessionInfo(
                status = SessionStatusType.COMPLETED,
                activeSession = null,
                activeMeeting = null,
                upcomingSession = null,
                upcomingMeeting = null,
                lastCompletedSession = lastCompletedSession,
                lastCompletedMeeting = meeting
            )
            else -> ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }
    }

    /**
     * Resuelve el estado global de la temporada a partir de los eventos (Grandes Premios)
     * y una función que provee las sesiones de cada evento.
     */
    suspend fun resolveSeasonStatus(
        meetings: List<Meeting>,
        fetchSessions: suspend (Long) -> List<Session>,
        now: Date = Date()
    ): ResolvedSessionInfo {
        if (meetings.isEmpty()) {
            return ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }

        val validMeetings = meetings.filter { it.dateStart != null }
            .sortedBy { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }

        if (validMeetings.isEmpty()) {
            return ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }

        // 1. Verificar si hay alguna sesión en directo en los eventos cercanos al momento actual (+/- 24 hrs)
        val candidateMeetings = validMeetings.filter { m ->
            val start = TimeUtils.parseUtc(m.dateStart)?.time
            val end = (TimeUtils.parseUtc(m.dateEnd) ?: TimeUtils.parseUtc(m.dateStart))?.time
            if (start != null && end != null) {
                now.time in (start - 86_400_000L)..(end + 86_400_000L)
            } else false
        }

        for (candidate in candidateMeetings) {
            val sessions = fetchSessions(candidate.meetingKey)
            val liveSession = sessions.firstOrNull { TimeUtils.isCurrentlyLive(it.dateStart, it.dateEnd, now) }
            if (liveSession != null) {
                val nextUpcoming = sessions
                    .filter { TimeUtils.isUpcoming(it.dateStart, now) }
                    .minByOrNull { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }
                val prevCompleted = sessions
                    .filter { TimeUtils.isCompleted(it.dateEnd, now) }
                    .maxByOrNull { TimeUtils.parseUtc(it.dateEnd)?.time ?: Long.MIN_VALUE }

                return ResolvedSessionInfo(
                    status = SessionStatusType.LIVE,
                    activeSession = liveSession,
                    activeMeeting = candidate,
                    upcomingSession = nextUpcoming,
                    upcomingMeeting = candidate,
                    lastCompletedSession = prevCompleted,
                    lastCompletedMeeting = candidate
                )
            }
        }

        // 2. Si no hay sesión en directo, buscar la próxima sesión programada
        var nextSession: Session? = null
        var nextMeeting: Meeting? = null

        val upcomingMeetings = validMeetings.filter { m ->
            val end = TimeUtils.parseUtc(m.dateEnd) ?: TimeUtils.parseUtc(m.dateStart)
            end != null && end.after(now)
        }

        for (m in upcomingMeetings) {
            val sessions = fetchSessions(m.meetingKey)
            val upcoming = sessions
                .filter { TimeUtils.isUpcoming(it.dateStart, now) }
                .minByOrNull { TimeUtils.parseUtc(it.dateStart)?.time ?: Long.MAX_VALUE }

            if (upcoming != null) {
                nextSession = upcoming
                nextMeeting = m
                break
            } else if (sessions.isNotEmpty()) {
                // Si la reunión está programada a futuro pero los horarios de sesión no tienen start posterior
                nextSession = sessions.firstOrNull()
                nextMeeting = m
                break
            }
        }

        // 3. Buscar la última sesión finalizada
        var completedSession: Session? = null
        var completedMeeting: Meeting? = null

        val pastMeetings = validMeetings.filter { m ->
            val start = TimeUtils.parseUtc(m.dateStart)
            start != null && start.before(now)
        }.reversed()

        for (m in pastMeetings) {
            val sessions = fetchSessions(m.meetingKey)
            val completed = sessions
                .filter { TimeUtils.isCompleted(it.dateEnd, now) }
                .maxByOrNull { TimeUtils.parseUtc(it.dateEnd)?.time ?: Long.MIN_VALUE }

            if (completed != null) {
                completedSession = completed
                completedMeeting = m
                break
            } else if (sessions.isNotEmpty()) {
                // Última sesión disponible del evento pasado
                completedSession = sessions.lastOrNull()
                completedMeeting = m
                break
            }
        }

        return when {
            nextSession != null -> ResolvedSessionInfo(
                status = SessionStatusType.UPCOMING,
                activeSession = null,
                activeMeeting = null,
                upcomingSession = nextSession,
                upcomingMeeting = nextMeeting,
                lastCompletedSession = completedSession,
                lastCompletedMeeting = completedMeeting
            )
            completedSession != null -> ResolvedSessionInfo(
                status = SessionStatusType.COMPLETED,
                activeSession = null,
                activeMeeting = null,
                upcomingSession = null,
                upcomingMeeting = null,
                lastCompletedSession = completedSession,
                lastCompletedMeeting = completedMeeting
            )
            else -> ResolvedSessionInfo(status = SessionStatusType.NO_DATA)
        }
    }
}
