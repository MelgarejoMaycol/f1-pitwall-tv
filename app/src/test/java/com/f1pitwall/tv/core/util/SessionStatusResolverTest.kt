package com.f1pitwall.tv.core.util

import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.model.Session
import com.f1pitwall.tv.model.SessionStatusType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class SessionStatusResolverTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun createSession(
        key: Long,
        name: String,
        startIso: String,
        endIso: String,
        meetingKey: Long = 100L
    ): Session {
        return Session(
            sessionKey = key,
            sessionName = name,
            sessionType = "Practice",
            dateStart = startIso,
            dateEnd = endIso,
            meetingKey = meetingKey,
            circuitShortName = "Test Circuit",
            countryName = "Test Country"
        )
    }

    private fun createMeeting(
        key: Long,
        name: String,
        startIso: String,
        endIso: String
    ): Meeting {
        return Meeting(
            meetingKey = key,
            meetingName = name,
            officialName = "Official $name",
            location = "Location",
            countryName = "Country",
            countryCode = "TC",
            countryFlag = null,
            circuitKey = 1,
            circuitShortName = "Track",
            circuitImage = null,
            dateStart = startIso,
            dateEnd = endIso,
            gmtOffset = "00:00:00",
            year = 2026,
            isCancelled = false
        )
    }

    @Test
    fun `historical session past dateEnd is NEVER marked as live`() {
        val session = createSession(
            key = 1L,
            name = "Race",
            startIso = "2026-11-08T17:00:00+00:00",
            endIso = "2026-11-08T19:00:00+00:00"
        )

        // Ref time: Nov 19, 2026 (11 days later)
        val now = dateFormat.parse("2026-11-19T14:00:00")!!

        val result = SessionStatusResolver.resolveMeetingSessions(
            sessions = listOf(session),
            now = now
        )

        assertNotEquals(SessionStatusType.LIVE, result.status)
        assertEquals(SessionStatusType.COMPLETED, result.status)
        assertEquals(1L, result.lastCompletedSession?.sessionKey)
        assertNull(result.activeSession)
    }

    @Test
    fun `session currently in progress is marked as LIVE`() {
        val session = createSession(
            key = 2L,
            name = "Qualifying",
            startIso = "2026-11-20T14:00:00+00:00",
            endIso = "2026-11-20T15:00:00+00:00"
        )

        // Ref time: inside session window
        val now = dateFormat.parse("2026-11-20T14:30:00")!!

        val result = SessionStatusResolver.resolveMeetingSessions(
            sessions = listOf(session),
            now = now
        )

        assertEquals(SessionStatusType.LIVE, result.status)
        assertEquals(2L, result.activeSession?.sessionKey)
    }

    @Test
    fun `upcoming session before dateStart is marked as UPCOMING`() {
        val session = createSession(
            key = 3L,
            name = "Practice 1",
            startIso = "2026-11-20T00:30:00+00:00",
            endIso = "2026-11-20T01:30:00+00:00"
        )

        // Ref time: before session starts
        val now = dateFormat.parse("2026-11-19T18:00:00")!!

        val result = SessionStatusResolver.resolveMeetingSessions(
            sessions = listOf(session),
            now = now
        )

        assertEquals(SessionStatusType.UPCOMING, result.status)
        assertEquals(3L, result.upcomingSession?.sessionKey)
        assertNull(result.activeSession)
    }

    @Test
    fun `empty sessions list resolves to NO_DATA`() {
        val result = SessionStatusResolver.resolveMeetingSessions(
            sessions = emptyList(),
            now = Date()
        )

        assertEquals(SessionStatusType.NO_DATA, result.status)
        assertNull(result.activeSession)
        assertNull(result.upcomingSession)
        assertNull(result.lastCompletedSession)
    }

    @Test
    fun `resolveSeasonStatus correctly distinguishes live meeting from upcoming meeting`() = runBlocking {
        val pastMeeting = createMeeting(10L, "Brazil GP", "2026-11-06T15:00:00", "2026-11-08T19:00:00")
        val upcomingMeeting = createMeeting(20L, "Las Vegas GP", "2026-11-20T00:00:00", "2026-11-22T06:00:00")

        val sessionsMap = mapOf(
            10L to listOf(
                createSession(101L, "Race", "2026-11-08T17:00:00", "2026-11-08T19:00:00", 10L)
            ),
            20L to listOf(
                createSession(201L, "FP1", "2026-11-20T00:30:00", "2026-11-20T01:30:00", 20L)
            )
        )

        // Nov 19: between Brazil and Las Vegas
        val now = dateFormat.parse("2026-11-19T12:00:00")!!

        val result = SessionStatusResolver.resolveSeasonStatus(
            meetings = listOf(pastMeeting, upcomingMeeting),
            fetchSessions = { key -> sessionsMap[key] ?: emptyList() },
            now = now
        )

        assertEquals(SessionStatusType.UPCOMING, result.status)
        assertEquals(201L, result.upcomingSession?.sessionKey)
        assertEquals(20L, result.upcomingMeeting?.meetingKey)
        assertEquals(101L, result.lastCompletedSession?.sessionKey)
        assertEquals(10L, result.lastCompletedMeeting?.meetingKey)
        assertNull(result.activeSession)
    }
}
