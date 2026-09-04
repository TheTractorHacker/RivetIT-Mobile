package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.TimeReportResponse
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSummaryScreen(navController: NavController) {
    var period by remember { mutableStateOf("week") }
    var mineOnly by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<Result<TimeReportResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = runCatching {
                ApiClient.service().getTimeReport(period, if (mineOnly) 1 else 0)
            }
        }
    }
    LaunchedEffect(period, mineOnly) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Summary") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filters
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("week" to "7 Days", "month" to "30 Days", "all" to "All Time").forEach { (v, label) ->
                    FilterChip(selected = period == v, onClick = { period = v }, label = { Text(label) })
                }
                Spacer(Modifier.weight(1f))
                FilterChip(selected = mineOnly, onClick = { mineOnly = !mineOnly }, label = { Text("Mine") })
            }

            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(state!!.exceptionOrNull()?.message ?: "Error", onRetry = ::load)
                else -> {
                    val report = state!!.getOrThrow()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total summary card
                        item {
                            ReportHeroStat(
                                label = "Total Hours",
                                value = "%.1f hrs".format(report.totalHours),
                                icon = Icons.Outlined.Timer
                            )
                        }

                        if (report.entries.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("No time logged in this period",
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            item {
                                Text("By Client",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp))
                            }
                            items(report.entries) { entry ->
                                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                    Row(Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.client, fontWeight = FontWeight.Medium)
                                            Text("${entry.ticketCount} ticket${if (entry.ticketCount != 1) "s" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline)
                                        }
                                        // Simple bar proportional to total
                                        val pct = if (report.totalHours > 0)
                                            (entry.hours / report.totalHours).toFloat() else 0f
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("%.1f hrs".format(entry.hours),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text("${(pct * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline)
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
