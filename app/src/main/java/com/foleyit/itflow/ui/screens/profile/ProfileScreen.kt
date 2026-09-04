package com.foleyit.itflow.ui.screens.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.foleyit.itflow.BuildConfig
import com.foleyit.itflow.data.api.*
import com.foleyit.itflow.data.local.AppPreferences
import com.foleyit.itflow.ui.theme.ColorSeed
import com.foleyit.itflow.ui.theme.ThemeMode
import com.foleyit.itflow.ui.theme.colorSchemeFor
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconBg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {}
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = iconBg, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = titleColor)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            trailing()
        }
    }
}

/** Small display name for a color seed — the enum id ("foleyit") isn't presentation-ready. */
private fun ColorSeed.displayName(): String = when (this) {
    ColorSeed.FOLEYIT -> "FoleyIT"
    ColorSeed.TEAL -> "Teal"
    ColorSeed.SUNSET -> "Sunset"
    ColorSeed.FOREST -> "Forest"
    ColorSeed.VIOLET -> "Violet"
}

/**
 * One tappable accent-color preview. Deliberately resolves [seed]'s own color scheme rather than
 * reading MaterialTheme.colorScheme (which only ever reflects the currently-active seed) so the
 * user can see every option's true color while picking, not just the one already selected.
 */
@Composable
private fun ColorSeedSwatch(
    seed: ColorSeed,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val seedScheme = colorSchemeFor(seed, darkTheme)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(seedScheme.primary, CircleShape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                // This seed's own onPrimary, not a hardcoded white — dark-mode primaries in this
                // app are deliberately light/bright (standard M3 convention), so a fixed white
                // checkmark would wash out against e.g. dark-mode Sunset's pale peach primary.
                Icon(Icons.Outlined.Check, null, tint = seedScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            seed.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    prefs: AppPreferences,
    onChangeServer: () -> Unit = {},
    onLoggedOut: () -> Unit = {}
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var obscureCur by remember { mutableStateOf(true) }
    var obscureNew by remember { mutableStateOf(true) }
    var obscureConfirm by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showChangeServerConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        profile = runCatching { ApiClient.service().getProfile() }.getOrNull()
        profile?.let { name = it.name; email = it.email }
        loading = false
    }

    fun save() {
        if (name.isBlank() || email.isBlank()) { error = "Name and email are required"; return }
        if (newPass.isNotBlank() && newPass != confirmPass) { error = "New passwords don't match"; return }
        if (newPass.isNotBlank() && currentPass.isBlank()) { error = "Enter current password to change it"; return }
        saving = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.service().updateProfile(UpdateProfileRequest(
                        name = name.trim(), email = email.trim(),
                        currentPassword = currentPass, newPassword = newPass
                    ))
                }
                val currentToken = prefs.authToken.first() ?: ""
                prefs.saveAuthData(currentToken,
                    UserInfo(profile?.id ?: 0, name.trim(), email.trim(), profile?.type ?: 1))
                currentPass = ""; newPass = ""; confirmPass = ""
                snackbar.showSnackbar("Profile updated successfully")
            } catch (e: Exception) {
                error = userMessage(e)
            } finally {
                saving = false
            }
        }
    }

    if (showChangeServerConfirm) {
        AlertDialog(
            onDismissRequest = { showChangeServerConfirm = false },
            title = { Text("Change Server?") },
            text = { Text("You'll be signed out of ${ApiClient.serverUrl} to connect to a different server.") },
            confirmButton = {
                TextButton(onClick = { showChangeServerConfirm = false; onChangeServer() }) { Text("Sign Out & Change") }
            },
            dismissButton = {
                TextButton(onClick = { showChangeServerConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("You'll need to sign in again to access your account.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try { ApiClient.service().registerFcmToken(FcmTokenRequest("")) } catch (_: Exception) {}
                            try { ApiClient.service().logout() } catch (_: Exception) {}
                        }
                        prefs.clearAuth()
                        ApiClient.clearToken()
                        onLoggedOut()
                    }
                }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val isDarkMode = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "System default",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefs.setThemeMode(mode) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = {
                                scope.launch { prefs.setThemeMode(mode) }
                                showThemeDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showChangePasswordSheet) {
        ModalBottomSheet(onDismissRequest = { showChangePasswordSheet = false }) {
            Column(
                Modifier
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Change Password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("Leave blank to keep your current password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                HorizontalDivider()
                OutlinedTextField(
                    value = currentPass, onValueChange = { currentPass = it },
                    label = { Text("Current Password") },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                    trailingIcon = {
                        IconButton(onClick = { obscureCur = !obscureCur }) {
                            Icon(if (obscureCur) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (obscureCur) "Show password" else "Hide password")
                        }
                    },
                    visualTransformation = if (obscureCur) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = newPass, onValueChange = { newPass = it },
                    label = { Text("New Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { obscureNew = !obscureNew }) {
                            Icon(if (obscureNew) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (obscureNew) "Show password" else "Hide password")
                        }
                    },
                    visualTransformation = if (obscureNew) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirmPass, onValueChange = { confirmPass = it },
                    label = { Text("Confirm New Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { obscureConfirm = !obscureConfirm }) {
                            Icon(if (obscureConfirm) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (obscureConfirm) "Show password" else "Hide password")
                        }
                    },
                    visualTransformation = if (obscureConfirm) PasswordVisualTransformation() else VisualTransformation.None,
                    isError = newPass.isNotBlank() && confirmPass.isNotBlank() && newPass != confirmPass,
                    supportingText = if (newPass.isNotBlank() && confirmPass.isNotBlank() && newPass != confirmPass) {
                        { Text("Passwords don't match") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && currentPass.isNotBlank() && newPass.isNotBlank()
                ) {
                    Text("Change Password")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Avatar header ────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val avatarGradient = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(avatarGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        name.ifBlank { "Your Name" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        email.ifBlank { "your@email.com" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Account card ─────────────────────────────────────────────
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Account",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Outlined.Person, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Outlined.Email, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Button(
                            onClick = ::save,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save Profile")
                        }
                    }
                }

                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // ── Security & Server card ──────────────────────────────────
                val biometricEnabled by prefs.biometricLock.collectAsState(initial = false)
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Text("Security & Server",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp))

                        SettingsRow(
                            icon = Icons.Outlined.Fingerprint,
                            title = "Biometric Lock",
                            subtitle = "Lock after 5 min in background",
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            trailing = {
                                Switch(
                                    checked = biometricEnabled,
                                    onCheckedChange = { scope.launch { prefs.setBiometricLock(it) } }
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))

                        SettingsRow(
                            icon = Icons.Outlined.Lock,
                            title = "Change Password",
                            subtitle = "Update your account password",
                            onClick = { showChangePasswordSheet = true }
                        )
                        Spacer(Modifier.height(8.dp))

                        SettingsRow(
                            icon = Icons.Outlined.Dns,
                            title = "Server",
                            subtitle = ApiClient.serverUrl,
                            onClick = { showChangeServerConfirm = true },
                            trailing = {
                                Text("Change", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        )
                        Spacer(Modifier.height(8.dp))

                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications",
                            subtitle = "Manage push notification settings",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                })
                            },
                            trailing = {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null,
                                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                // ── Appearance card ──────────────────────────────────────────
                val colorSeedId by prefs.colorSeed.collectAsState(initial = ColorSeed.DEFAULT.id)
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Text("Appearance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp))

                        SettingsRow(
                            icon = Icons.Outlined.Palette,
                            title = "Theme",
                            subtitle = when (themeMode) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                else -> "System default"
                            },
                            onClick = { showThemeDialog = true }
                        )
                        Spacer(Modifier.height(16.dp))

                        Text("Accent Color",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ColorSeed.entries.forEach { seed ->
                                ColorSeedSwatch(
                                    seed = seed,
                                    selected = seed.id == colorSeedId,
                                    darkTheme = isDarkMode,
                                    onClick = { scope.launch { prefs.setColorSeed(seed.id) } }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Reports, Knowledge Base, Credentials, Quotes, Invoices, and Expenses are in the menu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // ── Sign out ─────────────────────────────────────────────────
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = "Sign Out",
                            subtitle = "Log out of this account on this device",
                            iconBg = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { showSignOutConfirm = true },
                            trailing = {}
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "ITFlow MSP ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
