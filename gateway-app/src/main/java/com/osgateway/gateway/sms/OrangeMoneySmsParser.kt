package com.osgateway.gateway.sms

import com.osgateway.gateway.ussd.GatewayBalanceParser
import com.osgateway.shared.model.BalancePattern
import java.util.Locale

/**
 * Parseur SMS opérateur multi-types (Orange Money et assimilés).
 *
 * Types couverts (codes OS Gateway) :
 * - RETRAIT, DEPOT, TRANSFERT, PAIEMENT, ACHAT_CREDIT, ACHAT_UV, SOLDE
 *
 * Source de vérité pour WAIT_SMS. Les motifs template restent un repli
 * (voir UssdSessionController / PendingSmsConfirmationRegistry).
 */
object OrangeMoneySmsParser {

    enum class OutcomeKind { SUCCESS, FAILED, IGNORED }

    data class ParsedSms(
        val kind: OutcomeKind,
        /** Code normalisé OS Gateway : RETRAIT, DEPOT, TRANSFERT, … */
        val type: String? = null,
        val amount: Long? = null,
        val customerPhone: String? = null,
        val orangeTransactionId: String? = null,
        val normalizedBody: String,
        val rawBody: String,
    )

    data class CorrelationContext(
        val amount: Double?,
        val customerPhone: String?,
        val operationType: String? = "RETRAIT",
    )

    /**
     * Familles de mots-clés → type d'opération.
     * Ordre = priorité de détection (premier match gagne).
     */
    private data class OpFamily(
        val code: String,
        val keywords: List<String>,
        /** Téléphone client attendu pour corrélation stricte. */
        val requiresPhone: Boolean = true,
    )

    private val OP_FAMILIES = listOf(
        OpFamily("RETRAIT", listOf("retrait", "withdrawal")),
        OpFamily("DEPOT", listOf("depot", "deposit")),
        OpFamily(
            "TRANSFERT",
            listOf("transfert", "transfer", "envoi de", "envoi d"),
        ),
        OpFamily(
            "PAIEMENT",
            listOf("paiement", "payment", "marchand", "merchant", "facture"),
            requiresPhone = false,
        ),
        OpFamily(
            "ACHAT_CREDIT",
            listOf("achat credit", "credit telephonique", "airtime", "recharge credit", "forfait"),
            requiresPhone = false,
        ),
        // SOLDE avant ACHAT_UV : « Bonus UV » ne doit pas classer une consultation solde
        OpFamily(
            "SOLDE",
            listOf("solde de votre", "votre principal", "principal :", "principal:", "bonus uv", "nouveau solde"),
            requiresPhone = false,
        ),
        OpFamily(
            "ACHAT_UV",
            listOf("achat uv", "recharge uv", "achat de uv"),
            requiresPhone = false,
        ),
        // Repli solde générique
        OpFamily(
            "SOLDE",
            listOf("solde", "balance", "principal"),
            requiresPhone = false,
        ),
    )

    private val SUCCESS_VERBS = Regex(
        """(?:a\s+ete\s+)?(?:effectue|effectuee|reussi|reussie|succes|envoye|confirmee?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FAILURE_VERBS = Regex(
        """(?:annul\w*|echec|echoue\w*|refuse\w*|insuffisant|impossible|incorrect|delais|non\s+confirme|expire\w*)""",
        RegexOption.IGNORE_CASE,
    )

    private val ORANGE_ID = Regex(
        """(?:id\s*(?:de\s+tran[sf]ert)?\s*[:：]?\s*)([a-z]{1,4}\d{4,}(?:\.[\w]+)+)""",
        RegexOption.IGNORE_CASE,
    )

    private val AMOUNT = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:fcfa|f\s*cfa|xof|cfa)?""",
        RegexOption.IGNORE_CASE,
    )

    private val PHONE_NEAR = Regex(
        """(?:sur|au|a|vers|pour|du|de|beneficiaire|client|numero|n[°o])\s+(?:le\s+|au\s+)?(\d{6,15})""",
        RegexOption.IGNORE_CASE,
    )

    private val PHONE_ANY = Regex("""(\d{8,15})""")

    private val PROCESSED_KEYS = LinkedHashSet<String>()

    fun resetProcessedForTests() {
        synchronized(PROCESSED_KEYS) { PROCESSED_KEYS.clear() }
    }

    fun markProcessed(key: String): Boolean {
        synchronized(PROCESSED_KEYS) {
            if (PROCESSED_KEYS.contains(key)) return false
            PROCESSED_KEYS.add(key)
            while (PROCESSED_KEYS.size > 200) {
                val first = PROCESSED_KEYS.iterator().next()
                PROCESSED_KEYS.remove(first)
            }
            return true
        }
    }

    fun duplicateKey(address: String, body: String, timestamp: Long): String =
        "${timestamp}|${address.trim()}|${SmsNormalizer.normalize(body)}"

    fun parseBalanceFromSms(
        rawBody: String,
        operator: String? = null,
        patterns: List<BalancePattern> = emptyList(),
    ): Long? = GatewayBalanceParser.parsePrincipal(rawBody, patterns, operator)

    fun parse(rawBody: String): ParsedSms {
        val normalized = SmsNormalizer.normalize(rawBody)
        if (normalized.isBlank()) {
            return ParsedSms(OutcomeKind.IGNORED, normalizedBody = normalized, rawBody = rawBody)
        }

        val opCode = detectOperationType(normalized)
        val amount = extractAmount(normalized, opCode)
        val phone = extractPhone(normalized)
        val orangeId = ORANGE_ID.find(normalized)?.groupValues?.getOrNull(1)?.uppercase()

        val isFailure = FAILURE_VERBS.containsMatchIn(normalized)
        val isSuccess = !isFailure && SUCCESS_VERBS.containsMatchIn(normalized)

        // SOLDE : pas de verbe succès obligatoire — le montant Principal suffit
        if (opCode == "SOLDE") {
            val balance = GatewayBalanceParser.parsePrincipal(rawBody)
            if (balance != null) {
                return ParsedSms(
                    kind = OutcomeKind.SUCCESS,
                    type = "SOLDE",
                    amount = balance,
                    customerPhone = phone,
                    orangeTransactionId = orangeId,
                    normalizedBody = normalized,
                    rawBody = rawBody,
                )
            }
        }

        if (isFailure && (opCode != null || amount != null || FAILURE_VERBS.containsMatchIn(normalized))) {
            return ParsedSms(
                kind = OutcomeKind.FAILED,
                type = opCode,
                amount = amount,
                customerPhone = phone,
                orangeTransactionId = orangeId,
                normalizedBody = normalized,
                rawBody = rawBody,
            )
        }

        if (isSuccess && opCode != null) {
            return ParsedSms(
                kind = OutcomeKind.SUCCESS,
                type = opCode,
                amount = amount,
                customerPhone = phone,
                orangeTransactionId = orangeId,
                normalizedBody = normalized,
                rawBody = rawBody,
            )
        }

        // Succès structurel : type + montant (+ téléphone si requis)
        if (opCode != null && amount != null && opCode != "SOLDE") {
            val family = OP_FAMILIES.first { it.code == opCode }
            if ((!family.requiresPhone || phone != null) &&
                hasOperationKeywordNearAmount(normalized, family)
            ) {
                return ParsedSms(
                    kind = OutcomeKind.SUCCESS,
                    type = opCode,
                    amount = amount,
                    customerPhone = phone,
                    orangeTransactionId = orangeId,
                    normalizedBody = normalized,
                    rawBody = rawBody,
                )
            }
        }

        return ParsedSms(
            OutcomeKind.IGNORED,
            type = opCode,
            amount = amount,
            customerPhone = phone,
            orangeTransactionId = orangeId,
            normalizedBody = normalized,
            rawBody = rawBody,
        )
    }

    /**
     * Corrélation TX ↔ SMS : type d'opération + montant + téléphone (si requis).
     */
    fun correlates(parsed: ParsedSms, ctx: CorrelationContext): Boolean {
        if (parsed.kind == OutcomeKind.IGNORED) return false
        if (!operationTypeMatches(parsed.type, ctx.operationType)) return false

        val op = normalizeOpCode(ctx.operationType)
        val family = OP_FAMILIES.firstOrNull { it.code == op }

        val expectedAmount = ctx.amount?.let { Math.round(it) }
        if (expectedAmount != null) {
            if (parsed.amount == null) {
                // Échec générique sans montant extractible
                return parsed.kind == OutcomeKind.FAILED &&
                    FAILURE_VERBS.containsMatchIn(parsed.normalizedBody)
            }
            if (parsed.amount != expectedAmount) return false
        } else if (op != "SOLDE") {
            return false
        }

        val requiresPhone = family?.requiresPhone != false
        if (requiresPhone) {
            val expectedPhone = nationalDigits(ctx.customerPhone) ?: return false
            if (parsed.customerPhone == null) {
                return parsed.kind == OutcomeKind.FAILED &&
                    FAILURE_VERBS.containsMatchIn(parsed.normalizedBody)
            }
            if (!phonesMatch(parsed.customerPhone, expectedPhone)) return false
        } else if (parsed.customerPhone != null && ctx.customerPhone != null) {
            // Si les deux sont présents, exiger la cohérence
            if (!phonesMatch(parsed.customerPhone, ctx.customerPhone)) return false
        }
        return true
    }

    fun operationTypeMatches(parsedType: String?, operationType: String?): Boolean {
        val expected = normalizeOpCode(operationType) ?: return true
        val actual = normalizeOpCode(parsedType) ?: return true
        if (expected == actual) return true
        // Alias / familles proches
        val aliases = mapOf(
            "DEPOT" to setOf("DEPOT", "TRANSFERT"), // Orange envoie souvent « transfert » pour un dépôt
            "TRANSFERT" to setOf("TRANSFERT", "DEPOT"),
            "RETRAIT" to setOf("RETRAIT"),
            "PAIEMENT" to setOf("PAIEMENT"),
            "ACHAT_CREDIT" to setOf("ACHAT_CREDIT"),
            "ACHAT_UV" to setOf("ACHAT_UV"),
            "SOLDE" to setOf("SOLDE"),
        )
        return aliases[expected]?.contains(actual) == true
    }

    fun phonesMatch(a: String, b: String): Boolean {
        val da = nationalDigits(a) ?: return false
        val db = nationalDigits(b) ?: return false
        if (da == db) return true
        val min = minOf(da.length, db.length)
        if (min < 6) return false
        return da.takeLast(min) == db.takeLast(min) || da.endsWith(db) || db.endsWith(da)
    }

    fun normalizeOpCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().uppercase(Locale.ROOT)) {
            "WITHDRAWAL", "RETRAIT" -> "RETRAIT"
            "DEPOSIT", "DEPOT" -> "DEPOT"
            "TRANSFER", "TRANSFERT", "ENVOI" -> "TRANSFERT"
            "PAYMENT", "PAIEMENT" -> "PAIEMENT"
            "AIRTIME", "ACHAT_CREDIT", "CREDIT" -> "ACHAT_CREDIT"
            "UV", "ACHAT_UV" -> "ACHAT_UV"
            "BALANCE", "SOLDE" -> "SOLDE"
            else -> raw.trim().uppercase(Locale.ROOT)
        }
    }

    private fun detectOperationType(normalized: String): String? {
        for (family in OP_FAMILIES) {
            if (family.keywords.any { normalized.contains(it) }) {
                return family.code
            }
        }
        return null
    }

    private fun hasOperationKeywordNearAmount(normalized: String, family: OpFamily): Boolean {
        val amountMatch = AMOUNT.find(normalized) ?: return false
        val windowStart = (amountMatch.range.first - 40).coerceAtLeast(0)
        val windowEnd = (amountMatch.range.last + 40).coerceAtMost(normalized.length)
        val window = normalized.substring(windowStart, windowEnd)
        return family.keywords.any { window.contains(it) }
    }

    private fun extractAmount(normalized: String, opCode: String?): Long? {
        // Préférer le montant collé au mot-clé d'opération
        if (opCode != null) {
            val family = OP_FAMILIES.firstOrNull { it.code == opCode }
            if (family != null) {
                for (kw in family.keywords) {
                    val m = Regex(
                        """$kw\s+(?:de\s+)?(\d+(?:[.,]\d+)?)""",
                        RegexOption.IGNORE_CASE,
                    ).find(normalized)
                    val amount = m?.groupValues?.getOrNull(1)?.let(::parseAmount)
                    if (amount != null) return amount
                }
            }
        }
        // Sinon premier montant « FCFA / XOF »
        val withCurrency = Regex(
            """(\d+(?:[.,]\d+)?)\s*(?:fcfa|f\s*cfa|xof)""",
            RegexOption.IGNORE_CASE,
        ).find(normalized)
        return withCurrency?.groupValues?.getOrNull(1)?.let(::parseAmount)
            ?: AMOUNT.find(normalized)?.groupValues?.getOrNull(1)?.let(::parseAmount)
    }

    private fun extractPhone(normalized: String): String? {
        PHONE_NEAR.find(normalized)?.groupValues?.getOrNull(1)?.let { digitsOnly(it) }?.let { return it }
        return PHONE_ANY.findAll(normalized)
            .map { it.groupValues[1] }
            .mapNotNull { digitsOnly(it) }
            .firstOrNull { it.length in 8..12 }
    }

    private fun parseAmount(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.replace(',', '.').trim()
        val asDouble = normalized.toDoubleOrNull() ?: return null
        return Math.round(asDouble)
    }

    private fun digitsOnly(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val d = raw.filter { it.isDigit() }
        return d.takeIf { it.length >= 6 }
    }

    private fun nationalDigits(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        var digits = phone.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.drop(2)
        if (digits.startsWith("223") && digits.length > 9) {
            digits = digits.drop(3)
        }
        return digits.takeIf { it.length >= 6 }
    }
}
