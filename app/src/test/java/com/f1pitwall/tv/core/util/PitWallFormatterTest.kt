package com.f1pitwall.tv.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PitWallFormatterTest {

    @Test
    fun `formatSpeed returns numeric string when speed present and double dash when null`() {
        assertEquals("312", PitWallFormatter.formatSpeed(312))
        assertEquals("0", PitWallFormatter.formatSpeed(0))
        assertEquals("--", PitWallFormatter.formatSpeed(null))
    }

    @Test
    fun `formatGear returns gear number, N for 0 and double dash for null`() {
        assertEquals("7", PitWallFormatter.formatGear(7))
        assertEquals("1", PitWallFormatter.formatGear(1))
        assertEquals("N", PitWallFormatter.formatGear(0))
        assertEquals("--", PitWallFormatter.formatGear(null))
    }

    @Test
    fun `formatRpm formats with commas and returns placeholder when null`() {
        assertEquals("11,480 RPM", PitWallFormatter.formatRpm(11480))
        assertEquals("RPM: --", PitWallFormatter.formatRpm(null))
    }

    @Test
    fun `formatThrottle formats percentage and returns placeholder when null`() {
        assertEquals("Acelerador: 100%", PitWallFormatter.formatThrottle(100))
        assertEquals("Acelerador: 45%", PitWallFormatter.formatThrottle(45))
        assertEquals("Acelerador: --", PitWallFormatter.formatThrottle(null))
    }

    @Test
    fun `formatBrake formats binary 100 percent or 0 percent and placeholder when null`() {
        assertEquals("Freno: 100%", PitWallFormatter.formatBrake(100))
        assertEquals("Freno: 100%", PitWallFormatter.formatBrake(1))
        assertEquals("Freno: 0%", PitWallFormatter.formatBrake(0))
        assertEquals("Freno: --", PitWallFormatter.formatBrake(null))
    }

    @Test
    fun `resolveDrsState correctly resolves ACTIVE INACTIVE and UNAVAILABLE without guessing`() {
        assertEquals(DrsDisplayState.ACTIVE, PitWallFormatter.resolveDrsState(10))
        assertEquals(DrsDisplayState.ACTIVE, PitWallFormatter.resolveDrsState(12))
        assertEquals(DrsDisplayState.ACTIVE, PitWallFormatter.resolveDrsState(14))

        assertEquals(DrsDisplayState.INACTIVE, PitWallFormatter.resolveDrsState(0))
        assertEquals(DrsDisplayState.INACTIVE, PitWallFormatter.resolveDrsState(1))
        assertEquals(DrsDisplayState.INACTIVE, PitWallFormatter.resolveDrsState(8))
        assertEquals(DrsDisplayState.INACTIVE, PitWallFormatter.resolveDrsState(9))

        assertEquals(DrsDisplayState.UNAVAILABLE, PitWallFormatter.resolveDrsState(null))
        assertEquals(DrsDisplayState.UNAVAILABLE, PitWallFormatter.resolveDrsState(99))
    }

    @Test
    fun `formatTelemetrySessionStatus differentiates live from historical session`() {
        assertEquals("🔴 Telemetría en Vivo", PitWallFormatter.formatTelemetrySessionStatus(isLive = true, hasSession = true))
        assertEquals("🏁 Telemetría Histórica (Sesión Finalizada)", PitWallFormatter.formatTelemetrySessionStatus(isLive = false, hasSession = true))
        assertEquals("No disponible", PitWallFormatter.formatTelemetrySessionStatus(isLive = false, hasSession = false))
    }

    @Test
    fun `formatWeatherTemperature displays values or double dash without fabricating`() {
        assertEquals("Pista: 32.5°C | Aire: 24.0°C", PitWallFormatter.formatWeatherTemperature(32.5, 24.0))
        assertEquals("Pista: 32.0°C | Aire: --", PitWallFormatter.formatWeatherTemperature(32.0, null))
        assertEquals("Pista: -- | Aire: 20.0°C", PitWallFormatter.formatWeatherTemperature(null, 20.0))
        assertEquals("Pista: -- | Aire: --", PitWallFormatter.formatWeatherTemperature(null, null))
    }

    @Test
    fun `formatRadioTitle accurately reports recorded audio availability`() {
        assertEquals(
            "📻 Audio de Radio (Lando Norris #4): Grabación disponible",
            PitWallFormatter.formatRadioTitle("Lando Norris", 4, hasRecording = true)
        )
        assertEquals(
            "📻 Audio de Radio (Max Verstappen #1): Grabación no disponible",
            PitWallFormatter.formatRadioTitle("Max Verstappen", 1, hasRecording = false)
        )
    }
}
