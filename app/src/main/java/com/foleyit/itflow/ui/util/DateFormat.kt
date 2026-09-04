package com.foleyit.itflow.ui.util

import java.text.SimpleDateFormat
import java.util.Locale

/** Parse a server date/datetime string and return MM/dd/yyyy or MM/dd/yyyy h:mm a */
fun fmtDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        if (raw.length > 10) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)?.let {
                SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.US).format(it)
            } ?: raw
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)?.let {
                SimpleDateFormat("MM/dd/yyyy", Locale.US).format(it)
            } ?: raw
        }
    } catch (_: Exception) { raw }
}
