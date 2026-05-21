package com.example.aiwidget.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 按用户配置的 baseUrl 动态创建 Retrofit 实例。
 *
 * 读超时 180s，与后端多轮 tool calling 对齐（技术方案 §10.1）。
 */
object RetrofitClient {
    private val moshi: Moshi =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    /** 保证 Retrofit 要求的尾部 `/`。 */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().ifEmpty { com.example.aiwidget.data.Presets.DEFAULT_BASE_URL }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /** 创建带 `X-API-Key` 的 [ApiService]。 */
    fun createApi(baseUrl: String, apiKey: String): ApiService {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val req =
                        chain.request().newBuilder()
                            .header("X-API-Key", apiKey.trim())
                            .header("Content-Type", "application/json")
                            .build()
                    chain.proceed(req)
                }
                .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    /** 供 SSE 流使用（Retrofit 不承载 EventStream）。 */
    fun createOkHttp(apiKey: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-API-Key", apiKey.trim())
                        .build(),
                )
            }
            .build()

    val moshiInstance: Moshi = moshi
}
