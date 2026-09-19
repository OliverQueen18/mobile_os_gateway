package com.osgateway.gateway.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.osgateway.gateway.data.FcmTokenStore
import com.osgateway.gateway.data.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GatewayFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")
        FcmTokenStore.saveToken(applicationContext, token)
        JournalRepository.append("FCM token mis à jour")
        scope.launch { FcmTokenStore.syncTokenToBackend(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "OS Gateway"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: message.data["body"]
            ?: ""
        JournalRepository.append("FCM reçu: $title — $body")
    }

    companion object {
        private const val TAG = "GatewayFcm"
    }
}
