package com.foleyit.itflow

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.screens.auth.LoginScreen
import com.foleyit.itflow.ui.screens.auth.ServerSetupScreen
import com.foleyit.itflow.ui.screens.main.MainScreen
import com.foleyit.itflow.ui.theme.ITFlowTheme
import com.foleyit.itflow.ui.util.BiometricCrypto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : FragmentActivity() {

    private var backgroundedAt = 0L
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onPause() {
        super.onPause()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("deep_link_route")?.let { pendingDeepLink.value = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        ApiClient.onUnauthorized = null
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (isRooted() && BuildConfig.DEBUG) android.util.Log.w("ITFlow", "Device appears rooted.")
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        val prefs = (application as ITFlowApplication).prefs

        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }
        lifecycleScope.launch {
            val url   = prefs.serverUrl.first()
            val token = prefs.authToken.first()
            startDestination = when {
                url.isBlank() -> Screen.Setup.route
                token == null -> Screen.Login.route
                else          -> Screen.Dashboard.route
            }
        }

        intent?.getStringExtra("deep_link_route")?.let { pendingDeepLink.value = it }

        setContent {
            val resolvedStart = startDestination ?: return@setContent
            val themeMode by prefs.themeMode.collectAsState(initial = com.foleyit.itflow.ui.theme.ThemeMode.SYSTEM)
            val colorSeed by prefs.colorSeed.collectAsState(initial = com.foleyit.itflow.ui.theme.ColorSeed.DEFAULT.id)
            ITFlowTheme(themeMode = themeMode, colorSeed = colorSeed) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var isLocked by remember { mutableStateOf(false) }
                    val biometricEnabled by prefs.biometricLock.collectAsState(initial = false)

                    // Wire 401 auto-logout
                    LaunchedEffect(Unit) {
                        ApiClient.onUnauthorized = {
                            MainScope().launch {
                                runCatching { ApiClient.service().registerFcmToken(com.foleyit.itflow.data.api.FcmTokenRequest("")) }
                                prefs.clearAuth()
                                ApiClient.clearToken()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    }

                    // App lock — re-lock after 5 minutes in background
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner, biometricEnabled) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME &&
                                biometricEnabled && backgroundedAt > 0 &&
                                System.currentTimeMillis() - backgroundedAt > 5 * 60_000L) {
                                isLocked = true
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    if (isLocked) {
                        BiometricLockScreen(
                            prefs = prefs,
                            onUnlocked = { isLocked = false },
                            onSignOut = {
                                MainScope().launch {
                                    runCatching { ApiClient.service().registerFcmToken(com.foleyit.itflow.data.api.FcmTokenRequest("")) }
                                    prefs.clearAuth()
                                    ApiClient.clearToken()
                                    isLocked = false
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                        return@Surface
                    }

                    NavHost(navController, startDestination = resolvedStart) {
                        composable(Screen.Setup.route) {
                            ServerSetupScreen(prefs = prefs, onDone = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Setup.route) { inclusive = true }
                                }
                            })
                        }
                        composable(Screen.Login.route) {
                            LoginScreen(prefs = prefs,
                                onLoggedIn = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                },
                                onChangeServer = {
                                    navController.navigate(Screen.Setup.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                })
                        }
                        composable(Screen.Dashboard.route) {
                            val deepLink by pendingDeepLink
                            MainScreen(
                                prefs = prefs,
                                deepLinkRoute = deepLink,
                                onDeepLinkConsumed = { pendingDeepLink.value = null },
                                onLoggedOut = {
                                    ApiClient.clearToken()
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onChangeServer = {
                                    lifecycleScope.launch {
                                        runCatching { ApiClient.service().registerFcmToken(com.foleyit.itflow.data.api.FcmTokenRequest("")) }
                                        prefs.clearAuth()
                                        ApiClient.clearToken()
                                        navController.navigate(Screen.Setup.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(prefs: AppPreferences, onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity ?: return

    fun authenticate() {
        val crypto = try { BiometricCrypto.cryptoObject() } catch (_: Exception) { return }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (BiometricCrypto.confirm(result)) onUnlocked()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) onSignOut()
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("ITFlow MSP")
                .setSubtitle("Authenticate to continue")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Sign out")
                .build(),
            crypto
        )
    }

    LaunchedEffect(Unit) { authenticate() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Fingerprint, null, Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("ITFlow MSP", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Authentication required", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { authenticate() }) { Text("Unlock") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
