package com.foleyit.itflow.ui.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

class ErrorMessagesTest {

    private fun httpException(code: Int, body: String?): HttpException {
        val responseBody = body?.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody ?: "".toResponseBody()))
    }

    @Test
    fun `surfaces backend authored error message from response body`() {
        val e = httpException(401, """{"error":"Current password is incorrect"}""")
        assertEquals("Current password is incorrect", userMessage(e))
    }

    @Test
    fun `falls back to a friendly message per status code when body has no error field`() {
        assertEquals("Your session has expired. Please sign in again.", userMessage(httpException(401, null)))
        assertEquals("You don't have permission to do this.", userMessage(httpException(403, null)))
        assertEquals("Not found.", userMessage(httpException(404, null)))
        assertEquals("The server ran into a problem. Please try again.", userMessage(httpException(500, null)))
    }

    @Test
    fun `blank error field falls back to status-based message rather than showing nothing`() {
        val e = httpException(404, """{"error":""}""")
        assertEquals("Not found.", userMessage(e))
    }

    @Test
    fun `network exceptions never leak raw internals like hostnames`() {
        val e = UnknownHostException("itflow.example.internal.corp: nodename nor servname provided")
        val msg = userMessage(e)
        assertEquals("Network error — check your connection and try again.", msg)
        assertFalse(msg.contains("itflow.example.internal.corp"))
    }

    @Test
    fun `generic IOException maps to the network error message`() {
        assertEquals("Network error — check your connection and try again.", userMessage(IOException("connection reset")))
    }

    @Test
    fun `unrecognized exceptions get a generic message, not their raw text`() {
        val e = IllegalStateException("NullPointerException at com.foleyit.itflow.internal.Secret:42")
        val msg = userMessage(e)
        assertEquals("Something went wrong. Please try again.", msg)
        assertFalse(msg.contains("Secret"))
    }
}
