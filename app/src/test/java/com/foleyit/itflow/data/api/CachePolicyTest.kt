package com.foleyit.itflow.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachePolicyTest {
    @Test fun `server no-store is honored`() {
        assertNull(cacheControlOverride("no-store", true))
        assertNull(cacheControlOverride("No-Store, must-revalidate", true))
    }

    @Test fun `server private is honored`() {
        assertNull(cacheControlOverride("private, max-age=0", true))
    }

    @Test fun `responses without a directive get the default`() {
        assertEquals("private, max-age=300", cacheControlOverride(null, true))
        assertEquals("private, max-age=300", cacheControlOverride("max-age=60", true))
    }

    @Test fun `failures are never cached`() {
        assertEquals("no-store", cacheControlOverride(null, false))
        assertEquals("no-store", cacheControlOverride("max-age=600", false))
    }
}
