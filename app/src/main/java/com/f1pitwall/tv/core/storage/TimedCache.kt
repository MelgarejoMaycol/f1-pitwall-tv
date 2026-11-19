package com.f1pitwall.tv.core.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache with configurable expiration time (TTL).
 * Supports graceful fallback to stale cache on network failure.
 */
class TimedCache<K : Any, V : Any>(
    private val defaultTtlMillis: Long = DEFAULT_TTL_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DEFAULT_TTL_MS: Long = 15 * 60 * 1000L // 15 minutos
        const val LONG_TTL_MS: Long = 2 * 60 * 60 * 1000L // 2 horas (temporadas pasadas)
        const val SHORT_TTL_MS: Long = 60 * 1000L // 1 minuto (datos dinámicos de sesión)
    }

    private data class Entry<V>(
        val value: V,
        val timestamp: Long,
        val ttl: Long
    ) {
        fun isExpired(now: Long): Boolean = (now - timestamp) > ttl
    }

    private val cache = ConcurrentHashMap<K, Entry<V>>()

    /**
     * Retorna el valor si existe y no ha expirado. Si ha expirado, retorna null
     * pero preserva el valor para recuperación mediante getStale().
     */
    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        return if (entry.isExpired(timeProvider())) {
            null
        } else {
            entry.value
        }
    }

    /**
     * Retorna el valor en caché incluso si ha expirado (útil como mecanismo de rescate offline).
     */
    fun getStale(key: K): V? {
        return cache[key]?.value
    }

    /**
     * Almacena un valor con un TTL específico o el predeterminado.
     */
    fun put(key: K, value: V, customTtlMillis: Long = defaultTtlMillis) {
        cache[key] = Entry(value, timeProvider(), customTtlMillis)
    }

    /**
     * Elimina una clave de la caché.
     */
    fun remove(key: K): V? {
        return cache.remove(key)?.value
    }

    /**
     * Limpia completamente la caché.
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Retorna la cantidad de elementos almacenados en la caché.
     */
    fun size(): Int = cache.size
}
