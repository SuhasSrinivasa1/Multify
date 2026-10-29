package com.multify.traderpro.data.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.logging.HttpLoggingInterceptor
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.data.logging.AuditLogger
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowwApiFactory @Inject constructor(
    private val auditLogger: AuditLogger
) {
    private val gson = GsonBuilder().create()

    val groww: GrowwApi by lazy {
        val alphaGovernor = GrowwRateLimitInterceptor(auditLogger)
        val alphaHotCache = GrowwHotDataCacheInterceptor()
        val client = OkHttpClient.Builder()
            // Rate permits are now acquired before enqueue; 10 host slots accommodate
            // the eight Fixed evaluation workers without increasing requests/sec.
            .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 10 })
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(alphaHotCache)
            .addInterceptor(alphaGovernor)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("X-API-VERSION", "1.0")
                    .build()
                chain.proceed(req)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.groww.in/")
            .callFactory(AlphaAsyncCallFactory(client, alphaGovernor, alphaHotCache))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GrowwApi::class.java)
    }

    /** Independent Beta research-only network client. Never contains the Alpha governor or cache. */
    val betaGroww: GrowwApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(GrowwHotDataCacheInterceptor(account = "BETA", recordAlphaStats = false))
            .addInterceptor(BetaReadOnlyInterceptor())
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("X-API-VERSION", "1.0").build())
            }
            .build()
        Retrofit.Builder().baseUrl("https://api.groww.in/")
            .client(client).addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(GrowwApi::class.java)
    }

    val publicIp: PublicIpApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.ipify.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PublicIpApi::class.java)
    }
}
