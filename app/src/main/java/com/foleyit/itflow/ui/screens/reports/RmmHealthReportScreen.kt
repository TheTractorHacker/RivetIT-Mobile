package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.RmmHealthReportResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.theme.forAlertSeverity
import com.foleyit.itflow.ui.theme.forAlertStatus
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RmmHealthReportScreen(navController: NavController) {
    var preset by remember { mutableStateOf(DateRangePreset.THIS_MONTH) }
    var state by remember { mutableStateOf<Result<RmmHealthReportResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            val (from, to) = preset.toRange()
            state = runCatching { ApiClient.service().getRmmHealthReport(from, to) }
        }
    }
    LaunchedEffect(preset) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RMM Health") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateRangePresetPicker(preset) { preset = it }
            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = ::load)
                else -> {
                    val report = state!!.getOrThrow()
                    if (!report.haveData) {
                        EmptyScreen("No RMM alert data yet")
                    } else {
                        val totals = report.totals
                        val maxSeverity = (report.bySeverity.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                        val maxStatus = (report.byStatus.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                        val maxAssetAlerts = (report.noisiestAssets.maxOfOrNull { it.alerts } ?: 1).coerceAtLeast(1)
                        val maxClientAlerts = (report.noisiestClients.maxOfOrNull { it.alerts } ?: 1).coerceAtLeast(1)

                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                ReportHeroStat(
                                    label = "Mean Time to Resolve",
                                    value = formatSeconds(totals.mttrSeconds),
                                    icon = Icons.Outlined.Timer
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    RmmStatCard("Total Alerts", totals.total.toString(), Modifier.weight(1f))
                                    RmmStatCard("Resolved", totals.resolved.toString(), Modifier.weight(1f))
                                    RmmStatCard("Open", totals.open.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    RmmStatCard("Acknowledged", totals.acknowledged.toString(), Modifier.weight(1f))
                                    RmmStatCard("Conversion", totals.conversionPct?.let { "%.1f%%".format(it) } ?: "-", Modifier.weight(1f))
                                    RmmStatCard("MTTA", formatSeconds(totals.mttaSeconds), Modifier.weight(1f))
                                }
                            }

                            if (report.bySeverity.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("By Severity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.bySeverity) { s ->
                                    ReportBarRow(
                                        label = s.key.replaceFirstChar { it.uppercase() },
                                        value = s.count.toString(),
                                        fraction = s.count.toFloat() / maxSeverity,
                                        barColor = MaterialTheme.statusColors.forAlertSeverity(s.key)
                                    )
                                }
                            }

                            if (report.byStatus.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("By Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.byStatus) { s ->
                                    ReportBarRow(
                                        label = s.key.replaceFirstChar { it.uppercase() },
                                        value = s.count.toString(),
                                        fraction = s.count.toFloat() / maxStatus,
                                        barColor = MaterialTheme.statusColors.forAlertStatus(s.key)
                                    )
                                }
                            }

                            if (report.trend.counts.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Trend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    MonthlyTrendChart(values = report.trend.counts.map { it.toDouble() }, labels = report.trend.labels)
                                }
                            }

                            if (report.noisiestAssets.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("Noisiest Assets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.noisiestAssets, key = { it.assetId }) { a ->
                                    ReportBarRow(
                                        label = a.assetName,
                                        value = "${a.alerts} alerts",
                                        fraction = a.alerts.toFloat() / maxAssetAlerts,
                                        subtitle = "MTTR: ${formatSeconds(a.mttrSeconds)}"
                                    )
                                }
                            }

                            if (report.noisiestClients.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("Noisiest Clients", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.noisiestClients, key = { it.clientId }) { c ->
                                    ReportBarRow(
                                        label = c.clientName,
                                        value = "${c.alerts} alerts",
                                        fraction = c.alerts.toFloat() / maxClientAlerts,
                                        subtitle = "${c.withTicket} converted to tickets"
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

@Composable
private fun RmmStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
    }
}
