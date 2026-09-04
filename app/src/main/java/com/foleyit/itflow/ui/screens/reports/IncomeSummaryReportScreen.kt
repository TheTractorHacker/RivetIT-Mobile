package com.foleyit.itflow.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.FinancialSummaryResponse
import com.foleyit.itflow.ui.components.EmptyScreen
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Shared rendering for income/expense summary reports — same response shape, same layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialSummaryScreen(
    title: String,
    navController: NavController,
    year: Int,
    onYearChange: (Int) -> Unit,
    state: Result<FinancialSummaryResponse>?,
    onRetry: () -> Unit,
    barColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                YearPicker(year, onYearChange)
            }
            when {
                state == null -> LoadingScreen()
                state.isFailure -> ErrorScreen(com.foleyit.itflow.ui.util.userMessage(state.exceptionOrNull()!!), onRetry = onRetry)
                else -> {
                    val report = state.getOrThrow()
                    if (report.categories.all { it.total == 0.0 }) {
                        EmptyScreen("No activity in $year")
                    } else {
                        val maxTotal = (report.categories.maxOfOrNull { it.total } ?: 1.0).coerceAtLeast(1.0)
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            item {
                                ReportHeroStat(
                                    label = "Total",
                                    value = currency.format(report.grandTotal),
                                    icon = Icons.Outlined.Payments,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                            items(report.categories.filter { it.total > 0.0 }, key = { it.categoryId }) { c ->
                                ReportBarRow(
                                    label = c.categoryName,
                                    value = currency.format(c.total),
                                    fraction = (c.total / maxTotal).toFloat(),
                                    barColor = barColor
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
fun IncomeSummaryReportScreen(navController: NavController) {
    var year by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var state by remember { mutableStateOf<Result<FinancialSummaryResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch { state = runCatching { com.foleyit.itflow.data.api.ApiClient.service().getIncomeSummaryReport(year) } }
    }
    LaunchedEffect(year) { load() }

    FinancialSummaryScreen(
        title = "Income Summary",
        navController = navController,
        year = year,
        onYearChange = { year = it },
        state = state,
        onRetry = ::load,
        barColor = MaterialTheme.colorScheme.primary
    )
}
