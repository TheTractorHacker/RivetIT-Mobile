package com.foleyit.itflow.data

import com.foleyit.itflow.data.api.TicketSummary
import com.foleyit.itflow.data.timer.TimerSnapshot
import com.foleyit.itflow.data.timer.formatClock
import com.foleyit.itflow.data.timer.timeWorkedString
import com.foleyit.itflow.ui.screens.dashboard.isOverdue
import com.foleyit.itflow.ui.screens.dashboard.parseDueMillis
import com.foleyit.itflow.ui.screens.dashboard.rankQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTimerTest {
    private fun snap(acc: Long, since: Long?) = TimerSnapshot(1, 257, "s", null, acc, since)

    @Test fun runningTimerAddsLiveTimeToBanked() {
        assertEquals(125L, snap(65, 1_000).elapsedSec(61_000))
    }

    @Test fun pausedTimerIgnoresClock() {
        val s = snap(65, null)
        assertFalse(s.running)
        assertEquals(65L, s.elapsedSec(9_999_999))
    }

    @Test fun clockGoingBackwardsNeverGoesNegative() {
        assertEquals(10L, snap(10, 50_000).elapsedSec(1_000))
    }

    @Test fun formatsClockAndServerTime() {
        assertEquals("1:05:09", formatClock(3909))
        assertEquals("0:00:07", formatClock(7))
        assertEquals("01:05:00", timeWorkedString(3909))
        assertEquals("00:00:00", timeWorkedString(-5))
    }
}

class NextUpTest {
    private fun t(n: Int, priority: String?, due: String?) =
        TicketSummary(n, n, "t$n", priority, null, null, null, null, null, due, null)

    private val now = parseDueMillis("2026-11-03 12:00:00")!!

    @Test fun parsesDatetimeAndBareDate() {
        assertTrue(parseDueMillis("2026-11-03 12:00:00")!! > parseDueMillis("2026-11-03")!!)
        assertNull(parseDueMillis(""))
        assertNull(parseDueMillis("not a date"))
        assertNull(parseDueMillis(null))
    }

    @Test fun overdueBeatsPriority() {
        val ranked = rankQueue(
            listOf(t(1, "Critical", "2026-11-09 09:00:00"), t(2, "Low", "2026-11-01 09:00:00")),
            now
        )
        assertEquals(listOf(2, 1), ranked.map { it.number })
    }

    @Test fun thenPriorityThenDueThenNumber() {
        val ranked = rankQueue(
            listOf(
                t(5, "Low", null),
                t(4, "High", "2026-11-10 09:00:00"),
                t(3, "High", "2026-11-05 09:00:00"),
                t(2, "High", "2026-11-05 09:00:00"),
                t(1, "Critical", null),
            ),
            now
        )
        assertEquals(listOf(1, 2, 3, 4, 5), ranked.map { it.number })
    }

    @Test fun noDueDateIsNotOverdue() {
        assertFalse(isOverdue(t(1, "High", null), now))
        assertTrue(isOverdue(t(1, "High", "2026-11-03 11:59:59"), now))
    }
}
