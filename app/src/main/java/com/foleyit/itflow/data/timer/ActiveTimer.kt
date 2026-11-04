package com.foleyit.itflow.data.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one ticket timer the technician is running. It is app-level and saved to disk so it
 * survives leaving the ticket, switching tabs and the process being killed.
 *
 * [runningSince] is the wall-clock start of the current run, or null while paused;
 * [accumulatedSec] is everything banked before that run.
 */
data class TimerSnapshot(
    val ticketId: Int,
    val number: Int,
    val subject: String,
    val client: String?,
    val accumulatedSec: Long,
    val runningSince: Long?,
) {
    val running: Boolean get() = runningSince != null

    fun elapsedSec(nowMs: Long): Long {
        val live = runningSince?.let { ((nowMs - it) / 1000).coerceAtLeast(0) } ?: 0L
        return accumulatedSec + live
    }
}

/** 1:05:09 style clock for display. */
fun formatClock(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "${s / 3600}:${((s % 3600) / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

/** HH:MM:SS string the server's time_worked field takes. Seconds are dropped, as the web form does. */
fun timeWorkedString(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "${(s / 3600).toString().padStart(2, '0')}:${((s % 3600) / 60).toString().padStart(2, '0')}:00"
}

object ActiveTimer {
    private const val PREFS = "active_timer"
    private val _state = MutableStateFlow<TimerSnapshot?>(null)
    val state: StateFlow<TimerSnapshot?> = _state.asStateFlow()
    private var loaded = false

    /** Load the saved timer once per process. Safe to call from any screen. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("ticketId")) return
        _state.value = TimerSnapshot(
            ticketId = p.getInt("ticketId", 0),
            number = p.getInt("number", 0),
            subject = p.getString("subject", "") ?: "",
            client = p.getString("client", null),
            accumulatedSec = p.getLong("accumulatedSec", 0L),
            runningSince = if (p.contains("runningSince")) p.getLong("runningSince", 0L) else null,
        )
    }

    /** Start (or resume) the timer on a ticket. Returns false if another ticket's timer is in use. */
    @Synchronized
    fun start(context: Context, ticketId: Int, number: Int, subject: String, client: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        ensureLoaded(context)
        val cur = _state.value
        if (cur != null && cur.ticketId != ticketId) return false
        val next = if (cur == null) {
            TimerSnapshot(ticketId, number, subject, client, 0L, nowMs)
        } else if (cur.running) cur else cur.copy(runningSince = nowMs)
        publish(context, next)
        return true
    }

    @Synchronized
    fun pause(context: Context, nowMs: Long = System.currentTimeMillis()) {
        ensureLoaded(context)
        val cur = _state.value ?: return
        if (!cur.running) return
        publish(context, cur.copy(accumulatedSec = cur.elapsedSec(nowMs), runningSince = null))
    }

    @Synchronized
    fun clear(context: Context) {
        ensureLoaded(context)
        _state.value = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun publish(context: Context, s: TimerSnapshot) {
        _state.value = s
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            putInt("ticketId", s.ticketId)
            putInt("number", s.number)
            putString("subject", s.subject)
            s.client?.let { putString("client", it) }
            putLong("accumulatedSec", s.accumulatedSec)
            s.runningSince?.let { putLong("runningSince", it) }
        }.apply()
    }
}
