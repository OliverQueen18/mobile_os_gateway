package com.osgateway.distributor.data

import android.content.Context
import com.osgateway.shared.api.AuthApi
import com.osgateway.shared.api.TransactionApi
import com.osgateway.shared.network.ApiClientFactory
import retrofit2.Retrofit

class ServiceLocator private constructor(context: Context) {
    val appContext = context.applicationContext
    val tokenStore = TokenStore(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val statsCache = StatsCacheStore(appContext)

    @Volatile
    private var retrofit: Retrofit = buildRetrofit()

    var authApi: AuthApi = ApiClientFactory.authApi(retrofit)
        private set
    var transactionApi: TransactionApi = ApiClientFactory.transactionApi(retrofit)
        private set

    val sessionKeepAlive = SessionKeepAlive(
        tokenStore = tokenStore,
        authApiProvider = {
            SessionKeepAlive.bareAuthApi(tokenStore.getApiBaseUrl())
        },
    ).also { it.start() }

    fun rebuildClient() {
        retrofit = buildRetrofit()
        authApi = ApiClientFactory.authApi(retrofit)
        transactionApi = ApiClientFactory.transactionApi(retrofit)
    }

    private fun buildRetrofit(): Retrofit {
        val client = ApiClientFactory.createOkHttp(
            tokenProvider = { tokenStore.getAccessToken() },
            refreshTokenProvider = { tokenStore.getRefreshToken() },
            baseUrlProvider = { tokenStore.getApiBaseUrl() },
            onTokensRefreshed = { access, refresh ->
                tokenStore.updateTokensSync(access, refresh)
            },
            onUnauthorized = {
                // Refresh impossible : retour login sans crasher l'UI
                tokenStore.clearSync()
            },
        )
        return ApiClientFactory.createRetrofit(tokenStore.getApiBaseUrl(), client)
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
