package com.foleyit.itflow.ui.util

import com.google.gson.Gson
import retrofit2.HttpException

private data class StepUpErrorBody(val error: String?)

/** The server's `{"error": "..."}` text for an HTTP failure, or null. */
internal fun backendMessage(e: HttpException): String? = try {
    e.response()?.errorBody()?.string()
        ?.let { Gson().fromJson(it, StepUpErrorBody::class.java)?.error }
        ?.takeIf { it.isNotBlank() }
} catch (_: Exception) { null }

/** Seconds from a numeric `Retry-After` header, or null when absent or not a number. */
fun retryAfterSeconds(e: HttpException): Int? =
    e.response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }

fun rateLimitMessage(retryAfterSeconds: Int?): String {
    val wait = when {
        retryAfterSeconds == null -> "in a few minutes"
        retryAfterSeconds < 90 -> "in about a minute"
        else -> "in about ${(retryAfterSeconds + 59) / 60} minutes"
    }
    return "Too many attempts. Try again $wait."
}

/** What the step-up key enrolment answered, mapped to what the prompt should do. */
sealed interface EnrolFailure {
    /** Wrong or missing password: stay on the prompt. */
    data class WrongPassword(val message: String) : EnrolFailure
    /** The account has MFA and no (valid) code was sent: show the code field. */
    data class NeedsCode(val message: String) : EnrolFailure
    data class RateLimited(val message: String) : EnrolFailure
    /** Anything else: use the normal error UI. */
    data class Other(val error: Throwable) : EnrolFailure
}

fun classifyEnrolFailure(e: Throwable): EnrolFailure {
    if (e !is HttpException) return EnrolFailure.Other(e)
    return when (e.code()) {
        429 -> EnrolFailure.RateLimited(rateLimitMessage(retryAfterSeconds(e)))
        401 -> {
            val msg = backendMessage(e).orEmpty()
            when {
                msg.startsWith("MFA code required", ignoreCase = true) ->
                    EnrolFailure.NeedsCode("Enter your authenticator code or a recovery code.")
                msg.startsWith("Current password", ignoreCase = true) ->
                    EnrolFailure.WrongPassword("That password is not correct.")
                else -> EnrolFailure.Other(e)
            }
        }
        else -> EnrolFailure.Other(e)
    }
}

/**
 * True when a credential reveal was refused because the server has no usable step-up key for this
 * account (never enrolled, or the device key was regenerated), so enrolling again can fix it.
 */
fun revealNeedsKeyEnrolment(e: Throwable): Boolean {
    if (e !is HttpException || e.code() != 403) return false
    val msg = backendMessage(e) ?: return false
    return msg.startsWith("No biometric key", true) ||
        msg.startsWith("Biometric verification failed", true) ||
        msg.startsWith("Invalid registered biometric key", true)
}
