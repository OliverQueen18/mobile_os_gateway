package com.osgateway.gateway.sms

import com.osgateway.gateway.ussd.UssdWindowParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Soft-wait : après le délai USSD (~2 min), la TX reste en attente SMS
 * jusqu'à [deadlineMs] pour couvrir les retards opérateur.
 */
object PendingSmsConfirmationRegistry {

    data class Pending(
        val taskId: String,
        val amount: Double?,
        val customerPhone: String?,
        val operationType: String,
        val sinceMs: Long,
        val deadlineMs: Long,
        val successPattern: String?,
        val failurePattern: String?,
    )

    data class Match(
        val pending: Pending,
        val sms: InboundSmsHub.InboundSms,
        val parsed: OrangeMoneySmsParser.ParsedSms,
        val success: Boolean,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    fun register(entry: Pending) {
        pruneExpired()
        pending[entry.taskId] = entry
    }

    fun remove(taskId: String) {
        pending.remove(taskId)
    }

    fun clearForTests() {
        pending.clear()
    }

    fun size(): Int = pending.size

    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        pending.entries.removeIf { (_, p) -> p.deadlineMs < now }
    }

    /** Retire et retourne les entrées dont le délai soft-wait est dépassé. */
    fun drainExpired(now: Long = System.currentTimeMillis()): List<Pending> {
        val expired = pending.filter { (_, p) -> p.deadlineMs <= now }.values.toList()
        expired.forEach { pending.remove(it.taskId) }
        return expired
    }

    /**
     * Cherche une TX en soft-wait corrélée à ce SMS.
     * Retire l'entrée en cas de match (consommée).
     */
    fun tryMatch(
        address: String,
        body: String,
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
    ): Match? {
        pruneExpired(now)
        if (pending.isEmpty()) return null

        val sms = InboundSmsHub.InboundSms(address, body, timestamp)
        val key = OrangeMoneySmsParser.duplicateKey(address, body, timestamp)
        val parsed = OrangeMoneySmsParser.parse(body)

        for ((taskId, p) in pending.toMap()) {
            if (p.deadlineMs < now) {
                pending.remove(taskId)
                continue
            }
            if (timestamp < p.sinceMs - 2_000L) continue

            val ctx = OrangeMoneySmsParser.CorrelationContext(
                amount = p.amount,
                customerPhone = p.customerPhone,
                operationType = p.operationType,
            )

            when (parsed.kind) {
                OrangeMoneySmsParser.OutcomeKind.IGNORED -> {
                    val failureByPattern = !p.failurePattern.isNullOrBlank() &&
                        UssdWindowParser.matchesExpected(body, p.failurePattern)
                    val successByPattern = !p.successPattern.isNullOrBlank() &&
                        UssdWindowParser.matchesExpected(body, p.successPattern)
                    when {
                        failureByPattern -> {
                            if (!OrangeMoneySmsParser.markProcessed(key)) continue
                            pending.remove(taskId)
                            return Match(
                                p,
                                sms,
                                parsed.copy(kind = OrangeMoneySmsParser.OutcomeKind.FAILED),
                                success = false,
                            )
                        }
                        successByPattern -> {
                            // Motif template succès + corrélation montant/téléphone si extractibles
                            val enriched = enrichIgnoredForCorrelation(parsed, body)
                            if (!OrangeMoneySmsParser.correlates(
                                    enriched.copy(kind = OrangeMoneySmsParser.OutcomeKind.SUCCESS),
                                    ctx,
                                ) &&
                                (enriched.amount != null || enriched.customerPhone != null)
                            ) {
                                continue
                            }
                            if (!OrangeMoneySmsParser.markProcessed(key)) continue
                            pending.remove(taskId)
                            return Match(
                                p,
                                sms,
                                enriched.copy(kind = OrangeMoneySmsParser.OutcomeKind.SUCCESS, type = enriched.type ?: "DEPOT"),
                                success = true,
                            )
                        }
                        else -> continue
                    }
                }
                OrangeMoneySmsParser.OutcomeKind.FAILED,
                OrangeMoneySmsParser.OutcomeKind.SUCCESS,
                -> {
                    if (!OrangeMoneySmsParser.correlates(parsed, ctx)) continue
                    if (!OrangeMoneySmsParser.markProcessed(key)) continue
                    pending.remove(taskId)
                    return Match(
                        p,
                        sms,
                        parsed,
                        success = parsed.kind == OrangeMoneySmsParser.OutcomeKind.SUCCESS,
                    )
                }
            }
        }
        return null
    }

    private fun enrichIgnoredForCorrelation(
        parsed: OrangeMoneySmsParser.ParsedSms,
        body: String,
    ): OrangeMoneySmsParser.ParsedSms {
        if (parsed.amount != null && parsed.customerPhone != null) return parsed
        val reParsed = OrangeMoneySmsParser.parse(body)
        if (reParsed.kind != OrangeMoneySmsParser.OutcomeKind.IGNORED) return reParsed
        val amountMatch = Regex(
            """(\d+(?:[.,]\d+)?)\s*(?:fcfa|f\s*cfa|xof)""",
            RegexOption.IGNORE_CASE,
        ).find(SmsNormalizer.normalize(body))
        val phoneMatch = Regex("""(\d{6,15})""").findAll(SmsNormalizer.normalize(body))
            .map { it.groupValues[1] }
            .firstOrNull { it.length in 8..15 }
        val amount = amountMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let { Math.round(it) }
        return parsed.copy(
            amount = parsed.amount ?: amount,
            customerPhone = parsed.customerPhone ?: phoneMatch,
            type = parsed.type ?: "DEPOT",
        )
    }
}
