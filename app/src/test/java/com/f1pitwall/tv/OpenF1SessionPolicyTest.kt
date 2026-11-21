package com.f1pitwall.tv

import com.f1pitwall.tv.network.OpenF1SessionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenF1SessionPolicyTest {

    @Test
    fun practiceSessionsDoNotRequestRaceOnlyIntervals() {
        assertFalse(OpenF1SessionPolicy.supportsIntervals("Practice", "Practice 1"))
        assertFalse(OpenF1SessionPolicy.supportsIntervals("Practice", "Practice 2"))
        assertFalse(OpenF1SessionPolicy.supportsIntervals("Practice", "Práctica Libre 1"))
    }

    @Test
    fun qualifyingSessionsDoNotRequestRaceOnlyIntervals() {
        assertFalse(OpenF1SessionPolicy.supportsIntervals("Qualifying", "Qualifying"))
        assertFalse(OpenF1SessionPolicy.supportsIntervals("Qualifying", "Sprint Shootout"))
    }

    @Test
    fun raceAndSprintSessionsCanRequestIntervals() {
        assertTrue(OpenF1SessionPolicy.supportsIntervals("Race", "Race"))
        assertTrue(OpenF1SessionPolicy.supportsIntervals("Sprint", "Sprint"))
        assertTrue(OpenF1SessionPolicy.supportsIntervals(" race ", "Carrera"))
    }

    @Test
    fun httpResponsesAreClassifiedWithoutConfusingAuthAndRateLimits() {
        assertTrue(
            com.f1pitwall.tv.network.OpenF1AccessStatus.fromHttpStatus(401) ==
                com.f1pitwall.tv.network.OpenF1AccessStatus.AUTHENTICATION_REQUIRED
        )
        assertTrue(
            com.f1pitwall.tv.network.OpenF1AccessStatus.fromHttpStatus(403) ==
                com.f1pitwall.tv.network.OpenF1AccessStatus.AUTHENTICATION_REQUIRED
        )
        assertTrue(
            com.f1pitwall.tv.network.OpenF1AccessStatus.fromHttpStatus(429) ==
                com.f1pitwall.tv.network.OpenF1AccessStatus.RATE_LIMITED
        )
        assertTrue(
            com.f1pitwall.tv.network.OpenF1AccessStatus.fromHttpStatus(503) ==
                com.f1pitwall.tv.network.OpenF1AccessStatus.SERVICE_UNAVAILABLE
        )
    }
}
