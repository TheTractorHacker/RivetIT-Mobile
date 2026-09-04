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

    val serverUrl get() = _serverUrl

    var onUnauthorized: (() -> Unit)? = null

    fun init(serverUrl: String, token: String?, trustedCertSha: String? = null, context: Context? = null) {
        _serverUrl = serverUrl.trimEnd('/')
        _token = token
        _trustedCertSha = trustedCertSha
        context?.let { _appContext = it.applicationContext }
        _service = buildService()
    }

    fun setToken(token: String) { _token = token; _service = buildService() }
    fun clearToken() { _token = null; _service = buildService() }
    fun setTrustedCert(sha: String?) { _trustedCertSha = sha; _service = buildService() }

    fun service(): ApiService = _service ?: error("ApiClient not initialized")

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
                    cache(Cache(cacheDir, 10L * 1024 * 1024))
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
            // Cache GET responses for 5 minutes on the network side
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET") {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=300")
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
