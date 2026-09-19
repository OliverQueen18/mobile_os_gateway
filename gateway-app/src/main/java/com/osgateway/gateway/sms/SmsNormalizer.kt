package com.osgateway.gateway.sms

/**
 * Normalise un SMS opérateur pour comparaison / parsing robuste (accents, casse, espaces).
 */
object SmsNormalizer {

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val collapsed = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        return stripAccents(collapsed)
    }

    fun stripAccents(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "")
    }
}
