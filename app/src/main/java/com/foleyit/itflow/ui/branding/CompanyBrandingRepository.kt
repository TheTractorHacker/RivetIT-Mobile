package com.foleyit.itflow.ui.branding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.foleyit.itflow.data.ssl.FingerprintTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/** Only same-origin paths are allowed; branding requests must never send credentials. */
internal fun companyLogoUrl(server: String, path: String?): String? {
    if (path.isNullOrBlank()) return null
    val base = server.toHttpUrlOrNull() ?: return null
    val logo = base.resolve(path) ?: return null
    return logo.takeIf {
        it.scheme == base.scheme && it.host == base.host && it.port == base.port &&
            it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.startsWith("/uploads/settings/")
    }?.toString()
}

private fun decodeLogo(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Last company name seen, readable without Compose (notifications, widget, lock screen). */
object CompanyNameStore {
    private const val PREFS = "company_branding"
    fun last(context: android.content.Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString("name", null)?.takeIf { it.isNotBlank() }
    fun remember(context: android.content.Context, name: String) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString("name", name).apply()
    }
}

/** Loads cached public branding, then refreshes without changing API auth or server identity. */
internal class CompanyBrandingRepository(private val context: android.content.Context) {
    suspend fun load(server: String, certificate: String?, emit: (CompanyBranding) -> Unit) {
        val key = MessageDigest.getInstance("SHA-256").digest(server.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val folder = File(context.cacheDir, "company_branding").apply { mkdirs() }
        val metadata = File(folder, "$key.json")
        val image = File(folder, "$key.image")
        val cached = withContext(Dispatchers.IO) {
            runCatching {
                val data = JSONObject(metadata.readText())
                CompanyBranding(data.optString("name", "RivetIT"),
                    data.optString("logo_path").isNotBlank(), true,
                    if (image.exists()) decodeLogo(image.readBytes()) else null)
            }.getOrNull()
        }
        cached?.let { emit(it) }
        val refreshed = withContext(Dispatchers.IO) {
            runCatching {
                val trust = FingerprintTrustManager(certificate)
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                val client = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
                    .followRedirects(false).followSslRedirects(false)
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                fun download(url: String, limit: Int): ByteArray = client.newCall(
                    Request.Builder().url(url).get().build()).execute().use { response ->
                    check(response.isSuccessful)
                    val body = checkNotNull(response.body)
                    require(body.contentLength() <= limit)
                    body.byteStream().use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count == -1) break
                            require(out.size() + count <= limit)
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                }
                val data = JSONObject(String(download("${server.trimEnd('/')}/api/v1/branding", 16_384)))
                val name = data.optString("name").ifBlank { "RivetIT" }
                val path = if (data.isNull("logo_path")) "" else data.optString("logo_path")
                val url = companyLogoUrl(server, path)
                val bytes = url?.let { runCatching { download(it, 5 * 1024 * 1024) }.getOrNull() }
                val bitmap = bytes?.let(::decodeLogo)
                val previousPath = runCatching { JSONObject(metadata.readText()).optString("logo_path") }.getOrNull()
                val effectiveBitmap = bitmap ?: cached?.bitmap?.takeIf { path.isNotBlank() && previousPath == path }
                if (bitmap != null) image.writeBytes(checkNotNull(bytes))
                else if (path.isBlank() || previousPath != path) image.delete()
                metadata.writeText(JSONObject().put("name", name).put("logo_path", path).toString())
                CompanyNameStore.remember(context, name)
                CompanyBranding(name, path.isNotBlank(), true, effectiveBitmap)
            }.getOrNull()
        }
        emit(refreshed ?: cached ?: CompanyBranding(
            name = server.toHttpUrlOrNull()?.host ?: "Company",
            logoConfigured = true,
            fromServer = true,
        ))
    }
}
