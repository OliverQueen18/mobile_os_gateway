package com.osgateway.gateway

import android.app.Application
import com.osgateway.gateway.data.FcmTokenStore
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.worker.HeartbeatWorker
import com.osgateway.gateway.worker.TaskPollingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GatewayApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.get(this)
        JournalRepository.append("OS Gateway démarré v${BuildConfig.VERSION_NAME}")
        HeartbeatWorker.enqueue(this)
        TaskPollingWorker.enqueue(this)
        val locator = ServiceLocator.get(this)
        if (locator.tokenStore.hasSession()) {
            // Redémarre le FG heartbeat si une session existe déjà (après kill / cold start)
            com.osgateway.gateway.service.GatewayForegroundService.start(this)
            appScope.launch {
                locator.sessionKeepAlive.ensureFreshSession()
            }
        }
        appScope.launch {
            runCatching { FcmTokenStore.refreshToken(this@GatewayApplication) }
        }
    }
}
