package com.f1pitwall.tv.ui.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-based unit tests for player states, error mapping, and radio status transitions.
 */
class PlaybackAndRadioStateTest {

    @Test
    fun `video playback error messages map correctly to user friendly messages`() {
        val networkMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        assertTrue(networkMsg.contains("Error de conexión"))

        val timeoutMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
        assertTrue(timeoutMsg.contains("Error de conexión"))

        val httpErrorMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertTrue(httpErrorMsg.contains("Fuente no disponible"))

        val notFoundMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        assertTrue(notFoundMsg.contains("Fuente no disponible"))

        val malformedMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )
        assertTrue(malformedMsg.contains("Formato incompatible"))

        val fallbackMsg = VideoPlaybackManager.resolvePlaybackErrorMessage(
            99999,
            "Custom codec error"
        )
        assertEquals("Error de reproducción: Custom codec error", fallbackMsg)
    }

    @Test
    fun `radio status formatting reflects player state accurately without fabricating connection`() {
        val buffering = RadioManager.formatRadioPlaybackTitle(
            driverName = "Charles Leclerc",
            driverNumber = 16,
            playbackState = Player.STATE_BUFFERING,
            isPlaying = false
        )
        assertEquals("📻 Audio de Radio (Charles Leclerc #16): Preparando reproducción...", buffering)

        val playing = RadioManager.formatRadioPlaybackTitle(
            driverName = "Charles Leclerc",
            driverNumber = 16,
            playbackState = Player.STATE_READY,
            isPlaying = true
        )
        assertEquals("📻 Audio de Radio (Charles Leclerc #16): Reproduciendo grabación", playing)

        val paused = RadioManager.formatRadioPlaybackTitle(
            driverName = "Charles Leclerc",
            driverNumber = 16,
            playbackState = Player.STATE_READY,
            isPlaying = false
        )
        assertEquals("📻 Audio de Radio (Charles Leclerc #16): Pausado", paused)

        val ended = RadioManager.formatRadioPlaybackTitle(
            driverName = "Charles Leclerc",
            driverNumber = 16,
            playbackState = Player.STATE_ENDED,
            isPlaying = false
        )
        assertEquals("📻 Audio de Radio (Charles Leclerc #16): Grabación finalizada", ended)
    }

    @Test
    fun `radio status handles missing driver details gracefully with defaults`() {
        val unknownBuffering = RadioManager.formatRadioPlaybackTitle(
            driverName = null,
            driverNumber = null,
            playbackState = Player.STATE_BUFFERING,
            isPlaying = false
        )
        assertEquals("📻 Audio de Radio (Piloto): Preparando reproducción...", unknownBuffering)
    }
}
