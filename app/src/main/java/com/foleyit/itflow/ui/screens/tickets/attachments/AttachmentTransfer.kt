package com.foleyit.itflow.ui.screens.tickets.attachments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.model.TicketAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.UUID

/** Metadata of a picked file as reported by its content provider. */
data class PickedFile(val name: String?, val size: Long, val mime: String?)

/**
 * Everything file-related that needs Android: app-private temp files, the FileProvider, picker metadata, and the
 * authenticated upload/download. Temp files live under the app cache and are wiped on sign-out
 * ([ApiClient] clears them with its HTTP cache) and when the ticket screen closes.
 */
object AttachmentTransfer {
    private const val DIR_DOWNLOADS = "attachments"
    private const val DIR_CAMERA = "camera"
    private const val MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024

    fun authority(ctx: Context) = "${ctx.packageName}.fileprovider"

    private fun dir(ctx: Context, name: String) = File(ctx.cacheDir, name).apply { mkdirs() }

    /**
     * Deletes downloaded and camera temp files (all of them, or only those not modified in the last [olderThanMs]).
     * Safe to call at any time.
     */
    fun clearTemp(ctx: Context, olderThanMs: Long = 0L) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        for (n in listOf(DIR_DOWNLOADS, DIR_CAMERA)) runCatching {
            val d = File(ctx.cacheDir, n)
            if (olderThanMs <= 0L) d.deleteRecursively()
            else d.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    /** A fresh app-private file for the camera app to write to, and the content URI that grants it access. */
    fun newCameraTarget(ctx: Context): Uri {
        val file = File(dir(ctx, DIR_CAMERA), "photo_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(ctx, authority(ctx), file)
    }

    fun describe(ctx: Context, uri: Uri): PickedFile {
        var name: String? = null
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (size < 0) size = runCatching { ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        return PickedFile(name, size, ctx.contentResolver.getType(uri))
    }

    /** Streams the file as multipart field `file`, reporting 0..100 as the body is written. Never loads it in memory. */
    suspend fun upload(ctx: Context, ticketId: Int, item: PendingAttachment, onProgress: (Int) -> Unit) {
        val uri = Uri.parse(item.uri)
        val body = object : RequestBody() {
            override fun contentType() = item.mime.toMediaTypeOrNull()
            override fun contentLength() = item.size
            override fun writeTo(sink: BufferedSink) {
                val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("File is no longer available")
                input.use { stream ->
                    val src = stream.source()
                    var sent = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = src.read(sink.buffer, 8192)
                        if (n == -1L) break
                        sent += n
                        sink.emitCompleteSegments()
                        val pct = if (item.size > 0) ((sent * 100) / item.size).toInt().coerceAtMost(100) else 0
                        if (pct != lastPercent) { lastPercent = pct; onProgress(pct) }
                    }
                }
            }
        }
        val part = MultipartBody.Part.createFormData("file", item.name, body)
        val id = ticketId.toString().toRequestBody("text/plain".toMediaType())
        val response = withContext(Dispatchers.IO) { ApiClient.service().uploadTicketAttachment(id, part) }
        val ok = response.get("ok")
        if (ok == null || ok.isJsonNull || (ok.isJsonPrimitive && ok.asJsonPrimitive.isBoolean && !ok.asBoolean)) {
            throw IOException("Upload was not confirmed")
        }
    }

    /** Downloads through the authenticated endpoint into the app cache and returns the file. */
    suspend fun download(ctx: Context, att: TicketAttachment): File = withContext(Dispatchers.IO) {
        val body = ApiClient.service().downloadTicketAttachment(att.id)
        body.use { b ->
            if (b.contentLength() > MAX_DOWNLOAD_BYTES) throw IOException("File is too large to open on this device")
            val target = File(dir(ctx, DIR_DOWNLOADS), "${att.id}_${AttachmentRules.safeFileName(att.name)}")
            // Never write outside the downloads dir, whatever the name contained.
            if (target.canonicalFile.parentFile != File(ctx.cacheDir, DIR_DOWNLOADS).canonicalFile) throw IOException("Unsafe file name")
            b.byteStream().use { input -> target.outputStream().use { out -> input.copyTo(out) } }
            target
        }
    }

    /** An ACTION_VIEW intent for a downloaded file through the FileProvider, or null when its type is not allowlisted. */
    fun viewIntent(ctx: Context, file: File, displayName: String): Intent? {
        val mime = AttachmentRules.openMime(displayName) ?: return null
        val uri = FileProvider.getUriForFile(ctx, authority(ctx), file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }
}
