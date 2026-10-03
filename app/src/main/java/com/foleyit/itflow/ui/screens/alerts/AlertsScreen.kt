package com.foleyit.itflow.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val STATUS_TABS = listOf("new" to "New", "acknowledged" to "Acked", "resolved" to "Resolved", "all" to "All")

@Composable
fun AlertsScreen(navController: NavController) {
    var status by rememberSaveable { mutableStateOf("new") }
    var state by remember { mutableStateOf<Result<List<AlertItem>>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(status, refreshKey) {
        state = null
        try {
            state = Result.success(ApiClient.service().getAlerts(status = status).data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = Result.failure(e)
        }
    }

    fun act(alert: AlertItem, action: String) {
        if (pendingKey != null) return
        pendingKey = "${alert.source}:${alert.id}"
        pendingAction = action
        scope.launch {
            var errorMessage: String? = null
            try {
                ApiClient.service().actOnAlert(AlertActionRequest(alert.source, alert.id, action))
                val nextStatus = if (action == "acknowledge") "acknowledged" else "resolved"
                state = state?.map { alerts ->
                    if (status == "all" || status == nextStatus) {
                        alerts.map { item ->
                            if (item.source == alert.source && item.id == alert.id) item.copy(status = nextStatus) else item
                        }
                    } else {
                        alerts.filterNot { it.source == alert.source && it.id == alert.id }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Could not ${if (action == "acknowledge") "acknowledge" else "resolve"} alert: ${userMessage(e)}"
            } finally {
                pendingKey = null
                pendingAction = null
            }
            errorMessage?.let { snackbar.showSnackbar(it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                            enabled = pendingKey == null,
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
                state!!.isFailure -> ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = { refreshKey++ })
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
                            items(alerts, key = { "${it.source}:${it.id}" }) { alert ->
                                AlertCard(
                                    modifier = Modifier.animateItem(),
                                    alert = alert,
                                    enabled = pendingKey == null,
                                    pendingAction = if (pendingKey == "${alert.source}:${alert.id}") pendingAction else null,
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
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun AlertCard(
    modifier: Modifier,
    alert: AlertItem,
    enabled: Boolean,
    pendingAction: String?,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit,
    onViewTicket: () -> Unit
) {
    val severityColor = MaterialTheme.statusColors.forAlertSeverity(alert.severity)
    val statusColor = MaterialTheme.statusColors.forAlertStatus(alert.status)
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
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
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (alert.status != "resolved") {
                        if (alert.status == "new") {
                            TextButton(onClick = onAcknowledge, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                if (pendingAction == "acknowledge") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Acknowledge")
                            }
                        }
                        TextButton(onClick = onResolve, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            if (pendingAction == "resolve") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Resolve")
                        }
                    }
                    if (alert.ticketId != null) {
                        TextButton(onClick = onViewTicket, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(alert.ticketLabel?.takeIf { it.isNotBlank() } ?: "View Ticket")
                        }
                    }
                }
            }
        }
    }
}
