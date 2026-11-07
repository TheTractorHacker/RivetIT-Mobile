package com.foleyit.itflow.ui

import com.foleyit.itflow.ui.util.fmtDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatTest {
    @Test fun formatsDatetimeAndBareDate() {
        assertEquals("11/03/2026 12:05 PM", fmtDate("2026-11-03 12:05:09"))
        assertEquals("11/03/2026", fmtDate("2026-11-03"))
    }
    @Test fun blankAndUnparseableAreSafe() {
        assertEquals("", fmtDate(null)); assertEquals("", fmtDate("  "))
        assertEquals("not a date", fmtDate("not a date"))
    }
    @Test fun repeatedCallsGiveTheSameAnswer() {
        assertEquals(fmtDate("2026-11-03 09:00:00"), fmtDate("2026-11-03 09:00:00"))
    }
    @Test fun worksFromSeveralThreads() {
        val results = java.util.Collections.synchronizedList(mutableListOf<String>())
        (1..8).map { Thread { repeat(200) { results += fmtDate("2026-11-03 09:00:00") } }.also { it.start() } }.forEach { it.join() }
        assertEquals(setOf("11/03/2026 9:00 AM"), results.toSet())
    }
}
