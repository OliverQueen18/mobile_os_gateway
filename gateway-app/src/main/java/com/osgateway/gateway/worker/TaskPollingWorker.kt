package com.osgateway.gateway.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.sms.PendingSmsExpirationReporter
import com.osgateway.gateway.sms.SmsEngine
import com.osgateway.gateway.ussd.GatewayBalanceReader
import com.osgateway.gateway.ussd.TransactionBalanceTrace
import com.osgateway.gateway.ussd.UssdScenarioExecutor
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.TaskResultRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Polls assigned USSD/SMS jobs and executes them.
 */
class TaskPollingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.get(applicationContext)
        val gatewayId = locator.tokenStore.getGatewayId() ?: return Result.success()
        PendingSmsExpirationReporter.processExpired(applicationContext)
        return try {
            val response = locator.gatewayApi.pollTasks(gatewayId)
            val tasks = response.data.orEmpty()
            JournalRepository.append("Poll tâches: ${tasks.size}")
            tasks.forEach { task ->
                processTask(locator, gatewayId, task)
            }
            Result.success()
        } catch (e: CancellationException) {
            JournalRepository.append("Poll annulé (un autre poll tourne déjà)")
            throw e
        } catch (e: Exception) {
            JournalRepository.append("Poll erreur: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun processTask(
        locator: ServiceLocator,
        gatewayId: String,
        task: GatewayTask,
    ) {
        val type = task.type.uppercase()
        val started = System.currentTimeMillis()
        val result = try {
            when {
                type.contains("SMS") -> SmsEngine(applicationContext).executeTask(task)
                type.contains("USSD") || task.steps.isNotEmpty() || !task.ussdCode.isNullOrBlank() ->
                    executeUssdWithBalance(task)
                else -> {
                    JournalRepository.append("Type tâche inconnu: ${task.type}")
                    return
                }
            }
        } catch (e: CancellationException) {
            JournalRepository.append("USSD/SMS ${task.id} interrompu — report FAILED forcé")
            TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Exécution interrompue (poll annulé)",
                durationMs = System.currentTimeMillis() - started,
            )
        }
        withContext(NonCancellable) {
            runCatching {
                locator.gatewayApi.reportTaskResult(gatewayId, task.id, result)
                JournalRepository.append("Résultat tâche ${task.id} reporté: ${result.status}")
                if (result.status != "WAITING_SMS_CONFIRMATION") {
                    TransactionBalanceTrace.remove(task.id)
                }
            }.onFailure {
                JournalRepository.append("Échec report tâche ${task.id}: ${it.message}")
            }
        }
    }

    private suspend fun executeUssdWithBalance(task: GatewayTask): TaskResultRequest {
        val executor = UssdScenarioExecutor(applicationContext)
        val balanceReader = GatewayBalanceReader(applicationContext, executor)
        val txType = task.variables["type"]?.uppercase(Locale.ROOT)
        val amount = task.amount?.let { BigDecimal.valueOf(it.toLong()) }
            ?: task.variables["amount"]?.toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)

        // SOLDE = lecture seule (peut utiliser WAIT_SMS pour récupérer le Principal)
        if (txType == "SOLDE") {
            return executor.run(task)
        }

        val extracted = linkedMapOf<String, String>()
        val soldeSteps = task.balanceCheckSteps
        if (soldeSteps.isEmpty()) {
            JournalRepository.append("Pas de scénario SOLDE — impossible de confirmer la TX ${task.id}")
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Scénario SOLDE manquant (confirmation par solde requise)",
                extracted = extracted,
            )
        }

        // 1) Solde avant
        val balanceBefore = balanceReader.readBalance(task, soldeSteps)
        if (balanceBefore == null) {
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Lecture solde avant transaction impossible",
                extracted = extracted,
            )
        }
        extracted["gateway_balance_before"] = balanceBefore.toPlainString()

        // 2) Solde insuffisant → échec immédiat (débits float)
        if (amount != null && !GatewayBalanceReader.hasSufficientBalance(balanceBefore, txType, amount)) {
            JournalRepository.append("Solde gateway insuffisant: $balanceBefore < $amount")
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Solde gateway insuffisant ($balanceBefore XOF)",
                extracted = extracted,
            )
        }

        TransactionBalanceTrace.put(
            TransactionBalanceTrace.Trace(
                taskId = task.id,
                balanceBefore = balanceBefore,
                txType = txType,
                amount = amount,
                balanceCheckSteps = soldeSteps,
                balancePatterns = task.balancePatterns,
                task = task,
            ),
        )

        // 3) Exécuter l'USSD métier (sans WAIT_SMS — confirmation = solde uniquement)
        val moneySteps = task.steps.filterNot {
            it.action.equals("WAIT_SMS", ignoreCase = true) ||
                it.action.equals("VERIFY_BALANCE", ignoreCase = true) ||
                it.action.equals("RUN_TEMPLATE", ignoreCase = true)
        }
        if (moneySteps.isEmpty()) {
            JournalRepository.append("Aucun step USSD métier pour ${task.id} (type=$txType) → FAILED")
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Scénario USSD $txType vide",
                extracted = extracted,
            )
        }
        // Laisser le réseau / dialogue SOLDE se stabiliser avant de composer le dépôt
        kotlinx.coroutines.delay(2_000)
        JournalRepository.append(
            "[USSD] Opération $txType démarrée (${moneySteps.size} steps, montant=$amount)",
        )
        val moneyTask = task.copy(steps = moneySteps)
        val ussdResult = executor.run(moneyTask)
        JournalRepository.append("[USSD] Opération $txType terminée → ${ussdResult.status}")
        val merged = ussdResult.extracted.toMutableMap()
        merged.putAll(extracted)

        // Échec USSD dur (composition / dialog) sans même tenter le solde après
        if (ussdResult.status.equals("FAILED", ignoreCase = true) &&
            ussdResult.errorMessage?.contains("insuffisant", ignoreCase = true) == true
        ) {
            merged["confirmation_source"] = "BALANCE"
            return ussdResult.copy(status = "FAILED", extracted = merged)
        }

        // 4) Solde après → confirmer / infirmer
        val balanceAfter = balanceReader.readBalance(task, soldeSteps)
        if (balanceAfter == null) {
            JournalRepository.append("Lecture solde après TX ${task.id} échouée → FAILED")
            merged["confirmation_source"] = "BALANCE"
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Lecture solde après transaction impossible",
                ussdResponse = ussdResult.ussdResponse,
                extracted = merged,
                stepLogs = ussdResult.stepLogs,
                durationMs = ussdResult.durationMs,
            )
        }
        merged["gateway_balance_after"] = balanceAfter.toPlainString()
        merged["gateway_balance_delta"] = balanceAfter.subtract(balanceBefore).toPlainString()
        merged["confirmation_source"] = "BALANCE"

        val matches = GatewayBalanceReader.balanceMatches(balanceBefore, balanceAfter, txType, amount)
        merged["balance_confirmed"] = (matches == true).toString()

        return if (matches == true) {
            JournalRepository.append(
                "TX ${task.id} confirmée par solde: $balanceBefore → $balanceAfter",
            )
            TaskResultRequest(
                taskId = task.id,
                status = "SUCCESS",
                ussdResponse = ussdResult.ussdResponse,
                extracted = merged,
                stepLogs = ussdResult.stepLogs,
                durationMs = ussdResult.durationMs,
            )
        } else {
            JournalRepository.append(
                "TX ${task.id} infirmée par solde: $balanceBefore → $balanceAfter (attendu sens=$txType amount=$amount)",
            )
            TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "Solde gateway incompatible avec la transaction " +
                    "(avant=$balanceBefore après=$balanceAfter)",
                ussdResponse = ussdResult.ussdResponse,
                extracted = merged,
                stepLogs = ussdResult.stepLogs,
                durationMs = ussdResult.durationMs,
            )
        }
    }

    companion object {
        private const val PERIODIC = "gateway_task_poll_periodic"
        private const val IMMEDIATE = "gateway_task_poll_once"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TaskPollingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<TaskPollingWorker>().build()
            // KEEP : ne pas annuler un poll/USSD déjà en cours (sinon TX reste ASSIGNED)
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
