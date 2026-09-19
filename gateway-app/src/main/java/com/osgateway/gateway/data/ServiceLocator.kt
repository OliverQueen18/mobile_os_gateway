package com.osgateway.gateway.data

import android.content.Context
import com.osgateway.shared.api.AuthApi
import com.osgateway.shared.api.GatewayApi
import com.osgateway.shared.network.ApiClientFactory
import retrofit2.Retrofit

class ServiceLocator private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val tokenStore = SecureTokenStore(appContext)

    @Volatile
    private var retrofit: Retrofit = buildRetrofit()

    var authApi: AuthApi = ApiClientFactory.authApi(retrofit)
        private set
    var gatewayApi: GatewayApi = ApiClientFactory.gatewayApi(retrofit)
        private set

    val sessionKeepAlive = SessionKeepAlive(
        tokenStore = tokenStore,
        authApiProvider = {
            SessionKeepAlive.bareAuthApi(tokenStore.getApiBaseUrl())
        },
        onRefreshFailed = {
            tokenStore.clearSync()
            JournalRepository.append("Session expirée — retour login")
        },
    ).also { it.start() }

    fun rebuildClient() {
        retrofit = buildRetrofit()
        authApi = ApiClientFactory.authApi(retrofit)
        gatewayApi = ApiClientFactory.gatewayApi(retrofit)
    }

    private fun buildRetrofit(): Retrofit {
        val client = ApiClientFactory.createOkHttp(
            tokenProvider = { tokenStore.getAccessToken() },
            refreshTokenProvider = { tokenStore.getRefreshToken() },
            baseUrlProvider = { tokenStore.getApiBaseUrl() },
            onTokensRefreshed = { access, refresh ->
                tokenStore.updateTokensSync(access, refresh)
                JournalRepository.append("Token rafraîchi")
            },
            onUnauthorized = {
                JournalRepository.append("API 401 — refresh impossible, reconnexion")
                tokenStore.clearSync()
            },
        )
        return ApiClientFactory.createRetrofit(tokenStore.getApiBaseUrl(), client)
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator {
            return instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
        }
    }
}
