package com.kursach.mobile.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "kursach_mobile_settings"
    private const val KEY_BASE_URL = "api_base_url"
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"

    private val cookieJar = SessionCookieJar()
    private var retrofit: Retrofit? = null
    private var baseUrl: String = DEFAULT_BASE_URL

    fun init(context: Context, customBaseUrl: String? = null): String {
        val resolvedBaseUrl = normalizeBaseUrl(
            customBaseUrl ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, DEFAULT_BASE_URL)
                .orEmpty()
        )

        if (resolvedBaseUrl != baseUrl) {
            baseUrl = resolvedBaseUrl
            retrofit = null
        }

        if (customBaseUrl != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, baseUrl)
                .apply()
        }

        return baseUrl
    }

    fun currentBaseUrl(): String = baseUrl

    fun clearSession() {
        cookieJar.clear()
    }

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

    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun <T> create(serviceClass: Class<T>): T {
        return getRetrofit().create(serviceClass)
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().ifEmpty { DEFAULT_BASE_URL }
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "http://$trimmed"
        }
        val withTrailingSlash = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        return withTrailingSlash.toHttpUrlOrNull()?.toString() ?: DEFAULT_BASE_URL
    }
}

private class SessionCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            this.cookies.removeAll { it.expiresAt < System.currentTimeMillis() }
            this.cookies.removeAll { existing -> cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path } }
            this.cookies.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(this) {
            return cookies.filter { it.expiresAt >= System.currentTimeMillis() && it.matches(url) }
        }
    }

    fun clear() {
        synchronized(this) {
            cookies.clear()
        }
    }
}
