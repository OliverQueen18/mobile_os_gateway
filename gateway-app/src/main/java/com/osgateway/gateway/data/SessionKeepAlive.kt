package com.osgateway.gateway.data

import android.util.Base64
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.osgateway.shared.api.AuthApi
import com.osgateway.shared.model.RefreshRequest
import com.osgateway.shared.network.ApiClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Garde la session gateway active : refresh proactif avant expiration du JWT.
 */
class SessionKeepAlive(
    private val tokenStore: SecureTokenStore,
    private val authApiProvider: () -> AuthApi,
    private val onRefreshFailed: (() -> Unit)? = null,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = AtomicBoolean(false)
    private var loopJob: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        loopJob?.cancel()
        loopJob = scope.launch {
            ensureFreshSession()
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                ensureFreshSession()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * Refresh si le access token est absent, expiré, ou expire dans moins de [REFRESH_SKEW_SEC].
     * @return true si une session valide est disponible après l'appel.
     */
    suspend fun ensureFreshSession(): Boolean = withContext(Dispatchers.IO) {
        val access = tokenStore.getAccessToken()
        val refresh = tokenStore.getRefreshToken()
        if (refresh.isNullOrBlank()) {
            return@withContext !access.isNullOrBlank()
        }
        val expiresAt = access?.let { jwtExpEpochSeconds(it) }
        val now = System.currentTimeMillis() / 1000L
        val needsRefresh = access.isNullOrBlank()
            || expiresAt == null
            || expiresAt <= now + REFRESH_SKEW_SEC
        if (!needsRefresh) {
            return@withContext true
        }
        refreshNow(refresh)
    }

    private suspend fun refreshNow(refreshToken: String): Boolean {
        if (!refreshing.compareAndSet(false, true)) {
            return !tokenStore.getAccessToken().isNullOrBlank()
        }
        return try {
            val api = authApiProvider()
            val resp = api.refresh(RefreshRequest(refreshToken))
            val data = resp.data
            if (resp.success && data != null) {
                tokenStore.updateTokensSync(data.accessToken, data.refreshToken)
                JournalRepository.append("Session rafraîchie (auto)")
                Log.i(TAG, "Session refreshed proactively")
                true
            } else {
                Log.w(TAG, "Refresh failed: ${resp.message}")
                JournalRepository.append("Refresh JWT échoué — reconnexion requise")
                onRefreshFailed?.invoke()
                false
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Refresh error: ${ex.message}")
            false
        } finally {
            refreshing.set(false)
        }
    }

    companion object {
        private const val TAG = "GwSessionKeepAlive"
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val REFRESH_SKEW_SEC = 5 * 60L

        fun jwtExpEpochSeconds(jwt: String): Long? {
            return try {
                val parts = jwt.split('.')
                if (parts.size < 2) return null
                var payload = parts[1]
                val pad = (4 - payload.length % 4) % 4
                if (pad > 0) payload += "=".repeat(pad)
                val json = String(
                    Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP),
                    Charsets.UTF_8,
                )
                Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()
            } catch (_: Exception) {
                null
            }
        }

        fun bareAuthApi(baseUrl: String): AuthApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return ApiClientFactory.authApi(ApiClientFactory.createRetrofit(baseUrl, client))
        }
    }
}
