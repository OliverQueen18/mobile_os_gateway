package com.osgateway.gateway.ussd

import com.osgateway.shared.model.BalancePattern
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.UssdStep
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Trace solde avant TX pour relecture à l'expiration du soft-wait SMS.
 */
object TransactionBalanceTrace {

    data class Trace(
        val taskId: String,
        val balanceBefore: BigDecimal?,
        val txType: String?,
        val amount: BigDecimal?,
        val balanceCheckSteps: List<UssdStep>,
        val balancePatterns: List<BalancePattern> = emptyList(),
        val task: GatewayTask,
    )

    private val traces = ConcurrentHashMap<String, Trace>()

    fun put(trace: Trace) {
        traces[trace.taskId] = trace
    }

    fun get(taskId: String): Trace? = traces[taskId]

    fun remove(taskId: String) {
        traces.remove(taskId)
    }

    fun clearForTests() {
        traces.clear()
    }
}
