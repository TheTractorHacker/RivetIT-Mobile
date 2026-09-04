package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.TechUtilizationReportResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianUtilizationReportScreen(navController: NavController) {
    var preset by remember { mutableStateOf(DateRangePreset.THIS_MONTH) }
    var state by remember { mutableStateOf<Result<TechUtilizationReportResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            val (from, to) = preset.toRange()
            state = runCatching { ApiClient.service().getTechUtilizationReport(from, to) }
        }
    }
    LaunchedEffect(preset) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Technician Utilization") },
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
                    if (report.technicians.isEmpty()) {
                        EmptyScreen("No technician activity in this range")
                    } else {
                        val capacity = report.capacity
                        val totals = report.totals
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                ReportHeroStat(
                                    label = "Team Utilization",
                                    value = totals.teamUtilizationPct?.let { "%.0f%%".format(it) } ?: "N/A",
                                    icon = Icons.Outlined.Speed
                                )
                            }
                            item {
                                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                    Text(
                                        "${capacity.businessDays} business days · ${capacity.capacityHoursPerTech}h capacity per tech",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UtilizationStatCard(
                                        label = "Billable",
                                        value = formatSeconds(totals.billableSeconds),
                                        subtitle = "$%,.2f billed".format(totals.billableValue),
                                        modifier = Modifier.weight(1f)
                                    )
                                    UtilizationStatCard(
                                        label = "Non-billable",
                                        value = formatSeconds(totals.nonbillableSeconds),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UtilizationStatCard(
                                        label = "Tickets Closed",
                                        value = totals.ticketsClosed.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    UtilizationStatCard(
                                        label = "CSAT Avg",
                                        value = totals.csatAvgRating?.let { "%.1f".format(it) } ?: "N/A",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)); Text("By Technician", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.technicians, key = { it.userId }) { t ->
                                ReportBarRow(
                                    label = t.name,
                                    value = t.utilizationPct?.let { "%.0f%%".format(it) } ?: "N/A",
                                    fraction = ((t.utilizationPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
                                    subtitle = "${formatSeconds(t.billableSeconds)} billable · ${formatSeconds(t.nonbillableSeconds)} non-billable · ${t.ticketsClosed} closed"
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
private fun UtilizationStatCard(label: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
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
