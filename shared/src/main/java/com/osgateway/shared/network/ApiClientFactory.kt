package com.osgateway.shared.network

import com.osgateway.shared.api.AuthApi
import com.osgateway.shared.api.GatewayApi
import com.osgateway.shared.api.TransactionApi
import com.osgateway.shared.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ApiClientFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun createOkHttp(
        tokenProvider: () -> String?,
        refreshTokenProvider: (() -> String?)? = null,
        onTokensRefreshed: ((access: String, refresh: String) -> Unit)? = null,
        onUnauthorized: (() -> Unit)? = null,
        baseUrlProvider: (() -> String)? = null,
        enableLogging: Boolean = true,
    ): OkHttpClient {
        val refreshing = AtomicBoolean(false)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenProvider()
            val request = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
            } else {
                original.newBuilder()
                    .header("Accept", "application/json")
                    .build()
            }
            chain.proceed(request)
        }

        val authenticator = Authenticator { _: Route?, response: Response ->
            if (responseCount(response) >= 2) {
                onUnauthorized?.invoke()
                return@Authenticator null
            }
            val path = response.request.url.encodedPath
            if (path.contains("/auth/login") || path.contains("/auth/refresh")) {
                onUnauthorized?.invoke()
                return@Authenticator null
            }
            val refresh = refreshTokenProvider?.invoke()
            if (refresh.isNullOrBlank() || baseUrlProvider == null) {
                onUnauthorized?.invoke()
                return@Authenticator null
            }
            if (!refreshing.compareAndSet(false, true)) {
                // Un autre thread refresh déjà : attendre puis rejouer avec le nouveau token
                var waited = 0
                while (refreshing.get() && waited < 40) {
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    waited++
                }
                val latest = tokenProvider()
                return@Authenticator if (!latest.isNullOrBlank()) {
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $latest")
                        .build()
                } else {
                    onUnauthorized?.invoke()
                    null
                }
            }
            try {
                val newAccess = runBlocking {
                    refreshAccessToken(baseUrlProvider(), refresh)
                }
                if (newAccess == null) {
                    onUnauthorized?.invoke()
                    null
                } else {
                    onTokensRefreshed?.invoke(newAccess.first, newAccess.second)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newAccess.first}")
                        .build()
                }
            } finally {
                refreshing.set(false)
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)

        if (enableLogging) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
        }
        return builder.build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private suspend fun refreshAccessToken(baseUrl: String, refreshToken: String): Pair<String, String>? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            val retrofit = createRetrofit(baseUrl, client)
            val resp = authApi(retrofit).refresh(RefreshRequest(refreshToken))
            val data = resp.data
            if (resp.success && data != null) {
                data.accessToken to data.refreshToken
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    fun gatewayApi(retrofit: Retrofit): GatewayApi = retrofit.create(GatewayApi::class.java)
    fun transactionApi(retrofit: Retrofit): TransactionApi = retrofit.create(TransactionApi::class.java)
}
