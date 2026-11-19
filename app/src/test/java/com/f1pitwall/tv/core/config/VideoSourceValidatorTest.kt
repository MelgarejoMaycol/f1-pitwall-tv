package com.f1pitwall.tv.core.config

import org.junit.Assert.*
import org.junit.Test

class VideoSourceValidatorTest {

    @Test
    fun `valid http and https urls pass validation`() {
        val validUrls = listOf(
            "https://servidor.com/live/stream.m3u8",
            "http://192.168.1.100:8080/f1/manifest.mpd",
            "https://cdn.example.org/feed.mp4?token=abc123xyz&expires=1790000000",
            "http://my-tv-box.local/stream.m3u8#track=1"
        )

        for (url in validUrls) {
            val result = VideoSourceValidator.validateUrl(url)
            assertTrue("URL $url debería ser válida", result is VideoSourceValidator.ValidationResult.Valid)
        }
    }

    @Test
    fun `empty or blank urls are rejected`() {
        val emptyUrls = listOf("", "   ", null)

        for (url in emptyUrls) {
            val result = VideoSourceValidator.validateUrl(url)
            assertTrue("URL vacía '$url' debe ser rechazada", result is VideoSourceValidator.ValidationResult.Invalid)
        }
    }

    @Test
    fun `forbidden and unsafe schemes are strictly rejected`() {
        val unsafeUrls = listOf(
            "file:///sdcard/video.mp4",
            "javascript:alert(1)",
            "content://media/external/video/media/1",
            "ftp://ftp.server.com/stream.m3u8",
            "data:text/html;base64,PHNjcmlwdD4=",
            "intent://host#Intent;scheme=something;end",
            "rtsp://wowza.example.com/live"
        )

        for (url in unsafeUrls) {
            val result = VideoSourceValidator.validateUrl(url)
            assertTrue("Esquema no permitido '$url' debe ser rechazado", result is VideoSourceValidator.ValidationResult.Invalid)
            val invalid = result as VideoSourceValidator.ValidationResult.Invalid
            assertTrue(invalid.reason.contains("HTTP o HTTPS"))
        }
    }

    @Test
    fun `urls without host or with malformed syntax are rejected`() {
        val malformedUrls = listOf(
            "https://",
            "http://",
            "http:///stream.m3u8",
            "://invalid.url",
            "not a url at all"
        )

        for (url in malformedUrls) {
            val result = VideoSourceValidator.validateUrl(url)
            assertTrue("URL mal formada '$url' debe ser rechazada", result is VideoSourceValidator.ValidationResult.Invalid)
        }
    }

    @Test
    fun `valid names pass validation`() {
        val result = VideoSourceValidator.validateName("Transmisión Oficial DAZN 4K")
        assertTrue(result is VideoSourceValidator.ValidationResult.Valid)
    }

    @Test
    fun `empty or overly long names are rejected`() {
        assertTrue(VideoSourceValidator.validateName("") is VideoSourceValidator.ValidationResult.Invalid)
        assertTrue(VideoSourceValidator.validateName("   ") is VideoSourceValidator.ValidationResult.Invalid)
        assertTrue(VideoSourceValidator.validateName(null) is VideoSourceValidator.ValidationResult.Invalid)

        val overlyLongName = "A".repeat(61)
        val result = VideoSourceValidator.validateName(overlyLongName)
        assertTrue(result is VideoSourceValidator.ValidationResult.Invalid)
    }

    @Test
    fun `stream type normalization and detection from url`() {
        // Normalización explícita
        assertEquals("WEB", VideoSourceValidator.normalizeType("WEB"))
        assertEquals("WEB", VideoSourceValidator.normalizeType("web"))
        assertEquals("WEB", VideoSourceValidator.normalizeType("html"))
        assertEquals("HLS", VideoSourceValidator.normalizeType("HLS"))
        assertEquals("HLS", VideoSourceValidator.normalizeType("hls"))
        assertEquals("DASH", VideoSourceValidator.normalizeType("DASH"))
        assertEquals("DASH", VideoSourceValidator.normalizeType("mpd"))
        assertEquals("MP4", VideoSourceValidator.normalizeType("MP4"))
        assertEquals("MP4", VideoSourceValidator.normalizeType("mp4"))
        assertEquals("HLS", VideoSourceValidator.normalizeType("UNKNOWN"))
        assertEquals("HLS", VideoSourceValidator.normalizeType(null))

        // Detección por extensión en URL (con query parameters y fragments)
        assertEquals("HLS", VideoSourceValidator.detectTypeFromUrl("https://example.com/live/index.m3u8?token=xyz#track1"))
        assertEquals("DASH", VideoSourceValidator.detectTypeFromUrl("https://example.com/stream/manifest.mpd?auth=123"))
        assertEquals("MP4", VideoSourceValidator.detectTypeFromUrl("https://example.com/videos/onboard.mp4?dl=1"))
        assertEquals("WEB", VideoSourceValidator.detectTypeFromUrl("https://futbolvivo.tv/"))
        assertEquals("WEB", VideoSourceValidator.detectTypeFromUrl("https://futbolvivo.tv"))
        assertEquals("WEB", VideoSourceValidator.detectTypeFromUrl("https://sitio.com/transmision.html"))
        assertEquals("HLS", VideoSourceValidator.detectTypeFromUrl("https://example.com/custom/live_stream"))
    }
}
