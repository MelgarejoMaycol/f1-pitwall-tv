package com.f1pitwall.tv.core.storage

import org.junit.Assert.*
import org.junit.Test

class TimedCacheTest {

    @Test
    fun `put and get returns value within ttl`() {
        var currentTime = 1000L
        val cache = TimedCache<String, String>(
            defaultTtlMillis = 5000L,
            timeProvider = { currentTime }
        )
        cache.put("key1", "value1")

        assertEquals("value1", cache.get("key1"))
        assertEquals("value1", cache.getStale("key1"))
    }

    @Test
    fun `expired entry returns null on get but value on getStale`() {
        var currentTime = 1000L
        val cache = TimedCache<String, String>(
            defaultTtlMillis = 500L,
            timeProvider = { currentTime }
        )
        cache.put("key1", "value1", customTtlMillis = 200L)

        // Advance time past TTL
        currentTime += 300L

        assertNull(cache.get("key1"))
        assertEquals("value1", cache.getStale("key1"))
    }

    @Test
    fun `remove and clear operate correctly`() {
        val cache = TimedCache<Int, String>()
        cache.put(2024, "Temporada 2024")
        cache.put(2025, "Temporada 2025")

        assertEquals(2, cache.size())
        assertEquals("Temporada 2024", cache.remove(2024))
        assertEquals(1, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get(2025))
    }
}
