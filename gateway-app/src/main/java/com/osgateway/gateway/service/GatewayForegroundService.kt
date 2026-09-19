package com.osgateway.gateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.osgateway.gateway.MainActivity
import com.osgateway.gateway.R
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.util.DeviceMetricsCollector
import com.osgateway.gateway.worker.TaskPollingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ForegroundService posting heartbeat every 30 seconds with device metrics.
 */
class GatewayForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Démarrage…"))
        JournalRepository.append("GatewayForegroundService démarré")
        TaskPollingWorker.enqueue(this)
        startHeartbeatLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val metrics = DeviceMetricsCollector(applicationContext)
            while (isActive) {
                runCatching { postHeartbeat(metrics) }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private suspend fun postHeartbeat(metrics: DeviceMetricsCollector) {
        val locator = ServiceLocator.get(this)
        // Refresh proactif avant heartbeat si le JWT va expirer / a expiré
        locator.sessionKeepAlive.ensureFreshSession()
        val gatewayId = locator.tokenStore.getGatewayId()
        if (gatewayId.isNullOrBlank() || !locator.tokenStore.hasSession()) {
            updateNotification("Non authentifié")
            JournalRepository.append("Heartbeat ignoré — session absente (reconnectez-vous)")
            return
        }
        if (locator.tokenStore.getAccessToken().isNullOrBlank()) {
            updateNotification("Session à renouveler")
            JournalRepository.append("Heartbeat reporté — access token absent après refresh")
            return
        }
        try {
            val body = metrics.toHeartbeat()
            val response = locator.gatewayApi.heartbeat(gatewayId, body)
            val pending = response.data?.pendingTasks ?: 0
            updateNotification("Online · batterie ${body.battery}% · tâches $pending")
            JournalRepository.append(
                "Heartbeat OK batt=${body.battery}% net=${body.network}" +
                    (if (body.latitude != null) " gps=${body.latitude},${body.longitude}" else " gps=—"),
            )
            // Always poll after heartbeat so SMS/USSD jobs are not stuck waiting for the 15‑min worker.
            TaskPollingWorker.enqueueImmediate(applicationContext)
            if (pending > 0) {
                JournalRepository.append("Tâches en attente: $pending")
            }
        } catch (e: Exception) {
            updateNotification("Erreur heartbeat")
            JournalRepository.append("Heartbeat erreur: ${e.message}")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        JournalRepository.append("GatewayForegroundService arrêté")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.osgateway.gateway.STOP_FG"
        private const val CHANNEL_ID = "gateway_heartbeat"
        private const val NOTIFICATION_ID = 1001
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
            ContextCompatStart(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun ContextCompatStart(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
