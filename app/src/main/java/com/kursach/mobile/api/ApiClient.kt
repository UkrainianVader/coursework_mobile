package com.kursach.mobile.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton API client for Retrofit-based HTTP communication with backend.
 * Configures OkHttp client with logging, timeouts, and Gson for JSON serialization.
 */
object ApiClient {
    private var retrofit: Retrofit? = null
    private var baseUrl: String = "http://10.0.2.2:3000" // Default for emulator

    /**
     * Initialize the API client with a custom base URL.
     * Reads API_BASE_URL from local.properties if available.
     *
     * @param context Android application context (for local.properties access if needed)
     * @param customBaseUrl Optional override for the base URL
     */
    fun init(context: Context, customBaseUrl: String? = null) {
        if (customBaseUrl != null) {
            baseUrl = customBaseUrl
        }
        // TODO: Read API_BASE_URL from local.properties at runtime
    }

    /**
     * Get or create the Retrofit instance with OkHttp client.
     */
    fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setPrettyPrinting().create()))
                .build()
        }
        return retrofit!!
    }

    /**
     * Create OkHttp client with logging and timeout configuration.
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create or get the API service interface.
     */
    fun <T> create(serviceClass: Class<T>): T {
        return getRetrofit().create(serviceClass)
    }
}
