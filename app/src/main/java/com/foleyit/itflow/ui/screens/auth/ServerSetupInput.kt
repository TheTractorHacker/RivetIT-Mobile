package com.foleyit.itflow.ui.screens.auth

/** Pure helpers for the server-setup screen, kept free of Compose so they can be unit tested. */

/**
 * Tidies what a user typed into the server field: trims whitespace, drops a doubled
 * "https://" (the field is pre-filled with it), and prepends it when only a host was typed.
 * A plain "http://" address is left alone so the caller can still reject it.
 */
fun normalizeServerUrl(raw: String): String {
    var s = raw.trim()
    while (s.startsWith("https://", ignoreCase = true) &&
        s.substring(8).startsWith("https://", ignoreCase = true)
    ) {
        s = s.substring(8)
    }
    if (s.isEmpty() || s.equals("https://", ignoreCase = true)) return s
    if (!s.contains("://")) s = "https://$s"
    return s.trimEnd('/')
}

/** True when [input] is the last six hex characters of [fingerprint], with or without colons/spaces. */
fun fingerprintSuffixMatches(input: String, fingerprint: String): Boolean {
    fun clean(v: String) = v.filter { it != ':' && !it.isWhitespace() }
    val want = clean(fingerprint).takeLast(6)
    return want.isNotEmpty() && clean(input).equals(want, ignoreCase = true)
}
