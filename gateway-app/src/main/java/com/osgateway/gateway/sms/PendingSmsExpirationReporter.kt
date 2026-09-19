package com.osgateway.gateway.sms

import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.ussd.GatewayBalanceReader
import com.osgateway.gateway.ussd.TransactionBalanceTrace
import com.osgateway.shared.model.TaskResultRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * À l'expiration du soft-wait SMS : relit le solde gateway et reporte SUCCESS/FAILED/TIMEOUT.
 */
object PendingSmsExpirationReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun processExpired(context: android.content.Context) {
        val expired = PendingSmsConfirmationRegistry.drainExpired()
        if (expired.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            val locator = runCatching { ServiceLocator.get(appContext) }.getOrNull() ?: return@launch
            val gatewayId = locator.tokenStore.getGatewayId() ?: return@launch
            val balanceReader = GatewayBalanceReader(appContext)
            for (pending in expired) {
                processOne(locator, gatewayId, balanceReader, pending)
            }
        }
    }

    private suspend fun processOne(
        locator: ServiceLocator,
        gatewayId: String,
        balanceReader: GatewayBalanceReader,
        pending: PendingSmsConfirmationRegistry.Pending,
    ) {
        val trace = TransactionBalanceTrace.get(pending.taskId)
        val extracted = linkedMapOf<String, String>()
        val balanceBefore = trace?.balanceBefore
        if (balanceBefore != null) {
            extracted["gateway_balance_before"] = balanceBefore.toPlainString()
        }

        var balanceAfter: BigDecimal? = null
        if (trace != null && trace.balanceCheckSteps.isNotEmpty()) {
            balanceAfter = balanceReader.readBalance(trace.task, trace.balanceCheckSteps)
        }
        if (balanceAfter != null) {
            extracted["gateway_balance_after"] = balanceAfter.toPlainString()
        }
        if (balanceBefore != null && balanceAfter != null) {
            extracted["gateway_balance_delta"] = balanceAfter.subtract(balanceBefore).toPlainString()
        }

        val matches = if (balanceBefore != null && balanceAfter != null) {
            GatewayBalanceReader.balanceMatches(
                balanceBefore,
                balanceAfter,
                trace?.txType ?: pending.operationType,
                trace?.amount ?: pending.amount?.let { BigDecimal(it.toLong()) },
            )
        } else {
            null
        }

        val status = when (matches) {
            true -> "SUCCESS"
            false -> "FAILED"
            null -> "TIMEOUT"
        }
        if (matches != null) {
            extracted["confirmation_source"] = "BALANCE"
            extracted["balance_confirmed"] = matches.toString()
        }

        JournalRepository.append(
            "[BALANCE] Expiration attente ${pending.taskId} → $status (match=$matches)",
        )

        runCatching {
            locator.gatewayApi.reportTaskResult(
                gatewayId,
                pending.taskId,
                TaskResultRequest(
                    taskId = pending.taskId,
                    status = status,
                    extracted = extracted,
                    errorMessage = when (status) {
                        "TIMEOUT" -> "Confirmation SMS absente (délai dépassé)"
                        "FAILED" -> "Solde gateway incompatible avec la transaction"
                        else -> null
                    },
                ),
            )
        }.onFailure {
            JournalRepository.append("Échec report expiration ${pending.taskId}: ${it.message}")
            PendingSmsConfirmationRegistry.register(pending)
        }.onSuccess {
            TransactionBalanceTrace.remove(pending.taskId)
        }
    }
}
