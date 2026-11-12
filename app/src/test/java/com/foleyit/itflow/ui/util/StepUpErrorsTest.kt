package com.foleyit.itflow.ui.util

import com.foleyit.itflow.data.api.BiometricKeyRequest
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class StepUpErrorsTest {

    private fun http(code: Int, error: String?, retryAfter: String? = null): HttpException {
        val body = (if (error == null) "" else """{"error":"$error"}""").toResponseBody("application/json".toMediaType())
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.test/api/v1/me").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("x")
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
            .build()
        return HttpException(Response.error<Any>(body, raw))
    }

    // --- request body ---

    @Test
    fun `body carries the password and omits totp_code when not needed`() {
        val json = JsonParser.parseString(Gson().toJson(BiometricKeyRequest("PEM", "hunter2"))).asJsonObject
        assertEquals("PEM", json["device_public_key_pem"].asString)
        assertEquals("hunter2", json["current_password"].asString)
        assertFalse(json.has("totp_code"))
    }

    @Test
    fun `body carries totp_code when given`() {
        val json = JsonParser.parseString(Gson().toJson(BiometricKeyRequest("PEM", "hunter2", "123456"))).asJsonObject
        assertEquals("123456", json["totp_code"].asString)
    }

    @Test
    fun `secrets never appear in toString`() {
        val s = BiometricKeyRequest("PEM", "hunter2", "123456").toString()
        assertFalse(s.contains("hunter2"))
        assertFalse(s.contains("123456"))
    }

    // --- enrolment error mapping ---

    @Test
    fun `wrong password stays on the prompt`() {
        val f = classifyEnrolFailure(http(401, "Current password is required to register a biometric key"))
        assertTrue(f is EnrolFailure.WrongPassword)
    }

    @Test
    fun `MFA required asks for the code`() {
        val f = classifyEnrolFailure(http(401, "MFA code required to register a biometric key"))
        assertTrue(f is EnrolFailure.NeedsCode)
    }

    @Test
    fun `429 maps to a try again message using Retry-After`() {
        val f = classifyEnrolFailure(http(429, "Too many attempts. Try again later.", "600")) as EnrolFailure.RateLimited
        assertEquals("Too many attempts. Try again in about 10 minutes.", f.message)
    }

    @Test
    fun `other failures use the normal error UI`() {
        assertTrue(classifyEnrolFailure(http(500, null)) is EnrolFailure.Other)
        assertTrue(classifyEnrolFailure(java.io.IOException("x")) is EnrolFailure.Other)
        assertTrue(classifyEnrolFailure(http(401, "something else")) is EnrolFailure.Other)
    }

    // --- reveal 429 and key-missing detection ---

    @Test
    fun `reveal 429 gives a clear message with and without Retry-After`() {
        assertEquals("Too many attempts. Try again in about 10 minutes.", userMessage(http(429, "Credential reveal limit reached. Try again later.", "600")))
        assertEquals("Too many attempts. Try again in a few minutes.", userMessage(http(429, null)))
        assertEquals("Too many attempts. Try again in about a minute.", userMessage(http(429, null, "30")))
        assertNull(retryAfterSeconds(http(429, null, "Wed, 21 Oct 2026 07:28:00 GMT")))
    }

    @Test
    fun `reveal refusal for a missing or stale key triggers enrolment`() {
        assertTrue(revealNeedsKeyEnrolment(http(403, "No biometric key registered for this device")))
        assertTrue(revealNeedsKeyEnrolment(http(403, "Biometric verification failed")))
        assertFalse(revealNeedsKeyEnrolment(http(403, "Access denied")))
        assertFalse(revealNeedsKeyEnrolment(http(404, "Credential not found")))
    }
}
