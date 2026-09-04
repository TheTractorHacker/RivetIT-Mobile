package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ServiceDeskReportResponse
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.theme.forPriority
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDeskReportScreen(navController: NavController) {
    var preset by remember { mutableStateOf(DateRangePreset.THIS_MONTH) }
    var state by remember { mutableStateOf<Result<ServiceDeskReportResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            val (from, to) = preset.toRange()
            state = runCatching { ApiClient.service().getServiceDeskReport(from, to) }
        }
    }
    LaunchedEffect(preset) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service Desk") },
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
                    val totals = report.totals
                    val maxAging = (report.aging.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    val maxPriority = (report.byPriority.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
                    val maxTechOpen = (report.byTechnician.maxOfOrNull { it.open } ?: 1).coerceAtLeast(1)

                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ReportHeroStat(
                                label = "Open Now",
                                value = totals.openNow.toString(),
                                icon = Icons.Outlined.PendingActions,
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ServiceDeskStatCard("Opened", totals.opened.toString(), Modifier.weight(1f))
                                ServiceDeskStatCard("Resolved", totals.resolved.toString(), Modifier.weight(1f))
                            }
                        }

                        if (report.volume.opened.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text("Ticket Volume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Opened per period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                MonthlyTrendChart(values = report.volume.opened.map { it.toDouble() }, labels = report.volume.labels)
                            }
                        }

                        if (report.aging.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("Ticket Aging", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.aging) { bucket ->
                                ReportBarRow(
                                    label = bucket.label,
                                    value = bucket.count.toString(),
                                    fraction = bucket.count.toFloat() / maxAging
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("SLA Compliance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ServiceDeskStatCard(
                                    label = "Response Met",
                                    value = report.sla.responsePct?.let { "%.0f%%".format(it) } ?: "N/A",
                                    modifier = Modifier.weight(1f),
                                    subtitle = "${report.sla.responseMet}/${report.sla.responseTotal}"
                                )
                                ServiceDeskStatCard(
                                    label = "Resolution Met",
                                    value = report.sla.resolutionPct?.let { "%.0f%%".format(it) } ?: "N/A",
                                    modifier = Modifier.weight(1f),
                                    subtitle = "${report.sla.resolutionMet}/${report.sla.resolutionTotal}"
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("CSAT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            report.csat.avgRating?.let { "%.1f / 5".format(it) } ?: "N/A",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column {
                                        Text("Satisfied", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            report.csat.satisfiedPct?.let { "%.0f%%".format(it) } ?: "N/A",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column {
                                        Text("Rated", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            report.csat.rated.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        if (report.byPriority.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("By Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.byPriority) { p ->
                                ReportBarRow(
                                    label = p.priority,
                                    value = p.total.toString(),
                                    fraction = p.total.toFloat() / maxPriority,
                                    barColor = MaterialTheme.statusColors.forPriority(p.priority),
                                    subtitle = "Avg response: ${formatSeconds(p.avgResponseSeconds)} · Avg resolve: ${formatSeconds(p.avgResolveSeconds)}"
                                )
                            }
                        }

                        if (report.byTechnician.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("By Technician", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.byTechnician, key = { it.userId }) { t ->
                                ReportBarRow(
                                    label = t.name,
                                    value = "${t.open} open",
                                    fraction = t.open.toFloat() / maxTechOpen,
                                    subtitle = "${t.resolved} resolved · avg ${formatSeconds(t.avgResolveSeconds)}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceDeskStatCard(label: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}
