package com.foleyit.itflow.ui.util

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

private data class ApiErrorBody(val error: String?)
private val gson = Gson()

/**
 * Maps a caught [Throwable] to a message safe to show a user. Backend error bodies
 * (`{"error": "..."}`) are authored for end users and are surfaced directly, but raw exception
 * internals (hostnames, stack traces, class names) are never shown — those get a generic message
 * instead, since they can leak backend/network internals.
 */
fun userMessage(e: Throwable): String = when (e) {
    is HttpException -> {
        val backendMessage = try {
            e.response()?.errorBody()?.string()
                ?.let { gson.fromJson(it, ApiErrorBody::class.java)?.error }
        } catch (_: Exception) { null }
        backendMessage?.takeIf { it.isNotBlank() } ?: when (e.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You don't have permission to do this."
            404 -> "Not found."
            in 500..599 -> "The server ran into a problem. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }
    is IOException -> "Network error — check your connection and try again."
    else -> "Something went wrong. Please try again."
}
