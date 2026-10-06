package com.foleyit.itflow.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/** True only for absolute http(s) URLs with a host. Rejects intent:, javascript:, file:, content: etc. */
fun isSafeWebUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = try { URI(url.trim()) } catch (_: Exception) { return false }
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}

/** Opens an API-supplied URL in an external app, http(s) only; never throws. */
fun openWebUrl(context: Context, url: String?) {
    if (!isSafeWebUrl(url)) {
        android.widget.Toast.makeText(context, "Unsupported link", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url!!.trim())))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app found to open this link", android.widget.Toast.LENGTH_SHORT).show()
    }
}
