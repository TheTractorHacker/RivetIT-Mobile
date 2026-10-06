package com.foleyit.itflow.ui.screens.tickets.attachments

import java.util.Locale

enum class FileKind { IMAGE, PDF, DOCUMENT, SHEET, ARCHIVE, TEXT, MEDIA, OTHER }

sealed interface Rejection {
    data class Extension(val ext: String) : Rejection
    data class TooLarge(val maxBytes: Long) : Rejection
    data object Empty : Rejection
    data object TooMany : Rejection
    data object Duplicate : Rejection
}

/**
 * Client-side attachment rules. The extension allowlist is the web app's agent-side ticket attachment list
 * (`agent/post/ticket.php`), so a file the phone accepts is one the server accepts. The server remains the authority.
 */
object AttachmentRules {
    val ALLOWED_EXTENSIONS: Set<String> = setOf(
        "jpg", "jpeg", "gif", "png", "webp", "pdf", "txt", "md", "doc", "docx", "odt", "csv", "xls", "xlsx", "ods",
        "pptx", "odp", "zip", "tar", "gz", "xml", "msg", "json", "wav", "mp3", "ogg", "mov", "mp4", "av1", "ovpn",
    )

    /** Used when the API does not report a limit. */
    const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024
    const val MAX_FILES = 10

    private val DENIED_SEGMENT = Regex("""php\d?|phtml|pht|phar|phps|cgi|pl|py|sh|bash|asp|aspx|jsp|exe|dll|bat|cmd|com|scr|html?|xhtml|js|svg""")
    private val IMAGE = setOf("jpg", "jpeg", "gif", "png", "webp")

    fun extensionOf(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        return if (dot <= 0 || dot == base.length - 1) "" else base.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /**
     * A file name that is safe to use on disk and to show: no path segments, no control or reserved characters, no
     * leading dots, bounded length, extension preserved. Never empty.
     */
    fun safeFileName(raw: String?, fallback: String = "attachment"): String {
        var name = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        name = name.filter { !it.isISOControl() }
            .replace(Regex("""[<>:"|?*\u0000]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart('.')
            .trim()
        if (name.isEmpty()) return fallback
        if (name.length > 100) {
            val ext = extensionOf(name)
            val stem = name.substring(0, name.length - if (ext.isEmpty()) 0 else ext.length + 1)
            val keep = 100 - (if (ext.isEmpty()) 0 else ext.length + 1)
            name = stem.take(keep.coerceAtLeast(1)) + if (ext.isEmpty()) "" else ".$ext"
        }
        return name
    }

    /** Null when the file may be added. [queued] is the number of files already in the queue. */
    fun check(name: String, sizeBytes: Long, maxBytes: Long, queued: Int, existingNames: Collection<String> = emptyList()): Rejection? {
        if (queued >= MAX_FILES) return Rejection.TooMany
        val ext = extensionOf(name)
        if (ext !in ALLOWED_EXTENSIONS) return Rejection.Extension(ext)
        // Like the server: "shell.php.png" is refused because a middle segment is a script/executable type.
        val middle = name.substringAfterLast('/').lowercase(Locale.ROOT).split('.').drop(1).dropLast(1)
        middle.firstOrNull { DENIED_SEGMENT.matches(it) }?.let { return Rejection.Extension(it) }
        if (sizeBytes == 0L) return Rejection.Empty
        if (sizeBytes > maxBytes) return Rejection.TooLarge(maxBytes)
        if (existingNames.any { it.equals(name, ignoreCase = true) }) return Rejection.Duplicate
        return null
    }

    fun kindOf(name: String, mime: String? = null): FileKind {
        val ext = extensionOf(name)
        val m = mime.orEmpty().lowercase(Locale.ROOT)
        return when {
            ext in IMAGE || m.startsWith("image/") -> FileKind.IMAGE
            ext == "pdf" || m == "application/pdf" -> FileKind.PDF
            ext in setOf("doc", "docx", "odt", "msg", "pptx", "odp") -> FileKind.DOCUMENT
            ext in setOf("xls", "xlsx", "ods", "csv") -> FileKind.SHEET
            ext in setOf("zip", "tar", "gz") -> FileKind.ARCHIVE
            ext in setOf("txt", "md", "json", "xml", "ovpn") -> FileKind.TEXT
            ext in setOf("wav", "mp3", "ogg", "mov", "mp4", "av1") || m.startsWith("audio/") || m.startsWith("video/") -> FileKind.MEDIA
            else -> FileKind.OTHER
        }
    }

    private val MIME_BY_EXT = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "pdf" to "application/pdf", "txt" to "text/plain", "md" to "text/markdown", "csv" to "text/csv",
        "json" to "application/json", "xml" to "application/xml",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "zip" to "application/zip", "tar" to "application/x-tar", "gz" to "application/gzip",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "mp4" to "video/mp4", "mov" to "video/quicktime",
    )

    /**
     * The MIME type to open a downloaded file with. Derived from the (allowlisted) extension so a server-supplied
     * type cannot steer the file to an unexpected handler; null means "do not open" (extension not allowlisted).
     */
    fun openMime(name: String): String? {
        val ext = extensionOf(name)
        if (ext !in ALLOWED_EXTENSIONS) return null
        return MIME_BY_EXT[ext] ?: "application/octet-stream"
    }

    /** MIME for the upload part: the picker's type when it looks sane, else from the extension. */
    fun uploadMime(name: String, pickerMime: String?): String =
        pickerMime?.takeIf { Regex("""[\w.+-]+/[\w.+-]+""").matches(it) } ?: MIME_BY_EXT[extensionOf(name)] ?: "application/octet-stream"

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
