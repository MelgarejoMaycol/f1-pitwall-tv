package com.f1pitwall.tv

import com.f1pitwall.tv.data.model.RemoteBroadcastConfig
import com.f1pitwall.tv.data.model.RemoteChannelItem
import com.f1pitwall.tv.data.model.RemoteProviderItem
import com.f1pitwall.tv.model.FutbolLibreEvent
import com.f1pitwall.tv.model.SpanishBroadcast
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteBroadcastConfigTest {

    private val sampleJson = """
        {
          "schemaVersion": 1,
          "updatedAt": "2026-11-20T15:10:00Z",
          "providers": [
            {
              "id": "futbol_libre",
              "name": "Fútbol Libre — Agenda",
              "url": "https://futbol-libre.app/agenda",
              "type": "FUTBOL_LIBRE",
              "enabled": true,
              "order": 1
            },
            {
              "id": "flx",
              "name": "FLX",
              "url": "https://flive.cc.cd/f1",
              "type": "FLX",
              "enabled": true,
              "order": 2
            },
            {
              "id": "futbol_vivo",
              "name": "Fútbol Vivo",
              "url": "https://futbolvivo.tv/",
              "type": "FUTBOL_VIVO",
              "enabled": false,
              "order": 3
            }
          ],
          "channels": [
            {
              "id": "hls_stream_1",
              "name": "Señal HLS Principal",
              "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
              "type": "HLS",
              "enabled": true,
              "order": 1
            },
            {
              "id": "mp4_backup",
              "name": "Señal MP4 Backup",
              "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
              "type": "MP4",
              "enabled": true,
              "order": 2
            },
            {
              "id": "disabled_channel",
              "name": "Canal Deshabilitado",
              "url": "https://ejemplo.com/live.m3u8",
              "type": "HLS",
              "enabled": false,
              "order": 3
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testReadingAndParsingValidRemoteJson() {
        val config = Gson().fromJson(sampleJson, RemoteBroadcastConfig::class.java)
        assertNotNull(config)
        assertEquals(1, config.schemaVersion)
        assertEquals("2026-11-20T15:10:00Z", config.updatedAt)
        assertEquals(3, config.providers.size)
        assertEquals(3, config.channels.size)
        assertTrue(config.isValid())
    }

    @Test
    fun testChannelValidationValidUrlsAndTypes() {
        val config = RemoteBroadcastConfig(
            schemaVersion = 1,
            updatedAt = "2026-11-20T12:00:00Z",
            providers = listOf(
                RemoteProviderItem("p1", "Proveedor 1", "https://ejemplo.com/agenda", "FUTBOL_LIBRE", true, 1)
            ),
            channels = listOf(
                RemoteChannelItem("c1", "Canal HLS", "https://ejemplo.com/live.m3u8", "HLS", true, 1),
                RemoteChannelItem("c2", "Canal DASH", "https://ejemplo.com/manifest.mpd", "DASH", true, 2),
                RemoteChannelItem("c3", "Canal MP4", "http://ejemplo.com/video.mp4", "MP4", true, 3),
                RemoteChannelItem("c4", "Canal Web", "https://ejemplo.com/watch", "WEB", true, 4)
            )
        )
        assertTrue(config.isValid())
        assertEquals(4, config.getEnabledChannels().size)
    }

    @Test
    fun testChannelValidationRejectsInvalidUrlsAndScriptInjection() {
        val scriptChannel = RemoteBroadcastConfig(
            schemaVersion = 1,
            providers = listOf(RemoteProviderItem("p1", "P1", "https://ejemplo.com", "WEB", true, 1)),
            channels = listOf(
                RemoteChannelItem("c1", "Script Malicioso", "javascript:alert(1)", "WEB", true, 1)
            )
        )
        assertFalse(scriptChannel.isValid())

        val invalidTypeChannel = RemoteBroadcastConfig(
            schemaVersion = 1,
            providers = listOf(RemoteProviderItem("p1", "P1", "https://ejemplo.com", "WEB", true, 1)),
            channels = listOf(
                RemoteChannelItem("c1", "Tipo Inválido", "https://ejemplo.com/live", "RTMP_UNSUPPORTED", true, 1)
            )
        )
        assertFalse(invalidTypeChannel.isValid())
    }

    @Test
    fun testFilterEnabledAndDisabledChannels() {
        val config = Gson().fromJson(sampleJson, RemoteBroadcastConfig::class.java)
        val enabledChannels = config.getEnabledChannels()
        assertEquals(2, enabledChannels.size)
        assertTrue(enabledChannels.none { it.id == "disabled_channel" })
        assertEquals("hls_stream_1", enabledChannels[0].id)
        assertEquals("mp4_backup", enabledChannels[1].id)
    }

    @Test
    fun testFilterEnabledAndDisabledProviders() {
        val config = Gson().fromJson(sampleJson, RemoteBroadcastConfig::class.java)
        val enabledProviders = config.getEnabledProviders()
        assertEquals(2, enabledProviders.size)
        assertTrue(enabledProviders.none { it.id == "futbol_vivo" })
        assertEquals("futbol_libre", enabledProviders[0].id)
        assertEquals("flx", enabledProviders[1].id)
    }

    @Test
    fun testInvalidRemoteConfigRejection() {
        val corruptedJson = """{ "schemaVersion": 0, "providers": [], "channels": [] }"""
        val config = Gson().fromJson(corruptedJson, RemoteBroadcastConfig::class.java)
        assertFalse(config.isValid())
    }

    @Test
    fun testAddingChannelWithoutRecompilation() {
        // Simular adición de un nuevo canal publicado en GitHub
        val dynamicUpdatedJson = """
            {
              "schemaVersion": 1,
              "updatedAt": "2026-11-20T16:00:00Z",
              "providers": [
                { "id": "p1", "name": "P1", "url": "https://ejemplo.com", "type": "WEB", "enabled": true, "order": 1 }
              ],
              "channels": [
                { "id": "canal_existente", "name": "Canal 1", "url": "https://ejemplo.com/c1.m3u8", "type": "HLS", "enabled": true, "order": 1 },
                { "id": "nuevo_canal_remoto", "name": "Nuevo Canal Remoto F1", "url": "https://ejemplo.com/c2.m3u8", "type": "HLS", "enabled": true, "order": 2 }
              ]
            }
        """.trimIndent()

        val updatedConfig = Gson().fromJson(dynamicUpdatedJson, RemoteBroadcastConfig::class.java)
        assertTrue(updatedConfig.isValid())
        val channels = updatedConfig.getEnabledChannels()
        assertEquals(2, channels.size)
        assertEquals("nuevo_canal_remoto", channels[1].id)
        assertEquals("Nuevo Canal Remoto F1", channels[1].name)
    }

    @Test
    fun testDynamicChannelsMoreThanEightCanBeHandled() {
        // Simula la recepción de 12 canales dinámicos
        val twelveChannels = (1..12).map { i ->
            SpanishBroadcast(
                index = i,
                network = "Canal $i",
                language = "Español",
                serverName = "Servidor $i",
                originalTitle = "Opción $i",
                streamUrl = "https://ejemplo.com/match#source=$i",
                clickActionJs = "click($i);",
                sourceId = "$i",
                customFormattedTitle = "Transmisión $i — Canal $i",
                customButtonLabel = "T$i: C$i"
            )
        }
        assertEquals(12, twelveChannels.size)
        assertEquals("T1: C1", twelveChannels[0].getButtonLabel())
        assertEquals("T12: C12", twelveChannels[11].getButtonLabel())
    }

    @Test
    fun testSessionChannelIsolationInFutbolLibre() {
        val session1 = FutbolLibreEvent(
            id = "event_1",
            title = "GP Las Vegas - Práctica 1",
            category = "Fórmula 1",
            sessionName = "Práctica 1",
            gpName = "Las Vegas",
            utcInstant = "2026-11-20T19:30:00Z",
            colombiaTime = "02:30 PM",
            matchPath = "/match/las-vegas-p1",
            fullMatchUrl = "https://futbol-libre.app/match/las-vegas-p1",
            sources = listOf(
                SpanishBroadcast(
                    index = 1,
                    network = "ESPN",
                    language = "Español",
                    serverName = "S1",
                    originalTitle = "ESPN",
                    streamUrl = "https://futbol-libre.app/match/las-vegas-p1#source=1",
                    isOnline = true,
                    clickActionJs = null,
                    sourceId = "1",
                    customFormattedTitle = "",
                    customButtonLabel = "T1: ESPN"
                ),
                SpanishBroadcast(
                    index = 2,
                    network = "Fox",
                    language = "Español",
                    serverName = "S2",
                    originalTitle = "Fox",
                    streamUrl = "https://futbol-libre.app/match/las-vegas-p1#source=2",
                    isOnline = true,
                    clickActionJs = null,
                    sourceId = "2",
                    customFormattedTitle = "",
                    customButtonLabel = "T2: Fox"
                )
            )
        )

        val session2 = FutbolLibreEvent(
            id = "event_2",
            title = "GP Las Vegas - Clasificación",
            category = "Fórmula 1",
            sessionName = "Clasificación",
            gpName = "Las Vegas",
            utcInstant = "2026-11-20T23:00:00Z",
            colombiaTime = "06:00 PM",
            matchPath = "/match/las-vegas-quali",
            fullMatchUrl = "https://futbol-libre.app/match/las-vegas-quali",
            sources = listOf(
                SpanishBroadcast(
                    index = 1,
                    network = "DAZN F1",
                    language = "Español",
                    serverName = "S1",
                    originalTitle = "DAZN",
                    streamUrl = "https://futbol-libre.app/match/las-vegas-quali#source=101",
                    isOnline = true,
                    clickActionJs = null,
                    sourceId = "101",
                    customFormattedTitle = "",
                    customButtonLabel = "T1: DAZN F1"
                ),
                SpanishBroadcast(
                    index = 2,
                    network = "Disney+",
                    language = "Español",
                    serverName = "S2",
                    originalTitle = "Disney+",
                    streamUrl = "https://futbol-libre.app/match/las-vegas-quali#source=102",
                    isOnline = true,
                    clickActionJs = null,
                    sourceId = "102",
                    customFormattedTitle = "",
                    customButtonLabel = "T2: Disney+"
                ),
                SpanishBroadcast(
                    index = 3,
                    network = "ESPN 2",
                    language = "Español",
                    serverName = "S3",
                    originalTitle = "ESPN 2",
                    streamUrl = "https://futbol-libre.app/match/las-vegas-quali#source=103",
                    isOnline = true,
                    clickActionJs = null,
                    sourceId = "103",
                    customFormattedTitle = "",
                    customButtonLabel = "T3: ESPN 2"
                )
            )
        )

        assertEquals(2, session1.sources.size)
        assertEquals(3, session2.sources.size)
        // Canales completamente aislados entre partidos
        assertNotEquals(session1.sources[0].sourceId, session2.sources[0].sourceId)
        assertEquals("1", session1.sources[0].sourceId)
        assertEquals("101", session2.sources[0].sourceId)
        assertFalse(session1.sources.any { it.sourceId == "103" })
    }
}
