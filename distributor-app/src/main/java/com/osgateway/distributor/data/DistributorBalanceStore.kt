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
 * Solde affiché = solde serveur + aperçu des opérations encore ouvertes.
 * Débit (dépôt, transfert, paiement, crédit téléphonique) : l'aperçu est négatif.
 * Crédit (retrait, achat UV) : l'aperçu est positif.
 * Consultation : aucun aperçu.
 * À l'échec l'aperçu est retiré. Au succès le serveur a déjà bougé le solde,
 * l'aperçu est retiré après relecture.
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

    fun preview(transactionId: String, amount: Double, operation: OperationTypeDto?) {
        val delta = previewDelta(operation, amount)
        if (transactionId.isBlank() || delta == 0.0) return
        pending[transactionId] = delta
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
            if (fetchServer()) publish()
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
            else -> (server + hold).coerceAtLeast(0.0)
        }
    }

    companion object {
        private const val POLL_MS = 5_000L

        /** Delta affiché tant que la transaction n'est pas terminée. 0 = aucun impact. */
        fun previewDelta(operation: OperationTypeDto?, amount: Double): Double {
            if (amount <= 0.0) return 0.0
            return when (effectOf(operation)) {
                "NONE" -> 0.0
                "CREDIT" -> amount
                else -> -amount
            }
        }

        fun effectOf(operation: OperationTypeDto?): String {
            val configured = operation?.balanceEffect?.trim()?.uppercase()
            if (configured == "NONE") return "NONE"
            return when (operation?.code?.trim()?.uppercase()) {
                "SOLDE" -> "NONE"
                "RETRAIT", "ACHAT_UV" -> "CREDIT"
                "DEPOT", "TRANSFERT", "PAIEMENT", "ACHAT_CREDIT" -> "DEBIT"
                else -> when (configured) {
                    "CREDIT", "DEBIT" -> configured
                    else -> "DEBIT"
                }
            }
        }
    }
}
