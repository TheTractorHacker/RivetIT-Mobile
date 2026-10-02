package com.foleyit.itflow.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.foleyit.itflow.BuildConfig
import com.foleyit.itflow.data.ssl.FingerprintTrustManager
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object ApiClient {
    private var _serverUrl: String = ""
    private var _token: String? = null
    private var _trustedCertSha: String? = null
    private var _service: ApiService? = null
    private var _appContext: Context? = null
    private var _cachedProfile: UserProfile? = null
    private var _httpCache: Cache? = null

    val serverUrl get() = _serverUrl

    var onUnauthorized: (() -> Unit)? = null

    fun init(serverUrl: String, token: String?, trustedCertSha: String? = null, context: Context? = null) {
        val cleanUrl = serverUrl.trimEnd('/')
        if (_serverUrl.isNotEmpty() && (_serverUrl != cleanUrl || _token != token)) clearCachedResponses()
        _serverUrl = cleanUrl
        _token = token
        _trustedCertSha = trustedCertSha
        context?.let { _appContext = it.applicationContext }
        _cachedProfile = null
        _service = buildService()
    }

    fun setToken(token: String) { if (_token != token) clearCachedResponses(); _token = token; _cachedProfile = null; _service = buildService() }
    fun clearToken() { clearCachedResponses(); _token = null; _cachedProfile = null; _service = buildService() }
    fun setTrustedCert(sha: String?) { _trustedCertSha = sha; _service = buildService() }

    fun service(): ApiService = _service ?: error("ApiClient not initialized")

    // Session-scoped cache: /me is now read from several places (drawer, ticket detail) to
    // check module flags, not just the profile screen - fetch once per session instead of
    // once per screen. Invalidated in init()/setToken()/clearToken() so switching accounts
    // never leaks the previous user's cached profile/module flags.
    suspend fun profile(): UserProfile = _cachedProfile ?: service().getProfile().also { _cachedProfile = it }

    private fun clearCachedResponses() {
        try { _httpCache?.evictAll() } catch (_: Exception) { /* A failed eviction must not block sign-out. */ }
    }

    private fun isOnline(): Boolean {
        val ctx = _appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildService(): ApiService {
        val trustManager = FingerprintTrustManager(_trustedCertSha)
        val sslContext = SSLContext.getInstance("TLS").also {
            it.init(null, arrayOf(trustManager), null)
        }

        val clientBuilder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Offline cache (10 MB)
            .apply {
                _appContext?.let { ctx ->
                    val cacheDir = File(ctx.cacheDir, "http_cache")
                    cache(_httpCache ?: Cache(cacheDir, 10L * 1024 * 1024).also { _httpCache = it })
                }
            }
            // Serve stale cache when offline
            .addInterceptor { chain ->
                val request = if (!isOnline()) {
                    chain.request().newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build()
                } else chain.request()
                chain.proceed(request)
            }
            // Cache successful GETs only. A cached 404 or 401 makes Retry
            // repeat an obsolete failure even after the server recovers.
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET") {
                    response.newBuilder()
                        .header("Cache-Control", if (response.isSuccessful) "private, max-age=300" else "no-store")
                        .build()
                } else response
            }
            // Auth header
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    _token?.let { addHeader("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(req)
            }
            // 401 auto-logout
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 && _token != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onUnauthorized?.invoke()
                    }
                }
                response
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }

        return Retrofit.Builder()
            .baseUrl("$_serverUrl/api/v1/")
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
