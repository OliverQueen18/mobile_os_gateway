package com.osgateway.gateway.data

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.osgateway.shared.model.RegisterPushDeviceRequest
import kotlinx.coroutines.tasks.await

object FcmTokenStore {
    private const val TAG = "FcmTokenStore"
    private const val PREFS = "fcm_prefs"
    private const val KEY_TOKEN = "fcm_token"
    private const val APP = "GATEWAY"

    fun currentToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
            ?.takeIf { it.isNotBlank() }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    suspend fun refreshToken(context: Context): String {
        val token = FirebaseMessaging.getInstance().token.await()
        saveToken(context.applicationContext, token)
        syncTokenToBackend(context.applicationContext, token)
        return token
    }

    suspend fun syncTokenToBackend(context: Context, token: String? = null) {
        val appContext = context.applicationContext
        val locator = ServiceLocator.get(appContext)
        if (locator.tokenStore.getAccessToken().isNullOrBlank()) return
        val fcmToken = token ?: currentToken(appContext) ?: return
        val gatewayId = locator.tokenStore.getGatewayId()?.toLongOrNull()
        runCatching {
            locator.gatewayApi.registerPushDevice(
                RegisterPushDeviceRequest(
                    token = fcmToken,
                    app = APP,
                    gatewayId = gatewayId,
                ),
            )
        }.onFailure { Log.w(TAG, "FCM token upload failed: ${it.message}") }
    }
}
