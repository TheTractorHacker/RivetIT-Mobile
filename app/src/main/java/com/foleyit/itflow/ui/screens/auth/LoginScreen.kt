package com.foleyit.itflow.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.FcmTokenRequest
import com.foleyit.itflow.data.api.LoginRequest
import com.foleyit.itflow.data.api.PasskeyCompleteRequest
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.ui.components.FoleyItLogoMark
import com.foleyit.itflow.ui.util.userMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(prefs: AppPreferences, onLoggedIn: () -> Unit, onChangeServer: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var passkeyPending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requires2fa by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val localActivity = androidx.activity.compose.LocalActivity.current
    var serverUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { serverUrl = prefs.serverUrl.first() }

    fun finishLogin(resp: com.foleyit.itflow.data.api.LoginResponse) {
        val token = resp.token ?: run { error = "No token received"; loading = false; return }
        scope.launch {
            prefs.saveAuthData(token, resp.user!!)
            ApiClient.setToken(token)
            try {
                val fcmToken = FirebaseMessaging.getInstance().token.await()
                ApiClient.service().registerFcmToken(FcmTokenRequest(fcmToken))
            } catch (_: Exception) { }
            onLoggedIn()
        }
    }

    fun login() {
        if (username.isBlank() || password.isBlank()) return
        if (requires2fa && totpCode.isBlank()) return
        loading = true; error = null
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.service().login(LoginRequest(
                        username = username.trim(), password = password,
                        device_name = "ITFlow MSP Android",
                        totp_code = if (totpCode.isNotBlank()) totpCode.trim() else null
                    ))
                }
                if (resp.requires2fa == true) { requires2fa = true; loading = false; return@launch }
                finishLogin(resp)
            } catch (e: Exception) {
                error = if (requires2fa) "Invalid 2FA code" else "Invalid username or password"
            } finally { loading = false }
        }
    }

    fun loginWithPasskey() {
        loading = true; passkeyPending = true; error = null
        scope.launch {
            try {
                val beginResp = withContext(Dispatchers.IO) { ApiClient.service().passkeyBegin() }

                val optionsJson = JSONObject().apply {
                    put("challenge", beginResp.challenge)
                    put("timeout", beginResp.timeout)
                    put("rpId", beginResp.rpId)
                    put("userVerification", beginResp.userVerification)
                    put("allowCredentials", JSONArray())
                }.toString()

                val activity = localActivity
                    ?: run { error = "Cannot show credential picker"; loading = false; return@launch }
                val credentialManager = CredentialManager.create(activity)
                val request = GetCredentialRequest(listOf(
                    GetPublicKeyCredentialOption(requestJson = optionsJson)
                ))
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential !is PublicKeyCredential) {
                    error = "Unexpected credential type: ${credential.type}"
                    loading = false
                    return@launch
                }

                @Suppress("UNCHECKED_CAST")
                val assertionMap = Gson().fromJson(credential.authenticationResponseJson, Map::class.java)
                    as Map<String, Any>

                val completeResp = withContext(Dispatchers.IO) {
                    ApiClient.service().passkeyComplete(PasskeyCompleteRequest(
                        challengeToken = beginResp.challengeToken,
                        passkeyResponse = assertionMap
                    ))
                }
                finishLogin(completeResp)
            } catch (e: GetCredentialCancellationException) {
                // user dismissed the picker
            } catch (e: NoCredentialException) {
                error = "No passkeys found. Register one at Settings → Security on the web portal."
            } catch (e: GetCredentialUnsupportedException) {
                error = "Passkey error: ${e.message ?: "passkeys not supported — enable your password manager as a credential provider in Android Settings"}"
            } catch (e: HttpException) {
                error = userMessage(e)
            } catch (e: Exception) {
                if (e.message?.contains("cancel", ignoreCase = true) != true) {
                    error = "Passkey sign-in failed: ${userMessage(e)}"
                }
            } finally { loading = false; passkeyPending = false }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        val logoGradient = Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
            start = Offset.Zero,
            end = Offset.Infinite
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(logoGradient, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            FoleyItLogoMark(
                modifier = Modifier.size(40.dp),
                markColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(serverUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(40.dp))

        if (!requires2fa) {
            OutlinedTextField(value = username, onValueChange = { username = it },
                label = { Text("Username or email") }, leadingIcon = { Icon(Icons.Outlined.Person, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                trailingIcon = { IconButton(onClick = { obscure = !obscure }) {
                    Icon(if (obscure) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (obscure) "Show password" else "Hide password")
                }},
                visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { login() }))
        } else {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text("Enter your authenticator code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = totpCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                label = { Text("6-digit code") }, leadingIcon = { Icon(Icons.Outlined.Key, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { login() }))
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { requires2fa = false; totpCode = "" }) { Text("← Back") }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = ::login, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (requires2fa) "Verify" else "Sign in")
        }

        if (!requires2fa) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = ::loginWithPasskey,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !loading
            ) {
                if (passkeyPending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Waiting for passkey…", fontWeight = FontWeight.Medium)
                } else {
                    Icon(Icons.Outlined.Fingerprint, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with passkey", fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) { Text("Change server") }
        }
        Spacer(Modifier.height(32.dp))
    }
}
