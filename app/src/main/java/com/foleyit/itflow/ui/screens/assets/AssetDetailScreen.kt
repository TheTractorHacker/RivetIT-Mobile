package com.foleyit.itflow.ui.screens.assets

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.AssetDetail
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<AssetDetail>?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }

    fun load() { scope.launch { state = runCatching { ApiClient.service().getAsset(id) } } }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.name ?: "Asset") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
            else -> {
                val a = state!!.getOrThrow()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(assetTypeIcon(a.type), null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(a.name, style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold)
                                        a.type?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                                    }
                                }
                                if (a.status != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small) {
                                        Text(a.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }

                    // Details
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Details", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                AssetRow("Client", a.client)
                                AssetRow("Make", a.make)
                                AssetRow("Model", a.model)
                                AssetRow("OS", a.os)
                                AssetRow("Location", a.locationName?.takeIf { it.isNotBlank() }
                                    ?: a.physicalLocation)
                                AssetRow("Contact", a.contactName)
                                if (!a.contactPhone.isNullOrBlank())
                                    AssetRow("Phone", a.contactPhone)
                                AssetRow("Purchase Date", fmtDate(a.purchaseDate))
                                AssetRow("Warranty Expires", fmtDate(a.warrantyExpire))
                            }
                        }
                    }

                    // Serial number with copy
                    if (!a.serial.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.QrCode, null,
                                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Serial Number", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline)
                                        Text(a.serial, fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(android.content.ClipData.newPlainText("Serial", a.serial))
                                            )
                                            snackbar.showSnackbar("Serial copied")
                                        }
                                    }) {
                                        Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Notes
                    if (!a.notes.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Notes", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.notes)
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
private fun AssetRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(label, modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun assetTypeIcon(type: String?) = when (type?.lowercase()) {
    "laptop"  -> Icons.Outlined.LaptopMac
    "desktop" -> Icons.Outlined.DesktopWindows
    "server"  -> Icons.Outlined.Dns
    "phone", "mobile" -> Icons.Outlined.PhoneAndroid
    "printer" -> Icons.Outlined.Print
    "network", "switch", "router" -> Icons.Outlined.Router
    else      -> Icons.Outlined.Devices
}
