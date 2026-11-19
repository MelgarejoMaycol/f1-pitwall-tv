package com.f1pitwall.tv.ui.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F1ContentDiscoveryTest {

    @Test
    fun testParseF1Events() {
        val sampleJson = """
            [
                {
                    "title": "Las Vegas Grand Prix vs Practice 1",
                    "league": "Formula 1",
                    "url": "https://golygoals.com/latin/soccer.html?m=1059",
                    "score": 100,
                    "session": "Practice 1"
                },
                {
                    "title": "Formula 1 2026 - Las Vegas GP",
                    "league": "Motorsport",
                    "url": "https://golygoals.com/latin/us.html?id=2795",
                    "score": 60,
                    "session": "Evento"
                }
            ]
        """.trimIndent()

        val events = F1ContentDiscovery.parseF1Events(sampleJson)
        assertEquals(2, events.size)

        assertEquals("Las Vegas Grand Prix vs Practice 1", events[0].title)
        assertEquals("Formula 1", events[0].league)
        assertEquals("https://golygoals.com/latin/soccer.html?m=1059", events[0].url)
        assertEquals(100, events[0].score)
        assertEquals("Practice 1", events[0].session)

        assertEquals("Formula 1 2026 - Las Vegas GP", events[1].title)
        assertEquals(60, events[1].score)
    }

    @Test
    fun testParseEventPageChannels() {
        val sampleJson = """
            {
                "title": "Las Vegas Grand Prix vs Practice 1",
                "channels": [
                    {"name": "Live 1", "index": 0, "type": "TAB", "url": ""},
                    {"name": "Live 2", "index": 1, "type": "TAB", "url": ""},
                    {"name": "Live 3", "index": 2, "type": "TAB", "url": ""}
                ]
            }
        """.trimIndent()

        val (title, channels) = F1ContentDiscovery.parseEventPageChannels(sampleJson)
        assertEquals("Las Vegas Grand Prix vs Practice 1", title)
        assertEquals(3, channels.size)

        assertEquals("Live 1", channels[0].name)
        assertEquals(0, channels[0].index)

        assertEquals("Live 2", channels[1].name)
        assertEquals(1, channels[1].index)

        assertEquals("Live 3", channels[2].name)
        assertEquals(2, channels[2].index)
    }

    @Test
    fun testBuildSwitchChannelJs() {
        val js0 = F1ContentDiscovery.buildSwitchChannelJs(0)
        assertTrue(js0.contains("tabs[0].click()"))

        val js2 = F1ContentDiscovery.buildSwitchChannelJs(2)
        assertTrue(js2.contains("tabs[2].click()"))
    }
}
