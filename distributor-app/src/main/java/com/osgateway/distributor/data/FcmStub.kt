package com.osgateway.distributor.data

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.osgateway.shared.model.RegisterPushDeviceRequest
import kotlinx.coroutines.tasks.await

/**
 * Récupère, met en cache et synchronise le jeton FCM avec le backend.
 */
object FcmTokenStore {
    private const val TAG = "FcmTokenStore"
    private const val PREFS = "fcm_prefs"
    private const val KEY_TOKEN = "fcm_token"
    private const val APP = "DISTRIBUTOR"

    fun currentTokenOrStub(context: Context): String {
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
        return cached ?: "En attente du jeton FCM…"
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    suspend fun registerForNotifications(context: Context): String {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            saveToken(context.applicationContext, token)
            syncTokenToBackend(context.applicationContext, token)
            token
        } catch (e: Exception) {
            "Erreur FCM: ${e.message ?: "impossible d'obtenir le jeton"}"
        }
    }

    suspend fun syncTokenToBackend(context: Context, token: String? = null) {
        val appContext = context.applicationContext
        val locator = ServiceLocator.get(appContext)
        if (locator.tokenStore.getAccessToken().isNullOrBlank()) return
        val fcmToken = token
            ?: appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
        if (fcmToken.isNullOrBlank()) return
        runCatching {
            locator.transactionApi.registerPushDevice(
                RegisterPushDeviceRequest(token = fcmToken, app = APP),
            )
        }.onFailure { Log.w(TAG, "FCM token upload failed: ${it.message}") }
    }
}

