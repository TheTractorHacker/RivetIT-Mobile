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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.ProfitLossResponse
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import com.foleyit.itflow.ui.util.userMessage
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private val MONTH_NAMES = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitLossReportScreen(navController: NavController) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var state by remember { mutableStateOf<Result<ProfitLossResponse>?>(null) }
    val scope = rememberCoroutineScope()
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    fun load() { scope.launch { state = runCatching { ApiClient.service().getProfitLossReport(year) } } }
    LaunchedEffect(year) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profit & Loss") },
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
                    val netProfit = report.months.sumOf { it.profit }
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        item {
                            val isLoss = netProfit < 0
                            ReportHeroStat(
                                label = "Net Profit ($year)",
                                value = currency.format(netProfit),
                                icon = Icons.Outlined.Payments,
                                modifier = Modifier.padding(bottom = 16.dp),
                                containerColor = if (isLoss) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isLoss) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                accentColor = if (isLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                onAccentColor = if (isLoss) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("Monthly Profit Trend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            MonthlyTrendChart(report.months.map { it.profit })
                            Spacer(Modifier.height(16.dp))
                        }
                        items(report.months) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(MONTH_NAMES[m.month - 1], modifier = Modifier.weight(1f))
                                Text(currency.format(m.income), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                Text(currency.format(m.expense), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                                Text(currency.format(m.profit), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
