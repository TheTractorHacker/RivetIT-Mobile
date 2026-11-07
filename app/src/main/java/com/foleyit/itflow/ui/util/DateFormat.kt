package com.foleyit.itflow.ui.util

import java.text.SimpleDateFormat
import java.util.Locale

// SimpleDateFormat is costly to build and not thread-safe: one per thread, and results are remembered, because list rows
// call fmtDate every time they scroll into view (it used to build two formatters per call).
private fun formatter(pattern: String) = ThreadLocal.withInitial { SimpleDateFormat(pattern, Locale.US) }
private val parseDateTime = formatter("yyyy-MM-dd HH:mm:ss")
private val parseDate = formatter("yyyy-MM-dd")
private val showDateTime = formatter("MM/dd/yyyy h:mm a")
private val showDate = formatter("MM/dd/yyyy")

private val remembered = object : LinkedHashMap<String, String>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 512
}

/** Parse a server date/datetime string and return MM/dd/yyyy or MM/dd/yyyy h:mm a */
fun fmtDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    synchronized(remembered) { remembered[raw] }?.let { return it }
    val out = try {
        if (raw.length > 10) parseDateTime.get()!!.parse(raw)?.let { showDateTime.get()!!.format(it) } ?: raw
        else parseDate.get()!!.parse(raw)?.let { showDate.get()!!.format(it) } ?: raw
    } catch (_: Exception) { raw }
    synchronized(remembered) { remembered[raw] = out }
    return out
}
