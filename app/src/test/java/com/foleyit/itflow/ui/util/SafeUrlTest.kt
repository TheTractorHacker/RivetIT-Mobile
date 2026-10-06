package com.foleyit.itflow.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeUrlTest {
    @Test fun `allows http and https`() {
        assertTrue(isSafeWebUrl("https://example.com/a?b=1"))
        assertTrue(isSafeWebUrl("HTTP://example.com"))
    }

    @Test fun `rejects other schemes and junk`() {
        listOf(
            "intent://x#Intent;scheme=http;end", "javascript:alert(1)", "file:///etc/passwd",
            "content://com.x/y", "tel:123", "example.com", "https://", "", "  ", "http://exa mple.com"
        ).forEach { assertFalse(it, isSafeWebUrl(it)) }
        assertFalse(isSafeWebUrl(null))
    }
}
