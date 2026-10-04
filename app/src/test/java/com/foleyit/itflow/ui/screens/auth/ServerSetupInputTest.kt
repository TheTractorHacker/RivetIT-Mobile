package com.foleyit.itflow.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSetupInputTest {

    @Test
    fun `keeps a well formed https url and drops trailing slash`() {
        assertEquals("https://10.1.0.45:8444", normalizeServerUrl("https://10.1.0.45:8444/"))
    }

    @Test
    fun `collapses a doubled scheme`() {
        assertEquals("https://example.com", normalizeServerUrl("https://https://example.com"))
        assertEquals("https://example.com", normalizeServerUrl(" HTTPS://https://example.com "))
    }

    @Test
    fun `adds https to a bare host`() {
        assertEquals("https://example.com:8445", normalizeServerUrl("example.com:8445"))
    }

    @Test
    fun `leaves empty and prefix-only input alone`() {
        assertEquals("", normalizeServerUrl("  "))
        assertEquals("https://", normalizeServerUrl("https://"))
    }

    @Test
    fun `does not upgrade plain http`() {
        assertEquals("http://example.com", normalizeServerUrl("http://example.com"))
    }

    @Test
    fun `fingerprint suffix accepts colon, plain and spaced forms`() {
        val fp = "16:59:E5:18:FC:27:E7:AF:03:73:5B:39:05:7F:50:C6:AE:5C:4C:74:C1:FC:DE:4F:7C:BA:5C:CF:B0:61:7D:49"
        assertTrue(fingerprintSuffixMatches("61:7D:49", fp))
        assertTrue(fingerprintSuffixMatches("617d49", fp))
        assertTrue(fingerprintSuffixMatches(" 61 7D 49 ", fp))
    }

    @Test
    fun `fingerprint suffix rejects wrong or empty input`() {
        val fp = "16:59:E5:18:FC:27:E7:AF:03:73:5B:39:05:7F:50:C6:AE:5C:4C:74:C1:FC:DE:4F:7C:BA:5C:CF:B0:61:7D:49"
        assertFalse(fingerprintSuffixMatches("617D4", fp))
        assertFalse(fingerprintSuffixMatches("000000", fp))
        assertFalse(fingerprintSuffixMatches("", fp))
        assertFalse(fingerprintSuffixMatches("617D49", ""))
    }
}
