package com.osgateway.distributor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.osgateway.distributor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "osg_distributor_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("osg_distributor_fallback", Context.MODE_PRIVATE)
    }

    private val _loggedIn = MutableStateFlow(!getAccessToken().isNullOrBlank())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun getAccessToken(): String? = prefs.getString("access", null)
    fun getRefreshToken(): String? = prefs.getString("refresh", null)
    fun getUsername(): String? = prefs.getString("username", null)
    fun getApiBaseUrl(): String {
        val stored = prefs.getString("api_url", null)
        if (stored.isNullOrBlank() || isLegacyLocalApiUrl(stored)) {
            return BuildConfig.DEFAULT_API_BASE_URL
        }
        return stored
    }

    /** Mise à jour synchrone pour l'Authenticator OkHttp (thread réseau). */
    fun updateTokensSync(access: String, refresh: String) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .commit()
        _loggedIn.value = true
    }

    /** Déconnexion synchrone (échec refresh / 401 définitif). */
    fun clearSync() {
        prefs.edit()
            .remove("access")
            .remove("refresh")
            .remove("username")
            .commit()
        _loggedIn.value = false
    }

    suspend fun saveSession(access: String, refresh: String, username: String?) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putString("username", username)
            .apply()
        _loggedIn.value = true
    }

    suspend fun saveApiBaseUrl(url: String) = withContext(Dispatchers.IO) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString("api_url", normalized).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove("access").remove("refresh").remove("username").apply()
        _loggedIn.value = false
    }

    companion object {
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
