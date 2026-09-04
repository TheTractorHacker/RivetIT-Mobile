package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.CsatReportResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch

private val RATING_KEYS = listOf("5", "4", "3", "2", "1")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsatReportScreen(navController: NavController) {
    var preset by remember { mutableStateOf(DateRangePreset.THIS_MONTH) }
    var state by remember { mutableStateOf<Result<CsatReportResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            val (from, to) = preset.toRange()
            state = runCatching { ApiClient.service().getCsatReport(from, to) }
        }
    }
    LaunchedEffect(preset) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Satisfaction") },
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
                    if (report.summary.rated == 0) {
                        EmptyScreen("No CSAT ratings in this range")
                    } else {
                        val summary = report.summary
                        val maxDistribution = (report.distribution.values.maxOrNull() ?: 1).coerceAtLeast(1)
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                ReportHeroStat(
                                    label = "Avg. Rating",
                                    value = summary.avgRating?.let { "%.1f / 5".format(it) } ?: "N/A",
                                    icon = Icons.Outlined.Star
                                )
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CsatStatCard(
                                        label = "Response Rate",
                                        value = summary.responseRatePct?.let { "%.0f%%".format(it) } ?: "N/A",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CsatStatCard(
                                        label = "Satisfied",
                                        value = summary.satisfiedPct?.let { "%.0f%%".format(it) } ?: "N/A",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CsatStatCard(
                                        label = "Needs Follow-up",
                                        value = summary.needsFollowupCount.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)); Text("Rating Distribution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(RATING_KEYS) { key ->
                                val count = report.distribution[key] ?: 0
                                val stars = key.toIntOrNull() ?: 0
                                ReportBarRow(
                                    label = if (stars == 1) "1 star" else "$stars stars",
                                    value = count.toString(),
                                    fraction = count.toFloat() / maxDistribution
                                )
                            }
                            if (report.trend.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("Trend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                item {
                                    MonthlyTrendChart(
                                        values = report.trend.map { it.count.toDouble() },
                                        labels = report.trend.map { it.label }
                                    )
                                }
                            }
                            if (report.byTechnician.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("By Technician", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.byTechnician) { t ->
                                    ReportBarRow(
                                        label = t.name,
                                        value = t.avgRating?.let { "%.1f".format(it) } ?: "-",
                                        fraction = ((t.avgRating ?: 0.0) / 5.0).toFloat(),
                                        subtitle = "${t.ratedCount} rated · ${t.satisfiedPct?.let { "%.0f".format(it) } ?: "-"}% satisfied"
                                    )
                                }
                            }
                            if (report.byClient.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("By Client", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.byClient) { c ->
                                    ReportBarRow(
                                        label = c.name,
                                        value = c.avgRating?.let { "%.1f".format(it) } ?: "-",
                                        fraction = ((c.avgRating ?: 0.0) / 5.0).toFloat(),
                                        subtitle = "${c.ratedCount} rated · ${c.satisfiedPct?.let { "%.0f".format(it) } ?: "-"}% satisfied"
                                    )
                                }
                            }
                            if (report.feedback.isNotEmpty()) {
                                item { Spacer(Modifier.height(8.dp)); Text("Recent Feedback", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(report.feedback.take(10)) { f ->
                                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                        Column(Modifier.padding(16.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("Ticket #${f.ticketNumber}", fontWeight = FontWeight.Medium)
                                                Text(
                                                    "★".repeat(f.rating) + "☆".repeat((5 - f.rating).coerceAtLeast(0)),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            val subtitle = listOfNotNull(f.clientName, f.techName?.let { "Tech: $it" }).joinToString(" · ")
                                            if (subtitle.isNotBlank()) {
                                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                            if (!f.comment.isNullOrBlank()) {
                                                Spacer(Modifier.height(8.dp))
                                                Text(f.comment, style = MaterialTheme.typography.bodyMedium)
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
    }
}

@Composable
private fun CsatStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
