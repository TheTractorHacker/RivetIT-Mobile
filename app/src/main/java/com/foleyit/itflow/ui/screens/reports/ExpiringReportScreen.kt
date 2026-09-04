package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ExpiringResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.fmtDate
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiringReportScreen(navController: NavController) {
    var type by remember { mutableStateOf("domains") }
    var state by remember { mutableStateOf<Result<ExpiringResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { state = runCatching { ApiClient.service().getExpiringReport(type = type, days = 30) } } }
    LaunchedEffect(type) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expiring Soon") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = type == "domains", onClick = { type = "domains" },
                    label = { Text("Domains") },
                    leadingIcon = { Icon(Icons.Outlined.Language, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = type == "certificates", onClick = { type = "certificates" },
                    label = { Text("Certificates") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)) }
                )
            }
            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = ::load)
                else -> {
                    val report = state!!.getOrThrow()
                    if (report.items.isEmpty()) {
                        EmptyScreen("Nothing expiring in the next ${report.days} days")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(report.items, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Medium)
                                            item.clientName?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        Text(
                                            "Expires ${fmtDate(item.expireDate)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
