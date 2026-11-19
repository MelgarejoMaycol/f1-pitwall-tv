package com.f1pitwall.tv.core.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object TimeUtils {

    private const val COLOMBIA_TIMEZONE = "America/Bogota"

    fun parseUtc(isoDate: String?): Date? {
        if (isoDate.isNullOrBlank()) return null
        return try {
            val clean = isoDate.trim()
            if (clean.length >= 19) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(clean.substring(0, 19))
            } else if (clean.length >= 10) {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(clean.substring(0, 10))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatToColombiaTime(isoDate: String?, pattern: String = "EEEE, d 'de' MMMM • hh:mm a"): String {
        val date = parseUtc(isoDate) ?: return "Horario por confirmar"
        return try {
            val format = SimpleDateFormat(pattern, Locale("es", "CO"))
            format.timeZone = TimeZone.getTimeZone(COLOMBIA_TIMEZONE)
            format.format(date).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Horario por confirmar"
        }
    }

    fun formatColombiaDateRange(startIso: String?, endIso: String?): String {
        val start = parseUtc(startIso) ?: return "Fechas por confirmar"
        val end = parseUtc(endIso)
        return try {
            val dayMonthFormat = SimpleDateFormat("d 'de' MMMM", Locale("es", "CO"))
            val yearFormat = SimpleDateFormat("yyyy", Locale("es", "CO"))
            dayMonthFormat.timeZone = TimeZone.getTimeZone(COLOMBIA_TIMEZONE)
            yearFormat.timeZone = TimeZone.getTimeZone(COLOMBIA_TIMEZONE)

            if (end != null) {
                "${dayMonthFormat.format(start)} al ${dayMonthFormat.format(end)} de ${yearFormat.format(end)}"
            } else {
                "${dayMonthFormat.format(start)} de ${yearFormat.format(start)}"
            }
        } catch (e: Exception) {
            "Fechas por confirmar"
        }
    }

    fun calculateCountdown(isoDate: String?, now: Date = Date()): String {
        val target = parseUtc(isoDate) ?: return ""
        val diffMs = target.time - now.time

        if (diffMs <= 0) return "Comenzando ahora"

        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60

        return when {
            days > 0 -> "Faltan $days días, $hours hrs y $minutes min"
            hours > 0 -> "Faltan $hours horas y $minutes minutos"
            else -> "Faltan $minutes minutos para el inicio"
        }
    }

    fun isCurrentlyLive(startIso: String?, endIso: String?, now: Date = Date()): Boolean {
        val start = parseUtc(startIso) ?: return false
        val end = parseUtc(endIso) ?: return false
        return now.time >= start.time && now.time <= end.time
    }

    fun isCompleted(endIso: String?, now: Date = Date()): Boolean {
        val end = parseUtc(endIso) ?: return false
        return now.time > end.time
    }

    fun isUpcoming(startIso: String?, now: Date = Date()): Boolean {
        val start = parseUtc(startIso) ?: return false
        return now.time < start.time
    }
}
