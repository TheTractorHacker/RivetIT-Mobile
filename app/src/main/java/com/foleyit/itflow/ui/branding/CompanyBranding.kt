package com.foleyit.itflow.ui.branding

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.foleyit.itflow.data.local.AppPreferences
import kotlinx.coroutines.flow.map

/** Brand data is public, scoped to the selected server, and never contains an auth token. */
data class CompanyBranding(
    val name: String = "RivetIT",
    val logoConfigured: Boolean = false,
    /** True once the name came from the connected server; the RivetIT mark is then never shown. */
    val fromServer: Boolean = false,
    val bitmap: Bitmap? = null,
    val loading: Boolean = false,
)
val LocalCompanyBranding = staticCompositionLocalOf { CompanyBranding() }

@Composable
fun CompanyBrandingProvider(prefs: AppPreferences, content: @Composable () -> Unit) {
    val serverFlow = remember(prefs) { prefs.serverUrl.map<String, String?> { it } }
    val server by serverFlow.collectAsState(initial = null)
    val certificate by prefs.trustedCertSha.collectAsState(initial = null)
    val context = LocalContext.current.applicationContext
    // Reset synchronously on server/certificate changes; never flash the previous tenant's logo.
    var branding by remember(server, certificate) { mutableStateOf(CompanyBranding(loading = server == null || !server.isNullOrBlank())) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeCount++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(server, certificate, resumeCount) {
        val selectedServer = server ?: return@LaunchedEffect
        if (selectedServer.isBlank()) return@LaunchedEffect
        CompanyBrandingRepository(context).load(selectedServer, certificate) { branding = it }
    }
    CompositionLocalProvider(LocalCompanyBranding provides branding, content = content)
}
