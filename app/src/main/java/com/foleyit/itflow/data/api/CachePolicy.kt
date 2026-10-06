package com.foleyit.itflow.data.api

/**
 * Cache-Control value to set on a GET response before OkHttp stores it. Server directives win:
 * a response marked no-store or private is left exactly as the server sent it.
 * Returns null to leave the header untouched.
 */
fun cacheControlOverride(serverCacheControl: String?, successful: Boolean): String? {
    if (!successful) return "no-store"
    val directives = serverCacheControl?.lowercase().orEmpty()
    if (directives.contains("no-store") || directives.contains("private")) return null
    return "private, max-age=300"
}
