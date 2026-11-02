package com.foleyit.itflow.data.attachments

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** What the user picked: a content Uri plus the name and size the system reported for it. */
data class PickedFile(val uri: Uri, val name: String, val size: Long)

/** Mirrors the server's ticket upload rules (api/v1/tickets.php api_save_ticket_attachments + checkFileUpload). */
object AttachmentRules {
    const val MAX_BYTES = 500L * 1024 * 1024
    const val MAX_FILES = 10
    val ALLOWED_EXTENSIONS = setOf(
        "jpg", "jpeg", "gif", "png", "webp", "pdf", "txt", "md", "doc", "docx", "odt", "csv", "xls", "xlsx", "ods",
        "pptx", "odp", "zip", "tar", "gz", "xml", "msg", "json", "wav", "mp3", "ogg", "mov", "mp4", "av1", "ovpn",
    )

    /** A user-facing reason the file cannot be sent, or null when it passes. */
    fun problem(name: String, size: Long): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension.isEmpty() || extension !in ALLOWED_EXTENSIONS ->
                "\"$name\" is not an allowed file type" + if (extension.isEmpty()) "" else " (.$extension)"
            size <= 0L -> "\"$name\" is empty"
            size > MAX_BYTES -> "\"$name\" is larger than 500 MB"
            else -> null
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }
}

/**
 * Attachment links come from the server as paths like `/uploads/tickets/12/abc.pdf`. Only a same-origin
 * http(s) address under /uploads/tickets/ is ever opened; anything else (other host, other scheme, userinfo,
 * a path outside the upload folder) returns null.
 */
fun ticketAttachmentUrl(server: String, path: String?): String? {
    if (path.isNullOrBlank()) return null
    val base = server.toHttpUrlOrNull() ?: return null
    val link = base.resolve(path) ?: return null
    return link.takeIf {
        it.scheme == base.scheme && it.host == base.host && it.port == base.port &&
            it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.startsWith("/uploads/tickets/") && ".." !in it.pathSegments
    }?.toString()
}

/** Reads the display name and size the provider reports; null when the provider gives no usable answer. */
fun ContentResolver.describe(uri: Uri): PickedFile? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return null
        val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
        PickedFile(uri, name, size)
    }

/** Streams a picked file from its Uri (never loading it whole into memory) and reports bytes written. */
class StreamingFileBody(
    private val resolver: ContentResolver,
    private val file: PickedFile,
    private val onBytes: (Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? =
        (resolver.getType(file.uri) ?: "application/octet-stream").toMediaTypeOrNull()
    override fun contentLength(): Long = file.size
    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri) ?: throw java.io.IOException("Cannot read ${file.name}")
        input.source().use { source ->
            var total = 0L
            while (true) {
                val read = source.read(sink.buffer, 8192)
                if (read == -1L) break
                total += read
                sink.emit()
                onBytes(total)
            }
        }
    }

    fun part(): MultipartBody.Part = MultipartBody.Part.createFormData("files[]", file.name, this)
}
