package com.foleyit.itflow.ui.screens.credentials

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.util.BiometricCrypto
import com.foleyit.itflow.ui.util.rememberPagedList
import com.foleyit.itflow.ui.util.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(navController: NavController) {
    var authenticated by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current
    val localActivity = androidx.activity.compose.LocalActivity.current

    fun authenticate() {
        val activity = localActivity as? FragmentActivity ?: return
        val crypto = try { BiometricCrypto.cryptoObject() } catch (_: Exception) { return }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (BiometricCrypto.confirm(result)) authenticated = true
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify identity")
            .setSubtitle("Access credentials")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info, crypto)
    }

    if (!authenticated) {
        Scaffold(topBar = { TopAppBar(title = { Text("Credentials") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("Authentication Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Verify your identity to view credentials", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = ::authenticate) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Authenticate") }
                }
            }
        }
        return
    }

    val list = rememberPagedList<com.foleyit.itflow.data.api.CredentialSummary> { page, q ->
        ApiClient.service().getCredentials(search = q, page = page)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Credentials") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; list.onSearchChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search credentials…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = ""; list.onSearchChanged("") }) { Icon(Icons.Outlined.Clear, "Clear search") }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            val ls = list.state
            when {
                ls.isRefreshing -> LoadingScreen()
                ls.error != null -> ErrorScreen(userMessage(ls.error), onRetry = list::retry)
                ls.items.isEmpty() -> EmptyScreen("No credentials found", Icons.Outlined.Lock)
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ls.items, key = { it.id }) { c ->
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = { navController.navigate(Screen.CredDetail.go(c.id)) }) {
                                ListItem(
                                    headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                                    supportingContent = c.uri?.takeIf { it.isNotBlank() }?.let { uri -> { Text(uri, maxLines = 1) } },
                                    leadingContent = {
                                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                                        }
                                    },
                                    trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(c.client ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Icon(Icons.Outlined.ChevronRight, null) } }
                                )
                            }
                        }
                        if (ls.hasMore) {
                            item(key = "load_more") { LoadMoreRow(ls.isLoadingMore, list::loadMore) }
                        }
                    }
                }
            }
        }
    }
}
