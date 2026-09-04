package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.OverviewResponse
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewReportScreen(navController: NavController) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var state by remember { mutableStateOf<Result<OverviewResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() { scope.launch { state = runCatching { ApiClient.service().getOverviewReport(year) } } }
    LaunchedEffect(year) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                YearPicker(year) { year = it }
            }
            when {
                state == null -> LoadingScreen()
                state!!.isFailure -> ErrorScreen(userMessage(state!!.exceptionOrNull()!!), onRetry = ::load)
                else -> {
                    val report = state!!.getOrThrow()
                    val maxStatus = (report.byStatus.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    val maxCategory = (report.byCategory.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    val maxPriority = (report.byPriority.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ReportHeroStat(
                                label = "Avg. Resolution Time",
                                value = report.avgResolutionHours?.let { "%.1f hrs".format(it) } ?: "N/A",
                                icon = Icons.Outlined.Schedule
                            )
                        }
                        if (report.byPriority.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("Open Tickets by Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.byPriority) { p ->
                                ReportBarRow(label = p.priority, value = p.count.toString(), fraction = p.count.toFloat() / maxPriority)
                            }
                        }
                        if (report.byStatus.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("Open Tickets by Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.byStatus) { s ->
                                ReportBarRow(label = s.status, value = s.count.toString(), fraction = s.count.toFloat() / maxStatus, barColor = parseHexColor(s.color))
                            }
                        }
                        if (report.byCategory.isNotEmpty()) {
                            item { Spacer(Modifier.height(8.dp)); Text("Open Tickets by Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(report.byCategory) { c ->
                                ReportBarRow(label = c.category, value = c.count.toString(), fraction = c.count.toFloat() / maxCategory, barColor = parseHexColor(c.color))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: Exception) { Color.Gray }
