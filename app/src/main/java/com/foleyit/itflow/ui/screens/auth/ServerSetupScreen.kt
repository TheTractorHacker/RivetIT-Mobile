package com.foleyit.itflow.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.data.ssl.FingerprintTrustManager
import com.foleyit.itflow.data.ssl.probeCertificate
import com.foleyit.itflow.data.ssl.sha256Fingerprint
import com.foleyit.itflow.ui.components.FoleyItLogoMark
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

@Composable
fun ServerSetupScreen(prefs: AppPreferences, onDone: () -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCert by remember { mutableStateOf<X509Certificate?>(null) }
    val scope = rememberCoroutineScope()

    fun connect(trustedSha: String? = null) {
        if (!url.startsWith("https://")) { error = "URL must start with https://"; return }
        loading = true; error = null
        scope.launch {
            try {
                val cleanUrl = url.trimEnd('/')
                val tm = FingerprintTrustManager(trustedSha)
                val ssl = SSLContext.getInstance("TLS").also { it.init(null, arrayOf(tm), null) }
                val responseCode = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .sslSocketFactory(ssl.socketFactory, tm)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    client.newCall(Request.Builder().url("$cleanUrl/api/v1/auth").build())
                        .execute().code
                }
                if (responseCode in 200..499) {
                    if (trustedSha != null) prefs.saveTrustedCert(trustedSha)
                    prefs.saveServerUrl(cleanUrl)
                    ApiClient.init(cleanUrl, null, trustedSha)
                    onDone()
                } else {
                    error = "Server responded with $responseCode — check the URL"
                }
            } catch (e: SSLHandshakeException) {
                // Certificate not trusted by system — probe it and offer to accept
                val cert = withContext(Dispatchers.IO) { probeCertificate("${url.trimEnd('/')}/api/v1/auth") }
                if (cert != null) {
                    pendingCert = cert
                } else {
                    error = "SSL error and could not retrieve certificate.\n${userMessage(e)}"
                }
            } catch (e: Exception) {
                error = "Cannot reach server. Check the URL.\n${userMessage(e)}"
            } finally {
                loading = false
            }
        }
    }

    // Self-signed certificate acceptance dialog
    pendingCert?.let { cert ->
        val fingerprint = cert.sha256Fingerprint()
        val lastSix = remember(fingerprint) { fingerprint.replace(":", "").takeLast(6) }
        var confirmInput by remember(fingerprint) { mutableStateOf("") }
        val confirmed = confirmInput.trim().equals(lastSix, ignoreCase = true)
        AlertDialog(
            onDismissRequest = { pendingCert = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Untrusted Certificate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This server's certificate is not signed by a trusted authority. If you " +
                        "did not set up this server yourself, or you're on a network you don't " +
                        "fully trust (e.g. public Wi-Fi), someone could be intercepting your " +
                        "connection. Only continue if you personally recognize this server.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Text("Issued to: ${cert.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")}")
                    Text("Expires: ${cert.notAfter}")
                    Spacer(Modifier.height(4.dp))
                    Text("SHA-256 Fingerprint:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(fingerprint)
                                }
                            },
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "To confirm you've verified this fingerprint (e.g. against your server admin), " +
                        "type its last 6 characters: ${lastSix.chunked(2).joinToString(":")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sha = fingerprint
                        pendingCert = null
                        connect(trustedSha = sha)
                    },
                    enabled = confirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Security, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Trust & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCert = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        val logoTileGradient = Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
            start = Offset.Zero,
            end = Offset.Infinite
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(logoTileGradient, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            FoleyItLogoMark(
                modifier = Modifier.size(40.dp),
                markColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Connect to ITFlow", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Enter your ITFlow server address.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://itflow.example.com") },
            leadingIcon = { Icon(Icons.Outlined.Link, null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            singleLine = true,
            isError = error != null
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { connect() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            else Text("Connect")
        }
        Spacer(Modifier.height(32.dp))
        Text("Not affiliated with ITFlow LLC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(24.dp))
    }
}
