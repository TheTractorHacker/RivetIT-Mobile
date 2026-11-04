package com.foleyit.itflow.data.local

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One ticket the technician is clocking time against. [runningSince] is null while paused. */
data class WorkSession(
    val ticketId: Int,
    val number: Int,
    val subject: String,
    val client: String?,
    val accumulatedMs: Long = 0L,
    val runningSince: Long? = null,
) {
    val running: Boolean get() = runningSince != null

    fun elapsedMs(now: Long): Long =
        accumulatedMs + (runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
}

/**
 * All of the technician's open work sessions. At most one runs at a time (starting another pauses
 * it, like switching tickets on a job), the rest stay paused with their time kept.
 * Immutable; every operation returns the next state so the rules are unit-testable without Android.
 */
data class WorkClockState(
    val sessions: List<WorkSession> = emptyList(),
    /** Ticket whose reply sheet should open the next time its detail screen is shown. */
    val logRequestFor: Int? = null,
) {
    /** The session to show in the bar: the running one, else the most recently touched paused one. */
    val active: WorkSession? get() = sessions.firstOrNull { it.running } ?: sessions.lastOrNull()

    fun sessionFor(ticketId: Int): WorkSession? = sessions.firstOrNull { it.ticketId == ticketId }

    /** Start (or resume) [ticketId]; any other running session is paused first. */
    fun start(ticketId: Int, number: Int, subject: String, client: String?, now: Long): WorkClockState {
        val paused = pauseAll(now).sessions
        val existing = paused.firstOrNull { it.ticketId == ticketId }
        val started = (existing ?: WorkSession(ticketId, number, subject, client)).copy(runningSince = now)
        return copy(sessions = paused.filterNot { it.ticketId == ticketId } + started)
    }

    fun pause(ticketId: Int, now: Long): WorkClockState = copy(
        sessions = sessions.map { if (it.ticketId == ticketId) it.paused(now) else it },
    )

    fun pauseAll(now: Long): WorkClockState = copy(sessions = sessions.map { it.paused(now) })

    /** Drop a session, e.g. once its time has been logged on the ticket or the user discards it. */
    fun stop(ticketId: Int): WorkClockState = copy(
        sessions = sessions.filterNot { it.ticketId == ticketId },
        logRequestFor = logRequestFor.takeUnless { it == ticketId },
    )

    fun requestLog(ticketId: Int, now: Long): WorkClockState = pause(ticketId, now).copy(logRequestFor = ticketId)

    private fun WorkSession.paused(now: Long): WorkSession =
        if (runningSince == null) this else copy(accumulatedMs = elapsedMs(now), runningSince = null)

    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(json: String?): WorkClockState {
            if (json.isNullOrBlank()) return WorkClockState()
            return runCatching { Gson().fromJson(json, WorkClockState::class.java) }.getOrNull()
                ?.let { s -> s.copy(sessions = s.sessions.orEmpty()) }
                ?: WorkClockState()
        }
    }
}

/** HH:MM:SS for display, no wrap at 24h. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/**
 * Value for the reply sheet's "time worked" field (HH:MM:SS, seconds always 00, which is what the
 * server's time_worked column takes). Rounds to the nearest minute; under 30 s there is nothing to log.
 */
fun timeWorkedField(ms: Long): String {
    val minutes = (ms + 30_000L) / 60_000L
    if (minutes <= 0) return ""
    return "%02d:%02d:00".format(minutes / 60, minutes % 60)
}

/**
 * Process-wide holder, persisted so a running session survives leaving the ticket, the app being
 * swiped away and the phone rebooting (the start time is wall-clock, so time keeps counting).
 */
object WorkClock {
    private const val PREFS = "work_clock"
    private const val KEY = "state"

    private val _state = MutableStateFlow(WorkClockState())
    val state: StateFlow<WorkClockState> = _state.asStateFlow()

    private var appContext: Context? = null

    @Synchronized
    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _state.value = WorkClockState.decode(prefs()?.getString(KEY, null))
    }

    fun start(ticketId: Int, number: Int, subject: String, client: String?) =
        mutate { it.start(ticketId, number, subject, client, System.currentTimeMillis()) }

    fun pause(ticketId: Int) = mutate { it.pause(ticketId, System.currentTimeMillis()) }
    fun stop(ticketId: Int) = mutate { it.stop(ticketId) }
    fun requestLog(ticketId: Int) = mutate { it.requestLog(ticketId, System.currentTimeMillis()) }

    /** True once if a log was requested for [ticketId]; the request is cleared by the read. */
    fun consumeLogRequest(ticketId: Int): Boolean {
        if (_state.value.logRequestFor != ticketId) return false
        mutate { it.copy(logRequestFor = null) }
        return true
    }

    /** Sign-out: nothing from one account's day may carry into the next sign-in. */
    fun clear() = mutate { WorkClockState() }

    @Synchronized
    private fun mutate(change: (WorkClockState) -> WorkClockState) {
        val next = change(_state.value)
        _state.value = next
        prefs()?.edit()?.putString(KEY, next.encode())?.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
