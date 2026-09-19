package com.osgateway.gateway.ussd

import android.content.Context
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.sms.OrangeMoneySmsParser
import com.osgateway.shared.model.BalancePattern
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.UssdStep
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Lit le solde MM de la SIM gateway via le mini-scénario SOLDE fourni par le serveur.
 */
class GatewayBalanceReader(
    private val context: Context,
    private val executor: UssdScenarioExecutor = UssdScenarioExecutor(context),
) {

    suspend fun readBalance(task: GatewayTask, steps: List<UssdStep>): BigDecimal? {
        if (steps.isEmpty()) return null
        val balanceTask = task.copy(
            id = "${task.id}-solde",
            steps = steps,
            timeoutSeconds = 90,
            variables = task.variables + mapOf(
                "_balance_check" to "true",
                "type" to "SOLDE",
            ),
        )
        val result = executor.run(balanceTask)
        val raw = result.extracted["mm_balance"]
            ?: result.extracted["gateway_balance"]
        val smsBody = result.extracted["sms_body"] ?: result.extracted["confirmation_sms"]
        val parsed = parseBalance(raw, task.balancePatterns)
            ?: OrangeMoneySmsParser.parseBalanceFromSms(
                smsBody.orEmpty(),
                task.operator,
                task.balancePatterns,
            )?.let {
                BigDecimal.valueOf(it)
            }
        if (parsed != null) {
            JournalRepository.append("Solde gateway lu: $parsed XOF")
        } else {
            JournalRepository.append("Lecture solde gateway échouée (task ${task.id})")
        }
        return parsed
    }

    companion object {
        fun parseBalance(raw: String?, patterns: List<BalancePattern> = emptyList()): BigDecimal? {
            if (raw.isNullOrBlank()) return null
            GatewayBalanceParser.parsePrincipal(raw, patterns)?.let {
                return BigDecimal.valueOf(it)
            }
            val normalized = raw.trim()
                .replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(',', '.')
            return runCatching {
                BigDecimal(normalized).setScale(0, RoundingMode.HALF_UP)
            }.getOrNull()
        }

        fun expectedGatewayDelta(txType: String?, amount: BigDecimal?): BigDecimal? {
            if (amount == null || txType.isNullOrBlank()) return null
            // Agent : dépôt client → float ↓ ; retrait client → float ↑
            return when (txType.uppercase(Locale.ROOT)) {
                "RETRAIT" -> amount
                "DEPOT", "TRANSFERT", "PAIEMENT", "ACHAT_CREDIT" -> amount.negate()
                else -> BigDecimal.ZERO
            }
        }

        fun hasSufficientBalance(before: BigDecimal, txType: String?, amount: BigDecimal): Boolean {
            val expected = expectedGatewayDelta(txType, amount) ?: return true
            if (expected.signum() >= 0) return true
            return before >= amount
        }

        fun balanceMatches(
            before: BigDecimal,
            after: BigDecimal,
            txType: String?,
            amount: BigDecimal?,
        ): Boolean? {
            val expected = expectedGatewayDelta(txType, amount) ?: return null
            if (expected.signum() == 0) return null
            val actual = after.subtract(before)
            val tolerance = BigDecimal("5")
            return actual.subtract(expected).abs() <= tolerance
        }
    }
}
