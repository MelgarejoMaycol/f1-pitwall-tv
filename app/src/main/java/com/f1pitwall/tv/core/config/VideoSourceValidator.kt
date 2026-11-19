package com.f1pitwall.tv.core.config

import java.net.URI

object VideoSourceValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Valida que el nombre de la fuente de video sea descriptivo y no esté vacío.
     */
    fun validateName(name: String?): ValidationResult {
        if (name.isNullOrBlank()) {
            return ValidationResult.Invalid("El nombre de la fuente no puede estar vacío.")
        }
        val trimmed = name.trim()
        if (trimmed.length > 60) {
            return ValidationResult.Invalid("El nombre no debe superar los 60 caracteres.")
        }
        return ValidationResult.Valid
    }

    /**
     * Valida que la URL de transmisión sea una dirección web válida con esquema HTTP o HTTPS
     * y con un host o dominio definido. Rechaza esquemas inseguros o no permitidos.
     */
    fun validateUrl(rawUrl: String?): ValidationResult {
        if (rawUrl.isNullOrBlank()) {
            return ValidationResult.Invalid("La URL de la transmisión no puede estar vacía.")
        }
        val trimmed = rawUrl.trim()

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return ValidationResult.Invalid("La URL tiene un formato no válido.")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null || (scheme != "http" && scheme != "https")) {
            return ValidationResult.Invalid("Solo se permiten transmisiones web con protocolo HTTP o HTTPS.")
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return ValidationResult.Invalid("La URL debe incluir un servidor o dominio válido.")
        }

        return ValidationResult.Valid
    }

    /**
     * Normaliza el tipo de transmisión a los formatos soportados: WEB, HLS, DASH o MP4.
     */
    fun normalizeType(type: String?): String {
        return when (type?.uppercase()?.trim()) {
            "WEB", "HTML", "BROWSER", "PAGINA", "PÁGINA" -> "WEB"
            "DASH", "MPD" -> "DASH"
            "MP4" -> "MP4"
            else -> "HLS"
        }
    }

    /**
     * Detecta el tipo de transmisión basándose en la extensión del archivo o la estructura de la URL.
     * Si la URL es una página web raíz (ej. https://futbolvivo.tv/), retorna WEB.
     * Por defecto retorna HLS si hay una ruta sin extensión concluyente.
     */
    fun detectTypeFromUrl(url: String): String {
        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase().trim()
        val path = try { URI(cleanUrl).path?.lowercase() ?: "" } catch (_: Exception) { cleanUrl }
        return when {
            cleanUrl.endsWith(".mpd") -> "DASH"
            cleanUrl.endsWith(".mp4") -> "MP4"
            cleanUrl.endsWith(".m3u8") -> "HLS"
            cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm") -> "WEB"
            path.isEmpty() || path == "/" -> "WEB"
            else -> "HLS"
        }
    }
}
