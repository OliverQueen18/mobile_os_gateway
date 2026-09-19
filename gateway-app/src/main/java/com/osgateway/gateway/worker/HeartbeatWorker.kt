package com.osgateway.gateway.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.util.DeviceMetricsCollector
import java.util.concurrent.TimeUnit

/**
 * WorkManager backup for heartbeat when the foreground service is restricted.
 */
class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.get(applicationContext)
        val gatewayId = locator.tokenStore.getGatewayId() ?: return Result.retry()
        return try {
            val body = DeviceMetricsCollector(applicationContext).toHeartbeat()
            locator.gatewayApi.heartbeat(gatewayId, body)
            JournalRepository.append("HeartbeatWorker OK")
            Result.success()
        } catch (e: Exception) {
            JournalRepository.append("HeartbeatWorker erreur: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "gateway_heartbeat_periodic"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
