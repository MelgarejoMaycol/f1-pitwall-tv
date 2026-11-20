package com.f1pitwall.tv.core.util

object BroadcastDetectorUtils {

    // Regex estricto para confirmación del idioma español (sin asumir ni adivinar)
    private val SPANISH_REGEX = Regex(
        """(?:\b(?:español|spanish|castellano)\b|\[(?:spanish|español|esp|es)\]|\((?:spanish|español|esp|es)\)|\b(?:esp|es)\b)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Identifica las transmisiones en español mediante etiquetas como Español, Spanish, ESP, ES
     * o identificadores equivalentes que realmente permitan confirmar el idioma.
     * Si no puede confirmar el idioma de una transmisión, no debe clasificarla como española por suposición.
     */
    fun isConfirmedSpanish(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return SPANISH_REGEX.containsMatchIn(text)
    }

    /**
     * Identifica, cuando esté disponible, el nombre de la cadena:
     * ESPN, DAZN, FOX Sports u otras que aparezcan.
     */
    fun detectNetwork(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        return when {
            lower.contains("dazn") -> "DAZN"
            lower.contains("fox sports") || lower.contains("foxsports") -> "FOX Sports"
            lower.contains("fox") -> "FOX Sports"
            lower.contains("espn premium") -> "ESPN Premium"
            lower.contains("espn 1") || lower.contains("espn1") -> "ESPN 1"
            lower.contains("espn 2") || lower.contains("espn2") -> "ESPN 2"
            lower.contains("espn 3") || lower.contains("espn3") -> "ESPN 3"
            lower.contains("espn 4") || lower.contains("espn4") -> "ESPN 4"
            lower.contains("espn extra") -> "ESPN Extra"
            lower.contains("espn") -> "ESPN"
            lower.contains("star+") || lower.contains("star +") -> "Star+"
            lower.contains("movistar") -> "Movistar"
            lower.contains("disney+") || lower.contains("disney +") -> "Disney+"
            lower.contains("sky sports") || lower.contains("skysports") -> "Sky Sports"
            lower.contains("f1 tv") || lower.contains("f1tv") -> "F1 TV"
            else -> null
        }
    }

    /**
     * Extrae información de calidad si existe (HD, SD, 4K, UHD)
     */
    fun detectQuality(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val upper = text.uppercase()
        return when {
            upper.contains("4K") -> "4K"
            upper.contains("UHD") -> "UHD"
            upper.contains("FHD") -> "FHD"
            upper.contains("HD") -> "HD"
            upper.contains("SD") -> "SD"
            else -> null
        }
    }
}
