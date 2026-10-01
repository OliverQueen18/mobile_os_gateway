package com.osgateway.distributor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.osgateway.distributor.R
import com.osgateway.shared.util.TransactionStatusFr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DistributorFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")
        FcmTokenStore.saveToken(applicationContext, token)
        scope.launch { FcmTokenStore.syncTokenToBackend(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "OS Distributeur"
        val rawBody = message.notification?.body
            ?: message.data["message"]
            ?: message.data["body"]
            ?: ""
        val body = TransactionStatusFr.stripCommissionMentions(rawBody)
        if (body.isBlank() && message.notification == null) return
        ServiceLocator.get(applicationContext).balanceStore.reconcile()
        showNotification(title, body.ifBlank { "Nouvelle notification" })
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        private const val TAG = "DistributorFcm"
        private const val CHANNEL_ID = "os_distributor_fcm"
        private const val CHANNEL_NAME = "Notifications push"
    }
}
