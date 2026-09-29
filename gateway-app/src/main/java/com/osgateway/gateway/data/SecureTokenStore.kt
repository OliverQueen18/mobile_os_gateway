package com.osgateway.gateway.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "osg_gateway_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("osg_gateway_fallback", Context.MODE_PRIVATE)
    }

    private val _loggedIn = MutableStateFlow(hasSession())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** Session valide si refresh ou access encore présent (refresh auto gère l'expiration). */
    fun hasSession(): Boolean =
        !getRefreshToken().isNullOrBlank() || !getAccessToken().isNullOrBlank()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun getGatewayId(): String? = prefs.getString(KEY_GATEWAY_ID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getApiBaseUrl(): String {
        val stored = prefs.getString(KEY_API_URL, null)
        if (stored.isNullOrBlank() || isLegacyLocalApiUrl(stored)) {
            return com.osgateway.gateway.BuildConfig.DEFAULT_API_BASE_URL
        }
        return stored
    }

    fun getPreferredOperator(): String? = prefs.getString(KEY_OPERATOR, null)
        ?.takeIf { it.isNotBlank() }

    fun savePreferredOperator(operator: String) {
        prefs.edit().putString(KEY_OPERATOR, operator.trim().uppercase()).apply()
    }

    /** Mise à jour synchrone pour l'Authenticator OkHttp (thread réseau). */
    fun updateTokensSync(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .commit()
        _loggedIn.value = true
    }

    /**
     * Persists tokens without flipping [loggedIn]. Call [markLoggedIn] only after post-login
     * work finishes — otherwise Compose cancels [rememberCoroutineScope] mid-login.
     */
    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        gatewayId: String?,
        username: String?,
        markLoggedIn: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_GATEWAY_ID, gatewayId)
            .putString(KEY_USERNAME, username)
            .apply()
        if (markLoggedIn) {
            _loggedIn.value = true
        }
    }

    fun markLoggedIn() {
        _loggedIn.value = true
    }

    suspend fun saveApiBaseUrl(url: String) = withContext(Dispatchers.IO) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_API_URL, normalized).apply()
    }

    suspend fun saveGatewayId(gatewayId: String?) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_GATEWAY_ID, gatewayId).apply()
    }

    fun clearSync() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_GATEWAY_ID)
            .remove(KEY_USERNAME)
            .commit()
        _loggedIn.value = false
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        clearSync()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_GATEWAY_ID = "gateway_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_API_URL = "api_base_url"
        private const val KEY_OPERATOR = "preferred_operator"

        /** Anciennes URLs locales / émulateur — basculer vers la prod BuildConfig. */
        fun isLegacyLocalApiUrl(url: String): Boolean {
            val u = url.lowercase()
            return u.contains("10.0.2.2") ||
                u.contains("127.0.0.1") ||
                u.contains("localhost") ||
                u.contains(":18080") ||
                u.contains(":8080")
        }
    }
}
