package com.foleyit.itflow.ui.screens.alerts

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.AlertActionRequest
import com.foleyit.itflow.data.api.AlertItem
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.forAlertSeverity
import com.foleyit.itflow.ui.theme.forAlertStatus
import com.foleyit.itflow.ui.theme.statusColors
import kotlinx.coroutines.launch

private val STATUS_TABS = listOf("new" to "New", "acknowledged" to "Acked", "resolved" to "Resolved", "all" to "All")

@Composable
fun AlertsScreen(navController: NavController) {
    var status by remember { mutableStateOf("new") }
    var state by remember { mutableStateOf<Result<List<AlertItem>>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = runCatching { ApiClient.service().getAlerts(status = status).data }
        }
    }
    LaunchedEffect(status) { load() }

    fun act(alert: AlertItem, action: String) {
        scope.launch {
            runCatching { ApiClient.service().actOnAlert(AlertActionRequest(alert.source, alert.id, action)) }
            load()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Segmented pill control for New/Acked/Resolved/All — matches the Tickets screen's
        // segmented status control rather than a stock ScrollableTabRow.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                STATUS_TABS.forEach { (key, label) ->
                    val selected = status == key
                    Surface(
                        selected = selected,
                        onClick = { status = key },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        when {
            state == null -> LoadingScreen()
            state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "", onRetry = ::load)
            else -> {
                val alerts = state!!.getOrThrow()
                if (alerts.isEmpty()) {
                    EmptyScreen("No alerts here", Icons.Outlined.CheckCircle)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts, key = { it.source + it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                onAcknowledge = { act(alert, "acknowledge") },
                                onResolve = { act(alert, "resolve") },
                                onViewTicket = {
                                    alert.ticketId?.let { navController.navigate(Screen.TicketDetail.go(it)) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertItem,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit,
    onViewTicket: () -> Unit
) {
    val severityColor = MaterialTheme.statusColors.forAlertSeverity(alert.severity)
    val statusColor = MaterialTheme.statusColors.forAlertStatus(alert.status)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(modifier = Modifier.padding(16.dp)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = severityColor.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (alert.source == "backup") Icons.Outlined.CloudUpload else Icons.Outlined.Dns
                    Icon(icon, null, tint = severityColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.message ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Both chips share the same tinted-container + colored-text idiom used
                    // everywhere else in the app (Invoices/Quotes/Tickets) — previously this
                    // severity chip alone used a solid fill with hardcoded white text, which
                    // silently broke (near-invisible white-on-pastel) once dark mode used a
                    // light severity tone. A single shared idiom sidesteps that class of bug.
                    Surface(color = severityColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(alert.severity, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = severityColor)
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(alert.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
                val subtitle = listOfNotNull(alert.subject, alert.clientName).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.padding(top = 6.dp)) {
                    if (alert.status != "resolved") {
                        if (alert.status == "new") {
                            TextButton(onClick = onAcknowledge, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Acknowledge") }
                        }
                        TextButton(onClick = onResolve, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Resolve") }
                    }
                    if (alert.ticketId != null) {
                        TextButton(onClick = onViewTicket, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(alert.ticketLabel?.takeIf { it.isNotBlank() } ?: "View Ticket")
                        }
                    }
                }
            }
        }
    }
}
