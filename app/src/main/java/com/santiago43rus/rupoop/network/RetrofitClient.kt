package com.santiago43rus.rupoop.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.santiago43rus.rupoop.BuildConfig

object RetrofitClient {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Rupoop-App")
                .build()
            chain.proceed(request)
        }
        .build()

    val api: RutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://rutube.ru/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RutubeApi::class.java)
    }

    val gistApi: GistApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GistApi::class.java)
    }

    val gitHubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.PROXY_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }

    val suggestApi: SuggestApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://suggestqueries.google.com/")
            .client(okHttpClient)
            .build()
            .create(SuggestApi::class.java)
    }
}
