package com.foleyit.itflow.ui.screens.credentials

import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import com.foleyit.itflow.ui.util.generatePassword
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.BiometricKeyRequest
import com.foleyit.itflow.data.api.CredentialDetail
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.BiometricSigningKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailScreen(id: Int, navController: NavController) {
    var authenticated by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<Result<CredentialDetail>?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val localActivity = androidx.activity.compose.LocalActivity.current
    val snackbarHost = remember { SnackbarHostState() }

    // challengeToken/challengeBytes for the in-flight biometric step-up, set
    // just before showing the prompt and consumed once inside
    // onAuthenticationSucceeded (the actual signing - and therefore the only
    // moment the private key is usable - must happen inside that callback).
    fun authenticate() {
        val activity = localActivity as? FragmentActivity ?: return
        authError = null
        scope.launch {
            val challenge = try {
                ApiClient.service().passkeyBegin()
            } catch (e: Exception) {
                authError = "Could not reach server: ${e.message}"
                return@launch
            }
            val challengeBytes = BiometricSigningKey.base64UrlDecode(challenge.challenge)

            val crypto = BiometricSigningKey.cryptoObject()
            if (crypto == null) {
                authError = "Biometric key unavailable on this device"
                return@launch
            }

            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val signatureB64 = BiometricSigningKey.sign(result, challengeBytes)
                    if (signatureB64 == null) {
                        authError = "Signing failed"
                        return
                    }
                    scope.launch {
                        try {
                            // Idempotent - cheap to always (re-)register in case this
                            // is the first use, or the key was regenerated after a
                            // biometric-enrollment invalidation.
                            val pub = BiometricSigningKey.getOrCreatePublicKey()
                            if (pub != null) {
                                ApiClient.service().registerBiometricKey(
                                    BiometricKeyRequest(BiometricSigningKey.publicKeyPem(pub))
                                )
                            }
                            state = runCatching {
                                ApiClient.service().getCredential(id, challenge.challengeToken, signatureB64)
                            }
                            authenticated = true
                        } catch (e: Exception) {
                            authError = "Verification failed: ${e.message}"
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authError = errString.toString()
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify identity")
                .setSubtitle("Access credential details")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()
            prompt.authenticate(info, crypto)
        }
    }

    fun load() {
        scope.launch {
            val challenge = try {
                ApiClient.service().passkeyBegin()
            } catch (e: Exception) {
                state = Result.failure(e)
                return@launch
            }
            // A retry (e.g. after a transient network error) still needs a fresh
            // biometric-signed challenge, not just a plain re-fetch - re-run the
            // whole step-up flow rather than reusing a stale signature.
            authenticated = false
            authenticate()
        }
    }

    fun copy(value: String, label: String, sensitive: Boolean = false) {
        scope.launch {
            val clip = android.content.ClipData.newPlainText(label, value)
            if (sensitive) {
                clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setClipEntry(ClipEntry(clip))
            snackbarHost.showSnackbar("$label copied")
            if (sensitive) {
                kotlinx.coroutines.delay(60_000)
                val stillSameValue = clipboard.getClipEntry()?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString() == value
                if (stillSameValue) {
                    clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("", "")))
                }
            }
        }
    }

    if (!authenticated) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Credential") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("Authentication Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Verify your identity to view this credential", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = ::authenticate) {
                        Icon(Icons.Outlined.Fingerprint, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Authenticate")
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.name ?: "Credential") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
            else -> {
                val c = state!!.getOrThrow()
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        c.client?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.outline) }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                                c.username?.takeIf { it.isNotBlank() }?.let { CredField("Username", it, Icons.Outlined.Person, onCopy = { copy(it, "Username") }) }
                                c.password?.takeIf { it.isNotBlank() }?.let { pwd ->
                                    CredField(
                                        "Password",
                                        if (showPassword) pwd else "••••••••••",
                                        Icons.Outlined.Lock,
                                        onCopy = { copy(pwd, "Password", sensitive = true) },
                                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(32.dp)) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, Modifier.size(18.dp)) } }
                                    )
                                }
                                c.uri?.takeIf { it.isNotBlank() }?.let { url ->
                                    CredField("URL", url, Icons.Outlined.Link, onCopy = { copy(url, "URL") }, onTap = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) })
                                }
                            }
                        }
                    }
                    item {
                        // Password generator
                        var genLen by remember { mutableStateOf(16f) }
                        var genResult by remember { mutableStateOf("") }
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Generate Password", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(value = genLen, onValueChange = { genLen = it },
                                        valueRange = 8f..32f, steps = 23,
                                        modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${genLen.toInt()} chars",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                                if (genResult.isNotBlank()) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(genResult, Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                            IconButton(onClick = { copy(genResult, "Password", sensitive = true) },
                                                modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                Button(onClick = { genResult = generatePassword(genLen.toInt()) },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Generate")
                                }
                            }
                        }
                    }
                    if (!c.note.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(c.note)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      onCopy: (() -> Unit)? = null, onTap: (() -> Unit)? = null,
                      trailingIcon: (@Composable () -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Surface(color = androidx.compose.ui.graphics.Color.Transparent, onClick = { onTap?.invoke() }, enabled = onTap != null) { Text(value, style = MaterialTheme.typography.bodyMedium) }
            }
            trailingIcon?.invoke()
            onCopy?.let { IconButton(onClick = it, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp)) } }
        }
    }
}
