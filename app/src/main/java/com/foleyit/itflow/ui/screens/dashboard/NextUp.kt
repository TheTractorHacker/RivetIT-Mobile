package com.foleyit.itflow.ui.screens.dashboard

import com.foleyit.itflow.data.api.TicketSummary
import java.text.SimpleDateFormat
import java.util.Locale

/** Server datetime ("yyyy-MM-dd HH:mm:ss" or a bare date) to epoch millis, or null. */
internal fun parseDueMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        val fmt = if (raw.length > 10) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd"
        SimpleDateFormat(fmt, Locale.US).parse(raw)?.time
    } catch (_: Exception) { null }
}

internal fun priorityRank(priority: String?): Int = when (priority?.lowercase()) {
    "critical" -> 0
    "high" -> 1
    "medium" -> 2
    else -> 3
}

internal fun isOverdue(t: TicketSummary, nowMs: Long): Boolean =
    parseDueMillis(t.dueAt)?.let { it < nowMs } == true

/**
 * Order the technician's queue by what to do next: overdue first, then priority, then the
 * earliest due date (no due date last), then ticket number so the order is stable.
 */
internal fun rankQueue(queue: List<TicketSummary>, nowMs: Long): List<TicketSummary> =
    queue.sortedWith(
        compareBy<TicketSummary>(
            { if (isOverdue(it, nowMs)) 0 else 1 },
            { priorityRank(it.priority) },
            { parseDueMillis(it.dueAt) ?: Long.MAX_VALUE },
            { it.number },
        )
    )
