package com.foleyit.itflow.ui.screens.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.FinancialSummaryResponse
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ExpenseSummaryReportScreen(navController: NavController) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var state by remember { mutableStateOf<Result<FinancialSummaryResponse>?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch { state = runCatching { ApiClient.service().getExpenseSummaryReport(year) } }
    }
    LaunchedEffect(year) { load() }

    FinancialSummaryScreen(
        title = "Expense Summary",
        navController = navController,
        year = year,
        onYearChange = { year = it },
        state = state,
        onRetry = ::load,
        barColor = MaterialTheme.colorScheme.error
    )
}
