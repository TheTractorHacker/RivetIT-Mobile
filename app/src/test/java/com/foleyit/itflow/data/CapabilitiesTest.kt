package com.foleyit.itflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    @Test
    fun `server that reports no permissions keeps everything visible`() {
        val caps = Capabilities(isAdmin = false, permissions = null)
        assertTrue(caps.canView(Capabilities.CREDENTIAL))
        assertTrue(caps.canWrite(Capabilities.SUPPORT))
    }

    @Test
    fun `admin sees and changes everything regardless of the map`() {
        val caps = Capabilities(isAdmin = true, permissions = mapOf(Capabilities.KB to 0))
        assertTrue(caps.canView(Capabilities.KB))
        assertTrue(caps.canWrite(Capabilities.KB))
    }

    @Test
    fun `read-only level can view but not write`() {
        val caps = Capabilities(permissions = mapOf(Capabilities.SUPPORT to 1))
        assertTrue(caps.canView(Capabilities.SUPPORT))
        assertFalse(caps.canWrite(Capabilities.SUPPORT))
    }

    @Test
    fun `write and full levels can change`() {
        val caps = Capabilities(permissions = mapOf(Capabilities.SUPPORT to 2, Capabilities.CLIENT to 3))
        assertTrue(caps.canWrite(Capabilities.SUPPORT))
        assertTrue(caps.canWrite(Capabilities.CLIENT))
    }

    @Test
    fun `none or missing module is hidden`() {
        val caps = Capabilities(permissions = mapOf(Capabilities.CREDENTIAL to 0))
        assertFalse(caps.canView(Capabilities.CREDENTIAL))
        assertFalse(caps.canView(Capabilities.KB))
    }
}
