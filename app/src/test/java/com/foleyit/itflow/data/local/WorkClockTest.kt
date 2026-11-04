package com.foleyit.itflow.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkClockTest {
    private val min = 60_000L

    @Test fun runningSessionCountsWallClockTime() {
        val s = WorkClockState().start(1, 101, "Printer jam", "Acme", now = 1_000L)
        assertEquals(5 * min, s.active!!.elapsedMs(1_000L + 5 * min))
    }

    @Test fun pauseFreezesTimeAndResumeContinuesIt() {
        var s = WorkClockState().start(1, 101, "Printer jam", null, now = 0L)
        s = s.pause(1, now = 10 * min)
        assertFalse(s.sessionFor(1)!!.running)
        assertEquals(10 * min, s.sessionFor(1)!!.elapsedMs(99 * min))
        s = s.start(1, 101, "Printer jam", null, now = 20 * min)
        assertEquals(15 * min, s.sessionFor(1)!!.elapsedMs(25 * min))
    }

    @Test fun startingAnotherTicketPausesTheRunningOneAndKeepsItsTime() {
        var s = WorkClockState().start(1, 101, "A", null, now = 0L)
        s = s.start(2, 102, "B", null, now = 30 * min)
        assertFalse(s.sessionFor(1)!!.running)
        assertEquals(30 * min, s.sessionFor(1)!!.elapsedMs(500 * min))
        assertEquals(2, s.active!!.ticketId)
        assertEquals(1, s.sessions.count { it.running })
    }

    @Test fun activeFallsBackToMostRecentPausedSession() {
        var s = WorkClockState().start(1, 101, "A", null, 0L).start(2, 102, "B", null, 5 * min)
        s = s.pause(2, 6 * min)
        assertEquals(2, s.active!!.ticketId)
    }

    @Test fun stopRemovesSessionAndItsLogRequest() {
        var s = WorkClockState().start(1, 101, "A", null, 0L).requestLog(1, 4 * min)
        assertEquals(1, s.logRequestFor)
        s = s.stop(1)
        assertNull(s.active)
        assertNull(s.logRequestFor)
    }

    @Test fun requestLogPausesTheSession() {
        val s = WorkClockState().start(1, 101, "A", null, 0L).requestLog(1, 7 * min)
        assertFalse(s.sessionFor(1)!!.running)
        assertEquals(7 * min, s.sessionFor(1)!!.accumulatedMs)
    }

    @Test fun clockGoingBackwardsNeverGivesNegativeTime() {
        val s = WorkClockState().start(1, 101, "A", null, now = 10_000L)
        assertEquals(0L, s.active!!.elapsedMs(5_000L))
    }

    @Test fun survivesEncodeDecode() {
        val s = WorkClockState().start(1, 101, "A \"quoted\"\nline", "Acme", 0L).start(2, 102, "B", null, 3 * min)
        val back = WorkClockState.decode(s.encode())
        assertEquals(s, back)
    }

    @Test fun garbageOrMissingStateDecodesToEmpty() {
        assertTrue(WorkClockState.decode(null).sessions.isEmpty())
        assertTrue(WorkClockState.decode("").sessions.isEmpty())
        assertTrue(WorkClockState.decode("{not json").sessions.isEmpty())
        assertTrue(WorkClockState.decode("{}").sessions.isEmpty())
    }

    @Test fun formatClock() {
        assertEquals("0:00:00", formatClock(0))
        assertEquals("0:01:05", formatClock(65_000))
        assertEquals("27:00:00", formatClock(27 * 3_600_000L))
        assertEquals("0:00:00", formatClock(-5))
    }

    @Test fun timeWorkedFieldRoundsToNearestMinuteAndSkipsTrivialTime() {
        assertEquals("", timeWorkedField(0))
        assertEquals("", timeWorkedField(29_000))
        assertEquals("00:01:00", timeWorkedField(30_000))
        assertEquals("00:45:00", timeWorkedField(45 * min))
        assertEquals("01:05:00", timeWorkedField(65 * min + 10_000))
    }
}
