package com.osgateway.distributor.data

import com.osgateway.shared.api.TransactionApi
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.OperationTypeDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Solde affiché = solde serveur − réserves des opérations DEBIT encore ouvertes.
 * La réserve est posée à la création et retirée à l'échec (le serveur n'a pas débité)
 * ou au succès une fois le solde serveur relu (débit déjà appliqué).
 */
class DistributorBalanceStore(
    private val api: () -> TransactionApi,
    private val cache: StatsCacheStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, Double>()
    private val mutex = Mutex()
    private val serverBalance = MutableStateFlow<Double?>(null)
    private val _displayedBalance = MutableStateFlow<Double?>(null)
    val displayedBalance: StateFlow<Double?> = _displayedBalance.asStateFlow()

    private var pollJob: Job? = null

    fun applyServer(distributor: DistributorMeDto?) {
        serverBalance.value = distributor?.balance
        publish()
    }

    fun reserve(transactionId: String, amount: Double) {
        if (transactionId.isBlank() || amount <= 0.0) return
        pending[transactionId] = amount
        publish()
        ensurePolling()
    }

    /** Relit le solde et clôture les réserves dont la transaction est terminée. */
    fun reconcile() {
        scope.launch { reconcilePending() }
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (pending.isNotEmpty()) {
                reconcilePending()
                if (pending.isEmpty()) break
                delay(POLL_MS)
            }
        }
    }

    private suspend fun reconcilePending() {
        val ids = pending.keys.toList()
        if (ids.isEmpty()) {
            refreshServer()
            return
        }
        for (id in ids) {
            val status = runCatching { api().getById(id).data?.status }
                .getOrNull()
                ?.trim()
                ?.uppercase()
                ?: continue
            when (status) {
                "FAILED", "TIMEOUT", "CANCELLED" -> {
                    pending.remove(id)
                    fetchServer()
                    publish()
                }
                "SUCCESS" -> {
                    if (fetchServer()) {
                        pending.remove(id)
                        publish()
                    }
                }
            }
        }
    }

    private suspend fun fetchServer(): Boolean = mutex.withLock {
        val response = runCatching { api().myDistributor() }.getOrNull() ?: return false
        val data = response.data
        if (!response.success || data == null) return false
        serverBalance.value = data.balance
        runCatching { cache.save(distributor = data) }
        true
    }

    private fun publish() {
        val server = serverBalance.value
        val hold = pending.values.sum()
        _displayedBalance.value = when {
            server == null -> null
            else -> (server - hold).coerceAtLeast(0.0)
        }
    }

    companion object {
        private const val POLL_MS = 5_000L

        fun reservesUv(operation: OperationTypeDto?): Boolean {
            val effect = operation?.balanceEffect?.trim()?.uppercase()
            return when (effect) {
                "NONE", "CREDIT" -> false
                "DEBIT" -> true
                else -> {
                    val code = operation?.code?.trim()?.uppercase()
                    code != "SOLDE" && code != "ACHAT_UV"
                }
            }
        }
    }
}
