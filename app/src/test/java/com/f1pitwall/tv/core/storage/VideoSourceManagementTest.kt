package com.f1pitwall.tv.core.storage

import com.f1pitwall.tv.core.config.VideoProviderItem
import com.f1pitwall.tv.core.config.VideoSourcesConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VideoSourceManagementTest {

    @Test
    fun `adding and updating sources in list preserves unique ids`() {
        val sources = mutableListOf<VideoProviderItem>()

        val source1 = VideoProviderItem(
            id = UUID.randomUUID().toString(),
            name = "Señal Principal",
            streamUrl = "https://server.com/live/hls.m3u8",
            type = "HLS",
            isDefault = true
        )
        sources.add(source1)

        val source2 = VideoProviderItem(
            id = UUID.randomUUID().toString(),
            name = "Cámara Onboard",
            streamUrl = "https://server.com/live/onboard.mpd",
            type = "DASH",
            isDefault = false
        )
        sources.add(source2)

        assertEquals(2, sources.size)

        // Editar source1: cambiar nombre, url y tipo conservando el mismo ID
        val updatedSource1 = source1.copy(
            name = "Señal Principal 4K Actualizada",
            streamUrl = "https://server.com/live/4k.m3u8",
            type = "HLS"
        )
        val index = sources.indexOfFirst { it.id == updatedSource1.id }
        assertTrue(index >= 0)
        sources[index] = updatedSource1

        assertEquals(2, sources.size)
        assertEquals("Señal Principal 4K Actualizada", sources[index].name)
        assertEquals("https://server.com/live/4k.m3u8", sources[index].streamUrl)
        assertEquals(source1.id, sources[index].id)
    }

    @Test
    fun `duplicate url detection identifies existing stream`() {
        val sources = listOf(
            VideoProviderItem(
                id = "id-1",
                name = "Canal 1",
                streamUrl = "https://cdn.example.com/live.m3u8",
                type = "HLS"
            )
        )

        val newUrlExact = "https://cdn.example.com/live.m3u8"
        val newUrlWithSpaces = "  https://cdn.example.com/live.m3u8  "
        val differentUrl = "https://cdn.example.com/other.m3u8"

        val duplicate1 = sources.find { it.streamUrl.trim().equals(newUrlExact.trim(), ignoreCase = true) }
        val duplicate2 = sources.find { it.streamUrl.trim().equals(newUrlWithSpaces.trim(), ignoreCase = true) }
        val notDuplicate = sources.find { it.streamUrl.trim().equals(differentUrl.trim(), ignoreCase = true) }

        assertNotNull("Debe detectar duplicado exacto", duplicate1)
        assertNotNull("Debe detectar duplicado con espacios", duplicate2)
        assertNull("No debe marcar como duplicada una URL diferente", notDuplicate)
    }

    @Test
    fun `selection fallback priority selects active then default then first`() {
        val s1 = VideoProviderItem("id-1", "Fuente 1", "https://s1.com", "HLS", isDefault = false)
        val s2 = VideoProviderItem("id-2", "Fuente 2 Default", "https://s2.com", "DASH", isDefault = true)
        val s3 = VideoProviderItem("id-3", "Fuente 3", "https://s3.com", "MP4", isDefault = false)

        val list = listOf(s1, s2, s3)

        // 1. Con activeId coincidente
        var activeId = "id-3"
        val selectedActive = list.find { it.id == activeId } ?: list.find { it.isDefault } ?: list.first()
        assertEquals("id-3", selectedActive.id)

        // 2. Con activeId no existente, cae en isDefault
        activeId = "non-existent"
        val selectedDefault = list.find { it.id == activeId } ?: list.find { it.isDefault } ?: list.first()
        assertEquals("id-2", selectedDefault.id)

        // 3. Sin default ni activeId existente, cae en first
        val listNoDefault = listOf(s1, s3)
        val selectedFirst = listNoDefault.find { it.id == activeId } ?: listNoDefault.find { it.isDefault } ?: listNoDefault.first()
        assertEquals("id-1", selectedFirst.id)
    }

    @Test
    fun `deleting active source falls back cleanly`() {
        val s1 = VideoProviderItem("id-1", "Fuente 1", "https://s1.com", "HLS")
        val s2 = VideoProviderItem("id-2", "Fuente 2", "https://s2.com", "DASH")

        var sources = listOf(s1, s2)
        var activeId = "id-1"

        // Eliminar la fuente activa
        sources = sources.filter { it.id != "id-1" }
        if (activeId == "id-1") {
            activeId = sources.firstOrNull()?.id ?: ""
        }

        assertEquals(1, sources.size)
        assertEquals("id-2", activeId)
    }

    @Test
    fun `smart search query generator creates relevant keywords for F1 sessions`() {
        val practiceQueries = VideoSourcesConfig.generateSmartSearchQueries("Las Vegas Grand Prix", "Practice 2")
        assertTrue(practiceQueries.any { it.contains("Las Vegas") })
        assertTrue(practiceQueries.any { it.contains("prácticas") })

        val qualifyingQueries = VideoSourcesConfig.generateSmartSearchQueries("Monaco Grand Prix", "Qualifying")
        assertTrue(qualifyingQueries.any { it.contains("Monaco") })
        assertTrue(qualifyingQueries.any { it.contains("clasificación") })

        val raceQueries = VideoSourcesConfig.generateSmartSearchQueries("Gran Premio de España", "Race")
        assertTrue(raceQueries.any { it.contains("España") })
        assertTrue(raceQueries.any { it.contains("carrera") })
    }

    @Test
    fun `parseProvidersFromJson supports both object with providers and raw json array`() {
        // Formato 1: Objeto con clave providers
        val objectJson = """
            {
              "providers": [
                {
                  "id": "prov-1",
                  "name": "Canal Objeto",
                  "streamUrl": "https://server.com/live1.m3u8",
                  "type": "HLS",
                  "isDefault": true
                }
              ]
            }
        """.trimIndent()

        val parsedObj = VideoSourcesConfig.parseProvidersFromJson(objectJson)
        assertEquals(1, parsedObj.size)
        assertEquals("prov-1", parsedObj[0].id)
        assertEquals("Canal Objeto", parsedObj[0].name)
        assertTrue(parsedObj[0].isDefault)

        // Formato 1B: Objeto con clave proveedores en español
        val objectSpanishJson = """
            {
              "proveedores": [
                {
                  "id": "f1_web",
                  "nombre": "F1 Web TV",
                  "url": "https://futbolvivo.tv/",
                  "tipo": "WEB",
                  "predeterminado": true
                }
              ]
            }
        """.trimIndent()

        val parsedSpanish = VideoSourcesConfig.parseProvidersFromJson(objectSpanishJson)
        assertEquals(1, parsedSpanish.size)
        assertEquals("f1_web", parsedSpanish[0].id)
        assertEquals("F1 Web TV", parsedSpanish[0].name)
        assertEquals("https://futbolvivo.tv/", parsedSpanish[0].streamUrl)
        assertEquals("WEB", parsedSpanish[0].type)
        assertTrue(parsedSpanish[0].isDefault)

        // Formato 2: Array crudo directamente pegado por el usuario en Secrets
        val arrayJson = """
            [
              {
                "id": "prov-2",
                "name": "Canal Array",
                "streamUrl": "https://server.com/live2.m3u8",
                "type": "DASH",
                "isDefault": false
              }
            ]
        """.trimIndent()

        val parsedArr = VideoSourcesConfig.parseProvidersFromJson(arrayJson)
        assertEquals(1, parsedArr.size)
        assertEquals("prov-2", parsedArr[0].id)
        assertEquals("Canal Array", parsedArr[0].name)
        assertFalse(parsedArr[0].isDefault)

        // Casos límite: Vacío o corrupto
        assertTrue(VideoSourcesConfig.parseProvidersFromJson("").isEmpty())
        assertTrue(VideoSourcesConfig.parseProvidersFromJson("{ invalid json }").isEmpty())
    }
}
