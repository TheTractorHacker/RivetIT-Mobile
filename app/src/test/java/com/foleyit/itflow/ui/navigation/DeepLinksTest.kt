package com.foleyit.itflow.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `allows known top-level routes`() {
        val allowed = listOf(
            "tickets", "clients", "assets", "credentials", "quotes", "invoices",
            "expenses", "notifications", "appointments", "worksheets", "outtakes",
            "search", "reports", "scan", "profile", "kb", "alerts"
        )
        allowed.forEach {
            assertTrue("expected '$it' to be allowed", DeepLinks.ALLOWED_ROUTE.matches(it))
        }
    }

    @Test
    fun `allows a route with a numeric id`() {
        assertTrue(DeepLinks.ALLOWED_ROUTE.matches("tickets/42"))
        assertTrue(DeepLinks.ALLOWED_ROUTE.matches("clients/1"))
    }

    @Test
    fun `allows a route with a numeric id and a word suffix`() {
        assertTrue(DeepLinks.ALLOWED_ROUTE.matches("tickets/42/chat"))
    }

    @Test
    fun `rejects unknown top-level resource`() {
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("evil"))
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("../../malicious"))
    }

    @Test
    fun `rejects non-numeric id segment`() {
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("tickets/abc"))
    }

    @Test
    fun `rejects path traversal and injected segments`() {
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("tickets/1/../../credentials/1"))
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("tickets/1?evil=true"))
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches("tickets//1"))
    }

    @Test
    fun `rejects empty and blank input`() {
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches(""))
        assertFalse(DeepLinks.ALLOWED_ROUTE.matches(" "))
    }
}
