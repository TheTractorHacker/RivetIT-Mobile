package com.foleyit.itflow

import android.app.Application
import com.foleyit.itflow.crash.CrashReporter
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.push.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ITFlowApplication : Application() {

    // Single shared instance — use (application as ITFlowApplication).prefs everywhere
    lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else so a crash during the init steps below is
        // captured too.
        CrashReporter.install(this)
        prefs = AppPreferences(this)
        NotificationHelper.createChannel(this)
        runBlocking {
            val url     = prefs.serverUrl.first()
            val token   = prefs.authToken.first()
            val certSha = prefs.trustedCertSha.first()
            if (url.isNotBlank()) {
                ApiClient.init(url, token, certSha, context = this@ITFlowApplication)
            }
        }
        // Best-effort report of a crash saved by CrashReporter.install() on the
        // previous run; needs ApiClient initialized above, so it comes after.
        CrashReporter.sendPendingCrashIfAny(this)
    }
}
