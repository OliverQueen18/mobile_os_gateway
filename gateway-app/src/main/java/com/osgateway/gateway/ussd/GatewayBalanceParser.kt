package com.osgateway.gateway.ussd

import com.osgateway.gateway.sms.SmsNormalizer
import com.osgateway.shared.model.BalancePattern
import java.util.Locale

/**
 * Parse le solde « Principal » (float MM gateway) depuis le dialogue USSD ou le SMS opérateur.
 * Les motifs regex sont paramétrables par opérateur (table operator_balance_patterns).
 */
object GatewayBalanceParser {

    data class ParsedBalance(
        val principal: Long,
        val bonusUv: Long? = null,
    )

    /** Repli si aucun motif en base (Orange Mali typique). */
    private val DEFAULT_PRINCIPAL = Regex(
        """(?i)(?:(?:le\s+)?solde\s+de\s+votre\s+)?principal\s*:?\s*([0-9][0-9\s.,\u00A0]*)""",
    )
    private val DEFAULT_BONUS_UV = Regex(
        """(?i)bonus\s*uv\s*:?\s*([0-9][0-9\s.,\u00A0]*)""",
    )

    fun parse(text: String, patterns: List<BalancePattern> = emptyList(), operator: String? = null): ParsedBalance? {
        val principal = parsePrincipal(text, patterns, operator) ?: return null
        val bonus = parseBonusUv(text, patterns)
        return ParsedBalance(principal = principal, bonusUv = bonus)
    }

    fun parsePrincipal(
        text: String,
        patterns: List<BalancePattern> = emptyList(),
        @Suppress("UNUSED_PARAMETER") operator: String? = null,
    ): Long? {
        if (text.isBlank()) return null
        val norm = normalizeBalanceText(text)
        if (norm.isBlank()) return null
        return matchByPatterns(norm, patterns, "PRINCIPAL")
            ?: matchRegex(norm, DEFAULT_PRINCIPAL)
    }

    fun parseBonusUv(text: String, patterns: List<BalancePattern> = emptyList()): Long? {
        val norm = normalizeBalanceText(text)
        return matchByPatterns(norm, patterns, "BONUS_UV")
            ?: matchRegex(norm, DEFAULT_BONUS_UV)
    }

    private fun matchByPatterns(norm: String, patterns: List<BalancePattern>, fieldType: String): Long? {
        if (patterns.isEmpty()) return null
        val ordered = patterns
            .filter { it.fieldType.equals(fieldType, ignoreCase = true) }
            .sortedBy { it.priority }
        for (pattern in ordered) {
            val amount = matchRegex(norm, pattern.regexPattern) ?: continue
            if (amount in 0..999_999_999) return amount
        }
        return null
    }

    private fun matchRegex(norm: String, pattern: String): Long? {
        return try {
            val m = Regex(pattern, RegexOption.IGNORE_CASE).find(norm) ?: return null
            parseAmount(m.groupValues.getOrNull(1))
        } catch (_: Exception) {
            null
        }
    }

    private fun matchRegex(norm: String, regex: Regex): Long? {
        val m = regex.find(norm) ?: return null
        return parseAmount(m.groupValues.getOrNull(1))
    }

    private fun normalizeBalanceText(raw: String): String {
        val withNewlines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
        return SmsNormalizer.stripAccents(withNewlines.lowercase(Locale.ROOT))
    }

    private fun parseAmount(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val compact = raw
            .replace(Regex("[\\s\u00A0]"), "")
            .replace(',', '.')
            .trim()
        val asDouble = compact.toDoubleOrNull() ?: return null
        return Math.round(asDouble)
    }
}
